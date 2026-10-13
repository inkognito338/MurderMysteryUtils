package real.inkognito338.murdermysteryutils.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 21.09.2026
 *
 * Логгер в отдельный файл.
 * Пишет в .minecraft/MurderMysteryUtils-debug.log
 */

public final class DebugLog {

    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss.SSS");
    private static PrintWriter writer;
    private static boolean enabled = true;

    private DebugLog() {}

    static {
        try {
            File file = new File("MurderMysteryUtils-debug.log");
            writer = new PrintWriter(new FileWriter(file, false), true);
            log("DebugLog", "=== MurderMysteryUtils debug log started ===");
        } catch (IOException e) {
            enabled = false;
            System.err.println("[MurderMysteryUtils] Cannot open debug log: " + e);
        }
    }

    public static synchronized void log(String tag, String msg) {
        if (!enabled) return;
        String line = "[" + TIME.format(new Date()) + "] [" + tag + "] " + msg;
        writer.println(line);
        // Дублируем в консоль, чтобы видеть в реальном времени
        System.out.println("[MurderMysteryUtils DEBUG] " + line);
    }

    public static void log(String msg) {
        log("General", msg);
    }

    public static void close() {
        if (writer != null) {
            log("DebugLog", "=== MurderMysteryUtils debug log closed ===");
            writer.close();
        }
    }
}