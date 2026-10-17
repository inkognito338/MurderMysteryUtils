package real.inkognito338.murdermysteryutils.utils;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import real.inkognito338.murdermysteryutils.Main;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker {

    private static final AtomicBoolean updateNotified = new AtomicBoolean(false);
    private static final AtomicBoolean mirrorNotified = new AtomicBoolean(false);

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://[\\w\\-.]+\\.[a-zA-Z]{2,}(?:/[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]*)?)"
    );

    private static final String DEFAULT_LANG = "ru";

    public static boolean isUpdateSuccess() {
        return API.isUpdateCheckSuccess();
    }

    // ================= Вызывается из API после завершения загрузки =================

    private static final AtomicBoolean connected = new AtomicBoolean(false);
    /**
     * Вызывается из API.init() / API.reloadFromCurrentMirror() после того, как
     * обе загрузки завершились. Ставит нужные уведомления в очередь Main —
     * тик доставит их игроку, когда mc.player станет доступен.
     */
    public static void onInitLoadFinished() {
        // В главном меню ничего не ставим в очередь: уведомления отправит onServerConnect.
        if (connected.get()) {
            if (API.isMirrorLoadFailed() && mirrorNotified.compareAndSet(false, true)) {
                Main.enqueueMessage(buildMirrorFailedNotice());
            }
            if (API.isUpdateScriptLoaded()) {
                enqueueUpdateNotice();
            }
        }

        if (API.isUpdateScriptLoaded()) {
            checkStaticState();
        }

        Main.refreshUpdateState();
    }

    /**
     * Сбрасывает флаги уведомлений при смене зеркала, чтобы после перезагрузки
     * уведомления появились снова (если новое зеркало тоже не ответит).
     */
    public static void resetNotifyFlags() {
        updateNotified.set(false);
        mirrorNotified.set(false);
    }

    // ================= Логика проверок =================

    private static void checkStaticState() {
        if (!API.isUpdateScriptLoaded()) return;
        try {
            if (API.checkAndKick()) {
                syncStaticState();
                Main.applyRestrictions();
            }
        } catch (Exception ignored) {}
    }

    private static void enqueueUpdateNotice() {
        if (!API.isUpdateScriptLoaded()) return;
        if (!updateNotified.compareAndSet(false, true)) return;

        try {
            String[] info = API.getUpdateInfo();
            if (info == null) return;

            boolean isOutdated = Boolean.parseBoolean(info[0]);
            String current = info[1];
            String latest = info[2];
            String downloadUrl = info[3];

            String message = isOutdated
                    ? API.getUpdateMessage(DEFAULT_LANG, current, latest, downloadUrl)
                    : API.getUptodateMessage(DEFAULT_LANG);

            if (message != null && !message.isEmpty()) {
                Main.enqueueMessage(buildUrlComponent(message));
            }
        } catch (Exception ignored) {}
    }

    // ================= Построение компонентов =================

    /**
     * Уведомление о недоступности зеркала с кликабельными кнопками.
     * Оформлено в несколько строк (с пустыми строками сверху/снизу) для читаемости в чате.
     */
    private static ITextComponent buildMirrorFailedNotice() {
        String currentMirrorName = MirrorManager.getCurrent().displayName;

        ITextComponent root = new TextComponentString("§7 \n");

        root.appendSibling(new TextComponentString(
                "§7[§6" + Main.NAME + "§7] §cНе удалось загрузить скрипты с §e"
                        + currentMirrorName + "§c.\n"
        ));

        root.appendSibling(new TextComponentString(
                "§7Провайдер может блокировать доступ. Выберите другой источник:\n"
        ));

        ITextComponent autoBtn = new TextComponentString("§7[§a.mirror auto§7]");
        autoBtn.setStyle(new Style()
                .setColor(TextFormatting.GREEN)
                .setClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/mmutils mirror auto"))
                .setHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new TextComponentString("§aАвтоматически подобрать\n§7наилучшее доступное зеркало"))));

        ITextComponent listBtn = new TextComponentString(" §7[§e.mirror list§7]");
        listBtn.setStyle(new Style()
                .setColor(TextFormatting.YELLOW)
                .setClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/mmutils mirror list"))
                .setHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new TextComponentString("§eПоказать все зеркала\n§7с проверкой доступности"))));

        root.appendSibling(autoBtn);
        root.appendSibling(listBtn);
        root.appendSibling(new TextComponentString("\n§7 "));

        return root;
    }

    /**
     * Строит ITextComponent, превращая http/https-ссылки в кликабельные компоненты.
     */
    private static ITextComponent buildUrlComponent(String rawMessage) {
        String formatted = rawMessage.replace('&', '§');
        Matcher matcher = URL_PATTERN.matcher(formatted);

        ITextComponent root = new TextComponentString("");
        int lastEnd = 0;

        while (matcher.find()) {
            String before = formatted.substring(lastEnd, matcher.start());
            if (!before.isEmpty()) {
                root.appendSibling(new TextComponentString(before));
            }
            String url = matcher.group(1);
            ITextComponent link = new TextComponentString(url);
            link.setStyle(new Style()
                    .setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                    .setHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            new TextComponentString("§7Открыть ссылку")))
                    .setUnderlined(true));
            root.appendSibling(link);
            lastEnd = matcher.end();
        }

        String tail = formatted.substring(lastEnd);
        if (!tail.isEmpty()) {
            root.appendSibling(new TextComponentString(tail));
        }
        return root;
    }

    // ================= Chat processing =================

    private static void processChatMessage(String message) {
        if (message == null || message.isEmpty()) return;
        if (!API.isUpdateScriptLoaded()) return;
        try {
            if (API.onChatMessage(message)) {
                syncStaticState();
            }
        } catch (Exception ignored) {}
    }

    private static void syncStaticState() {
        try {
            ConfigManager.setStaticItems(true);
        } catch (Exception ignored) {}
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new Handler());
    }

    // ================= Handler =================

    public static class Handler {

        @SubscribeEvent
        public void onServerConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
            resetNotifyFlags();
            connected.set(true);

            if (API.isMirrorLoadFailed() && mirrorNotified.compareAndSet(false, true)) {
                Main.enqueueMessage(buildMirrorFailedNotice());
            }

            if (API.isUpdateScriptLoaded()) {
                enqueueUpdateNotice();
            }
        }

        @SubscribeEvent
        public void onServerDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
            connected.set(false);
        }

        @SubscribeEvent
        public void onChatMessage(ClientChatReceivedEvent event) {
            if (event.getMessage() != null) {
                String message = event.getMessage().getUnformattedText();
                if (!message.isEmpty()) {
                    processChatMessage(message);
                }
            }
        }
    }
}