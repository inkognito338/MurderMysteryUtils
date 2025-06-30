package real.inkognito338.murdermysteryutils;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

// by inkognito338 | forge 1.12.2 - 14.23.5.2860

public class ChatMessageHandler {

    private final Minecraft mc;
    private final ESPRenderer espRenderer;

    public ChatMessageHandler(Minecraft mc, ESPRenderer espRenderer) {
        this.mc = mc;
        this.espRenderer = espRenderer;
    }

    @SubscribeEvent
    public void onChatMessageReceived(ClientChatReceivedEvent event) {
        String cleanedMessage = removeFormatting(event.getMessage().getUnformattedText());

        if (ConfigManager.getResetMessages().contains(cleanedMessage)) {
            //smc.player.sendMessage(new TextComponentString("[MurderMystery Utils] Success"));
        }
    }

    private String removeFormatting(String message) {
        return message.replaceAll("§.", "");
    }
}
