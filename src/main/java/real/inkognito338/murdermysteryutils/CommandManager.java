package real.inkognito338.murdermysteryutils;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.GameType;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class CommandManager {

    private final Minecraft mc = Minecraft.getMinecraft();

    // Слушатель чата
    @SubscribeEvent
    public void onChat(ClientChatEvent event) {
        String message = event.getMessage();
        if (!message.startsWith(".")) return; // только команды
        event.setCanceled(true); // не отправляем на сервер
        executeCommand(message);
    }

    // Обработка команды
    public void executeCommand(String input) {
        String[] args = input.split(" ");
        if (args.length == 0) return;

        switch (args[0].toLowerCase()) {
            case ".gm":
                if (args.length < 2) {
                    sendMessage("§cИспользование: .gm <0/1/2/3>");
                    return;
                }
                try {
                    int gm = Integer.parseInt(args[1]);
                    setVisualGamemode(gm);
                } catch (NumberFormatException e) {
                    sendMessage("§cНеверный номер режима!");
                }
                break;

            default:
                sendMessage("§cНеизвестная команда!");
        }
    }

    // Визуальное изменение гейммода (только клиент)
    private void setVisualGamemode(int gm) {
        if (mc.player == null || mc.playerController == null) return;

        GameType gameType;
        switch (gm) {
            case 0:
                gameType = GameType.SURVIVAL;
                break;
            case 1:
                gameType = GameType.CREATIVE;
                break;
            case 2:
                gameType = GameType.ADVENTURE;
                break;
            case 3:
                gameType = GameType.SPECTATOR;
                break;
            default:
                sendMessage("§cНеверный режим! Используйте 0, 1, 2 или 3.");
                return;
        }

        mc.playerController.setGameType(gameType);
        sendMessage("§aВизуальный режим установлен: §e" + gameType.getName());
    }

    // Отправка сообщения в чат
    private void sendMessage(String message) {
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString("§7[§5MurderMystery Utils§7]§f " + message));
        }
    }
}
