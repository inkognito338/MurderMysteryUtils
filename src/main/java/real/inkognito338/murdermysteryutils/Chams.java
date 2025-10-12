package real.inkognito338.murdermysteryutils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class Chams {

    private final Minecraft mc = Minecraft.getMinecraft();

    @SubscribeEvent
    public void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (!SettingsOption.CHAMS.getValue()) return;

        EntityPlayer player = event.getEntityPlayer();
        if (player == mc.player) return; // не рендерим себя

        if (!(player instanceof AbstractClientPlayer)) return;

        GlStateManager.pushMatrix();
        GlStateManager.disableDepth(); // видеть сквозь блоки
    }

    @SubscribeEvent
    public void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (!SettingsOption.CHAMS.getValue()) return;

        EntityPlayer player = event.getEntityPlayer();
        if (player == mc.player) return;

        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }
}
