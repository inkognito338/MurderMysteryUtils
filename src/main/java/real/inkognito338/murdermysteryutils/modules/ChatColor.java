package real.inkognito338.murdermysteryutils.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.CPacketChatMessage;
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

    private static ChatColor INSTANCE;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Setting debug;
    private final Setting mode;
    private BufferedWriter logWriter;
    private boolean logInitialized = false;
    private File currentLogFile;
    private boolean registered = false;

    private static final char[] COLOR_CODES = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    private static final String[] COLOR_NAMES = {
            "Black (&0)",       "Dark Blue (&1)",    "Dark Green (&2)", "Dark Aqua (&3)",
            "Dark Red (&4)",    "Dark Purple (&5)",  "Gold (&6)",       "Gray (&7)",
            "Dark Gray (&8)",   "Blue (&9)",         "Green (&a)",      "Aqua (&b)",
            "Red (&c)",         "Light Purple (&d)", "Yellow (&e)",     "White (&f)"
    };

    private static final String PREFIX_CHARS = "!@#$";

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

        addSetting(new Setting("ColorStyle", SettingType.MODE, "Auto",
                "Auto", "PerLetter", "PerWord"));

        addSetting(new Setting("Preset", SettingType.MODE, "None",
                "None", "Rainbow", "Fire", "Ice", "Pastel", "Neon", "Galaxy", "Sunset", "Candy"));

        for (int i = 0; i < COLOR_CODES.length; i++) {
            addSetting(new Setting(COLOR_NAMES[i], SettingType.BOOLEAN, true));
        }
    }

    public static ChatColor getInstance() {
        return INSTANCE;
    }

    public String getCurrentMode() {
        return (String) mode.getValue();
    }

    @Override
    public void onEnable() {
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(this);
            registered = true;
        }

        if ((boolean) debug.getValue()) {
            initLogFile();
        }

        logDebug("ChatColor enabled, mode: " + getCurrentMode());
        LOGGER.fine("ChatColor enabled, INSTANCE=" + INSTANCE + ", mode=" + getCurrentMode());
    }

    @Override
    public void onDisable() {
        if (registered) {
            MinecraftForge.EVENT_BUS.unregister(this);
            registered = false;
        }
        closeLogFile();
        logDebug("ChatColor disabled");
    }

    // ─── Обработка сообщения (вызывается из миксина на EntityPlayerSP#sendChatMessage) ──

    /**
     * Публичная точка входа для миксина. Работает со строкой ДО того,
     * как она превратится в CPacketChatMessage — так что перехватывается
     * любой путь отправки чата, независимо от того, как именно создаётся пакет.
     */
    public String processMessageFromMixin(String message) {
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

    // ─── Обработка пакета (устаревший путь, оставлен для совместимости) ──

    /**
     * @deprecated Используй processMessageFromMixin вместо этого — обработка на уровне
     * пакета менее надёжна, так как зависит от того, как именно создаётся CPacketChatMessage.
     */
    @Deprecated
    public void processPacketInPlace(CPacketChatMessage packet) {
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

    // ─── Логирование ───────────────────────────────────────────────────────

    private void closeLogFile() {
        try {
            if (logWriter != null) {
                logWriter.close();
                logWriter = null;
            }
            logInitialized = false;
            currentLogFile = null;
        } catch (IOException ignored) {}
    }

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

        if (!logInitialized && logWriter == null) {
            initLogFile();
        }

        if (logInitialized && logWriter != null) {
            try {
                String timestamp = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date());
                logWriter.write("[" + timestamp + "] " + text);
                logWriter.newLine();
                logWriter.flush();
            } catch (IOException ignored) {}
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private int getMaxLength() {
        Setting s = getSettingByName("ChatType");
        String modeVal = (s != null && s.getValue() instanceof String) ? (String) s.getValue() : "Auto";
        switch (modeVal) {
            case "1.10 (100 chars)":  return 100;
            case "1.11+ (255 chars)": return 256;
            default:
                try {
                    String ver = Minecraft.getMinecraft().getVersion();
                    if (ver.startsWith("1.8")) return 100;
                } catch (Exception ignored) {}
                return 256;
        }
    }

    // ─── Presets ────────────────────────────────────────────────────────────────

    private static final Map<String, char[]> PRESETS = new HashMap<>();
    static {
        // Радуга: полный спектр по порядку (не перемешивается — см. colorizeSmart)
        PRESETS.put("Rainbow", new char[]{'c', '6', 'e', 'a', 'b', '9', 'd'});
        // Огонь: красный, золото, жёлтый, тёмно-красный — энергичный, "пацанский"
        PRESETS.put("Fire", new char[]{'c', '6', 'e', '4'});
        // Лёд: синий, аква, тёмно-синий, белый — холодный, строгий
        PRESETS.put("Ice", new char[]{'9', 'b', '1', 'f'});
        // Пастель: мягкие светлые тона, нравится многим независимо от пола
        PRESETS.put("Pastel", new char[]{'d', 'b', 'e', 'f'});
        // Неон: яркие кислотные тона, молодёжный стиль
        PRESETS.put("Neon", new char[]{'a', 'd', 'b', 'e'});
        // Галактика: фиолетовый, тёмно-синий, аква — таинственный, космический
        PRESETS.put("Galaxy", new char[]{'5', 'd', '1', 'b'});
        // Закат: тёплый градиент от красного к жёлтому через фиолетовый
        PRESETS.put("Sunset", new char[]{'4', 'c', '6', 'd'});
        // Конфеты: розовый, красный, жёлтый, белый — яркий и "милый"
        PRESETS.put("Candy", new char[]{'d', 'c', 'e', 'f'});
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

    /**
     * Стейтфул-хелпер для выдачи цветов по кругу в ОДНОМ И ТОМ ЖЕ порядке.
     * Порядок перемешивается один раз при создании (случайно), а дальше
     * просто циклически повторяется: например красный-зелёный-синий-
     * красный-зелёный-синий-... на всё сообщение.
     */
    private static final class ColorRotator {
        private final List<Character> order;
        private int index = 0;

        ColorRotator(List<Character> available, Random random, boolean shuffle) {
            this.order = new ArrayList<>(available);
            if (shuffle) {
                Collections.shuffle(this.order, random);
            }
        }

        private ColorRotator(List<Character> fixedOrder) {
            this.order = fixedOrder;
        }

        /** Создаёт новый ротатор с уже готовым (зафиксированным) порядком цветов, index сбрасывается на 0. */
        static ColorRotator fromOrder(List<Character> fixedOrder) {
            return new ColorRotator(fixedOrder);
        }

        /** Возвращает копию текущего порядка цветов (для повторных независимых проходов). */
        List<Character> snapshotOrder() {
            return new ArrayList<>(order);
        }

        char next() {
            char chosen = order.get(index);
            index = (index + 1) % order.size();
            return chosen;
        }
    }

    private String getColorStyle() {
        Setting s = getSettingByName("ColorStyle");
        return (s != null && s.getValue() instanceof String) ? (String) s.getValue() : "Auto";
    }

    private String colorizeSmart(String text, int availableSpace) {
        List<Character> active = getActiveColors();
        if (active.isEmpty() || text.isEmpty()) return text;

        Random random = new Random();
        boolean shuffleOrder = !"Rainbow".equals(getPreset());
        ColorRotator rotator = new ColorRotator(active, random, shuffleOrder);

        String style = getColorStyle();

        if ("PerWord".equals(style)) {
            return colorizePerWord(text, availableSpace, rotator);
        }

        // Считаем доступное пространство для цветовых кодов (&X -> 2 символа),
        // используя урезанную по availableSpace длину — это релевантно только
        // для посимвольного режима, где 1 буква = 1 код.
        String forLetterCheck = text.length() > availableSpace ? text.substring(0, availableSpace) : text;
        int freeSpace = availableSpace - forLetterCheck.length();
        int maxCodes = freeSpace / 2;

        if ("PerLetter".equals(style)) {
            return colorizePerLetter(text, availableSpace, rotator);
        }

        // "Auto": посимвольно, если хватает места под коды, иначе по словам
        if (maxCodes >= forLetterCheck.length()) {
            return colorizePerLetter(text, availableSpace, rotator);
        }
        return colorizePerWord(text, availableSpace, rotator);
    }

    /**
     * Красит каждую букву индивидуально по очереди из ротатора.
     * Пробелы цвет не получают и не сдвигают счётчик ротатора.
     */
    private String colorizePerLetter(String text, int availableSpace, ColorRotator rotator) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == ' ') {
                if (result.length() + 1 <= availableSpace) {
                    result.append(c);
                }
                continue;
            }

            char color = rotator.next();
            String piece = "&" + color + c;
            if (result.length() + piece.length() <= availableSpace) {
                result.append(piece);
            } else {
                break;
            }
        }
        return result.toString();
    }

    /**
     * Красит текст по словам, группируя несколько слов подряд под один цвет.
     * Чем больше слов в группе — тем меньше всего цветовых кодов &X в тексте,
     * тем больше места остаётся под сами буквы. Алгоритм подбирает минимальный
     * размер группы (начиная с 1 слову = 1 цвет), при котором весь текст
     * помещается в availableSpace — то есть уменьшает КОЛИЧЕСТВО цветовых
     * кодов, а не сами слова. Слова никогда не обрезаются, кроме полного
     * провала (текста физически больше лимита даже без единого цвета).
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
                return candidate;
            }
        }

        // Даже один цвет на всё сообщение не помог уместиться —
        // текста физически больше лимита, обрезаем по буквам (крайний случай).
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
     * обрезаем по буквам с конца, красим первым доступным цветом целиком.
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

    // ─── Core processing ──────────────────────────────────────────────────────

    /**
     * Сделан публичным, чтобы вызываться напрямую как из ClientChatEvent,
     * так и из миксина на sendChatMessage.
     */
    public String processChatMessage(String originalMessage) {
        if (originalMessage == null || originalMessage.isEmpty()) return originalMessage;

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

    // ─── Event: ClientChatEvent (для режима ClientChat) ─────────────────────

    @SubscribeEvent
    public void onClientChat(ClientChatEvent event) {
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
}