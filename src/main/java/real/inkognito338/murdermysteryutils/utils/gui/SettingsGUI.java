package real.inkognito338.murdermysteryutils.utils.gui;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import real.inkognito338.murdermysteryutils.Main;
import real.inkognito338.murdermysteryutils.modules.Scripts;
import real.inkognito338.murdermysteryutils.online.OnlineMode;
import real.inkognito338.murdermysteryutils.online.OnlineModeGUI;
import real.inkognito338.murdermysteryutils.utils.*;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;
import real.inkognito338.murdermysteryutils.modules.HUD;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

@SuppressWarnings("SpellCheckingInspection")
public class SettingsGUI extends GuiScreen {
    private static final int WINDOW_WIDTH = 480;
    private static final int WINDOW_HEIGHT = 300;
    private static final int SIDEBAR_WIDTH = 135;
    private static final net.minecraft.util.ResourceLocation PALETTE_ICON =
            new net.minecraft.util.ResourceLocation("murdermysteryutils", "textures/gui/palette.png");
    private static final int SIDEBAR_OFFSET_Y = 58;
    private static final int SETTINGS_VIEW_TOP_OFFSET = 32;
    private static final int SETTINGS_VIEW_HEIGHT = WINDOW_HEIGHT - 38;
    private static int windowX = 0;
    private static int windowY = 0;
    private static Module selectedModule = null;

    // Theme selector
    private boolean showThemeSelector = false;
    private float themeSelectorScrollY = 0;
    private float maxThemeSelectorScrollY = 0;
    private int themeDropX, themeDropY, themeDropW, themeDropH;

    // Списки анимаций и текстовых полей
    private final Map<String, SwitchAnimation> switchAnimations = new HashMap<>();
    private final Map<Setting, GuiTextField> textFields = new HashMap<>();
    private final Map<String, Float> hoverAnims = new HashMap<>();

    // Скроллинг
    private float settingsScrollY = 0;
    private float maxSettingsScrollY = 0;
    private float moduleScrollY = 0;
    private float maxModuleScrollY = 0;

    // Состояния UX/UI элементов
    private boolean dragging = false;
    private int dragX, dragY;
    private boolean draggingSlider = false;
    private Setting activeSlider = null;

    // Поиск и Анимация окна
    private GuiTextField searchField;

    // Продвинутый Dropdown для MODE
    private Setting openDropdownSetting = null;
    private int dropdownDrawX, dropdownDrawY, dropdownDrawW;

    // Скролл внутри выпадающего списка MODE
    private float dropdownScrollY = 0;
    private boolean draggingDropdownScroll = false;
    private float dropdownScrollStartY = 0;
    private float dropdownScrollStartValue = 0;

    // Колорпикер
    private final ColorPicker colorPicker = new ColorPicker();

    private List<Module> sortedModules = new ArrayList<>();

    private boolean draggingModuleScroll = false;
    private boolean draggingSettingsScroll = false;
    private boolean draggingThemeScroll = false;
    private float scrollDragOffset = 0;

    // HUD Position editor
    private boolean editingHudPosition = false;
    private Setting activeHudPositionSetting = null;
    private boolean draggingHudPreview = false;
    private int hudPreviewDragOffsetX = 0;
    private int hudPreviewDragOffsetY = 0;

    private static final int SETTINGS_CONTENT_TOP = 38;
    private static final int SETTINGS_CONTENT_BOTTOM = WINDOW_HEIGHT - 12; // 288, запас больше радиуса скругления (10)
    private static final int SETTINGS_CONTENT_HEIGHT = SETTINGS_CONTENT_BOTTOM - SETTINGS_CONTENT_TOP; // 250

    private float interpolate(float current, float target) {
        return current + (target - current) * 0.5f;
    }

    private int mixColors(int color2, float ratio) {
        if (ratio > 1f) ratio = 1f;
        if (ratio < 0f) ratio = 0f;
        int a1 = (0) & 0xFF;
        int r1 = (0) & 0xFF;
        int g1 = (0) & 0xFF;
        int b1 = 0;
        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        int a = (int) (a1 + ratio * (a2 - a1));
        int r = (int) (r1 + ratio * (r2 - r1));
        int g = (int) (g1 + ratio * (g2 - g1));
        int b = (int) (b1 + ratio * (b2 - b1));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private float getHoverStage(String id, boolean hovered) {
        float current = hoverAnims.getOrDefault(id, 0f);
        current = interpolate(current, hovered ? 1f : 0f);
        hoverAnims.put(id, current);
        return current;
    }

    private boolean isHovered(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private GuiTextField getOrCreateField(Setting s, int tbX, int tbY, int tbW, int tbH) {
        GuiTextField f = textFields.get(s);
        int centeredY = tbY + (tbH / 2) - 4;
        if (f == null) {
            f = new GuiTextField(s.hashCode(), mc.fontRenderer, tbX + 1, centeredY, tbW - 2, tbH - 2);
            f.setMaxStringLength(256);
            f.setEnableBackgroundDrawing(false);
            f.setText((String) s.getValue());
            f.setCursorPositionEnd();
            textFields.put(s, f);
        } else {
            f.x = tbX + 1;
            f.y = centeredY;
            f.width = tbW - 2;
            f.height = tbH - 2;
        }
        return f;
    }

    private boolean isAnyFieldFocused() {
        if (searchField != null && searchField.isFocused()) return true;
        for (GuiTextField f : textFields.values()) if (f.isFocused()) return true;
        return false;
    }

    private void blurAllFields() {
        if (searchField != null) searchField.setFocused(false);
        for (GuiTextField f : textFields.values()) f.setFocused(false);
    }

    private void syncField(Setting s, GuiTextField f) {
        s.setValue(f.getText());
    }

    private String stripColors(String s) {
        if (s == null) return "";
        return s.replaceAll("§[0-9a-fk-or]", "");
    }

    private int getScriptListHeight(Setting s, int sw) {
        String text = (String) s.getValue();
        if (text == null || text.isEmpty() || text.equals("Нет загруженных скриптов")) return 30;

        String[] lines = text.split("\n");
        int totalHeight = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = stripColors(lines[i].trim());
            if (line.isEmpty()) continue;

            if (line.startsWith("[✓]") || line.startsWith("[✗]")) {
                // Скипаем строку автора (она нужна только для отображения)
                if (i + 1 < lines.length) {
                    String next = stripColors(lines[i + 1]);
                    if (next.contains("Автор:") || next.contains("Author:")) i++;
                }

                // Парсим описание для расчета высоты
                if (i + 1 < lines.length) {
                    String next = stripColors(lines[i + 1]);
                    if (next.contains("Описание:") || next.contains("Description:")) {
                        String desc = next.replace("Описание:", "").replace("Description:", "").trim();
                        List<String> wrapped = mc.fontRenderer.listFormattedStringToWidth(desc, sw - 80);
                        totalHeight += 32 + (wrapped.size() * 10) + 4;
                        i++; // скипаем строку описания
                    } else {
                        totalHeight += 32 + 4;
                    }
                } else {
                    totalHeight += 32 + 4;
                }
            }
        }
        return totalHeight + 4;
    }

    @Override
    public void initGui() {
        if (windowX == 0 && windowY == 0) {
            ScaledResolution sr = new ScaledResolution(mc);
            windowX = (sr.getScaledWidth() - WINDOW_WIDTH) / 2;
            windowY = (sr.getScaledHeight() - WINDOW_HEIGHT) / 2;
        }

        sortedModules = new ArrayList<>(ModuleManager.getModules());
        sortedModules.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));

        if (selectedModule == null && !sortedModules.isEmpty())
            selectedModule = sortedModules.get(0);

        textFields.clear();
        hoverAnims.clear();
        openDropdownSetting = null;
        showThemeSelector = false;

        editingHudPosition = false;
        activeHudPositionSetting = null;

