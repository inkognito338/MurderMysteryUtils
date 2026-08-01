package real.inkognito338.murdermysteryutils.commands.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import real.inkognito338.murdermysteryutils.commands.CommandSource;
import real.inkognito338.murdermysteryutils.online.OnlineChatUtils;
import real.inkognito338.murdermysteryutils.online.OnlineMode;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 23.07.2026
 */
public class CmdOnlineChat {

    private final Minecraft mc = Minecraft.getMinecraft();

    public void run(String[] args, CommandSource source) {
        OnlineMode onlineMode = OnlineMode.getInstance();

        if (!onlineMode.isAgreementAccepted()) {
            sendError("§cСначала примите пользовательское соглашение в настройках онлайн-режима");
            return;
        }

        if (args.length < 2) {
            showUsage(source);
            return;
        }

        if (!onlineMode.isConnected()) {
            sendError("§cНет подключения к онлайн-чату");
            return;
        }

        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) messageBuilder.append(" ");
            messageBuilder.append(args[i]);
        }
        String message = messageBuilder.toString();

        if (message.trim().isEmpty()) {
            sendError("§cСообщение не может быть пустым");
            return;
        }

        OnlineChatUtils chatUtils = OnlineChatUtils.getInstance();
        boolean sent = chatUtils.sendMessage(message);

        if (!sent) {
            sendError("§cНе удалось отправить сообщение");
        }
    }

    private void showUsage(CommandSource source) {
        if (source == CommandSource.DOT) {
            sendInfo("§eИспользование: .chat <сообщение>");
            sendInfo("§7Алиас: .onlinechat <сообщение>");
        } else {
            sendInfo("§eИспользование: /mmutils chat <сообщение>");
            sendInfo("§7Алиас: /mmutils onlinechat <сообщение>");
        }

        OnlineMode onlineMode = OnlineMode.getInstance();
        if (onlineMode.isConnected()) {
            sendInfo("§aПодключены как: §f" + onlineMode.getUserNick() + " §7(" + onlineMode.getUserRankName() + ")");
        } else {
            sendInfo("§7Статус: §cне подключены");
        }
    }

    private void sendError(String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString("§7[§9ОнлайнЧат§7] " + msg));
        }
    }

    private void sendInfo(String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString("§7[§9ОнлайнЧат§7] " + msg));
        }
    }
}