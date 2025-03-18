package real.inkognito338.murdermysteryutils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class Fly {
    private static final Minecraft mc = Minecraft.getMinecraft();

    @SubscribeEvent
    public void onInputUpdate(InputUpdateEvent event) {
        if (mc.player == null) return;

        EntityPlayerSP player = mc.player;

        if (SettingsOption.FLY.getValue()) {
            player.capabilities.allowFlying = true;
        } else {
            player.capabilities.allowFlying = false;
        }
    }

}
