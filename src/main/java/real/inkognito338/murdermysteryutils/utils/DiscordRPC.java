package real.inkognito338.murdermysteryutils.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.IPCListener;
import com.jagrosh.discordipc.entities.Packet;
import com.jagrosh.discordipc.entities.RichPresence;
import com.jagrosh.discordipc.entities.User;
import com.jagrosh.discordipc.entities.pipe.PipeStatus;
import com.jagrosh.discordipc.entities.ActivityType;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import real.inkognito338.murdermysteryutils.Main;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.management.ManagementFactory;
import java.util.UUID;

import static real.inkognito338.murdermysteryutils.Main.*;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

public class DiscordRPC {

    private static final long APP_ID = 1514186924791824494L;
    private static final long RETRY_DELAY_MS = 15_000L;

    private static IPCClient client;
    private static volatile boolean initialized = false;
    private static volatile boolean shuttingDown = false;
    private static volatile String lastState = "In the main menu";
    private static final long START_TIME = System.currentTimeMillis() / 1000L;

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new DiscordRPC());
        Runtime.getRuntime().addShutdownHook(
                new Thread(DiscordRPC::shutdown, "DiscordRPC-Shutdown")
        );

        new Thread(DiscordRPC::connectLoop, "DiscordRPC-Init").start();
    }

    private static void connectLoop() {
        while (!shuttingDown) {
            try {
                client = new IPCClient(APP_ID);
                client.setListener(new IPCListener() {
                    @Override
                    public void onPacketSent(IPCClient c, Packet packet) {}

                    @Override
                    public void onPacketReceived(IPCClient c, Packet packet) {}

                    @Override
                    public void onActivityJoin(IPCClient c, String secret) {}

                    @Override
                    public void onActivitySpectate(IPCClient c, String secret) {}

                    @Override
                    public void onActivityJoinRequest(IPCClient c, String secret, User user) {}

                    @Override
                    public void onReady(IPCClient c) {
                        LOGGER.info("[DiscordRPC] Ready");
                        initialized = true;
                        update(lastState);
                    }

                    @Override
                    public void onClose(IPCClient c, JsonObject json) {
                        initialized = false;
                        scheduleReconnect();
                    }

                    @Override
                    public void onDisconnect(IPCClient c, Throwable t) {
                        initialized = false;
                        scheduleReconnect();
                    }
                });

                client.connect();
                return;

            } catch (Exception e) {
                // просто игнорируем
            }

            if (shuttingDown) return;
            sleepQuietly();
        }
    }

    private static void scheduleReconnect() {
        if (shuttingDown) return;
        new Thread(DiscordRPC::connectLoop, "DiscordRPC-Reconnect").start();
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(DiscordRPC.RETRY_DELAY_MS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public static void shutdown() {
        shuttingDown = true;
        if (!initialized || client == null) return;
        try { client.close(); } catch (Exception ignored) {}
        initialized = false;
    }

    public static void update(String state) {
        lastState = state;
        if (!initialized || client == null) return;
        if (client.getStatus() != PipeStatus.CONNECTED) return;
        try {
            JsonArray buttons = new JsonArray();
            JsonObject button = new JsonObject();
            button.addProperty("label", "GitHub");
            button.addProperty("url", SOURCE_URL);
            buttons.add(button);

            JsonObject args = new JsonObject();
            args.addProperty("pid", getProcessId());

            JsonObject activity = new JsonObject();
            activity.addProperty("details", "v" + VERSION);
            activity.addProperty("state", state);
            activity.addProperty("type", 0);

            JsonObject timestamps = new JsonObject();
            timestamps.addProperty("start", START_TIME);
            activity.add("timestamps", timestamps);

            activity.add("buttons", buttons);
            args.add("activity", activity);

            sendCustomPacket(client, "SET_ACTIVITY", args);

        } catch (Exception e) {
            LOGGER.error("[DiscordRPC] Failed to update presence", e);
        }
    }

    // Совместимый с Java 8 способ получения PID текущего процесса
    private static long getProcessId() {
        try {
            String name = ManagementFactory.getRuntimeMXBean().getName();
            return Long.parseLong(name.split("@")[0]);
        } catch (Exception e) {
            return 0L;
        }
    }

    private static void sendCustomPacket(IPCClient ipcClient, String cmd, JsonObject args) {
        try {
            Field pipeField = IPCClient.class.getDeclaredField("pipe");
            pipeField.setAccessible(true);
            Object pipe = pipeField.get(ipcClient);

            if (pipe != null) {
                JsonObject jsonPacket = new JsonObject();
                jsonPacket.addProperty("cmd", cmd);
                jsonPacket.add("args", args);
                jsonPacket.addProperty("nonce", UUID.randomUUID().toString());

                Method writeMethod = pipe.getClass().getDeclaredMethod("write", com.jagrosh.discordipc.entities.Packet.OpCode.class, JsonObject.class);
                writeMethod.setAccessible(true);
                writeMethod.invoke(pipe, com.jagrosh.discordipc.entities.Packet.OpCode.FRAME, jsonPacket);
            }
        } catch (Exception e) {
            client.sendRichPresence(new RichPresence.Builder()
                    .setState(lastState)
                    .setDetails("v" + VERSION)
                    .setStartTimestamp(START_TIME)
                    .build());
        }
    }

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