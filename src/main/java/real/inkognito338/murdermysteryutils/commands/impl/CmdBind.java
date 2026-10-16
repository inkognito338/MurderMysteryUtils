package real.inkognito338.murdermysteryutils.commands.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.input.Keyboard;
import real.inkognito338.murdermysteryutils.utils.BindManager;
import real.inkognito338.murdermysteryutils.commands.CommandSource;

import java.util.List;
import java.util.Map;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

@SuppressWarnings("SpellCheckingInspection")
public class CmdBind {

    private final Minecraft mc = Minecraft.getMinecraft();

    public void run(String[] args, CommandSource source) {
        if (args.length < 2) {
            sendUsage(source);
            return;
        }

        switch (args[1].toLowerCase()) {

            // bind add <key> <действие...>
            case "add": {
                if (args.length < 4) {
                    if (source == CommandSource.DOT) {
                        send("§cИспользование: .bind add <клавиша> <.команда или сообщение>");
                    } else {
                        send("§cИспользование: /mmutils bind add <клавиша> <.команда или сообщение>");
                    }
                    return;
                }
                String keyName = args[2].toUpperCase();
                int keyCode = Keyboard.getKeyIndex(keyName);
                if (keyCode == Keyboard.KEY_NONE) {
                    send("§cНеизвестная клавиша: §e" + args[2]);
                    return;
                }

                // Собираем действие из оставшихся слов
                StringBuilder action = new StringBuilder();
                for (int i = 3; i < args.length; i++) {
                    if (i > 3) action.append(" ");
                    action.append(args[i]);
                }

                BindManager.getInstance().addBind(keyCode, action.toString());
                send("§aБинд добавлен: §e" + keyName + " §7→ §f" + action);
                break;
            }

            // bind remove <key> [действие...]
            // Если действие не указано — удаляются все бинды с клавиши
            case "remove": {
                if (args.length < 3) {
                    if (source == CommandSource.DOT) {
                        send("§cИспользование: .bind remove <клавиша> [действие]");
                    } else {
                        send("§cИспользование: /mmutils bind remove <клавиша> [действие]");
                    }
                    return;
                }
                String keyName = args[2].toUpperCase();
                int keyCode = Keyboard.getKeyIndex(keyName);
                if (keyCode == Keyboard.KEY_NONE) {
                    send("§cНеизвестная клавиша: §e" + args[2]);
                    return;
                }

                // Если действие указано — удаляем только его
                if (args.length >= 4) {
                    StringBuilder action = new StringBuilder();
                    for (int i = 3; i < args.length; i++) {
                        if (i > 3) action.append(" ");
                        action.append(args[i]);
                    }
                    if (BindManager.getInstance().removeBind(keyCode, action.toString())) {
                        send("§aБинд §e" + keyName + " §7→ §f" + action + " §aудалён");
                    } else {
                        send("§cТакого бинда на §e" + keyName + " §cне существует");
                    }
                } else {
                    // Удаляем все бинды с клавиши
                    if (BindManager.getInstance().removeAllBinds(keyCode)) {
                        send("§aВсе бинды с §e" + keyName + " §aудалены");
                    } else {
                        send("§cБиндов на §e" + keyName + " §cне существует");
                    }
                }
                break;
            }

            // bind list
            case "list": {
                Map<Integer, List<String>> binds = BindManager.getInstance().getBinds();
                if (binds.isEmpty()) {
                    send("§7Биндов нет");
                    return;
                }
                int total = 0;
                for (List<String> l : binds.values()) total += l.size();

                send("§6=== Бинды [" + total + "] ===");
                for (Map.Entry<Integer, List<String>> e : binds.entrySet()) {
                    String keyName = Keyboard.getKeyName(e.getKey());
                    for (String action : e.getValue()) {
                        send("§e" + keyName + " §7→ §f" + action);
                    }
                }
                break;
            }

            default:
                sendUsage(source);
        }
    }

    private void sendUsage(CommandSource source) {
        if (source == CommandSource.DOT) {
            send("§cИспользование:");
            send("§e.bind add <клавиша> <команда/сообщение>");
            send("§e.bind remove <клавиша> [действие]");
            send("§e.bind list");
        } else {
            send("§cИспользование:");
            send("§e/mmutils bind add <клавиша> <команда/сообщение>");
            send("§e/mmutils bind remove <клавиша> [действие]");
            send("§e/mmutils bind list");
        }
    }

    private void send(String msg) {
        if (mc.player != null)
            mc.player.sendMessage(new TextComponentString("§7[§6MurderMysteryUtils§7] " + msg));
    }
}