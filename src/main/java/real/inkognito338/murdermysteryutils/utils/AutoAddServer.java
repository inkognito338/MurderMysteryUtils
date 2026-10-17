package real.inkognito338.murdermysteryutils.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;

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

    private static final int HTTP_TIMEOUT_MS = 5_000;
    private static final int MAX_REDIRECTS   = 5;

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
                    String body;
                    try {
                        body = httpGet(CONFIG_URL, true);
                    } catch (Throwable t) {
                        LOGGER.warn("[AutoAddServer] HTTPS не удался, пробуем HTTP: {}", t.getMessage());
                        body = httpGet(CONFIG_URL.replace("https://", "http://"), false);
                    }

                    if (body != null) {
                        JsonObject json = new JsonParser().parse(body).getAsJsonObject();
                        primaryIp  = json.get("primary_ip").getAsString();
                        fallbackIp = json.get("fallback_ip").getAsString();
                        serverName = json.get("name").getAsString();
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

    /**
     * Возвращает SSLSocketFactory из CertManager или null (дефолтный JVM SSL).
     */
    private SSLSocketFactory buildSslFactory() {
        try {
            SSLContext ctx = CertManager.getSSLContext();
            if (ctx != null) {
                return ctx.getSocketFactory();
            }
        } catch (Throwable t) {
            LOGGER.warn("[AutoAddServer] Не удалось создать HTTPS-клиент через CertManager: {}", t.getMessage());
        }
        return null;
    }

    /**
     * GET-запрос. Возвращает тело ответа или null, если код ответа не 2xx.
     * Сетевые ошибки пробрасываются наружу (нужны для fallback https → http).
     * Редиректы ведутся вручную, в том числе между http и https (как делал OkHttp).
     */
    private String httpGet(String url, boolean useCertManager) throws IOException {
        SSLSocketFactory factory = useCertManager ? buildSslFactory() : null;
        String current = url;

        for (int hops = 0; hops <= MAX_REDIRECTS; hops++) {
            URL u = new URL(current);
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            try {
                if (factory != null && conn instanceof HttpsURLConnection) {
                    ((HttpsURLConnection) conn).setSSLSocketFactory(factory);
                }
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(HTTP_TIMEOUT_MS);
                conn.setReadTimeout(HTTP_TIMEOUT_MS);
                conn.setInstanceFollowRedirects(false);

                int code = conn.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = conn.getHeaderField("Location");
                    if (location == null) throw new IOException("Redirect without Location for " + current);
                    current = new URL(u, location).toString();
                    continue;
                }
                if (code < 200 || code >= 300) {
                    LOGGER.warn("[AutoAddServer] HTTP {} для {}", code, current);
                    return null;
                }

                try (InputStream in = conn.getInputStream()) {
                    if (in == null) return null;
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                    }
                    return new String(out.toByteArray(), StandardCharsets.UTF_8);
                }
            } finally {
                conn.disconnect();
            }
        }
        throw new IOException("Too many redirects for " + url);
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