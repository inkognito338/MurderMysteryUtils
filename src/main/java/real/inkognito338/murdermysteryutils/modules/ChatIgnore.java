package real.inkognito338.murdermysteryutils.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import real.inkognito338.murdermysteryutils.Main;
import real.inkognito338.murdermysteryutils.utils.API;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

@SideOnly(Side.CLIENT)
public class ChatIgnore extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Setting debug;
    private BufferedWriter logWriter;
    private boolean logInitialized = false;
    private File currentLogFile;

    private static final String CHATIGNORE_SCRIPT_URL = "https://raw.githubusercontent.com/inkognito338/MurderMysteryUtils/main/API/chatignore.lua";
    private static boolean scriptLoaded = false;

    public ChatIgnore() {
        super("ChatIgnore");
        debug = new Setting("Debug", SettingType.BOOLEAN, false);
        addSetting(debug);
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);

        if ((boolean) debug.getValue()) {
            initLogFile();
        }

        if (!scriptLoaded) {
            loadLuaScript();
        }
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);

        try {
            if (logWriter != null) {
                logWriter.close();
                logWriter = null;
            }
            logInitialized = false;
            currentLogFile = null;
        } catch (IOException e) {
            // Игнорируем
        }
    }

    private void loadLuaScript() {
        new Thread(() -> {
            try {
                String script = API.httpGet(CHATIGNORE_SCRIPT_URL);
                if (script != null && !script.isEmpty()) {
                    API.loadChatIgnoreScript(script);
                    scriptLoaded = true;
                    Main.LOGGER.info("[ChatIgnore] Lua script loaded");
                }
            } catch (Exception e) {
                Main.LOGGER.error("[ChatIgnore] Error loading Lua script", e);
            }
        }, "ChatIgnore-LuaLoader").start();
    }

    private void initLogFile() {
        try {
            File logDir = new File(Main.getConfigDir(), "logs");
            if (!logDir.exists()) {
                if (!logDir.mkdirs()) {
                    if (mc.player != null) {
                        mc.player.sendMessage(new TextComponentString("§7[§6ChatIgnore§7] §cFailed to create log directory"));
                    }
                    return;
                }
            }

            String timestamp = new SimpleDateFormat("dd.MM.yyyy_HH-mm-ss").format(new Date());
            currentLogFile = new File(logDir, "chat_debug_" + timestamp + ".log");
            logWriter = new BufferedWriter(new FileWriter(currentLogFile, true));
            logInitialized = true;

            if (mc.player != null) {
                ITextComponent message = new TextComponentString("§7[§6ChatIgnore§7] §aLog file created: §f");

                ITextComponent fileLink = new TextComponentString(currentLogFile.getName());
                fileLink.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, currentLogFile.getAbsolutePath()));
                fileLink.getStyle().setUnderlined(true);

                ITextComponent folderLink = new TextComponentString(" §7(§aоткрыть папку§7)");
                folderLink.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, currentLogFile.getParentFile().getAbsolutePath()));
                folderLink.getStyle().setUnderlined(true);

                message.appendSibling(fileLink);
                message.appendSibling(folderLink);

                mc.player.sendMessage(message);
            }
        } catch (IOException e) {
            if (mc.player != null) {
                mc.player.sendMessage(new TextComponentString("§7[§6ChatIgnore§7] §cFailed to create log file: " + e.getMessage()));
            }
        }
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (mc.player == null) return;
        if (!API.isChatIgnoreScriptLoaded()) return;

        try {
            String json = ITextComponent.Serializer.componentToJson(event.getMessage());
            String serverIP = API.getServerIP();

            // Вся логика в Lua - передаем полный JSON
            boolean shouldCancel = API.processChatMessage(json, serverIP);

            if (shouldCancel) {
                event.setCanceled(true);
                if ((boolean) debug.getValue()) {
                    mc.player.sendMessage(new TextComponentString(
                            "§7[§6ChatIgnore§7] §cMessage filtered by Lua"
                    ));
                }
            }
        } catch (Exception e) {
            Main.LOGGER.error("[ChatIgnore] Error", e);
        }

        // Debug
        if ((boolean) debug.getValue()) {
            if (!logInitialized && logWriter == null) {
                initLogFile();
            }

            if (logInitialized && logWriter != null) {
                String json = ITextComponent.Serializer.componentToJson(event.getMessage());
                mc.player.sendMessage(new TextComponentString("§7[§6ChatIgnore Debug§7] §f" + json));
                try {
                    String timestamp = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date());
                    logWriter.write("[" + timestamp + "] " + json);
                    logWriter.newLine();
                    logWriter.flush();
                } catch (IOException e) {
                    // Игнорируем
                }
            }
        }
    }
}