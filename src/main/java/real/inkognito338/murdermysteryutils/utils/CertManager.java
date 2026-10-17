package real.inkognito338.murdermysteryutils.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import real.inkognito338.murdermysteryutils.Main;

import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.*;
import java.util.*;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 *
 * Менеджер SSL-сертификатов и совместимости TLS для старых Java 8 (на стандартной библиотеке).
 *
 * Доверие:
 *  Уровень 1  — JVM cacerts.
 *  Уровень 2  — Бандлированные ISRG Root X1/X2 из JAR.
 *  Уровень 2b — Сертификаты с диска (только с зашитым SHA-256 отпечатком).
 *
 * Совместимость (CompatSocketFactory):
 *  Старые Java 8 шлют SSLv2Hello и/или предлагают устаревший набор — современные серверы
 *  отвечают handshake_failure. Обёртка включает только TLSv1.2/1.3 и нормальные шифры.
 *
 * Обновление сертификатов (каждый шаг принимает результат ТОЛЬКО при совпадении пина):
 *  1) HTTPS с проверкой, 2) HTTP (крайний случай), 3) HTTPS TrustAll.
 */
public class CertManager {

    private static final Logger LOGGER = LogManager.getLogger("MurderMysteryUtils");

    private static final File CERTS_DIR = new File(Main.getConfigDir(), "certs");

    private static final long RENEW_THRESHOLD_MS = 30L * 24 * 60 * 60 * 1000;
    private static final int MAX_CERT_BYTES = 64 * 1024;
    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_REDIRECTS = 5;

    private static final String[] BUNDLED_CERTS = {
            "isrgrootx1.pem",
            "isrg-root-x2.pem",
    };

    /** SHA-256 DER-отпечатки (hex, нижний регистр). Проверьте по своим бандленным .pem. */
    private static final Map<String, String> PINNED_SHA256 = new HashMap<String, String>() {{
        put("isrgrootx1.pem",   "96bcec06264976f37460779acf28c5a7cfe8a3c0aae11a8ffcee05c0bddf08c6");
        put("isrg-root-x2.pem", "69729b8e15a86efc177a57afb7171dfc64add28c2fca8cf1507e34453ccb1470");
    }};

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

    private static volatile SSLContext cachedContext = null;
    private static volatile SSLSocketFactory cachedCompatFactory = null;

    // ── TLS-совместимость для старых Java 8 ──────────────────────────────────

    /**
     * Обёртка над SSLSocketFactory: только TLSv1.2/1.3 (убирает SSLv2Hello и старые протоколы)
     * и все поддерживаемые не-мусорные шифры, ECDHE+GCM в начале списка.
     */
    private static final class CompatSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;

        CompatSocketFactory(SSLSocketFactory delegate) {
            this.delegate = delegate;
        }

        private Socket configure(Socket s) {
            if (!(s instanceof SSLSocket)) return s;
            SSLSocket ssl = (SSLSocket) s;
            try {
                List<String> protocols = new ArrayList<>();
                for (String p : ssl.getSupportedProtocols()) {
                    if (p.equals("TLSv1.2") || p.equals("TLSv1.3")) protocols.add(p);
                }
                if (!protocols.isEmpty()) {
                    ssl.setEnabledProtocols(protocols.toArray(new String[0]));
                }

                List<String> ciphers = new ArrayList<>();
                for (String c : ssl.getSupportedCipherSuites()) {
                    if (c.contains("_anon_") || c.contains("NULL") || c.contains("EXPORT")
                            || c.contains("RC4") || c.contains("DES") || c.contains("MD5")) continue;
                    ciphers.add(c);
                }
                ciphers.sort((a, b) -> Integer.compare(score(b), score(a))); // стабильная сортировка
                if (!ciphers.isEmpty()) {
                    ssl.setEnabledCipherSuites(ciphers.toArray(new String[0]));
                }
            } catch (Exception e) {
                LOGGER.warn("[CertManager] Compat configure failed: {}", e.getMessage());
            }
            return ssl;
        }

        private static int score(String c) {
            int s = 0;
            if (c.contains("ECDHE")) s += 2;
            if (c.contains("GCM")) s += 1;
            return s;
        }

