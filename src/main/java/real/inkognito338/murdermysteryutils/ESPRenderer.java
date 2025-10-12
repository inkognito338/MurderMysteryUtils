package real.inkognito338.murdermysteryutils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ESPRenderer {

    private static final Logger LOGGER = LogManager.getLogger(ESPRenderer.class);

    private final Minecraft mc = Minecraft.getMinecraft();
    private final MurderMysteryTracker tracker = new MurderMysteryTracker();

    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {
        tracker.update();

        EntityPlayer murderer = tracker.getMurderer();
        EntityPlayer detective = tracker.getDetective();
        EntityPlayer currentPlayer = mc.player;

        for (EntityPlayer player : mc.world.playerEntities) {
            if (player instanceof AbstractClientPlayer && player != currentPlayer) {
                String role = getRole(player, murderer, detective);

                if ("murderer".equals(role) && SettingsOption.MURDERER_ESP.getValue()) {
                    renderESP(player, event.getPartialTicks(), 1.0F, 0.0F, 0.0F);
                } else if ("detective".equals(role) && SettingsOption.DETECTIVE_ESP.getValue()) {
                    renderESP(player, event.getPartialTicks(), 0.6F, 0.4F, 0.0F);
                } else if ("default".equals(role) && SettingsOption.OTHER_ESP.getValue()) {
                    renderESP(player, event.getPartialTicks(), 1.0F, 1.0F, 1.0F);
                }
            }
        }
    }

    private String getRole(EntityPlayer player, EntityPlayer murderer, EntityPlayer detective) {
        if (player.equals(murderer)) {
            return "murderer";
        } else if (player.equals(detective)) {
            return "detective";
        } else {
            return "default";
        }
    }

    private void renderESP(EntityPlayer entity, float partialTicks, float r, float g, float b) {
        if (entity == null) return;

        try {
            RenderManager renderManager = mc.getRenderManager();

            double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - renderManager.viewerPosX;
            double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - renderManager.viewerPosY;
            double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - renderManager.viewerPosZ;

            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, z);

            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.disableTexture2D();
            GlStateManager.color(r, g, b, 0.7F);

            renderBoundingBox(entity, r, g, b);

            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.enableDepth();

            GlStateManager.popMatrix();
        } catch (Exception e) {
            // Используем параметризированный лог, чтобы не конкатенировать строки напрямую
            LOGGER.error("Error during ESP rendering for entity {}", entity.getName(), e);
        }
    }

    private void renderBoundingBox(EntityPlayer entity, float r, float g, float b) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        // Нижняя грань
        buffer.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(-0.5, 0, -0.5).color(r, g, b, 0.7F).endVertex();
        buffer.pos(0.5, 0, -0.5).color(r, g, b, 0.7F).endVertex();
        buffer.pos(0.5, 0, 0.5).color(r, g, b, 0.7F).endVertex();
        buffer.pos(-0.5, 0, 0.5).color(r, g, b, 0.7F).endVertex();
        tessellator.draw();

        // Верхняя грань
        buffer.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(-0.5, entity.height, -0.5).color(r, g, b, 0.7F).endVertex();
        buffer.pos(0.5, entity.height, -0.5).color(r, g, b, 0.7F).endVertex();
        buffer.pos(0.5, entity.height, 0.5).color(r, g, b, 0.7F).endVertex();
        buffer.pos(-0.5, entity.height, 0.5).color(r, g, b, 0.7F).endVertex();
        tessellator.draw();

        // Вертикальные линии
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(-0.5, 0, -0.5).color(r, g, b, 0.7F).endVertex();
        buffer.pos(-0.5, entity.height, -0.5).color(r, g, b, 0.7F).endVertex();
        buffer.pos(0.5, 0, -0.5).color(r, g, b, 0.7F).endVertex();
        buffer.pos(0.5, entity.height, -0.5).color(r, g, b, 0.7F).endVertex();
        buffer.pos(0.5, 0, 0.5).color(r, g, b, 0.7F).endVertex();
        buffer.pos(0.5, entity.height, 0.5).color(r, g, b, 0.7F).endVertex();
        buffer.pos(-0.5, 0, 0.5).color(r, g, b, 0.7F).endVertex();
        buffer.pos(-0.5, entity.height, 0.5).color(r, g, b, 0.7F).endVertex();
        tessellator.draw();
    }
}
