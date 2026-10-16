package real.inkognito338.murdermysteryutils.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.CPacketChatMessage;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import real.inkognito338.murdermysteryutils.Main;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

@SideOnly(Side.CLIENT)
public class ChatColor extends Module {

    private static final Logger LOGGER = Logger.getLogger(ChatColor.class.getName());
    private static final char[] COLOR_CODES = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };
    private static final String[] COLOR_NAMES = {
            "Black (&0)", "Dark Blue (&1)", "Dark Green (&2)", "Dark Aqua (&3)",
            "Dark Red (&4)", "Dark Purple (&5)", "Gold (&6)", "Gray (&7)",
            "Dark Gray (&8)", "Blue (&9)", "Green (&a)", "Aqua (&b)",
            "Red (&c)", "Light Purple (&d)", "Yellow (&e)", "White (&f)"
    };
    private static final String PREFIX_CHARS = "!@#$";
    private static final Map<String, char[]> PRESETS = new HashMap<>();
    private static ChatColor INSTANCE;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Setting debug;
    private final Setting mode;
    private BufferedWriter logWriter;
    private boolean logInitialized = false;
    private File currentLogFile;
    private boolean registered = false;
    /**
     * Флаг активности модуля. Нужен, потому что режим Packet работает через миксин
     * и прямые вызовы processXxx, которые не зависят от регистрации на EVENT_BUS.
     */
    private volatile boolean active = false;

    static {
        // Лёд: синий → аква → тёмно-синий → белый
        PRESETS.put("Galaxy", new char[]{'5', 'd', '1', 'b'});
        // Закат: тёмно-красный → красный → золото → жёлтый
        PRESETS.put("Sunset", new char[]{'4', 'c', '6', 'e'});
        // Призрак: тёмно-серый → серый → белый → аква
        PRESETS.put("Ghost", new char[]{'8', '7', 'f', 'b'});
        // Гроза: жёлтый → белый → аква → тёмно-аква
        PRESETS.put("Thunder", new char[]{'e', 'f', 'b', '3'});
        // Кровь: красный → тёмно-красный → тёмно-серый
        PRESETS.put("Cyberpunk", new char[]{'a', 'b', '3', 'd', 'e', '6'});

        // ─── Новые ───
        // Неон: зелёный → аква → розовый → жёлтый
        PRESETS.put("Neon", new char[]{'a', 'b', 'd', 'e'});
        // Океан: тёмно-синий → синий → тёмно-аква → аква
        PRESETS.put("Ocean", new char[]{'1', '9', '3', 'b'});
        // Токсик: тёмно-зелёный → зелёный → жёлтый → тёмно-серый
        PRESETS.put("Toxic", new char[]{'2', 'a', 'e', '8'});
        // Мята: тёмно-зелёный → зелёный → белый → аква (свежий, холодный)
        PRESETS.put("Mint", new char[]{'2', 'a', 'f', 'b'});

        PRESETS.put("Rainbow", new char[]{'c', 'b', 'd', 'e', 'a', '3', '6'});
        // Конфетка: розовый → белый → аква → жёлтый
        PRESETS.put("Candy", new char[]{'d', 'f', 'b', 'e'});
    }

    public ChatColor() {
        super("ChatColor");
        INSTANCE = this;

        debug = new Setting("Debug", SettingType.BOOLEAN, false);
        addSetting(debug);

        mode = new Setting("InterceptMode", SettingType.MODE, "ClientChat",
                new String[]{"ClientChat", "Packet"}
        );
        addSetting(mode);

        addSetting(new Setting("ChatType", SettingType.MODE, "Auto",
                "Auto", "1.10 (100 chars)", "1.11+ (255 chars)"));

        addSetting(new Setting("ColorStyle", SettingType.MODE, "PerLetter",
                new String[]{"PerLetter", "PerWord"}
        ));

        addSetting(new Setting("Sequence", SettingType.MODE, "Random",
                new String[]{"Random", "Static"}
        ));
        addSetting(new Setting("Preset", SettingType.MODE, "None",
                new String[]{"None", "Cyberpunk", "Thunder", "Galaxy",
                        "Sunset", "Ghost", "Neon", "Ocean", "Toxic", "Mint", "Rainbow", "Candy"}
        ));
        for (int i = 0; i < COLOR_CODES.length; i++) {
            addSetting(new Setting(COLOR_NAMES[i], SettingType.BOOLEAN, true));
        }
    }

    public static ChatColor getInstance() {
        return INSTANCE;
    }

    /** Красит текст §-кодами (как в чате): по буквам или по словам. */
    private static String paint(String text, ColorRotator rotator, boolean perWord) {
        StringBuilder sb = new StringBuilder();
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                sb.append(c);
                inWord = false;
                continue;
            }
            if (!perWord || !inWord) {
                sb.append('§').append(rotator.next());
            }
            inWord = true;
            sb.append(c);
        }
        return sb.toString();
    }

    /** Пример текста с текущими настройками (Static-порядок, чтобы превью не мигало). */
    public String buildPreview(String text) {
        List<Character> colors = getActiveColors();
        if (colors.isEmpty()) return text;
        ColorRotator r = new ColorRotator(colors, new Random(), false);
        return paint(text, r, "PerWord".equals(getColorStyle()));
    }

    /** Название пресета, покрашенное его собственными цветами. "None" остаётся как есть. */
    public static String presetNameColored(String preset) {
        char[] p = PRESETS.get(preset);
        if (p == null) return preset;
        List<Character> list = new ArrayList<>();
        for (char c : p) list.add(c);
        return paint(preset, new ColorRotator(list, new Random(), false), false);
    }

    public String getCurrentMode() {
        return (String) mode.getValue();
    }

    private String getSequence() {
        Setting s = getSettingByName("Sequence");
        return (s != null && s.getValue() instanceof String) ? (String) s.getValue() : "Random";
    }

    /**
     * Включён ли модуль. Миксин должен проверять это перед вызовом processMessageFromMixin.
     */
    public boolean isActive() {
        return active;
    }

    // ─── Обработка сообщения (вызывается из миксина на EntityPlayerSP#sendChatMessage) ──

    @Override
    public void onEnable() {
        active = true;

        if (!registered) {
            MinecraftForge.EVENT_BUS.register(this);
            registered = true;
        }

        if ((boolean) debug.getValue()) {
            initLogFile();
        }

        logDebug("ChatColor enabled, mode: " + getCurrentMode());
    }

    // ─── Обработка пакета (устаревший путь, оставлен для совместимости) ──

    @Override
    public void onDisable() {
        // Сначала гасим флаг — после этого все публичные точки входа становятся no-op
        active = false;

        if (registered) {
            MinecraftForge.EVENT_BUS.unregister(this);
            registered = false;
        }

        // Сначала пишем в лог, потом закрываем файл:
        // иначе logDebug после closeLogFile() заново создаст лог-файл
        logDebug("ChatColor disabled");
        closeLogFile();
    }

    /**
     * Публичная точка входа для миксина. Работает со строкой ДО того,
     * как она превратится в CPacketChatMessage — так что перехватывается
     * любой путь отправки чата, независимо от того, как именно создаётся пакет.
     * <p>
     * Работает только когда модуль включён и выбран режим Packet.
     * В режиме ClientChat сообщение обрабатывает onClientChat.
     */
    public String processMessageFromMixin(String message) {
        if (!active) {
            return message;
        }

        if (!"Packet".equals(getCurrentMode())) {
            return message;
        }

        LOGGER.fine("processMessageFromMixin: original=\"" + message + "\"");

        if (message == null || message.isEmpty()) {
            return message;
        }

        if (message.startsWith("/") || message.startsWith(".") || message.startsWith("-")) {
            logDebug("Mixin: ignored command: " + message);
            LOGGER.fine("Ignored command: " + message);
            return message;
        }

        // Защита от повторной покраски при рекурсивном self-вызове sendChatMessage
        if (message.matches(".*&[0-9a-fA-F].*")) {
            logDebug("Mixin: message already colored, skipping to avoid double-processing: " + message);
            LOGGER.fine("Already colored, skipping to avoid double-processing");
            return message;
        }

        logDebug("Mixin intercepted: " + message);

        String processed = processChatMessage(message);
        LOGGER.fine("processMessageFromMixin: processed=\"" + processed + "\"");

        return processed;
    }

    // ─── Логирование ───────────────────────────────────────────────────────

    /**
     * @deprecated Используй processMessageFromMixin вместо этого — обработка на уровне
     * пакета менее надёжна, так как зависит от того, как именно создаётся CPacketChatMessage.
     */
    @Deprecated
    public void processPacketInPlace(CPacketChatMessage packet) {
        if (!active) return;
        if (!"Packet".equals(getCurrentMode())) return;

        try {
            Field messageField = CPacketChatMessage.class.getDeclaredField("message");
            messageField.setAccessible(true);

            String originalMessage = (String) messageField.get(packet);
            LOGGER.fine("processPacketInPlace: original=" + originalMessage);

            if (originalMessage == null || originalMessage.isEmpty()) {
                logDebug("Packet: empty message, skipping");
                return;
            }

            if (originalMessage.startsWith("/") || originalMessage.startsWith(".") || originalMessage.startsWith("-")) {
                logDebug("Packet: ignored command: " + originalMessage);
                return;
            }

            logDebug("Packet intercepted (in-place): " + originalMessage);

            String processed = processChatMessage(originalMessage);
            if (processed != null && !processed.isEmpty() && !processed.equals(originalMessage)) {
                messageField.set(packet, processed);
                logDebug("Packet field replaced in-place: " + processed + " (length: " + processed.length() + ")");
                LOGGER.fine("processPacketInPlace: replaced with=" + processed);
            } else {
                logDebug("Packet: nothing changed, keeping original");
                LOGGER.fine("processPacketInPlace: nothing changed");
            }

        } catch (Exception e) {
            logDebug("Error processing packet in-place: " + e.getMessage());
            LOGGER.log(Level.WARNING, "processPacketInPlace error", e);
        }
    }

    @Deprecated
    public CPacketChatMessage processPacket(CPacketChatMessage packet) {
        if (!active) return packet;
        if (!"Packet".equals(getCurrentMode())) return packet;

        try {
            Field messageField = CPacketChatMessage.class.getDeclaredField("message");
            messageField.setAccessible(true);

            String originalMessage = (String) messageField.get(packet);
            if (originalMessage == null || originalMessage.isEmpty()) {
                return packet;
            }

            if (originalMessage.startsWith("/") || originalMessage.startsWith(".") || originalMessage.startsWith("-")) {
                logDebug("Packet: Ignored command: " + originalMessage);
                return packet;
            }

            logDebug("Packet intercepted: " + originalMessage);

            String processed = processChatMessage(originalMessage);
            if (processed != null && !processed.isEmpty() && !processed.equals(originalMessage)) {
                CPacketChatMessage newPacket = new CPacketChatMessage(processed);
                logDebug("Packet replaced: " + processed + " (length: " + processed.length() + ")");
                return newPacket;
            }

        } catch (Exception e) {
            logDebug("Error processing packet: " + e.getMessage());
            LOGGER.log(Level.WARNING, "processPacket error", e);
        }

        return packet;
    }

    private void closeLogFile() {
        try {
            if (logWriter != null) {
                logWriter.close();
                logWriter = null;
            }
            logInitialized = false;
            currentLogFile = null;
        } catch (IOException ignored) {
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private void initLogFile() {
        try {
            File logDir = new File(Main.getConfigDir(), "logs");
            if (!logDir.exists()) {
                if (!logDir.mkdirs()) {
                    if (mc.player != null) {
                        mc.player.sendMessage(new TextComponentString("§7[§6ChatColor§7] §cFailed to create log directory"));
                    }
                    return;
                }
            }

            String timestamp = new SimpleDateFormat("dd.MM.yyyy_HH-mm-ss").format(new Date());
            currentLogFile = new File(logDir, "chat_color_debug_" + timestamp + ".log");
            logWriter = new BufferedWriter(new FileWriter(currentLogFile, true));
            logInitialized = true;

            if (mc.player != null) {
                ITextComponent message = new TextComponentString("§7[§6ChatColor§7] §aLog file created: §f");

                ITextComponent fileLink = new TextComponentString(currentLogFile.getName());
                fileLink.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, currentLogFile.getAbsolutePath()));
                fileLink.getStyle().setUnderlined(true);

                ITextComponent folderLink = new TextComponentString(" §7(§aОткрыть папку§7)");
                folderLink.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, currentLogFile.getParentFile().getAbsolutePath()));
                folderLink.getStyle().setUnderlined(true);

                message.appendSibling(fileLink);
                message.appendSibling(folderLink);

                mc.player.sendMessage(message);
            }
        } catch (IOException e) {
            if (mc.player != null) {
                mc.player.sendMessage(new TextComponentString("§7[§6ChatColor§7] §cFailed to create log file: " + e.getMessage()));
            }
        }
    }

    private void logDebug(String text) {
        if (!((boolean) debug.getValue())) return;

        // Не создаём лог-файл заново, если модуль выключен
        if (!active && !logInitialized) return;

        if (!logInitialized && logWriter == null) {
            initLogFile();
        }

        if (logInitialized && logWriter != null) {
            try {
                String timestamp = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date());
                logWriter.write("[" + timestamp + "] " + text);
                logWriter.newLine();
                logWriter.flush();
            } catch (IOException ignored) {
            }
        }
    }

    // ─── Presets ────────────────────────────────────────────────────────────────

    private int getMaxLength() {
        Setting s = getSettingByName("ChatType");
        String modeVal = (s != null && s.getValue() instanceof String) ? (String) s.getValue() : "Auto";
        switch (modeVal) {
            case "1.10 (100 chars)":
                return 100;
            case "1.11+ (255 chars)":
                return 256;
            default:
                return detectMaxLengthByTeam();
        }
    }

    /**
     * Auto: определяем версию по названию team игрока.
     * Последняя буква названия == 'Q' -> 1.11+ (256), иначе 100.
     * Если team нет или произошла ошибка — безопасные 100
     * (превышение лимита на 1.10 приводит к отклонению сообщения).
     */
    private int detectMaxLengthByTeam() {
        try {
            if (mc.player == null) return 100;

            Team team = mc.player.getTeam();
            if (team == null) {
                logDebug("Auto: player has no team, fallback to 100");
                return 100;
            }

            String teamName = team.getName();
            if (teamName == null || teamName.isEmpty()) {
                logDebug("Auto: empty team name, fallback to 100");
                return 100;
            }

            char last = teamName.charAt(teamName.length() - 1);
            boolean modern = (last == 'Q');
            logDebug("Auto: team=\"" + teamName + "\", last='" + last + "' -> "
                    + (modern ? "1.11+ (256)" : "1.10 (100)"));
            return modern ? 256 : 100;
        } catch (Exception e) {
            logDebug("Auto: team detection error: " + e.getMessage());
            return 100;
        }
    }

    private String getPreset() {
        Setting s = getSettingByName("Preset");
        return (s != null && s.getValue() instanceof String) ? (String) s.getValue() : "None";
    }

    private List<Character> getActiveColors() {
        String preset = getPreset();
        char[] presetColors = PRESETS.get(preset);

        if (presetColors != null) {
            List<Character> active = new ArrayList<>();
            for (char c : presetColors) {
                active.add(c);
            }
            return active;
        }

        // "None" — свой набор цветов, берём цвета по включённым чекбоксам
        List<Character> active = new ArrayList<>();
        for (int i = 0; i < COLOR_CODES.length; i++) {
            Setting s = getSettingByName(COLOR_NAMES[i]);
            if (s != null && Boolean.TRUE.equals(s.getValue())) {
                active.add(COLOR_CODES[i]);
            }
        }
        return active;
    }

    private String getColorStyle() {
        Setting s = getSettingByName("ColorStyle");
        return (s != null && s.getValue() instanceof String) ? (String) s.getValue() : "PerLetter";
    }

    /**
     * PerWord   — цвет делится по словам.
     * PerLetter — цвет делится по буквам (любое другое значение, в том числе
     *             устаревшее "Auto" из старых конфигов, трактуется как PerLetter).
     * В обоих режимах, если сообщение не влезает в лимит, группа (букв или слов)
     * на один цвет растёт, пока всё не поместится.
     */
    private String colorizeSmart(String text, int availableSpace) {
        List<Character> active = getActiveColors();
        if (active.isEmpty() || text.isEmpty()) return text;

        Random random = new Random();
        boolean shuffleOrder = "Random".equals(getSequence());
        ColorRotator rotator = new ColorRotator(active, random, shuffleOrder);

        if ("PerWord".equals(getColorStyle())) {
            return colorizePerWord(text, availableSpace, rotator);
        }
        return colorizePerLetter(text, availableSpace, rotator);
    }

    /**
     * PerLetter: пробует группы по 1, 2, 3... буквы на один цвет и берёт первую,
     * которая влезает в availableSpace. Пробелы цвет не получают и в группу не считаются.
     * Если не влезает даже с одним цветом на всё сообщение, обрезаем (крайний случай).
     */
    private String colorizePerLetter(String text, int availableSpace, ColorRotator rotator) {
        int letters = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != ' ') letters++;
        }
        if (letters == 0) return text;

        List<Character> colorOrder = rotator.snapshotOrder();

        for (int groupSize = 1; groupSize <= letters; groupSize++) {
            ColorRotator attempt = ColorRotator.fromOrder(colorOrder);
            String candidate = buildLetterGroups(text, groupSize, attempt);
            if (candidate.length() <= availableSpace) {
                logDebug("PerLetter: letters per color = " + groupSize);
                return candidate;
            }
        }

        // Даже один цвет на всё не помещается, значит сам текст длиннее лимита
        String candidate = buildLetterGroups(text, letters, ColorRotator.fromOrder(colorOrder));
        return candidate.length() > availableSpace ? candidate.substring(0, availableSpace) : candidate;
    }

    /**
     * Красит буквы группами по groupSize штук: первые groupSize букв одним цветом,
     * следующие groupSize следующим и т.д. Пробелы остаются как есть
     * (цветовой код действует и через пробел, отдельный код на него не нужен).
     */
    private String buildLetterGroups(String text, int groupSize, ColorRotator rotator) {
        StringBuilder result = new StringBuilder();
        int inGroup = 0;
        boolean haveColor = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == ' ') {
                result.append(c);
                continue;
            }

            if (!haveColor || inGroup >= groupSize) {
                result.append('&').append(rotator.next());
                haveColor = true;
                inGroup = 0;
            }
            result.append(c);
            inGroup++;
        }
        return result.toString();
    }

    /**
     * PerWord: красит текст по словам, группируя несколько слов подряд под один цвет.
     * Чем больше слов в группе — тем меньше всего цветовых кодов &X в тексте,
     * тем больше места остаётся под сами буквы. Алгоритм подбирает минимальный
     * размер группы (начиная с 1 слово = 1 цвет), при котором весь текст
     * помещается в availableSpace. Слова никогда не обрезаются, кроме полного
     * провала (текста физически больше лимита даже с одним цветом).
     */
    private String colorizePerWord(String text, int availableSpace, ColorRotator rotator) {
        String[] words = text.split(" ", -1);
        if (words.length == 0) return "";

        int wordCount = 0;
        for (String w : words) {
            if (!w.isEmpty()) wordCount++;
        }
        if (wordCount == 0) return text;

        // Пробуем размер группы от 1 (цвет на каждое слово) и увеличиваем,
        // пока текст не влезет — так количество кодов &X плавно уменьшается.
        // На каждую попытку берём цвета заново с начала того же порядка,
        // чтобы разные groupSize не сбивали друг друга.
        List<Character> colorOrder = rotator.snapshotOrder();
        for (int groupSize = 1; groupSize <= wordCount; groupSize++) {
            ColorRotator attempt = ColorRotator.fromOrder(colorOrder);
            String candidate = buildGrouped(words, groupSize, attempt);
            if (candidate.length() <= availableSpace) {
                logDebug("PerWord: words per color = " + groupSize);
                return candidate;
            }
        }

        // Даже один цвет на всё сообщение не помог уместиться —
        // текста физически больше лимита, обрезаем (крайний случай).
        return buildTruncated(words, availableSpace);
    }

    /**
     * Красит слова группами по groupSize: первые groupSize слов — один цвет,
     * следующие groupSize — следующий цвет по кругу, и так далее.
     */
    private String buildGrouped(String[] words, int groupSize, ColorRotator rotator) {
        StringBuilder result = new StringBuilder();
        int wordIndexInGroup = 0;
        char currentColor = 0;
        boolean haveColor = false;

        for (int w = 0; w < words.length; w++) {
            String word = words[w];

            if (!word.isEmpty()) {
                if (!haveColor || wordIndexInGroup >= groupSize) {
                    currentColor = rotator.next();
                    haveColor = true;
                    wordIndexInGroup = 0;
                }
                result.append('&').append(currentColor).append(word);
                wordIndexInGroup++;
            }

            if (w < words.length - 1) {
                result.append(' ');
            }
        }
        return result.toString();
    }

    /**
     * Крайний случай: даже один цвет на всё сообщение не помог уместиться —
     * обрезаем по буквам с конца.
     */
    private String buildTruncated(String[] words, int availableSpace) {
        StringBuilder result = new StringBuilder();
        for (int w = 0; w < words.length; w++) {
            String word = words[w];
            boolean hasMoreWords = w < words.length - 1;

            if (!word.isEmpty()) {
                if (result.length() + word.length() <= availableSpace) {
                    result.append(word);
                } else {
                    int room = availableSpace - result.length();
                    if (room > 0) {
                        result.append(word, 0, room);
                    }
                    break;
                }
            }

            if (hasMoreWords && result.length() + 1 <= availableSpace) {
                result.append(' ');
            } else if (hasMoreWords) {
                break;
            }
        }
        return result.toString();
    }

    /**
     * Сделан публичным, чтобы вызываться напрямую как из ClientChatEvent,
     * так и из миксина на sendChatMessage.
     */
    public String processChatMessage(String originalMessage) {
        if (originalMessage == null || originalMessage.isEmpty()) return originalMessage;
        if (!active) return originalMessage;

        char first = originalMessage.charAt(0);
        if (first == '/' || first == '.' || first == '-') {
            logDebug("Ignored command/prefix message: " + originalMessage);
            return originalMessage;
        }

        String prefix = "";
        String body = originalMessage;

        if (PREFIX_CHARS.indexOf(first) >= 0) {
            prefix = String.valueOf(first);
            body = originalMessage.substring(1);
        }

        if (body.isEmpty()) {
            logDebug("Empty body, returning raw");
            return originalMessage;
        }

        int maxLen = getMaxLength();
        int availableSpace = maxLen - prefix.length();

        String coloredBody = colorizeSmart(body, availableSpace);
        String finalMessage = prefix + coloredBody;

        if (finalMessage.length() > maxLen) {
            finalMessage = finalMessage.substring(0, maxLen);
        }

        logDebug("Original: " + originalMessage + " | Final (" + finalMessage.length() + "/" + maxLen + "): " + finalMessage);
        return finalMessage;
    }

    // ─── Core processing ──────────────────────────────────────────────────────

    @SubscribeEvent
    public void onClientChat(ClientChatEvent event) {
        if (!active) return;

        String currentMode = getCurrentMode();
        if (!"ClientChat".equals(currentMode)) {
            logDebug("Skipping ClientChatEvent, mode: " + currentMode);
            return;
        }

        String msg = event.getMessage();
        if (msg == null || msg.isEmpty()) return;

        char first = msg.charAt(0);
        if (first == '/' || first == '.' || first == '-') {
            logDebug("Ignored command/prefix message: " + msg);
            return;
        }

        event.setCanceled(true);
        mc.ingameGUI.getChatGUI().addToSentMessages(msg);

        String processed = processChatMessage(msg);
        if (processed != null && !processed.isEmpty()) {
            mc.player.sendChatMessage(processed);
        }
    }

    // ─── Event: ClientChatEvent (для режима ClientChat) ─────────────────────

    /**
     * Для миксина/хендлеров: модуль включён и выбран режим Packet.
     */
    public boolean isPacketModeActive() {
        return active && "Packet".equals((String) mode.getValue());
    }

    /**
     * Стейтфул-хелпер для выдачи цветов по кругу в ОДНОМ И ТОМ ЖЕ порядке.
     * Порядок (при Random) перемешивается один раз при создании, а дальше
     * циклически повторяется. Соседние выданные цвета никогда не совпадают
     * (если в наборе больше одного уникального цвета).
     */
    private static final class ColorRotator {
        private final List<Character> order;
        private final int distinctCount;
        private int index = 0;
        private char last = 0;
        private boolean hasLast = false;

        ColorRotator(List<Character> available, Random random, boolean shuffle) {
            this.order = new ArrayList<>(available);
            if (shuffle) {
                Collections.shuffle(this.order, random);
            }
            this.distinctCount = new HashSet<>(this.order).size();
        }

        private ColorRotator(List<Character> fixedOrder) {
            this.order = fixedOrder;
            this.distinctCount = new HashSet<>(fixedOrder).size();
        }

        /**
         * Создаёт новый ротатор с уже готовым (зафиксированным) порядком цветов, index сбрасывается на 0.
         */
        static ColorRotator fromOrder(List<Character> fixedOrder) {
            return new ColorRotator(fixedOrder);
        }

        /**
         * Возвращает копию текущего порядка цветов (для повторных независимых проходов).
         */
        List<Character> snapshotOrder() {
            return new ArrayList<>(order);
        }

        /**
         * Следующий цвет по кругу. Если он совпал с предыдущим выданным,
         * пропускаем его (возможно только при повторах в списке или на стыке цикла).
         * Если в наборе всего один уникальный цвет, повтора не избежать.
         */
        char next() {
            char chosen;
            do {
                chosen = order.get(index);
                index = (index + 1) % order.size();
            } while (distinctCount > 1 && hasLast && chosen == last);

            last = chosen;
            hasLast = true;
            return chosen;
        }
    }
}