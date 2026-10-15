package real.inkognito338.murdermysteryutils.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.common.Loader;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import real.inkognito338.murdermysteryutils.Main;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 14.10.2026
 */

public class Analytics {

    private static final boolean DEBUG = false;

    private static final Logger LOGGER = LogManager.getLogger("MurderMysteryUtils");

    private static final String MEASUREMENT_ID = "G-7C3RKH2L0W";
    private static final String API_SECRET = "sH9LzJK7TfSrWNd10BqyMA";

    private static final String QUERY = "?measurement_id=" + MEASUREMENT_ID + "&api_secret=" + API_SECRET;

    // Боевой эндпоинт (ответ 204, тела нет)
    private static final String ENDPOINT = "https://www.google-analytics.com/mp/collect" + QUERY;

    // Эндпоинт валидации (возвращает JSON с validationMessages)
    private static final String DEBUG_ENDPOINT = "https://www.google-analytics.com/debug/mp/collect" + QUERY;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static void debug(String msg, Object... args) {
        if (DEBUG) LOGGER.info(msg, args);
    }

    public static void sendEvent(String eventName) {
        new Thread(() -> {
            try {
                debug("[Analytics] Начало отправки события: {}", eventName);

                // Собираем параметры
                Minecraft mc = Minecraft.getMinecraft();
                String language = mc.gameSettings != null ? mc.gameSettings.language : "unknown";
                int guiScale = mc.gameSettings != null ? mc.gameSettings.guiScale : 0;
                int renderDist = mc.gameSettings != null ? mc.gameSettings.renderDistanceChunks : 0;

                // Строим JSON через Gson
                JsonObject params = new JsonObject();
                params.addProperty("engagement_time_msec", 100); // обязательный параметр
                if (DEBUG) params.addProperty("debug_mode", 1);
                params.addProperty("mc_version", mc.getVersion());
                params.addProperty("mod_version", Main.VERSION);
                params.addProperty("forge_version", ForgeVersion.getVersion());
                params.addProperty("java_version", System.getProperty("java.version", "unknown"));
                params.addProperty("os_name", System.getProperty("os.name", "unknown"));
                params.addProperty("os_arch", System.getProperty("os.arch", "unknown"));
                params.addProperty("allocated_ram_mb", Runtime.getRuntime().maxMemory() / (1024 * 1024));
                params.addProperty("optifine_installed", Loader.isModLoaded("optifine"));
                params.addProperty("mods_count", Loader.instance().getActiveModList().size());
                params.addProperty("language", language);
                params.addProperty("gui_scale", guiScale);
                params.addProperty("render_distance", renderDist);

                debug("[Analytics] Параметры собраны: mc={}, java={}, os={}, ram={}mb",
                        mc.getVersion(),
                        System.getProperty("java.version"),
                        System.getProperty("os.name"),
                        Runtime.getRuntime().maxMemory() / (1024 * 1024));

                JsonObject event = new JsonObject();
                event.addProperty("name", eventName);
                event.add("params", params);

                JsonArray events = new JsonArray();
                events.add(event);

                JsonObject root = new JsonObject();
                root.addProperty("client_id", getClientId());
                root.add("events", events);

                String body = root.toString();
                debug("[Analytics] JSON тело: {}", body);

                // 1) В debug режиме сначала проверяем событие валидатором
                if (DEBUG) {
                    debug("[Analytics] Проверка события через валидатор (debug/mp/collect)...");
                    try {
                        String validation = post(DEBUG_ENDPOINT, body);
                        LOGGER.info("[Analytics] Результат валидатора Google: {}", validation);
                    } catch (Throwable t) {
                        LOGGER.warn("[Analytics] Валидатор недоступен: {}", t.getMessage());
                    }
                }

                // 2) Затем отправляем реальный запрос
                debug("[Analytics] Отправка события на mp/collect...");
                String result = post(ENDPOINT, body);
                debug("[Analytics] Событие успешно отправлено: {} (ответ: '{}')", eventName, result);

            } catch (Throwable t) {
                LOGGER.warn("[Analytics] Ошибка отправки события: {}", t.getMessage());
            }
        }, "Analytics-Thread").start();
    }

    /**
     * POST JSON на указанный URL (https → http fallback).
     * Возвращает тело ответа (у /mp/collect оно пустое, у /debug/mp/collect - JSON).
     */
    private static String post(String url, String body) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT) // обязательный User-Agent
                .post(RequestBody.create(JSON, body))
                .build();

        debug("[Analytics] Отправка через HTTPS: {}", url.substring(0, url.indexOf('?')));

        try (Response response = buildHttpClient(true).newCall(request).execute()) {
            String text = response.body() != null ? response.body().string() : "";
            debug("[Analytics] HTTPS ответ: {}", response.code());
            return text;
        } catch (Throwable t) {
            LOGGER.warn("[Analytics] HTTPS не удался, пробуем HTTP: {}", t.getMessage());
            debug("[Analytics] Отправка через HTTP...");

            Request httpRequest = request.newBuilder()
                    .url(url.replace("https://", "http://"))
                    .build();

            try (Response response = buildHttpClient(false).newCall(httpRequest).execute()) {
                String text = response.body() != null ? response.body().string() : "";
                debug("[Analytics] HTTP ответ: {}", response.code());
                return text;
            }
        }
    }

    private static OkHttpClient buildHttpClient(boolean useHttps) {
        if (useHttps) {
            try {
                SSLContext ctx = CertManager.getSSLContext();
                if (ctx != null) {
                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                            TrustManagerFactory.getDefaultAlgorithm());
                    tmf.init((KeyStore) null);
                    X509TrustManager tm = (X509TrustManager) tmf.getTrustManagers()[0];
                    debug("[Analytics] HTTPS-клиент создан через CertManager");
                    return new OkHttpClient.Builder()
                            .connectTimeout(5, TimeUnit.SECONDS)
                            .readTimeout(5, TimeUnit.SECONDS)
                            .sslSocketFactory(ctx.getSocketFactory(), tm)
                            .build();
                }
            } catch (Throwable t) {
                LOGGER.warn("[Analytics] Не удалось создать HTTPS-клиент: {}", t.getMessage());
            }
        }

        debug("[Analytics] Используем дефолтный HTTP-клиент");
        return new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Уникальный ID клиента - генерируется один раз и хранится в папке Minecraft.
     */
    private static String getClientId() {
        File file = new File(Minecraft.getMinecraft().mcDataDir, "murdermysteryutils_client_id.txt");

        if (file.exists()) {
            try {
                String id = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
                if (!id.isEmpty()) {
                    debug("[Analytics] client_id загружен: {}", id);
                    return id;
                }
            } catch (Throwable t) {
                LOGGER.warn("[Analytics] Не удалось прочитать client_id: {}", t.getMessage());
            }
        }

        String id = UUID.randomUUID().toString();
        debug("[Analytics] Сгенерирован новый client_id: {}", id);
        try {
            Files.write(file.toPath(), id.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            LOGGER.warn("[Analytics] Не удалось сохранить client_id: {}", t.getMessage());
        }
        return id;
    }
}