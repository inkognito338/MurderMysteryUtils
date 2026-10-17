package real.inkognito338.murdermysteryutils.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static real.inkognito338.murdermysteryutils.Main.*;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 *
 * Discord Rich Presence напрямую через IPC, без сторонних библиотек.
 *  - Windows: named pipe \\?\pipe\discord-ipc-N
 *  - Linux/macOS: unix-сокет discord-ipc-N через JNA (JNA уже есть в Minecraft 1.12.2)
 *
 * Поток IO никогда не завершается сам: если Discord не установлен, не запущен,
 * перезапущен или закрыл соединение, он продолжает пытаться подключиться (с backoff).
 * Весь ввод-вывод идёт из одного потока.
 */
public class DiscordRPC {

    /** true = писать в лог все ответы Discord (для отладки). */
    private static final boolean DEBUG = true;

    private static final String APP_ID = "1514186924791824494";
    private static final String BUTTON_LABEL = "GitHub";

    private static final long RETRY_MIN_MS = 2_000L;
    private static final long RETRY_MAX_MS = 30_000L;
    private static final long HANDSHAKE_TIMEOUT_MS = 10_000L;
    private static final long MIN_UPDATE_INTERVAL_MS = 2_000L;  // лимит Discord на SET_ACTIVITY
    private static final long KEEPALIVE_INTERVAL_MS = 30_000L;  // heartbeat: обнаруживает мёртвое соединение
    private static final long POLL_INTERVAL_MS = 100L;
    private static final long START_TIME = System.currentTimeMillis() / 1000L;

    // opcodes Discord IPC
    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;
    private static final int OP_PING = 3;
    private static final int OP_PONG = 4;

    private static final String OS = System.getProperty("os.name", "").toLowerCase();
    private static final boolean IS_WINDOWS = OS.contains("win");
    private static final boolean IS_MAC = OS.contains("mac") || OS.contains("darwin");

    private static volatile boolean shuttingDown = false;
    private static volatile boolean ready = false;
    private static volatile boolean dirty = true;
    private static volatile String lastState = "In the main menu";

