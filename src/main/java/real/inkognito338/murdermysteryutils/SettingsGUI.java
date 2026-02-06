package real.inkognito338.murdermysteryutils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import real.inkognito338.murdermysteryutils.modules.Module;
import real.inkognito338.murdermysteryutils.modules.ModuleManager;
import real.inkognito338.murdermysteryutils.modules.settings.Setting;
import real.inkognito338.murdermysteryutils.modules.settings.SettingType;

import java.awt.Color;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("SpellCheckingInspection")
public class SettingsGUI extends GuiScreen {
    // Координаты и размеры окна (уменьшенные)
    private static int windowX = 0;
    private static int windowY = 0;
    private static final int WINDOW_WIDTH = 480;
    private static final int WINDOW_HEIGHT = 300;
    private static final int SIDEBAR_WIDTH = 135;

    // Скроллинг
    private float settingsScrollY = 0;
    private float targetSettingsScrollY = 0;
    private float maxSettingsScrollY = 0;

    private float moduleScrollY = 0;
    private float targetModuleScrollY = 0;
    private float maxModuleScrollY = 0;

    // Логика перетаскивания
    private boolean dragging = false;
    private int dragX, dragY;
    private static Module selectedModule = null; // Static для сохранения между открытиями

    // Слайдеры
    private boolean draggingSlider = false;
    private Setting activeSlider = null;

    // Color Picker
    private boolean showColorPicker = false;
    private Setting activeColorSetting = null;
    private int colorPickerX, colorPickerY;
    private boolean draggingHue = false;
    private boolean draggingSatVal = false;
    private boolean draggingAlpha = false;

    private String hexInput = "";
    private boolean editingHex = false;
    private float pickerAlpha = 1.0f;

    // Анимации
    private Map<String, SwitchAnimation> switchAnimations = new HashMap<>();

    private static class SwitchAnimation {
        public float position;
        public long lastUpdate;

        public SwitchAnimation(float startPos) {
            this.position = startPos;
            this.lastUpdate = System.currentTimeMillis();
        }
    }

