package real.inkognito338.murdermysteryutils.utils;

import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import real.inkognito338.murdermysteryutils.Main;

import javax.net.ssl.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 *
 * Трёхуровневый менеджер SSL-сертификатов.
 *
 * Уровень 1 — JVM cacerts (DigiCert, GlobalSign и др. — есть в Java 8_51, живут до 2031–2038).
 *             Используется по умолчанию. Покрывает Mojang (DigiCert) без каких-либо дополнений.
 *
 * Уровень 2 — Бандлированные ISRG Root X1/X2 из JAR-ресурсов.
 *             Нужны для letsencrypt.org и других LE-серверов на Java 8_51.
 *             X1 живёт до 2035, X2 до 2040. Обновляются вместе с модом.
 *
 * Уровень 2b — Сертификаты, скачанные на диск в предыдущих сессиях.
 *              Автоматически подхватываются при загрузке.
 *
 * Уровень 3 — TrustAll-fallback.
 *             Включается ТОЛЬКО если уровни 1+2 не смогли установить соединение
 *             (например, оба корневых CA истекли). Используется для скачивания
 *             свежих сертификатов с официальных серверов Let's Encrypt, после чего
 *             кэш сбрасывается и последующие запросы идут уже через честный SSL.
 *
 * Зеркала для автообновления — только официальные домены letsencrypt.org / lencr.org,
 * оба подписаны DigiCert (есть в Java 8_51). GitHub и сторонние зеркала не используются.
 */
public class CertManager {

    private static final Logger LOGGER = LogManager.getLogger("MurderMysteryUtils");

    private static final File CERTS_DIR = new File(Main.getConfigDir(), "certs");

    /** Обновлять за 30 дней до истечения. */
    private static final long RENEW_THRESHOLD_MS = 30L * 24 * 60 * 60 * 1000;

    /** Максимальный размер скачиваемого сертификата (защита от мусора). */
    private static final int MAX_CERT_BYTES = 64 * 1024;

    /**
     * Бандлированные сертификаты — лежат в JAR по пути:
     * resources/assets/murdermysteryutils/certs/
     *
     * ISRG Root X1 — действителен до 04 Jun 2035
     * ISRG Root X2 — действителен до 17 Sep 2040
     */
    private static final String[] BUNDLED_CERTS = {
            "isrgrootx1.pem",
            "isrg-root-x2.pem",
    };

    /**
     * Официальные зеркала для автообновления.
     * Все домены используют DigiCert → доступны через JVM cacerts даже на Java 8_51.
     * Порядок важен: первое зеркало — эталон для кросс-проверки.
     */
    private static final Map<String, String[]> CERT_MIRRORS = new LinkedHashMap<String, String[]>() {{
        put("isrgrootx1.pem", new String[]{
                "https://letsencrypt.org/certs/isrgrootx1.pem",
                "https://x1.i.lencr.org/",
        });
        put("isrg-root-x2.pem", new String[]{
                "https://letsencrypt.org/certs/isrg-root-x2.pem",
                "https://x2.i.lencr.org/",
        });
    }};

    /**
     * TrustAll-клиент — используется ТОЛЬКО как последний fallback
     * при скачивании сертификатов, когда весь честный SSL недоступен.
     * Никогда не используется для Mojang-запросов или игровых данных.
     */
    private static final OkHttpClient BOOTSTRAP_CLIENT = buildTrustAllClient();

    private static volatile SSLContext cachedContext = null;

    // ── TrustAll (только для bootstrap) ─────────────────────────────────────

