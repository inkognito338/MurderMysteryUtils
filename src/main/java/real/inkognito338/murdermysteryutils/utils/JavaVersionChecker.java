package real.inkognito338.murdermysteryutils.utils;

import real.inkognito338.murdermysteryutils.Main;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 17.10.2026
 * Проверяет, актуальна ли версия Java у пользователя (любая: 8, 11, 17, 21...).
 * Результат кэшируется — запрос делается один раз за сессию.
 */
@SuppressWarnings("GrazieInspection")
public class JavaVersionChecker {

    private static final String BASELINE_URL =
            "https://javadl-esd-secure.oracle.com/update/baseline.version";
    private static final String DOWNLOAD_URL =
            "https://www.java.com/ru/download/manual.jsp";
    private static final int TIMEOUT_MS = 5_000;

    public enum Status { OUTDATED, UP_TO_DATE, UNKNOWN }

    public static class Result {
        public final Status status;
        public final String currentVersion;
        public final String latestVersion;

        Result(Status status, String currentVersion, String latestVersion) {
            this.status = status;
            this.currentVersion = currentVersion;
            this.latestVersion = latestVersion;
        }
    }

    private static final AtomicReference<Result> cached = new AtomicReference<>(null);

    public static Result check() {
        Result r = cached.get();
        if (r != null) return r;
        r = doCheck();
        cached.compareAndSet(null, r);
        return r;
    }

    public static void invalidate() {
        cached.set(null);
    }

    private static Result doCheck() {
        String current = System.getProperty("java.version");
        if (current == null) {
            return new Result(Status.UNKNOWN, null, null);
        }

        int majorCurrent = parseMajor(current);
        if (majorCurrent < 0) {
            return new Result(Status.UNKNOWN, current, null);
        }

        String latest = fetchLatestForMajor(majorCurrent);
        if (latest == null) {
            return new Result(Status.UNKNOWN, current, null);
        }

        boolean outdated = compareVersions(current, latest) < 0;
        Status status = outdated ? Status.OUTDATED : Status.UP_TO_DATE;
        Main.LOGGER.info("[JavaVersionChecker] current={} latest={} -> {}", current, latest, status);
        return new Result(status, current, latest);
    }

    /**
     * Определяет major-версию Java из системного свойства java.version:
     *   "1.8.0_51"  → 8
     *   "11.0.23"   → 11
     *   "21.0.3"    → 21
     */
    @SuppressWarnings("GrazieInspection")
    private static int parseMajor(String version) {
        try {
            if (version.startsWith("1.")) {
                // Старый формат: 1.8.0_xx → major = 8
                String[] parts = version.split("\\.");
                return Integer.parseInt(parts[1]);
            } else {
                // Новый формат: 11.0.x, 17.0.x, 21.0.x
                String[] parts = version.split("[.+\\-]");
                return Integer.parseInt(parts[0]);
            }
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Ищет в baseline.version строку, соответствующую нужному major.
     * baseline.version содержит по одной строке на каждый major, например:
     *   1.8.0_503  → Java 8
     *   11.0.32.1  → Java 11
     *   21.0.12.1  → Java 21
     */
    private static String fetchLatestForMajor(int major) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(BASELINE_URL).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "MurderMysteryUtils/" + Main.VERSION);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != 200) {
                Main.LOGGER.warn("[JavaVersionChecker] baseline.version returned HTTP {}", conn.getResponseCode());
                return null;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    if (parseMajor(line) == major) return line;
                }
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            Main.LOGGER.warn("[JavaVersionChecker] Failed to fetch baseline.version: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Сравнивает две версии Java лексикографически по числовым сегментам.
     * Понимает оба формата: "1.8.0_503" и "21.0.12.1".
     * Возвращает < 0 если a < b, 0 если равны, > 0 если a > b.
     */
    private static int compareVersions(String a, String b) {
        int[] pa = toSegments(a);
        int[] pb = toSegments(b);
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int va = i < pa.length ? pa[i] : 0;
            int vb = i < pb.length ? pb[i] : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    /**
     * "1.8.0_503" → [1, 8, 0, 503]
     * "21.0.12.1" → [21, 0, 12, 1]
     */
    private static int[] toSegments(String version) {
        String normalized = version.replaceAll("[._+\\-]", " ").trim();
        String[] parts = normalized.split("\\s+");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                result[i] = 0;
            }
        }
        return result;
    }

    /**
     * Возвращает подсказку при SSL-ошибке если Java устарела, иначе null.
     */
    public static String getOutdatedHint() {
        Result r = check();
        if (r.status != Status.OUTDATED) return null;
        return String.format(
                "Ваша Java устарела (%s), актуальная: %s. " +
                        "Установите новую версию (%s) и выберите её в настройках лаунчера Minecraft.",
                r.currentVersion, r.latestVersion, DOWNLOAD_URL);
    }
}