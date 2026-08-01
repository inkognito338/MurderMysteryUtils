package real.inkognito338.murdermysteryutils.utils;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.*;
import real.inkognito338.murdermysteryutils.Main;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.*;

public class API {

    private static final String API_SCRIPT_URL = "https://raw.githubusercontent.com/inkognito338/MurderMysteryUtils/main/API/API.lua";
    private static final String AUTONEXT_SCRIPT_URL = "https://raw.githubusercontent.com/inkognito338/MurderMysteryUtils/main/API/AutoNext.lua";
    private static final String CHECK_UPDATE_URL = "https://raw.githubusercontent.com/inkognito338/MurderMysteryUtils/refs/heads/main/API/check_update.lua";

    private static volatile OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

    private static volatile boolean scriptLoaded = false;
    private static volatile boolean updateScriptLoaded = false;
    private static volatile boolean updateCheckSuccess = false;
    private static volatile long lastFetchTime = 0;
    private static final long FETCH_INTERVAL_MS = 15 * 60 * 1000;
    private static String cachedScript = null;
    private static String currentServerIP = "";
    private static volatile boolean chatIgnoreScriptLoaded = false;



    private static final CountDownLatch updateScriptLoadLatch = new CountDownLatch(1);
    private static volatile boolean updateCheckStarted = false;

    private static Globals globals;
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    private static synchronized void rebuildClientWithFreshCerts() {
        try {
            Main.LOGGER.info("[API] SSL error detected, refreshing certs and rebuilding client...");
            CertManager.refreshCerts();
            SSLContext ctx = CertManager.getSSLContext();
            if (ctx == null) {
                Main.LOGGER.error("[API] CertManager returned null SSLContext after refresh");
                return;
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
            X509TrustManager tm = (X509TrustManager) tmf.getTrustManagers()[0];
            httpClient = httpClient.newBuilder().sslSocketFactory(ctx.getSocketFactory(), tm).build();
            Main.LOGGER.info("[API] SSL client rebuilt successfully with fresh certs");
        } catch (Exception e) {
            Main.LOGGER.error("[API] Failed to rebuild SSL client: {}", e.getMessage());
        }
    }

    private static void initLuaEngine() {
        globals = new Globals();

        globals.load(new BaseLib());
        globals.load(new PackageLib());
        globals.load(new StringLib());
        globals.load(new TableLib());
        globals.load(new MathLib());

        org.luaj.vm2.compiler.LuaC.install(globals);

        // Обнуляем доступ к функциям, позволяющим скрипту загружать
        // и исполнять произвольный код в обход api-стола, либо читать
        // файлы/модули с диска. PackageLib оставлена загруженной, т.к.
        // StringLib внутренне зависит от таблицы package при инициализации —
        // без этого падает "attempt to index ? (a nil value)" в StringLib.call.
        globals.set("load", LuaValue.NIL);
        globals.set("loadstring", LuaValue.NIL);
        globals.set("dofile", LuaValue.NIL);
        globals.set("loadfile", LuaValue.NIL);
        globals.set("require", LuaValue.NIL);
        globals.set("collectgarbage", LuaValue.NIL);

        globals.set("MOD_NAME", LuaValue.valueOf(Main.NAME));
        globals.set("MOD_VERSION", LuaValue.valueOf(Main.VERSION));
        globals.set("currentServerIP", LuaValue.valueOf(currentServerIP));

        registerApiFunctions();

        Main.LOGGER.info("[API] Lua engine initialized");
    }

    private static void registerApiFunctions() {
        LuaTable api = new LuaTable();

        api.set("log", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                StringBuilder sb = new StringBuilder("[Lua] ");
                for (int i = 1; i <= args.narg(); i++) {
                    if (i > 1) sb.append(" ");
                    sb.append(args.arg(i).tojstring());
                }
                Main.LOGGER.info(sb.toString());
                return LuaValue.NIL;
            }
        });

