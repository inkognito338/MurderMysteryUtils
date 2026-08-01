package real.inkognito338.murdermysteryutils.modules;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.SPacketChat;
import net.minecraft.network.play.server.SPacketTitle;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mozilla.javascript.*;
import real.inkognito338.murdermysteryutils.Main;
import real.inkognito338.murdermysteryutils.utils.MurderAPI;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.NPCValidator;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import java.util.jar.JarFile;
import java.util.jar.JarEntry;

@SideOnly(Side.CLIENT)
public class Scripts extends Module {

    private static final Logger LOGGER = LogManager.getLogger("Scripts");
    private static final String SCRIPTS_DIR = "scripts";
    private static final String SCRIPT_EXTENSION = ".js";
    private static final long PLAYER_CACHE_TTL_MS = 100;
    private static final int RHINO_OPTIMIZATION_LEVEL = -1;
    private static final long SHUTDOWN_AWAIT_SECONDS = 2;
    private static final String HANDLER_NAME = "local_api_handler";

    private static final String[] KNOWN_EVENTS = {
            "onChatMessage", "onPacketChat",
            "onTitle", "onSubtitle", "onActionBar",
            "onTick",
            "onWorldJoin", "onWorldLoad", "onWorldUnload",
            "onServerConnect", "onServerDisconnect", "onServerChange",
            "onModuleEnable", "onModuleDisable",
            "onScriptLoaded", "onScriptEnabled", "onScriptDisabled",
            "onScriptToggle", "onScriptsReloaded"
    };

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Setting scriptsList = new Setting("Scripts", SettingType.SCRIPT_LIST, "");
    private final Path scriptsPath;
    private final Map<String, ScriptData> loadedScripts = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> scriptEnabled = new ConcurrentHashMap<>();
    private final Map<String, PlayerData> playerCache = new ConcurrentHashMap<>();
    private final Map<String, List<ScriptListener>> eventListeners = new ConcurrentHashMap<>();
    private final ThreadLocal<String> currentScript = new ThreadLocal<>();

    private ScriptableObject globalScope;
    private ApiBridge apiBridge;
    private ConsoleBridge consoleBridge;
    private boolean jsInitialized = false;
    private volatile ExecutorService scriptExecutor;
    private volatile long lastPlayerUpdate = 0;
    private volatile String lastServerIP = "";
    private int tickCounter = 0;
    private volatile boolean handlerInjected = false;

    public Scripts() {
        super("Scripts");
        addSetting(scriptsList);
        addSetting(new Setting("Reload Scripts", SettingType.SCRIPT_BUTTON, "Reload"));
        addSetting(new Setting("Open Folder", SettingType.SCRIPT_BUTTON, "Open Folder"));
        scriptsPath = Paths.get(Minecraft.getMinecraft().mcDataDir.getAbsolutePath(),
                "MurderMysteryUtils", SCRIPTS_DIR);
        scriptExecutor = Executors.newCachedThreadPool();
        initJsEngine();
        scanScripts();
        updateScriptsList();
    }

    @Override
    public void onEnable() {
        if (!jsInitialized) {
            initJsEngine();
            scanScripts();
            updateScriptsList();
        }
        MinecraftForge.EVENT_BUS.register(this);
        injectPacketHandler();
        triggerEvent("onModuleEnable");
        LOGGER.info("Scripts module enabled");
    }

    @Override
    public void onDisable() {
        removePacketHandler();
        MinecraftForge.EVENT_BUS.unregister(this);
        triggerEvent("onModuleDisable");
        LOGGER.info("Scripts module disabled");
    }

    // ============================================================
    // ПАКЕТНЫЙ ХЭНДЛЕР
    // ============================================================

