package real.inkognito338.murdermysteryutils.utils;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import real.inkognito338.murdermysteryutils.Main;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 *
 * Хранит доступные зеркала для загрузки Lua-скриптов и текущий выбор пользователя.
 * По умолчанию используется только GitHub. Никакого автоматического перебора зеркал -
 * если основной источник недоступен, пользователю нужно явно выбрать другой командой .mirror.
 * Доступные зеркала: GitHub, Codeberg, GitFlic, GitLab, Statically (CDN), jsDelivr (CDN).
 */
public class MirrorManager {

    public enum Mirror {
        GITHUB("github", "GitHub",
                "https://raw.githubusercontent.com/inkognito338/MurderMysteryUtils/main", true),
        CODEBERG("codeberg", "Codeberg",
                "https://codeberg.org/inkognito338/MurderMysteryUtils/raw/branch/main", true),
        GITFLIC("gitflic", "GitFlic",
                "https://gitflic.ru/project/inkognito338/murdermysteryutils/blob/raw?file=", false),
        GITLAB("gitlab", "GitLab",
                "https://gitlab.com/real.inkognito338/murder-mystery-utils/-/raw/main", true),
        STATICALLY("statically", "Statically",
                "https://cdn.statically.io/gh/inkognito338/MurderMysteryUtils@main", true),
        JSDELIVR("jsdelivr", "jsDelivr",
                "https://cdn.jsdelivr.net/gh/inkognito338/MurderMysteryUtils@main", true);

        public final String id;
        public final String displayName;
        public final String baseUrl;
        /** Участвует ли зеркало в автоматическом выборе (.mirror auto). GitFlic исключён по умолчанию. */
        public final boolean recommended;

        Mirror(String id, String displayName, String baseUrl, boolean recommended) {
            this.id = id;
            this.displayName = displayName;
            this.baseUrl = baseUrl;
            this.recommended = recommended;
        }

        public static Mirror byId(String id) {
            if (id == null) return null;
            for (Mirror m : values()) {
                if (m.id.equalsIgnoreCase(id)) return m;
            }
            return null;
        }
    }

    private static final String CONFIG_KEY = "selectedMirror";
    private static final Mirror DEFAULT_MIRROR = Mirror.GITHUB;

    private static volatile Mirror current = null;

    // Отдельный клиент с коротким таймаутом специально для проверки доступности -
    // не должен влиять на основной httpClient в API и не должен долго висеть в команде.
    // Инициализируется дефолтным JVM SSL; при SSLHandshakeException пересобирается
    // через CertManager (см. rebuildProbeClientWithFreshCerts), как в CmdGetSkin.
    private static volatile OkHttpClient PROBE_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build();