    @Override
    public void initGui() {
        if (windowX == 0 && windowY == 0) {
            ScaledResolution sr = new ScaledResolution(mc);
            windowX = (sr.getScaledWidth() - WINDOW_WIDTH) / 2;
            windowY = (sr.getScaledHeight() - WINDOW_HEIGHT) / 2;
        }
        // Сохраняем выбранный модуль между открытиями
        if (selectedModule == null && !ModuleManager.getModules().isEmpty()) {
            selectedModule = ModuleManager.getModules().get(0);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        moduleScrollY = interpolate(moduleScrollY, targetModuleScrollY, 0.15f);
        settingsScrollY = interpolate(settingsScrollY, targetSettingsScrollY, 0.15f);

        // Затемнение фона
        drawRect(0, 0, width, height, 0x70000000);

        // Основное окно
        RenderUtils.drawRoundedRect(windowX, windowY, windowX + WINDOW_WIDTH, windowY + WINDOW_HEIGHT, 10, 0xFF1A1A1A);

        // Сайдбар
        RenderUtils.drawRoundedRect(windowX, windowY, windowX + SIDEBAR_WIDTH, windowY + WINDOW_HEIGHT, 10, 0xFF212121);

        // Разделительная линия
        drawRect(windowX + SIDEBAR_WIDTH, windowY + 38, windowX + SIDEBAR_WIDTH + 1, windowY + WINDOW_HEIGHT - 8, 0xFF2A2A2A);

        mc.fontRenderer.drawStringWithShadow(Main.NAME + "§b v" + Main.VERSION, windowX + 12, windowY + 10, 0xFFFFFFFF);
        mc.fontRenderer.drawString("by inkognito338", windowX + 12, windowY + 22, 0xFF707070);

        // --- СПИСОК МОДУЛЕЙ ---
        RenderUtils.startScissor(windowX, windowY + 40, SIDEBAR_WIDTH, WINDOW_HEIGHT - 48);

        float currentModuleY = windowY + 44 + moduleScrollY;
        float moduleStartPos = currentModuleY;

        for (Module module : ModuleManager.getModules()) {
            boolean isSelected = (selectedModule == module);
            int btnX = windowX + 8;
            int btnWidth = SIDEBAR_WIDTH - 16;
            int btnHeight = 22;

            boolean isHovered = isHovered(mouseX, mouseY, btnX, (int)currentModuleY, btnWidth, btnHeight);

            if (isSelected) {
                RenderUtils.drawRoundedRect(btnX, currentModuleY, btnX + btnWidth, currentModuleY + btnHeight, 5, 0xFF2A2A2A);
                RenderUtils.drawRoundedRectOutline(btnX, currentModuleY, btnX + btnWidth, currentModuleY + btnHeight, 5, 0xFF569CD6, 1.5f);

                // Индикатор слева
                RenderUtils.drawRoundedRect(btnX + 2, currentModuleY + 6, btnX + 4, currentModuleY + btnHeight - 6, 1, 0xFF569CD6);
            } else if (isHovered) {
                RenderUtils.drawRoundedRect(btnX, currentModuleY, btnX + btnWidth, currentModuleY + btnHeight, 5, 0xFF282828);
            }

            int textColor = isSelected ? 0xFFFFFFFF : (module.isToggled() ? 0xFF90EE90 : 0xFFAAAAAA);
            mc.fontRenderer.drawString(module.getName(), btnX + 8, (int)currentModuleY + 7, textColor);

            if (module.isToggled()) {
                RenderUtils.drawCircle(btnX + btnWidth - 6, currentModuleY + btnHeight / 2f, 2.5f, 0xFF4CAF50);
            }

            currentModuleY += 26;
        }

        float totalModulesHeight = (currentModuleY - moduleStartPos);
        maxModuleScrollY = Math.min(0, (WINDOW_HEIGHT - 52) - totalModulesHeight);

        RenderUtils.stopScissor();

        // --- ПРАВАЯ ЧАСТЬ (НАСТРОЙКИ) ---
        if (selectedModule != null) {
            int settingsX = windowX + SIDEBAR_WIDTH + 15;
            int settingsWidth = WINDOW_WIDTH - SIDEBAR_WIDTH - 30;

            mc.fontRenderer.drawStringWithShadow(selectedModule.getName(), settingsX, windowY + 12, 0xFFFFFFFF);
            drawRect(settingsX, windowY + 28, settingsX + settingsWidth, windowY + 29, 0xFF2A2A2A);

            RenderUtils.startScissor(windowX + SIDEBAR_WIDTH, windowY + 32, WINDOW_WIDTH - SIDEBAR_WIDTH, WINDOW_HEIGHT - 38);

            float currentY = windowY + 38 + settingsScrollY;
            float settingsStartPos = currentY;

            // 1. Включение модуля
            drawSettingBackground(settingsX, (int)currentY, settingsWidth, 24, mouseX, mouseY);
            mc.fontRenderer.drawString("Enable Module", settingsX + 8, (int)currentY + 8, 0xFFE0E0E0);
            drawSwitch(settingsX + settingsWidth - 34, (int)currentY + 4, selectedModule.isToggled(), "mod_" + selectedModule.getName());
            currentY += 28;

            // 2. Отрисовка настроек
            for (Setting setting : selectedModule.getSettings()) {
                int height = (setting.getType() == SettingType.NUMBER ? 38 : 28);

                if (currentY + height > windowY + 32 && currentY < windowY + WINDOW_HEIGHT) {
                    switch (setting.getType()) {
                        case BOOLEAN:
                            drawSettingBackground(settingsX, (int)currentY, settingsWidth, 24, mouseX, mouseY);
                            mc.fontRenderer.drawString(setting.getName(), settingsX + 8, (int)currentY + 8, 0xFFE0E0E0);
                            drawSwitch(settingsX + settingsWidth - 34, (int)currentY + 4, (boolean) setting.getValue(), setting.getName());
                            break;

                        case NUMBER:
                            drawSettingBackground(settingsX, (int)currentY, settingsWidth, 32, mouseX, mouseY);
                            String valStr = String.format("%.2f", ((Number)setting.getValue()).doubleValue());
                            mc.fontRenderer.drawString(setting.getName(), settingsX + 8, (int)currentY + 5, 0xFFE0E0E0);
                            mc.fontRenderer.drawString(valStr, settingsX + settingsWidth - mc.fontRenderer.getStringWidth(valStr) - 5, (int)currentY + 5, 0xFF808080);
                            drawSlider(setting, settingsX + 8, (int)currentY + 19, settingsWidth - 16, mouseX, mouseY);
                            break;

                        case MODE:
                            drawSettingBackground(settingsX, (int)currentY, settingsWidth, 24, mouseX, mouseY);
                            mc.fontRenderer.drawString(setting.getName(), settingsX + 8, (int)currentY + 8, 0xFFE0E0E0);
                            String modeText = setting.getMode();
                            mc.fontRenderer.drawString(modeText, settingsX + settingsWidth - mc.fontRenderer.getStringWidth(modeText) - 8, (int)currentY + 8, 0xFF569CD6);
                            break;

                        case COLOR:
                            drawSettingBackground(settingsX, (int)currentY, settingsWidth, 24, mouseX, mouseY);
                            mc.fontRenderer.drawString(setting.getName(), settingsX + 8, (int)currentY + 8, 0xFFE0E0E0);
                            float[] rgb = (float[]) setting.getValue();
                            int c = new Color(rgb[0], rgb[1], rgb[2]).getRGB();
                            int colorBoxX = settingsX + settingsWidth - 28;
                            int colorBoxY = (int)currentY + 4;

                            drawCheckerboard(colorBoxX, colorBoxY, 20, 16);
                            RenderUtils.drawRoundedRect(colorBoxX, colorBoxY, colorBoxX + 20, colorBoxY + 16, 3, c);
                            RenderUtils.drawRoundedRectOutline(colorBoxX, colorBoxY, colorBoxX + 20, colorBoxY + 16, 3, 0xFFFFFFFF, 1f);
                            break;
                    }
                }
                currentY += height;
            }

            float totalSettingsHeight = (currentY - settingsStartPos);
            maxSettingsScrollY = Math.min(0, (WINDOW_HEIGHT - 50) - totalSettingsHeight);

            RenderUtils.stopScissor();

            if (draggingSlider && activeSlider != null) {
                updateSliderValue(activeSlider, mouseX, settingsX + 8, settingsWidth - 16);
            }
        }

        if (showColorPicker && activeColorSetting != null) {
            drawImprovedColorPicker(mouseX, mouseY);
        }

        handleScroll(mouseX, mouseY);
    }

    private void updateSliderValue(Setting setting, int mouseX, int x, int width) {
        double min = ((Number) setting.getMin()).doubleValue();
        double max = ((Number) setting.getMax()).doubleValue();

        double diff = Math.min(width, Math.max(0, mouseX - x));
        double percent = diff / width;

        double newValue = min + (percent * (max - min));
        newValue = Math.round(newValue * 100.0) / 100.0;

        setting.setValue(newValue);
    }

    // --- ОТРИСОВКА КОМПОНЕНТОВ ---

    private void drawSettingBackground(int x, int y, int w, int h, int mx, int my) {
        boolean hover = isHovered(mx, my, x, y, w, h);
        RenderUtils.drawRoundedRect(x, y, x + w, y + h, 5, hover ? 0xFF282828 : 0xFF232323);
    }

    private void drawSwitch(int x, int y, boolean state, String key) {
        int w = 30;
        int h = 16;

        SwitchAnimation anim = switchAnimations.computeIfAbsent(key, k -> new SwitchAnimation(state ? 1.0f : 0.0f));
        float target = state ? 1.0f : 0.0f;

        long now = System.currentTimeMillis();
        float delta = (now - anim.lastUpdate) / 1000f;
        anim.lastUpdate = now;

        if (anim.position < target) anim.position = Math.min(target, anim.position + delta * 8f);
        else if (anim.position > target) anim.position = Math.max(target, anim.position - delta * 8f);

        float t = anim.position;

        int colorBg = interpolateColor(0xFF3A3A3A, 0xFF3584E4, t);
        int colorCircle = 0xFFFFFFFF;

        RenderUtils.drawRoundedRect(x, y, x + w, y + h, h/2f, colorBg);

        float circleX = x + 2 + (w - h) * t;
        RenderUtils.drawCircle(circleX + (h/2f) - 2, y + h/2f, (h/2f) - 2, colorCircle);
    }

    private void drawSlider(Setting setting, int x, int y, int width, int mouseX, int mouseY) {
        double val = ((Number) setting.getValue()).doubleValue();
        double min = ((Number) setting.getMin()).doubleValue();
        double max = ((Number) setting.getMax()).doubleValue();

        RenderUtils.drawRoundedRect(x, y, x + width, y + 4, 2, 0xFF3A3A3A);

        double percent = (val - min) / (max - min);
        int filled = (int) (width * percent);

        if (filled > 0) {
            RenderUtils.drawRoundedRect(x, y, x + filled, y + 4, 2, 0xFF569CD6);
        }

        RenderUtils.drawCircle(x + filled, y + 2, 5, 0xFFFFFFFF);
        RenderUtils.drawCircleOutline(x + filled, y + 2, 5, 0x60000000, 1.5f);

        if (Mouse.isButtonDown(0) && isHovered(mouseX, mouseY, x - 5, y - 5, width + 10, 14)) {
            draggingSlider = true;
            activeSlider = setting;
        }
    }

    // ColorPicker
    private void drawImprovedColorPicker(int mouseX, int mouseY) {
        int pW = 220;
        int pH = 230;
        colorPickerX = windowX + (WINDOW_WIDTH - pW) / 2;
        colorPickerY = windowY + (WINDOW_HEIGHT - pH) / 2;

        drawRect(0, 0, width, height, 0x80000000);

        RenderUtils.drawRoundedRect(colorPickerX, colorPickerY, colorPickerX + pW, colorPickerY + pH, 10, 0xFF1E1E1E);
        RenderUtils.drawRoundedRectOutline(colorPickerX, colorPickerY, colorPickerX + pW, colorPickerY + pH, 10, 0xFF3A3A3A, 1.5f);

        mc.fontRenderer.drawStringWithShadow("Color Picker", colorPickerX + 12, colorPickerY + 10, 0xFFFFFFFF);

        float[] rgb = (float[]) activeColorSetting.getValue();
        float[] hsv = Color.RGBtoHSB((int)(rgb[0] * 255), (int)(rgb[1] * 255), (int)(rgb[2] * 255), null);

        // SV Square
        int svSize = 130;
        int svX = colorPickerX + 12;
        int svY = colorPickerY + 32;

        drawSaturationValueSquare(svX, svY, svSize, hsv[0]);
        RenderUtils.drawRoundedRectOutline(svX - 1, svY - 1, svX + svSize + 1, svY + svSize + 1, 3, 0xFF3A3A3A, 1.5f);

        int indX = svX + (int)(hsv[1] * svSize);
        int indY = svY + (int)((1 - hsv[2]) * svSize);

        RenderUtils.drawCircleOutline(indX, indY, 5, 0xFFFFFFFF, 2f);
        RenderUtils.drawCircleOutline(indX, indY, 3, 0xFF000000, 1.5f);

        // Hue Bar
        int hX = svX + svSize + 12;
        int hY = svY;
        int hW = 18;
        int hH = svSize;

        drawHueBar(hX, hY, hW, hH);
        RenderUtils.drawRoundedRectOutline(hX - 1, hY - 1, hX + hW + 1, hY + hH + 1, 3, 0xFF3A3A3A, 1.5f);

        int hIndY = hY + (int)(hsv[0] * hH);
        RenderUtils.drawRoundedRect(hX - 2, hIndY - 2, hX + hW + 2, hIndY + 2, 2, 0xFFFFFFFF);
        RenderUtils.drawRoundedRectOutline(hX - 2, hIndY - 2, hX + hW + 2, hIndY + 2, 2, 0xFF000000, 1f);

        // Alpha Bar
        int aX = svX;
        int aY = svY + svSize + 12;
        int aW = svSize;
        int aH = 14;

        drawCheckerboard(aX, aY, aW, aH);
        drawAlphaBar(aX, aY, aW, aH, rgb);
        RenderUtils.drawRoundedRectOutline(aX - 1, aY - 1, aX + aW + 1, aY + aH + 1, 3, 0xFF3A3A3A, 1.5f);

        int aIndX = aX + (int)(pickerAlpha * aW);
        RenderUtils.drawRoundedRect(aIndX - 2, aY - 2, aIndX + 2, aY + aH + 2, 2, 0xFFFFFFFF);
        RenderUtils.drawRoundedRectOutline(aIndX - 2, aY - 2, aIndX + 2, aY + aH + 2, 2, 0xFF000000, 1f);

        // Preview
        int previewX = hX;
        int previewY = aY;
        int previewW = hW;
        int previewH = aH;

        drawCheckerboard(previewX, previewY, previewW, previewH);
        int currentColor = new Color(rgb[0], rgb[1], rgb[2], pickerAlpha).getRGB();
        RenderUtils.drawRoundedRect(previewX, previewY, previewX + previewW, previewY + previewH, 3, currentColor);
        RenderUtils.drawRoundedRectOutline(previewX, previewY, previewX + previewW, previewY + previewH, 3, 0xFF3A3A3A, 1f);

        // HEX Input
        int hexY = aY + aH + 12;
        mc.fontRenderer.drawString("HEX:", svX, hexY + 4, 0xFF999999);

        int hexInputX = svX + 26;
        int hexInputW = 94;
        int hexInputH = 18;

        boolean hexHover = isHovered(mouseX, mouseY, hexInputX, hexY, hexInputW, hexInputH);
        RenderUtils.drawRoundedRect(hexInputX, hexY, hexInputX + hexInputW, hexY + hexInputH, 4,
                editingHex ? 0xFF2A2A2A : (hexHover ? 0xFF252525 : 0xFF1A1A1A));
        RenderUtils.drawRoundedRectOutline(hexInputX, hexY, hexInputX + hexInputW, hexY + hexInputH, 4,
                editingHex ? 0xFF569CD6 : 0xFF3A3A3A, 1f);

        String hexText = editingHex ? hexInput : colorToHex(rgb);
        mc.fontRenderer.drawString(hexText, hexInputX + 5, hexY + 5, 0xFFFFFFFF);

        if (editingHex && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = hexInputX + 5 + mc.fontRenderer.getStringWidth(hexInput);
            drawRect(cursorX, hexY + 3, cursorX + 1, hexY + hexInputH - 3, 0xFFFFFFFF);
        }

        // RGB Values
        int rgbY = hexY;
        int rgbX = hexInputX + hexInputW + 8;
        mc.fontRenderer.drawString("R:" + (int)(rgb[0] * 255), rgbX, rgbY, 0xFFFF6B6B);
        mc.fontRenderer.drawString("G:" + (int)(rgb[1] * 255), rgbX + 35, rgbY, 0xFF6BFF6B);
        mc.fontRenderer.drawString("B:" + (int)(rgb[2] * 255), rgbX, rgbY + 10, 0xFF6B6BFF);

        // Done Button
        int btnY = hexY + 26;
        int btnH = 20;
        int doneBtnW = 70;
        int doneBtnX = svX + (svSize - doneBtnW) / 2;

        boolean doneHover = isHovered(mouseX, mouseY, doneBtnX, btnY, doneBtnW, btnH);
        RenderUtils.drawRoundedRect(doneBtnX, btnY, doneBtnX + doneBtnW, btnY + btnH, 5,
                doneHover ? 0xFF45A049 : 0xFF4CAF50);
        String doneTxt = "Done";
        mc.fontRenderer.drawString(doneTxt,
                doneBtnX + (doneBtnW - mc.fontRenderer.getStringWidth(doneTxt))/2,
                btnY + 6, 0xFFFFFFFF);

        if (draggingSatVal) {
            float s = MathHelper.clamp((float)(mouseX - svX) / svSize, 0, 1);
            float v = MathHelper.clamp(1 - (float)(mouseY - svY) / svSize, 0, 1);
            updateColor(hsv[0], s, v);
        }
        if (draggingHue) {
            float h = MathHelper.clamp((float)(mouseY - hY) / hH, 0, 1);
            updateColor(h, hsv[1], hsv[2]);
        }
        if (draggingAlpha) {
            pickerAlpha = MathHelper.clamp((float)(mouseX - aX) / aW, 0, 1);
        }
    }

    private void updateColor(float h, float s, float v) {
        int c = Color.HSBtoRGB(h, s, v);
        Color color = new Color(c);
        activeColorSetting.setValue(new float[]{color.getRed()/255f, color.getGreen()/255f, color.getBlue()/255f});
    }

    private void drawCheckerboard(int x, int y, int width, int height) {
        int squareSize = 4;
        for (int i = 0; i < width; i += squareSize) {
            for (int j = 0; j < height; j += squareSize) {
                int color = ((i / squareSize) + (j / squareSize)) % 2 == 0 ? 0xFFCCCCCC : 0xFFFFFFFF;
                drawRect(x + i, y + j,
                        x + Math.min(i + squareSize, width),
                        y + Math.min(j + squareSize, height),
                        color);
            }
        }
    }

    private void drawAlphaBar(int x, int y, int w, int h, float[] rgb) {
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_QUADS);

        GL11.glColor4f(rgb[0], rgb[1], rgb[2], 0f);
        GL11.glVertex2d(x, y);
        GL11.glVertex2d(x, y + h);

        GL11.glColor4f(rgb[0], rgb[1], rgb[2], 1f);
        GL11.glVertex2d(x + w, y + h);
        GL11.glVertex2d(x + w, y);

        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glPopMatrix();
    }

