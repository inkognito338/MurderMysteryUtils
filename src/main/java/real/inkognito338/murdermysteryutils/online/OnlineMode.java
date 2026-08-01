package real.inkognito338.murdermysteryutils.online;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.text.TextComponentString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import real.inkognito338.murdermysteryutils.utils.gui.AgreementGui;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.awt.*;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.util.text.ITextComponent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings({"SpellCheckingInspection", "deprecation"})
public class OnlineMode {

    private static final Logger LOGGER = LogManager.getLogger("OnlineMode");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String SERVER_HOST = "murdermysteryutils.inkognito338.workers.dev";
    private static final String HTTP_BASE = "https://" + SERVER_HOST;
    private static final String WS_URL = "wss://" + SERVER_HOST + "/ws";
    private static final long REQUEST_TIMEOUT_SECONDS = 10;

    private static final String TOKEN_FILE_NAME = "authtoken.txt";
    private static final String TOKEN_PREFIX = "TOKEN:";
    private static final String AGREEMENT_PREFIX = "AGREEMENT:";

    private static final int MAX_LATENCY_HISTORY = 20;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final long RECONNECT_BASE_DELAY_MS = 3000;

    // Сколько раз пытаемся дождаться готовности сессии (mc.getSession()) при старте мода.
    private static final int STARTUP_SESSION_WAIT_MAX_ATTEMPTS = 40; // 40 * 250ms = 10 секунд
    private static final long STARTUP_SESSION_WAIT_INTERVAL_MS = 250;

    private static OnlineMode instance;

    private final Map<String, CompletableFuture<JsonObject>> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicLong messageCounter = new AtomicLong(0);
    private final List<OnlineModeListener> listeners = new ArrayList<>();
    private final List<Double> latencyHistory = new ArrayList<>();

    private boolean agreementAccepted = false;
    private boolean isConnected = false;
    private boolean isGuest = false;
    private String userNick = null;
    private int userRank = 0;
    private String userRankName = "User";
    private String userPrefix = "";
    private String savedDiscordToken = null;

    private OnlineWebSocketClient webSocketClient;
    private ScheduledExecutorService pingExecutor;
    private ScheduledExecutorService reconnectExecutor;
    private ScheduledExecutorService startupExecutor;
    private int reconnectAttempts = 0;
    private boolean isReconnecting = false;
    private boolean manualDisconnect = false;
    private boolean authFailedPermanent = false;

    private long connectionStartTime = 0;
    private int totalMessagesSent = 0;
    private int totalMessagesReceived = 0;
    private long lastPingTime = 0;
    private double averageLatency = 0;

    // null = ещё не инициализирован (сессия недоступна). После первого валидного
    // считывания ника этот флаг больше не должен становиться null.
    private volatile String lastKnownNick = null;
    private volatile boolean nickBaselineEstablished = false;
    private final ScheduledExecutorService nickMonitor = Executors.newSingleThreadScheduledExecutor();