        searchField = new GuiTextField(999, mc.fontRenderer, windowX + 8, windowY + 38, SIDEBAR_WIDTH - 16, 14);
        searchField.setMaxStringLength(32);
        searchField.setEnableBackgroundDrawing(false);

        Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        for (Map.Entry<Setting, GuiTextField> e : textFields.entrySet())
            syncField(e.getKey(), e.getValue());
        blurAllFields();
        ConfigManager.save();
        colorPicker.closeWithCancel();
    }

    @Override
    public void updateScreen() {
        if (searchField != null) searchField.updateCursorCounter();
        for (GuiTextField f : textFields.values()) f.updateCursorCounter();
    }

    private void handleScrollDragging(int mouseY) {
        if (!Mouse.isButtonDown(0)) {
            draggingModuleScroll = false;
            draggingSettingsScroll = false;
            draggingThemeScroll = false;
            return;
        }

        if (draggingModuleScroll && maxModuleScrollY < 0) {
            float viewH = WINDOW_HEIGHT - SIDEBAR_OFFSET_Y - 14;
            float totalH = viewH - maxModuleScrollY;
            float thumbH = Math.max(10, (viewH / totalH) * viewH);
            float trackTop = windowY + SIDEBAR_OFFSET_Y + 4;
            float maxThumbY = viewH - thumbH;

            float relativeY = (mouseY - trackTop) - scrollDragOffset;
            float scrollFraction = MathHelper.clamp(relativeY / maxThumbY, 0f, 1f);

            moduleScrollY = MathHelper.clamp(-(scrollFraction * -maxModuleScrollY), maxModuleScrollY, 0f);
        }

        if (draggingSettingsScroll && maxSettingsScrollY < 0) {
            float viewH = SETTINGS_CONTENT_HEIGHT;
            float totalH = viewH - maxSettingsScrollY;
            float thumbH = Math.max(10, (viewH / totalH) * viewH);
            float trackTop = windowY + SETTINGS_CONTENT_TOP;
            float maxThumbY = viewH - thumbH;
            float relativeY = (mouseY - trackTop) - scrollDragOffset;
            float scrollFraction = MathHelper.clamp(relativeY / maxThumbY, 0f, 1f);
            settingsScrollY = MathHelper.clamp(-(scrollFraction * -maxSettingsScrollY), maxSettingsScrollY, 0f);
        }

        if (draggingThemeScroll && showThemeSelector && maxThemeSelectorScrollY < 0) {
            float viewH = themeDropH - 4;
            float totalH = viewH - maxThemeSelectorScrollY;
            float thumbH = Math.max(10, (viewH / totalH) * viewH);
            float trackTop = themeDropY + 2;
            float maxThumbY = viewH - thumbH;

            float relativeY = (mouseY - trackTop) - scrollDragOffset;
            float scrollFraction = MathHelper.clamp(relativeY / maxThumbY, 0f, 1f);

            themeSelectorScrollY = MathHelper.clamp(-(scrollFraction * -maxThemeSelectorScrollY), maxThemeSelectorScrollY, 0f);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (editingHudPosition && activeHudPositionSetting != null) {
            drawHudPositionEditorOverlay();
            return;
        }

        handleScrollDragging(mouseY);

        drawRect(0, 0, width, height, 0x70000000);

        for (int i = 1; i <= 6; i++) {
            RenderUtils.drawRoundedRect(windowX - i, windowY - i, windowX + WINDOW_WIDTH + i, windowY + WINDOW_HEIGHT + i, 10 + i, mixColors(0x12000000, (7 - i) / 6f));
        }

        RenderUtils.drawRoundedRect(windowX, windowY, windowX + WINDOW_WIDTH, windowY + WINDOW_HEIGHT, 10, ThemeManager.getBackground());
        RenderUtils.drawRoundedRect(windowX, windowY, windowX + SIDEBAR_WIDTH, windowY + WINDOW_HEIGHT, 10, ThemeManager.getSidebar());
        drawRect(windowX + SIDEBAR_WIDTH, windowY + 38, windowX + SIDEBAR_WIDTH + 1, windowY + WINDOW_HEIGHT - 8, ThemeManager.getSidebarSeparator());

        mc.fontRenderer.drawStringWithShadow("MurderMysteryUtils §bv" + Main.VERSION, windowX + 12, windowY + 10, ThemeManager.getTextPrimary());
        mc.fontRenderer.drawString("by inkognito338", windowX + 12, windowY + 22, ThemeManager.getTextDim());

        mc.fontRenderer.drawString("by inkognito338", windowX + 12, windowY + 22, ThemeManager.getTextDim());
        int searchBg = searchField.isFocused() ? ThemeManager.getSearchBgFocused() : (isHovered(mouseX, mouseY, windowX + 8, windowY + 36, SIDEBAR_WIDTH - 16, 16) ? ThemeManager.getElementBgHover() : ThemeManager.getSearchBg());
        RenderUtils.drawRoundedRect(windowX + 8, windowY + 36, windowX + SIDEBAR_WIDTH - 8, windowY + 52, 4, searchBg);
        RenderUtils.drawRoundedRectOutline(windowX + 8, windowY + 36, windowX + SIDEBAR_WIDTH - 8, windowY + 52, 4, searchField.isFocused() ? ThemeManager.getAccent() : ThemeManager.getBorder(), 1f);

        searchField.x = windowX + 12;
        searchField.y = windowY + 40;
        if (!searchField.isFocused() && searchField.getText().isEmpty()) {
            mc.fontRenderer.drawString("Search...", windowX + 14, windowY + 40, ThemeManager.getTextDim());
        } else {
            searchField.drawTextBox();
        }

        RenderUtils.startScissor(windowX, windowY + SIDEBAR_OFFSET_Y, SIDEBAR_WIDTH, WINDOW_HEIGHT - SIDEBAR_OFFSET_Y - 14);
        float curModY = windowY + SIDEBAR_OFFSET_Y + 4 + moduleScrollY;
        float modStart = curModY;

        for (Module module : sortedModules) {
            if (!searchField.getText().isEmpty() && !module.getName().toLowerCase().contains(searchField.getText().toLowerCase())) {
                continue;
            }

            boolean sel = (selectedModule == module);
            int btnX = windowX + 8, btnW = SIDEBAR_WIDTH - 16, btnH = 22;
            boolean hov = isHovered(mouseX, mouseY, btnX, (int) curModY - 2, btnW, btnH + 4);

            float hStage = getHoverStage("mod_" + module.getName(), hov);
            int baseColor = sel ? ThemeManager.getElementBgActive() : mixColors(ThemeManager.getElementBgHover(), hStage);

            if (baseColor != 0) {
                RenderUtils.drawRoundedRect(btnX, curModY, btnX + btnW, curModY + btnH, 5, baseColor);
            }
            if (sel) {
                RenderUtils.drawRoundedRectOutline(btnX, curModY, btnX + btnW, curModY + btnH, 5, ThemeManager.getAccent(), 1.2f);
                RenderUtils.drawRoundedRect(btnX + 2, curModY + 6, btnX + 4, curModY + btnH - 6, 1, ThemeManager.getAccent());
            }

            int tc = sel ? ThemeManager.getTextPrimary() : (module.isToggled() ? ThemeManager.getSwitchOn() : ThemeManager.getTextDim());
            mc.fontRenderer.drawString(module.getName(), btnX + 8, (int) curModY + 7, tc);
            if (module.isToggled())
                RenderUtils.drawCircle(btnX + btnW - 6, curModY + btnH / 2f, 2.5f, ThemeManager.getSwitchOn());

            curModY += 26;
        }
        maxModuleScrollY = Math.min(0, (WINDOW_HEIGHT - SIDEBAR_OFFSET_Y - 14) - (curModY - modStart));
        moduleScrollY = MathHelper.clamp(moduleScrollY, maxModuleScrollY, 0f);
        RenderUtils.stopScissor();

        if (maxModuleScrollY < 0) {
            float viewH = WINDOW_HEIGHT - SIDEBAR_OFFSET_Y - 14;
            float totalH = curModY - modStart;
            float thumbH = Math.max(10, (viewH / totalH) * viewH);
            float thumbY = windowY + SIDEBAR_OFFSET_Y + 4 + (-moduleScrollY / -maxModuleScrollY) * (viewH - thumbH);
            RenderUtils.drawRoundedRect(windowX + SIDEBAR_WIDTH - 4, thumbY, windowX + SIDEBAR_WIDTH - 2, thumbY + thumbH, 1, ThemeManager.getScrollbar());
        }

        Setting pendingDropdownToDraw = null;

        if (selectedModule != null) {
            int sx = windowX + SIDEBAR_WIDTH + 15;
            int sw = WINDOW_WIDTH - SIDEBAR_WIDTH - 34;

            mc.fontRenderer.drawStringWithShadow(selectedModule.getName(), sx, windowY + 12, ThemeManager.getTextPrimary());
            drawRect(sx, windowY + 28, sx + sw, windowY + 29, ThemeManager.getBorder());

            drawThemeSettings(mouseX, mouseY);
            drawOnlineModeButton(mouseX, mouseY);

            RenderUtils.startScissor(windowX + SIDEBAR_WIDTH, windowY + SETTINGS_CONTENT_TOP, WINDOW_WIDTH - SIDEBAR_WIDTH - 6, SETTINGS_CONTENT_HEIGHT);
            float cy = windowY + SETTINGS_CONTENT_TOP + settingsScrollY;
            float cyStart = cy;

            drawSettingBackground(sx, (int) cy, sw, 24, mouseX, mouseY);
            mc.fontRenderer.drawString("Enable Module", sx + 8, (int) cy + 8, ThemeManager.getTextSecondary());
            drawSwitch(sx + sw - 34, (int) cy + 4, selectedModule.isToggled());
            cy += 28;

            for (Setting s : selectedModule.getSettings()) {
                int rowH;
                switch (s.getType()) {
                    case NUMBER: rowH = 38; break;
                    case TAB_ANIMATION: rowH = 24; break;
                    case SCRIPT_LIST:
                        rowH = 30;
                        String text = (String) s.getValue();
                        if (text != null && !text.isEmpty() && !text.equals("Нет загруженных скриптов")) {
                            rowH = getScriptListHeight(s, sw);
                        }
                        break;
                    case SCRIPT_BUTTON: rowH = 34; break;
                    default: rowH = 28; break;
                }
                boolean elementVisible = cy + rowH > windowY + SETTINGS_CONTENT_TOP && cy < windowY + SETTINGS_CONTENT_BOTTOM;
                if (elementVisible) {
                    switch (s.getType()) {
                        case BOOLEAN:
                            drawSettingBackground(sx, (int) cy, sw, 24, mouseX, mouseY);
                            mc.fontRenderer.drawString(s.getName(), sx + 8, (int) cy + 8, ThemeManager.getTextSecondary());
                            drawSwitch(sx + sw - 34, (int) cy + 4, (boolean) s.getValue());
                            break;
                        case SCRIPT_BUTTON:
                            drawScriptButton(s, sx, (int) cy, sw, mouseX, mouseY);
                            break;
                        case SCRIPT_LIST:
                            drawScriptList(s, sx, (int) cy, sw, mouseX, mouseY);
                            break;
                        case NUMBER:
                            drawSettingBackground(sx, (int) cy, sw, 32, mouseX, mouseY);
                            String vs = String.format("%.2f", ((Number) s.getValue()).doubleValue());
                            mc.fontRenderer.drawString(s.getName(), sx + 8, (int) cy + 5, ThemeManager.getTextSecondary());
                            mc.fontRenderer.drawString(vs, sx + sw - mc.fontRenderer.getStringWidth(vs) - 5, (int) cy + 5, ThemeManager.getTextDim());
                            drawSlider(s, sx + 8, (int) cy + 19, sw - 16);
                            break;
                        case MODE:
                            drawSettingBackground(sx, (int) cy, sw, 24, mouseX, mouseY);
                            mc.fontRenderer.drawString(s.getName(), sx + 8, (int) cy + 8, ThemeManager.getTextSecondary());

                            int dW = 90, dH = 16;
                            int dX = sx + sw - dW - 6, dY = (int) cy + 4;
                            RenderUtils.drawRoundedRect(dX, dY, dX + dW, dY + dH, 4, ThemeManager.getDropdownBg());
                            RenderUtils.drawRoundedRectOutline(dX, dY, dX + dW, dY + dH, 4, openDropdownSetting == s ? ThemeManager.getAccent() : ThemeManager.getBorder(), 1f);
                            String modeStr = s.getMode();
                            RenderUtils.startScissor(dX, dY, dW - 12, dH);
                            mc.fontRenderer.drawString(modeStr, dX + 5, dY + 4, ThemeManager.getAccent());
                            RenderUtils.stopScissor();
                            mc.fontRenderer.drawString(openDropdownSetting == s ? "▲" : "▼", dX + dW - 10, dY + 4, ThemeManager.getTextDim());

                            if (openDropdownSetting == s) {
                                int ddX = dX;
                                if (ddX + dW > windowX + WINDOW_WIDTH - 5) {
                                    ddX = windowX + WINDOW_WIDTH - 5 - dW;
                                }
                                dropdownDrawX = ddX;
                                dropdownDrawY = dY + dH;
                                dropdownDrawW = dW;
                                pendingDropdownToDraw = s;
                            }
                            break;
                        case COLOR:
                            drawSettingBackground(sx, (int) cy, sw, 24, mouseX, mouseY);
                            mc.fontRenderer.drawString(s.getName(), sx + 8, (int) cy + 8, ThemeManager.getTextSecondary());
                            float[] rgb = (float[]) s.getValue();
                            int cc = new Color(rgb[0], rgb[1], rgb[2]).getRGB();
                            int cbx = sx + sw - 28, cby = (int) cy + 4;
                            drawCheckerboard(cbx, cby);
                            RenderUtils.drawRoundedRect(cbx, cby, cbx + 20, cby + 16, 3, cc);
                            RenderUtils.drawRoundedRectOutline(cbx, cby, cbx + 20, cby + 16, 3, ThemeManager.getTextPrimary(), 1f);
                            break;
                        case TEXT:
                            drawTextSetting(s, sx, (int) cy, sw, mouseX, mouseY);
                            break;
                        case HUD_POSITION:
                            drawHudPositionEditor(s, sx, (int) cy, sw, mouseX, mouseY);
                            break;
                        case TAB_ANIMATION:
                            drawTabAnimationButton(s, sx, (int) cy, sw, mouseX, mouseY);
                            break;
                    }
                }
                cy += rowH;
            }

            maxSettingsScrollY = Math.min(0, SETTINGS_CONTENT_HEIGHT - (cy - cyStart));
            settingsScrollY = MathHelper.clamp(settingsScrollY, maxSettingsScrollY, 0f);
            RenderUtils.stopScissor();

            if (maxSettingsScrollY < 0) {
                float viewH = SETTINGS_CONTENT_HEIGHT;
                float totalH = viewH - maxSettingsScrollY;
                float thumbH = Math.max(10, (viewH / totalH) * viewH);
                float trackTop = windowY + SETTINGS_CONTENT_TOP;
                float thumbY = trackTop + (-settingsScrollY / -maxSettingsScrollY) * (viewH - thumbH);
                RenderUtils.drawRoundedRect(windowX + WINDOW_WIDTH - 4, thumbY, windowX + WINDOW_WIDTH - 2, thumbY + thumbH, 1, ThemeManager.getScrollbar());
            }

            if (draggingSlider && activeSlider != null)
                updateSliderValue(activeSlider, mouseX, sx + 8, sw - 16);
        }

        if (pendingDropdownToDraw != null) {
            drawDropdownMenu(pendingDropdownToDraw, mouseX, mouseY);
        }

        if (showThemeSelector && selectedModule != null) {
            drawThemeSelectorDropdown(mouseX, mouseY);
        }

        // Draw color picker on top of everything
        if (colorPicker.isVisible()) {
            colorPicker.draw(mouseX, mouseY);
        }
    }

    // ============================================================
    //  НОВЫЙ МЕТОД ОТРИСОВКИ СПИСКА СКРИПТОВ (БЕЗ §, С ТУМБЛЕРОМ И ПЕРЕНОСОМ)
    // ============================================================
    private void drawScriptList(Setting s, int sx, int cy, int sw, int mouseX, int mouseY) {
        String text = (String) s.getValue();
        if (text == null || text.isEmpty() || text.equals("Нет загруженных скриптов")) {
            drawSettingBackground(sx, cy, sw, 30, mouseX, mouseY);
            mc.fontRenderer.drawString("No scripts loaded", sx + 8, cy + 8, ThemeManager.getTextDim());
            return;
        }

        String[] lines = text.split("\n");
        int currentY = cy;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            // Убираем все цветовые коды для парсинга
            String cleanLine = stripColors(line);
            boolean isNewScript = cleanLine.startsWith("[✓]") || cleanLine.startsWith("[✗]");

            if (isNewScript) {
                boolean enabled = cleanLine.startsWith("[✓]");
                String scriptName = cleanLine.replace("[✓] ", "").replace("[✗] ", "").trim();
                if (scriptName.contains("#")) {
                    scriptName = scriptName.substring(0, scriptName.lastIndexOf("#")).trim();
                }
                String version = "";

                // Извлекаем версию (если есть)
                if (scriptName.contains(" v")) {
                    int vIdx = scriptName.lastIndexOf(" v");
                    version = scriptName.substring(vIdx + 2);
                    scriptName = scriptName.substring(0, vIdx);
                }

                // Считываем автора
                String author = "Unknown";
                if (i + 1 < lines.length) {
                    String authLine = stripColors(lines[i + 1]);
                    if (authLine.contains("Автор:") || authLine.contains("Author:")) {
                        author = authLine.replace("Автор:", "").replace("Author:", "").trim();
                        i++; // пропускаем строку автора в цикле
                    }
                }

                // Считываем описание
                String desc = "";
                if (i + 1 < lines.length) {
                    String descLine = stripColors(lines[i + 1]);
                    if (descLine.contains("Описание:") || descLine.contains("Description:")) {
                        desc = descLine.replace("Описание:", "").replace("Description:", "").trim();
                        i++; // пропускаем строку описания в цикле
                    }
                }

                // Делаем перенос длинного описания
                int maxTextWidth = sw - 80;
                List<String> wrappedDesc = mc.fontRenderer.listFormattedStringToWidth(desc, maxTextWidth);
                int cardHeight = 32 + (wrappedDesc.size() * 10);

                // Рисуем фон карточки (серый при наведении)
                boolean hover = isHovered(mouseX, mouseY, sx, currentY, sw, cardHeight);
                RenderUtils.drawRoundedRect(sx, currentY, sx + sw, currentY + cardHeight, 5, hover ? ThemeManager.getElementBgHover() : ThemeManager.getElementBg());

                // Рисуем ТУМБЛЕР (настоящий переключатель для каждого скрипта отдельно)
                int toggleX = sx + sw - 34;
                int toggleY = currentY + 4;
                drawSwitch(toggleX, toggleY, enabled);

                // Рисуем Название и Версию
                mc.fontRenderer.drawString(scriptName, sx + 8, currentY + 4, ThemeManager.getTextPrimary());
                if (!version.isEmpty()) {
                    mc.fontRenderer.drawString("v" + version, sx + 8 + mc.fontRenderer.getStringWidth(scriptName) + 4, currentY + 4, ThemeManager.getTextDim());
                }

                // Рисуем Автора
                mc.fontRenderer.drawString("Author: " + author, sx + 8, currentY + 16, ThemeManager.getTextSecondary());

                // Рисуем перенесенное Описание
                int descY = currentY + 26;
                for (String descLine : wrappedDesc) {
                    mc.fontRenderer.drawString(descLine, sx + 8, descY, ThemeManager.getTextDim());
                    descY += 10;
                }

                currentY += cardHeight + 4;
            }
        }
    }

    private void drawScriptButton(Setting s, int sx, int cy, int sw, int mouseX, int mouseY) {
        int rowH = 28;
        drawSettingBackground(sx, cy, sw, rowH, mouseX, mouseY);
        mc.fontRenderer.drawString(s.getName(), sx + 8, cy + 8, ThemeManager.getTextSecondary());

        String label = (String) s.getValue();
        int btnW = Math.max(80, mc.fontRenderer.getStringWidth(label) + 24);
        int btnX = sx + sw - btnW - 8;
        int btnY = cy + 4;
        int btnH = 20;

        boolean hover = isHovered(mouseX, mouseY, btnX, btnY, btnW, btnH);
        int bg = hover ? ThemeManager.getAccentDark() : ThemeManager.getElementBg();
        RenderUtils.drawRoundedRect(btnX, btnY, btnX + btnW, btnY + btnH, 4, bg);
        RenderUtils.drawRoundedRectOutline(btnX, btnY, btnX + btnW, btnY + btnH, 4, ThemeManager.getAccent(), 1f);

        int textColor = hover ? 0xFFFFFF : ThemeManager.getTextPrimary();
        mc.fontRenderer.drawString(label, btnX + (btnW - mc.fontRenderer.getStringWidth(label)) / 2, btnY + 5, textColor);
    }

    private void drawTabAnimationButton(Setting s, int sx, int cy, int sw, int mouseX, int mouseY) {
        drawSettingBackground(sx, cy, sw, 24, mouseX, mouseY);
        mc.fontRenderer.drawString(s.getName(), sx + 8, cy + 8, ThemeManager.getTextSecondary());

        String currentAnim = (String) s.getValue();
        int btnW = 90, btnH = 16;
        int btnX = sx + sw - btnW - 6;
        int btnY = cy + 4;
        boolean btnHov = isHovered(mouseX, mouseY, btnX, btnY, btnW, btnH);
        int btnBg = btnHov ? ThemeManager.getElementBgHover() : ThemeManager.getElementBg();
        RenderUtils.drawRoundedRect(btnX, btnY, btnX + btnW, btnY + btnH, 4, btnBg);
        RenderUtils.drawRoundedRectOutline(btnX, btnY, btnX + btnW, btnY + btnH, 4, ThemeManager.getAccent(), 1f);
        mc.fontRenderer.drawString(currentAnim.equals("Off") ? "Select" : currentAnim, btnX + 5, btnY + 4, ThemeManager.getTextPrimary());
    }

    // ───────────────────────────────────────────────────────────────────
    //  HUD Position Editor
    // ───────────────────────────────────────────────────────────────────

    private void drawHudPositionEditor(Setting s, int sx, int cy, int sw, int mouseX, int mouseY) {
        int rowH = 28;
        boolean editorOpen = (activeHudPositionSetting == s && editingHudPosition);

        drawSettingBackground(sx, cy, sw, rowH, mouseX, mouseY);
        mc.fontRenderer.drawString(s.getName(), sx + 8, cy + 8, ThemeManager.getTextSecondary());

        int btnX = sx + sw - 100;
        int btnW = 92;
        int btnH = 18;
        int btnY = cy + 5;

        boolean btnHover = isHovered(mouseX, mouseY, btnX, btnY, btnW, btnH);
        int btnBg = btnHover ? ThemeManager.getElementBgHover() : ThemeManager.getElementBg();
        RenderUtils.drawRoundedRect(btnX, btnY, btnX + btnW, btnY + btnH, 4, btnBg);
        RenderUtils.drawRoundedRectOutline(btnX, btnY, btnX + btnW, btnY + btnH, 4, editorOpen ? ThemeManager.getAccent() : ThemeManager.getBorder(), 1f);
        mc.fontRenderer.drawString(editorOpen ? "Close" : "Edit", btnX + 8, btnY + 5, ThemeManager.getTextPrimary());
    }

    private void drawHudPositionEditorOverlay() {
        ScaledResolution sr = new ScaledResolution(mc);
        int screenW = sr.getScaledWidth();
        int screenH = sr.getScaledHeight();

        float[] pos = (float[]) activeHudPositionSetting.getValue();
        float hudXPercent = pos[0];
        float hudYPercent = pos[1];

        int hudW = HUD.HUD_WIDTH;
        int hudH = HUD.HUD_HEIGHT;
        int hudX = (int)(hudXPercent * screenW);
        int hudY = (int)(hudYPercent * screenH);

        hudX = Math.max(HUD.HUD_EDGE_MARGIN, Math.min(screenW - hudW - HUD.HUD_EDGE_MARGIN, hudX));
        hudY = Math.max(HUD.HUD_EDGE_MARGIN, Math.min(screenH - hudH - HUD.HUD_EDGE_MARGIN, hudY));

        drawRect(0, 0, screenW, screenH, 0x80000000);

        int gridSize = 40;
        for (int gx = 0; gx < screenW; gx += gridSize) {
            drawRect(gx, 0, gx + 1, screenH, 0x20FFFFFF);
        }
        for (int gy = 0; gy < screenH; gy += gridSize) {
            drawRect(0, gy, screenW, gy + 1, 0x20FFFFFF);
        }

        drawRect(screenW / 2 - 1, 0, screenW / 2 + 1, screenH, 0x30FFFFFF);
        drawRect(0, screenH / 2 - 1, screenW, screenH / 2 + 1, 0x30FFFFFF);

        Gui.drawRect(hudX, hudY, hudX + hudW, hudY + hudH, 0x60FFFFFF);

        Gui.drawRect(hudX - 1, hudY - 1, hudX + hudW + 1, hudY + 1, 0xFFFFFFFF);
        Gui.drawRect(hudX - 1, hudY + hudH - 1, hudX + hudW + 1, hudY + hudH + 1, 0xFFFFFFFF);
        Gui.drawRect(hudX - 1, hudY - 1, hudX + 1, hudY + hudH + 1, 0xFFFFFFFF);
        Gui.drawRect(hudX + hudW - 1, hudY - 1, hudX + hudW + 1, hudY + hudH + 1, 0xFFFFFFFF);

        String dragText = "Drag me";
        int textW = mc.fontRenderer.getStringWidth(dragText);
        mc.fontRenderer.drawStringWithShadow(dragText, hudX + (float) (hudW - textW) / 2, hudY + (float) hudH / 2 - 4, 0xFFFFFFFF);

        String posText = String.format("X: %.1f%% | Y: %.1f%%", hudXPercent * 100, hudYPercent * 100);
        int posW = mc.fontRenderer.getStringWidth(posText);
        mc.fontRenderer.drawStringWithShadow(posText, hudX + (float) (hudW - posW) / 2, hudY + (float) hudH / 2 + 8, 0xAAAAAAAA);

        String helpText = "Drag the HUD | ESC to save and close";
        int helpW = mc.fontRenderer.getStringWidth(helpText);
        mc.fontRenderer.drawStringWithShadow(helpText, (float) screenW / 2 - (float) helpW / 2, screenH - 20, 0xCCCCCCCC);

        String resetText = "[Right-click to reset position]";
        int resetW = mc.fontRenderer.getStringWidth(resetText);
        mc.fontRenderer.drawStringWithShadow(resetText, (float) screenW / 2 - (float) resetW / 2, screenH - 36, 0xAAAAAAAA);
    }

    // ───────────────────────────────────────────────────────────────────
    //  Theme Settings
    // ───────────────────────────────────────────────────────────────────

    private void drawThemeSettings(int mouseX, int mouseY) {
        int sx = windowX + SIDEBAR_WIDTH + 15;
        int sw = WINDOW_WIDTH - SIDEBAR_WIDTH - 34;

        int btnW = 110, btnH = 20;
        int btnX = sx + sw - btnW;
        int btnY = windowY + 9;

        boolean btnHover = isHovered(mouseX, mouseY, btnX, btnY, btnW, btnH);
        int bgColor = btnHover ? ThemeManager.getElementBgHover() : ThemeManager.getElementBg();
        RenderUtils.drawRoundedRect(btnX, btnY, btnX + btnW, btnY + btnH, 4, bgColor);
        RenderUtils.drawRoundedRectOutline(btnX, btnY, btnX + btnW, btnY + btnH, 4, ThemeManager.getBorder(), 1f);

        mc.getTextureManager().bindTexture(PALETTE_ICON);
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO
        );

        int tc = ThemeManager.getTextPrimary();
        float a = ((tc >> 24) & 0xFF) / 255f;
        float r = ((tc >> 16) & 0xFF) / 255f;
        float g = ((tc >> 8) & 0xFF) / 255f;
        float b = (tc & 0xFF) / 255f;
        if (a == 0) a = 1.0f;
        net.minecraft.client.renderer.GlStateManager.color(r, g, b, a);

        int iconSize = 12;
        GuiScreen.drawModalRectWithCustomSizedTexture(
                btnX + 6,
                btnY + (btnH - iconSize) / 2,
                0, 0, iconSize, iconSize, iconSize, iconSize
        );
        net.minecraft.client.renderer.GlStateManager.color(1f, 1f, 1f, 1f);

        mc.fontRenderer.drawString(
                ThemeManager.getCurrentThemeName(),
                btnX + 22,
                btnY + 6,
                ThemeManager.getTextPrimary()
        );
    }

    // ───────────────────────────────────────────────────────────────────
    //  Online Mode Button
    // ───────────────────────────────────────────────────────────────────

    private void drawOnlineModeButton(int mouseX, int mouseY) {
        int sx = windowX + SIDEBAR_WIDTH + 15;
        int sw = WINDOW_WIDTH - SIDEBAR_WIDTH - 34;

        int themeBtnW = 110;
        int onlineBtnW = 90, onlineBtnH = 20;
        int onlineBtnX = sx + sw - themeBtnW - onlineBtnW - 8;
        int onlineBtnY = windowY + 9;

        OnlineMode onlineMode = OnlineMode.getInstance();
        boolean connected = onlineMode.isConnected();

        boolean btnHover = isHovered(mouseX, mouseY, onlineBtnX, onlineBtnY, onlineBtnW, onlineBtnH);
        int bgColor = btnHover ? ThemeManager.getElementBgHover() : ThemeManager.getElementBg();

        RenderUtils.drawRoundedRect(onlineBtnX, onlineBtnY, onlineBtnX + onlineBtnW, onlineBtnY + onlineBtnH, 4, bgColor);

        int borderColor = connected ? ThemeManager.getAccent() : ThemeManager.getBorder();
        RenderUtils.drawRoundedRectOutline(onlineBtnX, onlineBtnY, onlineBtnX + onlineBtnW, onlineBtnY + onlineBtnH, 4, borderColor, 1f);

        String text = connected ? "Online" : "Offline";
        int textColor = connected ? ThemeManager.getSwitchOn() : ThemeManager.getTextDim();

        int textWidth = mc.fontRenderer.getStringWidth(text);
        mc.fontRenderer.drawString(text, onlineBtnX + (onlineBtnW - textWidth) / 2, onlineBtnY + 6, textColor);
        if (connected) {
            RenderUtils.drawCircle(onlineBtnX + onlineBtnW - 12, onlineBtnY + 10, 3, ThemeManager.getSwitchOn());
        }
    }

    private void drawThemeSelectorDropdown(int mouseX, int mouseY) {
        int sx = windowX + SIDEBAR_WIDTH + 15;
        int sw = WINDOW_WIDTH - SIDEBAR_WIDTH - 34;
        int btnW = 110, btnH = 20;
        int btnX = sx + sw - btnW;
        int btnY = windowY + 9;

        String[] themeNames = ThemeManager.getThemeNames();
        int themeCount = ThemeManager.getThemeCount();

        int dropW = 160;
        int dropH = Math.min(220, themeCount * 24 + 4);
        int dropX = btnX + btnW - dropW;
        int dropY = btnY + btnH + 2;

        if (dropX + dropW > windowX + WINDOW_WIDTH - 5) {
            dropX = btnX - dropW + btnW;
        }
        if (dropY + dropH > windowY + WINDOW_HEIGHT - 5) {
            dropY = btnY - dropH - 2;
        }

        themeDropX = dropX;
        themeDropY = dropY;
        themeDropW = dropW;
        themeDropH = dropH;

        GL11.glPushMatrix();
        GL11.glTranslatef(0, 0, 200);

        RenderUtils.drawRoundedRect(dropX, dropY, dropX + dropW, dropY + dropH, 4, ThemeManager.getDropdownBg());
        RenderUtils.drawRoundedRectOutline(dropX, dropY, dropX + dropW, dropY + dropH, 4, ThemeManager.getAccent(), 1f);

        RenderUtils.startScissor(dropX, dropY + 2, dropW, dropH - 4);
        float curY = dropY + 2 + themeSelectorScrollY;

        for (int i = 0; i < themeCount; i++) {
            boolean isSelected = i == ThemeManager.getCurrentThemeIndex();
            boolean hover = isHovered(mouseX, mouseY, dropX + 2, (int) curY, dropW - 4, 22);

            int bg = isSelected ? ThemeManager.getAccentDark() : (hover ? ThemeManager.getDropdownHover() : 0);
            if (bg != 0) {
                RenderUtils.drawRoundedRect(dropX + 2, curY, dropX + dropW - 2, curY + 22, 3, bg);
            }

            int previewColor = ThemeManager.getColorByTheme(i, 9); // ACCENT
            RenderUtils.drawRoundedRect(dropX + 6, curY + 5, dropX + 14, curY + 17, 3, previewColor);

            int textColor = isSelected ? ThemeManager.getAccent() : ThemeManager.getTextSecondary();
            mc.fontRenderer.drawString(themeNames[i], dropX + 20, (int) curY + 6, textColor);

            curY += 24;
        }

        float totalContentHeight = themeCount * 24;
        maxThemeSelectorScrollY = Math.min(0, (dropH - 4) - totalContentHeight);
        themeSelectorScrollY = MathHelper.clamp(themeSelectorScrollY, maxThemeSelectorScrollY, 0f);

        RenderUtils.stopScissor();

        if (maxThemeSelectorScrollY < 0) {
            float viewH = dropH - 4;
            float thumbH = Math.max(10, (viewH / totalContentHeight) * viewH);
            float thumbY = dropY + 2 + (-themeSelectorScrollY / -maxThemeSelectorScrollY) * (viewH - thumbH);
            RenderUtils.drawRoundedRect(dropX + dropW - 6, thumbY, dropX + dropW - 4, thumbY + thumbH, 1, ThemeManager.getScrollbar());
        }

        GL11.glPopMatrix();
    }

    // ───────────────────────────────────────────────────────────────────
    //  Dropdown Menu для MODE
    // ───────────────────────────────────────────────────────────────────

    private void drawDropdownMenu(Setting s, int mouseX, int mouseY) {
        List<String> modes = s.getModes();
        if (modes == null || modes.isEmpty()) {
            return;
        }

        int itemH = 14;
        int maxVisibleItems = 15;
        int totalItems = modes.size();

        int visibleItems = Math.min(totalItems, maxVisibleItems);
        int visibleHeight = visibleItems * itemH + 2;
        int totalHeight = totalItems * itemH + 2;

        float maxScroll = Math.max(0, totalHeight - visibleHeight);
        float currentScroll = MathHelper.clamp(dropdownScrollY, 0, maxScroll);

        GL11.glPushMatrix();
        GL11.glTranslatef(0, 0, 150);
        RenderUtils.drawRoundedRect(dropdownDrawX, dropdownDrawY,
                dropdownDrawX + dropdownDrawW, dropdownDrawY + visibleHeight, 4, ThemeManager.getDropdownBg());
        RenderUtils.drawRoundedRectOutline(dropdownDrawX, dropdownDrawY,
                dropdownDrawX + dropdownDrawW, dropdownDrawY + visibleHeight, 4, ThemeManager.getAccent(), 1f);

        RenderUtils.startScissor(dropdownDrawX + 1, dropdownDrawY + 1,
                dropdownDrawW - 2, visibleHeight - 2);

        int startIndex = (int)(currentScroll / itemH);
        int endIndex = Math.min(startIndex + visibleItems + 1, totalItems);
        float curY = dropdownDrawY + 1 - (currentScroll % itemH);

        for (int i = startIndex; i < endIndex; i++) {
            String mode = modes.get(i);
            boolean hov = isHovered(mouseX, mouseY, dropdownDrawX, (int)curY, dropdownDrawW, itemH);

            if (hov) {
                RenderUtils.drawRoundedRect(dropdownDrawX + 2, curY,
                        dropdownDrawX + dropdownDrawW - 2, curY + itemH, 3, ThemeManager.getDropdownHover());
            }

            int color = mode.equals(s.getMode()) ? ThemeManager.getAccent() : ThemeManager.getTextSecondary();
            mc.fontRenderer.drawString(mode, dropdownDrawX + 6, (int)curY + 3, color);
            curY += itemH;
        }

        RenderUtils.stopScissor();

        if (totalItems > maxVisibleItems) {
            float scrollBarHeight = visibleHeight - 4;
            float thumbHeight = Math.max(15, (float)visibleItems / totalItems * scrollBarHeight);
            float thumbY = dropdownDrawY + 2 + (currentScroll / maxScroll) * (scrollBarHeight - thumbHeight);

            RenderUtils.drawRoundedRect(dropdownDrawX + dropdownDrawW - 5, dropdownDrawY + 2,
                    dropdownDrawX + dropdownDrawW - 2, dropdownDrawY + visibleHeight - 2, 1.5f, 0x20FFFFFF);

            RenderUtils.drawRoundedRect(dropdownDrawX + dropdownDrawW - 5, thumbY,
                    dropdownDrawX + dropdownDrawW - 2, thumbY + thumbHeight, 1.5f,
                    isHovered(mouseX, mouseY, dropdownDrawX + dropdownDrawW - 6, dropdownDrawY,
                            6, visibleHeight) ? ThemeManager.getAccent() : ThemeManager.getScrollbar());
        }

        GL11.glPopMatrix();
    }

    private void drawTextSetting(Setting s, int sx, int cy, int sw, int mouseX, int mouseY) {
        int resetBtnW = 16, resetBtnH = 16;
        int resetBtnX = sx + sw - resetBtnW - 4;
        int resetBtnY = cy + 4;

        int[] bounds = getTextFieldBounds(s, sx, cy, sw);
        int tbX = bounds[0], tbY = bounds[1], tbW = bounds[2], tbH = bounds[3];

        GuiTextField field = getOrCreateField(s, tbX, tbY, tbW, tbH);
        if (!field.isFocused()) {
            String cur = (String) s.getValue();
            if (!field.getText().equals(cur)) field.setText(cur);
        } else {
            syncField(s, field);
        }

        boolean active = field.isFocused();
        boolean hovered = isHovered(mouseX, mouseY, sx, cy, sw, 24);

        RenderUtils.drawRoundedRect(sx, cy, sx + sw, cy + 24, 5, active || hovered ? ThemeManager.getElementBgHover() : ThemeManager.getElementBg());
        mc.fontRenderer.drawString(s.getName(), sx + 8, cy + 8, ThemeManager.getTextSecondary());

        boolean resetHov = isHovered(mouseX, mouseY, resetBtnX, resetBtnY, resetBtnW, resetBtnH);
        boolean isDefault = s.getValue().equals(s.getDefaultValue());
        int resetColor = isDefault ? ThemeManager.getSwitchOff() : (resetHov ? 0xFFE05050 : 0xFF773333);

        RenderUtils.drawRoundedRect(resetBtnX, resetBtnY, resetBtnX + resetBtnW, resetBtnY + resetBtnH, 4, resetColor);
        String resetSym = "↺";
        float symScale = 1.2f;
        float symX = resetBtnX + (resetBtnW - mc.fontRenderer.getStringWidth(resetSym) * symScale) / 2f;
        float symY = resetBtnY + (resetBtnH - mc.fontRenderer.FONT_HEIGHT * symScale) / 2f + 1;

        GL11.glPushMatrix();
        GL11.glTranslatef(symX, symY, 0);
        GL11.glScalef(symScale, symScale, 1f);
        mc.fontRenderer.drawString(resetSym, 0, 0, isDefault ? ThemeManager.getTextDim() : ThemeManager.getTextPrimary());
        GL11.glPopMatrix();

        boolean fieldHovered = isHovered(mouseX, mouseY, tbX, tbY, tbW, tbH);
        RenderUtils.drawRoundedRect(tbX, tbY, tbX + tbW, tbY + tbH, 3, active ? ThemeManager.getSearchBgFocused() : (fieldHovered ? ThemeManager.getElementBgHover() : ThemeManager.getElementBg()));
        RenderUtils.drawRoundedRectOutline(tbX, tbY, tbX + tbW, tbY + tbH, 3, active ? ThemeManager.getAccent() : ThemeManager.getBorder(), active ? 1.2f : 1f);

        if (!active && field.getText().isEmpty()) {
            mc.fontRenderer.drawString("Click to input...", tbX + 5, tbY + 5, ThemeManager.getTextDim());
            return;
        }

        RenderUtils.startScissor(tbX + 1, tbY + 1, tbW - 2, tbH - 2);
        field.drawTextBox();
        RenderUtils.stopScissor();
    }
    // ───────────────────────────────────────────────────────────────────
    //  Управление Вводом (Мышь & Клавиатура)
    // ───────────────────────────────────────────────────────────────────

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dw = Mouse.getDWheel();
        if (dw == 0) return;

        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;

        if (colorPicker.isVisible()) {
            return;
        }

        if (openDropdownSetting != null) {
            List<String> modes = openDropdownSetting.getModes();
            if (modes != null && !modes.isEmpty()) {
                int itemH = 14;
                int maxVisibleItems = 15;
                int visibleHeight = Math.min(modes.size(), maxVisibleItems) * itemH + 2;
                int totalHeight = modes.size() * itemH + 2;
                float maxScroll = Math.max(0, totalHeight - visibleHeight);

                if (maxScroll > 0 && isHovered(mouseX, mouseY, dropdownDrawX, dropdownDrawY,
                        dropdownDrawW, visibleHeight)) {
                    int delta = dw > 0 ? -20 : 20;
                    dropdownScrollY = MathHelper.clamp(dropdownScrollY + delta, 0, maxScroll);
                    return;
                }
            }
        }

        if (showThemeSelector) {
            if (isHovered(mouseX, mouseY, themeDropX, themeDropY, themeDropW, themeDropH)) {
                int delta = dw > 0 ? 36 : -36;
                themeSelectorScrollY = MathHelper.clamp(themeSelectorScrollY + delta, maxThemeSelectorScrollY, 0);
                return;
            }
        }

        int delta = dw > 0 ? 36 : -36;
        if (isHovered(mouseX, mouseY, windowX, windowY, SIDEBAR_WIDTH, WINDOW_HEIGHT))
            moduleScrollY = MathHelper.clamp(moduleScrollY + delta, maxModuleScrollY, 0);
        else if (isHovered(mouseX, mouseY, windowX + SIDEBAR_WIDTH, windowY, WINDOW_WIDTH - SIDEBAR_WIDTH, WINDOW_HEIGHT))
            settingsScrollY = MathHelper.clamp(settingsScrollY + delta, maxSettingsScrollY, 0);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (colorPicker.isVisible()) {
            colorPicker.keyTyped(typedChar, keyCode);
            return;
        }

        if (editingHudPosition && activeHudPositionSetting != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                editingHudPosition = false;
                activeHudPositionSetting = null;
                return;
            }
            return;
        }

        if (searchField.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
                searchField.setFocused(false);
            } else {
                searchField.textboxKeyTyped(typedChar, keyCode);
                moduleScrollY = 0;
            }
            return;
        }

        if (isAnyFieldFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
                Setting fs = focusedSetting();
                if (fs != null) {
                    GuiTextField f = textFields.get(fs);
                    if (f != null) syncField(fs, f);
                }
                blurAllFields();
                return;
            }
            GuiTextField f = focusedField();
            if (f != null) {
                f.textboxKeyTyped(typedChar, keyCode);
                Setting fs = focusedSetting();
                if (fs != null) syncField(fs, f);
            }
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (openDropdownSetting != null) {
                openDropdownSetting = null;
                dropdownScrollY = 0;
                return;
            }
            if (showThemeSelector) {
                showThemeSelector = false;
                return;
            }
            super.keyTyped(typedChar, keyCode);
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        super.mouseClicked(mouseX, mouseY, button);

        if (colorPicker.isVisible()) {
            colorPicker.mouseClicked(mouseX, mouseY, button);
            return;
        }

        if (editingHudPosition && activeHudPositionSetting != null) {
            if (button == 0) {
                float[] pos = (float[]) activeHudPositionSetting.getValue();
                ScaledResolution sr = new ScaledResolution(mc);
                int screenW = sr.getScaledWidth();
                int screenH = sr.getScaledHeight();

                int hudW = HUD.HUD_WIDTH;
                int hudH = HUD.HUD_HEIGHT;
                int hudX = (int)(pos[0] * screenW);
                int hudY = (int)(pos[1] * screenH);

                hudX = Math.max(HUD.HUD_EDGE_MARGIN, Math.min(screenW - hudW - HUD.HUD_EDGE_MARGIN, hudX));
                hudY = Math.max(HUD.HUD_EDGE_MARGIN, Math.min(screenH - hudH - HUD.HUD_EDGE_MARGIN, hudY));

                if (isHovered(mouseX, mouseY, hudX, hudY, hudW, hudH)) {
                    draggingHudPreview = true;
                    hudPreviewDragOffsetX = mouseX - hudX;
                    hudPreviewDragOffsetY = mouseY - hudY;
                    return;
                }
            }
            if (button == 1) {
                activeHudPositionSetting.setValue(new float[]{0.78f, 0.05f});
                return;
            }
            return;
        }

        if (button == 0) {
            int sx = windowX + SIDEBAR_WIDTH + 15;
            int sw = WINDOW_WIDTH - SIDEBAR_WIDTH - 34;

            int themeBtnW = 110, themeBtnH = 20;
            int themeBtnX = sx + sw - themeBtnW;
            int themeBtnY = windowY + 9;
            boolean isOnThemeBtn = isHovered(mouseX, mouseY, themeBtnX, themeBtnY, themeBtnW, themeBtnH);

            int onlineBtnW = 90, onlineBtnH = 20;
            int onlineBtnX = sx + sw - themeBtnW - onlineBtnW - 8;
            int onlineBtnY = windowY + 9;
            boolean isOnOnlineBtn = isHovered(mouseX, mouseY, onlineBtnX, onlineBtnY, onlineBtnW, onlineBtnH);

            boolean isOnSearch = isHovered(mouseX, mouseY, windowX + 8, windowY + 36, SIDEBAR_WIDTH - 16, 16);

            boolean isOnTitle = isHovered(mouseX, mouseY, windowX + 10, windowY, WINDOW_WIDTH - 20, 30);

            if (isOnOnlineBtn) {
                OnlineModeGUI.getInstance().openFrom(this);
                return;
            }

            if (isOnTitle && !isOnThemeBtn && !isOnSearch) {
                dragging = true;
                dragX = mouseX - windowX;
                dragY = mouseY - windowY;
                return;
            }
        }

        if (button == 0) {
            if (showThemeSelector && maxThemeSelectorScrollY < 0) {
                float viewH = themeDropH - 4;
                float totalH = viewH - maxThemeSelectorScrollY;
                float thumbH = Math.max(10, (viewH / totalH) * viewH);
                float thumbY = themeDropY + 2 + (-themeSelectorScrollY / -maxThemeSelectorScrollY) * (viewH - thumbH);
                float scrollX = themeDropX + themeDropW - 10;

                if (mouseX >= scrollX && mouseX <= scrollX + 10 && mouseY >= themeDropY + 2 && mouseY <= themeDropY + 2 + viewH) {
                    draggingThemeScroll = true;
                    scrollDragOffset = (mouseY >= thumbY && mouseY <= thumbY + thumbH) ? (mouseY - thumbY) : (thumbH / 2f);
                    return;
                }
            }

            if (maxSettingsScrollY < 0 && selectedModule != null) {
                float viewH = SETTINGS_CONTENT_HEIGHT;
                float totalH = viewH - maxSettingsScrollY;
                float thumbH = Math.max(10, (viewH / totalH) * viewH);
                float trackTop = windowY + SETTINGS_CONTENT_TOP;
                float thumbY = trackTop + (-settingsScrollY / -maxSettingsScrollY) * (viewH - thumbH);
                float scrollX = windowX + WINDOW_WIDTH - 8;
                float scrollYStart = trackTop;
                float scrollYEnd = trackTop + viewH;

                if (mouseX >= scrollX && mouseX <= scrollX + 8 && mouseY >= scrollYStart && mouseY <= scrollYEnd) {
                    draggingSettingsScroll = true;
                    scrollDragOffset = (mouseY >= thumbY && mouseY <= thumbY + thumbH) ? (mouseY - thumbY) : (thumbH / 2f);
                    return;
                }
            }

            if (maxModuleScrollY < 0) {
                float viewH = WINDOW_HEIGHT - SIDEBAR_OFFSET_Y - 14;
                float totalH = viewH - maxModuleScrollY;
                float thumbH = Math.max(10, (viewH / totalH) * viewH);
                float thumbY = windowY + SIDEBAR_OFFSET_Y + 4 + (-moduleScrollY / -maxModuleScrollY) * (viewH - thumbH);
                float scrollX = windowX + SIDEBAR_WIDTH - 8;

                if (mouseX >= scrollX && mouseX <= scrollX + 8 && mouseY >= windowY + SIDEBAR_OFFSET_Y + 4 && mouseY <= windowY + SIDEBAR_OFFSET_Y + 4 + viewH) {
                    draggingModuleScroll = true;
                    scrollDragOffset = (mouseY >= thumbY && mouseY <= thumbY + thumbH) ? (mouseY - thumbY) : (thumbH / 2f);
                    return;
                }
            }
        }

        int sx = windowX + SIDEBAR_WIDTH + 15;
        int sw = WINDOW_WIDTH - SIDEBAR_WIDTH - 34;

        if (selectedModule != null) {
            int themeBtnW = 110, themeBtnH = 20;
            int themeBtnX = sx + sw - themeBtnW;
            int themeBtnY = windowY + 9;

            if (isHovered(mouseX, mouseY, themeBtnX, themeBtnY, themeBtnW, themeBtnH)) {
                showThemeSelector = !showThemeSelector;
                themeSelectorScrollY = 0;
                return;
            }

            int onlineBtnW = 90, onlineBtnH = 20;
            int onlineBtnX = sx + sw - themeBtnW - onlineBtnW - 8;
            int onlineBtnY = windowY + 9;

            if (isHovered(mouseX, mouseY, onlineBtnX, onlineBtnY, onlineBtnW, onlineBtnH)) {
                OnlineModeGUI.getInstance().openFrom(this);
                return;
            }
        }

        if (showThemeSelector) {
            if (isHovered(mouseX, mouseY, themeDropX, themeDropY, themeDropW, themeDropH)) {
                String[] themeNames = ThemeManager.getThemeNames();
                int themeCount = ThemeManager.getThemeCount();

                float curY = themeDropY + 2 + themeSelectorScrollY;
                for (int i = 0; i < themeCount; i++) {
                    if (isHovered(mouseX, mouseY, themeDropX + 2, (int) curY, themeDropW - 4, 22)) {
                        ThemeManager.setCurrentTheme(i);
                        showThemeSelector = false;
                        return;
                    }
                    curY += 24;
                }
                return;
            }
            showThemeSelector = false;
        }

        if (openDropdownSetting != null) {
            List<String> modes = openDropdownSetting.getModes();
            if (modes == null || modes.isEmpty()) {
                openDropdownSetting = null;
                dropdownScrollY = 0;
                return;
            }

            int itemH = 14;
            int maxVisibleItems = 15;
            int visibleHeight = Math.min(modes.size(), maxVisibleItems) * itemH + 2;
            int totalHeight = modes.size() * itemH + 2;
            float maxScroll = Math.max(0, totalHeight - visibleHeight);

            if (maxScroll > 0) {
                int scrollBarX = dropdownDrawX + dropdownDrawW - 6;
                int scrollBarW = 6;
                int scrollBarY = dropdownDrawY;
                int scrollBarH = visibleHeight;

                if (isHovered(mouseX, mouseY, scrollBarX, scrollBarY, scrollBarW, scrollBarH)) {
                    draggingDropdownScroll = true;
                    dropdownScrollStartY = mouseY;
                    dropdownScrollStartValue = dropdownScrollY;
                    return;
                }
            }

            if (isHovered(mouseX, mouseY, dropdownDrawX, dropdownDrawY, dropdownDrawW, visibleHeight)) {
                float curY = dropdownDrawY + 1 - (dropdownScrollY % itemH);
                int startIndex = (int)(dropdownScrollY / itemH);
                int endIndex = Math.min(startIndex + maxVisibleItems + 1, modes.size());

                for (int i = startIndex; i < endIndex; i++) {
                    if (isHovered(mouseX, mouseY, dropdownDrawX + 1, (int)curY, dropdownDrawW - 2, itemH)) {
                        openDropdownSetting.setValue(modes.get(i));
                        openDropdownSetting = null;
                        dropdownScrollY = 0;
                        return;
                    }
                    curY += itemH;
                }
                return;
            }

            openDropdownSetting = null;
            dropdownScrollY = 0;
            return;
        }

        boolean searchHovered = isHovered(mouseX, mouseY, windowX + 8, windowY + 36, SIDEBAR_WIDTH - 16, 16);
        if (searchHovered) {
            searchField.setFocused(true);
            searchField.mouseClicked(mouseX, mouseY, button);
            return;
        } else {
            searchField.setFocused(false);
        }

        if (isHovered(mouseX, mouseY, windowX, windowY + SIDEBAR_OFFSET_Y, SIDEBAR_WIDTH, WINDOW_HEIGHT - SIDEBAR_OFFSET_Y - 8)) {
            float curModY = windowY + SIDEBAR_OFFSET_Y + 4 + moduleScrollY;
            for (Module module : sortedModules) {
                if (!searchField.getText().isEmpty() && !module.getName().toLowerCase().contains(searchField.getText().toLowerCase())) {
                    continue;
                }

                int btnX = windowX + 8, btnW = SIDEBAR_WIDTH - 16, btnH = 22;
                if (isHovered(mouseX, mouseY, btnX, (int) curModY, btnW, btnH)) {
                    if (button == 1) {
                        module.toggle();
                    } else if (button == 0) {
                        selectedModule = module;
                        settingsScrollY = 0;
                    }
                    return;
                }
                curModY += 26;
            }
        }

        if (selectedModule != null && isHovered(mouseX, mouseY, windowX + SIDEBAR_WIDTH, windowY + SETTINGS_CONTENT_TOP - 6, WINDOW_WIDTH - SIDEBAR_WIDTH, SETTINGS_CONTENT_HEIGHT + 6)) {
            float cy = windowY + SETTINGS_CONTENT_TOP + settingsScrollY;

            if (isHovered(mouseX, mouseY, sx + sw - 34, (int) cy + 4, 30, 16)) {
                selectedModule.toggle();
                return;
            }
            cy += 28;

            for (Setting s : selectedModule.getSettings()) {
                int rowH;
                switch (s.getType()) {
                    case NUMBER: rowH = 38; break;
                    case TAB_ANIMATION: rowH = 24; break;
                    case SCRIPT_LIST:
                        rowH = 30;
                        String scriptListText = (String) s.getValue();
                        if (scriptListText != null && !scriptListText.isEmpty() && !scriptListText.equals("Нет загруженных скриптов")) {
                            rowH = getScriptListHeight(s, sw);
                        }
                        break;
                    case SCRIPT_BUTTON: rowH = 34; break;
                    default: rowH = 28; break;
                }
                boolean elementVisible = cy + rowH > windowY + SETTINGS_CONTENT_TOP - 6 && cy < windowY + SETTINGS_CONTENT_BOTTOM;

                if (elementVisible) {
                    switch (s.getType()) {
                        // ИСПРАВЛЕНА ЛОГИКА КЛИКА ПО СПИСКУ СКРИПТОВ (ТУМБЛЕР)
                        case SCRIPT_LIST:
                            String text = (String) s.getValue();
                            if (text != null && !text.isEmpty() && !text.equals("Нет загруженных скриптов")) {
                                String[] lines = text.split("\n");
                                int currentY = (int) cy;

                                for (int i = 0; i < lines.length; i++) {
                                    String line = lines[i].trim();
                                    if (line.isEmpty()) continue;

                                    String cleanLine = stripColors(line);
                                    boolean isNewScript = cleanLine.startsWith("[✓]") || cleanLine.startsWith("[✗]");

                                    if (isNewScript) {
                                        String fullTitle = cleanLine.replace("[✓] ", "").replace("[✗] ", "").trim();

                                        String fileName = "";
                                        if (fullTitle.contains("#")) {
                                            fileName = fullTitle.substring(fullTitle.lastIndexOf("#") + 1).trim();
                                        }

                                        // Пропускаем строку автора
                                        if (i + 1 < lines.length) {
                                            String authLine = stripColors(lines[i + 1]);
                                            if (authLine.contains("Автор:") || authLine.contains("Author:")) i++;
                                        }

                                        // Получаем описание для расчета координат (не зависит от того, последний ли это скрипт)
                                        String desc = "";
                                        if (i + 1 < lines.length) {
                                            String descLine = stripColors(lines[i + 1]);
                                            if (descLine.contains("Описание:") || descLine.contains("Description:")) {
                                                desc = descLine.replace("Описание:", "").replace("Description:", "").trim();
                                                i++;
                                            }
                                        }
                                        List<String> wrappedDesc = mc.fontRenderer.listFormattedStringToWidth(desc, sw - 80);
                                        int cardHeight = 32 + (wrappedDesc.size() * 10);

                                        // Проверяем клик по ТУМБЛЕРУ (Переключателю)
                                        int toggleX = sx + sw - 34;
                                        int toggleW = 26;
                                        if (isHovered(mouseX, mouseY, toggleX, currentY + 4, toggleW, 14)) {
                                            if (selectedModule instanceof Scripts && !fileName.isEmpty()) {
                                                ((Scripts) selectedModule).toggleScript(fileName);
                                                updateScreen();
                                                return;
                                            }
                                        }
                                        currentY += cardHeight + 4;
                                    }
                                }
                            }
                            break;

                        case SCRIPT_BUTTON:
                            String label = (String) s.getValue();
                            int btnW = Math.max(80, mc.fontRenderer.getStringWidth(label) + 24);
                            int btnX = sx + sw - btnW - 8;
                            int btnY = (int) cy + 4;
                            int btnH = 20;
                            if (isHovered(mouseX, mouseY, btnX, btnY, btnW, btnH)) {
                                if (selectedModule instanceof Scripts) {
                                    ((Scripts) selectedModule).onScriptButtonClick(label);
                                }
                                return;
                            }
                            break;
                        case BOOLEAN:
                            if (isHovered(mouseX, mouseY, sx + sw - 34, (int) cy + 4, 30, 16)) {
                                s.setValue(!(boolean) s.getValue());
                                return;
                            }
                            break;
                        case NUMBER:
                            if (isHovered(mouseX, mouseY, sx + 8, (int) cy + 19, sw - 16, 12)) {
                                draggingSlider = true;
                                activeSlider = s;
                                updateSliderValue(activeSlider, mouseX, sx + 8, sw - 16);
                                return;
                            }
                            break;
                        case MODE:
                            int dW = 90, dH = 16;
                            int dX = sx + sw - dW - 6, dY = (int) cy + 4;
                            if (isHovered(mouseX, mouseY, dX, dY, dW, dH)) {
                                openDropdownSetting = s;
                                dropdownScrollY = 0;
                                return;
                            }
                            break;
                        case COLOR:
                            int cbx = sx + sw - 28, cby = (int) cy + 4;
                            if (isHovered(mouseX, mouseY, cbx, cby, 20, 16)) {
                                colorPicker.open(s, windowX, windowY, SIDEBAR_WIDTH, WINDOW_WIDTH, WINDOW_HEIGHT);
                                return;
                            }
                            break;
                        case HUD_POSITION:
                            int editBtnX = sx + sw - 100;
                            int editBtnW = 92;
                            int editBtnH = 18;
                            int editBtnY = (int) cy + 5;
                            if (isHovered(mouseX, mouseY, editBtnX, editBtnY, editBtnW, editBtnH)) {
                                if (editingHudPosition && activeHudPositionSetting == s) {
                                    editingHudPosition = false;
                                    activeHudPositionSetting = null;
                                } else {
                                    editingHudPosition = true;
                                    activeHudPositionSetting = s;
                                }
                                return;
                            }
                            break;
                        case TAB_ANIMATION:
                            int tabBtnW = 90, tabBtnH = 16;
                            int tabBtnX = sx + sw - tabBtnW - 6;
                            int tabBtnY = (int) cy + 4;
                            if (isHovered(mouseX, mouseY, tabBtnX, tabBtnY, tabBtnW, tabBtnH)) {
                                mc.displayGuiScreen(new TabAnimationSelector(this, s));
                                return;
                            }
                            break;
                        case TEXT:
                            int resetBtnW = 16, resetBtnH = 16;
                            int resetBtnX = sx + sw - resetBtnW - 4;
                            int resetBtnY = (int) cy + 4;

                            int[] bounds = getTextFieldBounds(s, sx, (int) cy, sw);
                            int tbX = bounds[0], tbY = bounds[1], tbW = bounds[2], tbH = bounds[3];

                            if (isHovered(mouseX, mouseY, resetBtnX, resetBtnY, resetBtnW, resetBtnH)) {
                                s.setValue(s.getDefaultValue());
                                GuiTextField f = textFields.get(s);
                                if (f != null) f.setText((String) s.getDefaultValue());
                                return;
                            }

                            GuiTextField field = textFields.get(s);
                            if (field != null) {
                                boolean fieldHovered = isHovered(mouseX, mouseY, tbX, tbY, tbW, tbH);
                                if (fieldHovered) {
                                    blurAllFields();
                                    field.setFocused(true);
                                    field.mouseClicked(mouseX, mouseY, button);
                                    return;
                                }
                            }
                            break;
                    }
                }
                cy += rowH;
            }
        }
        blurAllFields();
    }

    private int[] getTextFieldBounds(Setting s, int sx, int cy, int sw) {
        int resetBtnW = 16;
        int resetBtnX = sx + sw - resetBtnW - 4;

        int labelW = mc.fontRenderer.getStringWidth(s.getName());
        int tbX = sx + 8 + labelW + 12;

        int tbY = cy + 3;
        int tbW = Math.max(20, (resetBtnX - 4) - tbX);
        int tbH = 18;

        return new int[]{tbX, tbY, tbW, tbH};
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = draggingSlider = false;
        activeSlider = null;
        draggingDropdownScroll = false;
        draggingHudPreview = false;
        colorPicker.mouseReleased();
    }

    @Override
    public void mouseClickMove(int mouseX, int mouseY, int btn, long time) {
        if (dragging) {
            windowX = mouseX - dragX;
            windowY = mouseY - dragY;
            return;
        }

        if (draggingDropdownScroll && openDropdownSetting != null) {
            List<String> modes = openDropdownSetting.getModes();
            if (modes != null && !modes.isEmpty()) {
                int itemH = 14;
                int maxVisibleItems = 15;
                int visibleHeight = Math.min(modes.size(), maxVisibleItems) * itemH + 2;
                int totalHeight = modes.size() * itemH + 2;
                float maxScroll = Math.max(0, totalHeight - visibleHeight);

                if (maxScroll > 0) {
                    float scrollBarHeight = visibleHeight - 4;
                    float thumbHeight = Math.max(15, (float)maxVisibleItems / modes.size() * scrollBarHeight);
                    float deltaY = mouseY - dropdownScrollStartY;
                    float scrollRatio = deltaY / (scrollBarHeight - thumbHeight);
                    dropdownScrollY = MathHelper.clamp(dropdownScrollStartValue + scrollRatio * maxScroll, 0, maxScroll);
                }
            }
        }

        if (draggingHudPreview && activeHudPositionSetting != null) {
            ScaledResolution sr = new ScaledResolution(mc);
            int screenW = sr.getScaledWidth();
            int screenH = sr.getScaledHeight();

            int hudW = HUD.HUD_WIDTH;
            int hudH = HUD.HUD_HEIGHT;

            int newX = mouseX - hudPreviewDragOffsetX;
            int newY = mouseY - hudPreviewDragOffsetY;

            newX = Math.max(HUD.HUD_EDGE_MARGIN, Math.min(screenW - hudW - HUD.HUD_EDGE_MARGIN, newX));
            newY = Math.max(HUD.HUD_EDGE_MARGIN, Math.min(screenH - hudH - HUD.HUD_EDGE_MARGIN, newY));

            float xPercent = (float) newX / screenW;
            float yPercent = (float) newY / screenH;

            activeHudPositionSetting.setValue(new float[]{xPercent, yPercent});
        }

        colorPicker.mouseClickMove(mouseX, mouseY);
    }

    private void drawSettingBackground(int sx, int cy, int sw, int sh, int mouseX, int mouseY) {
        boolean hov = isHovered(mouseX, mouseY, sx, cy, sw, sh);
        RenderUtils.drawRoundedRect(sx, cy, sx + sw, cy + sh, 5, hov ? ThemeManager.getElementBgHover() : ThemeManager.getElementBg());
    }

    private void drawSwitch(int x, int y, boolean enabled) {
        float stage = enabled ? 1.0f : 0.0f;
        int w = 26, h = 14;
        int bgColor = enabled ? ThemeManager.getSwitchOn() : ThemeManager.getSwitchOff();
        RenderUtils.drawRoundedRect(x, y, x + w, y + h, h / 2f, bgColor);

        float thumbX = x + 2 + (stage * (w - h));
        RenderUtils.drawCircle(thumbX + (h - 4) / 2f, y + h / 2f, (h - 4) / 2f, 0xFFFFFFFF);
    }

    private void drawSlider(Setting s, int x, int y, int w) {
        RenderUtils.drawRoundedRect(x, y + 3, x + w, y + 5, 1, ThemeManager.getSwitchOff());
        Number min = (Number) s.getMin();
        Number max = (Number) s.getMax();
        double current = ((Number) s.getValue()).doubleValue();
        double percent = (current - min.doubleValue()) / (max.doubleValue() - min.doubleValue());

        int fillW = (int) (w * percent);
        RenderUtils.drawRoundedRect(x, y + 3, x + fillW, y + 5, 1, ThemeManager.getAccent());
        RenderUtils.drawCircle(x + fillW, y + 4f, 3.5f, 0xFFFFFFFF);
    }

    private void updateSliderValue(Setting s, int mouseX, int x, int w) {
        double percent = MathHelper.clamp((mouseX - x) / (double) w, 0.0, 1.0);
        double min = ((Number) s.getMin()).doubleValue();
        double max = ((Number) s.getMax()).doubleValue();
        double newValue = min + percent * (max - min);

        if (s.getValue() instanceof Integer) s.setValue((int) newValue);
        else if (s.getValue() instanceof Float) s.setValue((float) newValue);
        else s.setValue(newValue);
    }

    private Setting focusedSetting() {
        for (Map.Entry<Setting, GuiTextField> e : textFields.entrySet()) {
            if (e.getValue().isFocused()) return e.getKey();
        }
        return null;
    }

    private GuiTextField focusedField() {
        for (GuiTextField f : textFields.values()) if (f.isFocused()) return f;
        return null;
    }

    private void drawCheckerboard(int x, int y) {
        int size = 4;
        for (int i = 0; i < 20; i += size) {
            for (int j = 0; j < 16; j += size) {
                int cx = x + i;
                int cy = y + j;
                int cw = Math.min(size, 20 - i);
                int ch = Math.min(size, 16 - j);
                int color = ((i / size + j / size) % 2 == 0) ? 0xFFFFFFFF : 0xFFCCCCCC;
                drawRect(cx, cy, cx + cw, cy + ch, color);
            }
        }
    }

    private static class SwitchAnimation {
        private float stage = 0f;
        public void update(boolean target) {
            stage = target ? 1f : 0f;
        }
        public float getStage() { return stage; }
    }
}