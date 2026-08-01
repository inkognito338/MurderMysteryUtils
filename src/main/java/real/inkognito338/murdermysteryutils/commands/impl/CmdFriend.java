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
public class CmdFriend {

    private final Minecraft mc = Minecraft.getMinecraft();

    public void run(String[] args, CommandSource source) {
        if (args.length < 2) {
            sendUsage(source);
            return;
        }

        switch (args[1].toLowerCase()) {
            case "add":    handleAdd(args, source);    break;
            case "remove":
            case "delete": handleRemove(args, source); break;
            case "list":   handleList();               break;
            case "clear":  handleClear(source);        break;
            default:       sendUsage(source);          break;
        }
    }

    private void handleAdd(String[] args, CommandSource source) {
        if (args.length < 3) {
            send("§cИспользование: " + prefix(source) + "friend add <ник>");
            return;
        }

        String name = args[2];
        if (PlayerListManager.isFriend(name)) {
            send("§eИгрок §6" + name + " §eуже в списке друзей");
            return;
        }

        PlayerListManager.addFriend(name);
        send("§aИгрок §6" + name + " §aдобавлен в друзья");
    }

    private void handleRemove(String[] args, CommandSource source) {
        if (args.length < 3) {
            send("§cИспользование: " + prefix(source) + "friend remove <ник|*>");
            return;
        }

        String param = args[2];
        if (param.equals("*")) {
            int count = PlayerListManager.getFriendCount();
            PlayerListManager.clearFriends();
            send("§cУдалены все друзья (" + count + " игроков)");
            return;
        }

        if (!PlayerListManager.isFriend(param)) {
            send("§eИгрок §6" + param + " §eне найден в списке друзей");
            return;
        }

        PlayerListManager.removeFriend(param);
        send("§cИгрок §6" + param + " §cудалён из друзей");
    }

    private void handleList() {
        List<String> list = PlayerListManager.getFriendsList();

        if (list.isEmpty()) {
            send("§eСписок друзей пуст");
            return;
        }

        send("§6=== Список друзей [" + list.size() + "] ===");
        for (int i = 0; i < list.size(); i++) {
            send("§7" + (i + 1) + ". §e" + list.get(i));
        }
        send("§6=========================");
    }

    private void handleClear(CommandSource source) {
        handleRemove(new String[]{"friend", "remove", "*"}, source);
    }

    private void sendUsage(CommandSource source) {
        String p = prefix(source) + "friend";
        send("§6=== Команда friend ===");
        send("§e" + p + " add <ник> §7- Добавить друга");
        send("§e" + p + " remove <ник|*> §7- Удалить друга или всех");
        send("§e" + p + " list §7- Список друзей");
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