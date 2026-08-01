package real.inkognito338.murdermysteryutils.commands;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import real.inkognito338.murdermysteryutils.Main;
import real.inkognito338.murdermysteryutils.commands.impl.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

@SuppressWarnings("SpellCheckingInspection")
public class CommandManager extends CommandBase {

    private final Minecraft mc = Minecraft.getMinecraft();

    private final Map<String, CommandEntry> commands = new LinkedHashMap<>();

    private static CommandManager instance;
    public static CommandManager getInstance() { return instance; }

    public CommandManager() {
        instance = this;
        register();
        ClientCommandHandler.instance.registerCommand(this);
    }

    // ===== FORGE COMMAND =====
    @Override
    public String getName() {
        return "mmutils";
    }

    @Override
    public List<String> getAliases() {
        List<String> aliases = new ArrayList<>();
        return aliases;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/mmutils <команда> [аргументы]";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            handleHelp(new String[]{"help"}, CommandSource.SLASH);
            return;
        }

        String commandName = args[0].toLowerCase();
        CommandEntry entry = commands.get(commandName);

        if (entry == null) {
            sender.sendMessage(new TextComponentString("§7[§6MurderMysteryUtils§7] §cНеизвестная команда. Напиши §e/mmutils help"));
            return;
        }

        entry.action.accept(args, CommandSource.SLASH);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    // ===== Публичный execute (для биндов и . команды) =====
    public void execute(String raw) {
        if (!raw.startsWith(".")) return;

        String[] split = raw.substring(1).split(" ");
        CommandEntry entry = commands.get(split[0].toLowerCase());

        if (entry != null) {
            entry.action.accept(split, CommandSource.DOT);
        }
    }

    // ===== РЕГИСТРАЦИЯ =====
    private void register() {
        register("gm", (args, source) -> new CmdGm().run(args, source), "Сменить визуальный режим игры");
        register("getskin", (args, source) -> new CmdGetSkin().run(args, source), "Получить скин игрока");
        register("friend", (args, source) -> new CmdFriend().run(args, source), "Управление списком друзей");
        register("ignore", (args, source) -> new CmdIgnore().run(args, source), "Управление списоком игнора");
        register("t", (args, source) -> new CmdToggle().run(args, source), "Переключить модуль");
        register("toggle", (args, source) -> new CmdToggle().run(args, source), "Переключить модуль");
        register("bind", (args, source) -> new CmdBind().run(args, source), "Бинд");
        register("sendlang", (args, source) -> new CmdSendLang().run(args, source), "Отправить сообщение в чат на другом языке");
        register("chat", (args, source) -> new CmdOnlineChat().run(args, source),
                "Отправить сообщение в онлайн-чат");
        register("onlinechat", (args, source) -> new CmdOnlineChat().run(args, source),
                "Отправить сообщение в онлайн-чат");

        register("help", this::handleHelp, "Показать список команд");
    }

    private void register(String name, BiConsumer<String[], CommandSource> action, String description) {
        commands.put(name.toLowerCase(), new CommandEntry(action, description));
    }

    // ===== ОБРАБОТКА ЧАТА (префикс .) =====
    @SubscribeEvent
    public void onChat(ClientChatEvent event) {
        String msg = event.getMessage();
        if (!msg.startsWith(".")) return;

        event.setCanceled(true);
        mc.ingameGUI.getChatGUI().addToSentMessages(msg);

        String[] split = msg.substring(1).split(" ");
        CommandEntry entry = commands.get(split[0].toLowerCase());

        if (entry == null) {
            sendDotMessage("§cНеизвестная команда. Напиши §e.help");
            return;
        }

        entry.action.accept(split, CommandSource.DOT);
    }

    // ===== HELP =====
    private void handleHelp(String[] args, CommandSource source) {
        int page = 1;

        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {}
        }

        int perPage = 10;
        List<Map.Entry<String, CommandEntry>> list = new ArrayList<>(commands.entrySet());
        int totalPages = (int) Math.ceil(list.size() / (double) perPage);

        if (page < 1 || page > totalPages) {
            sendMessage(source, "§cСтраница не существует");
            return;
        }

        // Показываем заголовок для slash команд (всегда)
        if (source == CommandSource.SLASH) {
            sendSlashMessage("§6" + Main.NAME + " §ev" + Main.VERSION + " §fby §3inkognito338");
        }

        sendMessage(source, "§6=== Help §7[" + page + "/" + totalPages + "] ===");

        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, list.size());

        for (int i = start; i < end; i++) {
            Map.Entry<String, CommandEntry> e = list.get(i);
            if (source == CommandSource.DOT) {
                sendMessage(source, "§e." + e.getKey() + " §7- " + e.getValue().description);
            } else {
                sendMessage(source, "  §e/" + getName() + " " + e.getKey() + " §7- " + e.getValue().description);
            }
        }

        if (page < totalPages) {
            if (source == CommandSource.DOT) {
                sendMessage(source, "§7Следующая страница: §e.help " + (page + 1));
            } else {
                sendMessage(source, "  §7Следующая страница: §e/" + getName() + " help " + (page + 1));
            }
        }
    }

    // ===== UTILS =====
    private void sendDotMessage(String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString("§7[§6MurderMysteryUtils§7] " + msg));
        }
    }

    private void sendSlashMessage(String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString(msg));
        }
    }

    private void sendMessage(CommandSource source, String msg) {
        if (source == CommandSource.DOT) {
            sendDotMessage(msg);
        } else {
            sendSlashMessage(msg);
        }
    }

    // ===== ВНУТРЕННИЙ КЛАСС =====
    private static class CommandEntry {
        final BiConsumer<String[], CommandSource> action;
        final String description;

        CommandEntry(BiConsumer<String[], CommandSource> action, String description) {
            this.action = action;
            this.description = description;
        }
    }
}