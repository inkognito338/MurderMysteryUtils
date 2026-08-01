package real.inkognito338.murdermysteryutils.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import real.inkognito338.murdermysteryutils.Main;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker {

    private static final AtomicBoolean hasNotified = new AtomicBoolean(false);
    private static final AtomicBoolean isProcessing = new AtomicBoolean(false);

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://[\\w\\-.]+\\.[a-zA-Z]{2,}(?:/[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]*)?)"
    );

    private static final String DEFAULT_LANG = "ru";

    public static void checkUpdate() {
        API.checkUpdate();
    }

    public static boolean isUpdateSuccess() {
        return API.isUpdateCheckSuccess();
    }

    private static void checkStaticState() {
        if (!API.isUpdateScriptLoaded()) return;

        try {
            if (API.checkAndKick()) {
                syncStaticState();
                Main.applyRestrictions();
            }
        } catch (Exception ignored) {}
    }

    private static void checkUpdates() {
        if (!API.isUpdateScriptLoaded()) return;

        // Проверяем, не было ли уже уведомления
        if (hasNotified.get()) return;

        // Блокируем выполнение, чтобы предотвратить одновременные вызовы
        if (!isProcessing.compareAndSet(false, true)) {
            return;
        }

        try {
            String[] info = API.getUpdateInfo();
            if (info == null) return;

            boolean isOutdated = Boolean.parseBoolean(info[0]);
            String current = info[1];
            String latest = info[2];
            String downloadUrl = info[3];

            String message;
            if (isOutdated) {
                message = API.getUpdateMessage(DEFAULT_LANG, current, latest, downloadUrl);
            } else {
                message = API.getUptodateMessage(DEFAULT_LANG);
            }

            sendMessageWithRetry(message, 10);
        } catch (Exception ignored) {} finally {
            isProcessing.set(false);
        }
    }

    private static void sendMessageWithRetry(String message, int attemptsLeft) {
        if (message == null || message.isEmpty()) return;

        // Устанавливаем флаг перед отправкой
        if (!hasNotified.compareAndSet(false, true)) {
            return; // Уже отправлено
        }

        new Thread(() -> {
            try {
                Thread.sleep(500);

                Minecraft mc = Minecraft.getMinecraft();
                if (mc.player != null) {
                    mc.player.sendMessage(buildComponent(message));
                } else if (attemptsLeft > 1) {
                    // Если игрок еще не инициализирован, сбрасываем флаг и пробуем снова
                    hasNotified.set(false);
                    sendMessageWithRetry(message, attemptsLeft - 1);
                } else {
                    // Если не удалось отправить, сбрасываем флаг
                    hasNotified.set(false);
                }
            } catch (InterruptedException ignored) {
                hasNotified.set(false);
            }
        }, "UpdateChecker-SendMessage").start();
    }

    private static ITextComponent buildComponent(String rawMessage) {
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
            ITextComponent linkComponent = new TextComponentString(url);
            linkComponent.setStyle(new Style()
                    .setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                    .setUnderlined(true));
            root.appendSibling(linkComponent);

            lastEnd = matcher.end();
        }

        String tail = formatted.substring(lastEnd);
        if (!tail.isEmpty()) {
            root.appendSibling(new TextComponentString(tail));
        }

        return root;
    }

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

    public static class Handler {

        @SubscribeEvent
        public void onServerConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
            // Сбрасываем флаги при новом подключении
            hasNotified.set(false);
            isProcessing.set(false);

            new Thread(() -> {
                boolean loaded = API.awaitUpdateScriptLoaded(10);
                if (!loaded) {
                    return;
                }

                checkStaticState();
                checkUpdates();
            }, "UpdateChecker-OnConnect").start();
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