    private static synchronized void rebuildProbeClientWithFreshCerts() {
        try {
            Main.LOGGER.info("[MirrorManager] SSL error detected, refreshing certs and rebuilding probe client...");
            CertManager.refreshCerts();
            SSLContext ctx = CertManager.getSSLContext();
            if (ctx == null) {
                Main.LOGGER.error("[MirrorManager] CertManager returned null SSLContext after refresh");
                return;
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
            X509TrustManager tm = (X509TrustManager) tmf.getTrustManagers()[0];
            PROBE_CLIENT = PROBE_CLIENT.newBuilder().sslSocketFactory(ctx.getSocketFactory(), tm).build();
            Main.LOGGER.info("[MirrorManager] Probe client rebuilt successfully with fresh certs");
        } catch (Exception e) {
            Main.LOGGER.error("[MirrorManager] Failed to rebuild probe client: {}", e.getMessage());
        }
    }

    /**
     * Пути скриптов относительно корня репозитория, одинаковые для всех зеркал,
     * кроме GitFlic, где имя файла передаётся как query-параметр (без "/").
     */
    public static final String API_SCRIPT_PATH = "API/API.lua";
    public static final String AUTONEXT_SCRIPT_PATH = "API/AutoNext.lua";
    public static final String CHECK_UPDATE_SCRIPT_PATH = "API/check_update.lua";
    public static final String CHATIGNORE_SCRIPT_PATH = "API/chatignore.lua";

    private MirrorManager() {}

    public static synchronized Mirror getCurrent() {
        if (current == null) {
            String saved = ConfigManager.getString(CONFIG_KEY, DEFAULT_MIRROR.id);
            Mirror resolved = Mirror.byId(saved);
            current = resolved != null ? resolved : DEFAULT_MIRROR;
        }
        return current;
    }

    public static synchronized void setCurrent(Mirror mirror) {
        if (mirror == null) return;
        current = mirror;
        ConfigManager.setString(CONFIG_KEY, mirror.id);
        ConfigManager.save();
    }

    public static boolean isDefault() {
        return getCurrent() == DEFAULT_MIRROR;
    }

    public static Mirror getDefault() {
        return DEFAULT_MIRROR;
    }

    public static Map<String, Mirror> allById() {
        Map<String, Mirror> map = new LinkedHashMap<>();
        for (Mirror m : Mirror.values()) {
            map.put(m.id, m);
        }
        return map;
    }

    /**
     * Строит итоговый URL для заданного относительного пути скрипта на текущем зеркале.
     */
    public static String resolveUrl(String relativePath) {
        return resolveUrl(getCurrent(), relativePath);
    }

    public static String resolveUrl(Mirror mirror, String relativePath) {
        if (mirror == Mirror.GITFLIC) {
            // GitFlic отдаёт raw через query-параметр file=API%2FXxx.lua
            String encoded = relativePath.replace("/", "%2F");
            return mirror.baseUrl + encoded;
        }
        if (mirror == Mirror.GITLAB) {
            // GitLab raw-эндпоинт: .../raw/main/<path>?ref_type=heads
            return mirror.baseUrl + "/" + relativePath + "?ref_type=heads";
        }
        return mirror.baseUrl + "/" + relativePath;
    }

    /**
     * Результат проверки доступности зеркала.
     */
    public static class ProbeResult {
        public final Mirror mirror;
        public final boolean reachable;
        public final long latencyMs;
        /** Краткая машинно-понятная причина: OK, TIMEOUT, DNS_ERROR, SSL_ERROR, HTTP_4XX, HTTP_5XX, EMPTY_BODY, IO_ERROR */
        public final String reasonCode;
        /** Человекочитаемая причина для вывода в чат/лог, например "HTTP 404" или "SSLHandshakeException: ..." */
        public final String reasonDetail;

        ProbeResult(Mirror mirror, boolean reachable, long latencyMs, String reasonCode, String reasonDetail) {
            this.mirror = mirror;
            this.reachable = reachable;
            this.latencyMs = latencyMs;
            this.reasonCode = reasonCode;
            this.reasonDetail = reasonDetail;
        }
    }

    /**
     * Синхронно проверяет доступность одного зеркала лёгким GET-запросом
     * небольшого файла (check_update.lua) с коротким таймаутом. Блокирующий
     * вызов - использовать только из фонового потока, не из чат/тик-потока.
     *
     * В отличие от простого true/false, сохраняет причину сбоя (DNS, таймаут,
     * SSL, HTTP-код и т.д.) и всегда логирует её в Main.LOGGER, чтобы в логах
     * было видно, почему конкретное зеркало не отвечает.
     */
    public static ProbeResult probe(Mirror mirror) {
        String url = resolveUrl(mirror, CHECK_UPDATE_SCRIPT_PATH);
        long start = System.currentTimeMillis();

        try {
            return executeProbe(PROBE_CLIENT, mirror, url, start);
        } catch (SSLHandshakeException e) {
            Main.LOGGER.warn("[MirrorManager] SSL handshake failed, refreshing certs and retrying: {}", url);
            rebuildProbeClientWithFreshCerts();
            try {
                return executeProbe(PROBE_CLIENT, mirror, url, start);
            } catch (Exception retryEx) {
                return probeFailure(mirror, url, start, retryEx);
            }
        } catch (Exception e) {
            return probeFailure(mirror, url, start, e);
        }
    }

    private static ProbeResult executeProbe(OkHttpClient client, Mirror mirror, String url, long start) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) MurderMysteryUtils/" + Main.VERSION)
                .build();

