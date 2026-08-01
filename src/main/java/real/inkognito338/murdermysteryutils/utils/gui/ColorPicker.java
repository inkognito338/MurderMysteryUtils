package real.inkognito338.murdermysteryutils.utils.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;
import real.inkognito338.murdermysteryutils.utils.RenderUtils;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338 & AI
 * Version: Ultimate UX Edition (With Cancel/Apply Logic)
 */
public class ColorPicker {
    // Геометрия интерфейса
    private static final int CP_SV_SIZE = 120;
    private static final int CP_HUE_W = 14;
    private static final int CP_ALPHA_H = 12;
    private static final int PADDING = 12;
    private static final int GAP = 8;

    private int panelX, panelY, panelWidth, panelHeight;
    private int svX, svY, hueX, alphaY, bottomY, previewY;

    private boolean visible = false;
    private Setting activeSetting = null;

    private boolean draggingHue = false;
    private boolean draggingSatVal = false;
    private boolean draggingAlpha = false;

    private String hexInput = "";
    private boolean editingHex = false;

    // Локальное состояние цвета (не трогает настройку до нажатия Apply)
    private float localHue = 0f;
    private float localSat = 0f;
    private float localVal = 1f;
    private float localAlpha = 1.0f;

    private float[] originalRgb; // Сохраняем исходный цвет для отмены и сравнения

    private final Minecraft mc;

    public ColorPicker() {
        this.mc = Minecraft.getMinecraft();
    }

    public void open(Setting setting, int windowX, int windowY, int sidebarWidth, int windowWidth, int windowHeight) {
        this.visible = true;
        this.activeSetting = setting;

        // Копируем оригинальное значение для бэкапа
        float[] rgb = (float[]) activeSetting.getValue();
        this.originalRgb = rgb.clone();
        this.localAlpha = rgb.length > 3 ? rgb[3] : 1.0f;

        // Переводим RGB в HSB для локальных ползунков
        float[] hsb = Color.RGBtoHSB((int)(rgb[0]*255), (int)(rgb[1]*255), (int)(rgb[2]*255), null);
        this.localHue = hsb[0];
        this.localSat = hsb[1];
        this.localVal = hsb[2];

        // Математический расчет размеров панели
        this.panelWidth = PADDING * 2 + CP_SV_SIZE + GAP + CP_HUE_W;
        this.panelHeight = PADDING * 2 + 20 /* preview */ + GAP + CP_SV_SIZE + GAP + CP_ALPHA_H + GAP + 16 /* bottom */;

        // Центрирование
        int areaX = windowX + sidebarWidth;
        int areaWidth = windowWidth - sidebarWidth;
        this.panelX = areaX + (areaWidth - panelWidth) / 2;
        this.panelY = windowY + (windowHeight - panelHeight) / 2;

        // Сетка элементов
        this.previewY = panelY + PADDING;
        this.svX = panelX + PADDING;
        this.svY = previewY + 20 + GAP;
        this.hueX = svX + CP_SV_SIZE + GAP;
        this.alphaY = svY + CP_SV_SIZE + GAP;
        this.bottomY = alphaY + CP_ALPHA_H + GAP;

        this.editingHex = false;
        this.hexInput = "";
        this.draggingHue = draggingSatVal = draggingAlpha = false;
    }

    /**
     * Закрытие БЕЗ сохранения изменений (Отмена)
     */
    public void closeWithCancel() {
        if (activeSetting != null && originalRgb != null) {
            // Возвращаем старый цвет обратно (на всякий случай, если где-то проскочило обновление)
            activeSetting.setValue(originalRgb);
        }
        forceClose();
    }

    /**
     * Закрытие С сохранением изменений (Применить)
     */
    public void closeWithApply() {
        if (activeSetting != null) {
            int rgbInt = Color.HSBtoRGB(localHue, localSat, localVal);
            float[] valArray = (float[]) activeSetting.getValue();
            valArray[0] = ((rgbInt >> 16) & 0xFF) / 255f;
            valArray[1] = ((rgbInt >> 8) & 0xFF) / 255f;
            valArray[2] = (rgbInt & 0xFF) / 255f;
            if (valArray.length > 3) valArray[3] = localAlpha;

            activeSetting.setValue(valArray); // Фиксируем в конфиге
        }
        forceClose();
    }

