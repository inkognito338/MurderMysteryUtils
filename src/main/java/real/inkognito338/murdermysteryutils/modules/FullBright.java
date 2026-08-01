package real.inkognito338.murdermysteryutils.modules;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderWorldLastEvent;
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
public class FullBright extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();

    private float originalGamma;
    private int originalAmbientOcclusion;
    private int originalClouds;
    private boolean originalViewBobbing;
    private boolean originalRenderShadow;

    public FullBright() {
        super("FullBright");
    }

    @Override
    public void onEnable() {
        if (mc.gameSettings == null) return;

        originalGamma = mc.gameSettings.gammaSetting;
        originalAmbientOcclusion = mc.gameSettings.ambientOcclusion;
        originalClouds = mc.gameSettings.clouds;
        originalViewBobbing = mc.gameSettings.viewBobbing;
        originalRenderShadow = mc.getRenderManager().isRenderShadow();

        mc.gameSettings.gammaSetting = 10.0f;
        mc.gameSettings.ambientOcclusion = 0;
        mc.gameSettings.clouds = 0;
        mc.gameSettings.viewBobbing = false;
        mc.getRenderManager().setRenderShadow(false);

        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onDisable() {
        if (mc.gameSettings == null) return;

        mc.gameSettings.gammaSetting = originalGamma;
        mc.gameSettings.ambientOcclusion = originalAmbientOcclusion;
        mc.gameSettings.clouds = originalClouds;
        mc.gameSettings.viewBobbing = originalViewBobbing;
        mc.getRenderManager().setRenderShadow(originalRenderShadow);

        MinecraftForge.EVENT_BUS.unregister(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (mc.player == null || mc.world == null) return;
        if (mc.gameSettings == null) return;

        if (mc.gameSettings.gammaSetting < 10.0f) mc.gameSettings.gammaSetting = 10.0f;
        if (mc.gameSettings.ambientOcclusion > 0) mc.gameSettings.ambientOcclusion = 0;
        if (mc.gameSettings.clouds > 0) mc.gameSettings.clouds = 0;
        if (mc.gameSettings.viewBobbing) mc.gameSettings.viewBobbing = false;
    }

    // Вызывается прямо перед финальным рендером кадра —
    // надёжнее onTick для параметров рендера, которые F1 может сбрасывать
    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (mc.getRenderManager().isRenderShadow()) {
            mc.getRenderManager().setRenderShadow(false);
        }
    }
}