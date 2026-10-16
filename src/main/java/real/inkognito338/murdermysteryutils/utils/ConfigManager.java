package real.inkognito338.murdermysteryutils.utils;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import real.inkognito338.murdermysteryutils.Main;
import real.inkognito338.murdermysteryutils.modules.Scripts;
import real.inkognito338.murdermysteryutils.utils.gui.ThemeManager;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

@SuppressWarnings("SpellCheckingInspection")
public class ConfigManager {
    private static final Logger LOGGER = Logger.getLogger(ConfigManager.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Set<String> resetMessages = new HashSet<>();
    private static final File SPAM_FILE = new File(Main.getConfigFile().getParentFile(), "spam.txt");

    private static boolean staticItems = false;
    private static String currentThemeName = "Midnight";

    private static boolean initialized = false;

    // Произвольные настройки (используются, например, AutoAddServer) - сохраняются в конфиг
    private static final Map<String, Boolean> booleanSettings = new ConcurrentHashMap<>();
    private static final Map<String, String> stringSettings = new ConcurrentHashMap<>();
    private static final Map<String, Integer> intSettings = new ConcurrentHashMap<>();
    private static final Map<String, Long> longSettings = new ConcurrentHashMap<>();
    private static final File TIMEZONE_CONFIG_FILE = new File(Minecraft.getMinecraft().mcDataDir, "timezoneconfig.cfg");

    /**
     * Инициализация: создаёт нужные файлы/папки.
     * НЕ загружает настройки — вызывать в preInit, когда модули ещё не зарегистрированы.
     */
    public static void init() {
        if (initialized) return;
        initialized = true;

        File configDir = Main.getConfigFile().getParentFile();
        if (!configDir.exists() && !configDir.mkdirs()) {
            LOGGER.log(Level.SEVERE, "Failed to create config directory: " + configDir.getAbsolutePath());
        }

        createSpamFileIfNeeded();
    }

    public static boolean isStaticItems() {
        return staticItems;
    }

    public static void setStaticItems(boolean value) {
        staticItems = value;
        save();
        saveTimezoneConfigState(value);
    }

    public static String getTheme() {
        return currentThemeName;
    }

    public static void setTheme(String themeName) {
        currentThemeName = themeName;
        ThemeManager.loadThemeByName(themeName);
        save();
    }

    public static void saveThemeName(String themeName) {
        currentThemeName = themeName;
        save();
    }

    // ===================== БЕЗОПАСНАЯ ЗАПИСЬ / ЧТЕНИЕ =====================

    private static File getBackupFile() {
        File cfg = Main.getConfigFile();
        return new File(cfg.getParentFile(), cfg.getName() + ".bak");
    }

    /** Пишет файл атомарно: tmp -> fsync -> move. Файл не может остаться наполовину записанным. */
    private static void writeAtomically(File target, String content) throws IOException {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");

        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            fos.flush();
            fos.getFD().sync(); // гарантируем, что данные физически на диске
        }

        try {
            Files.move(tmp.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Читает JSON-объект из файла. Возвращает null, если файла нет, он пустой или повреждён. */
    private static JsonObject readJsonSafe(File file) {
        if (!file.exists() || file.length() == 0) return null;
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            return GSON.fromJson(text, JsonObject.class);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOGGER.log(Level.WARNING, "Config file is unreadable: " + file.getName(), e);
            return null;
        }
    }

    /** Откладывает повреждённый файл в сторону, чтобы его не перезаписали. */
    private static void quarantineCorrupted(File file) {
        if (!file.exists()) return;
        File dest = new File(file.getParentFile(), file.getName() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.move(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            LOGGER.warning("Corrupted config moved to " + dest.getName());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to quarantine corrupted config", e);
        }
    }

    // ===================== SAVE =====================

    public static synchronized void save() {
        JsonObject json = new JsonObject();

        json.addProperty("staticItems", staticItems);
        json.addProperty("theme", currentThemeName);
        json.addProperty("mirror", MirrorManager.getCurrent().id);

        // Произвольные настройки (boolean/string/int/long)
        JsonObject customObj = new JsonObject();

        JsonObject boolObj = new JsonObject();
        for (Map.Entry<String, Boolean> e : booleanSettings.entrySet()) {
            boolObj.addProperty(e.getKey(), e.getValue());
        }
        customObj.add("boolean", boolObj);

        JsonObject strObj = new JsonObject();
        for (Map.Entry<String, String> e : stringSettings.entrySet()) {
            strObj.addProperty(e.getKey(), e.getValue());
        }
        customObj.add("string", strObj);

        JsonObject intObj = new JsonObject();
        for (Map.Entry<String, Integer> e : intSettings.entrySet()) {
            intObj.addProperty(e.getKey(), e.getValue());
        }
        customObj.add("int", intObj);

        JsonObject longObj = new JsonObject();
        for (Map.Entry<String, Long> e : longSettings.entrySet()) {
            longObj.addProperty(e.getKey(), e.getValue());
        }
        customObj.add("long", longObj);

        json.add("custom", customObj);

        JsonObject modulesObj = new JsonObject();
        for (Module module : ModuleManager.getModules()) {
            JsonObject moduleObj = new JsonObject();
            moduleObj.addProperty("enabled", module.isToggled());

            JsonObject settingsObj = new JsonObject();
            for (Setting setting : module.getSettings()) {
                Object value = setting.getValue();
                if (value instanceof Boolean) {
                    settingsObj.addProperty(setting.getName(), (Boolean) value);
                } else if (value instanceof Number) {
                    if (value instanceof Float) {
                        settingsObj.addProperty(setting.getName(), ((Float) value).doubleValue());
                    } else if (value instanceof Double) {
                        settingsObj.addProperty(setting.getName(), (Double) value);
                    } else if (value instanceof Integer) {
                        settingsObj.addProperty(setting.getName(), ((Integer) value).doubleValue());
                    }
                } else if (value instanceof String) {
                    settingsObj.addProperty(setting.getName(), (String) value);
                } else if (value instanceof float[]) {
                    float[] arr = (float[]) value;
                    JsonArray array = new JsonArray();
                    for (float f : arr) {
                        array.add(new JsonPrimitive(f));
                    }
                    settingsObj.add(setting.getName(), array);
                }
            }
            moduleObj.add("settings", settingsObj);

            // Отдельно сохраняем состояние включённости каждого скрипта модуля Scripts
            if (module instanceof Scripts) {
                JsonObject scriptStatesObj = new JsonObject();
                Map<String, Boolean> states = ((Scripts) module).getScriptEnabledStates();
                for (Map.Entry<String, Boolean> entry : states.entrySet()) {
                    scriptStatesObj.addProperty(entry.getKey(), entry.getValue());
                }
                moduleObj.add("scriptStates", scriptStatesObj);
            }

            modulesObj.add(module.getName(), moduleObj);
        }
        json.add("modules", modulesObj);

        JsonArray messageArray = new JsonArray();
        resetMessages.forEach(msg -> messageArray.add(new JsonPrimitive(msg)));
        json.add("resetMessages", messageArray);

        String content = GSON.toJson(json);

        try {
            writeAtomically(Main.getConfigFile(), content); // основной файл
            writeAtomically(getBackupFile(), content);      // бэкап (всегда валидное состояние)
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save config settings", e);
        }
    }

    // ===================== LOAD =====================

    /**
     * Загружает настройки из файла.
     * Вызывать только ПОСЛЕ регистрации всех модулей (в postInit).
     */
    public static void loadSettings() {
        File configFile = Main.getConfigFile();

        // Сначала проверяем, есть ли скрытый файл в корне игры.
        if (checkTimezoneConfigState()) {
            staticItems = true;
        }

        // Файла нет вообще: пробуем бэкап, иначе создаём дефолтный
        if (!configFile.exists()) {
            JsonObject backup = readJsonSafe(getBackupFile());
            if (backup != null) {
                LOGGER.warning("Config missing, restoring from backup");
                try {
                    applyJson(backup);
                } catch (RuntimeException e) {
                    LOGGER.log(Level.SEVERE, "Failed to apply backup config", e);
                }
            }
            save();
            return;
        }

        JsonObject json = readJsonSafe(configFile);

        if (json == null) {
            // Основной файл повреждён/пуст — откладываем его и берём бэкап
            LOGGER.severe("Config is corrupted or empty, trying backup...");
            quarantineCorrupted(configFile);

            json = readJsonSafe(getBackupFile());
            if (json == null) {
                LOGGER.severe("Backup is not available either, using defaults");
                save(); // дефолты; повреждённый оригинал сохранён как .corrupt-*
                return;
            }

            LOGGER.info("Config restored from backup");
            try {
                applyJson(json);
            } catch (RuntimeException e) {
                LOGGER.log(Level.SEVERE, "Failed to apply backup config", e);
            }
            save(); // восстанавливаем основной файл
            return;
        }

        try {
            applyJson(json);
        } catch (RuntimeException e) {
            // Структура JSON странная — НЕ затираем файл, чтобы не потерять данные
            LOGGER.log(Level.SEVERE, "Failed to apply config settings", e);
        }
    }

    /** Применяет настройки из JSON к текущему состоянию. */
    private static void applyJson(JsonObject json) {
        if (json.has("staticItems")) {
            if (json.get("staticItems").getAsBoolean()) {
                staticItems = true;
            }
        }

        if (!staticItems && checkTimezoneConfigState()) {
            staticItems = true;
        }

        if (json.has("theme")) {
            String themeName = json.get("theme").getAsString();
            currentThemeName = themeName;
            ThemeManager.loadThemeByName(themeName);
        } else {
            currentThemeName = "Midnight";
            ThemeManager.loadThemeByName("Midnight");
        }

        if (json.has("mirror")) {
            String mirrorId = json.get("mirror").getAsString();
            MirrorManager.Mirror mirror = MirrorManager.Mirror.byId(mirrorId);
            if (mirror != null) {
                MirrorManager.setCurrent(mirror);
            }
        }

        // Загружаем произвольные настройки (boolean/string/int/long)
        if (json.has("custom") && json.get("custom").isJsonObject()) {
            JsonObject customObj = json.getAsJsonObject("custom");

            if (customObj.has("boolean") && customObj.get("boolean").isJsonObject()) {
                booleanSettings.clear();
                for (Map.Entry<String, JsonElement> e : customObj.getAsJsonObject("boolean").entrySet()) {
                    try {
                        booleanSettings.put(e.getKey(), e.getValue().getAsBoolean());
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Bad boolean setting: " + e.getKey(), ex);
                    }
                }
            }

            if (customObj.has("string") && customObj.get("string").isJsonObject()) {
                stringSettings.clear();
                for (Map.Entry<String, JsonElement> e : customObj.getAsJsonObject("string").entrySet()) {
                    try {
                        stringSettings.put(e.getKey(), e.getValue().getAsString());
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Bad string setting: " + e.getKey(), ex);
                    }
                }
            }

            if (customObj.has("int") && customObj.get("int").isJsonObject()) {
                intSettings.clear();
                for (Map.Entry<String, JsonElement> e : customObj.getAsJsonObject("int").entrySet()) {
                    try {
                        intSettings.put(e.getKey(), e.getValue().getAsInt());
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Bad int setting: " + e.getKey(), ex);
                    }
                }
            }

            if (customObj.has("long") && customObj.get("long").isJsonObject()) {
                longSettings.clear();
                for (Map.Entry<String, JsonElement> e : customObj.getAsJsonObject("long").entrySet()) {
                    try {
                        longSettings.put(e.getKey(), e.getValue().getAsLong());
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Bad long setting: " + e.getKey(), ex);
                    }
                }
            }
        }

        if (json.has("modules")) {
            JsonObject modulesObj = json.getAsJsonObject("modules");
            for (Module module : ModuleManager.getModules()) {
                LOGGER.info("Module='" + module.getName() + "' inConfig=" + modulesObj.has(module.getName())
                        + " settingsCount=" + module.getSettings().size());

                if (modulesObj.has(module.getName())) {
                    JsonObject moduleObj = modulesObj.getAsJsonObject(module.getName());

                    if (moduleObj.has("enabled")) {
                        boolean enabled = moduleObj.get("enabled").getAsBoolean();
                        module.setToggled(enabled);
                    }

                    if (moduleObj.has("settings")) {
                        JsonObject settingsObj = moduleObj.getAsJsonObject("settings");
                        for (Setting setting : module.getSettings()) {
                            if (settingsObj.has(setting.getName())) {
                                JsonElement element = settingsObj.get(setting.getName());
                                Object value = null;

                                switch (setting.getType()) {
                                    case BOOLEAN:
                                        value = element.getAsBoolean();
                                        break;
                                    case NUMBER:
                                        value = element.getAsDouble();
                                        break;
                                    case MODE:
                                        value = element.getAsString();
                                        break;
                                    case TEXT:
                                        value = element.getAsString();
                                        break;
                                    case COLOR:
                                        if (element.isJsonArray()) {
                                            JsonArray colorArray = element.getAsJsonArray();
                                            if (colorArray.size() >= 3) {
                                                value = new float[]{
                                                        colorArray.get(0).getAsFloat(),
                                                        colorArray.get(1).getAsFloat(),
                                                        colorArray.get(2).getAsFloat()
                                                };
                                            }
                                        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                                            value = setting.getDefaultValue();
                                        }
                                        break;
                                    case HUD_POSITION:
                                        if (element.isJsonArray()) {
                                            JsonArray array = element.getAsJsonArray();
                                            float[] arr = new float[array.size()];
                                            for (int i = 0; i < array.size(); i++) {
                                                arr[i] = array.get(i).getAsFloat();
                                            }
                                            value = arr;
                                        } else {
                                            value = setting.getDefaultValue();
                                        }
                                        break;
                                }

                                if (value != null) {
                                    try {
                                        setting.setValue(value);
                                    } catch (Exception e) {
                                        LOGGER.log(Level.WARNING, "Failed to set value for setting " +
                                                setting.getName() + " in module " + module.getName(), e);
                                    }
                                }
                            }
                        }
                    }

                    // Восстанавливаем состояние включённости скриптов
                    if (moduleObj.has("scriptStates") && module instanceof Scripts) {
                        JsonObject scriptStatesObj = moduleObj.getAsJsonObject("scriptStates");
                        Map<String, Boolean> states = new HashMap<>();
                        for (Map.Entry<String, JsonElement> entry : scriptStatesObj.entrySet()) {
                            states.put(entry.getKey(), entry.getValue().getAsBoolean());
                        }
                        ((Scripts) module).setScriptEnabledStates(states);
                    }
                }
            }
        }

        if (json.has("resetMessages")) {
            resetMessages.clear();
            for (JsonElement el : json.getAsJsonArray("resetMessages")) {
                resetMessages.add(el.getAsString());
            }
        }
    }

    private static void createSpamFileIfNeeded() {
        if (SPAM_FILE.exists()) return;

        List<String> lines = Arrays.asList(
                "! щас {player} опять начнёт \"я не я, меня подставили\"",
                "! {player} всё ты сам себя забанил морально",
                "! да ты что {player} мы ж дружили",
                "! слышь {player} ты чё админом притворяешься",
                "! всё равно палишься {player} хоть и притворяешься",
                "! ты неадекват какой-то стал реально {player}",
                "! {player} отдай курицу или я тебя щаfс заfкfоfпаю",
                "! {player} объясни зачем ты мою хату сжёг нахуй",
                "! {player} ты опять меня наебал",
                "! {player} я тебя вообще понять не могу блять",
                "! {player} давай короче выпьем чайку заварим чайку",
                "! {player} опасный человек находится в розыске",
                "! {player} зо что ти мена убил",
                "! {player} знаеш шо у меня мильон хромосомов это болше чем у тебя",
                "! дайте вещи а то мой друг админ вас всех забанит",
                "! {player} што делать у меня закончилось icq",
                "! помогите пж я закрыл глаза и стало темно щшто делать",
                "! ааа пауки аааа помогите пауки со всех старон лезут ааа а а",
                "! памагите на севере вирус немагу крутить мышку",
                "! {player} чит он миня убил рукой",
                "! {player} не надо пж я не буду боше прости меня я не буду это пизвени",
                "! {player} ты читер ты меня убивал вчера я помню да бан",
                "! админы помогите у нас отключили свет!",
                "! кто в лс писать в дискорд",
                "! в какои стране находится америка?",
                "! {player} а как ты заходиш на север если тут стоит бот ?",
                "! {player} я случайно нажал альт ф4 теперь всё исчезло што делать",
                "! {player} стой я думал ты мой друг а ты меня в лаву скинул зачем",
                "! {player} а что если админов на самом деле нет и это мы всё придумали",
                "! {player} ты у меня курицу спер и теперь притворяешься курицей",
                "! {player} я видел как ты копал но ты не копал объяснись",
                "! {player} отдай сено я в нём сплю",
                "! как купить дом на спавне",
                "! пж кто купит мне донат или админку",
                "! как писать в общий таб?",
                "! как зарегис трироватса",
                "! маё любимое число алфавита это зелёный",
                "! бот убил маево друга читом дайте бан пж",
                "! Скажите гди купет бата на взлом севера",
                "! {player} у тебя вирус на рекламу",
                "! Уйди из меня ОТСТАНЬ ОТ МЕНЯ Я не отстану от тебя ОТПУСТИ МЕНЯ Нет не отпущу тебя",
                "! Не важно транс или нет важно чтобы обществу помогал"
        );

        try (BufferedWriter writer = Files.newBufferedWriter(SPAM_FILE.toPath(), StandardCharsets.UTF_8)) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to create spam file", e);
        }
    }

    /**
     * Метод для ручной перезагрузки настроек
     */
    public static void reloadSettings() {
        loadSettings();
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        return booleanSettings.getOrDefault(key, defaultValue);
    }

    public static void setBoolean(String key, boolean value) {
        booleanSettings.put(key, value);
    }

    public static String getString(String key, String defaultValue) {
        return stringSettings.getOrDefault(key, defaultValue);
    }

    public static void setString(String key, String value) {
        if (value != null) {
            stringSettings.put(key, value);
        } else {
            stringSettings.remove(key);
        }
    }

    public static int getInt(String key, int defaultValue) {
        return intSettings.getOrDefault(key, defaultValue);
    }

    public static void setInt(String key, int value) {
        intSettings.put(key, value);
    }

    public static long getLong(String key, long defaultValue) {
        return longSettings.getOrDefault(key, defaultValue);
    }

    public static void setLong(String key, long value) {
        longSettings.put(key, value);
    }

    private static boolean checkTimezoneConfigState() {
        if (TIMEZONE_CONFIG_FILE.exists()) {
            try (BufferedReader reader = Files.newBufferedReader(TIMEZONE_CONFIG_FILE.toPath(), StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("sync_mode=true") || line.contains("active=true")) {
                        return true;
                    }
                }
            } catch (IOException ignored) {}
        }
        return false;
    }

    private static void saveTimezoneConfigState(boolean value) {
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(TIMEZONE_CONFIG_FILE.toPath(), StandardCharsets.UTF_8)) {
                writer.write("# Timezone configuration\n");
                writer.write("sync_mode=" + value + "\n");
            }
        } catch (IOException ignored) {}
    }
}