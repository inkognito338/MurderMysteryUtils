package real.inkognito338.murdermysteryutils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

// by inkognito338 | forge 1.12.2 - 14.23.5.2860

public class Sprint {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static boolean isSprintEnabled() {
        return SettingsOption.SPRINT.getValue(); // Теперь берем значение напрямую из настроек
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (mc.player != null && mc.player.equals(event.player)) {
            EntityPlayerSP player = mc.player;
            if (player != null) {
                if (isSprintEnabled() && !player.isSneaking() && player.getFoodStats().getFoodLevel() > 6) {
                    if (!player.isSprinting()) {
                        player.setSprinting(true);
                    }
                }
            }
        }
    }
}