    private void drawSaturationValueSquare(int x, int y, int size, float hue) {
        GL11.glPushMatrix();
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        int step = 3;
        for (int i = 0; i < size; i += step) {
            for (int j = 0; j < size; j += step) {
                float sat = (float) i / size;
                float val = 1 - (float) j / size;
                int c = Color.HSBtoRGB(hue, sat, val);
                float r = ((c >> 16) & 0xFF) / 255f;
                float g = ((c >> 8) & 0xFF) / 255f;
                float b = (c & 0xFF) / 255f;

                GL11.glColor3f(r, g, b);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glVertex2i(x + i, y + j);
                GL11.glVertex2i(x + i, y + j + step);
                GL11.glVertex2i(x + i + step, y + j + step);
                GL11.glVertex2i(x + i + step, y + j);
                GL11.glEnd();
            }
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glPopMatrix();
    }

    private void drawHueBar(int x, int y, int w, int h) {
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= h; i += 2) {
            float hue = (float) i / h;
            int c = Color.HSBtoRGB(hue, 1, 1);
            float r = ((c >> 16) & 0xFF) / 255f;
            float g = ((c >> 8) & 0xFF) / 255f;
            float b = (c & 0xFF) / 255f;
            GL11.glColor3f(r, g, b);
            GL11.glVertex2d(x, y + i);
            GL11.glVertex2d(x + w, y + i);
        }
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glPopMatrix();
    }

