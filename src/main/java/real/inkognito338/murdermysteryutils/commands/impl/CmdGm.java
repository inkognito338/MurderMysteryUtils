package real.inkognito338.murdermysteryutils.commands.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.world.GameType;
import real.inkognito338.murdermysteryutils.commands.CommandSource;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

public class CmdGm {

    private final Minecraft mc = Minecraft.getMinecraft();

    public void run(String[] args, CommandSource source) {
        if (args.length < 2) {
            if (source == CommandSource.DOT) {
                send("§cИспользование: .gm <0/1/2/3>");
            } else {
                send("§cИспользование: /mmutils gm <0/1/2/3>");
            }
            return;
        }

        int gm;
        try {
            gm = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            send("§cНеверный режим");
            return;
        }

        GameType type;
        switch (gm) {
            case 0: type = GameType.SURVIVAL; break;
            case 1: type = GameType.CREATIVE; break;
            case 2: type = GameType.ADVENTURE; break;
            case 3: type = GameType.SPECTATOR; break;
            default:
                send("§cИспользуйте 0-3");
                return;
        }

        mc.playerController.setGameType(type);
        send("§aВизуальный режим: §e" + type.getName());
    }

    private void send(String msg) {
        if (mc.player != null)
            mc.player.sendMessage(
                    new net.minecraft.util.text.TextComponentString("§7[§6MurderMysteryUtils§7] " + msg));
    }
}