    private void injectPacketHandler() {
        try {
            if (mc.getConnection() == null) {
                LOGGER.warn("Cannot inject packet handler: connection is null");
                handlerInjected = false;
                return;
            }

            Channel channel = mc.getConnection().getNetworkManager().channel();
            if (!channel.isOpen()) {
                LOGGER.warn("Cannot inject packet handler: channel is null or closed");
                handlerInjected = false;
                return;
            }

            if (channel.pipeline().get(HANDLER_NAME) != null) {
                handlerInjected = true;
                return;
            }

            if (channel.pipeline().get("packet_handler") == null) {
                LOGGER.warn("Cannot inject packet handler: 'packet_handler' not found in pipeline yet ({})",
                        channel.pipeline().names());
                handlerInjected = false;
                return;
            }

            if (channel.eventLoop().inEventLoop()) {
                doInject(channel);
            } else {
                channel.eventLoop().execute(() -> doInject(channel));
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to inject packet handler", e);
            handlerInjected = false;
        }
    }

    private void doInject(Channel channel) {
        try {
            if (channel.pipeline().get(HANDLER_NAME) != null) {
                handlerInjected = true;
                return;
            }
            channel.pipeline().addBefore("packet_handler", HANDLER_NAME,
                    new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            try {
                                if (isToggled() && jsInitialized) {
                                    handlePacket(msg);
                                }
                            } catch (Exception e) {
                                LOGGER.error("Error handling packet in Scripts netty hook", e);
                            }
                            ctx.fireChannelRead(msg);
                        }
                    });
            handlerInjected = true;
            LOGGER.info("Packet handler injected successfully");
        } catch (Exception e) {
            LOGGER.warn("Failed to inject packet handler in event loop", e);
            handlerInjected = false;
        }
    }

    private void removePacketHandler() {
        try {
            if (mc.getConnection() != null) {
                Channel channel = mc.getConnection().getNetworkManager().channel();
                if (channel.pipeline().get(HANDLER_NAME) != null) {
                    if (channel.eventLoop().inEventLoop()) {
                        channel.pipeline().remove(HANDLER_NAME);
                    } else {
                        channel.eventLoop().execute(() -> {
                            if (channel.pipeline().get(HANDLER_NAME) != null) {
                                channel.pipeline().remove(HANDLER_NAME);
                            }
                        });
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to remove packet handler", e);
        } finally {
            handlerInjected = false;
        }
    }

    private void handlePacket(Object packet) {
        if (!isToggled() || !jsInitialized) return;

        if (packet instanceof SPacketTitle) {
            SPacketTitle titlePacket = (SPacketTitle) packet;
            SPacketTitle.Type type = titlePacket.getType();
            ITextComponent message = titlePacket.getMessage();

            String text = message.getUnformattedText();

            switch (type) {
                case TITLE:
                    triggerEvent("onTitle", text);
                    break;
                case SUBTITLE:
                    triggerEvent("onSubtitle", text);
                    break;
                case ACTIONBAR:
                    triggerEvent("onActionBar", text);
                    break;
                case TIMES:
                    break;
            }
        } else if (packet instanceof SPacketChat) {
            SPacketChat chatPacket = (SPacketChat) packet;
            ITextComponent message = chatPacket.getChatComponent();
            String text = message.getUnformattedText();
            String playerName = mc.player != null ? mc.player.getName() : "";
            triggerEvent("onPacketChat", text, playerName);
        }
    }

    // ============================================================
    // ИНИЦИАЛИЗАЦИЯ JS
    // ============================================================

    private void createDefaultScripts() {
        try {
            if (!Files.exists(scriptsPath)) {
                Files.createDirectories(scriptsPath);

                try {
                    // Получаем все скрипты из ресурсов
                    java.net.URL dirUrl = getClass().getResource("/assets/murdermysteryutils/scripts/");
                    if (dirUrl == null) {
                        LOGGER.warn("Scripts resource directory not found");
                        return;
                    }

                    // Для JAR файлов
                    if (dirUrl.getProtocol().equals("jar")) {
                        String jarPath = dirUrl.getPath().substring(5, dirUrl.getPath().indexOf("!"));
                        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(java.net.URLDecoder.decode(jarPath, StandardCharsets.UTF_8.name()))) {
                            java.util.Enumeration<java.util.jar.JarEntry> entries = jarFile.entries();
                            while (entries.hasMoreElements()) {
                                java.util.jar.JarEntry entry = entries.nextElement();
                                String name = entry.getName();
                                if (name.startsWith("assets/murdermysteryutils/scripts/") && !entry.isDirectory()) {
                                    String fileName = name.substring(name.lastIndexOf('/') + 1);
                                    if (fileName.endsWith(SCRIPT_EXTENSION)) {
                                        try (java.io.InputStream is = jarFile.getInputStream(entry)) {
                                            try (java.util.Scanner scanner = new java.util.Scanner(is, StandardCharsets.UTF_8.name())) {
                                                scanner.useDelimiter("\\A");
                                                String content = scanner.hasNext() ? scanner.next() : "";
                                                Path targetPath = scriptsPath.resolve(fileName);
                                                Files.write(targetPath, content.getBytes(StandardCharsets.UTF_8));
                                                LOGGER.info("Created default script: {}", fileName);
                                            }
                                        } catch (Exception e) {
                                            LOGGER.warn("Failed to copy script {}: {}", fileName, e.getMessage());
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Для IDE (файловая система)
                        java.io.File dir = new java.io.File(dirUrl.getFile());
                        java.io.File[] files = dir.listFiles((d, name) -> name.endsWith(SCRIPT_EXTENSION));
                        if (files != null) {
                            for (java.io.File file : files) {
                                try (java.io.FileInputStream fis = new java.io.FileInputStream(file);
                                     java.util.Scanner scanner = new java.util.Scanner(fis, StandardCharsets.UTF_8.name())) {
                                    scanner.useDelimiter("\\A");
                                    String content = scanner.hasNext() ? scanner.next() : "";
                                    Path targetPath = scriptsPath.resolve(file.getName());
                                    Files.write(targetPath, content.getBytes(StandardCharsets.UTF_8));
                                    LOGGER.info("Created default script: {}", file.getName());
                                } catch (Exception e) {
                                    LOGGER.warn("Failed to copy script {}: {}", file.getName(), e.getMessage());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to list resource scripts", e);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to create default scripts directory", e);
        }
    }

    private void initJsEngine() {
        if (scriptExecutor.isShutdown()) {
            scriptExecutor = Executors.newCachedThreadPool();
        }
        try {
            // Создаем папку и копируем дефолтные скрипты (только если папки нет)
            createDefaultScripts();

            try (RhinoContext rc = RhinoContext.enter()) {
                Context ctx = rc.ctx;
                ctx.setOptimizationLevel(RHINO_OPTIMIZATION_LEVEL);
                ctx.setLanguageVersion(Context.VERSION_ES6);
                globalScope = ctx.initStandardObjects();

                // Создаем API bridge
                apiBridge = new ApiBridge();
                Object wrapped = Context.javaToJS(apiBridge, globalScope);
                ScriptableObject.putProperty(globalScope, "api", wrapped);

                // Создаем console bridge
                consoleBridge = new ConsoleBridge();
                Object consoleWrapped = Context.javaToJS(consoleBridge, globalScope);
                ScriptableObject.putProperty(globalScope, "console", consoleWrapped);

                // Добавляем глобальные константы
                globalScope.put("MOD_NAME", globalScope, "MurderMysteryUtils");
                globalScope.put("MOD_VERSION", globalScope, Main.VERSION);
                globalScope.put("SCRIPT_EXTENSION", globalScope, SCRIPT_EXTENSION);

                // Добавляем полифиллы
                addPolyfills(ctx, globalScope);
            }

            jsInitialized = true;
            LOGGER.info("Rhino JS engine initialized with full console support");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize JS engine", e);
        }
    }

    private void addPolyfills(Context ctx, Scriptable scope) {
        try {
            String polyfills =
                    "// setTimeout и setInterval (исправленная версия)\n" +
                            "var setTimeout = function(fn, delay) {\n" +
                            "    var args = Array.prototype.slice.call(arguments, 2);\n" +
                            "    var timer = {\n" +
                            "        id: java.lang.System.currentTimeMillis() + Math.random(),\n" +
                            "        cancelled: false\n" +
                            "    };\n" +
                            "    \n" +
                            "    var run = function() {\n" +
                            "        if (!timer.cancelled) {\n" +
                            "            try {\n" +
                            "                if (typeof fn === 'string') {\n" +
                            "                    eval(fn);\n" +
                            "                } else if (typeof fn === 'function') {\n" +
                            "                    fn.apply(null, args);\n" +
                            "                }\n" +
                            "            } catch(e) {\n" +
                            "                console.error('setTimeout error:', e);\n" +
                            "            }\n" +
                            "        }\n" +
                            "    };\n" +
                            "    \n" +
                            "    if (delay === undefined) delay = 0;\n" +
                            "    var thread = new java.lang.Thread(function() {\n" +
                            "        java.lang.Thread.sleep(delay);\n" +
                            "        run();\n" +
                            "    });\n" +
                            "    thread.start();\n" +
                            "    return timer;\n" +
                            "};\n" +
                            "\n" +
                            "var clearTimeout = function(timer) {\n" +
                            "    if (timer && timer.cancelled !== undefined) {\n" +
                            "        timer.cancelled = true;\n" +
                            "    }\n" +
                            "};\n" +
                            "\n" +
                            "var setInterval = function(fn, interval) {\n" +
                            "    var args = Array.prototype.slice.call(arguments, 2);\n" +
                            "    var timer = {\n" +
                            "        id: java.lang.System.currentTimeMillis() + Math.random(),\n" +
                            "        cancelled: false\n" +
                            "    };\n" +
                            "    \n" +
                            "    var run = function() {\n" +
                            "        if (!timer.cancelled) {\n" +
                            "            try {\n" +
                            "                if (typeof fn === 'string') {\n" +
                            "                    eval(fn);\n" +
                            "                } else if (typeof fn === 'function') {\n" +
                            "                    fn.apply(null, args);\n" +
                            "                }\n" +
                            "            } catch(e) {\n" +
                            "                console.error('setInterval error:', e);\n" +
                            "            }\n" +
                            "            if (!timer.cancelled) {\n" +
                            "                var thread = new java.lang.Thread(function() {\n" +
                            "                    java.lang.Thread.sleep(interval);\n" +
                            "                    run();\n" +
                            "                });\n" +
                            "                thread.start();\n" +
                            "            }\n" +
                            "        }\n" +
                            "    };\n" +
                            "    \n" +
                            "    if (interval === undefined) interval = 0;\n" +
                            "    var thread = new java.lang.Thread(function() {\n" +
                            "        java.lang.Thread.sleep(interval);\n" +
                            "        run();\n" +
                            "    });\n" +
                            "    thread.start();\n" +
                            "    return timer;\n" +
                            "};\n" +
                            "\n" +
                            "var clearInterval = function(timer) {\n" +
                            "    if (timer && timer.cancelled !== undefined) {\n" +
                            "        timer.cancelled = true;\n" +
                            "    }\n" +
                            "};\n" +
                            "\n" +
                            "// Array полифиллы\n" +
                            "if (!Array.prototype.forEach) {\n" +
                            "    Array.prototype.forEach = function(callback, thisArg) {\n" +
                            "        for (var i = 0; i < this.length; i++) {\n" +
                            "            callback.call(thisArg, this[i], i, this);\n" +
                            "        }\n" +
                            "    };\n" +
                            "}\n" +
                            "\n" +
                            "if (!Array.prototype.map) {\n" +
                            "    Array.prototype.map = function(callback, thisArg) {\n" +
                            "        var result = [];\n" +
                            "        for (var i = 0; i < this.length; i++) {\n" +
                            "            result.push(callback.call(thisArg, this[i], i, this));\n" +
                            "        }\n" +
                            "        return result;\n" +
                            "    };\n" +
                            "}\n" +
                            "\n" +
                            "if (!Array.prototype.filter) {\n" +
                            "    Array.prototype.filter = function(callback, thisArg) {\n" +
                            "        var result = [];\n" +
                            "        for (var i = 0; i < this.length; i++) {\n" +
                            "            if (callback.call(thisArg, this[i], i, this)) {\n" +
                            "                result.push(this[i]);\n" +
                            "            }\n" +
                            "        }\n" +
                            "        return result;\n" +
                            "    };\n" +
                            "}\n" +
                            "\n" +
                            "if (!Array.prototype.find) {\n" +
                            "    Array.prototype.find = function(callback, thisArg) {\n" +
                            "        for (var i = 0; i < this.length; i++) {\n" +
                            "            if (callback.call(thisArg, this[i], i, this)) {\n" +
                            "                return this[i];\n" +
                            "            }\n" +
                            "        }\n" +
                            "        return undefined;\n" +
                            "    };\n" +
                            "}\n" +
                            "\n" +
                            "if (!Array.prototype.some) {\n" +
                            "    Array.prototype.some = function(callback, thisArg) {\n" +
                            "        for (var i = 0; i < this.length; i++) {\n" +
                            "            if (callback.call(thisArg, this[i], i, this)) {\n" +
                            "                return true;\n" +
                            "            }\n" +
                            "        }\n" +
                            "        return false;\n" +
                            "    };\n" +
                            "}\n" +
                            "\n" +
                            "if (!Array.prototype.every) {\n" +
                            "    Array.prototype.every = function(callback, thisArg) {\n" +
                            "        for (var i = 0; i < this.length; i++) {\n" +
                            "            if (!callback.call(thisArg, this[i], i, this)) {\n" +
                            "                return false;\n" +
                            "            }\n" +
                            "        }\n" +
                            "        return true;\n" +
                            "    };\n" +
                            "}\n" +
                            "\n" +
                            "// String полифиллы\n" +
                            "if (!String.prototype.includes) {\n" +
                            "    String.prototype.includes = function(search, start) {\n" +
                            "        if (typeof start !== 'number') start = 0;\n" +
                            "        return this.indexOf(search, start) !== -1;\n" +
                            "    };\n" +
                            "}\n" +
                            "\n" +
                            "if (!String.prototype.startsWith) {\n" +
                            "    String.prototype.startsWith = function(search, pos) {\n" +
                            "        return this.substr(!pos || pos < 0 ? 0 : +pos, search.length) === search;\n" +
                            "    };\n" +
                            "}\n" +
                            "\n" +
                            "if (!String.prototype.endsWith) {\n" +
                            "    String.prototype.endsWith = function(search, length) {\n" +
                            "        if (length === undefined || length > this.length) {\n" +
                            "            length = this.length;\n" +
                            "        }\n" +
                            "        return this.substring(length - search.length, length) === search;\n" +
                            "    };\n" +
                            "}\n" +
                            "\n" +
                            "if (!String.prototype.repeat) {\n" +
                            "    String.prototype.repeat = function(count) {\n" +
                            "        if (count < 0) throw new RangeError('Invalid count value');\n" +
                            "        var result = '';\n" +
                            "        for (var i = 0; i < count; i++) {\n" +
                            "            result += this;\n" +
                            "        }\n" +
                            "        return result;\n" +
                            "    };\n" +
                            "}\n" +
                            "\n" +
                            "// Object полифиллы\n" +
                            "if (!Object.assign) {\n" +
                            "    Object.assign = function(target) {\n" +
                            "        if (target === null || target === undefined) {\n" +
                            "            throw new TypeError('Cannot convert undefined or null to object');\n" +
                            "        }\n" +
                            "        var to = Object(target);\n" +
                            "        for (var index = 1; index < arguments.length; index++) {\n" +
                            "            var nextSource = arguments[index];\n" +
                            "            if (nextSource !== null && nextSource !== undefined) {\n" +
                            "                for (var nextKey in nextSource) {\n" +
                            "                    if (Object.prototype.hasOwnProperty.call(nextSource, nextKey)) {\n" +
                            "                        to[nextKey] = nextSource[nextKey];\n" +
                            "                    }\n" +
                            "                }\n" +
                            "            }\n" +
                            "        }\n" +
                            "        return to;\n" +
                            "    };\n" +
                            "}\n" +
                            "\n" +
                            "if (!Object.keys) {\n" +
                            "    Object.keys = function(obj) {\n" +
                            "        var keys = [];\n" +
                            "        for (var key in obj) {\n" +
                            "            if (Object.prototype.hasOwnProperty.call(obj, key)) {\n" +
                            "                keys.push(key);\n" +
                            "            }\n" +
                            "        }\n" +
                            "        return keys;\n" +
                            "    };\n" +
                            "}\n" +
                            "\n" +
                            "// Number полифиллы\n" +
                            "if (!Number.isInteger) {\n" +
                            "    Number.isInteger = function(value) {\n" +
                            "        return typeof value === 'number' && isFinite(value) && Math.floor(value) === value;\n" +
                            "    };\n" +
                            "}\n" +
                            "\n" +
                            "// Math полифиллы\n" +
                            "if (!Math.sign) {\n" +
                            "    Math.sign = function(x) {\n" +
                            "        return ((x > 0) - (x < 0)) || +x;\n" +
                            "    };\n" +
                            "}\n" +
                            "\n" +
                            "if (!Math.trunc) {\n" +
                            "    Math.trunc = function(x) {\n" +
                            "        return x < 0 ? Math.ceil(x) : Math.floor(x);\n" +
                            "    };\n" +
                            "}";

            ctx.evaluateString(scope, polyfills, "polyfills", 1, null);
        } catch (Exception e) {
            LOGGER.error("Failed to add polyfills", e);
        }
    }

    // ============================================================
    // CONSOLE BRIDGE
    // ============================================================

    @SuppressWarnings("unused")
    public class ConsoleBridge {
        private final Map<String, List<Function>> logListeners = new ConcurrentHashMap<>();

        public void log(Object... args) {
            String message = joinArgs(args);
            LOGGER.info("[JS] log: {}", message);
            triggerLogEvent("log", message);
        }

        public void info(Object... args) {
            String message = joinArgs(args);
            LOGGER.info("[JS] info: {}", message);
            triggerLogEvent("info", message);
        }

        public void warn(Object... args) {
            String message = joinArgs(args);
            LOGGER.warn("[JS] warn: {}", message);
            triggerLogEvent("warn", message);
        }

        public void error(Object... args) {
            String message = joinArgs(args);
            LOGGER.error("[JS] error: {}", message);
            triggerLogEvent("error", message);
        }

        public void debug(Object... args) {
            String message = joinArgs(args);
            LOGGER.debug("[JS] debug: {}", message);
            triggerLogEvent("debug", message);
        }

        public void trace(Object... args) {
            String message = joinArgs(args);
            LOGGER.trace("[JS] trace: {}", message);
            triggerLogEvent("trace", message);
        }

        public void dir(Object obj) {
            String str = obj != null ? obj.toString() : "null";
            if (obj instanceof NativeObject || obj instanceof NativeArray) {
                try {
                    Object json = NativeJSON.stringify(Context.getCurrentContext(),
                            globalScope, obj, null, null);
                    if (json != null) str = json.toString();
                } catch (Exception e) {
                    // fallback to toString
                }
            }
            LOGGER.info("[JS] dir: {}", str);
            triggerLogEvent("dir", str);
        }

        public void table(Object obj) {
            String str = obj != null ? obj.toString() : "null";
            if (obj instanceof NativeObject || obj instanceof NativeArray) {
                try {
                    Object json = NativeJSON.stringify(Context.getCurrentContext(),
                            globalScope, obj, null, "  ");
                    if (json != null) str = json.toString();
                } catch (Exception e) {
                    // fallback to toString
                }
            }
            LOGGER.info("[JS] table:\n{}", str);
            triggerLogEvent("table", str);
        }

        public void clear() {
            LOGGER.info("[JS] console.clear() called");
            triggerLogEvent("clear", "");
        }

        public void count(String label) {
            String lbl = label != null ? label : "default";
            LOGGER.info("[JS] count: {}", lbl);
            triggerLogEvent("count", lbl);
        }

        public void time(String label) {
            String lbl = label != null ? label : "default";
            LOGGER.info("[JS] time: {}", lbl);
            triggerLogEvent("time", lbl);
        }

        public void timeEnd(String label) {
            String lbl = label != null ? label : "default";
            LOGGER.info("[JS] timeEnd: {}", lbl);
            triggerLogEvent("timeEnd", lbl);
        }

        public void group(Object... args) {
            String label = args.length > 0 ? joinArgs(args) : "default";
            LOGGER.info("[JS] group: {}", label);
            triggerLogEvent("group", label);
        }

        public void groupEnd() {
            LOGGER.info("[JS] groupEnd");
            triggerLogEvent("groupEnd", "");
        }

        public void groupCollapsed(Object... args) {
            String label = args.length > 0 ? joinArgs(args) : "default";
            LOGGER.info("[JS] groupCollapsed: {}", label);
            triggerLogEvent("groupCollapsed", label);
        }

        public void assert_(boolean condition, Object... args) {
            if (!condition) {
                String message = args.length > 0 ? joinArgs(args) : "Assertion failed";
                LOGGER.warn("[JS] assert: {}", message);
                triggerLogEvent("assert", message);
            }
        }

        private String joinArgs(Object[] args) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                if (i > 0) sb.append(" ");
                sb.append(stringifyJsValue(args[i]));
            }
            return sb.toString();
        }

        private void triggerLogEvent(String method, String message) {
            List<Function> listeners = logListeners.get(method);
            if (listeners == null) return;

            Context ctx = Context.getCurrentContext();
            if (ctx == null) return;

            for (Function listener : listeners) {
                try {
                    listener.call(ctx, globalScope, globalScope, new Object[]{message});
                } catch (Exception e) {
                    LOGGER.debug("Error in console log listener", e);
                }
            }
        }

        public void onLog(String event, Function callback) {
            logListeners.computeIfAbsent(event, k -> new CopyOnWriteArrayList<>()).add(callback);
        }
    }

    // ============================================================
    // API
    // ============================================================

    @SuppressWarnings("unused")
    public class ApiBridge {

        public void log(Object... args) {
            LOGGER.info("[JS] api.log: {}", joinArgs(args));
        }

        public void warn(Object... args) {
            LOGGER.warn("[JS] api.warn: {}", joinArgs(args));
        }

        public void error(Object... args) {
            LOGGER.error("[JS] api.error: {}", joinArgs(args));
        }

        private String joinArgs(Object[] args) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                if (i > 0) sb.append(" ");
                sb.append(stringifyJsValue(args[i]));
            }
            return sb.toString();
        }

        public boolean on(String eventName, Function callback) {
            eventListeners.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>())
                    .add(new ScriptListener(currentScript.get(), callback));
            return true;
        }

        public boolean off(String eventName, Function callback) {
            List<ScriptListener> listeners = eventListeners.get(eventName);
            if (listeners != null) {
                listeners.removeIf(l -> l.callback.equals(callback));
            }
            return true;
        }

        public void emit(String eventName, Object... args) {
            List<ScriptListener> listeners = eventListeners.get(eventName);
            if (listeners == null || listeners.isEmpty()) return;

            boolean enteredHere = Context.getCurrentContext() == null;
            Context ctx = enteredHere ? Context.enter() : Context.getCurrentContext();
            try {
                for (ScriptListener listener : listeners) {
                    if (listener.scriptName != null && !isScriptEnabled(listener.scriptName)) {
                        continue;
                    }
                    String previousScript = currentScript.get();
                    currentScript.set(listener.scriptName);
                    try {
                        listener.callback.call(ctx, globalScope, globalScope, args);
                    } catch (RhinoException re) {
                        String msg = describeRhinoError(re);
                        LOGGER.error("Error in event '{}': {}", eventName, msg);
                        sendSystemMessage("§c[Scripts] Ошибка в " + eventName + ": " + msg);
                    } catch (Exception e) {
                        LOGGER.error("Error in event: {}", eventName, e);
                    } finally {
                        if (previousScript == null) currentScript.remove();
                        else currentScript.set(previousScript);
                    }
                }
            } finally {
                if (enteredHere) Context.exit();
            }
        }

        public boolean sendChatMessage(String msg) {
            if (mc.player != null) {
                String formattedMessage = msg.replace("§", "&");
                mc.player.sendChatMessage(formattedMessage);
                return true;
            }
            return false;
        }

        public void sendSystemMessage(String msg) {
            Scripts.this.sendSystemMessage(msg);
        }

        public boolean sendRawMessage(String msg) {
            Scripts.this.sendRawMessage(msg);
            return true;
        }

        public boolean sendActionBar(String msg) {
            Scripts.this.sendActionBarMessage(msg);
            return true;
        }

        public boolean sendTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
            Scripts.this.sendTitleMessage(title, subtitle, fadeIn, stay, fadeOut);
            return true;
        }

        public String getOwnName() {
            return mc.player != null ? mc.player.getName() : "";
        }

        public String getServerIP() {
            return Scripts.this.getServerIP();
        }

        public String getServerName() {
            return Scripts.this.getServerName();
        }

        public int getPlayerCount() {
            return getAllPlayers().size();
        }

        public List<PlayerData> getPlayerList() {
            return getAllPlayers();
        }

        public PlayerData getPlayer(String name) {
            return getPlayerData(name);
        }

        public List<String> getPlayerNames() {
            List<String> names = new ArrayList<>();
            for (PlayerData p : getAllPlayers()) names.add(p.name);
            return names;
        }

        public Map<String, Object> getOwnPosition() {
            if (mc.player == null) return null;
            return getPlayerPositionMap(mc.player.getName());
        }

        public Object getOwnHealth() {
            return mc.player != null ? mc.player.getHealth() : null;
        }

        public Map<String, Object> getPlayerPosition(String name) {
            PlayerData data = getPlayerData(name);
            if (data == null || !data.hasPosition) return null;
            Map<String, Object> pos = new HashMap<>();
            pos.put("x", data.x);
            pos.put("y", data.y);
            pos.put("z", data.z);
            pos.put("dimension", data.dimension);
            return pos;
        }

        public int getPlayerPing(String name) {
            PlayerData data = getPlayerData(name);
            return data != null ? data.ping : -1;
        }

        public List<Map<String, Object>> getAllPlayersInfo() {
            List<Map<String, Object>> result = new ArrayList<>();
            for (PlayerData p : getAllPlayers()) {
                Map<String, Object> info = new HashMap<>();
                info.put("name", p.name);
                info.put("ping", p.ping);
                info.put("team", p.team);
                info.put("prefix", p.prefix);
                info.put("suffix", p.suffix);
                info.put("isNPC", p.isNPC);
                info.put("isSelf", p.isSelf);
                info.put("hasPosition", p.hasPosition);
                info.put("role", MurderAPI.getInstance().getRole(p.name).name());
                if (p.hasPosition) {
                    info.put("x", p.x);
                    info.put("y", p.y);
                    info.put("z", p.z);
                    info.put("dimension", p.dimension);
                }
                result.add(info);
            }
            return result;
        }

        // ── MurderMystery роли ───────────────────────────────────────────

        public String getPlayerRole(String name) {
            if (name == null) return null;
            return MurderAPI.getInstance().getRole(name).name();
        }

        public boolean isMurderer(String name) {
            return name != null && MurderAPI.getInstance().isMurderer(name);
        }

        public boolean isDetective(String name) {
            return name != null && MurderAPI.getInstance().isDetective(name);
        }

        public boolean isInnocent(String name) {
            return name != null && MurderAPI.getInstance().isInnocent(name);
        }

        public List<String> getMurderers() {
            return new ArrayList<>(MurderAPI.getInstance().getMurderers());
        }

        public List<String> getDetectives() {
            return new ArrayList<>(MurderAPI.getInstance().getDetectives());
        }

        public List<String> getInnocents() {
            return new ArrayList<>(MurderAPI.getInstance().getInnocents());
        }

        public Map<String, String> getAllRoles() {
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, MurderAPI.Role> entry : MurderAPI.getInstance().getAllRoles().entrySet()) {
                result.put(entry.getKey(), entry.getValue().name());
            }
            return result;
        }

        public List<String> getScripts() {
            return new ArrayList<>(loadedScripts.keySet());
        }

        public ScriptMetadata getScriptMetadata(String name) {
            ScriptData data = loadedScripts.get(normalizeScriptName(name));
            return data != null ? data.metadata : null;
        }

        public void reloadScript(String name) {
            loadScript(name);
        }

        public void reloadAllScripts() {
            reloadAll();
        }

        public void enableScript(String name) {
            Scripts.this.enableScript(name);
        }

        public void disableScript(String name) {
            Scripts.this.disableScript(name);
        }

        public boolean isScriptEnabled(String name) {
            return Scripts.this.isScriptEnabled(name);
        }

        public String color(String s) {
            return s.replace('&', '§');
        }

        public String stripColors(String s) {
            return s.replaceAll("§[0-9a-fk-or]", "");
        }

        public String getTimeFormatted() {
            return new SimpleDateFormat("HH:mm:ss").format(new Date());
        }

        public int random(int min, int max) {
            return new Random().nextInt(max - min + 1) + min;
        }

        public boolean contains(String str, String substr) {
            return str != null && str.contains(substr);
        }

        public boolean startsWith(String str, String prefix) {
            return str != null && str.startsWith(prefix);
        }

        public boolean endsWith(String str, String suffix) {
            return str != null && str.endsWith(suffix);
        }

        public String[] split(String str, String delimiter) {
            return str != null ? str.split(java.util.regex.Pattern.quote(delimiter)) : new String[0];
        }

        public boolean executeCommand(String cmd) {
            if (mc.player != null) {
                mc.player.sendChatMessage(cmd);
                return true;
            }
            return false;
        }
    }

    // ============================================================
    // СОБЫТИЯ
    // ============================================================

    private void triggerEvent(String eventName, Object... args) {
        if (!jsInitialized || apiBridge == null) return;
        try {
            apiBridge.emit(eventName, args);
        } catch (Exception e) {
            LOGGER.debug("Event error: {}", eventName, e);
        }
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (!isToggled() || !jsInitialized) return;

        String message = event.getMessage().getUnformattedText();
        String playerName = mc.player != null ? mc.player.getName() : "";

        triggerEvent("onChatMessage", message, playerName);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!isToggled() || !jsInitialized) return;

        tickCounter++;
        if (tickCounter % 20 == 0) {
            triggerEvent("onTick");
        }

        if (tickCounter % 20 == 0 && !handlerInjected) {
            injectPacketHandler();
        }

        String currentIP = getServerIP();
        if (!currentIP.equals(lastServerIP)) {
            if (!lastServerIP.isEmpty()) {
                triggerEvent("onServerChange", lastServerIP, currentIP);
            }
            lastServerIP = currentIP;
        }
    }

    @SubscribeEvent
    public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        triggerEvent("onServerConnect", getServerIP());
        injectPacketHandler();
    }

    @SubscribeEvent
    public void onClientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        triggerEvent("onServerDisconnect", lastServerIP);
        lastServerIP = "";
        removePacketHandler();
        handlerInjected = false;
    }

    // ============================================================
    // УПРАВЛЕНИЕ СКРИПТАМИ
    // ============================================================

    private static class RhinoContext implements AutoCloseable {
        final Context ctx;

        private RhinoContext() {
            this.ctx = Context.enter();
        }

        static RhinoContext enter() {
            return new RhinoContext();
        }

        @Override
        public void close() {
            Context.exit();
        }
    }

    private static class ScriptData {
        final String name;
        final String content;
        final ScriptMetadata metadata;

        ScriptData(String name, String content, ScriptMetadata metadata) {
            this.name = name;
            this.content = content;
            this.metadata = metadata;
        }
    }

    public static class ScriptMetadata {
        public String name;
        public String author;
        public String description;
        public String version = "1.0";
    }

    private static class ScriptListener {
        final String scriptName;
        final Function callback;

        ScriptListener(String scriptName, Function callback) {
            this.scriptName = scriptName;
            this.callback = callback;
        }
    }

    public static class PlayerData {
        public String name;
        public int ping;
        public String team;
        public String prefix;
        public String suffix;
        public boolean isNPC;
        public boolean isSelf;
        public double x;
        public double y;
        public double z;
        public int dimension;
        public boolean hasPosition;
    }

    private void scanScripts() {
        if (!jsInitialized || globalScope == null) return;

        try {
            if (!Files.exists(scriptsPath)) {
                return; // Папка будет создана в createDefaultScripts()
            }

            List<Path> scriptFiles = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(scriptsPath,
                    path -> path.toString().endsWith(SCRIPT_EXTENSION))) {
                for (Path scriptPath : stream) {
                    scriptFiles.add(scriptPath);
                }
            }

            for (Path scriptPath : scriptFiles) {
                loadScript(scriptPath.getFileName().toString());
            }

            LOGGER.info("Scanned scripts, loaded {} scripts", loadedScripts.size());
        } catch (Exception e) {
            LOGGER.error("Failed to scan scripts", e);
        }
    }

    private ScriptMetadata parseMetadataFromComments(String content) {
        ScriptMetadata metadata = new ScriptMetadata();

        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();

            if (line.startsWith("// @")) {
                String[] parts = line.substring(4).split(" ", 2);
                if (parts.length == 2) {
                    String key = parts[0].toLowerCase();
                    String value = parts[1].trim();

                    switch (key) {
                        case "name":
                            metadata.name = value;
                            break;
                        case "author":
                            metadata.author = value;
                            break;
                        case "description":
                            metadata.description = value;
                            break;
                        case "version":
                            metadata.version = value;
                            break;
                    }
                }
            }
        }

        if (metadata.name == null) {
            int commentStart = content.indexOf("/*");
            if (commentStart != -1) {
                int commentEnd = content.indexOf("*/", commentStart + 2);
                if (commentEnd != -1) {
                    String comment = content.substring(commentStart + 2, commentEnd).trim();
                    String[] parts = comment.split(",");
                    for (String part : parts) {
                        String[] keyValue = part.split(":", 2);
                        if (keyValue.length == 2) {
                            String key = keyValue[0].trim().toLowerCase();
                            String value = keyValue[1].trim();

                            switch (key) {
                                case "name":
                                    metadata.name = value;
                                    break;
                                case "author":
                                    metadata.author = value;
                                    break;
                                case "description":
                                    metadata.description = value;
                                    break;
                                case "version":
                                    metadata.version = value;
                                    break;
                            }
                        }
                    }
                }
            }
        }

        return metadata;
    }

    private boolean validateMetadata(ScriptMetadata metadata) {
        return metadata.name != null && !metadata.name.isEmpty() &&
                metadata.author != null && !metadata.author.isEmpty() &&
                metadata.description != null && !metadata.description.isEmpty();
    }

    private void autoRegisterEventHandlers(Scriptable scriptScope, String fileName) {
        for (String eventName : KNOWN_EVENTS) {
            Object candidate;
            try {
                candidate = ScriptableObject.getProperty(scriptScope, eventName);
            } catch (Exception e) {
                continue;
            }
            if (candidate instanceof Function) {
                eventListeners.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>())
                        .add(new ScriptListener(fileName, (Function) candidate));
                LOGGER.info("Auto-registered handler '{}' from script {}", eventName, fileName);
            }
        }
    }

    private void removeListenersForScript(String fileName) {
        for (List<ScriptListener> listeners : eventListeners.values()) {
            listeners.removeIf(l -> fileName.equals(l.scriptName));
        }
    }

    private void loadScript(String fileName) {
        try {
            Path filePath = scriptsPath.resolve(fileName);
            if (!Files.exists(filePath)) {
                LOGGER.warn("Script not found: {}", fileName);
                return;
            }

            String content = stripBom(new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8));

            ScriptMetadata metadata = parseMetadataFromComments(content);
            if (!validateMetadata(metadata)) {
                LOGGER.error("Script missing required metadata: {}", fileName);
                sendSystemMessage("§c[Scripts] Скрипт " + fileName + " не содержит обязательных метаданных (name, author, description)");
                return;
            }

            removeListenersForScript(fileName);

            try (RhinoContext rc = RhinoContext.enter()) {
                Context ctx = rc.ctx;
                ctx.setOptimizationLevel(RHINO_OPTIMIZATION_LEVEL);
                ctx.setLanguageVersion(Context.VERSION_ES6);

                Scriptable scriptScope = ctx.newObject(globalScope);
                scriptScope.setParentScope(globalScope);

                Object apiWrapped = Context.javaToJS(apiBridge, scriptScope);
                scriptScope.put("api", scriptScope, apiWrapped);

                Object consoleWrapped = Context.javaToJS(consoleBridge, scriptScope);
                scriptScope.put("console", scriptScope, consoleWrapped);

                currentScript.set(fileName);
                try {
                    ctx.evaluateString(scriptScope, content, fileName, 1, null);

                    autoRegisterEventHandlers(scriptScope, fileName);

                    loadedScripts.put(fileName, new ScriptData(fileName, content, metadata));
                    scriptEnabled.putIfAbsent(fileName, new AtomicBoolean(true));

                    LOGGER.info("Script loaded: {} by {}", metadata.name, metadata.author);
                    sendSystemMessage("§a[Scripts] Скрипт загружен: §f" + metadata.name + " §7(автор: " + metadata.author + ")");
                    updateScriptsList();
                    triggerEvent("onScriptLoaded", fileName);

                } finally {
                    currentScript.remove();
                }
            }
        } catch (RhinoException re) {
            String msg = describeRhinoError(re);
            LOGGER.error("Error executing script {}: {}", fileName, msg);
            sendSystemMessage("§c[Scripts] Ошибка в " + fileName + ": " + msg);
        } catch (Exception e) {
            LOGGER.error("Error executing script: {}", fileName, e);
            sendSystemMessage("§c[Scripts] Ошибка в " + fileName + ": " + e);
        }
    }

    private void reloadAll() {
        if (!jsInitialized || globalScope == null) {
            LOGGER.warn("Cannot reload scripts: JS engine not initialized, attempting to reinitialize");
            initJsEngine();
            if (!jsInitialized) {
                sendSystemMessage("§c[Scripts] Не удалось перезагрузить: JS-движок не инициализирован");
                return;
            }
        }

        for (String script : loadedScripts.keySet()) {
            removeListenersForScript(script);
        }
        loadedScripts.clear();
        scanScripts();
        updateScriptsList();
        triggerEvent("onScriptsReloaded");
        sendSystemMessage("§a[Scripts] Скрипты перезагружены (" + loadedScripts.size() + ")");
    }

    private void enableScript(String fileName) {
        String name = normalizeScriptName(fileName);
        scriptEnabled.computeIfAbsent(name, k -> new AtomicBoolean(true)).set(true);
        updateScriptsList();
        triggerEvent("onScriptEnabled", name);
    }

    private void disableScript(String fileName) {
        String name = normalizeScriptName(fileName);
        scriptEnabled.computeIfAbsent(name, k -> new AtomicBoolean(true)).set(false);
        updateScriptsList();
        triggerEvent("onScriptDisabled", name);
    }

    private boolean isScriptEnabled(String fileName) {
        String name = normalizeScriptName(fileName);
        AtomicBoolean enabled = scriptEnabled.get(name);
        return enabled != null && enabled.get();
    }

    private String normalizeScriptName(String fileName) {
        return fileName.endsWith(SCRIPT_EXTENSION) ? fileName : fileName + SCRIPT_EXTENSION;
    }

    private void updateScriptsList() {
        List<String> scripts = new ArrayList<>(loadedScripts.keySet());
        if (scripts.isEmpty()) {
            scriptsList.setValue("Нет загруженных скриптов");
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (String script : scripts) {
            boolean enabled = isScriptEnabled(script);
            ScriptData data = loadedScripts.get(script);
            if (data != null && data.metadata != null) {
                sb.append(enabled ? "[✓] " : "[✗] ");
                sb.append(data.metadata.name);
                if (data.metadata.version != null && !data.metadata.version.isEmpty()) {
                    sb.append(" v").append(data.metadata.version);
                }
                sb.append(" §8#").append(script);
                sb.append("\n");

                sb.append("Автор: ").append(data.metadata.author).append("\n");
                sb.append("Описание: ").append(data.metadata.description).append("\n");
            }
        }
        scriptsList.setValue(sb.toString());
    }

    private void openScriptsFolder() {
        try {
            if (!Files.exists(scriptsPath)) {
                Files.createDirectories(scriptsPath);
            }
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(scriptsPath.toFile());
            } else {
                LOGGER.warn("Desktop.open is not supported in this environment");
                sendSystemMessage("§c[Scripts] Не удалось открыть папку: не поддерживается на этой системе. Путь: §f" + scriptsPath);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to open scripts folder", e);
            sendSystemMessage("§c[Scripts] Не удалось открыть папку скриптов: " + e.getMessage());
        }
    }

    public void toggleScript(String scriptName) {
        if (isScriptEnabled(scriptName)) {
            disableScript(scriptName);
        } else {
            enableScript(scriptName);
        }
        updateScriptsList();
        triggerEvent("onScriptToggle", scriptName, isScriptEnabled(scriptName));
    }

    public void onScriptButtonClick(String buttonName) {
        switch (buttonName) {
            case "Reload":
                reloadAll();
                break;
            case "Open Folder":
                openScriptsFolder();
                break;
            default:
                LOGGER.warn("Unknown script button: {}", buttonName);
        }
    }

    // ============================================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ============================================================

    private String stripBom(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') {
            return s.substring(1);
        }
        return s;
    }

    private String describeRhinoError(RhinoException re) {
        StringBuilder sb = new StringBuilder();
        if (re instanceof JavaScriptException) {
            Object value = ((JavaScriptException) re).getValue();
            sb.append(stringifyJsValue(value));
        } else {
            sb.append(re.details());
        }
        String source = re.sourceName();
        int line = re.lineNumber();
        if (source != null && line > 0) {
            sb.append(" (").append(source).append(":").append(line).append(")");
        }
        return sb.toString();
    }

    private String stringifyJsValue(Object value) {
        if (value == null || value == Undefined.instance) return "undefined";
        if (value instanceof NativeObject || value instanceof NativeArray) {
            try {
                Object json = NativeJSON.stringify(Context.getCurrentContext(), globalScope, value, null, null);
                return json != null ? json.toString() : String.valueOf(value);
            } catch (Exception e) {
                return String.valueOf(value);
            }
        }
        try {
            return Context.toString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private void sendMessage(String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString(msg));
        }
    }

    private void sendSystemMessage(String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString(
                    TextFormatting.GRAY + "[" + TextFormatting.GOLD + "JS" + TextFormatting.GRAY + "]" +
                            TextFormatting.WHITE + " " + msg));
        }
    }

    private void sendRawMessage(String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString(msg.replace("&", "§")));
        }
    }

    private void sendActionBarMessage(String msg) {
        if (mc.player != null) {
            mc.player.sendStatusMessage(new TextComponentString(msg), true);
        }
    }

    private void sendTitleMessage(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (mc.player != null && mc.player.connection != null) {
            try {
                net.minecraft.network.play.server.SPacketTitle titlePacket =
                        new net.minecraft.network.play.server.SPacketTitle(
                                net.minecraft.network.play.server.SPacketTitle.Type.TITLE,
                                new TextComponentString(title != null ? title : "")
                        );
                mc.player.connection.sendPacket(titlePacket);

                if (subtitle != null) {
                    net.minecraft.network.play.server.SPacketTitle subtitlePacket =
                            new net.minecraft.network.play.server.SPacketTitle(
                                    net.minecraft.network.play.server.SPacketTitle.Type.SUBTITLE,
                                    new TextComponentString(subtitle)
                            );
                    mc.player.connection.sendPacket(subtitlePacket);
                }

                if (fadeIn >= 0 && stay >= 0 && fadeOut >= 0) {
                    net.minecraft.network.play.server.SPacketTitle timesPacket =
                            new net.minecraft.network.play.server.SPacketTitle(fadeIn, stay, fadeOut);
                    mc.player.connection.sendPacket(timesPacket);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to send title packet", e);
            }
        }
    }

    private String getServerIP() {
        if (mc.getCurrentServerData() != null) {
            String ip = mc.getCurrentServerData().serverIP;
            return ip.contains(":") ? ip.substring(0, ip.lastIndexOf(":")) : ip;
        }
        return "";
    }

    private String getServerName() {
        return mc.getCurrentServerData() != null ? mc.getCurrentServerData().serverName : "";
    }

    private Map<String, Object> getPlayerPositionMap(String name) {
        if (mc.world == null || name == null) return null;
        for (EntityPlayer player : mc.world.playerEntities) {
            if (name.equalsIgnoreCase(player.getName())) {
                Map<String, Object> pos = new HashMap<>();
                pos.put("x", player.posX);
                pos.put("y", player.posY);
                pos.put("z", player.posZ);
                pos.put("dimension", player.dimension);
                return pos;
            }
        }
        return null;
    }

    private PlayerData getPlayerData(String name) {
        if (name == null) return null;
        updatePlayerCache();
        return playerCache.get(name.toLowerCase());
    }

    private List<PlayerData> getAllPlayers() {
        updatePlayerCache();
        return new ArrayList<>(playerCache.values());
    }

    private void updatePlayerCache() {
        long now = System.currentTimeMillis();
        if (now - lastPlayerUpdate < PLAYER_CACHE_TTL_MS) return;
        lastPlayerUpdate = now;
        playerCache.clear();
        if (mc.player == null || mc.player.connection == null) return;

        String ownName = mc.player.getName();

        Map<String, EntityPlayer> worldPlayers = new HashMap<>();
        if (mc.world != null) {
            for (EntityPlayer p : mc.world.playerEntities) {
                if (p.getName() != null) {
                    worldPlayers.put(p.getName().toLowerCase(), p);
                }
            }
        }

        for (NetworkPlayerInfo info : mc.player.connection.getPlayerInfoMap()) {
            String name = info.getGameProfile().getName();
            if (name == null) continue;

            PlayerData data = new PlayerData();
            data.name = name;
            data.ping = info.getResponseTime();
            data.isSelf = name.equals(ownName);
            data.isNPC = NPCValidator.isNPC(name);

            ScorePlayerTeam team = mc.world != null ? mc.world.getScoreboard().getPlayersTeam(name) : null;
            if (team != null) {
                data.team = team.getName();
                data.prefix = team.getPrefix();
                data.suffix = team.getSuffix();
            }

            EntityPlayer entity = worldPlayers.get(name.toLowerCase());
            if (entity != null) {
                data.x = entity.posX;
                data.y = entity.posY;
                data.z = entity.posZ;
                data.dimension = entity.dimension;
                data.hasPosition = true;
            }

            playerCache.put(name.toLowerCase(), data);
        }
    }

    public void shutdown() {
        scriptExecutor.shutdown();
        try {
            if (!scriptExecutor.awaitTermination(SHUTDOWN_AWAIT_SECONDS, TimeUnit.SECONDS)) {
                scriptExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scriptExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public Map<String, Boolean> getScriptEnabledStates() {
        Map<String, Boolean> result = new HashMap<>();
        for (Map.Entry<String, AtomicBoolean> entry : scriptEnabled.entrySet()) {
            result.put(entry.getKey(), entry.getValue().get());
        }
        return result;
    }

    public void setScriptEnabledStates(Map<String, Boolean> states) {
        for (Map.Entry<String, Boolean> entry : states.entrySet()) {
            scriptEnabled.put(entry.getKey(), new AtomicBoolean(entry.getValue()));
        }
        updateScriptsList();
    }
}