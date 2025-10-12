package real.inkognito338.murdermysteryutils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class NameRenderer {
    private final Minecraft mc = Minecraft.getMinecraft();

    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {
        if (mc.world == null || mc.player == null) return;
        if (!SettingsOption.NAME_TAGS.getValue()) return;

        EntityPlayerSP player = mc.player;

        for (EntityPlayer entity : mc.world.playerEntities) {
            if (entity != player) {
                renderPlayerName(entity, event.getPartialTicks());
            }
        }
    }

    private void renderPlayerName(EntityPlayer entity, float partialTicks) {
        String name = entity.getName();
        if (name == null) return;

        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - mc.getRenderManager().viewerPosX;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - mc.getRenderManager().viewerPosY + entity.height + 0.5;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - mc.getRenderManager().viewerPosZ;

        FontRenderer fontRenderer = mc.fontRenderer;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-0.025F, -0.025F, 0.025F);

        // Сохраняем предыдущее состояние
        GlStateManager.disableLighting();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableCull();
        GlStateManager.disableBlend();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();

        int stringWidth = fontRenderer.getStringWidth(name);
        fontRenderer.drawStringWithShadow(name, -stringWidth / 2f, 0, 0xFFFFFF);

        // Возвращаем всё обратно
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableBlend();
        GlStateManager.enableCull();
        RenderHelper.enableStandardItemLighting();
        GlStateManager.enableLighting();

        GlStateManager.popMatrix();
    }
}
