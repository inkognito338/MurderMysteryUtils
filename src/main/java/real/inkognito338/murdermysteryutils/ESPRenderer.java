package real.inkognito338.murdermysteryutils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

// by inkognito338 | forge 1.12.2 - 14.23.5.2860

public class ESPRenderer {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final MurderMysteryTracker tracker = new MurderMysteryTracker();

    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {
        // Обновляем роли игроков
        tracker.update();

        EntityPlayer murderer = tracker.getMurderer();
        EntityPlayer detective = tracker.getDetective();
        EntityPlayer currentPlayer = mc.player;

        // Рендерим ESP для игроков
        for (EntityPlayer player : mc.world.playerEntities) {
            if (player != currentPlayer) {
                String role = getRole(player, murderer, detective);  // Получаем роль игрока

                // Рендерим в зависимости от роли
                if ("murderer".equals(role) && SettingsOption.MURDERER_ESP.getValue()) {
                    renderESP(player, event.getPartialTicks(), 1.0F, 0.0F, 0.0F, true, true); // Убийца - красный
                } else if ("detective".equals(role) && SettingsOption.DETECTIVE_ESP.getValue()) {
                    renderESP(player, event.getPartialTicks(), 0.6F, 0.4F, 0.0F, true, true); // Детектив - оранжевый
                } else if ("default".equals(role) && SettingsOption.OTHER_ESP.getValue()) {
                    renderESP(player, event.getPartialTicks(), 1.0F, 1.0F, 1.0F, true, true); // Обычные игроки - белый
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
            return "default"; // Все остальные - обычные игроки
        }
    }

    private void renderESP(EntityPlayer entity, float partialTicks, float r, float g, float b, boolean showName, boolean renderSkin) {
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

            if (renderSkin) {
                renderBoundingBox(entity, r, g, b);
            }

            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.enableDepth();

            GlStateManager.popMatrix();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void renderBoundingBox(EntityPlayer entity, float r, float g, float b) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(GL11.GL_LINE_LOOP, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(-0.5, 0, -0.5).color(r, g, b, 0.7F).endVertex();
        buffer.pos(0.5, 0, -0.5).color(r, g, b, 0.7F).endVertex();
        buffer.pos(0.5, 0, 0.5).color(r, g, b, 0.7F).endVertex();
        buffer.pos(-0.5, 0, 0.5).color(r, g, b, 0.7F).endVertex();
        tessellator.draw();

        buffer.begin(GL11.GL_LINE_LOOP, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(-0.5, entity.height, -0.5).color(r, g, b, 0.7F).endVertex();
        buffer.pos(0.5, entity.height, -0.5).color(r, g, b, 0.7F).endVertex();
        buffer.pos(0.5, entity.height, 0.5).color(r, g, b, 0.7F).endVertex();
        buffer.pos(-0.5, entity.height, 0.5).color(r, g, b, 0.7F).endVertex();
        tessellator.draw();

        buffer.begin(GL11.GL_LINES, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
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