        try (Response response = client.newCall(request).execute()) {
            long latency = System.currentTimeMillis() - start;
            int code = response.code();
            String actualUrl = response.request().url().toString();

            if (!response.isSuccessful()) {
                String reasonCode = code >= 500 ? "HTTP_5XX" : "HTTP_4XX";
                String detail = "HTTP " + code + " for " + actualUrl;
                Main.LOGGER.warn("[MirrorManager] Probe failed for {}: {} ({} ms)", mirror.id, detail, latency);
                return new ProbeResult(mirror, false, latency, reasonCode, detail);
            }

            if (response.body() == null) {
                String detail = "empty response body from " + actualUrl;
                Main.LOGGER.warn("[MirrorManager] Probe failed for {}: {} ({} ms)", mirror.id, detail, latency);
                return new ProbeResult(mirror, false, latency, "EMPTY_BODY", detail);
            }

            Main.LOGGER.info("[MirrorManager] Probe OK for {} ({} ms)", mirror.id, latency);
            return new ProbeResult(mirror, true, latency, "OK", "HTTP " + code);
        }
    }

    private static ProbeResult probeFailure(Mirror mirror, String url, long start, Exception e) {
        long latency = System.currentTimeMillis() - start;
        String reasonCode;
        String detail;

        if (e instanceof UnknownHostException) {
            reasonCode = "DNS_ERROR";
            detail = "DNS resolution failed for " + url + ": " + e.getMessage();
        } else if (e instanceof SocketTimeoutException) {
            reasonCode = "TIMEOUT";
            detail = "connection/read timeout for " + url;
        } else if (e instanceof SSLException) {
            reasonCode = "SSL_ERROR";
            detail = "SSL error for " + url + ": " + e.getClass().getSimpleName() + " - " + e.getMessage();
        } else {
            reasonCode = "IO_ERROR";
            detail = e.getClass().getSimpleName() + " for " + url + ": " + e.getMessage();
        }

        Main.LOGGER.warn("[MirrorManager] Probe failed for {}: {} ({} ms)", mirror.id, detail, latency, e);
        return new ProbeResult(mirror, false, latency, reasonCode, detail);
    }

    /**
     * Результат автоматического подбора зеркала: лучший найденный вариант
     * (может быть null, если ни одно рекомендуемое зеркало не ответило)
     * и полный список проб по всем проверенным зеркалам - для диагностики.
     */
    public static class AutoPickResult {
        public final Mirror best;
        public final java.util.List<ProbeResult> allProbes;

        AutoPickResult(Mirror best, java.util.List<ProbeResult> allProbes) {
            this.best = best;
            this.allProbes = allProbes;
        }
    }

    /**
     * Параллельно проверяет доступность всех рекомендуемых зеркал (GitFlic исключён -
     * см. Mirror.recommended) и возвращает самое быстрое из доступных. Блокирующий вызов,
     * использовать только из фонового потока (не из чат/тик-потока).
     */
    public static AutoPickResult pickBestMirror() {
        java.util.List<Mirror> candidates = new java.util.ArrayList<>();
        for (Mirror m : Mirror.values()) {
            if (m.recommended) candidates.add(m);
        }

        java.util.List<CompletableFuture<ProbeResult>> futures = new java.util.ArrayList<>();
        for (Mirror m : candidates) {
            futures.add(CompletableFuture.supplyAsync(() -> probe(m)));
        }

        java.util.List<ProbeResult> results = new java.util.ArrayList<>();
        for (CompletableFuture<ProbeResult> f : futures) {
            try {
                results.add(f.get(5, TimeUnit.SECONDS));
            } catch (Exception e) {
                Main.LOGGER.warn("[MirrorManager] Auto-pick probe future failed: {}: {}",
                        e.getClass().getSimpleName(), e.getMessage());
            }
        }

        Mirror best = null;
        long bestLatency = Long.MAX_VALUE;
        for (ProbeResult r : results) {
            if (r.reachable && r.latencyMs < bestLatency) {
                best = r.mirror;
                bestLatency = r.latencyMs;
            }
        }

        if (best != null) {
            Main.LOGGER.info("[MirrorManager] Auto-pick selected {} ({} ms)", best.id, bestLatency);
        } else {
            Main.LOGGER.warn("[MirrorManager] Auto-pick found no reachable recommended mirror");
        }

        return new AutoPickResult(best, results);
    }
}