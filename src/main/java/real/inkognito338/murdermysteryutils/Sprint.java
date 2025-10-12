package real.inkognito338.murdermysteryutils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovementInput;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class Sprint {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static boolean isSprintEnabled() {
        return SettingsOption.SPRINT.getValue();
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (mc.player == null || !mc.player.equals(event.player)) return;
        EntityPlayerSP player = mc.player;

        MovementInput input = player.movementInput;
        boolean movingForward = input != null && input.moveForward > 0;

        // Не спринтим, если игрок что-то использует (лук, еда, зелье, и т.д.)
        boolean usingItem = player.isHandActive();

        boolean shouldSprint =
                isSprintEnabled()
                        && movingForward
                        && !player.isSneaking()
                        && !usingItem
                        && player.getFoodStats().getFoodLevel() > 6;

        if (shouldSprint && !player.isSprinting()) {
            player.setSprinting(true);
        } else if (!shouldSprint && player.isSprinting() && !movingForward) {
            player.setSprinting(false);
        }
    }
}
