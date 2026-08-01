package real.inkognito338.murdermysteryutils.commands.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import real.inkognito338.murdermysteryutils.commands.CommandSource;
import real.inkognito338.murdermysteryutils.utils.PlayerListManager;

import java.util.List;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

@SuppressWarnings("SpellCheckingInspection")
public class CmdIgnore {

    private final Minecraft mc = Minecraft.getMinecraft();

    public void run(String[] args, CommandSource source) {
        if (args.length < 2) {
            sendUsage(source);
            return;
        }

        switch (args[1].toLowerCase()) {
            case "add":
                handleAdd(args, source);
                break;
            case "remove":
            case "delete":
                handleRemove(args, source);
                break;
            case "list":
                handleList();
                break;
            case "clear":
                handleClear(source);
                break;
            default:
                sendUsage(source);
                break;
        }
    }

    private void handleAdd(String[] args, CommandSource source) {
        if (args.length < 3) {
            send("§cИспользование: " + prefix(source) + "ignore add <ник>");
            return;
        }

        String name = args[2];
        if (PlayerListManager.isIgnored(name)) {
            send("§eИгрок §6" + name + " §eуже в списке игнора");
            return;
        }

        PlayerListManager.addIgnore(name);
        send("§aИгрок §6" + name + " §aдобавлен в игнор");
    }

    private void handleRemove(String[] args, CommandSource source) {
        if (args.length < 3) {
            send("§cИспользование: " + prefix(source) + "ignore remove <ник|*>");
            return;
        }

        String param = args[2];
        if (param.equals("*")) {
            int count = PlayerListManager.getIgnoreCount();
            PlayerListManager.clearIgnored();
            send("§cУдалены все игнорируемые игроки (" + count + " игроков)");
            return;
        }

        if (!PlayerListManager.isIgnored(param)) {
            send("§eИгрок §6" + param + " §eне найден в списке игнора");
            return;
        }

        PlayerListManager.removeIgnore(param);
        send("§cИгрок §6" + param + " §cудалён из игнора");
    }

    private void handleList() {
        List<String> list = PlayerListManager.getIgnoreList();

        if (list.isEmpty()) {
            send("§eСписок игнора пуст");
            return;
        }

        send("§6=== Список игнора [" + list.size() + "] ===");
        for (int i = 0; i < list.size(); i++) {
            send("§7" + (i + 1) + ". §e" + list.get(i));
        }
        send("§6========================");
    }

    private void handleClear(CommandSource source) {
        handleRemove(new String[]{"ignore", "remove", "*"}, source);
    }

    private void sendUsage(CommandSource source) {
        String p = prefix(source) + "ignore";
        send("§6=== Команда ignore ===");
        send("§e" + p + " add <ник> §7- Добавить в игнор");
        send("§e" + p + " remove <ник|*> §7- Убрать из игнора или всех");
        send("§e" + p + " list §7- Список игнора");
        send("§e" + p + " clear §7- Очистить список");
    }

    private String prefix(CommandSource source) {
        return source == CommandSource.DOT ? "." : "/mmutils ";
    }

    private void send(String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString("§7[§6MurderMysteryUtils§7] " + msg));
        }
    }
}