    private String lastDisconnectReason = "";

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://[\\w\\-.]+(?::\\d+)?(?:/[\\w\\-./?%&=#]*)?)"
    );

    private OnlineMode() {
        disableSSLVerification();
        loadState();
        startNickMonitor();
        initAutoConnect();
    }

    public static OnlineMode getInstance() {
        if (instance == null) {
            instance = new OnlineMode();
        }
        return instance;
    }

    /**
     * Пытается выполнить авто-вход при старте мода. Если сессия игрока
     * (mc.getSession()) на этом этапе ещё не готова (частая ситуация при
     * ранней инициализации мода/после перезахода), запускает короткий
     * polling-таймер и повторяет попытку, вместо того чтобы молча сдаться.
     *
     * Это устраняет баг, когда после перезахода в игру сохранённый токен
     * есть на диске, но авто-логин не срабатывает, потому что ник ещё
     * не был доступен в момент создания синглтона OnlineMode.
     */
    private void initAutoConnect() {
        String currentNick = getCurrentPlayerNick();

        if (currentNick != null) {
            establishNickBaseline(currentNick);
            attemptAutoConnectIfPossible(currentNick, "constructor");
            return;
        }

        LOGGER.warn("Player session not ready at OnlineMode init, deferring nick baseline / auto-connect");

        startupExecutor = Executors.newSingleThreadScheduledExecutor();
        final int[] attempts = {0};

        startupExecutor.scheduleAtFixedRate(() -> {
            attempts[0]++;
            String nick = getCurrentPlayerNick();

            if (nick != null) {
                LOGGER.info("Session became ready after {} attempt(s), nick={}", attempts[0], nick);
                establishNickBaseline(nick);
                attemptAutoConnectIfPossible(nick, "deferred-startup");
                startupExecutor.shutdown();
                return;
            }

            if (attempts[0] >= STARTUP_SESSION_WAIT_MAX_ATTEMPTS) {
                LOGGER.warn("Gave up waiting for player session after {} attempts", attempts[0]);
                startupExecutor.shutdown();
            }
        }, STARTUP_SESSION_WAIT_INTERVAL_MS, STARTUP_SESSION_WAIT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void attemptAutoConnectIfPossible(String currentNick, String source) {
        if (agreementAccepted && savedDiscordToken != null && !savedDiscordToken.isEmpty()) {
            LOGGER.info("Attempting auto-connect with saved Discord token for nick: {} (source={})", currentNick, source);
            authenticateWithDiscord(currentNick, savedDiscordToken);
        } else {
            LOGGER.info("Auto-connect skipped (source={}): agreementAccepted={}, hasSavedToken={}",
                    source, agreementAccepted, hasSavedToken());
        }
    }

    /**
     * Фиксирует базовый (эталонный) ник для nickMonitor'а. Вызывается один раз —
     * как только удаётся достоверно узнать ник игрока — чтобы nickMonitor не
     * ошибочно принял "первое успешное чтение ника" за "смену ника" и не стёр
     * валидный токен.
     */
    private synchronized void establishNickBaseline(String nick) {
        if (nickBaselineEstablished) return;
        lastKnownNick = nick;
        nickBaselineEstablished = true;
        LOGGER.info("Nick baseline established: {}", nick);
    }

    private Path getTokenFilePath() {
        File mmuDir = new File("MurderMysteryUtils");
        if (!mmuDir.exists()) {
            mmuDir.mkdirs();
        }
        return Paths.get(mmuDir.getAbsolutePath(), TOKEN_FILE_NAME);
    }

    private void loadState() {
        Path tokenFile = getTokenFilePath();
        agreementAccepted = false;
        savedDiscordToken = null;

        if (Files.exists(tokenFile)) {
            try (BufferedReader reader = Files.newBufferedReader(tokenFile)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    if (line.startsWith(AGREEMENT_PREFIX)) {
                        String value = line.substring(AGREEMENT_PREFIX.length()).trim();
                        agreementAccepted = "true".equalsIgnoreCase(value);
                    } else if (line.startsWith(TOKEN_PREFIX)) {
                        savedDiscordToken = line.substring(TOKEN_PREFIX.length()).trim();
                        if (savedDiscordToken.isEmpty()) {
                            savedDiscordToken = null;
                        }
                    }
                }
                LOGGER.info("OnlineMode state loaded: agreement={}, hasSavedToken={}",
                        agreementAccepted, savedDiscordToken != null);
            } catch (IOException e) {
                LOGGER.error("Failed to load state from token file", e);
            }
        }
    }

    private void saveState() {
        Path tokenFile = getTokenFilePath();
        try (BufferedWriter writer = Files.newBufferedWriter(tokenFile)) {
            writer.write(AGREEMENT_PREFIX + (agreementAccepted ? "true" : "false"));
            writer.newLine();
            if (savedDiscordToken != null && !savedDiscordToken.isEmpty()) {
                writer.write(TOKEN_PREFIX + savedDiscordToken);
                writer.newLine();
            }
            writer.flush();
            LOGGER.info("OnlineMode state saved");
        } catch (IOException e) {
            LOGGER.error("Failed to save state", e);
        }
    }

    private void disableSSLVerification() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            LOGGER.warn("Failed to disable SSL verification", e);
        }
    }

    /**
     * Строит ITextComponent, где все URL в тексте кликабельны (открывают браузер)
     * и показывают hover-подсказку. Остальной текст остаётся обычным.
     */
    private ITextComponent buildClickableComponent(String prefix, String msg) {
        TextComponentString root = new TextComponentString(prefix);

        Matcher matcher = URL_PATTERN.matcher(msg);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                root.appendSibling(new TextComponentString(msg.substring(lastEnd, matcher.start())));
            }

            String url = matcher.group(1);
            TextComponentString linkComponent = new TextComponentString(url);
            linkComponent.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
            linkComponent.getStyle().setHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT, new TextComponentString("§7Открыть в браузере")));
            linkComponent.getStyle().setUnderlined(true);
            root.appendSibling(linkComponent);

            lastEnd = matcher.end();
        }

        if (lastEnd < msg.length()) {
            root.appendSibling(new TextComponentString(msg.substring(lastEnd)));
        }

        return root;
    }

    private void sendChatMessageToPlayer(String msg) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            mc.player.sendMessage(buildClickableComponent("§7[§6MurderMysteryUtils§7] ", msg));
        }
    }

    /**
     * Запрашивает данные скина у сервера. Доступно только авторизованным
     * (не гостевым) пользователям — сервер сам это проверяет и вернёт
     * success=false с соответствующим сообщением для гостей.
     *
     * Возвращаемый CompletableFuture содержит JsonObject "data" из ответа
     * сервера (структура SkinFetchResult), либо null при ошибке/таймауте.
     */
    public CompletableFuture<JsonObject> requestSkinData(String nick) {
        CompletableFuture<JsonObject> result = new CompletableFuture<>();

        if (!isConnected || webSocketClient == null || !webSocketClient.isOpen()) {
            result.complete(null);
            return result;
        }

        if (isGuest) {
            result.complete(null);
            return result;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("nick", nick);

        sendRequest("get_skin", payload).thenAccept(response -> {
            if (response == null) {
                result.complete(null);
                return;
            }

            JsonObject respPayload = response.has("payload") ? response.getAsJsonObject("payload") : new JsonObject();
            boolean success = respPayload.has("success") && respPayload.get("success").getAsBoolean();

            if (!success) {
                String message = respPayload.has("message") ? respPayload.get("message").getAsString() : "Неизвестная ошибка";
                LOGGER.warn("get_skin failed: {}", message);
                sendChatMessageToPlayer("§cОшибка получения скина: " + message);
                result.complete(null);
                return;
            }

            JsonObject data = respPayload.has("data") ? respPayload.getAsJsonObject("data") : null;
            result.complete(data);
        });

        return result;
    }

    private void startNickMonitor() {
        nickMonitor.scheduleAtFixedRate(() -> {
            String current = getCurrentPlayerNick();
            if (current == null) return;

            if (!nickBaselineEstablished) {
                // Обычно baseline уже устанавливается в initAutoConnect(),
                // но подстрахуемся на случай гонки/edge-кейса.
                establishNickBaseline(current);
                return;
            }

            if (!lastKnownNick.equals(current)) {
                LOGGER.warn("Nick changed from {} to {} — invalidating session (source=nickMonitor)", lastKnownNick, current);
                lastDisconnectReason = "Смена ника с " + lastKnownNick + " на " + current;

                if (isConnected) {
                    manualDisconnect = true;
                    disconnect();
                    sendChatMessageToPlayer("§cСессия сброшена из-за смены ника. Авторизуйтесь заново.");
                }
                clearDiscordToken();
                lastKnownNick = current;
                notifyListeners(OnlineModeListener.Event.NICK_CHANGED, current);
            }
        }, 1, 2, TimeUnit.SECONDS);
    }

    private void saveDiscordToken(String token) {
        this.savedDiscordToken = token;
        saveState();
        LOGGER.info("Discord token saved");
    }

    private void clearDiscordToken() {
        this.savedDiscordToken = null;
        saveState();
        LOGGER.info("Discord token cleared");
    }

    public boolean hasSavedToken() {
        return savedDiscordToken != null && !savedDiscordToken.isEmpty();
    }

    public String getCurrentPlayerNick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.getSession() != null && mc.getSession().getUsername() != null) {
            return mc.getSession().getUsername();
        }
        return null;
    }

    public String getCurrentGameServer() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return null;
        ServerData sd = mc.getCurrentServerData();
        if (sd != null && sd.serverIP != null) {
            return sd.serverIP;
        }
        return null;
    }

    public void showAgreementDialog() {
        showAgreementDialog(Minecraft.getMinecraft().currentScreen);
    }

    public void showAgreementDialog(net.minecraft.client.gui.GuiScreen returnTo) {
        Minecraft.getMinecraft().displayGuiScreen(new AgreementGui(returnTo));
    }

    public void acceptAgreement() {
        this.agreementAccepted = true;
        saveState();
        LOGGER.info("Agreement accepted");
        notifyListeners(OnlineModeListener.Event.AGREEMENT_ACCEPTED);
    }

    public boolean isAgreementAccepted() {
        return agreementAccepted;
    }

    public String getLastDisconnectReason() {
        return lastDisconnectReason;
    }

    private CompletableFuture<Boolean> ensureSocketOpen() {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            return CompletableFuture.completedFuture(true);
        }

        CompletableFuture<Boolean> openFuture = new CompletableFuture<>();

        webSocketClient = new OnlineWebSocketClient(WS_URL) {
            @Override
            public void onOpen(org.java_websocket.handshake.ServerHandshake handshake) {
                LOGGER.info("WebSocket opened");
                reconnectAttempts = 0;
                isReconnecting = false;
                lastDisconnectReason = "";
                if (!openFuture.isDone()) openFuture.complete(true);
            }

            @Override
            public void onMessage(String message) {
                totalMessagesReceived++;
                handleServerMessage(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                boolean wasConnected = isConnected;
                isConnected = false;
                isGuest = false;
                connectionStartTime = 0;

                lastDisconnectReason = "Код: " + code + ", причина: " + (reason.isEmpty() ? "не указана" : reason);
                LOGGER.info("WebSocket closed: {} - {}", code, reason);
                stopPing();
                failAllPending("Соединение закрыто");
                if (!openFuture.isDone()) openFuture.complete(false);

                if (wasConnected) {
                    sendChatMessageToPlayer("§cСоединение потеряно. Причина: " + lastDisconnectReason);
                    notifyListeners(OnlineModeListener.Event.DISCONNECTED);
                }

                if (!manualDisconnect && !authFailedPermanent && remote) {
                    scheduleReconnect();
                }
            }

            @Override
            public void onError(Exception ex) {
                LOGGER.error("WebSocket error", ex);
                lastDisconnectReason = "Ошибка: " + ex.getMessage();
                if (!openFuture.isDone()) openFuture.complete(false);
                notifyListeners(OnlineModeListener.Event.ERROR, ex.getMessage());
                sendChatMessageToPlayer("§cОшибка соединения: " + ex.getMessage());
            }
        };

        webSocketClient.connect();
        return openFuture;
    }

    private void scheduleReconnect() {
        if (isReconnecting) return;
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            LOGGER.warn("Max reconnect attempts reached, giving up");
            sendChatMessageToPlayer("§cНе удалось переподключиться после " + MAX_RECONNECT_ATTEMPTS + " попыток.");
            notifyListeners(OnlineModeListener.Event.ERROR, "Не удалось переподключиться после " + MAX_RECONNECT_ATTEMPTS + " попыток");
            return;
        }

        isReconnecting = true;
        long delay = RECONNECT_BASE_DELAY_MS * (long) Math.pow(2, reconnectAttempts);
        reconnectAttempts++;

        LOGGER.info("Scheduling reconnect attempt {} in {} ms", reconnectAttempts, delay);

        if (reconnectExecutor == null || reconnectExecutor.isShutdown()) {
            reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
        }

        reconnectExecutor.schedule(() -> {
            isReconnecting = false;
            if (manualDisconnect || authFailedPermanent) return;

            LOGGER.info("Reconnect attempt {}", reconnectAttempts);

            if (savedDiscordToken != null && !savedDiscordToken.isEmpty()) {
                String nick = getCurrentPlayerNick();
                if (nick != null) {
                    sendChatMessageToPlayer("§eПопытка восстановления сессии... (" + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS + ")");
                    authenticateWithDiscord(nick, savedDiscordToken);
                } else {
                    scheduleReconnect();
                }
            } else {
                String nick = getCurrentPlayerNick();
                if (nick != null) {
                    sendChatMessageToPlayer("§eПопытка гостевого входа... (" + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS + ")");
                    connectAsGuest(nick);
                } else {
                    scheduleReconnect();
                }
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void cancelReconnect() {
        if (reconnectExecutor != null) {
            reconnectExecutor.shutdownNow();
            reconnectExecutor = null;
        }
        isReconnecting = false;
        reconnectAttempts = 0;
    }

    public CompletableFuture<Boolean> authenticateWithDiscord(String nick, String tempDiscordToken) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        if (!agreementAccepted) {
            showAgreementDialog();
            result.complete(false);
            return result;
        }

        cancelReconnect();
        manualDisconnect = false;
        authFailedPermanent = false;
        lastDisconnectReason = "";

        ensureSocketOpen().thenCompose(opened -> {
            if (!opened) {
                notifyListeners(OnlineModeListener.Event.ERROR, "Не удалось подключиться к серверу");
                sendChatMessageToPlayer("§cНе удалось подключиться к серверу.");
                result.complete(false);
                return CompletableFuture.completedFuture(null);
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("nick", nick);
            payload.addProperty("token", tempDiscordToken);

            return sendRequest("discord_auth", payload).thenAccept(response -> {
                if (response == null) {
                    notifyListeners(OnlineModeListener.Event.ERROR, "Сервер не ответил");
                    sendChatMessageToPlayer("§cСервер не ответил на запрос авторизации.");
                    result.complete(false);
                    return;
                }

                JsonObject respPayload = response.has("payload") ? response.getAsJsonObject("payload") : new JsonObject();
                boolean success = respPayload.has("success") && respPayload.get("success").getAsBoolean();

                if (success) {
                    JsonObject data = respPayload.has("data") ? respPayload.getAsJsonObject("data") : new JsonObject();

                    boolean requiresApproval = data.has("requiresApproval") && data.get("requiresApproval").getAsBoolean();
                    boolean isNewAccount = data.has("isNewAccount") && data.get("isNewAccount").getAsBoolean();

                    if (requiresApproval) {
                        notifyListeners(OnlineModeListener.Event.REGISTERED_PENDING_APPROVAL,
                                data.has("nick") ? data.get("nick").getAsString() : nick);
                        sendChatMessageToPlayer("§eАккаунт зарегистрирован и ожидает одобрения администратора.");
                        result.complete(false);
                        return;
                    }

                    saveDiscordToken(tempDiscordToken);

                    userNick = data.has("nick") ? data.get("nick").getAsString() : nick;
                    userRank = data.has("rankLevel") ? data.get("rankLevel").getAsInt() : 0;
                    userRankName = data.has("rank") ? data.get("rank").getAsString() : "User";
                    userPrefix = data.has("prefix") ? data.get("prefix").getAsString() : "";
                    isGuest = false;
                    isConnected = true;
                    connectionStartTime = System.currentTimeMillis();
                    startPing();

                    establishNickBaseline(userNick);
                    lastKnownNick = userNick;
                    lastDisconnectReason = "";

                    sendChatMessageToPlayer(isNewAccount
                            ? "§aАккаунт зарегистрирован и вход выполнен!"
                            : "§aУспешный вход через Discord!");

                    notifyListeners(isNewAccount
                            ? OnlineModeListener.Event.REGISTERED_AND_CONNECTED
                            : OnlineModeListener.Event.CONNECTED);
                    result.complete(true);
                } else {
                    String message = respPayload.has("message") ? respPayload.get("message").getAsString() : "Неизвестная ошибка";
                    LOGGER.error("Discord auth failed: {}", message);
                    lastDisconnectReason = "Ошибка авторизации: " + message;

                    if (message.contains("Invalid") || message.contains("outdated") || message.contains("token")) {
                        clearDiscordToken();
                        authFailedPermanent = true;
                        manualDisconnect = true;
                        sendChatMessageToPlayer("§cТокен недействителен. Авторизуйтесь заново.");
                    } else {
                        sendChatMessageToPlayer("§cОшибка авторизации: " + message);
                    }

                    notifyListeners(OnlineModeListener.Event.ERROR, "Ошибка авторизации: " + message);
                    result.complete(false);
                }
            });
        });

        return result;
    }

    public CompletableFuture<Boolean> connectAsGuest(String nick) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        if (!agreementAccepted) {
            showAgreementDialog();
            result.complete(false);
            return result;
        }

        cancelReconnect();
        manualDisconnect = false;
        authFailedPermanent = false;
        lastDisconnectReason = "";

        clearDiscordToken();

        ensureSocketOpen().thenCompose(opened -> {
            if (!opened) {
                notifyListeners(OnlineModeListener.Event.ERROR, "Не удалось подключиться к серверу");
                sendChatMessageToPlayer("§cНе удалось подключиться к серверу.");
                result.complete(false);
                return CompletableFuture.completedFuture(null);
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("nick", nick);

            return sendRequest("guest", payload).thenAccept(response -> {
                if (response == null) {
                    notifyListeners(OnlineModeListener.Event.ERROR, "Сервер не ответил");
                    sendChatMessageToPlayer("§cСервер не ответил на запрос гостевого входа.");
                    result.complete(false);
                    return;
                }

                JsonObject respPayload = response.has("payload") ? response.getAsJsonObject("payload") : new JsonObject();
                boolean success = respPayload.has("success") && respPayload.get("success").getAsBoolean();

                if (success) {
                    JsonObject data = respPayload.has("data") ? respPayload.getAsJsonObject("data") : new JsonObject();
                    userNick = data.has("nick") ? data.get("nick").getAsString() : nick;
                    userRank = 0;
                    userRankName = "Guest";
                    userPrefix = "";
                    isGuest = true;
                    isConnected = true;
                    connectionStartTime = System.currentTimeMillis();
                    startPing();

                    establishNickBaseline(userNick);
                    lastKnownNick = userNick;
                    lastDisconnectReason = "";

                    sendChatMessageToPlayer("§7Вход выполнен как гость.");

                    notifyListeners(OnlineModeListener.Event.CONNECTED);
                    result.complete(true);
                } else {
                    String message = respPayload.has("message") ? respPayload.get("message").getAsString() : "Неизвестная ошибка";
                    LOGGER.error("Guest login failed: {}", message);
                    lastDisconnectReason = "Ошибка гостевого входа: " + message;
                    sendChatMessageToPlayer("§cОшибка гостевого входа: " + message);
                    notifyListeners(OnlineModeListener.Event.ERROR, "Ошибка гостевого входа: " + message);
                    result.complete(false);
                }
            });
        });

        return result;
    }

    public void openDiscordAuthPage() {
        String discordUrl = HTTP_BASE + "/auth/discord";
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(discordUrl));
            } else {
                LOGGER.warn("Desktop browsing not supported, open manually: {}", discordUrl);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to open Discord auth page", e);
        }
        sendChatMessageToPlayer("§eОткрыт браузер для входа через Discord");
        notifyListeners(OnlineModeListener.Event.NOTIFICATION, "Открыт браузер: " + discordUrl);
    }

    public void disconnect() {
        manualDisconnect = true;
        cancelReconnect();
        stopPing();
        failAllPending("Отключено пользователем");

        boolean wasConnected = isConnected;
        isConnected = false;
        isGuest = false;
        connectionStartTime = 0;
        lastDisconnectReason = "Отключение пользователем";

        if (webSocketClient != null) {
            webSocketClient.close();
            webSocketClient = null;
        }

        LOGGER.info("Disconnected");
        sendChatMessageToPlayer("§cВы отключены от онлайн-сервера.");

        if (wasConnected) {
            notifyListeners(OnlineModeListener.Event.DISCONNECTED);
        }
    }

    public String getDisconnectReason() {
        return lastDisconnectReason;
    }

    private String generateId() {
        return "msg_" + messageCounter.incrementAndGet() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private CompletableFuture<JsonObject> sendRequest(String type, JsonObject payload) {
        if (webSocketClient == null || !webSocketClient.isOpen()) {
            CompletableFuture<JsonObject> failed = new CompletableFuture<>();
            failed.complete(null);
            return failed;
        }

        String id = generateId();
        JsonObject message = new JsonObject();
        message.addProperty("id", id);
        message.addProperty("type", type);
        message.add("payload", payload);
        message.addProperty("timestamp", System.currentTimeMillis());

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        try {
            webSocketClient.send(GSON.toJson(message));
            totalMessagesSent++;
        } catch (Exception e) {
            LOGGER.error("Failed to send request", e);
            pendingRequests.remove(id);
            future.complete(null);
            return future;
        }

        ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
        timeoutExecutor.schedule(() -> {
            CompletableFuture<JsonObject> pending = pendingRequests.remove(id);
            if (pending != null && !pending.isDone()) {
                LOGGER.warn("Request {} timed out", id);
                pending.complete(null);
            }
            timeoutExecutor.shutdown();
        }, REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        return future.whenComplete((r, t) -> pendingRequests.remove(id));
    }

    private void failAllPending(String reason) {
        for (CompletableFuture<JsonObject> future : pendingRequests.values()) {
            if (!future.isDone()) future.complete(null);
        }
        pendingRequests.clear();
    }

    private void handleServerMessage(String message) {
        try {
            JsonObject json = GSON.fromJson(message, JsonObject.class);

            String id = json.has("id") ? json.get("id").getAsString() : null;
            if (id != null && pendingRequests.containsKey(id)) {
                CompletableFuture<JsonObject> future = pendingRequests.remove(id);
                if (future != null && !future.isDone()) {
                    future.complete(json);
                }
                return;
            }

            String type = json.has("type") ? json.get("type").getAsString() : "";
            switch (type) {
                case "chat": handleChatMessage(json); break;
                case "system": handleSystemMessage(json); break;
                case "notification": handleNotification(json); break;
                case "pong": handlePong(json); break;
                case "user_update": handleUserUpdate(json); break;
                case "rank_update": handleRankUpdate(json); break;
                case "error": handleErrorMessage(json); break;
                case "tab_animation": handleTabAnimation(json); break;
                case "response": handleResponseMessage(json); break;
                default: LOGGER.debug("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse server message", e);
        }
    }

    private void handleResponseMessage(JsonObject json) {
        JsonObject payload = json.has("payload") ? json.getAsJsonObject("payload") : null;
        if (payload == null) return;
        JsonObject data = payload.has("data") ? payload.getAsJsonObject("data") : null;
        if (data == null) return;

        if (data.has("styles")) {
            parseStylesArray(data.getAsJsonArray("styles"));
        }
        if (data.has("animations")) {
            JsonArray animations = data.getAsJsonArray("animations");
            for (int i = 0; i < animations.size(); i++) {
                parseAndStoreAnimation(animations.get(i).getAsJsonObject());
            }
        }
    }

    private void parseStylesArray(JsonArray stylesArray) {
        List<String> styles = new ArrayList<>();
        for (int i = 0; i < stylesArray.size(); i++) {
            JsonElement el = stylesArray.get(i);
            if (el.isJsonObject()) {
                JsonObject styleObj = el.getAsJsonObject();
                String name = styleObj.has("style") ? styleObj.get("style").getAsString()
                        : (styleObj.has("name") ? styleObj.get("name").getAsString() : null);
                if (name == null) continue;
                styles.add(name);
                int speed = styleObj.has("speed") ? styleObj.get("speed").getAsInt() : 100;
                int[] colors = null;
                if (styleObj.has("colors") && styleObj.get("colors").isJsonArray()) {
                    JsonArray colorArray = styleObj.getAsJsonArray("colors");
                    colors = new int[colorArray.size()];
                    for (int j = 0; j < colorArray.size(); j++) {
                        colors[j] = colorArray.get(j).getAsInt();
                    }
                }
                TabAnimationData.registerStyleMeta(name, speed, colors);
            } else {
                styles.add(el.getAsString());
            }
        }
        TabAnimationData.setAvailableStyles(styles);
    }

    public void requestAnimationsList() {
        if (!isConnected || webSocketClient == null) return;
        try {
            JsonObject json = new JsonObject();
            json.addProperty("id", generateId());
            json.addProperty("type", "tab_animation");
            JsonObject payload = new JsonObject();
            payload.addProperty("action", "get_all");
            json.add("payload", payload);
            json.addProperty("timestamp", System.currentTimeMillis());
            webSocketClient.send(GSON.toJson(json));
            totalMessagesSent++;
        } catch (Exception e) {
            LOGGER.error("Failed to request animations list", e);
        }
    }

    private void handleTabAnimation(JsonObject json) {
        JsonObject payload = json.has("payload") ? json.getAsJsonObject("payload") : null;
        if (payload == null) return;
        String action = payload.has("action") ? payload.get("action").getAsString() : "";

        switch (action) {
            case "update":
            case "sync_request": {
                if (payload.has("animations")) {
                    JsonArray animations = payload.getAsJsonArray("animations");
                    for (int i = 0; i < animations.size(); i++) {
                        parseAndStoreAnimation(animations.get(i).getAsJsonObject());
                    }
                }
                if (payload.has("styles")) {
                    parseStylesArray(payload.getAsJsonArray("styles"));
                }
                if (payload.has("nick") && payload.has("style")) {
                    parseAndStoreAnimation(payload);
                }
                break;
            }
            case "remove": {
                if (payload.has("nick")) {
                    TabAnimationData.remove(payload.get("nick").getAsString());
                }
                break;
            }
        }
    }

    private void parseAndStoreAnimation(JsonObject anim) {
        String nick = anim.has("nick") ? anim.get("nick").getAsString() : "";
        String style = anim.has("style") ? anim.get("style").getAsString() : "Off";
        int speed = anim.has("speed") ? anim.get("speed").getAsInt() : 100;
        int[] colors = null;
        if (anim.has("colors") && anim.get("colors").isJsonArray()) {
            JsonArray colorArray = anim.getAsJsonArray("colors");
            colors = new int[colorArray.size()];
            for (int i = 0; i < colorArray.size(); i++) {
                colors[i] = colorArray.get(i).getAsInt();
            }
        }
        TabAnimationData.update(nick, style, speed, colors);
    }

    public void setTabAnimation(String style) {
        if (!isConnected || webSocketClient == null) return;
        try {
            JsonObject json = new JsonObject();
            json.addProperty("id", generateId());
            json.addProperty("type", "tab_animation");
            JsonObject payload = new JsonObject();
            payload.addProperty("action", "set");
            payload.addProperty("style", style);
            json.add("payload", payload);
            json.addProperty("timestamp", System.currentTimeMillis());
            webSocketClient.send(GSON.toJson(json));
            totalMessagesSent++;
        } catch (Exception e) {
            LOGGER.error("Failed to send tab animation request", e);
        }
    }

    public void requestAllAnimations() {
        if (!isConnected || webSocketClient == null) return;
        try {
            JsonObject json = new JsonObject();
            json.addProperty("id", generateId());
            json.addProperty("type", "tab_animation");
            JsonObject payload = new JsonObject();
            payload.addProperty("action", "sync_request");
            json.add("payload", payload);
            json.addProperty("timestamp", System.currentTimeMillis());
            webSocketClient.send(GSON.toJson(json));
            totalMessagesSent++;
        } catch (Exception e) {
            LOGGER.error("Failed to request tab animations", e);
        }
    }

    private void handleErrorMessage(JsonObject json) {
        JsonObject payload = json.has("payload") ? json.getAsJsonObject("payload") : null;
        String msg = (payload != null && payload.has("message")) ? payload.get("message").getAsString() : "Неизвестная ошибка";
        notifyListeners(OnlineModeListener.Event.ERROR, msg);
        sendChatMessageToPlayer("§cОшибка: " + msg);
    }

    private void handleChatMessage(JsonObject json) {
        JsonObject payload = json.has("payload") ? json.getAsJsonObject("payload") : null;
        if (payload == null) return;
        String componentJson = payload.has("component")
                ? payload.get("component").toString()
                : payload.toString();
        OnlineChatUtils.getInstance().handleIncomingMessage(componentJson);
        notifyListeners(OnlineModeListener.Event.CHAT_MESSAGE, componentJson);
    }

    private void handleSystemMessage(JsonObject json) {
        JsonObject payload = json.has("payload") ? json.getAsJsonObject("payload") : null;
        if (payload == null) return;
        String message = payload.has("message") ? payload.get("message").getAsString() : "";
        if (Minecraft.getMinecraft().player != null) {
            Minecraft.getMinecraft().player.sendMessage(new TextComponentString("§7[§6MurderMysteryUtils§7] §e" + message));
        }
        notifyListeners(OnlineModeListener.Event.SYSTEM_MESSAGE, message);
    }

    private void handleNotification(JsonObject json) {
        JsonObject payload = json.has("payload") ? json.getAsJsonObject("payload") : null;
        if (payload == null) return;
        String message = payload.has("message") ? payload.get("message").getAsString() : "";
        sendChatMessageToPlayer("§a" + message);
        notifyListeners(OnlineModeListener.Event.NOTIFICATION, message);
    }

    private void handlePong(JsonObject json) {
        long now = System.currentTimeMillis();
        if (lastPingTime > 0) {
            double latency = (now - lastPingTime);
            latencyHistory.add(latency);
            if (latencyHistory.size() > MAX_LATENCY_HISTORY) {
                latencyHistory.remove(0);
            }
            averageLatency = latencyHistory.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }
    }

    private void handleUserUpdate(JsonObject json) {
        JsonObject payload = json.has("payload") ? json.getAsJsonObject("payload") : null;
        if (payload == null || !payload.has("data")) return;
        JsonObject data = payload.getAsJsonObject("data");
        if (data.has("rank")) userRank = data.get("rank").getAsInt();
        if (data.has("rankName")) userRankName = data.get("rankName").getAsString();
        if (data.has("prefix")) userPrefix = data.get("prefix").getAsString();
        notifyListeners(OnlineModeListener.Event.USER_UPDATE);
    }

    private void handleRankUpdate(JsonObject json) {
        JsonObject payload = json.has("payload") ? json.getAsJsonObject("payload") : null;
        if (payload == null) return;
        String target = payload.has("target") ? payload.get("target").getAsString() : "";
        int newRank = payload.has("rank") ? payload.get("rank").getAsInt() : 0;
        if (target.equals(userNick)) {
            userRank = newRank;
            notifyListeners(OnlineModeListener.Event.RANK_UPDATE);
        }
    }

    public void sendChatMessage(String message) {
        if (!isConnected || webSocketClient == null) {
            LOGGER.warn("Cannot send message: not connected");
            return;
        }
        try {
            JsonObject json = new JsonObject();
            json.addProperty("id", generateId());
            json.addProperty("type", "chat");
            JsonObject payload = new JsonObject();
            payload.addProperty("text", message);
            json.add("payload", payload);
            json.addProperty("timestamp", System.currentTimeMillis());
            webSocketClient.send(GSON.toJson(json));
            totalMessagesSent++;
        } catch (Exception e) {
            LOGGER.error("Failed to send chat message", e);
        }
    }

    private void sendPing() {
        if (!isConnected || webSocketClient == null) return;
        try {
            JsonObject json = new JsonObject();
            json.addProperty("id", generateId());
            json.addProperty("type", "ping");
            JsonObject payload = new JsonObject();
            payload.addProperty("time", System.currentTimeMillis());
            json.add("payload", payload);
            webSocketClient.send(GSON.toJson(json));
            lastPingTime = System.currentTimeMillis();
        } catch (Exception e) {
            LOGGER.error("Failed to send ping", e);
        }
    }

    private void startPing() {
        stopPing();
        pingExecutor = Executors.newSingleThreadScheduledExecutor();
        pingExecutor.scheduleAtFixedRate(this::sendPing, 5, 5, TimeUnit.SECONDS);
    }

    private void stopPing() {
        if (pingExecutor != null) {
            pingExecutor.shutdown();
            try {
                if (!pingExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    pingExecutor.shutdownNow();
                }
            } catch (InterruptedException ignored) {}
            pingExecutor = null;
        }
    }

    public boolean isConnected() { return isConnected; }
    public boolean isGuest() { return isGuest; }
    public String getUserNick() { return userNick; }
    public int getUserRank() { return userRank; }
    public String getUserRankName() { return userRankName; }
    public String getUserPrefix() { return userPrefix; }
    public double getAverageLatency() { return averageLatency; }
    public int getTotalMessagesSent() { return totalMessagesSent; }
    public int getTotalMessagesReceived() { return totalMessagesReceived; }
    public long getUptime() { return isConnected ? System.currentTimeMillis() - connectionStartTime : 0; }

    public void addListener(OnlineModeListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(OnlineModeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(OnlineModeListener.Event event) {
        for (OnlineModeListener listener : new ArrayList<>(listeners)) {
            listener.onEvent(event);
        }
    }

    private void notifyListeners(OnlineModeListener.Event event, Object data) {
        for (OnlineModeListener listener : new ArrayList<>(listeners)) {
            listener.onEvent(event, data);
        }
    }

    public interface OnlineModeListener {
        default void onEvent(Event event) {}
        default void onEvent(Event event, Object data) {}
        enum Event {
            AGREEMENT_ACCEPTED,
            STATE_CHANGED,
            CONNECTED,
            REGISTERED_AND_CONNECTED,
            REGISTERED_PENDING_APPROVAL,
            DISCONNECTED,
            ERROR,
            CHAT_MESSAGE,
            SYSTEM_MESSAGE,
            NOTIFICATION,
            USER_UPDATE,
            RANK_UPDATE,
            NICK_CHANGED
        }
    }
}