package real.inkognito338.murdermysteryutils.utils.analytics;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import real.inkognito338.murdermysteryutils.Main;
import real.inkognito338.murdermysteryutils.utils.CertManager;

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
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

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

    private static String post(String url, String body) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent",USER_AGENT)
                .post(RequestBody.create(JSON, body))
                .build();

        try (Response response = buildHttpClient(true).newCall(request).execute()) {
            String text = response.body() != null ? response.body().string() : "";
            debug("[Mixpanel] HTTPS ответ код: {}", response.code());
            return text;
        } catch (Throwable t) {
            LOGGER.warn("[Mixpanel] Ошибка отправки запроса: {}", t.getMessage());
            throw t;
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
                    return new OkHttpClient.Builder()
                            .connectTimeout(5, TimeUnit.SECONDS)
                            .readTimeout(5, TimeUnit.SECONDS)
                            .sslSocketFactory(ctx.getSocketFactory(), tm)
                            .build();
                }
            } catch (Throwable t) {
                LOGGER.warn("[Mixpanel] Не удалось создать SSL-клиент с CertManager: {}", t.getMessage());
            }
        }

        return new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
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