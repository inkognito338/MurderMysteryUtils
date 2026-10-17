package real.inkognito338.murdermysteryutils.commands.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import real.inkognito338.murdermysteryutils.commands.CommandSource;
import real.inkognito338.murdermysteryutils.utils.API;
import real.inkognito338.murdermysteryutils.utils.ConfigManager;
import real.inkognito338.murdermysteryutils.utils.JavaVersionChecker;
import real.inkognito338.murdermysteryutils.utils.MirrorManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 23.09.2026
 * Команда для просмотра и ручного выбора источника загрузки Lua-скриптов
 * (GitHub / Codeberg / GitFlic / GitLab / Statically / jsDelivr). По умолчанию
 * используется только GitHub; автоматического переключения нет - пользователь
 * выбирает зеркало сам, если основной источник недоступен (например, заблокирован провайдером).
 * .mirror auto подбирает лучшее из рекомендуемых зеркал (GitFlic в подбор не входит).
 */
public class CmdMirror {

    private final Minecraft mc = Minecraft.getMinecraft();

    /** Показываем Java-хинт только один раз за вызов .mirror list. */
    private final AtomicBoolean javaHintShown = new AtomicBoolean(false);

    public void run(String[] args, CommandSource source) {
        if (args.length < 2) {
            printStatus();
            return;
        }

        String arg = args[1].toLowerCase();

        if (arg.equals("list")) {
            printListWithProbe();
            return;
        }

        if (arg.equals("auto")) {
            runAuto();
            return;
        }

        MirrorManager.Mirror mirror = MirrorManager.Mirror.byId(arg);
        if (mirror == null) {
            send("§cНеизвестный источник §e" + arg + "§c. Доступные: " + listIds() + "§c, §eauto");
            return;
        }

        if (mirror == MirrorManager.getCurrent()) {
            send("§7Уже используется §e" + mirror.displayName);
            return;
        }

        MirrorManager.setCurrent(mirror);
        ConfigManager.save();
        send("§7Источник изменён на §e" + mirror.displayName + "§7, перезагружаю скрипты...");

        API.reloadFromCurrentMirror();
    }

    private void printStatus() {
        MirrorManager.Mirror current = MirrorManager.getCurrent();
        send("§7Текущий источник: §e" + current.displayName +
                (MirrorManager.isDefault() ? " §7(по умолчанию)" : ""));
        send("§7Сменить: §e.mirror <" + listIds() + "|auto> §7или §e.mirror list");
    }

    /**
     * Автоматически выбирает наилучшее (самое быстрое из доступных) рекомендуемое
     * зеркало и переключается на него. GitFlic в подборе не участвует - он помечен
     * как нерекомендуемый (Mirror.recommended = false) из-за нестабильной отдачи.
     * Проверка блокирующая, поэтому выполняется в отдельном фоновом потоке.
     */
    private void runAuto() {
        send("§7Подбираю оптимальный источник среди рекомендуемых...");

        new Thread(() -> {
            MirrorManager.AutoPickResult pick = MirrorManager.pickBestMirror();

            if (pick.best == null) {
                send("§cНи одно рекомендуемое зеркало не ответило. Попробуйте выбрать вручную: §e.mirror list");
                return;
            }

            if (pick.best == MirrorManager.getCurrent()) {
                send("§7Уже используется наилучший источник: §e" + pick.best.displayName);
                return;
            }

            MirrorManager.setCurrent(pick.best);
            ConfigManager.save();
            send("§7Выбран наилучший источник: §e" + pick.best.displayName + "§7, перезагружаю скрипты...");
            API.reloadFromCurrentMirror();
        }, "MMUtils-MirrorAuto").start();
    }

    /**
     * Выводит список зеркал: сразу запускает сетевую проверку каждого в фоне
     * (не блокируя основной поток) и печатает результат по мере готовности -
     * без промежуточных строк-заглушек. Порядок появления результатов зависит
     * от скорости ответа каждого источника, а не от порядка в списке.
     */
    private void printListWithProbe() {
        send("§6Проверка источников:");
        javaHintShown.set(false); // сбрасываем при каждом новом .mirror list

        for (MirrorManager.Mirror m : MirrorManager.Mirror.values()) {
            CompletableFuture.supplyAsync(() -> MirrorManager.probe(m))
                    .thenAccept(this::reportProbeResult);
        }
    }

    private void reportProbeResult(MirrorManager.ProbeResult result) {
        String marker = result.mirror == MirrorManager.getCurrent() ? " §7[активен]" : "";
        String note = result.mirror.recommended ? "" : " §8(не рекомендуется)";

        if (result.reachable) {
            send("  §e" + result.mirror.id + marker + note + " §a- доступен §7(" + result.latencyMs + " мс)");
        } else {
            send("  §e" + result.mirror.id + marker + note + " §c- недоступен §7(" + result.shortDetail + ")");

            // Java-хинт — только при SSL_ERROR и только один раз на весь список
            if ("SSL_ERROR".equals(result.reasonCode) && javaHintShown.compareAndSet(false, true)) {
                JavaVersionChecker.Result jv = JavaVersionChecker.check();
                if (jv.status == JavaVersionChecker.Status.OUTDATED) {
                    sendJavaHint(jv.currentVersion, jv.latestVersion);
                }
            }
        }
    }

    private void sendJavaHint(String current, String latest) {
        String downloadUrl = "https://www.java.com/ru/download/manual.jsp";

        TextComponentString prefix = new TextComponentString(
                "§7[§6MurderMysteryUtils§7]   §eJava устарела §7(" + current + " → " + latest + ")§e." +
                        " Обновление может исправить SSL ошибки. Скачать: ");

        TextComponentString link = new TextComponentString("§b" + downloadUrl);
        link.setStyle(new Style()
                .setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, downloadUrl))
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new TextComponentString("§7Нажмите чтобы открыть"))));

        TextComponentString suffix = new TextComponentString(
                " §e— затем выберите новую Java в настройках лаунчера.");

        prefix.appendSibling(link);
        prefix.appendSibling(suffix);

        if (mc.player != null) {
            mc.player.sendMessage(prefix);
        }
    }

    private String listIds() {
        StringBuilder sb = new StringBuilder();
        MirrorManager.Mirror[] values = MirrorManager.Mirror.values();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append("|");
            sb.append(values[i].id);
        }
        return sb.toString();
    }

    private void send(String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString("§7[§6MurderMysteryUtils§7] " + msg));
        }
    }
}