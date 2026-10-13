package real.inkognito338.murdermysteryutils.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ChatType;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 22.09.2026
 */

@SideOnly(Side.CLIENT)
public class ChatLogger extends Module {

    private static final Logger LOGGER = LogManager.getLogger("MurderMysteryUtils-ChatLogger");

    private final Minecraft mc = Minecraft.getMinecraft();
    private PrintWriter writer;
    private String currentLogDate = ""; // Дата, для которой открыт текущий writer
    private String currentLogNick = ""; // Ник (или "" если сплит выключен/неизвестен), для которого открыт текущий writer

    private final Setting logChat     = new Setting("Log Chat",     SettingType.BOOLEAN, true);
    private final Setting logSystem   = new Setting("Log System",   SettingType.BOOLEAN, false);
    private final Setting logGameInfo = new Setting("Log GameInfo", SettingType.BOOLEAN, false);
    private final Setting logColors   = new Setting("Log Colors",   SettingType.BOOLEAN, false);
    private final Setting logSent     = new Setting("Log Sent",     SettingType.BOOLEAN, true);
    private final Setting logParty    = new Setting("Log Party",    SettingType.BOOLEAN, true);
    private final Setting splitByNick = new Setting("Split By Nickname", SettingType.BOOLEAN, false);

    public ChatLogger() {
        super("ChatLogger");
        addSetting(logChat);
        addSetting(logSystem);
        addSetting(logGameInfo);
        addSetting(logColors);
        addSetting(logSent);
        addSetting(logParty);
        addSetting(splitByNick);
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
        initializeLogFile();
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        closeWriter();
    }

    /**
     * Папка для логов. Ник берётся напрямую из mc.player в момент вызова —
     * никакого кеширования, так что смена ника "на лету" (без релогина)
     * подхватывается сразу же на следующем сообщении.
     */
    private File resolveLogDir(boolean splitEnabled) {
        File base = new File(mc.mcDataDir, "MurderMysteryUtils/ChatLogger");
        if (!splitEnabled) {
            return base;
        }
        String nick = (mc.player != null) ? mc.player.getName() : null;
        if (nick == null || nick.isEmpty()) {
            nick = "Unknown";
        }
        return new File(base, sanitizeNick(nick));
    }

    /**
     * Убираем символы, недопустимые в именах папок на большинстве ОС,
     * на случай странных ников/прокси-серверов.
     */
    private String sanitizeNick(String nick) {
        return nick.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private void initializeLogFile() {
        boolean splitEnabled = (boolean) splitByNick.getValue();
        String currentDate = getCurrentDate();
        String nick = (mc.player != null) ? mc.player.getName() : "";

        currentLogDate = currentDate;
        currentLogNick = splitEnabled ? nick : "";

        try {
            File dir = resolveLogDir(splitEnabled);
            if (!dir.exists() && !dir.mkdirs()) {
                LOGGER.warn("Failed to create chat log directory: {}", dir.getAbsolutePath());
                return;
            }

            File logFile = new File(dir, currentDate + ".log");

            // Если файл уже существует, открываем в режиме добавления
            boolean isNewFile = !logFile.exists();
            writer = new PrintWriter(new FileWriter(logFile, true));

            if (isNewFile) {
                writer.println("=== Chat Log Started: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " ===");
                writer.flush();
            }

        } catch (IOException e) {
            LOGGER.error("Failed to initialize chat log file", e);
            if (writer != null) {
                writer.close();
                writer = null;
            }
        }
    }

    private void closeWriter() {
        if (writer != null) {
            writer.flush();
            writer.close();
            writer = null;
        }
    }

    // Метод для проверки и обновления файла при смене дня, смене ника
    // (логин на другой аккаунт ИЛИ смена ника без релогина) или переключении
    // настройки Split By Nickname. Ник читается заново каждый раз — без кеша.
    private void checkAndRotateLogFile() {
        boolean splitEnabled = (boolean) splitByNick.getValue();
        String currentDate = getCurrentDate();
        String nick = (mc.player != null) ? mc.player.getName() : "";
        String effectiveNick = splitEnabled ? nick : "";

        boolean dateChanged = !currentDate.equals(currentLogDate);
        boolean nickChanged = !effectiveNick.equals(currentLogNick);

        if (writer == null || dateChanged || nickChanged) {
            closeWriter(); // Закрываем старый writer если есть

            currentLogDate = currentDate;
            currentLogNick = effectiveNick;

            try {
                File dir = resolveLogDir(splitEnabled);
                if (!dir.exists() && !dir.mkdirs()) {
                    LOGGER.warn("Failed to create chat log directory: {}", dir.getAbsolutePath());
                    return;
                }

                File logFile = new File(dir, currentDate + ".log");

                boolean isNewFile = !logFile.exists();
                writer = new PrintWriter(new FileWriter(logFile, true));

                if (isNewFile) {
                    writer.println("=== Chat Log Started: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " ===");
                    writer.flush();
                }

            } catch (IOException e) {
                LOGGER.error("Failed to rotate chat log file", e);
                if (writer != null) {
                    writer.close();
                    writer = null;
                }
            }
        }
    }

    private String getCurrentDate() {
        return new SimpleDateFormat("dd.MM.yyyy").format(new Date());
    }

    private String getCurrentTimestamp() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date());
    }

    private void writeLog(String logEntry) {
        // Проверяем смену дня/ника/настройки перед каждой записью
        checkAndRotateLogFile();

        if (writer != null) {
            writer.println(logEntry);
            writer.flush();
        }
    }

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        if (event.getMessage() == null) return;

        ChatType type = event.getType();
        String formattedText = event.getMessage().getFormattedText();
        String unformattedText = event.getMessage().getUnformattedText();

        // Проверяем, является ли сообщение party сообщением (даже если это SYSTEM тип)
        boolean isPartyMessage = unformattedText.startsWith("Party ▸") || formattedText.contains("Party ▸");

        // Если это party сообщение и логирование party включено - логируем независимо от типа
        if (isPartyMessage && (boolean) logParty.getValue()) {
            String message = (boolean) logColors.getValue() ? formattedText : unformattedText;
            if (!message.isEmpty()) {
                String logEntry = String.format("[%s] [PARTY] %s", getCurrentTimestamp(), message);
                writeLog(logEntry);
            }
            return;
        }

        // Стандартная обработка для остальных сообщений
        if (type == ChatType.CHAT     && !(boolean) logChat.getValue())     return;
        if (type == ChatType.SYSTEM   && !(boolean) logSystem.getValue())   return;
        if (type == ChatType.GAME_INFO && !(boolean) logGameInfo.getValue()) return;

        String message = (boolean) logColors.getValue() ? formattedText : unformattedText;

        if (message.isEmpty()) return;

        String logEntry = String.format("[%s] [%s] %s", getCurrentTimestamp(), type.name(), message);
        writeLog(logEntry);
    }

    @SubscribeEvent
    public void onClientChatSent(net.minecraftforge.client.event.ClientChatEvent event) {
        if (!(boolean) logSent.getValue()) return;
        if (event.getMessage() == null || event.getMessage().isEmpty()) return;

        String message = event.getMessage();
        if (message.startsWith("/")) return;

        String logEntry = String.format("[%s] [SENT] [%s] %s", getCurrentTimestamp(), mc.player.getName(), message);
        writeLog(logEntry);
    }
}