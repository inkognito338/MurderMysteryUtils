package real.inkognito338.murdermysteryutils;

import com.google.gson.*;
import real.inkognito338.murdermysteryutils.modules.Module;
import real.inkognito338.murdermysteryutils.modules.ModuleManager;
import real.inkognito338.murdermysteryutils.modules.settings.Setting;
import real.inkognito338.murdermysteryutils.modules.settings.SettingType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("SpellCheckingInspection")
public class ConfigManager {
    private static final Logger LOGGER = Logger.getLogger(ConfigManager.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static int categoryX = 100;
    private static int categoryY = 100;
    private static final Set<String> resetMessages = new HashSet<>();
    private static final File SPAM_FILE = new File(Main.getConfigFile().getParentFile(), "spam.txt");

    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        createSpamFileIfNeeded();
        // Загружаем настройки при инициализации, если модули уже зарегистрированы
        if (!ModuleManager.getModules().isEmpty()) {
            loadSettings();
        }
    }

    public static void save() {
        JsonObject json = new JsonObject();
        json.addProperty("categoryX", categoryX);
        json.addProperty("categoryY", categoryY);

        // Сохраняем состояния модулей
        JsonObject modulesObj = new JsonObject();
        for (Module module : ModuleManager.getModules()) {
            JsonObject moduleObj = new JsonObject();
            moduleObj.addProperty("enabled", module.isToggled());

            // Сохраняем настройки модуля
            JsonObject settingsObj = new JsonObject();
            for (Setting setting : module.getSettings()) {
                Object value = setting.getValue();
                if (value instanceof Boolean) {
                    settingsObj.addProperty(setting.getName(), (Boolean) value);
                } else if (value instanceof Number) {
                    // Преобразуем Number в Double для сохранения
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
                    // Сохраняем COLOR настройки как массив
                    float[] color = (float[]) value;
                    JsonArray colorArray = new JsonArray();
                    colorArray.add(new JsonPrimitive(color[0]));
                    colorArray.add(new JsonPrimitive(color[1]));
                    colorArray.add(new JsonPrimitive(color[2]));
                    settingsObj.add(setting.getName(), colorArray);
                }
            }
            moduleObj.add("settings", settingsObj);
            modulesObj.add(module.getName(), moduleObj);
        }
        json.add("modules", modulesObj);

        // Сохраняем resetMessages
        JsonArray messageArray = new JsonArray();
        resetMessages.forEach(msg -> messageArray.add(new JsonPrimitive(msg)));
        json.add("resetMessages", messageArray);

        try (Writer writer = new FileWriter(Main.getConfigFile())) {
            GSON.toJson(json, writer);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save config settings", e);
        }
    }

    public static void loadSettings() {
        init(); // Инициализируем при первой загрузке

        File configFile = Main.getConfigFile();
        if (!configFile.exists()) {
            save(); // Создаем файл с настройками по умолчанию
            return;
        }

        try (Reader reader = new FileReader(configFile)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) {
                save(); // Создаем новый конфиг если файл пустой
                return;
            }

            categoryX = json.has("categoryX") ? json.get("categoryX").getAsInt() : categoryX;
            categoryY = json.has("categoryY") ? json.get("categoryY").getAsInt() : categoryY;

            // Загружаем состояния модулей
            if (json.has("modules")) {
                JsonObject modulesObj = json.getAsJsonObject("modules");
                for (Module module : ModuleManager.getModules()) {
                    if (modulesObj.has(module.getName())) {
                        JsonObject moduleObj = modulesObj.getAsJsonObject(module.getName());

                        // Загружаем состояние включения/выключения
                        if (moduleObj.has("enabled")) {
                            boolean enabled = moduleObj.get("enabled").getAsBoolean();
                            module.setToggled(enabled);
                        }

                        // Загружаем настройки модуля
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
                                                // Совместимость со старым форматом - используем значение по умолчанию
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
                    }
                }
            }

            // Загружаем resetMessages
            if (json.has("resetMessages")) {
                resetMessages.clear();
                for (JsonElement el : json.getAsJsonArray("resetMessages")) {
                    resetMessages.add(el.getAsString());
                }
            }

        } catch (IOException | JsonSyntaxException e) {
            LOGGER.log(Level.SEVERE, "Failed to load config settings", e);
            // Если файл поврежден, создаем новый с настройками по умолчанию
            save();
        }
    }

    private static void createSpamFileIfNeeded() {
        if (SPAM_FILE.exists()) return;

        List<String> lines = Arrays.asList(
                "! щас {player} опять начнёт \"я не я, меня подставили\"",
                "! {player} всё ты сам себя забанил морально",
                "! да ты что {player} мы ж дружили",
                "! а ты {player}, фашист да? сам признался",
                "! слышь {player} ты чё админом притворяешься",
                "! всё равно палишься {player} хоть и притворяешься",
                "! ты неадекват какой-то стал реально {player}",
                "! {player} отдай курицу или я тебя щас закопаю",
                "! {player} объясни зачем ты мою хату сжёг нахуй",
                "! {player} ты опять меня наебал",
                "! {player} я тебя вообще понять не могу блять",
                "! {player} давай короче выпьем чайку заварим чайку",
                "! {player} опасный человек находится в розыске",
                "! {player} зо что ти мена убил",
                "! {player} ты обещал загриферить {player2} потому что ты говарил он лох",
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
                "! {player} ти обещал чайку пить",
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

    // Getters
    public static int getCategoryX() {
        return categoryX;
    }

    public static int getCategoryY() {
        return categoryY;
    }

    // Setter для позиции категории
    public static void setCategoryPosition(int x, int y) {
        categoryX = x;
        categoryY = y;
        save();
    }

    // Метод для ручной загрузки настроек (если нужно перезагрузить)
    public static void reloadSettings() {
        loadSettings();
    }
}