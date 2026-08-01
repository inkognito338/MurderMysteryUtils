package real.inkognito338.murdermysteryutils.modules;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import real.inkognito338.murdermysteryutils.utils.Module;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

@SideOnly(Side.CLIENT)
public class BarrierVision extends Module {

    private static BarrierVision INSTANCE;
    private final Minecraft mc = Minecraft.getMinecraft();
    private boolean registered = false;

    public BarrierVision() {
        super("BarrierVision");
        INSTANCE = this;
    }

    public static BarrierVision getInstance() {
        return INSTANCE;
    }

    @Override
    public void onEnable() {
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(this);
            registered = true;
        }
        scheduleRenderUpdate();
    }

    @Override
    public void onDisable() {
        if (registered) {
            MinecraftForge.EVENT_BUS.unregister(this);
            registered = false;
        }
        scheduleRenderUpdate();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {}

    private void scheduleRenderUpdate() {
        if (mc.player == null || mc.world == null) return;

        int chunkRadius = 8;
        int chunkX = mc.player.getPosition().getX() >> 4;
        int chunkZ = mc.player.getPosition().getZ() >> 4;

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                mc.world.markBlockRangeForRenderUpdate(
                        (chunkX + dx) << 4, 0, (chunkZ + dz) << 4,
                        ((chunkX + dx) << 4) + 15, 255, ((chunkZ + dz) << 4) + 15
                );
            }
        }
    }
}