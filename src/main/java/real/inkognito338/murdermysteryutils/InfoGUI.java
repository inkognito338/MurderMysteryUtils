package real.inkognito338.murdermysteryutils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

// by inkognito338 | forge 1.12.2 - 14.23.5.2860

public class InfoGUI {
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
        int width = 160; // Ширина всей таблицы
        int height = 100; // Высота
        int padding = 5;

        // Рисуем фон таблицы
        Gui.drawRect(x - padding, y - padding, x + width + padding, y + height + padding, 0x90000000); // Тёмный фон

        // Получаем ники
        String murdererName = (murderer != null) ? murderer.getName() : "Unknown";
        String detectiveName = (detective != null) ? detective.getName() : "Unknown";

        // Смещаем y для скинов и ников
        int nameYOffset = 0; // Немного выше скина

        // Отрисовываем никнейм убийцы
        if (murderer != null) {
            int murdererNameWidth = mc.fontRenderer.getStringWidth(murdererName);
            int murdererNameX = x + (width / 4 - murdererNameWidth / 2); // Центрируем относительно скина
            int murdererNameY = y + nameYOffset;

            // Убедимся, что ник не выходит за пределы панели
            if (murdererNameX < x + padding) {
                murdererNameX = x + padding;
            } else if (murdererNameX + murdererNameWidth > x + width / 2 - padding) {
                murdererNameX = x + width / 2 - padding - murdererNameWidth;
            }

            mc.fontRenderer.drawStringWithShadow(murdererName, murdererNameX, murdererNameY, 0xFF0000); // Красный для убийцы
        }

        // Отрисовываем никнейм детектива
        if (detective != null) {
            int detectiveNameWidth = mc.fontRenderer.getStringWidth(detectiveName);
            int detectiveNameX = x + (width / 4 * 3 - detectiveNameWidth / 2); // Центрируем относительно скина
            int detectiveNameY = y + nameYOffset;

            // Убедимся, что ник не выходит за пределы панели
            if (detectiveNameX < x + width / 2 + padding) {
                detectiveNameX = x + width / 2 + padding;
            } else if (detectiveNameX + detectiveNameWidth > x + width - padding) {
                detectiveNameX = x + width - padding - detectiveNameWidth;
            }

            mc.fontRenderer.drawStringWithShadow(detectiveName, detectiveNameX, detectiveNameY, 0xFFD700); // Золотой для сыщика
        }

        // Линия между ролями
        Gui.drawRect(x + width / 2 - 1, y, x + width / 2 + 1, y + height, 0xFFAAAAAA); // Линия между ролями

        // Отрисовываем скины
// Отрисовываем скины
// Отрисовываем скины
        if (murderer != null) drawPlayerSkin(murderer, x + width / 4 - 40, y + 20); // Убийца (сдвинут на 10 пикселей влево)
        if (detective != null) drawPlayerSkin(detective, x + width / 2 + width / 4 - 40, y + 20); // Детектив (сдвинут на 10 пикселей влево)


    }






    private void drawPlayerSkin(AbstractClientPlayer player, int x, int y) {
        ResourceLocation skin = player.getLocationSkin(); // Получаем текстуру скина
        // Привязываем текстуру скина
        mc.getTextureManager().bindTexture(skin);

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();

        // Масштабируем скин (увеличим его в 2 раза)
        GlStateManager.scale(2.0f, 2.0f, 1.0f);

        // Позиции для частей скина с учетом увеличения
        x /= 2;
        y /= 2;

        // Рисуем голову
        drawSkinPart(x + 16, y, 8, 8, 8, 8, 64, 64);

        // Рисуем тело
        drawSkinPart(x + 16, y + 8, 8, 12, 20, 20, 64, 64);

        // Рисуем руки (по бокам туловища)
        drawSkinPart(x + 12, y + 8, 4, 12, 44, 20, 64, 64);  // Левая рука
        drawSkinPart(x + 24, y + 8, 4, 12, 40, 20, 64, 64); // Правая рука

        // Рисуем ноги (под туловищем)
        drawSkinPart(x + 16, y + 20, 4, 12, 4, 20, 64, 64);  // Левая нога
        drawSkinPart(x + 20, y + 20, 4, 12, 12, 20, 64, 64); // Правая нога

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void drawSkinPart(int x, int y, int width, int height, int u, int v, int texWidth, int texHeight) {
        Gui.drawScaledCustomSizeModalRect(x, y, u, v, width, height, width, height, texWidth, texHeight);
    }

}