    private String colorToHex(float[] rgb) {
        int r = (int)(rgb[0] * 255);
        int g = (int)(rgb[1] * 255);
        int b = (int)(rgb[2] * 255);
        return String.format("#%02X%02X%02X", r, g, b);
    }

    private void setColorFromHex(String hex) {
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            if (hex.length() == 6) {
                int r = Integer.parseInt(hex.substring(0, 2), 16);
                int g = Integer.parseInt(hex.substring(2, 4), 16);
                int b = Integer.parseInt(hex.substring(4, 6), 16);
                activeColorSetting.setValue(new float[]{r/255f, g/255f, b/255f});
            }
        } catch (Exception ignored) {}
    }

    // --- ОБРАБОТКА ВВОДА ---

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (editingHex) {
            if (keyCode == 1) {
                editingHex = false;
                hexInput = "";
            } else if (keyCode == 28) {
                setColorFromHex(hexInput);
                editingHex = false;
                hexInput = "";
            } else if (keyCode == 14) {
                if (hexInput.length() > 0) {
                    hexInput = hexInput.substring(0, hexInput.length() - 1);
                }
            } else if (hexInput.length() < 7) {
                if ((typedChar >= '0' && typedChar <= '9') ||
                        (typedChar >= 'a' && typedChar <= 'f') ||
                        (typedChar >= 'A' && typedChar <= 'F') ||
                        typedChar == '#') {
                    hexInput += typedChar;
                }
            }
        } else {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (showColorPicker) {
            int svX = colorPickerX + 12;
            int svY = colorPickerY + 32;
            int svSize = 130;
            int hX = svX + svSize + 12;
            int hY = svY;
            int hW = 18;
            int hH = svSize;
            int aX = svX;
            int aY = svY + svSize + 12;
            int aW = svSize;
            int aH = 14;

            if (isHovered(mouseX, mouseY, svX, svY, svSize, svSize)) {
                draggingSatVal = true;
                return;
            }
            if (isHovered(mouseX, mouseY, hX, hY, hW, hH)) {
                draggingHue = true;
                return;
            }
            if (isHovered(mouseX, mouseY, aX, aY, aW, aH)) {
                draggingAlpha = true;
                return;
            }

            int hexY = aY + aH + 12;
            int hexInputX = svX + 26;
            int hexInputW = 94;
            int hexInputH = 18;
            if (isHovered(mouseX, mouseY, hexInputX, hexY, hexInputW, hexInputH)) {
                editingHex = true;
                float[] rgb = (float[]) activeColorSetting.getValue();
                hexInput = colorToHex(rgb);
                return;
            } else if (editingHex) {
                editingHex = false;
                hexInput = "";
            }

            int btnY = hexY + 26;
            int btnH = 20;
            int doneBtnW = 70;
            int doneBtnX = svX + (svSize - doneBtnW) / 2;

            if (isHovered(mouseX, mouseY, doneBtnX, btnY, doneBtnW, btnH)) {
                showColorPicker = false;
                activeColorSetting = null;
                editingHex = false;
                hexInput = "";
                return;
            }

            int pW = 220;
            int pH = 230;
            if (!isHovered(mouseX, mouseY, colorPickerX, colorPickerY, pW, pH)) {
                showColorPicker = false;
                activeColorSetting = null;
                editingHex = false;
                hexInput = "";
            }
            return;
        }

        if (isHovered(mouseX, mouseY, windowX, windowY, WINDOW_WIDTH, 35)) {
            dragging = true;
            dragX = mouseX - windowX;
            dragY = mouseY - windowY;
            return;
        }

        if (isHovered(mouseX, mouseY, windowX, windowY + 40, SIDEBAR_WIDTH, WINDOW_HEIGHT - 48)) {
            float mY = windowY + 44 + moduleScrollY;

            for (Module m : ModuleManager.getModules()) {
                if (isHovered(mouseX, mouseY, windowX + 8, (int) mY, SIDEBAR_WIDTH - 16, 22)) {
                    if (button == 0) {
                        selectedModule = m;
                        targetSettingsScrollY = 0;
                        settingsScrollY = 0;
                    } else if (button == 1) {
                        m.toggle();
                    }
                    return;
                }
                mY += 26;
            }
        }

        if (selectedModule != null && isHovered(mouseX, mouseY, windowX + SIDEBAR_WIDTH, windowY + 32, WINDOW_WIDTH - SIDEBAR_WIDTH, WINDOW_HEIGHT - 38)) {
            int settingsX = windowX + SIDEBAR_WIDTH + 15;
            int settingsWidth = WINDOW_WIDTH - SIDEBAR_WIDTH - 30;

            float cy = windowY + 38 + settingsScrollY;

            if (isHovered(mouseX, mouseY, settingsX, (int) cy, settingsWidth, 24)) {
                selectedModule.toggle();
                return;
            }
            cy += 28;

            for (Setting s : selectedModule.getSettings()) {
                int h = (s.getType() == SettingType.NUMBER ? 32 : 24);
                int fullStep = (s.getType() == SettingType.NUMBER ? 38 : 28);

                if (isHovered(mouseX, mouseY, settingsX, (int) cy, settingsWidth, h)) {
                    switch (s.getType()) {
                        case BOOLEAN:
                            s.setValue(!(boolean) s.getValue());
                            break;

                        case MODE:
                            s.cycle(button == 0 ? 1 : -1);
                            break;

                        case COLOR:
                            showColorPicker = true;
                            activeColorSetting = s;
                            pickerAlpha = 1.0f;
                            break;

                        case NUMBER:
                            updateSliderValue(s, mouseX, settingsX + 8, settingsWidth - 16);
                            draggingSlider = true;
                            activeSlider = s;
                            break;
                    }
                    return;
                }
                cy += fullStep;
            }
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = false;
        draggingSlider = false;
        activeSlider = null;
        draggingHue = false;
        draggingSatVal = false;
        draggingAlpha = false;
    }

    @Override
    public void mouseClickMove(int mouseX, int mouseY, int btn, long time) {
        if (dragging) {
            windowX = mouseX - dragX;
            windowY = mouseY - dragY;
        }
    }

    private void handleScroll(int mouseX, int mouseY) {
        if (showColorPicker) return;
        int dWheel = Mouse.getDWheel();
        if (dWheel != 0) {
            if (isHovered(mouseX, mouseY, windowX, windowY, SIDEBAR_WIDTH, WINDOW_HEIGHT)) {
                targetModuleScrollY += dWheel > 0 ? 40 : -40;
                targetModuleScrollY = MathHelper.clamp(targetModuleScrollY, maxModuleScrollY, 0);
            }
            else if (isHovered(mouseX, mouseY, windowX + SIDEBAR_WIDTH, windowY, WINDOW_WIDTH - SIDEBAR_WIDTH, WINDOW_HEIGHT)) {
                targetSettingsScrollY += dWheel > 0 ? 40 : -40;
                targetSettingsScrollY = MathHelper.clamp(targetSettingsScrollY, maxSettingsScrollY, 0);
            }
        }
    }

    // --- УТИЛИТЫ ---
    private float interpolate(float cur, float target, float speed) {
        return cur + (target - cur) * speed;
    }

    private int interpolateColor(int c1, int c2, float t) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF, a1 = (c1 >> 24) & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF, a2 = (c2 >> 24) & 0xFF;
        int r = (int)(r1 + (r2 - r1) * t);
        int g = (int)(g1 + (g2 - g1) * t);
        int b = (int)(b1 + (b2 - b1) * t);
        int a = (int)(a1 + (a2 - a1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private boolean isHovered(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    // --- RENDER UTILS CLASS ---
    public static class RenderUtils {

        public static void drawRoundedRect(float x, float y, float x1, float y1, float radius, int color) {
            GL11.glPushMatrix();
            float r = ((color >> 16) & 0xFF) / 255.0F;
            float g = ((color >> 8) & 0xFF) / 255.0F;
            float b = ((color) & 0xFF) / 255.0F;
            float a = ((color >> 24) & 0xFF) / 255.0F;

            GlStateManager.enableBlend();
            GlStateManager.disableTexture2D();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(r, g, b, a);

            GL11.glBegin(GL11.GL_POLYGON);
            for (int i = 0; i <= 90; i+=10) {
                GL11.glVertex2d(x + radius + Math.sin(i * Math.PI / 180.0D) * radius * -1.0D, y + radius + Math.cos(i * Math.PI / 180.0D) * radius * -1.0D);
            }
            for (int i = 90; i <= 180; i+=10) {
                GL11.glVertex2d(x + radius + Math.sin(i * Math.PI / 180.0D) * radius * -1.0D, y1 - radius + Math.cos(i * Math.PI / 180.0D) * radius * -1.0D);
            }
            for (int i = 0; i <= 90; i+=10) {
                GL11.glVertex2d(x1 - radius + Math.sin(i * Math.PI / 180.0D) * radius, y1 - radius + Math.cos(i * Math.PI / 180.0D) * radius);
            }
            for (int i = 90; i <= 180; i+=10) {
                GL11.glVertex2d(x1 - radius + Math.sin(i * Math.PI / 180.0D) * radius, y + radius + Math.cos(i * Math.PI / 180.0D) * radius);
            }
            GL11.glEnd();

            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GL11.glPopMatrix();
        }

        public static void drawRoundedRectOutline(float x, float y, float x1, float y1, float radius, int color, float width) {
            GL11.glPushMatrix();
            float r = ((color >> 16) & 0xFF) / 255.0F;
            float g = ((color >> 8) & 0xFF) / 255.0F;
            float b = ((color) & 0xFF) / 255.0F;
            float a = ((color >> 24) & 0xFF) / 255.0F;

            GlStateManager.enableBlend();
            GlStateManager.disableTexture2D();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(r, g, b, a);
            GL11.glLineWidth(width);

            GL11.glBegin(GL11.GL_LINE_LOOP);
            for (int i = 0; i <= 90; i+=10) {
                GL11.glVertex2d(x + radius + Math.sin(i * Math.PI / 180.0D) * radius * -1.0D, y + radius + Math.cos(i * Math.PI / 180.0D) * radius * -1.0D);
            }
            for (int i = 90; i <= 180; i+=10) {
                GL11.glVertex2d(x + radius + Math.sin(i * Math.PI / 180.0D) * radius * -1.0D, y1 - radius + Math.cos(i * Math.PI / 180.0D) * radius * -1.0D);
            }
            for (int i = 0; i <= 90; i+=10) {
                GL11.glVertex2d(x1 - radius + Math.sin(i * Math.PI / 180.0D) * radius, y1 - radius + Math.cos(i * Math.PI / 180.0D) * radius);
            }
            for (int i = 90; i <= 180; i+=10) {
                GL11.glVertex2d(x1 - radius + Math.sin(i * Math.PI / 180.0D) * radius, y + radius + Math.cos(i * Math.PI / 180.0D) * radius);
            }
            GL11.glEnd();

            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GL11.glPopMatrix();
        }

        public static void drawCircle(float x, float y, float radius, int color) {
            drawRoundedRect(x - radius, y - radius, x + radius, y + radius, radius, color);
        }

        public static void drawCircleOutline(float x, float y, float radius, int color, float width) {
            drawRoundedRectOutline(x - radius, y - radius, x + radius, y + radius, radius, color, width);
        }

        public static void startScissor(int x, int y, int width, int height) {
            Minecraft mc = Minecraft.getMinecraft();
            ScaledResolution sr = new ScaledResolution(mc);
            int scale = sr.getScaleFactor();

            int scissorY = mc.displayHeight - (y * scale) - (height * scale);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(x * scale, scissorY, width * scale, height * scale);
        }

        public static void stopScissor() {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }
}