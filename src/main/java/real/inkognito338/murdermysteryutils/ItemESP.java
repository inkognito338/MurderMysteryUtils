package real.inkognito338.murdermysteryutils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.List;

// by inkognito338 | forge 1.12.2 - 14.23.5.2860

public class ItemESP {

    private final Minecraft mc = Minecraft.getMinecraft();

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (mc.world == null || mc.player == null) return;

        World world = mc.world;

        // Проверяем, включены ли ESP для золотых слитков или лука
        if (!SettingsOption.GOLD_INGOT_ESP.getValue() && !SettingsOption.BOW_ESP.getValue()) return;

        GlStateManager.pushMatrix();

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        List<?> entities = world.loadedEntityList;

        for (Object obj : entities) {
            if (!(obj instanceof EntityItem)) continue;
            EntityItem itemEntity = (EntityItem) obj;

            if (itemEntity.getItem() == null) continue;

            AxisAlignedBB bb = itemEntity.getEntityBoundingBox();

            if (itemEntity.getItem().getItem() == Items.GOLD_INGOT && SettingsOption.GOLD_INGOT_ESP.getValue()) {
                renderItemBoundingBox(bb, 1.0F, 1.0F, 0.0F); // Желтый
            } else if (itemEntity.getItem().getItem() == Items.BOW && SettingsOption.BOW_ESP.getValue()) {
                renderItemBoundingBox(bb, 0.6F, 0.3F, 0.1F); // Коричневый
            }
        }

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();

        GL11.glColor4f(1F, 1F, 1F, 1F);

        GlStateManager.popMatrix();
    }

    private void renderItemBoundingBox(AxisAlignedBB bb, float r, float g, float b) {
        GL11.glColor4f(r, g, b, 1.0F);

        double minX = bb.minX - 0.05;
        double minY = bb.minY - 0.05;
        double minZ = bb.minZ - 0.05;
        double maxX = bb.maxX + 0.05;
        double maxY = bb.maxY + 0.05;
        double maxZ = bb.maxZ + 0.05;

        double px = mc.getRenderManager().viewerPosX;
        double py = mc.getRenderManager().viewerPosY;
        double pz = mc.getRenderManager().viewerPosZ;

        GL11.glBegin(GL11.GL_LINES);

        // нижний квадрат
        drawLine(minX, minY, minZ, maxX, minY, minZ, px, py, pz);
        drawLine(maxX, minY, minZ, maxX, minY, maxZ, px, py, pz);
        drawLine(maxX, minY, maxZ, minX, minY, maxZ, px, py, pz);
        drawLine(minX, minY, maxZ, minX, minY, minZ, px, py, pz);

        // верхний квадрат
        drawLine(minX, maxY, minZ, maxX, maxY, minZ, px, py, pz);
        drawLine(maxX, maxY, minZ, maxX, maxY, maxZ, px, py, pz);
        drawLine(maxX, maxY, maxZ, minX, maxY, maxZ, px, py, pz);
        drawLine(minX, maxY, maxZ, minX, maxY, minZ, px, py, pz);

        // вертикальные линии
        drawLine(minX, minY, minZ, minX, maxY, minZ, px, py, pz);
        drawLine(maxX, minY, minZ, maxX, maxY, minZ, px, py, pz);
        drawLine(maxX, minY, maxZ, maxX, maxY, maxZ, px, py, pz);
        drawLine(minX, minY, maxZ, minX, maxY, maxZ, px, py, pz);

        GL11.glEnd();
    }

    private void drawLine(double x1, double y1, double z1, double x2, double y2, double z2,
                          double px, double py, double pz) {
        GL11.glVertex3d(x1 - px, y1 - py, z1 - pz);
        GL11.glVertex3d(x2 - px, y2 - py, z2 - pz);
    }
}
