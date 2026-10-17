package real.inkognito338.murdermysteryutils.utils.analytics;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import real.inkognito338.murdermysteryutils.Main;
import real.inkognito338.murdermysteryutils.utils.CertManager;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 15.10.2026
 */
public class MixpanelAnalytics {

    private static final boolean DEBUG = false;
    private static final Logger LOGGER = LogManager.getLogger("MurderMysteryUtils");

    // Ваш Mixpanel Project Token
    private static final String PROJECT_TOKEN = "34ce9193b87af97e7381c2104eba16e5";

    // Базовый европейский эндпоинт (в зависимости от DEBUG добавляем ?verbose=1)
    private static final String MIXPANEL_ENDPOINT = "https://api-eu.mixpanel.com/track" + (DEBUG ? "?verbose=1" : "");

    private static final String USER_AGENT = "MurderMysteryUtils/" + Main.VERSION + " (Minecraft)";

    private static final int HTTP_TIMEOUT_MS = 5_000;

    private static void debug(String msg, Object... args) {
        if (DEBUG) LOGGER.info(msg, args);
    }

    public static void sendEvent(String eventName) {
        new Thread(() -> {
            try {
                debug("[Mixpanel] Начало отправки события: {}", eventName);

                Minecraft mc = Minecraft.getMinecraft();
                String language = mc.gameSettings != null ? mc.gameSettings.language : "unknown";
                int guiScale = mc.gameSettings != null ? mc.gameSettings.guiScale : 0;
                int renderDist = mc.gameSettings != null ? mc.gameSettings.renderDistanceChunks : 0;

                // Проверяем наличие Mixin-библиотек среди активных модов
                boolean hasMixins = false;
                for (ModContainer mod : Loader.instance().getActiveModList()) {
                    String modId = mod.getModId().toLowerCase();
                    String modName = mod.getName().toLowerCase();
                    if (modId.contains("mixin") || modName.contains("mixin") || modId.equals("mixinbooter")) {
                        hasMixins = true;
                        break;
                    }
                }

                // 1. Формируем свойства (properties) события
                JsonObject properties = new JsonObject();
                properties.addProperty("token", PROJECT_TOKEN);
                properties.addProperty("distinct_id", getDistinctId()); // Уникальный идентификатор игрока

                // Дополнительные параметры мода и системы
                properties.addProperty("mc_version", mc.getVersion());
                properties.addProperty("mod_version", Main.VERSION);
                properties.addProperty("forge_version", ForgeVersion.getVersion());
                properties.addProperty("java_version", System.getProperty("java.version", "unknown"));
                properties.addProperty("os_name", System.getProperty("os.name", "unknown"));
                properties.addProperty("os_arch", System.getProperty("os.arch", "unknown"));
                properties.addProperty("allocated_ram_mb", Runtime.getRuntime().maxMemory() / (1024 * 1024));
                properties.addProperty("optifine_installed", Loader.isModLoaded("optifine"));

                // Параметры модов
                properties.addProperty("mods_count", Loader.instance().getActiveModList().size());
                properties.addProperty("mixins_installed", hasMixins);

                properties.addProperty("language", language);
                properties.addProperty("gui_scale", guiScale);
                properties.addProperty("render_distance", renderDist);

                // 2. Формируем объект события
                JsonObject event = new JsonObject();
                event.addProperty("event", eventName);
                event.add("properties", properties);

                // 3. Mixpanel Track API требует обертку в массив [...]
                JsonArray eventsArray = new JsonArray();
                eventsArray.add(event);

                String body = eventsArray.toString();
                debug("[Mixpanel] JSON тело: {}", body);

                // 4. Отправляем запрос
                String response = post(MIXPANEL_ENDPOINT, body);
                debug("[Mixpanel] Ответ сервера: {}", response);

            } catch (Throwable t) {
                LOGGER.warn("[Mixpanel] Ошибка отправки события: {}", t.getMessage());
            }
        }, "Mixpanel-Thread").start();
    }

    /**
     * POST JSON. Возвращает тело ответа независимо от кода (для 4xx/5xx читается errorStream).
     * Сетевые ошибки логируются и пробрасываются наружу.
     */
    private static String post(String url, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            SSLSocketFactory factory = buildSslFactory();
            if (factory != null && conn instanceof HttpsURLConnection) {
                ((HttpsURLConnection) conn).setSSLSocketFactory(factory);
            }
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(HTTP_TIMEOUT_MS);
            conn.setReadTimeout(HTTP_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setDoOutput(true);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(payload.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(payload);
            }

            int code = conn.getResponseCode();
            debug("[Mixpanel] HTTPS ответ код: {}", code);

            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in == null) return "";
            try (InputStream stream = in) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = stream.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOGGER.warn("[Mixpanel] Ошибка отправки запроса: {}", e.getMessage());
            throw e;
        } finally {
            conn.disconnect();
        }
    }

    /**
     * SSLSocketFactory из CertManager или null (дефолтный JVM SSL).
     */
    private static SSLSocketFactory buildSslFactory() {
        try {
            SSLContext ctx = CertManager.getSSLContext();
            if (ctx != null) {
                return ctx.getSocketFactory();
            }
        } catch (Throwable t) {
            LOGGER.warn("[Mixpanel] Не удалось создать SSL-фабрику с CertManager: {}", t.getMessage());
        }
        return null;
    }

    /**
     * Получение или генерация уникального distinct_id для Mixpanel.
     */
    private static String getDistinctId() {
        File file = new File(Minecraft.getMinecraft().mcDataDir, "murdermysteryutils_client_id.txt");

        if (file.exists()) {
            try {
                String id = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
                if (!id.isEmpty()) {
                    return id;
                }
            } catch (Throwable t) {
                LOGGER.warn("[Mixpanel] Не удалось прочитать client_id: {}", t.getMessage());
            }
        }

        String id = UUID.randomUUID().toString();
        try {
            Files.write(file.toPath(), id.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            LOGGER.warn("[Mixpanel] Не удалось сохранить client_id: {}", t.getMessage());
        }
        return id;
    }
}