        api.set("warn", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                StringBuilder sb = new StringBuilder("[Lua WARN] ");
                for (int i = 1; i <= args.narg(); i++) {
                    if (i > 1) sb.append(" ");
                    sb.append(args.arg(i).tojstring());
                }
                Main.LOGGER.warn(sb.toString());
                return LuaValue.NIL;
            }
        });

        api.set("error", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                StringBuilder sb = new StringBuilder("[Lua ERROR] ");
                for (int i = 1; i <= args.narg(); i++) {
                    if (i > 1) sb.append(" ");
                    sb.append(args.arg(i).tojstring());
                }
                Main.LOGGER.error(sb.toString());
                return LuaValue.NIL;
            }
        });

        api.set("getServerIP", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf(currentServerIP);
            }
        });

        api.set("getPlayerName", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.player != null) {
                    return LuaValue.valueOf(mc.player.getName());
                }
                return LuaValue.valueOf("");
            }
        });

        api.set("getModVersion", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf(Main.VERSION);
            }
        });

        api.set("base64", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                try {
                    String str = arg.tojstring();
                    byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
                    String encoded = java.util.Base64.getEncoder().encodeToString(bytes);
                    return LuaValue.valueOf(encoded);
                } catch (Exception e) {
                    return LuaValue.NIL;
                }
            }
        });

        api.set("isPlayerIgnored", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                try {
                    String name = arg.tojstring();
                    boolean ignored = PlayerListManager.isIgnored(name);
                    return LuaValue.valueOf(ignored);
                } catch (Exception e) {
                    return LuaValue.FALSE;
                }
            }
        });

        globals.set("api", api);
    }

    public static boolean isLoaded() {
        return scriptLoaded;
    }

    public static void loadScript(String script) {
        if (globals == null) {
            initLuaEngine();
        }

        try {
            globals.load(script).call();
            Main.LOGGER.info("[API] Script loaded successfully");
            scriptLoaded = true;
        } catch (LuaError e) {
            Main.LOGGER.error("[API] Lua error: {}", e.getMessage());
            scriptLoaded = false;
        }
    }

    public static String httpGet(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", Main.NAME + "/" + Main.VERSION)
                .build();

        try {
            return executeGet(httpClient, request);
        } catch (SSLHandshakeException e) {
            Main.LOGGER.warn("[API] SSL handshake failed, refreshing certs and retrying: {}", url);
            rebuildClientWithFreshCerts();
            return executeGet(httpClient, request);
        }
    }

    private static String executeGet(OkHttpClient client, Request request) throws IOException {
        try (Response response = client.newCall(request).execute()) {
            int code = response.code();
            if (code == 204 || code == 404) return null;
            if (code >= 400) {
                Main.LOGGER.debug("[API] HTTP {} for {}", code, request.url());
                return null;
            }
            ResponseBody body = response.body();
            if (body == null) return null;
            return body.string();
        }
    }

    public static void fetch() {
        new Thread(() -> {
            try {
                String mainScript = httpGet(API_SCRIPT_URL);
                if (mainScript == null || mainScript.isEmpty()) {
                    Main.LOGGER.warn("[API] Empty response from API");
                    if (cachedScript != null && !cachedScript.isEmpty()) {
                        compileAndRunScript(cachedScript);
                        scriptLoaded = true;
                        Main.LOGGER.info("[API] Using cached script");
                    }
                    return;
                }

                String autoNextScript = httpGet(AUTONEXT_SCRIPT_URL);
                if (autoNextScript == null || autoNextScript.isEmpty()) {
                    Main.LOGGER.warn("[API] AutoNext script not found, using embedded fallback");
                    autoNextScript = getFallbackAutoNextScript();
                }

                String fullScript = mainScript + "\n\n-- ====== AUTONEXT SCRIPT ======\n\n" + autoNextScript;

                compileAndRunScript(fullScript);
                cachedScript = fullScript;
                scriptLoaded = true;
                lastFetchTime = System.currentTimeMillis();
                Main.LOGGER.info("[API] Scripts loaded successfully");

            } catch (Exception e) {
                Main.LOGGER.error("[API] Failed to load script", e);
                if (cachedScript != null && !cachedScript.isEmpty()) {
                    try {
                        compileAndRunScript(cachedScript);
                        scriptLoaded = true;
                        Main.LOGGER.info("[API] Using cached script after error");
                    } catch (Exception ignored) {
                        Main.LOGGER.error("[API] Failed to load cached script", ignored);
                    }
                }
            }
        }).start();
    }

    private static String getFallbackAutoNextScript() {
        return
                "function detectAutoNextState(message, source)\n" +
                        "    if not message then return nil end\n" +
                        "    local text = message.text or \"\"\n" +
                        "    local formatted = message.formatted or \"\"\n" +
                        "    if source == \"chat\" then\n" +
                        "        if formatted:find(\"MurderMystery ▸ Перезагрузка сервера через 10 секунд!\") then\n" +
                        "            return \"GAME_END\"\n" +
                        "        end\n" +
                        "    end\n" +
                        "    if source == \"title\" or source == \"subtitle\" then\n" +
                        "        if text:find(\"РОЛЬ: МИРНЫЙ ЖИТЕЛЬ\") or text:find(\"ROLE: INNOCENT\") then\n" +
                        "            return \"INNOCENT\"\n" +
                        "        end\n" +
                        "        if text:find(\"РОЛЬ: ДЕТЕКТИВ\") or text:find(\"ROLE: DETECTIVE\") then\n" +
                        "            return \"DETECTIVE\"\n" +
                        "        end\n" +
                        "        if text:find(\"РОЛЬ: УБИЙЦА\") or text:find(\"ROLE: MURDERER\") then\n" +
                        "            return \"MURDERER\"\n" +
                        "        end\n" +
                        "    end\n" +
                        "    if source == \"chat\" then\n" +
                        "        if text:find(\"ВЫ ПОГИБЛИ\") or text:find(\"YOU DIED\") then\n" +
                        "            return \"DEATH\"\n" +
                        "        end\n" +
                        "    end\n" +
                        "    return nil\n" +
                        "end\n" +
                        "api:log(\"AutoNext script fallback loaded\")\n";
    }

    private static void compileAndRunScript(String scriptCode) {
        try {
            if (globals == null) {
                initLuaEngine();
            }

            globals.set("currentServerIP", LuaValue.valueOf(currentServerIP));
            globals.load(scriptCode).call();

            Main.LOGGER.info("[API] Script compiled and executed successfully");

        } catch (LuaError e) {
            Main.LOGGER.error("[API] Lua error: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static LuaValue callFunction(String functionName, Object... args) {
        if (!scriptLoaded || globals == null) {
            return LuaValue.NIL;
        }

        try {
            LuaValue func = globals.get(functionName);
            if (func.isnil()) {
                Main.LOGGER.warn("[API] Function '{}' not found", functionName);
                return LuaValue.NIL;
            }

            LuaValue[] luaArgs = new LuaValue[args.length];
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null) {
                    luaArgs[i] = LuaValue.NIL;
                } else if (args[i] instanceof String) {
                    luaArgs[i] = LuaValue.valueOf((String) args[i]);
                } else if (args[i] instanceof Boolean) {
                    luaArgs[i] = LuaValue.valueOf((Boolean) args[i]);
                } else if (args[i] instanceof Number) {
                    luaArgs[i] = LuaValue.valueOf(((Number) args[i]).doubleValue());
                } else if (args[i] instanceof LuaValue) {
                    luaArgs[i] = (LuaValue) args[i];
                } else {
                    luaArgs[i] = LuaValue.valueOf(args[i].toString());
                }
            }

            Future<Varargs> future = executor.submit(() -> func.invoke(LuaValue.varargsOf(luaArgs)));

            try {
                Varargs result = future.get(5000, TimeUnit.MILLISECONDS);
                return result.arg1();
            } catch (TimeoutException e) {
                future.cancel(true);
                Main.LOGGER.error("[API] Function '{}' timed out", functionName);
                return LuaValue.NIL;
            }

        } catch (Exception e) {
            Main.LOGGER.error("[API] Error calling function '{}': {}", functionName, e.getMessage());
            return LuaValue.NIL;
        }
    }

    public static boolean getStatus() {
        if (!scriptLoaded) return false;
        try {
            LuaValue result = callFunction("getStatus");
            if (result.isboolean()) {
                return result.toboolean();
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static String getTabColor(String playerName) {
        LuaValue result = callFunction("getApiTabColor", playerName);
        if (result.isnil()) return null;
        String color = result.tojstring();
        return color != null && !"null".equals(color) ? color.replace("&", "§") : null;
    }

    public static Object getModuleSetting(String playerName, String moduleName) {
        LuaValue result = callFunction("getApiModuleSetting", playerName, moduleName);
        if (result.isnil()) return null;
        return convertFromLua(result);
    }

    public static boolean getModuleBoolean(String playerName, String moduleName, boolean def) {
        Object v = getModuleSetting(playerName, moduleName);
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).doubleValue() != 0;
        return def;
    }

    public static int getModuleInt(String playerName, String moduleName, int def) {
        Object v = getModuleSetting(playerName, moduleName);
        if (v instanceof Number) return ((Number) v).intValue();
        return def;
    }

    public static String getTabNameColor(String playerName, String teamName, String prefix, String suffix) {
        LuaValue result = callFunction("getTabNameColor", playerName, teamName, prefix, suffix, currentServerIP);
        if (result.isnil()) return null;
        String color = result.tojstring();
        return color != null && !"null".equals(color) ? color.replace("&", "§") : null;
    }

    public static String getTabPrefix(String playerName, String teamName, String originalPrefix) {
        LuaValue result = callFunction("getTabPrefix", playerName, teamName, originalPrefix, currentServerIP);
        if (result.isnil()) return null;
        String prefix = result.tojstring();
        return prefix != null && !"null".equals(prefix) ? prefix.replace("&", "§") : null;
    }

    public static String getTabSuffix(String playerName, String teamName, String originalSuffix) {
        LuaValue result = callFunction("getTabSuffix", playerName, teamName, originalSuffix, currentServerIP);
        if (result.isnil()) return null;
        String suffix = result.tojstring();
        return suffix != null && !"null".equals(suffix) ? suffix.replace("&", "§") : null;
    }

    public static String getTabHeader(String originalHeader) {
        LuaValue result = callFunction("getTabHeader", originalHeader, currentServerIP);
        if (result.isnil()) return null;
        String header = result.tojstring();
        return header != null && !"null".equals(header) ? header.replace("&", "§") : null;
    }

    public static String getTabFooter(String originalFooter) {
        LuaValue result = callFunction("getTabFooter", originalFooter, currentServerIP);
        if (result.isnil()) return null;
        String footer = result.tojstring();
        return footer != null && !"null".equals(footer) ? footer.replace("&", "§") : null;
    }

    public static String getServerIP() {
        return currentServerIP;
    }

    public static String detectAutoNextState(MessageWrapper message, String source,
                                             String playerName, String teamName,
                                             String prefix, String suffix, String serverIP) {
        if (message == null) return null;

        LuaTable msgTable = new LuaTable();
        msgTable.set("text", LuaValue.valueOf(message.getText()));
        msgTable.set("formatted", LuaValue.valueOf(message.getFormatted()));
        msgTable.set("raw", LuaValue.valueOf(message.getRaw()));

        LuaValue result = callFunction("detectAutoNextState",
                msgTable,
                source != null ? source : "",
                playerName != null ? playerName : "",
                teamName != null ? teamName : "",
                prefix != null ? prefix : "",
                suffix != null ? suffix : "",
                serverIP != null ? serverIP : ""
        );

        return result.isnil() ? null : result.tojstring();
    }

    private static Object convertFromLua(LuaValue value) {
        if (value.isnil()) return null;
        if (value.isboolean()) return value.toboolean();
        if (value.isnumber()) {
            double num = value.todouble();
            return num == Math.floor(num) ? (int) num : num;
        }
        if (value.isstring()) return value.tojstring();
        if (value.istable()) {
            LuaTable table = (LuaTable) value;
            if (table.length() > 0) {
                java.util.List<Object> list = new java.util.ArrayList<>();
                for (int i = 1; i <= table.length(); i++) {
                    list.add(convertFromLua(table.get(i)));
                }
                return list;
            } else {
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                LuaValue[] keys = table.keys();
                for (LuaValue key : keys) {
                    if (key.isstring()) {
                        map.put(key.tojstring(), convertFromLua(table.get(key)));
                    }
                }
                return map;
            }
        }
        return value.tojstring();
    }

    public static Object[] detectAutoNextStateFull(MessageWrapper message, String source,
                                                   String playerName, String teamName,
                                                   String prefix, String suffix, String serverIP) {
        if (message == null) return null;

        LuaTable msgTable = new LuaTable();
        msgTable.set("text", LuaValue.valueOf(message.getText()));
        msgTable.set("formatted", LuaValue.valueOf(message.getFormatted()));
        msgTable.set("raw", LuaValue.valueOf(message.getRaw()));

        LuaValue result = callFunction("detectAutoNextStateFull",
                msgTable,
                source != null ? source : "",
                playerName != null ? playerName : "",
                teamName != null ? teamName : "",
                prefix != null ? prefix : "",
                suffix != null ? suffix : "",
                serverIP != null ? serverIP : ""
        );

        if (result.isnil()) return null;

        String state = null;
        String command = null;
        boolean autoConfirm = true;

        if (result.istable()) {
            LuaTable table = (LuaTable) result;

            LuaValue stateVal = table.get("state");
            if (!stateVal.isnil()) {
                state = stateVal.tojstring();
            }

            LuaValue cmdVal = table.get("command");
            if (!cmdVal.isnil()) {
                command = cmdVal.tojstring();
            }

            LuaValue acVal = table.get("autoConfirm");
            if (!acVal.isnil()) {
                autoConfirm = acVal.toboolean();
            }
        }

        if (state == null) return null;

        return new Object[]{state, command, autoConfirm};
    }
    // ================= Update-checker script loading =================

    public static boolean isUpdateScriptLoaded() {
        return updateScriptLoaded;
    }

    public static boolean isUpdateCheckSuccess() {
        return updateCheckSuccess;
    }

    /**
     * Downloads and loads the update-check Lua script into the shared Lua globals.
     * Safe to call multiple times; only the first call actually triggers work.
     */
    public static void checkUpdate() {
        if (updateCheckStarted) return;
        updateCheckStarted = true;

        new Thread(() -> {
            try {
                String script = httpGet(CHECK_UPDATE_URL);
                if (script != null && !script.isEmpty()) {
                    loadScript(script);
                    updateScriptLoaded = true;
                    updateCheckSuccess = true;
                } else {
                    updateCheckSuccess = false;
                }
            } catch (Exception e) {
                updateCheckSuccess = false;
            } finally {
                updateScriptLoadLatch.countDown();
            }
        }, "API-UpdateCheck-Load").start();
    }

    /**
     * Waits (up to timeoutSeconds) for the update-check script to finish loading.
     * Returns true if it loaded successfully within that time.
     */
    public static boolean awaitUpdateScriptLoaded(long timeoutSeconds) {
        try {
            boolean completed = updateScriptLoadLatch.await(timeoutSeconds, TimeUnit.SECONDS);
            return completed && updateScriptLoaded;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @SubscribeEvent
    public void onServerConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        String ip = getCurrentServerIP();
        if (!ip.equals(currentServerIP)) {
            currentServerIP = ip;
            Main.LOGGER.info("[API] Connected: " + currentServerIP);

            if (globals != null) {
                globals.set("currentServerIP", LuaValue.valueOf(currentServerIP));
            }

            checkUpdate();

            long now = System.currentTimeMillis();
            if (now - lastFetchTime > FETCH_INTERVAL_MS) fetch();
        }
    }

    @SubscribeEvent
    public void onServerDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        currentServerIP = "";
        if (globals != null) {
            globals.set("currentServerIP", LuaValue.valueOf(""));
        }
        Main.LOGGER.info("[API] Disconnected");
    }

    private String getCurrentServerIP() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getCurrentServerData() != null) {
            String ip = mc.getCurrentServerData().serverIP.toLowerCase();
            return ip.contains(":") ? ip.substring(0, ip.lastIndexOf(":")) : ip;
        }
        return "";
    }

    public static void init() {
        initLuaEngine();
        UpdateChecker.register();
        checkUpdate();
        fetch();
        MinecraftForge.EVENT_BUS.register(new API());
        Main.LOGGER.info("[API] Initialized with Lua scripting engine");
    }

    public static void shutdown() {
        executor.shutdownNow();
        Main.LOGGER.info("[API] Shutdown complete");
    }

    // ================= Update-checker high level helpers (no luaj exposed) =================

    public static boolean checkAndKick() {
        if (!updateScriptLoaded) return false;
        try {
            LuaValue result = callFunction("checkAndKick");
            return result.isboolean() && result.toboolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean onChatMessage(String message) {
        if (!updateScriptLoaded) return false;
        try {
            LuaValue result = callFunction("onChatMessage", message);
            return result.isboolean() && result.toboolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String[] getUpdateInfo() {
        if (!updateScriptLoaded) return null;
        try {
            LuaValue result = callFunction("getUpdateInfo");
            if (result.isnil() || !result.istable()) return null;

            LuaValue isOutdated = result.get("is_outdated");
            LuaValue current = result.get("current");
            LuaValue latest = result.get("latest");
            LuaValue downloadUrl = result.get("download_url");

            boolean outdated = isOutdated.isboolean() && isOutdated.toboolean();
            return new String[]{
                    String.valueOf(outdated),
                    current.tojstring(),
                    latest.tojstring(),
                    downloadUrl.tojstring()
            };
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String getUpdateMessage(String lang, String current, String latest, String downloadUrl) {
        try {
            LuaValue result = callFunction("getUpdateMessage", lang, current, latest, downloadUrl);
            return result.isnil() ? null : result.tojstring();
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String getUptodateMessage(String lang) {
        try {
            LuaValue result = callFunction("getUptodateMessage", lang);
            return result.isnil() ? null : result.tojstring();
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean isChatIgnoreScriptLoaded() {
        return chatIgnoreScriptLoaded;
    }
    public static void loadChatIgnoreScript(String script) {
        if (globals == null) {
            initLuaEngine();
        }
        try {
            globals.load(script).call();
            chatIgnoreScriptLoaded = true;
            Main.LOGGER.info("[API] ChatIgnore script loaded into global environment");
        } catch (LuaError e) {
            Main.LOGGER.error("[API] ChatIgnore Lua error: {}", e.getMessage());
            chatIgnoreScriptLoaded = false;
        }
    }

    public static boolean processChatMessage(String json, String serverIP) {
        if (!chatIgnoreScriptLoaded) return false;
        try {
            LuaValue result = callFunction("processChatMessage", json, serverIP);
            return result.isboolean() && result.toboolean();
        } catch (Exception e) {
            Main.LOGGER.error("[API] processChatMessage error: {}", e.getMessage());
            return false;
        }
    }
}