        @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
        @Override public Socket createSocket() throws IOException { return configure(delegate.createSocket()); }
        @Override public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return configure(delegate.createSocket(s, host, port, autoClose));
        }
        @Override public Socket createSocket(String host, int port) throws IOException {
            return configure(delegate.createSocket(host, port));
        }
        @Override public Socket createSocket(String host, int port, InetAddress lh, int lp) throws IOException {
            return configure(delegate.createSocket(host, port, lh, lp));
        }
        @Override public Socket createSocket(InetAddress host, int port) throws IOException {
            return configure(delegate.createSocket(host, port));
        }
        @Override public Socket createSocket(InetAddress a, int port, InetAddress lh, int lp) throws IOException {
            return configure(delegate.createSocket(a, port, lh, lp));
        }
    }

    /**
     * Фабрика сокетов с нашими доверенными корнями + режимом совместимости TLS.
     * Использовать для всех HTTPS-запросов мода. Кэшируется, сбрасывается invalidateCache().
     */
    public static synchronized SSLSocketFactory getCompatSocketFactory() {
        if (cachedCompatFactory == null) {
            SSLSocketFactory base = null;
            SSLContext ctx = getSSLContext();
            if (ctx != null) base = ctx.getSocketFactory();
            if (base == null) base = (SSLSocketFactory) SSLSocketFactory.getDefault();
            cachedCompatFactory = new CompatSocketFactory(base);
        }
        return cachedCompatFactory;
    }

    /**
     * true — проблема доверия к сертификату (имеет смысл обновлять сертификаты).
     * false — например handshake_failure: сервер и клиент не договорились о параметрах TLS.
     */
    public static boolean isTrustProblem(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof CertificateException
                    || c instanceof CertPathValidatorException
                    || c instanceof CertPathBuilderException) {
                return true;
            }
        }
        return false;
    }

    // ── TrustAll (только для bootstrap, результат всегда проверяется пином) ──

    private static final HostnameVerifier ALLOW_ALL_HOSTS = (hostname, session) -> true;
    private static SSLSocketFactory bootstrapFactory = null;

    private static synchronized SSLSocketFactory getBootstrapFactory() {
        if (bootstrapFactory == null) {
            X509TrustManager tm = new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
            };
            try {
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, new TrustManager[]{tm}, new SecureRandom());
                bootstrapFactory = new CompatSocketFactory(sc.getSocketFactory());
            } catch (Exception e) {
                throw new RuntimeException("[CertManager] Failed to build TrustAll factory", e);
            }
        }
        return bootstrapFactory;
    }

    // ── Публичный API ────────────────────────────────────────────────────────

    /** Возвращает SSLContext с тремя уровнями доверия. Результат кэшируется. */
    public static synchronized SSLContext getSSLContext() {
        if (cachedContext != null) return cachedContext;
        try {
            cachedContext = buildSSLContext();
        } catch (Exception e) {
            LOGGER.error("[CertManager] Failed to build SSL context: {}", e.getMessage());
        }
        return cachedContext;
    }

    /** Сбросить кэши: следующие getSSLContext()/getCompatSocketFactory() пересоберутся. */
    public static synchronized void invalidateCache() {
        cachedContext = null;
        cachedCompatFactory = null;
    }

    /**
     * Проверить и при необходимости обновить сертификаты на диске. Вызывать в фоновом потоке.
     * Порядок: HTTPS (с проверкой) → HTTP (крайний случай) → HTTPS TrustAll.
     * На каждом шаге сертификат сохраняется ТОЛЬКО если его SHA-256 совпал с зашитым пином.
     */
    public static void refreshCerts() {
        if (!CERTS_DIR.exists() && !CERTS_DIR.mkdirs()) {
            LOGGER.warn("[CertManager] Failed to create certs dir: {}", CERTS_DIR);
        }

        SSLSocketFactory verifiedFactory = null;
        try {
            verifiedFactory = new CompatSocketFactory(buildSSLContext().getSocketFactory());
        } catch (Exception e) {
            LOGGER.warn("[CertManager] Could not build verified SSL factory: {}", e.getMessage());
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

            // 1) HTTPS с нормальной проверкой
            if (verifiedFactory != null) {
                saved = downloadPinned(verifiedFactory, null, false, urls, fileName, dest, "verified HTTPS");
            }

            // 2) HTTP — крайний случай; безопасно благодаря пину
            if (!saved) {
                LOGGER.warn("[CertManager] Verified HTTPS failed for {}, trying plain HTTP (pin-checked)", fileName);
                saved = downloadPinned(null, null, true, toHttpUrls(urls), fileName, dest, "plain HTTP");
            }

            // 3) HTTPS TrustAll — последний шанс; тоже только с пином
            if (!saved) {
                LOGGER.warn("[CertManager] HTTP failed for {}, falling back to TrustAll HTTPS (pin-checked)", fileName);
                saved = downloadPinned(getBootstrapFactory(), ALLOW_ALL_HOSTS, false, urls, fileName, dest, "TrustAll");
            }

            if (!saved) {
                LOGGER.error("[CertManager] Could not update cert: {}", fileName);
            }
        }

        invalidateCache();
        LOGGER.info("[CertManager] Cert refresh complete");
    }

    // ── Построение SSLContext ────────────────────────────────────────────────

    public static SSLContext buildSSLContext() throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);

        int count = 0;
        count += loadJvmCerts(ks);       // Уровень 1
        count += loadBundledCerts(ks);   // Уровень 2
        count += loadDiskCerts(ks);      // Уровень 2b

        LOGGER.info("[CertManager] SSLContext built with {} trusted certs total", count);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);

        SSLContext ctx = SSLContext.getInstance("TLS");
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
            String resourcePath = "assets/murdermysteryutils/certs/" + name;

            try {
                try (InputStream is = CertManager.class.getClassLoader().getResourceAsStream(resourcePath)) {
                    if (is != null) {
                        LOGGER.debug("[CertManager] Found cert via ClassLoader: {}", resourcePath);
                        cert = (X509Certificate) cf.generateCertificate(is);
                    }
                }

                if (cert == null) {
                    try (InputStream is = CertManager.class.getResourceAsStream("/" + resourcePath)) {
                        if (is != null) {
                            LOGGER.debug("[CertManager] Found cert via Class.getResource: /{}", resourcePath);
                            cert = (X509Certificate) cf.generateCertificate(is);
                        }
                    }
                }

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

    /** С диска принимаются только сертификаты с зашитым отпечатком (защита от подмены файлов). */
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

                String fp = sha256Hex(cert.getEncoded());
                if (!PINNED_SHA256.containsValue(fp)) {
                    LOGGER.warn("[CertManager] Disk cert not pinned, ignored: {}", file.getName());
                    continue;
                }

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

    // ── Скачивание с проверкой пина ──────────────────────────────────────────

    /**
     * Пробует URL по очереди; сохраняет первый сертификат, чей SHA-256 совпал с пином.
     * Не совпал (подмена/мусор) — пробуем следующий URL, на диск ничего не пишем.
     */
    private static boolean downloadPinned(SSLSocketFactory factory, HostnameVerifier verifier,
                                          boolean allowHttp, String[] urls,
                                          String fileName, File dest, String label) {
        for (String url : urls) {
            try {
                byte[] der = toDer(fetchBytes(factory, verifier, allowHttp, url));
                if (der == null) {
                    LOGGER.debug("[CertManager] Not a valid X.509 from {} ({})", url, label);
                    continue;
                }
                if (!matchesPin(fileName, der)) {
                    LOGGER.error("[CertManager] PIN MISMATCH for {} from {} ({}) — possible MITM, rejected",
                            fileName, url, label);
                    continue;
                }
                Files.write(dest.toPath(), toPem(der));
                LOGGER.info("[CertManager] Updated cert {} via {} ({})", fileName, label, url);
                return true;
            } catch (Exception e) {
                LOGGER.debug("[CertManager] {} failed for {}: {}", label, url, e.getMessage());
            }
        }
        return false;
    }

    private static String[] toHttpUrls(String[] urls) {
        String[] out = new String[urls.length];
        for (int i = 0; i < urls.length; i++) {
            out[i] = urls[i].startsWith("https://") ? "http://" + urls[i].substring(8) : urls[i];
        }
        return out;
    }

    // ── HTTP ─────────────────────────────────────────────────────────────────

    /**
     * GET. factory/verifier применяются только для HTTPS. Редиректы ведутся вручную;
     * переход на http разрешён только при allowHttp. Размер ответа ограничен MAX_CERT_BYTES.
     */
    private static byte[] fetchBytes(SSLSocketFactory factory, HostnameVerifier verifier,
                                     boolean allowHttp, String url) throws IOException {
        String current = url;
        for (int hops = 0; hops <= MAX_REDIRECTS; hops++) {
            URL u = new URL(current);
            boolean https = "https".equals(u.getProtocol());
            if (!https && !(allowHttp && "http".equals(u.getProtocol()))) {
                throw new IOException("Refusing URL (protocol not allowed): " + current);
            }

            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            try {
                if (https && conn instanceof HttpsURLConnection) {
                    HttpsURLConnection hc = (HttpsURLConnection) conn;
                    if (factory != null) hc.setSSLSocketFactory(factory);
                    if (verifier != null) hc.setHostnameVerifier(verifier);
                }
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty("User-Agent", "MurderMysteryUtils/" + Main.VERSION);

                int code = conn.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = conn.getHeaderField("Location");
                    if (location == null) throw new IOException("Redirect without Location for " + current);
                    current = new URL(u, location).toString();
                    continue;
                }
                if (code < 200 || code >= 300) {
                    throw new IOException("HTTP " + code + " for " + current);
                }

                try (InputStream in = conn.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                        if (out.size() > MAX_CERT_BYTES) {
                            throw new IOException("Response too large for " + current);
                        }
                    }
                    return out.toByteArray();
                }
            } finally {
                conn.disconnect();
            }
        }
        throw new IOException("Too many redirects for " + url);
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

    private static boolean matchesPin(String fileName, byte[] der) {
        String expected = PINNED_SHA256.get(fileName);
        if (expected == null) return false;
        return expected.equalsIgnoreCase(sha256Hex(der));
    }

    private static String sha256Hex(byte[] data) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** PEM или DER на входе, DER на выходе. null, если это не валидный X.509. */
    private static byte[] toDer(byte[] data) {
        if (data == null) return null;
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return cf.generateCertificate(new ByteArrayInputStream(data)).getEncoded();
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] toPem(byte[] der) {
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        return ("-----BEGIN CERTIFICATE-----\n" + b64 + "\n-----END CERTIFICATE-----\n")
                .getBytes(StandardCharsets.US_ASCII);
    }

    private static String sanitizeAlias(String raw) {
        return raw.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}