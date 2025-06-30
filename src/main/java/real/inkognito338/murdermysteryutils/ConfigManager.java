package real.inkognito338.murdermysteryutils;

import com.google.gson.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConfigManager {
    private static final Logger LOGGER = Logger.getLogger(ConfigManager.class.getName());

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static int categoryX = 100;
    private static int categoryY = 100;
    private static final Set<String> resetMessages = new HashSet<>();

    private static final File SPAM_FILE = new File(Main.getConfigFile().getParentFile(), "spam.txt");

    static {
        loadSettings();
        createSpamFileIfNeeded();
    }

    public static void saveSettings(int x, int y, Set<String> messages) {
        categoryX = x;
        categoryY = y;

        resetMessages.clear();
        if (messages != null) {
            resetMessages.addAll(messages);
        }

        JsonObject json = new JsonObject();
        json.addProperty("categoryX", categoryX);
        json.addProperty("categoryY", categoryY);

        JsonArray settingsArray = new JsonArray();
        for (SettingsOption option : SettingsOption.values()) {
            JsonObject setting = new JsonObject();
            setting.addProperty("name", option.name());
            setting.addProperty("value", option.getValue());
            settingsArray.add(setting);
        }
        json.add("settings", settingsArray);

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
        File configFile = Main.getConfigFile();
        if (!configFile.exists()) return;

        try (Reader reader = new FileReader(configFile)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) return;

            categoryX = json.has("categoryX") ? json.get("categoryX").getAsInt() : categoryX;
            categoryY = json.has("categoryY") ? json.get("categoryY").getAsInt() : categoryY;

            if (json.has("settings")) {
                for (JsonElement el : json.getAsJsonArray("settings")) {
                    JsonObject setting = el.getAsJsonObject();
                    String name = setting.get("name").getAsString();
                    boolean value = setting.get("value").getAsBoolean();

                    Arrays.stream(SettingsOption.values())
                            .filter(opt -> opt.name().equals(name))
                            .findFirst()
                            .ifPresent(opt -> opt.setValue(value));
                }
            }

            resetMessages.clear();
            if (json.has("resetMessages")) {
                for (JsonElement el : json.getAsJsonArray("resetMessages")) {
                    resetMessages.add(el.getAsString());
                }
            }

        } catch (IOException | JsonSyntaxException e) {
            LOGGER.log(Level.SEVERE, "Failed to load config settings", e);
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

    public static Set<String> getResetMessages() {
        return new HashSet<>(resetMessages);
    }

    public static void setResetMessages(Set<String> messages) {
        resetMessages.clear();
        if (messages != null) {
            resetMessages.addAll(messages);
        }
        saveSettings(categoryX, categoryY, resetMessages);
    }
}
