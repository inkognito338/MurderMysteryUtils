package real.inkognito338.murdermysteryutils.utils;

import real.inkognito338.murdermysteryutils.Main;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 *
 * Проба доступности: обычный HTTPS -> HTTPS в режиме совместимости TLS (старые Java 8) ->
 * http:// как крайний случай (результат помечается insecure и в автовыбор не попадает).
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

    /** Короткий таймаут специально для проверки доступности - команда не должна долго висеть. */
    private static final int PROBE_TIMEOUT_MS = 2_000;

    /**
     * null = дефолтный JVM SSL. После обновления сертификатов подменяется
     * compat-фабрикой из CertManager (с нашими корнями и TLS 1.2+).
     */
    private static volatile SSLSocketFactory probeSslFactory = null;

    private static synchronized void rebuildProbeSslFactoryWithFreshCerts() {
        try {
            Main.LOGGER.info("[MirrorManager] Untrusted certificate, refreshing certs and rebuilding probe SSL factory...");
            CertManager.refreshCerts();
            SSLContext ctx = CertManager.getSSLContext();
            if (ctx == null) {
                Main.LOGGER.error("[MirrorManager] CertManager returned null SSLContext after refresh");
                return;
            }
            probeSslFactory = CertManager.getCompatSocketFactory();
            Main.LOGGER.info("[MirrorManager] Probe SSL factory rebuilt successfully with fresh certs");
        } catch (Exception e) {
            Main.LOGGER.error("[MirrorManager] Failed to rebuild probe SSL factory: {}", e.getMessage());
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
    public static final String TAGS_SCRIPT_PATH = "API/tags.lua";

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

    /** https://... -> http://... (для крайнего случая). Остальные URL возвращаются как есть. */
    public static String downgradeToHttp(String url) {
        return url.startsWith("https://") ? "http://" + url.substring(8) : url;
    }

    /**
     * Результат проверки доступности зеркала.
     */
    public static class ProbeResult {
        public final Mirror mirror;
        public final boolean reachable;
        public final long latencyMs;
        /** OK, OK_HTTP, TIMEOUT, DNS_ERROR, SSL_ERROR, HTTP_4XX, HTTP_5XX, EMPTY_BODY, IO_ERROR */
        public final String reasonCode;
        /** Полная причина для лога. */
        public final String reasonDetail;
        /** Краткая причина для вывода в чат. */
        public final String shortDetail;
        /** true — зеркало ответило только по голому http:// (нет шифрования и проверки подлинности). */
        public final boolean insecure;

        ProbeResult(Mirror mirror, boolean reachable, long latencyMs,
                    String reasonCode, String reasonDetail, String shortDetail, boolean insecure) {
            this.mirror = mirror;
            this.reachable = reachable;
            this.latencyMs = latencyMs;
            this.reasonCode = reasonCode;
            this.reasonDetail = reasonDetail;
            this.shortDetail = shortDetail;
            this.insecure = insecure;
        }

        /** Для случаев где краткий и полный detail совпадают (HTTP-ошибки, пустое тело). */
        ProbeResult(Mirror mirror, boolean reachable, long latencyMs,
                    String reasonCode, String reasonDetail, boolean insecure) {
            this(mirror, reachable, latencyMs, reasonCode, reasonDetail, reasonDetail, insecure);
        }

        ProbeResult(Mirror mirror, boolean reachable, long latencyMs, String reasonCode, String reasonDetail) {
            this(mirror, reachable, latencyMs, reasonCode, reasonDetail, reasonDetail, false);
        }
    }

    /**
     * Синхронно проверяет доступность одного зеркала лёгким GET-запросом (check_update.lua)
     * с коротким таймаутом. Блокирующий вызов - только из фонового потока.
     *
     * Шаги при SSL-ошибке:
     *  1) если проблема доверия - обновляем сертификаты и повторяем;
     *  2) иначе (handshake_failure и т.п.) - повтор через CertManager.getCompatSocketFactory();
     *  3) крайний случай - http://. Успех помечается insecure.
     */
    public static ProbeResult probe(Mirror mirror) {
        String url = resolveUrl(mirror, CHECK_UPDATE_SCRIPT_PATH);
        long start = System.currentTimeMillis();
        SSLException lastSsl;

        // Шаг 1: обычная попытка
        try {
            return executeProbe(mirror, url, start, probeSslFactory, false);
        } catch (SSLException e) {
            lastSsl = e;
            if (CertManager.isTrustProblem(e)) {
                Main.LOGGER.warn("[MirrorManager] Trust problem for {}: {}", url, e.getMessage());
                rebuildProbeSslFactoryWithFreshCerts();
            } else {
                Main.LOGGER.warn("[MirrorManager] TLS negotiation failed for {} ({}), retrying in compat mode",
                        url, e.getMessage());
            }
        } catch (Exception e) {
            return probeFailure(mirror, url, start, e);
        }

        // Шаг 2: compat-режим (TLS 1.2+, без SSLv2Hello, расширенный набор шифров)
        try {
            return executeProbe(mirror, url, start, CertManager.getCompatSocketFactory(), false);
        } catch (SSLException e) {
            lastSsl = e;
        } catch (Exception e) {
            return probeFailure(mirror, url, start, e);
        }

        // Шаг 3: http:// как крайний случай
        if (url.startsWith("https://")) {
            String httpUrl = downgradeToHttp(url);
            try {
                ProbeResult r = executeProbe(mirror, httpUrl, start, null, true);
                if (r.reachable) {
                    Main.LOGGER.warn("[MirrorManager] {} reachable ONLY via plain HTTP (insecure)", mirror.id);
                    return r;
                }
            } catch (Exception e) {
                Main.LOGGER.debug("[MirrorManager] HTTP fallback failed for {}: {}", httpUrl, e.getMessage());
            }
        }

        return probeFailure(mirror, url, start, lastSsl);
    }

    private static ProbeResult executeProbe(Mirror mirror, String url, long start,
                                            SSLSocketFactory factory, boolean viaHttp) throws IOException {
        String currentUrl = url;
        for (int hops = 0; hops <= 3; hops++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(currentUrl).openConnection();
            try {
                if (factory != null && conn instanceof HttpsURLConnection) {
                    ((HttpsURLConnection) conn).setSSLSocketFactory(factory);
                }
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(PROBE_TIMEOUT_MS);
                conn.setReadTimeout(PROBE_TIMEOUT_MS);
                conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) MurderMysteryUtils/" + Main.VERSION);

                int code = conn.getResponseCode();
                long latency = System.currentTimeMillis() - start;

                // Редирект — следуем вручную с той же factory
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = conn.getHeaderField("Location");
                    if (location == null) {
                        return new ProbeResult(mirror, false, latency, "IO_ERROR",
                                "Redirect without Location from " + currentUrl, "нет Location в редиректе", viaHttp);
                    }
                    currentUrl = new java.net.URL(new java.net.URL(currentUrl), location).toString();
                    if (currentUrl.startsWith("https://")) viaHttp = false;
                    continue;
                }

                if (code < 200 || code >= 300) {
                    String reasonCode = code >= 500 ? "HTTP_5XX" : "HTTP_4XX";
                    String detail = "HTTP " + code + " for " + currentUrl;
                    Main.LOGGER.warn("[MirrorManager] Probe failed for {}: {} ({} ms)", mirror.id, detail, latency);
                    return new ProbeResult(mirror, false, latency, reasonCode, detail, "HTTP " + code, viaHttp);
                }

                try (InputStream in = conn.getInputStream()) {
                    if (in == null || in.read() == -1) {
                        String detail = "empty response body from " + currentUrl;
                        Main.LOGGER.warn("[MirrorManager] Probe failed for {}: {} ({} ms)", mirror.id, detail, latency);
                        return new ProbeResult(mirror, false, latency, "EMPTY_BODY", detail, "пустой ответ", viaHttp);
                    }
                }

                if (viaHttp) {
                    return new ProbeResult(mirror, true, latency, "OK_HTTP",
                            "HTTP " + code + " (без шифрования!)", "HTTP " + code + " (без шифрования!)", true);
                }
                Main.LOGGER.info("[MirrorManager] Probe OK for {} ({} ms)", mirror.id, latency);
                return new ProbeResult(mirror, true, latency, "OK", "HTTP " + code, "HTTP " + code, false);
            } finally {
                conn.disconnect();
            }
        }
        throw new IOException("Too many redirects for " + url);
    }

    private static ProbeResult probeFailure(Mirror mirror, String url, long start, Exception e) {
        long latency = System.currentTimeMillis() - start;
        String reasonCode;
        String detail;      // полный текст → только в лог
        String shortDetail; // краткий текст → в чат

        if (e instanceof UnknownHostException) {
            reasonCode = "DNS_ERROR";
            detail = "DNS resolution failed for " + url + ": " + e.getMessage();
            shortDetail = "нет DNS";
        } else if (e instanceof SocketTimeoutException) {
            reasonCode = "TIMEOUT";
            detail = "connection/read timeout for " + url;
            shortDetail = "таймаут";
        } else if (e instanceof SSLException) {
            reasonCode = "SSL_ERROR";
            detail = "SSL error for " + url + ": " + e.getClass().getSimpleName() + " - " + e.getMessage()
                    + " (возможно, ваша Java слишком старая для этого зеркала)";
            shortDetail = "SSL ошибка";
        } else {
            reasonCode = "IO_ERROR";
            detail = e.getClass().getSimpleName() + " for " + url + ": " + e.getMessage();
            shortDetail = "ошибка соединения";
        }

        Main.LOGGER.warn("[MirrorManager] Probe failed for {}: {} ({} ms)", mirror.id, detail, latency, e);
        return new ProbeResult(mirror, false, latency, reasonCode, detail, shortDetail, false);
    }

    /**
     * Результат автоматического подбора зеркала: лучший найденный вариант
     * (может быть null) и полный список проб по всем проверенным зеркалам.
     */
    public static class AutoPickResult {
        public final Mirror best;
        public final List<ProbeResult> allProbes;

        AutoPickResult(Mirror best, List<ProbeResult> allProbes) {
            this.best = best;
            this.allProbes = allProbes;
        }
    }

    /**
     * Параллельно проверяет все рекомендуемые зеркала и возвращает самое быстрое.
     * Зеркала, доступные только по http:// (insecure), в автовыбор не попадают.
     * Блокирующий вызов - только из фонового потока.
     */
    public static AutoPickResult pickBestMirror() {
        List<Mirror> candidates = new ArrayList<>();
        for (Mirror m : Mirror.values()) {
            if (m.recommended) candidates.add(m);
        }

        List<CompletableFuture<ProbeResult>> futures = new ArrayList<>();
        for (Mirror m : candidates) {
            futures.add(CompletableFuture.supplyAsync(() -> probe(m)));
        }

        List<ProbeResult> results = new ArrayList<>();
        for (CompletableFuture<ProbeResult> f : futures) {
            try {
                results.add(f.get(8, TimeUnit.SECONDS));
            } catch (Exception e) {
                Main.LOGGER.warn("[MirrorManager] Auto-pick probe future failed: {}: {}",
                        e.getClass().getSimpleName(), e.getMessage());
            }
        }

        Mirror best = null;
        long bestLatency = Long.MAX_VALUE;
        for (ProbeResult r : results) {
            if (r.reachable && !r.insecure && r.latencyMs < bestLatency) {
                best = r.mirror;
                bestLatency = r.latencyMs;
            }
        }

        if (best != null) {
            Main.LOGGER.info("[MirrorManager] Auto-pick selected {} ({} ms)", best.id, bestLatency);
        } else {
            Main.LOGGER.warn("[MirrorManager] Auto-pick found no reachable secure mirror");
        }

        return new AutoPickResult(best, results);
    }
}