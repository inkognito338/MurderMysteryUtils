package real.inkognito338.murdermysteryutils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// by inkognito338 | forge 1.12.2 - 14.23.5.2860

public class HUD {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Minecraft mc = Minecraft.getMinecraft();
    private final MurderMysteryTracker tracker = new MurderMysteryTracker();

    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;

        tracker.update(); // Обновляем роли даже если GUI выключен

        AbstractClientPlayer murderer = (AbstractClientPlayer) tracker.getMurderer();
        AbstractClientPlayer detective = (AbstractClientPlayer) tracker.getDetective();

        if (!SettingsOption.INFO_HUD.getValue()) return; // Если GUI выключен, просто не рисуем его

        if (murderer == null && detective == null) return; // Если никого нет - не рисуем

        ScaledResolution sr = new ScaledResolution(mc);
        int screenWidth = sr.getScaledWidth();
        int xCenter = screenWidth - 180;
        int y = 20;

        drawTable(xCenter, y, murderer, detective);
    }


    private void drawTable(int x, int y, AbstractClientPlayer murderer, AbstractClientPlayer detective) {
        try {
            int width = 160;
            int height = 100;
            int padding = 5;

            Gui.drawRect(x - padding, y - padding, x + width + padding, y + height + padding, 0x90000000);

            int nameYOffset = 2;

            // Роли
            String murdererName = (murderer != null) ? tracker.getMurdererName() : "Unknown";
            String detectiveName = (detective != null) ? tracker.getDetectiveName() : "Unknown";

            // Ник убийцы
            if (murderer != null) {
                int nameWidth = mc.fontRenderer.getStringWidth(murdererName);
                int nameX = x + (width / 4 - nameWidth / 2);
                int nameY = y + nameYOffset;
                mc.fontRenderer.drawStringWithShadow(murdererName, nameX, nameY, 0xFF0000); // Красный
            }

            // Ник детектива
            if (detective != null) {
                int nameWidth = mc.fontRenderer.getStringWidth(detectiveName);
                int nameX = x + (width * 3 / 4 - nameWidth / 2);
                int nameY = y + nameYOffset;
                mc.fontRenderer.drawStringWithShadow(detectiveName, nameX, nameY, 0xFFD700); // Золотой
            }

            // Разделитель
            Gui.drawRect(x + width / 2 - 1, y, x + width / 2 + 1, y + height, 0xFFAAAAAA);

            // Отрисовка моделей
            int modelY = y + 90;
            float modelScale = 36.0F;

            if (detective != null) drawFullPlayerModel(detective, x + width * 3 / 4, modelY, modelScale);
            if (murderer != null) drawFullPlayerModel(murderer, x + width / 4, modelY, modelScale);

        } catch (Exception e) {
            LOGGER.error("HUD render error: ", e);
        }
    }


    private void drawFullPlayerModel(AbstractClientPlayer player, int x, int y, float scale) {
        if (player == null) return; // Если игрока нет — просто выходим
        if (Minecraft.getMinecraft() == null) return;
        if (Minecraft.getMinecraft().getRenderManager() == null) return;

        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();

        try {
            GlStateManager.translate(x, y, 100.0F);
            GlStateManager.scale(-scale, scale, scale);
            GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);

            // Сохраняем повороты
            float renderYawOffset = player.renderYawOffset;
            float rotationYaw = player.rotationYaw;
            float rotationPitch = player.rotationPitch;
            float prevRotationYawHead = player.rotationYawHead;

            // Сохраняем анимационные параметры
            float limbSwing = player.limbSwing;
            float limbSwingAmount = player.limbSwingAmount;
            float prevLimbSwingAmount = player.prevLimbSwingAmount;
            float cameraYaw = player.cameraYaw;

            // Обнуляем параметры поворота и анимации, чтобы модель не двигалась
            player.renderYawOffset = 0.0F;
            player.rotationYaw = 0.0F;
            player.rotationPitch = 0.0F;
            player.rotationYawHead = 0.0F;

            player.limbSwing = 0.0F;
            player.limbSwingAmount = 0.0F;
            player.prevLimbSwingAmount = 0.0F;
            player.cameraYaw = 0.0F;

            RenderManager renderManager = Minecraft.getMinecraft().getRenderManager();
            renderManager.setPlayerViewY(0.0F);
            renderManager.setRenderShadow(false);
            player.setAlwaysRenderNameTag(false);


            // Проверяем, что игрок и мир существуют перед рендером
            if (player != null && player.world != null) {
                renderManager.renderEntity(player, 0, 0, 0, 0.0F, 1.0F, false);
            }

            // Восстанавливаем повороты и анимационные параметры
            player.renderYawOffset = renderYawOffset;
            player.rotationYaw = rotationYaw;
            player.rotationPitch = rotationPitch;
            player.rotationYawHead = prevRotationYawHead;

            player.limbSwing = limbSwing;
            player.limbSwingAmount = limbSwingAmount;
            player.prevLimbSwingAmount = prevLimbSwingAmount;
            player.cameraYaw = cameraYaw;
        } catch (Exception e) {
            LOGGER.error("Error while rendering player model", e);
        }

        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.popAttrib();
        GlStateManager.popMatrix();
    }


}
