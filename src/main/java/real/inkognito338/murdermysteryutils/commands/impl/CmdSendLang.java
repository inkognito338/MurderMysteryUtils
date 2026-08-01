package real.inkognito338.murdermysteryutils.commands.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import real.inkognito338.murdermysteryutils.commands.CommandSource;
import real.inkognito338.murdermysteryutils.utils.CertManager;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

public class CmdSendLang {

    private static final Logger LOGGER = LogManager.getLogger("MurderMysteryUtils");

    private final Minecraft mc = Minecraft.getMinecraft();

    /**
     * HTTP-клиент для запросов к Google Translate.
     * Инициализируется с дефолтным JVM SSL (уровень 1).
     * При SSLHandshakeException пересобирается через CertManager (уровни 1+2+диск).
     */
    private volatile OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build();

    private final JsonParser jsonParser = new JsonParser();

    // ── SSL: пересборка клиента при ошибке ───────────────────────────────────

    private synchronized void rebuildClientWithFreshCerts() {
        try {
            LOGGER.info("[CmdSendLang] SSL error detected, refreshing certs and rebuilding client...");

            CertManager.refreshCerts();

            SSLContext ctx = CertManager.getSSLContext();
            if (ctx == null) {
                LOGGER.error("[CmdSendLang] CertManager returned null SSLContext after refresh");
                return;
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
            X509TrustManager tm = (X509TrustManager) tmf.getTrustManagers()[0];

            httpClient = httpClient.newBuilder()
                    .sslSocketFactory(ctx.getSocketFactory(), tm)
                    .build();

            LOGGER.info("[CmdSendLang] SSL client rebuilt successfully with fresh certs");
        } catch (Exception e) {
            LOGGER.error("[CmdSendLang] Failed to rebuild SSL client: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    public void run(String[] args, CommandSource source) {
        String usage = source == CommandSource.DOT
                ? "§cИспользование: .sendlang <код языка> <текст>"
                : "§cИспользование: /mmutils sendlang <код языка> <текст>";

        if (args.length < 3) {
            send(usage);
            send("§7Доступные языки:");
            send("§7ru(Русский), uk(Українська), be(Беларуская), pl(Polski)");
            send("§7en(English), de(Deutsch), fr(Français), es(Español)");
            send("§7kk(Қазақша), zh(中文), ja(日本語), pt(Português)");
            send("§7it(Italiano), nl(Nederlands), sv(Svenska), no(Norsk)");
            send("§7da(Dansk), fi(Suomi), cs(Čeština), hu(Magyar)");
            send("§7ro(Română)");
            return;
        }

        String langCode = args[1].toLowerCase();
        String internalLangCode = normalizeLangCode(langCode);

        if (!isValidLang(internalLangCode)) {
            send("§cНеизвестный язык: §e" + langCode);
            send("§7Доступные: ru, uk, be, pl, en, de, fr, es, kk, zh, ja, pt, it, nl, sv, no, da, fi, cs, hu, ro");
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2) sb.append(' ');
            sb.append(args[i]);
        }
        String text = sb.toString();

        send("§fВыполняется перевод...");

        new Thread(() -> {
            try {
                String translated = translate(text, internalLangCode);
                if (translated.isEmpty()) {
                    send("§cОшибка перевода");
                    return;
                }

                mc.addScheduledTask(() -> {
                    if (mc.player == null) return;
                    mc.player.sendChatMessage(translated);
                });

            } catch (Exception e) {
                send("§cОшибка: " + e.getMessage());
            }
        }).start();
    }

    private String normalizeLangCode(String lang) {
        // Поддержка альтернативных кодов
        switch (lang) {
            case "ua": return "uk";      // Украинский
            case "kz": return "kk";      // Казахский
            case "zh": return "cn";      // Китайский
            case "pt": return "pt";      // Португальский
            case "it": return "it";      // Итальянский
            case "nl": return "nl";      // Голландский
            case "sv": return "sv";      // Шведский
            case "no": return "no";      // Норвежский
            case "da": return "da";      // Датский
            case "fi": return "fi";      // Финский
            case "cs": return "cs";      // Чешский
            case "hu": return "hu";      // Венгерский
            case "ro": return "ro";      // Румынский
            default: return lang;
        }
    }

    private boolean isValidLang(String lang) {
        switch (lang) {
            // Существующие языки
            case "ru": case "uk": case "be": case "pl":
            case "en": case "de": case "fr": case "es":
            case "kk": case "cn": case "ja":
                // Новые языки
            case "pt": case "it": case "nl": case "sv":
            case "no": case "da": case "fi": case "cs":
            case "hu": case "ro":
                return true;
            default:
                return false;
        }
    }

    private String getLanguageName(String langCode) {
        switch (langCode) {
            case "ru":   return "Русский";
            case "uk":   return "Українська";
            case "be":   return "Беларуская";
            case "pl":   return "Polski";
            case "en":   return "English";
            case "de":   return "Deutsch";
            case "fr":   return "Français";
            case "es":   return "Español";
            case "kk":   return "Қазақша";
            case "cn":   return "中文";
            case "ja":   return "日本語";
            case "pt":   return "Português";
            case "it":   return "Italiano";
            case "nl":   return "Nederlands";
            case "sv":   return "Svenska";
            case "no":   return "Norsk";
            case "da":   return "Dansk";
            case "fi":   return "Suomi";
            case "cs":   return "Čeština";
            case "hu":   return "Magyar";
            case "ro":   return "Română";
            default:     return langCode;
        }
    }

    private String translate(String text, String targetLang) throws Exception {
        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8.toString());

        // Конвертация кодов для Google Translate API
        String tl = convertToGoogleLangCode(targetLang);

        String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl="
                + "auto" + "&tl=" + tl + "&dt=t&ie=UTF-8&oe=UTF-8&q=" + encoded;

        Request request = new Request.Builder().url(url).get().build();

        try {
            return executeTranslate(httpClient, request);
        } catch (SSLHandshakeException e) {
            LOGGER.warn("[CmdSendLang] SSL handshake failed, refreshing certs and retrying: {}", url);
            rebuildClientWithFreshCerts();
            return executeTranslate(httpClient, request);
        }
    }

    // Конвертация кодов языков для Google Translate API
    private String convertToGoogleLangCode(String langCode) {
        switch (langCode) {
            case "cn":   return "zh-CN";
            case "no":   return "no";
            case "sv":   return "sv";
            case "da":   return "da";
            case "fi":   return "fi";
            case "cs":   return "cs";
            case "hu":   return "hu";
            case "ro":   return "ro";
            default:     return langCode;
        }
    }

    private String executeTranslate(OkHttpClient client, Request request) throws IOException {
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return "";
            ResponseBody body = response.body();
            if (body == null) return "";

            String jsonString = body.string();
            JsonArray json  = jsonParser.parse(jsonString).getAsJsonArray();
            JsonArray parts = json.get(0).getAsJsonArray();

            StringBuilder result = new StringBuilder();
            for (int i = 0; i < parts.size(); i++) {
                try {
                    result.append(parts.get(i).getAsJsonArray().get(0).getAsString());
                } catch (Exception ignored) {}
            }
            return result.toString().trim();
        }
    }

    private void send(String msg) {
        if (mc.player != null)
            mc.player.sendMessage(
                    new net.minecraft.util.text.TextComponentString("§7[§6MurderMysteryUtils§7] " + msg));
    }
}