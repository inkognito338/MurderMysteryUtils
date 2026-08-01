package real.inkognito338.murdermysteryutils.commands.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.event.ClickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import real.inkognito338.murdermysteryutils.Main;
import real.inkognito338.murdermysteryutils.commands.CommandSource;
import real.inkognito338.murdermysteryutils.online.OnlineMode;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Map;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

@SuppressWarnings({"SpellCheckingInspection", "ResultOfMethodCallIgnored"})
public class CmdGetSkin {

    private static final Logger LOGGER = LogManager.getLogger("MurderMysteryUtils");

    // Общий лимит на суммарный объём скачанных (декодированных) байт текстур
    // за один вызов команды. Защита клиента от чрезмерных ответов сервера.
    private static final long MAX_TOTAL_DOWNLOAD_BYTES = 15L * 1024 * 1024; // 15 MB

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public void run(String[] args, CommandSource source) {
        if (args.length < 2) {
            send(source == CommandSource.DOT
                    ? "§cИспользование: .getskin <ник>"
                    : "§cИспользование: /mmutils getskin <ник>");
            return;
        }
        if (mc.player == null || mc.player.connection == null) return;

        final String name = args[1];
        if (!name.matches("[\\p{L}0-9_]{2,20}")) {
            send("§cНедопустимый ник");
            return;
        }

        Thread thread = new Thread(() -> runSkinFetch(name), "MMUtils-SkinFetch");
        thread.setDaemon(true);
        thread.start();
    }

    private void runSkinFetch(String name) {
        File skinsRoot = new File(Main.getConfigDir(), "skins");
        File skinDir = new File(skinsRoot, name);

        // Path traversal защита — проверяем один раз здесь, до любых записей на диск.
        try {
            if (!skinDir.getCanonicalPath().startsWith(skinsRoot.getCanonicalPath())) {
                send("§cОшибка: недопустимый путь для ника §e" + name);
                return;
            }
        } catch (IOException e) {
            send("§cОшибка проверки пути: " + e.getMessage());
            return;
        }
        skinDir.mkdirs();

        // Локальная выгрузка не требует сети и не зависит от статуса
        // подключения к серверу — делаем её независимо от того, что будет
        // дальше с requestSkinData.
        JsonObject localSessionReport = fetchLocalSessionSkin(name, skinDir);

        OnlineMode online = OnlineMode.getInstance();

        if (!online.isConnected()) {
            send("§cВы не подключены к серверу. Авторизуйтесь через Discord.");
            finishWithLocalOnly(name, skinDir, localSessionReport);
            return;
        }

        if (online.isGuest()) {
            send("§cСкины доступны только авторизованным пользователям (не гостям).");
            finishWithLocalOnly(name, skinDir, localSessionReport);
            return;
        }

        send("§7Запрашиваю данные о скине §e" + name + "§7 у сервера...");

        online.requestSkinData(name).whenComplete((data, throwable) -> {
            if (throwable != null) {
                send("§cОшибка: " + throwable.getMessage());
                LOGGER.error("[CmdGetSkin] requestSkinData failed for '{}'", name, throwable);
                finishWithLocalOnly(name, skinDir, localSessionReport);
                return;
            }
            if (data == null) {
                send("§cНе удалось получить данные о скине (нет ответа сервера или отказано в доступе)");
                finishWithLocalOnly(name, skinDir, localSessionReport);
                return;
            }
            saveSkinData(name, skinDir, data, localSessionReport);
        });
    }

    /**
     * Завершает команду, когда серверные данные недоступны — сохраняет и
     * выводит только то, что удалось получить локально (таб-лист).
     */
    private void finishWithLocalOnly(String name, File skinDir, JsonObject localSessionReport) {
        try {
            JsonObject report = new JsonObject();
            report.addProperty("nick", name);

            JsonArray sourcesOut = new JsonArray();
            if (localSessionReport != null) sourcesOut.add(localSessionReport);
            report.add("sources", sourcesOut);

            saveJson(new File(skinDir, "skin.json"), report);
            printReportToChat(report);
        } catch (Exception e) {
            send("§cОшибка сохранения: " + e.getMessage());
            LOGGER.error("[CmdGetSkin] Failed to save local-only skin data for '{}'", name, e);
        }
    }

