package real.inkognito338.murdermysteryutils.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 14.10.2026
 */

@SuppressWarnings("SpellCheckingInspection")
public class AutoAddServer {

    private static final Logger LOGGER = LogManager.getLogger("MurderMysteryUtils");

    private static final String CONFIG_URL   = "https://raw.githubusercontent.com/inkognito338/MurderMysteryUtils/main/server_config.json";
    private static final String DEFAULT_IP   = "ultramc.top";
    private static final String DEFAULT_NAME = "Выживание, SkyBlock, Vanilla, Мини-Игры";

    private static final long   THREE_MONTHS_MS = 90L * 24 * 60 * 60 * 1000;
    private static final String CFG_KEY         = "lastServerAdd";

    public void init() {
        new Thread(() -> {
            try {
                long lastAdd = ConfigManager.getLong(CFG_KEY, 0L);
                long now     = System.currentTimeMillis();

                if (lastAdd != 0 && (now - lastAdd) < THREE_MONTHS_MS) {
                    return;
                }

                String primaryIp  = DEFAULT_IP;
                String fallbackIp = "play.ultramc.top";
                String serverName = DEFAULT_NAME;

                // 1. Пробуем получить конфиг с GitHub (https → http fallback)
                try {
                    String url = CONFIG_URL;
                    OkHttpClient client = buildHttpClient(true);
                    Request request = new Request.Builder().url(url).get().build();
                    Response response = null;

                    try {
                        response = client.newCall(request).execute();
                    } catch (Throwable t) {
                        LOGGER.warn("[AutoAddServer] HTTPS не удался, пробуем HTTP: {}", t.getMessage());
                        url = CONFIG_URL.replace("https://", "http://");
                        client = buildHttpClient(false);
                        request = new Request.Builder().url(url).get().build();
                        response = client.newCall(request).execute();
                    }

                    if (response != null && response.isSuccessful()) {
                        ResponseBody body = response.body();
                        if (body != null) {
                            JsonObject json = new JsonParser().parse(body.string()).getAsJsonObject();
                            primaryIp  = json.get("primary_ip").getAsString();
                            fallbackIp = json.get("fallback_ip").getAsString();
                            serverName = json.get("name").getAsString();
                            response.close();
                        }
                    }
                } catch (Throwable t) {
                    LOGGER.warn("[AutoAddServer] Не удалось получить конфиг с GitHub, используем дефолтные значения: {}", t.getMessage());
                }

                // 2. Пингуем primary, если не отвечает — берём fallback
                String resolvedIp;
                try {
                    resolvedIp = pingable(primaryIp, 250) ? primaryIp
                            : pingable(fallbackIp, 250) ? fallbackIp
                            : primaryIp;
                } catch (Throwable t) {
                    LOGGER.warn("[AutoAddServer] Ошибка при пинге серверов, используем primary: {}", t.getMessage());
                    resolvedIp = primaryIp;
                }

                // 3. Добавляем в список серверов первым
                try {
                    addFirst(resolvedIp, serverName);
                } catch (Throwable t) {
                    LOGGER.warn("[AutoAddServer] Не удалось добавить сервер в список: {}", t.getMessage());
                    return;
                }

                // 4. Сохраняем время добавления
                try {
                    ConfigManager.setLong(CFG_KEY, now);
                    ConfigManager.save();
                } catch (Throwable t) {
                    LOGGER.warn("[AutoAddServer] Не удалось сохранить конфиг: {}", t.getMessage());
                }

            } catch (Throwable t) {
                LOGGER.error("[AutoAddServer] Неожиданная ошибка в потоке: {}", t.getMessage());
            }
        }, "AutoAddServer").start();
    }

    private OkHttpClient buildHttpClient(boolean useHttps) {
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
                LOGGER.warn("[AutoAddServer] Не удалось создать HTTPS-клиент через CertManager: {}", t.getMessage());
            }
        }

        return new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    private boolean pingable(String host, int timeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, 25565), timeout);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[AutoAddServer] Хост недоступен {}: {}", host, t.getMessage());
            return false;
        }
    }

    private void addFirst(String ip, String name) {
        ServerList serverList = new ServerList(Minecraft.getMinecraft());
        serverList.loadServerList();

        for (int i = 0; i < serverList.countServers(); i++) {
            if (ip.equalsIgnoreCase(serverList.getServerData(i).serverIP)) {
                if (i == 0) return;
                serverList.removeServerData(i);
                break;
            }
        }

        serverList.addServerData(new ServerData(name, ip, false));
        serverList.swapServers(serverList.countServers() - 1, 0);
        serverList.saveServerList();
    }
}