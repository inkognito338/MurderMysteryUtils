package real.inkognito338.murdermysteryutils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

// by inkognito338 | forge 1.12.2 - 14.23.5.2860

public class Fly {
    private static final Minecraft mc = Minecraft.getMinecraft();

    @SubscribeEvent
    public void onInputUpdate(InputUpdateEvent event) {
        if (mc.player == null) return;

        EntityPlayerSP player = mc.player;
        boolean modFlyEnabled = SettingsOption.FLY.getValue();

        if (player.capabilities.allowFlying) {
            // Сервер разрешил летать — не трогаем isFlying, чтобы не ломать
            return;
        }

        // Сервер не разрешил — включаем или выключаем читерский флай
        player.capabilities.isFlying = modFlyEnabled;

        // Если выключили читерский флай — не даём игроку летать
        if (!modFlyEnabled) {
            player.capabilities.isFlying = false;
        }
    }
}
