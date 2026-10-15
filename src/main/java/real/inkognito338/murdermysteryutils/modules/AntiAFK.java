package real.inkognito338.murdermysteryutils.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import real.inkognito338.murdermysteryutils.utils.Module;

import java.util.Random;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 14.10.2026
 */

@SuppressWarnings("SpellCheckingInspection")
@SideOnly(Side.CLIENT)
public class AntiAFK extends Module {
    private static final int MIN_DELAY_TICKS = 15 * 20; // 15 секунд
    private static final int MAX_DELAY_TICKS = 30 * 20; // 30 секунд

    private final Random random = new Random();

    private Minecraft mc;
    private int ticks;
    private int nextDelay;

    public AntiAFK() {
        super("AntiAFK");
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
        mc = Minecraft.getMinecraft();
        resetTimer();
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (mc == null || mc.player == null || mc.world == null) return;

        ticks++;
        if (ticks < nextDelay) return;

        // Прыгаем только когда это возможно - иначе попробуем на следующем тике
        if (!canJump(mc.player)) return;

        mc.player.jump();
        resetTimer();
    }

    private boolean canJump(EntityPlayerSP player) {
        return player.onGround
                && !player.isInWater()
                && !player.isInLava()
                && !player.isRiding()
                && !player.isPlayerSleeping()
                && !player.capabilities.isFlying
                && !player.isOnLadder();
    }

    private void resetTimer() {
        ticks = 0;
        nextDelay = MIN_DELAY_TICKS + random.nextInt(MAX_DELAY_TICKS - MIN_DELAY_TICKS + 1);
    }
}