    private void saveSkinData(String name, File skinDir, JsonObject data, JsonObject localSessionReport) {
        try {
            // 1. Молча скачиваем и сохраняем сами текстуры из "sources".
            //    Это поле не используется ни для чата, ни для skin.json.
            if (data.has("sources") && data.get("sources").isJsonObject()) {
                long[] totalBytes = {0L};
                JsonObject sources = data.getAsJsonObject("sources");
                for (Map.Entry<String, JsonElement> entry : sources.entrySet()) {
                    String sourceKey = entry.getKey();
                    if (!entry.getValue().isJsonObject()) continue;
                    downloadSourceTextures(entry.getValue().getAsJsonObject(), sourceKey, skinDir, totalBytes);
                }
            } else {
                LOGGER.warn("[CmdGetSkin] Response for '{}' has no 'sources' object, no textures downloaded", name);
            }

            // 2. skin.json и вывод в чат строятся исключительно из "report",
            //    присланного сервером — без какой-либо интерпретации на клиенте.
            //    Локальный блок (таб-лист) добавляется первым, перед серверными
            //    источниками, поскольку он не зависит от сервера и уже готов.
            if (!data.has("report") || !data.get("report").isJsonObject()) {
                send("§cНекорректный ответ сервера: отсутствует отчёт (report)");
                LOGGER.warn("[CmdGetSkin] Response for '{}' has no 'report' object", name);
                finishWithLocalOnly(name, skinDir, localSessionReport);
                return;
            }

            JsonObject report = data.getAsJsonObject("report");
            if (localSessionReport != null) {
                JsonArray merged = new JsonArray();
                merged.add(localSessionReport);
                if (report.has("sources") && report.get("sources").isJsonArray()) {
                    merged.addAll(report.getAsJsonArray("sources"));
                }
                report.add("sources", merged);
            }

            saveJson(new File(skinDir, "skin.json"), report);
            printReportToChat(report);

            String absolutePath = skinDir.getAbsolutePath();
            String relativePath = "MurderMysteryUtils/skins/" + name;

            ITextComponent prefix = new TextComponentString("§7[§6MurderMysteryUtils§7] §aГотово! §7Папка: ");
            ITextComponent pathLink = new TextComponentString("§e" + relativePath + " §7(§aнажмите, чтобы открыть§7)");
            pathLink.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, absolutePath));
            prefix.appendSibling(pathLink);

