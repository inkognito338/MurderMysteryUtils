package real.inkognito338.murdermysteryutils.commands.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import real.inkognito338.murdermysteryutils.commands.CommandSource;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.ModuleManager;
import real.inkognito338.murdermysteryutils.utils.ConfigManager;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

public class CmdToggle {

    private final Minecraft mc = Minecraft.getMinecraft();

    public void run(String[] args, CommandSource source) {
        // Проверка аргументов
        if (args.length < 2) {
            if (source == CommandSource.DOT) {
                send("§cИспользование: .t <модуль> [-s]");
            } else {
                send("§cИспользование: /mmutils t <модуль> [-s]");
            }
            return;
        }

        boolean silent = args[args.length - 1].equalsIgnoreCase("-s");
        String moduleName = args[1];

        Module module = ModuleManager.getModuleByName(moduleName);
        if (module == null) {
            if (!silent) send("§cМодуль §e" + moduleName + " §cне найден");
            return;
        }

        module.toggle();
        ConfigManager.save();

        if (!silent) {
            String state = module.isToggled() ? "§aвключён" : "§cвыключен";
            send("§e" + module.getName() + " §7— " + state);
        }
    }

    private void send(String msg) {
        if (mc.player != null)
            mc.player.sendMessage(new TextComponentString("§7[§6MurderMysteryUtils§7] " + msg));
    }
}