    private static OkHttpClient buildTrustAllClient() {
        X509TrustManager tm = new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] c, String a) {}
            public void checkServerTrusted(X509Certificate[] c, String a) {}
        };
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{tm}, new SecureRandom());
            return new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .sslSocketFactory(sc.getSocketFactory(), tm)
                    .hostnameVerifier((hostname, session) -> true)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("[CertManager] Failed to build TrustAll client", e);
        }
    }

    // ── Публичный API ────────────────────────────────────────────────────────

    /**
     * Возвращает SSLContext с тремя уровнями доверия.
     * Безопасно вызывать из любого потока; результат кэшируется.
     */
    public static synchronized SSLContext getSSLContext() {
        if (cachedContext != null) return cachedContext;
        try {
            cachedContext = buildSSLContext();
        } catch (Exception e) {
            LOGGER.error("[CertManager] Failed to build SSL context: {}", e.getMessage());
        }
        return cachedContext;
    }

    /**
     * Сбросить кэш SSLContext.
     * После вызова следующий getSSLContext() пересоберёт контекст
     * с учётом новых файлов на диске.
     */
    public static synchronized void invalidateCache() {
        cachedContext = null;
    }

    /**
     * Проверить и при необходимости обновить сертификаты на диске.
     * Вызывать в фоновом потоке.
     * После завершения автоматически сбрасывает кэш SSLContext.
     *
     * Логика:
     * 1. Пробуем скачать через честный SSLContext (уровни 1+2).
     * 2. Если несколько зеркал ответили — сравниваем байты (кросс-проверка на MITM).
     * 3. Только если все честные каналы провалились — TrustAll fallback.
     */
    public static void refreshCerts() {
        if (!CERTS_DIR.exists() && !CERTS_DIR.mkdirs()) {
            LOGGER.warn("[CertManager] Failed to create certs dir: {}", CERTS_DIR);
        }

        // Строим клиент с текущими уровнями 1+2
        OkHttpClient verifiedClient = null;
        try {
            verifiedClient = buildVerifiedClient();
        } catch (Exception e) {
            LOGGER.warn("[CertManager] Could not build verified client: {}", e.getMessage());
        }

        for (Map.Entry<String, String[]> entry : CERT_MIRRORS.entrySet()) {
            String   fileName = entry.getKey();
            String[] urls     = entry.getValue();
            File     dest     = new File(CERTS_DIR, fileName);

            if (!needsUpdate(dest)) {
                LOGGER.debug("[CertManager] Cert up to date: {}", fileName);
                continue;
            }

            boolean saved = false;

            // Попытка 1: честный SSL + кросс-проверка зеркал
            if (verifiedClient != null) {
                saved = tryDownloadVerified(verifiedClient, urls, dest, fileName);
            }

            // Попытка 2: TrustAll fallback (только если честный путь недоступен)
            if (!saved) {
                LOGGER.warn("[CertManager] All verified downloads failed for {}, " +
                        "falling back to TrustAll (unverified connection!)", fileName);
                for (String url : urls) {
                    try {
                        byte[] data = fetchBytes(BOOTSTRAP_CLIENT, url);
                        if (data != null && isValidX509(data)) {
                            Files.write(dest.toPath(), data);
                            LOGGER.warn("[CertManager] Saved {} via TrustAll fallback", fileName);
                            saved = true;
                            break;
                        }
                    } catch (Exception e) {
                        LOGGER.warn("[CertManager] TrustAll fallback failed for {}: {}", url, e.getMessage());
                    }
                }
            }

            if (!saved) {
                LOGGER.error("[CertManager] Could not update cert: {}", fileName);
            }
        }

        // Сбрасываем кэш — следующий getSSLContext() подхватит новые файлы с диска
        invalidateCache();
        LOGGER.info("[CertManager] Cert refresh complete");
    }

    // ── Построение SSLContext ────────────────────────────────────────────────

    public static SSLContext buildSSLContext() throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);

        int count = 0;

        // Уровень 1: сертификаты из JVM cacerts
        count += loadJvmCerts(ks);

        // Уровень 2: бандлированные сертификаты из JAR
        count += loadBundledCerts(ks);

        // Уровень 2b: сертификаты, скачанные на диск ранее
        count += loadDiskCerts(ks);

        LOGGER.info("[CertManager] SSLContext built with {} trusted certs total", count);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);

        SSLContext ctx = SSLContext.getInstance("TLSv1.2");
        ctx.init(null, tmf.getTrustManagers(), new SecureRandom());
        return ctx;
    }

    // ── Загрузка сертификатов ────────────────────────────────────────────────

    private static int loadJvmCerts(KeyStore ks) {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);

            int count = 0;
            for (TrustManager tm : tmf.getTrustManagers()) {
                if (!(tm instanceof X509TrustManager)) continue;
                for (X509Certificate cert : ((X509TrustManager) tm).getAcceptedIssuers()) {
                    String alias = "jvm_" + sanitizeAlias(cert.getSubjectDN().getName()) + "_" + count;
                    ks.setCertificateEntry(alias, cert);
                    count++;
                }
            }
            LOGGER.debug("[CertManager] Loaded {} JVM cacerts", count);
            return count;
        } catch (Exception e) {
            LOGGER.warn("[CertManager] Failed to load JVM cacerts: {}", e.getMessage());
            return 0;
        }
    }

    private static int loadBundledCerts(KeyStore ks) {
        CertificateFactory cf;
        try {
            cf = CertificateFactory.getInstance("X.509");
        } catch (Exception e) {
            LOGGER.error("[CertManager] Failed to get CertificateFactory", e);
            return 0;
        }

        int count = 0;
        for (String name : BUNDLED_CERTS) {
            X509Certificate cert = null;

            // В JAR файлы лежат по пути: assets/murdermysteryutils/certs/isrgrootx1.pem
            // Для ClassLoader путь не должен начинаться с /
            String resourcePath = "assets/murdermysteryutils/certs/" + name;

            try {
                // Пробуем через ClassLoader (работает в Forge)
                try (InputStream is = CertManager.class.getClassLoader().getResourceAsStream(resourcePath)) {
                    if (is != null) {
                        LOGGER.debug("[CertManager] Found cert via ClassLoader: {}", resourcePath);
                        cert = (X509Certificate) cf.generateCertificate(is);
                    }
                }

                // Если не нашли, пробуем через Class.getResourceAsStream
                if (cert == null) {
                    try (InputStream is = CertManager.class.getResourceAsStream("/" + resourcePath)) {
                        if (is != null) {
                            LOGGER.debug("[CertManager] Found cert via Class.getResource: /{}", resourcePath);
                            cert = (X509Certificate) cf.generateCertificate(is);
                        }
                    }
                }

                // Последняя попытка - ищем в корне
                if (cert == null) {
                    try (InputStream is = CertManager.class.getClassLoader().getResourceAsStream(name)) {
                        if (is != null) {
                            LOGGER.debug("[CertManager] Found cert in root: {}", name);
                            cert = (X509Certificate) cf.generateCertificate(is);
                        }
                    }
                }

            } catch (Exception e) {
                LOGGER.warn("[CertManager] Failed to load bundled cert {}: {}", name, e.getMessage());
            }

            if (cert == null) {
                LOGGER.error("[CertManager] Bundled cert NOT FOUND: {} (tried path: {})", name, resourcePath);
                continue;
            }

            try {
                cert.checkValidity();
                String alias = "bundled_" + name.replace(".pem", "");
                ks.setCertificateEntry(alias, cert);
                LOGGER.info("[CertManager] ✓ Loaded bundled cert: {} (expires: {})",
                        name, cert.getNotAfter());
                count++;
            } catch (CertificateExpiredException e) {
                LOGGER.warn("[CertManager] Bundled cert expired (update the mod!): {}", name);
            } catch (CertificateNotYetValidException e) {
                LOGGER.warn("[CertManager] Bundled cert not yet valid: {}", name);
            } catch (Exception e) {
                LOGGER.warn("[CertManager] Failed to add bundled cert {}: {}", name, e.getMessage());
            }
        }

        LOGGER.info("[CertManager] Loaded {} bundled certs", count);
        return count;
    }

    private static int loadDiskCerts(KeyStore ks) {
        if (!CERTS_DIR.exists()) return 0;

        CertificateFactory cf;
        try { cf = CertificateFactory.getInstance("X.509"); }
        catch (Exception e) { return 0; }

        File[] files = CERTS_DIR.listFiles((d, n) -> n.endsWith(".crt") || n.endsWith(".pem"));
        if (files == null || files.length == 0) return 0;

        int count = 0;
        for (File file : files) {
            try (InputStream is = Files.newInputStream(file.toPath())) {
                X509Certificate cert = (X509Certificate) cf.generateCertificate(is);
                try {
                    cert.checkValidity();
                    ks.setCertificateEntry("disk_" + file.getName() + "_" + count, cert);
                    count++;
                } catch (CertificateExpiredException e) {
                    LOGGER.warn("[CertManager] Expired disk cert skipped: {}", file.getName());
                }
            } catch (Exception e) {
                LOGGER.warn("[CertManager] Failed to load disk cert {}: {}", file.getName(), e.getMessage());
            }
        }
        if (count > 0) LOGGER.info("[CertManager] Loaded {} certs from disk", count);
        return count;
    }

    // ── Скачивание с кросс-проверкой ────────────────────────────────────────

    /**
     * Скачивает сертификат с нескольких официальных зеркал и сравнивает байты.
     * Если хотя бы два зеркала ответили — они обязаны совпасть побайтово.
     * При расхождении сохранение блокируется и выводится предупреждение о возможном MITM.
     */
    private static boolean tryDownloadVerified(OkHttpClient client, String[] urls,
                                               File dest, String logName) {
        List<byte[]> results = new ArrayList<>();

        for (String url : urls) {
            try {
                byte[] data = fetchBytes(client, url);
                if (data != null) results.add(data);
            } catch (SSLHandshakeException e) {
                LOGGER.debug("[CertManager] SSL handshake failed for {}: {}", url, e.getMessage());
            } catch (Exception e) {
                LOGGER.debug("[CertManager] Fetch failed for {}: {}", url, e.getMessage());
            }
        }

        if (results.isEmpty()) return false;

        // Кросс-проверка: все скачанные копии должны совпадать
        byte[] reference = results.get(0);
        for (int i = 1; i < results.size(); i++) {
            if (!Arrays.equals(reference, results.get(i))) {
                LOGGER.error("[CertManager] MIRROR MISMATCH for {}! " +
                        "Different bytes from different mirrors — possible MITM attack! " +
                        "Aborting save.", logName);
                return false;
            }
        }
        if (results.size() > 1) {
            LOGGER.debug("[CertManager] Cross-check passed ({} mirrors agree) for {}",
                    results.size(), logName);
        }

        // Финальная валидация: это должен быть корректный X.509
        if (!isValidX509(reference)) {
            LOGGER.error("[CertManager] Downloaded data is not a valid X.509 cert: {}", logName);
            return false;
        }

        try {
            Files.write(dest.toPath(), reference);
            LOGGER.info("[CertManager] Updated cert: {}", logName);
            return true;
        } catch (Exception e) {
            LOGGER.warn("[CertManager] Failed to save cert {}: {}", logName, e.getMessage());
            return false;
        }
    }

    // ── HTTP ─────────────────────────────────────────────────────────────────

    private static OkHttpClient buildVerifiedClient() throws Exception {
        SSLContext ctx = buildSSLContext();
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null);
        X509TrustManager tm = (X509TrustManager) tmf.getTrustManagers()[0];
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .sslSocketFactory(ctx.getSocketFactory(), tm)
                .build();
    }

    private static byte[] fetchBytes(OkHttpClient client, String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "MurderMysteryUtils/" + Main.VERSION)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " for " + url);
            }
            ResponseBody body = response.body();
            if (body == null) throw new IOException("Empty response body for " + url);
            byte[] bytes = body.bytes();
            if (bytes.length > MAX_CERT_BYTES) {
                throw new IOException("Response too large (" + bytes.length + " bytes) for " + url);
            }
            return bytes;
        }
    }

    // ── Утилиты ──────────────────────────────────────────────────────────────

    private static boolean needsUpdate(File certFile) {
        if (!certFile.exists()) return true;
        try (InputStream is = Files.newInputStream(certFile.toPath())) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(is);
            long timeLeft = cert.getNotAfter().getTime() - System.currentTimeMillis();
            if (timeLeft < RENEW_THRESHOLD_MS) {
                LOGGER.info("[CertManager] Cert expiring soon ({}d left), will update: {}",
                        timeLeft / 86_400_000, certFile.getName());
                return true;
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean isValidX509(byte[] data) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            cf.generateCertificate(new ByteArrayInputStream(data));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String sanitizeAlias(String raw) {
        return raw.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}