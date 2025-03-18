package real.inkognito338.murdermysteryutils;

import java.io.*;
import com.google.gson.*;
import java.util.HashSet;
import java.util.Set;

// by inkognito338 | forge 1.12.2 - 14.23.5.2860

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Основные настройки
    private static int categoryX = 100;
    private static int categoryY = 100;
    private static Set<String> resetMessages = new HashSet<>();

    // Загрузка настроек при инициализации класса
    static {
        loadSettings();
    }

    /**
     * Сохранение настроек в JSON файл
     * @param x координата X категории
     * @param y координата Y категории
     * @param messages набор сообщений для сброса
     */
    public static void saveSettings(int x, int y, Set<String> messages) {
        categoryX = x;
        categoryY = y;
        resetMessages = new HashSet<>(messages);

        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("categoryX", categoryX);
        jsonObject.addProperty("categoryY", categoryY);

        // Сохранение опций
        JsonArray settingsArray = new JsonArray();
        for (SettingsOption option : SettingsOption.values()) {
            JsonObject setting = new JsonObject();
            setting.addProperty("name", option.name());
            setting.addProperty("value", option.getValue());
            settingsArray.add(setting);
        }
        jsonObject.add("settings", settingsArray);

        // Сохранение сообщений для сброса
        JsonArray messagesArray = new JsonArray();
        for (String message : resetMessages) {
            messagesArray.add(new JsonPrimitive(message));
        }
        jsonObject.add("resetMessages", messagesArray);

        try (FileWriter writer = new FileWriter(Main.getConfigFile())) {
            GSON.toJson(jsonObject, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Загрузка настроек из JSON файла
     */
    public static void loadSettings() {
        File configFile = Main.getConfigFile();
        if (!configFile.exists()) return;

        try (FileReader reader = new FileReader(configFile)) {
            JsonObject jsonObject = GSON.fromJson(reader, JsonObject.class);

            categoryX = jsonObject.has("categoryX") ? jsonObject.get("categoryX").getAsInt() : 100;
            categoryY = jsonObject.has("categoryY") ? jsonObject.get("categoryY").getAsInt() : 100;

            // Загрузка опций
            if (jsonObject.has("settings")) {
                for (JsonElement element : jsonObject.getAsJsonArray("settings")) {
                    JsonObject setting = element.getAsJsonObject();
                    String name = setting.get("name").getAsString();
                    boolean value = setting.get("value").getAsBoolean();

                    for (SettingsOption option : SettingsOption.values()) {
                        if (option.name().equals(name)) {
                            option.setValue(value);
                            break;
                        }
                    }
                }
            }

            // Загрузка сообщений для сброса
            resetMessages.clear();
            if (jsonObject.has("resetMessages")) {
                for (JsonElement element : jsonObject.getAsJsonArray("resetMessages")) {
                    resetMessages.add(element.getAsString());
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            e.printStackTrace();
        }
    }

    // Геттеры для координат
    public static int getCategoryX() {
        return categoryX;
    }

    public static int getCategoryY() {
        return categoryY;
    }

    // Геттер и сеттер для resetMessages
    public static Set<String> getResetMessages() {
        return new HashSet<>(resetMessages);
    }

    public static void setResetMessages(Set<String> messages) {
        resetMessages = new HashSet<>(messages);
        saveSettings(categoryX, categoryY, resetMessages);
    }
}
