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
        World world = mc.world;

        // Проверяем, включены ли ESP для золотых слитков или лука
        if (!SettingsOption.GOLD_INGOT_ESP.getValue() && !SettingsOption.BOW_ESP.getValue()) return;

        // Получаем все объекты, которые являются предметами
        List<?> items = world.loadedEntityList;

        for (Object entity : items) {
            if (entity instanceof EntityItem) {
                EntityItem itemEntity = (EntityItem) entity;

                if (itemEntity.getItem() != null) {
                    // Получаем границы предмета (Bounding Box)
                    AxisAlignedBB boundingBox = itemEntity.getEntityBoundingBox();

                    // Получаем тип предмета и рисуем рамки в зависимости от типа
                    if (itemEntity.getItem().getItem() == Items.GOLD_INGOT && SettingsOption.GOLD_INGOT_ESP.getValue()) {
                        // Золотые слитки - жёлтым
                        renderItemBoundingBox(boundingBox, 1.0F, 1.0F, 0.0F); // Жёлтый цвет
                    } else if (itemEntity.getItem().getItem() == Items.BOW && SettingsOption.BOW_ESP.getValue()) {
                        // Лук - коричневым (приближенный цвет лука в Minecraft)
                        renderItemBoundingBox(boundingBox, 0.6F, 0.3F, 0.1F); // Коричневый цвет
                    }
                }
            }
        }
    }

    private void renderItemBoundingBox(AxisAlignedBB boundingBox, float r, float g, float b) {
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glDisable(GL11.GL_DEPTH_TEST); // Отключаем тест глубины, чтобы видеть через стены
        GL11.glDepthMask(false); // Отключаем запись глубины

        double distance = mc.player.getDistance(
                (boundingBox.minX + boundingBox.maxX) / 2,
                (boundingBox.minY + boundingBox.maxY) / 2,
                (boundingBox.minZ + boundingBox.maxZ) / 2
        );

        float lineWidth = Math.max(2.0F, (float) (6.0F - (distance / 10.0F))); // Динамическая ширина
        GL11.glLineWidth(lineWidth);

        GL11.glBegin(GL11.GL_LINES);
        GL11.glColor3f(r, g, b);
        renderBoundingBoxLines(boundingBox);
        GL11.glEnd();

        GL11.glEnable(GL11.GL_DEPTH_TEST); // Включаем тест глубины обратно
        GL11.glDepthMask(true); // Включаем запись глубины

        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }



    private void renderBoundingBoxLines(AxisAlignedBB boundingBox) {
        double minX = boundingBox.minX;
        double minY = boundingBox.minY;
        double minZ = boundingBox.minZ;
        double maxX = boundingBox.maxX;
        double maxY = boundingBox.maxY;
        double maxZ = boundingBox.maxZ;

        // Рисуем линии, соединяющие все углы Bounding Box
        drawLine(minX - 0.1, minY - 0.1, minZ - 0.1, maxX + 0.1, minY - 0.1, minZ - 0.1);
        drawLine(minX - 0.1, minY - 0.1, minZ - 0.1, minX - 0.1, maxY + 0.1, minZ - 0.1);
        drawLine(minX - 0.1, minY - 0.1, minZ - 0.1, minX - 0.1, minY - 0.1, maxZ + 0.1);
        drawLine(maxX + 0.1, minY - 0.1, minZ - 0.1, maxX + 0.1, maxY + 0.1, minZ - 0.1);
        drawLine(maxX + 0.1, minY - 0.1, minZ - 0.1, maxX + 0.1, minY - 0.1, maxZ + 0.1);
        drawLine(minX - 0.1, maxY + 0.1, minZ - 0.1, maxX + 0.1, maxY + 0.1, minZ - 0.1);
        drawLine(minX - 0.1, maxY + 0.1, minZ - 0.1, minX - 0.1, maxY + 0.1, maxZ + 0.1);
        drawLine(minX - 0.1, minY - 0.1, maxZ + 0.1, maxX + 0.1, minY - 0.1, maxZ + 0.1);
        drawLine(minX - 0.1, maxY + 0.1, maxZ + 0.1, maxX + 0.1, maxY + 0.1, maxZ + 0.1);
        drawLine(maxX + 0.1, minY - 0.1, maxZ + 0.1, maxX + 0.1, maxY + 0.1, maxZ + 0.1);
        drawLine(minX - 0.1, minY - 0.1, maxZ + 0.1, minX - 0.1, maxY + 0.1, maxZ + 0.1);
    }

    private void drawLine(double x1, double y1, double z1, double x2, double y2, double z2) {
        // Рисуем линию между двумя точками
        GL11.glVertex3d(x1 - mc.getRenderManager().viewerPosX, y1 - mc.getRenderManager().viewerPosY, z1 - mc.getRenderManager().viewerPosZ);
        GL11.glVertex3d(x2 - mc.getRenderManager().viewerPosX, y2 - mc.getRenderManager().viewerPosY, z2 - mc.getRenderManager().viewerPosZ);
    }
}