    private static volatile Transport transport;
    private static boolean loggedNotRunning = false;
    private static long lastSentAt = 0L;

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new DiscordRPC());
        Runtime.getRuntime().addShutdownHook(new Thread(DiscordRPC::shutdown, "DiscordRPC-Shutdown"));

        Thread t = new Thread(DiscordRPC::ioLoop, "DiscordRPC-IO");
        t.setDaemon(true);
        t.start();
    }

    public static void shutdown() {
        shuttingDown = true;
        ready = false;
        closeTransport();
    }

    /** Можно вызывать из любого потока. */
    public static void update(String state) {
        lastState = state;
        dirty = true;
    }

    // ------------------------------------------------------------------ IO loop

    private static void ioLoop() {
        long delay = RETRY_MIN_MS;
        while (!shuttingDown) {
            boolean connected = false;
            try {
                connected = connect();
                if (connected) {
                    delay = RETRY_MIN_MS;   // успешное подключение: следующая попытка будет быстрой
                    session();
                    if (!shuttingDown) LOGGER.info("[DiscordRPC] Connection lost, reconnecting...");
                }
            } catch (LinkageError e) {
                // JNA / libc не загрузились: повторять бессмысленно
                LOGGER.error("[DiscordRPC] Native access unavailable, RPC disabled", e);
                return;
            } catch (Throwable e) {
                if (!shuttingDown) LOGGER.warn("[DiscordRPC] Connection error: {}", e.toString());
            } finally {
                ready = false;
                closeTransport();
            }

            if (shuttingDown) return;
            sleep(delay);
            if (!connected) {
                delay = Math.min(delay * 2, RETRY_MAX_MS);
            }
        }
    }

    private static boolean connect() throws IOException {
        Transport t = IS_WINDOWS ? WindowsTransport.open() : UnixTransport.open();
        if (t == null) {
            if (!loggedNotRunning) {
                LOGGER.info("[DiscordRPC] Discord not found, will keep retrying in background");
                loggedNotRunning = true;
            }
            return false;
        }
        loggedNotRunning = false;
        transport = t;

        JsonObject hs = new JsonObject();
        hs.addProperty("v", 1);
        hs.addProperty("client_id", APP_ID);
        writePacket(OP_HANDSHAKE, hs.toString());

        long deadline = System.currentTimeMillis() + HANDSHAKE_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline && !shuttingDown) {
            if (transport == null) return false; // Discord закрыл соединение
            if (t.available(50)) {
                readAndHandle();
                if (ready) {
                    LOGGER.info("[DiscordRPC] Ready");
                    return true;
                }
            }
        }
        LOGGER.warn("[DiscordRPC] Handshake timeout");
        return false;
    }

    private static void session() throws IOException {
        dirty = true;      // отправить актуальный статус сразу после подключения
        lastSentAt = 0L;
        while (!shuttingDown && ready) {
            Transport t = transport;
            if (t == null) return;

            long now = System.currentTimeMillis();
            boolean keepalive = now - lastSentAt >= KEEPALIVE_INTERVAL_MS;
            if ((dirty || keepalive) && now - lastSentAt >= MIN_UPDATE_INTERVAL_MS) {
                dirty = false;
                lastSentAt = now;
                sendActivity(lastState); // при мёртвом соединении вылетит IOException -> переподключение
            }

            if (t.available(POLL_INTERVAL_MS)) {
                readAndHandle();
            }
        }
    }

    // ------------------------------------------------------------------ Packets

    private static void sendActivity(String state) throws IOException {
        JsonObject activity = new JsonObject();
        activity.addProperty("details", "v" + VERSION);
        activity.addProperty("state", state);
        activity.addProperty("type", 0);

        JsonObject timestamps = new JsonObject();
        timestamps.addProperty("start", START_TIME);
        activity.add("timestamps", timestamps);

        // Кнопки: url обязан быть http(s), label до 32 символов, иначе Discord отклонит весь запрос
        if (SOURCE_URL != null && SOURCE_URL.startsWith("https://") && BUTTON_LABEL.length() <= 32) {
            JsonObject button = new JsonObject();
            button.addProperty("label", BUTTON_LABEL);
            button.addProperty("url", SOURCE_URL);
            JsonArray buttons = new JsonArray();
            buttons.add(button);
            activity.add("buttons", buttons);
        }

        JsonObject args = new JsonObject();
        args.addProperty("pid", getProcessId());
        args.add("activity", activity);

        JsonObject packet = new JsonObject();
        packet.addProperty("cmd", "SET_ACTIVITY");
        packet.add("args", args);
        packet.addProperty("nonce", UUID.randomUUID().toString());

        writePacket(OP_FRAME, packet.toString());
    }

    private static void writePacket(int opcode, String json) throws IOException {
        Transport t = transport;
        if (t == null) throw new IOException("Connection closed");
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(8 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(opcode);
        buf.putInt(data.length);
        buf.put(data);
        t.write(buf.array()); // одним вызовом, чтобы заголовок и тело не разорвались
    }

    private static void readAndHandle() throws IOException {
        Transport t = transport;
        if (t == null) throw new IOException("Connection closed");

        byte[] header = new byte[8];
        t.readFully(header);
        ByteBuffer hb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int opcode = hb.getInt();
        int length = hb.getInt();

        if (length < 0 || length > 1_000_000) throw new IOException("Bad packet length: " + length);
        byte[] body = new byte[length];
        t.readFully(body);
        String json = new String(body, StandardCharsets.UTF_8);

        if (DEBUG) LOGGER.info("[DiscordRPC] <- op={} {}", opcode, json);

        switch (opcode) {
            case OP_PING:
                writePacket(OP_PONG, json);
                break;
            case OP_CLOSE:
                LOGGER.warn("[DiscordRPC] Closed by Discord: {}", json);
                ready = false;
                closeTransport();
                break;
            case OP_FRAME:
                handleFrame(json);
                break;
            default:
                break;
        }
    }

    private static void handleFrame(String json) {
        try {
            JsonObject obj = new JsonParser().parse(json).getAsJsonObject();
            String evt = obj.has("evt") && !obj.get("evt").isJsonNull() ? obj.get("evt").getAsString() : "";

            if ("READY".equals(evt)) {
                ready = true;
            } else if ("ERROR".equals(evt)) {
                LOGGER.error("[DiscordRPC] Discord returned error: {}", json);
            }
        } catch (Exception e) {
            LOGGER.error("[DiscordRPC] Failed to parse frame: " + json, e);
        }
    }

    // ------------------------------------------------------------------ Transports

    private interface Transport {
        /** Есть ли данные для чтения. Ждёт до waitMs. true также при обрыве (чтение тогда бросит IOException). */
        boolean available(long waitMs) throws IOException;

        void write(byte[] data) throws IOException;

        void readFully(byte[] buf) throws IOException;

        void close();
    }

    /** Windows: named pipe. Чтение через опрос length(): блокирующий read на pipe блокирует и write. */
    private static final class WindowsTransport implements Transport {
        private final RandomAccessFile raf;

        private WindowsTransport(RandomAccessFile raf) {
            this.raf = raf;
        }

        static Transport open() {
            for (int i = 0; i < 10; i++) {
                try {
                    return new WindowsTransport(new RandomAccessFile("\\\\?\\pipe\\discord-ipc-" + i, "rw"));
                } catch (FileNotFoundException ignored) {
                    // пробуем следующий номер
                }
            }
            return null;
        }

        @Override
        public boolean available(long waitMs) throws IOException {
            if (raf.length() > 0) return true;
            if (waitMs > 0) sleep(waitMs);
            return raf.length() > 0;
        }

        @Override
        public void write(byte[] data) throws IOException {
            raf.write(data);
        }

        @Override
        public void readFully(byte[] buf) throws IOException {
            raf.readFully(buf);
        }

        @Override
        public void close() {
            try { raf.close(); } catch (Exception ignored) {}
        }
    }

    /** Linux/macOS: unix-сокет через JNA (libc). Грузится только на этих системах. */
    private static final class UnixTransport implements Transport {

        private interface LibC extends Library {
            LibC INSTANCE = (LibC) Native.loadLibrary("c", LibC.class);

            int socket(int domain, int type, int protocol);

            int connect(int fd, byte[] addr, int len);

            NativeLong send(int fd, byte[] buf, NativeLong len, int flags);

            NativeLong read(int fd, byte[] buf, NativeLong len);

            int poll(byte[] fds, NativeLong nfds, int timeoutMs);

            int close(int fd);
        }

        private static final int AF_UNIX = 1;
        private static final int SOCK_STREAM = 1;
        private static final int POLLIN = 0x1;
        private static final int MSG_NOSIGNAL = IS_MAC ? 0 : 0x4000;
        private static final int SUN_PATH_LEN = IS_MAC ? 104 : 108;

        private final LibC libc;
        private final int fd;

        private UnixTransport(LibC libc, int fd) {
            this.libc = libc;
            this.fd = fd;
        }

        static Transport open() {
            LibC libc = LibC.INSTANCE; // при проблемах бросит LinkageError, он обработан в ioLoop

            for (String dir : candidateDirs()) {
                for (int i = 0; i < 10; i++) {
                    String path = new File(dir, "discord-ipc-" + i).getPath();
                    byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
                    if (pathBytes.length >= SUN_PATH_LEN) continue;
                    if (!new File(path).exists()) continue;

                    int fd = libc.socket(AF_UNIX, SOCK_STREAM, 0);
                    if (fd < 0) continue;

                    byte[] addr = buildAddr(pathBytes);
                    if (libc.connect(fd, addr, addr.length) == 0) {
                        return new UnixTransport(libc, fd);
                    }
                    libc.close(fd);
                }
            }
            return null;
        }

        /** sockaddr_un. Linux: sa_family(2) + path[108]. macOS: sun_len(1) + sun_family(1) + path[104]. */
        private static byte[] buildAddr(byte[] pathBytes) {
            if (IS_MAC) {
                byte[] a = new byte[2 + SUN_PATH_LEN];
                a[0] = (byte) a.length;
                a[1] = AF_UNIX;
                System.arraycopy(pathBytes, 0, a, 2, pathBytes.length);
                return a;
            }
            byte[] a = new byte[2 + SUN_PATH_LEN];
            ByteBuffer.wrap(a).order(ByteOrder.nativeOrder()).putShort((short) AF_UNIX);
            System.arraycopy(pathBytes, 0, a, 2, pathBytes.length);
            return a;
        }

        private static List<String> candidateDirs() {
            List<String> dirs = new ArrayList<>();
            String xdg = System.getenv("XDG_RUNTIME_DIR");
            if (xdg != null && !xdg.isEmpty()) {
                dirs.add(xdg);
                dirs.add(xdg + "/app/com.discordapp.Discord"); // Flatpak
                dirs.add(xdg + "/snap.discord");               // Snap
            }
            for (String env : new String[]{"TMPDIR", "TMP", "TEMP"}) {
                String v = System.getenv(env);
                if (v != null && !v.isEmpty()) dirs.add(v);
            }
            dirs.add("/tmp");
            return dirs;
        }

        @Override
        public boolean available(long waitMs) throws IOException {
            byte[] pfd = new byte[8]; // struct pollfd { int fd; short events; short revents; }
            ByteBuffer.wrap(pfd).order(ByteOrder.nativeOrder()).putInt(fd).putShort((short) POLLIN);
            int r = libc.poll(pfd, new NativeLong(1), (int) waitMs);
            if (r < 0) throw new IOException("poll failed");
            if (r == 0) return false;
            // любые revents (POLLIN, POLLHUP, POLLERR): следующее чтение либо вернёт данные, либо бросит IOException
            return true;
        }

        @Override
        public void write(byte[] data) throws IOException {
            int off = 0;
            while (off < data.length) {
                byte[] chunk = off == 0 ? data : java.util.Arrays.copyOfRange(data, off, data.length);
                long n = libc.send(fd, chunk, new NativeLong(chunk.length), MSG_NOSIGNAL).longValue();
                if (n <= 0) throw new IOException("send failed");
                off += (int) n;
            }
        }

        @Override
        public void readFully(byte[] buf) throws IOException {
            int off = 0;
            while (off < buf.length) {
                byte[] tmp = new byte[buf.length - off];
                long n = libc.read(fd, tmp, new NativeLong(tmp.length)).longValue();
                if (n <= 0) throw new IOException("read failed or connection closed");
                System.arraycopy(tmp, 0, buf, off, (int) n);
                off += (int) n;
            }
        }

        @Override
        public void close() {
            try { libc.close(fd); } catch (Throwable ignored) {}
        }
    }

    // ------------------------------------------------------------------ Utils

    private static synchronized void closeTransport() {
        Transport t = transport;
        transport = null;
        if (t != null) t.close();
    }

    /** Сон, который прерывается сразу при выходе из игры. */
    private static void sleep(long ms) {
        long end = System.currentTimeMillis() + ms;
        try {
            while (!shuttingDown) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) return;
                Thread.sleep(Math.min(left, 200L));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Совместимый с Java 8 способ получения PID
    private static long getProcessId() {
        try {
            String name = ManagementFactory.getRuntimeMXBean().getName();
            return Long.parseLong(name.split("@")[0]);
        } catch (Exception e) {
            return 0L;
        }
    }

    // ------------------------------------------------------------------ Forge events

    @SubscribeEvent
    public void onJoinServer(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        update("Playing on a server");
    }

    @SubscribeEvent
    public void onLeaveServer(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        update("In the main menu");
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.getGui() instanceof GuiMainMenu) {
            update("In the main menu");
        }
    }
}