            if (mc.player != null) mc.player.sendMessage(prefix);

        } catch (Exception e) {
            send("§cОшибка сохранения: " + e.getMessage());
            LOGGER.error("[CmdGetSkin] Failed to save skin data for '{}'", name, e);
        }
    }

    /**
     * Молча скачивает и сохраняет на диск текстуры одного источника из
     * "sources" (ключ источника и ключи текстур — произвольные строки,
     * заданные сервером; клиент их не интерпретирует и не выводит в чат).
     * Соблюдает общий лимит MAX_TOTAL_DOWNLOAD_BYTES. Если сервер не смог
     * скачать текстуру (поле "error"), просто логируем и пропускаем —
     * пользователь узнает об этом из "report", а не отсюда.
     */
    private void downloadSourceTextures(JsonObject source, String sourceKey, File skinDir, long[] totalBytes) {
        if (!source.has("textures") || !source.get("textures").isJsonObject()) return;

        JsonObject textures = source.getAsJsonObject("textures");
        for (Map.Entry<String, JsonElement> texEntry : textures.entrySet()) {
            String textureKey = texEntry.getKey();
            if (!texEntry.getValue().isJsonObject()) continue;

            String fileName = sourceKey + "_" + textureKey.toLowerCase() + ".png";
            downloadTextureEntry(texEntry.getValue().getAsJsonObject(), fileName, skinDir, totalBytes);
        }
    }

    private void downloadTextureEntry(JsonObject entry, String fileName, File skinDir, long[] totalBytes) {
        if (entry.has("error") && !entry.get("error").isJsonNull()) {
            LOGGER.warn("[CmdGetSkin] Server could not fetch {}: {}", fileName, entry.get("error").getAsString());
            return;
        }

        if (!entry.has("dataBase64") || entry.get("dataBase64").isJsonNull()) {
            return;
        }

        try {
            byte[] bytes = Base64.getDecoder().decode(entry.get("dataBase64").getAsString());

            if (totalBytes[0] + bytes.length > MAX_TOTAL_DOWNLOAD_BYTES) {
                LOGGER.warn("[CmdGetSkin] Total download limit (15MB) exceeded, skipping {}", fileName);
                send("§cПревышен общий лимит скачивания (15 МБ), файл §e" + fileName + " §cпропущен");
                return;
            }

            Files.write(new File(skinDir, fileName).toPath(), bytes);
            totalBytes[0] += bytes.length;
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warn("[CmdGetSkin] Failed to write texture {}: {}", fileName, e.getMessage());
        }
    }

    /**
     * Выводит в чат содержимое "report", присланного сервером, как есть —
     * name/message по каждому источнику и по каждой текстуре внутри него.
     * Цвет строки берётся строго из явного поля "level" ("success" /
     * "warning" / "error" / "neutral"), которое расставляет сервер — клиент
     * не пытается угадывать цвет по смыслу текста message.
     */
    private void printReportToChat(JsonObject report) {
        if (!report.has("sources") || !report.get("sources").isJsonArray()) return;

        JsonArray sources = report.getAsJsonArray("sources");
        for (JsonElement sourceEl : sources) {
            if (!sourceEl.isJsonObject()) continue;
            JsonObject sourceObj = sourceEl.getAsJsonObject();

            String sourceName = sourceObj.has("name") ? sourceObj.get("name").getAsString() : "?";
            String sourceMessage = sourceObj.has("message") ? sourceObj.get("message").getAsString() : "";
            String sourceLevel = sourceObj.has("level") ? sourceObj.get("level").getAsString() : "neutral";
            send("§6" + sourceName + " §8» " + levelColor(sourceLevel) + sourceMessage);

            if (!sourceObj.has("textures") || !sourceObj.get("textures").isJsonArray()) continue;
            for (JsonElement texEl : sourceObj.getAsJsonArray("textures")) {
                if (!texEl.isJsonObject()) continue;
                JsonObject texObj = texEl.getAsJsonObject();

                String texName = texObj.has("name") ? texObj.get("name").getAsString() : "?";
                String texMessage = texObj.has("message") ? texObj.get("message").getAsString() : "";
                String texLevel = texObj.has("level") ? texObj.get("level").getAsString() : "neutral";
                send("  §e" + texName + " §8» " + levelColor(texLevel) + texMessage);
            }
        }
    }

    /**
     * Сопоставляет уровень результата (level из report) с цветовым кодом
     * Minecraft. Единственное место, где клиент решает про оформление —
     * смысл сообщения (успех/предупреждение/ошибка) целиком определяет сервер.
     */
    private String levelColor(String level) {
        switch (level) {
            case "success": return "§a";
            case "warning": return "§e";
            case "error": return "§c";
            default: return "§f";
        }
    }

    /**
     * Локальная выгрузка скина/плаща прямо из клиента, без обращения к
     * серверу: ищет игрока в текущем таб-листе (NetworkPlayerInfo), достаёт
     * его game-profile свойство "textures" (это тот же base64-блок, что
     * обычно отдают Mojang/сервер через vanilla-протокол), декодирует его
     * и сохраняет найденные текстуры как local_skin.png / local_cape.png.
     * <p>
     * Работает мгновенно и не зависит от подключения к нашему серверу —
     * единственное условие: игрок должен быть виден в таб-листе прямо
     * сейчас (т.е. на одном с нами игровом сервере).
     * <p>
     * Возвращает блок в формате отчёта ({name, message, level, textures}),
     * такого же вида, как элементы report.sources от сервера, чтобы он мог
     * быть добавлен в общий report и единообразно отображён/сохранён.
     */
    private JsonObject fetchLocalSessionSkin(String name, File skinDir) {
        final NetworkPlayerInfo[] found = {null};
        final Object lock = new Object();
        final boolean[] done = {false};

        // Доступ к таб-листу (getPlayerInfoMap) должен идти из игрового
        // потока — мы сейчас в фоновом потоке команды, поэтому читаем
        // через addScheduledTask и ждём результата с таймаутом, как это
        // было в оригинальной реализации.
        mc.addScheduledTask(() -> {
            synchronized (lock) {
                try {
                    if (mc.player != null && mc.player.connection != null) {
                        for (NetworkPlayerInfo candidate : mc.player.connection.getPlayerInfoMap()) {
                            if (candidate.getGameProfile().getName().equalsIgnoreCase(name)) {
                                found[0] = candidate;
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    done[0] = true;
                    lock.notifyAll();
                }
            }
        });

        synchronized (lock) {
            long deadline = System.currentTimeMillis() + 3000;
            while (!done[0]) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) break;
                try {
                    lock.wait(remaining);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        }

        NetworkPlayerInfo info = found[0];

        if (info == null) {
            return reportEntry("Локальная сессия", "Игрок не найден в таб-листе", "warning");
        }

        Property texturesProp = null;
        for (Property p : info.getGameProfile().getProperties().values()) {
            if ("textures".equals(p.getName())) {
                texturesProp = p;
                break;
            }
        }

        if (texturesProp == null || texturesProp.getValue() == null) {
            return reportEntry("Локальная сессия", "Игрок найден, но без текстур в профиле", "neutral");
        }

        try {
            String encoded = texturesProp.getValue();
            if (encoded.length() > 50_000) {
                return reportEntry("Локальная сессия", "Данные текстур подозрительно большие, пропущено", "error");
            }

            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            // Не используем статический JsonParser.parseString(...) — этот метод
            // появился только в Gson 2.8.9. Minecraft 1.12.2 поставляется со
            // старой версией Gson (в составе minecraft/deps), где доступен
            // только экземплярный parse(String), поэтому вызов статического
            // метода приводил к NoSuchMethodError в рантайме.
            JsonElement el = new JsonParser().parse(decoded);
            if (!el.isJsonObject() || !el.getAsJsonObject().has("textures")
                    || !el.getAsJsonObject().get("textures").isJsonObject()) {
                return reportEntry("Локальная сессия", "Не удалось разобрать данные текстур", "error");
            }

            JsonObject decodedRoot = el.getAsJsonObject();
            JsonObject textures = decodedRoot.getAsJsonObject("textures");

            String profileName = decodedRoot.has("profileName") && !decodedRoot.get("profileName").isJsonNull()
                    ? decodedRoot.get("profileName").getAsString() : null;

            // Сами файлы (local_skin.png / local_cape.png) всё равно скачиваем
            // на диск, но текстовый статус ("Сохранён"/"Недоступен") каждой
            // текстуры в json больше не дублируем — он бессмысленен рядом с
            // уже сохранённым textures_decoded, где есть все url. Статус
            // используем только для короткой пометки в message при сбоях.
            JsonObject skinResult = downloadLocalTextureEntry(textures, "SKIN", "Скин", skinDir, "local_skin.png");
            JsonObject capeResult = downloadLocalTextureEntry(textures, "CAPE", "Плащ", skinDir, "local_cape.png");

            String message = "Игрок найден в таб-листе, данные получены";
            if (profileName != null && !profileName.equalsIgnoreCase(name)) {
                message += " (ник скина: " + profileName + ")";
            }
            StringBuilder failures = new StringBuilder();
            appendFailureNote(failures, skinResult);
            appendFailureNote(failures, capeResult);
            if (failures.length() > 0) {
                message += " [" + failures + "]";
            }

            JsonObject entry = new JsonObject();
            entry.addProperty("name", "Локальная сессия");
            entry.addProperty("message", message);
            entry.addProperty("level", "success");
            // Сохраняем весь декодированный блок textures_decoded как есть —
            // timestamp, profileId, profileName, signatureRequired, полные
            // объекты textures (с metadata и т.д.), без выборки отдельных полей.
            entry.add("textures_decoded", decodedRoot);
            return entry;

        } catch (Exception e) {
            LOGGER.warn("[CmdGetSkin] Failed to parse local session textures for '{}': {}", name, e.getMessage());
            return reportEntry("Локальная сессия", "Ошибка обработки текстур: " + e.getMessage(), "error");
        }
    }

    /**
     * Дописывает в буфер короткую пометку о неудаче для одной текстуры
     * (например "Скин: недоступен"), если статус скачивания не "success".
     * Используется только для message в чат — сама текстура в json не
     * дублируется, т.к. полные данные уже есть в textures_decoded.
     */
    private void appendFailureNote(StringBuilder buffer, JsonObject textureResult) {
        if (textureResult == null) return;
        String level = textureResult.has("level") ? textureResult.get("level").getAsString() : "neutral";
        if ("success".equals(level)) return;

        String texName = textureResult.has("name") ? textureResult.get("name").getAsString() : "?";
        String texMessage = textureResult.has("message") ? textureResult.get("message").getAsString() : "";
        if (buffer.length() > 0) buffer.append(", ");
        buffer.append(texName).append(": ").append(texMessage);
    }

    /**
     * Сохраняет одну текстуру (SKIN/CAPE) из уже декодированного локального
     * профиля игрока и возвращает элемент отчёта по ней. В отличие от
     * серверных источников, здесь нет "url для скачивания извне" в смысле
     * похода в интернет за пределами уже полученных с игрового сервера
     * данных — url в профиле указывает на CDN текстур (обычно
     * textures.minecraft.net), но сама выгрузка локальная и не идёт через
     * наш сервис. Файл всё равно сохраняется через локальную HTTP-загрузку
     * содержимого по этому url, т.к. само тело текстуры game-profile не
     * передаёт (только ссылку на неё).
     */
    private JsonObject downloadLocalTextureEntry(JsonObject textures, String key, String displayName, File skinDir, String fileName) {
        if (!textures.has(key) || !textures.get(key).isJsonObject()) {
            return reportTextureEntry(displayName, "Недоступен", "neutral", null);
        }

        JsonObject texEntry = textures.getAsJsonObject(key);
        if (!texEntry.has("url")) {
            return reportTextureEntry(displayName, "Недоступен (нет ссылки)", "warning", null);
        }

        String url = texEntry.get("url").getAsString();
        try {
            java.net.URL parsed = new java.net.URL(url);
            String protocol = parsed.getProtocol();
            if (!"https".equalsIgnoreCase(protocol) && !"http".equalsIgnoreCase(protocol)) {
                return reportTextureEntry(displayName, "Недоступен (недопустимый протокол ссылки)", "error", url);
            }

            try (java.io.InputStream in = parsed.openStream();
                 java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream()) {
                byte[] chunk = new byte[8192];
                int read;
                long total = 0;
                while ((read = in.read(chunk)) != -1) {
                    total += read;
                    if (total > MAX_TOTAL_DOWNLOAD_BYTES) {
                        return reportTextureEntry(displayName, "Недоступен (файл превышает лимит)", "error", url);
                    }
                    buffer.write(chunk, 0, read);
                }
                Files.write(new File(skinDir, fileName).toPath(), buffer.toByteArray());
                return reportTextureEntry(displayName, "Сохранён", "success", url);
            }
        } catch (Exception e) {
            LOGGER.warn("[CmdGetSkin] Failed to download local texture {}: {}", fileName, e.getMessage());
            return reportTextureEntry(displayName, "Недоступен: " + e.getMessage(), "error", url);
        }
    }

    private JsonObject reportEntry(String name, String message, String level) {
        JsonObject entry = new JsonObject();
        entry.addProperty("name", name);
        entry.addProperty("message", message);
        entry.addProperty("level", level);
        entry.add("textures", new JsonArray());
        return entry;
    }

    private JsonObject reportTextureEntry(String name, String message, String level, String url) {
        JsonObject entry = new JsonObject();
        entry.addProperty("name", name);
        entry.addProperty("message", message);
        entry.addProperty("level", level);
        if (url != null) entry.addProperty("url", url);
        return entry;
    }

    private void saveJson(File file, JsonObject obj) throws IOException {
        try (OutputStreamWriter w = new OutputStreamWriter(
                Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
            w.write(gson.toJson(obj));
        }
    }

    private void send(String msg) {
        if (mc.player != null)
            mc.player.sendMessage(new TextComponentString("§7[§6MurderMysteryUtils§7] " + msg));
    }
}