    private void forceClose() {
        this.visible = false;
        this.activeSetting = null;
        this.editingHex = false;
        this.draggingHue = this.draggingSatVal = this.draggingAlpha = false;
    }

    public boolean isVisible() { return visible; }

    public void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) { // ESC -> Отмена изменений
            closeWithCancel();
            return;
        }

        if (!editingHex) return;

        if (GuiScreen.isCtrlKeyDown() && keyCode == 47) { // Ctrl+V
            try {
                String data = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
                if (data != null) {
                    data = data.trim().replace("#", "");
                    if (data.length() <= 6) {
                        hexInput = data.toUpperCase();
                        updateFieldsFromHex(hexInput); // Живое обновление при вставке
                    }
                }
            } catch (Exception ignored) {}
            return;
        }

        if (keyCode == 28 || keyCode == 156) { // ENTER -> Применить и закрыть
            closeWithApply();
        } else if (keyCode == 14) { // BACKSPACE
            if (!hexInput.isEmpty()) {
                hexInput = hexInput.substring(0, hexInput.length() - 1);
                updateFieldsFromHex(hexInput); // Живое обновление при удалении букв
            }
        } else if (hexInput.length() < 6) {
            if (String.valueOf(typedChar).matches("[0-9a-fA-F]")) {
                hexInput += Character.toUpperCase(typedChar);
                updateFieldsFromHex(hexInput); // Живое обновление при вводе на лету
            }
        }
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (!visible || button != 0) return;

        boolean wasEditing = editingHex;
        editingHex = false;

        if (isHovered(mouseX, mouseY, svX, svY, CP_SV_SIZE, CP_SV_SIZE)) {
            draggingSatVal = true;
            updateFromMouse(mouseX, mouseY);
        } else if (isHovered(mouseX, mouseY, hueX, svY, CP_HUE_W, CP_SV_SIZE)) {
            draggingHue = true;
            updateFromMouse(mouseX, mouseY);
        } else if (isHovered(mouseX, mouseY, svX, alphaY, panelWidth - PADDING * 2, CP_ALPHA_H)) {
            draggingAlpha = true;
            updateFromMouse(mouseX, mouseY);
        } else if (isHovered(mouseX, mouseY, svX, bottomY, 52, 16)) {
            editingHex = true;
            if (!wasEditing) hexInput = "";
        } else if (isHovered(mouseX, mouseY, panelX + panelWidth - PADDING - 50, bottomY, 50, 16)) {
            closeWithApply(); // Нажал Кнопку Apply
        } else if (!isHovered(mouseX, mouseY, panelX, panelY, panelWidth, panelHeight)) {
            closeWithCancel(); // Кликнул мимо панели -> Отмена
        } else {
            editingHex = wasEditing;
        }
    }

    public void mouseReleased() {
        draggingHue = draggingSatVal = draggingAlpha = false;
    }

    public void mouseClickMove(int mouseX, int mouseY) {
        if (!visible) return;
        if (draggingSatVal || draggingHue || draggingAlpha) {
            updateFromMouse(mouseX, mouseY);
        }
    }

    private void updateFromMouse(int mouseX, int mouseY) {
        if (draggingSatVal) {
            localSat = MathHelper.clamp((mouseX - svX) / (float) CP_SV_SIZE, 0f, 1f);
            localVal = MathHelper.clamp(1f - (mouseY - svY) / (float) CP_SV_SIZE, 0f, 1f);
        }
        if (draggingHue) {
            localHue = MathHelper.clamp((mouseY - svY) / (float) CP_SV_SIZE, 0f, 1f);
        }
        if (draggingAlpha) {
            float alphaWidth = panelWidth - PADDING * 2;
            localAlpha = MathHelper.clamp((mouseX - svX) / alphaWidth, 0f, 1f);
        }
    }

    private void updateFieldsFromHex(String hex) {
        if (hex.length() != 6) return; // Обновляем ползунки только если введен полный гекс код
        try {
            int rgbInt = Integer.parseInt(hex, 16);
            int r = (rgbInt >> 16) & 0xFF;
            int g = (rgbInt >> 8) & 0xFF;
            int b = rgbInt & 0xFF;
            float[] hsb = Color.RGBtoHSB(r, g, b, null);
            this.localHue = hsb[0];
            this.localSat = hsb[1];
            this.localVal = hsb[2];
        } catch (NumberFormatException ignored) {}
    }

    public void draw(int mouseX, int mouseY) {
        if (!visible || activeSetting == null) return;

        if (draggingSatVal || draggingHue || draggingAlpha) {
            updateFromMouse(mouseX, mouseY);
        }

        // Получаем текущий локальный RGB цвет для рендеринга
        int currentRGB = Color.HSBtoRGB(localHue, localSat, localVal);
        float cr = ((currentRGB >> 16) & 0xFF) / 255f;
        float cg = ((currentRGB >> 8) & 0xFF) / 255f;
        float cb = (currentRGB & 0xFF) / 255f;

        // 1. Задний план всей карточки пикера
        RenderUtils.drawRoundedRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 6, 0xFF161616);
        RenderUtils.drawRoundedRectOutline(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 6, 0xFF2C2C2C, 1.5f);

        // 2. Предпросмотр БЫЛО / СТАЛО (Разделенный прямоугольник)
        int previewWidth = panelWidth - PADDING * 2;
        int origColorInt = new Color(originalRgb[0], originalRgb[1], originalRgb[2]).getRGB();
        float origAlpha = originalRgb.length > 3 ? originalRgb[3] : 1f;

        drawCheckerboard(svX, previewY, previewWidth, 20);
        // Левая половина: Было
        GuiScreen.drawRect(svX, previewY, svX + previewWidth / 2, previewY + 20, injectAlpha(origColorInt, origAlpha));
        // Правая половина: Стало
        GuiScreen.drawRect(svX + previewWidth / 2, previewY, svX + previewWidth, previewY + 20, injectAlpha(currentRGB, localAlpha));
        RenderUtils.drawRoundedRectOutline(svX, previewY, svX + previewWidth, previewY + 20, 0, 0xFF3D3D3D, 1f);

        // 3. Насыщенность и Яркость (SV Квадрат)
        int pureHueColor = Color.HSBtoRGB(localHue, 1f, 1f);
        float hr = ((pureHueColor >> 16) & 0xFF) / 255f;
        float hg = ((pureHueColor >> 8) & 0xFF) / 255f;
        float hb = (pureHueColor & 0xFF) / 255f;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(1f, 1f, 1f, 1f); GL11.glVertex2d(svX, svY);
        GL11.glColor4f(0f, 0f, 0f, 1f); GL11.glVertex2d(svX, svY + CP_SV_SIZE);
        GL11.glColor4f(0f, 0f, 0f, 1f); GL11.glVertex2d(svX + CP_SV_SIZE, svY + CP_SV_SIZE);
        GL11.glColor4f(hr, hg, hb, 1f); GL11.glVertex2d(svX + CP_SV_SIZE, svY);
        GL11.glEnd();
        RenderUtils.drawRoundedRectOutline(svX, svY, svX + CP_SV_SIZE, svY + CP_SV_SIZE, 0, 0xFF3D3D3D, 1f);

        // 4. Тон (Hue Вертикальный бар)
        GL11.glBegin(GL11.GL_QUADS);
        Color[] hueColors = {Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED};
        for (int i = 0; i < 6; i++) {
            float y1 = svY + (i * (CP_SV_SIZE / 6f));
            float y2 = svY + ((i + 1) * (CP_SV_SIZE / 6f));
            GL11.glColor4f(hueColors[i].getRed()/255f, hueColors[i].getGreen()/255f, hueColors[i].getBlue()/255f, 1f);
            GL11.glVertex2d(hueX, y1); GL11.glVertex2d(hueX + CP_HUE_W, y1);
            GL11.glColor4f(hueColors[i+1].getRed()/255f, hueColors[i+1].getGreen()/255f, hueColors[i+1].getBlue()/255f, 1f);
            GL11.glVertex2d(hueX + CP_HUE_W, y2); GL11.glVertex2d(hueX, y2);
        }
        GL11.glEnd();
        RenderUtils.drawRoundedRectOutline(hueX, svY, hueX + CP_HUE_W, svY + CP_SV_SIZE, 0, 0xFF3D3D3D, 1f);

        // 5. Прозрачность (Alpha Горизонтальный бар)
        drawCheckerboard(svX, alphaY, previewWidth, CP_ALPHA_H);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(cr, cg, cb, 0f); GL11.glVertex2d(svX, alphaY); GL11.glVertex2d(svX, alphaY + CP_ALPHA_H);
        GL11.glColor4f(cr, cg, cb, 1f); GL11.glVertex2d(svX + previewWidth, alphaY + CP_ALPHA_H); GL11.glVertex2d(svX + previewWidth, alphaY);
        GL11.glEnd();
        RenderUtils.drawRoundedRectOutline(svX, alphaY, svX + previewWidth, alphaY + CP_ALPHA_H, 0, 0xFF3D3D3D, 1f);

        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        // 6. Отрисовка высококонтрастных Ползунков/Маркеров
        float markerX = svX + localSat * CP_SV_SIZE;
        float markerY = svY + (1f - localVal) * CP_SV_SIZE;
        RenderUtils.drawCircle(markerX, markerY, 3.5f, 0xFFFFFFFF);
        RenderUtils.drawCircleOutline(markerX, markerY, 3.5f, 0xFF000000, 1.2f);

        float markerHueY = svY + localHue * CP_SV_SIZE;
        GuiScreen.drawRect(hueX - 1, (int) markerHueY - 2, hueX + CP_HUE_W + 1, (int) markerHueY + 2, 0xFFFFFFFF);
        RenderUtils.drawRoundedRectOutline(hueX - 1, (int) markerHueY - 2, hueX + CP_HUE_W + 1, (int) markerHueY + 2, 0, 0xFF000000, 1f);

        float markerAlphaX = svX + localAlpha * previewWidth;
        GuiScreen.drawRect((int) markerAlphaX - 2, alphaY - 1, (int) markerAlphaX + 2, alphaY + CP_ALPHA_H + 1, 0xFFFFFFFF);
        RenderUtils.drawRoundedRectOutline((int) markerAlphaX - 2, alphaY - 1, (int) markerAlphaX + 2, alphaY + CP_ALPHA_H + 1, 0, 0xFF000000, 1f);

        // 7. Поле инпута HEX
        boolean hoverHex = isHovered(mouseX, mouseY, svX, bottomY, 52, 16);
        RenderUtils.drawRoundedRect(svX, bottomY, svX + 52, bottomY + 16, 2, hoverHex ? 0xFF242424 : 0xFF1C1C1C);
        RenderUtils.drawRoundedRectOutline(svX, bottomY, svX + 52, bottomY + 16, 2, editingHex ? 0xFF5294E2 : 0xFF353535, 1f);

        String displayHex = editingHex ? hexInput : String.format("%06X", (currentRGB & 0xFFFFFF));
        boolean blink = editingHex && (System.currentTimeMillis() % 800 < 400);
        mc.fontRenderer.drawString("#" + displayHex + (blink ? "_" : ""), svX + 4, bottomY + 4, 0xFFECECEC);

        // 8. Кнопка "Apply"
        int btnW = 50;
        int btnX = panelX + panelWidth - PADDING - btnW;
        boolean hoverApply = isHovered(mouseX, mouseY, btnX, bottomY, btnW, 16);

        // Красивая зеленая или серая подсветка при наведении
        RenderUtils.drawRoundedRect(btnX, bottomY, btnX + btnW, bottomY + 16, 2, hoverApply ? 0xFF2E8B57 : 0xFF282828);
        RenderUtils.drawRoundedRectOutline(btnX, bottomY, btnX + btnW, bottomY + 16, 2, hoverApply ? 0xFF3CB371 : 0xFF404040, 1f);

        int strW = mc.fontRenderer.getStringWidth("Apply");
        mc.fontRenderer.drawString("Apply", btnX + (btnW - strW) / 2, bottomY + 4, 0xFFFFFFFF);
    }

    private void drawCheckerboard(int x, int y, int width, int height) {
        int size = 4;
        for (int i = 0; i < width; i += size) {
            for (int j = 0; j < height; j += size) {
                int cx = x + i;
                int cy = y + j;
                int cw = Math.min(size, width - i);
                int ch = Math.min(size, height - j);
                int color = ((i / size + j / size) % 2 == 0) ? 0xFFFFFFFF : 0xFFBCBCBC;
                GuiScreen.drawRect(cx, cy, cx + cw, cy + ch, color);
            }
        }
    }

    private boolean isHovered(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private int injectAlpha(int color, float alpha) {
        int a = (int)(alpha * 255) & 0xFF;
        return (color & 0xFFFFFF) | (a << 24);
    }
}