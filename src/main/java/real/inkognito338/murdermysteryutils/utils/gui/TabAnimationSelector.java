package real.inkognito338.murdermysteryutils.utils.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import real.inkognito338.murdermysteryutils.online.OnlineMode;
import real.inkognito338.murdermysteryutils.online.TabAnimationData;
import real.inkognito338.murdermysteryutils.utils.RenderUtils;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TabAnimationSelector extends GuiScreen {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final GuiScreen parent;
    private final Setting setting;

    private static final int WINDOW_WIDTH = 300;
    private static final int WINDOW_HEIGHT = 380;
    private static final int ROW_HEIGHT = 34;
    private static final int ROW_GAP = 6;
    private static final int HEADER_HEIGHT = 56;

    private int windowX, windowY;
    private boolean dragging;
    private int dragX, dragY;

    private float scrollY;
    private float targetScrollY;
    private float maxScrollY;

    private final List<String> animations = new ArrayList<>();
    private final List<String> filteredAnimations = new ArrayList<>();
    private String searchQuery = "";
    private boolean searchFocused = false;
    private boolean filterDirty = false;

    private boolean loading = true;
    private int loadAttempts = 0;

    private final Map<String, Float> hoverProgress = new HashMap<>();
    private float scrollbarHoverProgress = 0f;
    private float refreshHoverProgress = 0f;
    private float clearHoverProgress = 0f;

    private long lastUpdateTime = 0;
    private static final int UPDATE_INTERVAL = 50;
    private boolean needsUpdate = false;
    private long lastFrameTime = 0;

    public TabAnimationSelector(GuiScreen parent, Setting setting) {
        this.parent = parent;
        this.setting = setting;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        ScaledResolution sr = new ScaledResolution(mc);
        windowX = (sr.getScaledWidth() - WINDOW_WIDTH) / 2;
        windowY = (sr.getScaledHeight() - WINDOW_HEIGHT) / 2;
        loading = true;
        animations.clear();
        filteredAnimations.clear();
        loadAttempts = 0;
        lastUpdateTime = System.currentTimeMillis();
        lastFrameTime = lastUpdateTime;

        OnlineMode.getInstance().requestAnimationsList();
        OnlineMode.getInstance().requestAllAnimations();
        tryLoadAnimations();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        applyFilterIfDirty();
    }

    private void tryLoadAnimations() {
        List<String> serverList = TabAnimationData.getAvailableStyles();
        if (serverList != null && !serverList.isEmpty()) {
            animations.clear();
            animations.addAll(serverList);
            if (!animations.contains("Off")) {
                animations.add(0, "Off");
            }
            updateFilter();
            loading = false;
        }
    }

    private void updateFilter() {
        filteredAnimations.clear();
        String query = searchQuery.toLowerCase().trim();
        for (String anim : animations) {
            if (query.isEmpty() || anim.toLowerCase().contains(query)) {
                filteredAnimations.add(anim);
            }
        }
        targetScrollY = 0;
        filterDirty = false;
    }

    private void applyFilterIfDirty() {
        if (filterDirty) {
            updateFilter();
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        scrollY += (targetScrollY - scrollY) * 0.3f;

        if (loading && loadAttempts < 60) {
            loadAttempts++;
            if (loadAttempts % 10 == 0) {
                tryLoadAnimations();
            }
        }
        if (loading && loadAttempts >= 60) {
            loading = false;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL) {
            lastUpdateTime = currentTime;
            needsUpdate = true;
        }
    }

    private float approach(float current, float target, float dt, float speed) {
        float delta = target - current;
        if (Math.abs(delta) < 0.002f) return target;
        return current + delta * Math.min(1f, dt * speed);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.currentTimeMillis();
        float dt = Math.max(0f, Math.min(0.1f, (now - lastFrameTime) / 1000f));
        lastFrameTime = now;

        if (needsUpdate) {
            needsUpdate = false;
        }

        drawRect(0, 0, width, height, 0x9E000000);

        RenderUtils.drawRoundedRect(windowX - 3, windowY + 4, windowX + WINDOW_WIDTH + 3, windowY + WINDOW_HEIGHT + 6, 12, 0x40000000);
        RenderUtils.drawRoundedRect(windowX, windowY, windowX + WINDOW_WIDTH, windowY + WINDOW_HEIGHT, 10, ThemeManager.getBackground());
        RenderUtils.drawRoundedRectOutline(windowX, windowY, windowX + WINDOW_WIDTH, windowY + WINDOW_HEIGHT, 10, ThemeManager.getAccent(), 1.5f);

        drawHeader(mouseX, mouseY, now);

        int searchY = windowY + HEADER_HEIGHT;
        drawSearchBar(mouseX, mouseY, searchY, dt);

        int listY = searchY + 20 + 10;
        int listH = WINDOW_HEIGHT - (listY - windowY) - 14;
        String previewText = mc.player != null ? mc.player.getName() : "Player";
        String current = (String) setting.getValue();

        if (loading) {
            drawCenteredHint("Загрузка анимаций с сервера...", listY + listH / 2 - 14);
            drawSpinner(windowX + WINDOW_WIDTH / 2, listY + listH / 2 + 6, now);
        } else if (filteredAnimations.isEmpty()) {
            drawCenteredHint(animations.isEmpty() ? "Список стилей пуст" : "Ничего не найдено по запросу", listY + listH / 2 - 10);
            if (!animations.isEmpty()) {
                drawRefreshButton(mouseX, mouseY, listY + listH / 2 + 10, dt);
            }
        } else {
            RenderUtils.startScissor(windowX + 4, listY, WINDOW_WIDTH - 8, listH);
            float curY = listY + scrollY;
            float startY = curY;

            for (String anim : filteredAnimations) {
                if (curY + ROW_HEIGHT >= listY && curY <= listY + listH) {
                    drawAnimationRow(anim, mouseX, mouseY, (int) curY, current, previewText, now, dt);
                }
                curY += ROW_HEIGHT + ROW_GAP;
            }

            maxScrollY = Math.min(0, listH - (curY - startY - ROW_GAP));
            RenderUtils.stopScissor();
            drawScrollbar(mouseX, mouseY, listY, listH, curY - startY - ROW_GAP, dt);
        }
    }

    private void drawHeader(int mouseX, int mouseY, long now) {
        mc.fontRenderer.drawStringWithShadow("Выбор анимации таба", windowX + 16, windowY + 13, ThemeManager.getTextPrimary());

        String current = (String) setting.getValue();
        mc.fontRenderer.drawString("Активно:", windowX + 16, windowY + 27, ThemeManager.getTextDim());
        int labelW = mc.fontRenderer.getStringWidth("Активно: ");
        mc.fontRenderer.drawString("§l" + current, windowX + 16 + labelW, windowY + 27, ThemeManager.getAccent());

        drawRect(windowX + 12, windowY + HEADER_HEIGHT - 6, windowX + WINDOW_WIDTH - 12, windowY + HEADER_HEIGHT - 5, ThemeManager.getBorder());

        int refW = 22;
        int refX = windowX + WINDOW_WIDTH - refW - 10;
        int refY = windowY + 9;
        int refH = 16;
        boolean refHov = isHovered(mouseX, mouseY, refX, refY, refW, refH);
        refreshHoverProgress = approach(refreshHoverProgress, refHov ? 1f : 0f, 0.016f, 12f);
        int refBg = blendColor(ThemeManager.getElementBg(), ThemeManager.getElementBgHover(), refreshHoverProgress);
        RenderUtils.drawRoundedRect(refX, refY, refX + refW, refY + refH, 4, refBg);
        String refLabel = "⟳";
        int refLabelW = mc.fontRenderer.getStringWidth(refLabel);
        mc.fontRenderer.drawString(refLabel, refX + (refW - refLabelW) / 2, refY + 4, refHov ? ThemeManager.getAccent() : ThemeManager.getTextSecondary());
    }

    private void drawSearchBar(int mouseX, int mouseY, int searchY, float dt) {
        int searchX = windowX + 12;
        int searchW = WINDOW_WIDTH - 24;
        int searchH = 20;
        boolean searchHov = isHovered(mouseX, mouseY, searchX, searchY, searchW, searchH);

        RenderUtils.drawRoundedRect(searchX, searchY, searchX + searchW, searchY + searchH, 5, ThemeManager.getElementBg());
        RenderUtils.drawRoundedRectOutline(searchX, searchY, searchX + searchW, searchY + searchH, 5,
                searchFocused ? ThemeManager.getAccent() : (searchHov ? ThemeManager.getTextDim() : ThemeManager.getBorder()), 1f);

        int textStartX = searchX + 8;
        boolean hasClearBtn = !searchQuery.isEmpty();
        int clearBtnX = searchX + searchW - 16;

        if (searchQuery.isEmpty() && !searchFocused) {
            mc.fontRenderer.drawString("Поиск стиля...", textStartX, searchY + 6, ThemeManager.getTextDim());
        } else {
            String displayQuery = searchQuery + (searchFocused && (System.currentTimeMillis() / 500 % 2 == 0) ? "_" : "");
            int maxWidth = clearBtnX - textStartX - 4;
            while (mc.fontRenderer.getStringWidth(displayQuery) > maxWidth && displayQuery.length() > 1) {
                displayQuery = displayQuery.substring(1);
            }
            mc.fontRenderer.drawString(displayQuery, textStartX, searchY + 6, ThemeManager.getTextPrimary());
        }

        if (hasClearBtn) {
            boolean clearHov = isHovered(mouseX, mouseY, clearBtnX - 4, searchY + 2, 16, 16);
            clearHoverProgress = approach(clearHoverProgress, clearHov ? 1f : 0f, dt, 12f);
            int clearColor = blendColor(ThemeManager.getTextDim(), 0xFFFFFF, clearHoverProgress);
            mc.fontRenderer.drawString("×", clearBtnX, searchY + 6, clearColor);
        }

        if (!loading && !searchQuery.isEmpty()) {
            String countText = "Найдено: " + filteredAnimations.size() + " из " + animations.size();
            mc.fontRenderer.drawString(countText, searchX + 2, searchY + searchH + 4, ThemeManager.getTextDim());
        }
    }

    private void drawCenteredHint(String text, int y) {
        int textW = mc.fontRenderer.getStringWidth(text);
        mc.fontRenderer.drawString(text, windowX + (WINDOW_WIDTH - textW) / 2, y, ThemeManager.getTextDim());
    }

    private void drawSpinner(int centerX, int centerY, long now) {
        double t = (now % 1200L) / 1200.0;
        for (int i = 0; i < 3; i++) {
            double phase = (t - i * 0.2) % 1.0;
            if (phase < 0) phase += 1.0;
            float brightness = (float) (0.4 + 0.6 * Math.max(0, 1.0 - phase * 2.0));
            int alpha = (int) (brightness * 255) & 0xFF;
            int color = (alpha << 24) | (ThemeManager.getAccent() & 0xFFFFFF);
            RenderUtils.drawRoundedRect(centerX - 12 + i * 12, centerY, centerX - 8 + i * 12, centerY + 4, 2, color);
        }
    }

    private void drawAnimationRow(String anim, int mouseX, int mouseY, int curY, String current, String previewText, long nowMs, float dt) {
        boolean hov = isHovered(mouseX, mouseY, windowX + 10, curY, WINDOW_WIDTH - 20, ROW_HEIGHT);
        boolean sel = anim.equals(current);

        float hoverP = hoverProgress.getOrDefault(anim, 0f);
        hoverP = approach(hoverP, hov ? 1f : 0f, dt, 14f);
        hoverProgress.put(anim, hoverP);

        int baseBg = sel ? ThemeManager.getAccentDark() : ThemeManager.getElementBg();
        int hoverBg = sel ? ThemeManager.getAccentDark() : ThemeManager.getElementBgHover();
        int bg = blendColor(baseBg, hoverBg, sel ? 0f : hoverP);

        int expand = (int) (hoverP * 1.5f);
        RenderUtils.drawRoundedRect(windowX + 10 - expand, curY - expand, windowX + WINDOW_WIDTH - 10 + expand, curY + ROW_HEIGHT + expand, 6, bg);
        if (sel) {
            RenderUtils.drawRoundedRectOutline(windowX + 10, curY, windowX + WINDOW_WIDTH - 10, curY + ROW_HEIGHT, 6, ThemeManager.getAccent(), 1.3f);
        }

        int textColor = sel ? ThemeManager.getTextPrimary() : blendColor(ThemeManager.getTextSecondary(), ThemeManager.getTextPrimary(), hoverP * 0.6f);
        int nameX = windowX + 18;
        mc.fontRenderer.drawString(anim, nameX, curY + 6, textColor);

        int previewY = curY + ROW_HEIGHT - 14;
        if (!"Off".equalsIgnoreCase(anim)) {
            int[] colors = TabAnimationData.getColorsForStyle(anim);
            int speed = TabAnimationData.getSpeedForStyle(anim);
            if (colors == null || colors.length == 0) {
                mc.fontRenderer.drawString(previewText, nameX, previewY, ThemeManager.getTextDim());
            } else {
                drawAnimatedPreview(previewText, nameX, previewY, colors, speed, nowMs);
            }
        } else {
            mc.fontRenderer.drawString(previewText, nameX, previewY, 0x888888);
        }
    }

    private void drawRefreshButton(int mouseX, int mouseY, int y, float dt) {
        int refW = 100, refH = 20;
        int refX = windowX + WINDOW_WIDTH / 2 - refW / 2;
        boolean refHov = isHovered(mouseX, mouseY, refX, y, refW, refH);
        refreshHoverProgress = approach(refreshHoverProgress, refHov ? 1f : 0f, dt, 12f);
        int refBg = blendColor(ThemeManager.getElementBg(), ThemeManager.getElementBgHover(), refreshHoverProgress);
        RenderUtils.drawRoundedRect(refX, y, refX + refW, y + refH, 5, refBg);
        RenderUtils.drawRoundedRectOutline(refX, y, refX + refW, y + refH, 5, ThemeManager.getAccent(), 1f);
        String label = "Обновить";
        int labelW = mc.fontRenderer.getStringWidth(label);
        mc.fontRenderer.drawString(label, refX + (refW - labelW) / 2, y + 6, ThemeManager.getTextPrimary());
    }

    private void drawScrollbar(int mouseX, int mouseY, int listY, int listH, float totalH, float dt) {
        if (maxScrollY >= 0) return;

        int trackX1 = windowX + WINDOW_WIDTH - 8;
        int trackX2 = windowX + WINDOW_WIDTH - 5;
        boolean trackHov = isHovered(mouseX, mouseY, trackX1 - 2, listY, (trackX2 - trackX1) + 4, listH);
        scrollbarHoverProgress = approach(scrollbarHoverProgress, trackHov ? 1f : 0f, dt, 14f);

        RenderUtils.drawRoundedRect(trackX1, listY, trackX2, listY + listH, 1, (int) (0x18 * (0.5f + 0.5f * scrollbarHoverProgress)) << 24 | 0xFFFFFF);

        float thumbH = Math.max(24, (listH / totalH) * listH);
        float thumbY = listY + (-scrollY / -maxScrollY) * (listH - thumbH);
        int thumbColor = blendColor(ThemeManager.getScrollbar(), ThemeManager.getAccent(), scrollbarHoverProgress * 0.5f);
        int thumbWidth = trackHov ? 1 : 0;
        RenderUtils.drawRoundedRect(trackX1 - thumbWidth, (int) thumbY, trackX2 + thumbWidth, (int) (thumbY + thumbH), 2, thumbColor);
    }

    /**
     * Отрисовывает превью анимации
     */
    private void drawAnimatedPreview(String text, float x, float y, int[] colors, int speed, long nowMs) {
        if (colors == null || colors.length == 0) {
            mc.fontRenderer.drawString(text, (int) x, (int) y, ThemeManager.getTextDim());
            return;
        }

        // Используем скорость как есть - без дополнительного деления
        double t = (nowMs % 100000L) / 1000.0 * (speed / 100.0);
        float totalWidth = mc.fontRenderer.getStringWidth(text);
        if (totalWidth <= 0f) return;

        float offset = (float) ((t % 1.0 + 1.0) % 1.0);

        List<String> visibleChars = new ArrayList<>();
        List<String> formatPrefix = new ArrayList<>();
        StringBuilder currentFormat = new StringBuilder();

        int len = text.length();
        for (int i = 0; i < len; i++) {
            char ch = text.charAt(i);
            if (ch == '\u00A7' && i + 1 < len) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (code == 'r') currentFormat.setLength(0);
                else if ("lonmk".indexOf(code) >= 0) currentFormat.append('\u00A7').append(code);
                i++;
                continue;
            }
            visibleChars.add(String.valueOf(ch));
            formatPrefix.add(currentFormat.toString());
        }

        float cursorX = x;
        for (int i = 0; i < visibleChars.size(); i++) {
            String s = formatPrefix.get(i) + visibleChars.get(i);
            float charWidth = mc.fontRenderer.getStringWidth(s);
            float progress = ((cursorX - x) + charWidth / 2.0f) / totalWidth;
            progress = Math.max(0f, Math.min(1f, progress));
            int color = lerpMultiStop(colors, (float) (((progress - offset) % 1.0 + 1.0) % 1.0));
            mc.fontRenderer.drawString(s, (int) cursorX, (int) y, color);
            cursorX += charWidth;
        }
    }

    private int lerpMultiStop(int[] stops, float t) {
        if (stops.length == 0) return 0xFFFFFF;
        if (stops.length == 1) return stops[0];
        t = Math.max(0f, Math.min(1f, t));
        float scaled = t * (stops.length - 1);
        int idx = (int) Math.floor(scaled);
        if (idx >= stops.length - 1) return stops[stops.length - 1];
        float localT = scaled - idx;
        int a = stops[idx], b = stops[idx + 1];
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((Math.round(ar + (br - ar) * localT) & 0xFF) << 16) |
                ((Math.round(ag + (bg - ag) * localT) & 0xFF) << 8) |
                (Math.round(ab + (bb - ab) * localT) & 0xFF);
    }

    private int blendColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int ra = (int) (aa + (ba - aa) * t) & 0xFF;
        int rr = (int) (ar + (br - ar) * t) & 0xFF;
        int rg = (int) (ag + (bg - ag) * t) & 0xFF;
        int rb = (int) (ab + (bb - ab) * t) & 0xFF;
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    private boolean isHovered(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        super.mouseClicked(mouseX, mouseY, button);
        if (button != 0) return;

        if (isHovered(mouseX, mouseY, windowX, windowY, WINDOW_WIDTH, HEADER_HEIGHT - 6)) {
            int refW = 22;
            int refX = windowX + WINDOW_WIDTH - refW - 10;
            int refY = windowY + 9;
            int refH = 16;
            if (isHovered(mouseX, mouseY, refX, refY, refW, refH)) {
                triggerRefresh();
                return;
            }
            dragging = true;
            dragX = mouseX - windowX;
            dragY = mouseY - windowY;
            return;
        }

        int searchX = windowX + 12;
        int searchY = windowY + HEADER_HEIGHT;
        int searchW = WINDOW_WIDTH - 24;
        int searchH = 20;

        if (!searchQuery.isEmpty()) {
            int clearBtnX = searchX + searchW - 16;
            if (isHovered(mouseX, mouseY, clearBtnX - 4, searchY + 2, 16, 16)) {
                searchQuery = "";
                updateFilter();
                searchFocused = true;
                return;
            }
        }

        boolean wasFocused = searchFocused;
        searchFocused = isHovered(mouseX, mouseY, searchX, searchY, searchW, searchH);
        if (wasFocused && !searchFocused) {
            applyFilterIfDirty();
        }

        int listY = searchY + 20 + 10;
        if (loading || filteredAnimations.isEmpty()) {
            int refW = 100, refH = 20;
            int refX = windowX + WINDOW_WIDTH / 2 - refW / 2;
            int listH = WINDOW_HEIGHT - (listY - windowY) - 14;
            int refY = listY + listH / 2 + 10;
            if (!animations.isEmpty() && isHovered(mouseX, mouseY, refX, refY, refW, refH)) {
                triggerRefresh();
                return;
            }
        }

        int listH = WINDOW_HEIGHT - (listY - windowY) - 14;
        if (isHovered(mouseX, mouseY, windowX + 10, listY, WINDOW_WIDTH - 20, listH)) {
            float curY = listY + scrollY;
            for (String anim : filteredAnimations) {
                if (isHovered(mouseX, mouseY, windowX + 10, (int) curY, WINDOW_WIDTH - 20, ROW_HEIGHT)) {
                    setting.setValue(anim);
                    OnlineMode.getInstance().setTabAnimation(anim);
                    return;
                }
                curY += ROW_HEIGHT + ROW_GAP;
            }
        }

        if (!isHovered(mouseX, mouseY, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT)) {
            mc.displayGuiScreen(parent);
        }
    }

    private void triggerRefresh() {
        loading = true;
        loadAttempts = 0;
        OnlineMode.getInstance().requestAnimationsList();
        OnlineMode.getInstance().requestAllAnimations();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (searchFocused && !searchQuery.isEmpty()) {
                searchQuery = "";
                updateFilter();
                return;
            }
            applyFilterIfDirty();
            mc.displayGuiScreen(parent);
            return;
        }

        if (searchFocused) {
            if (keyCode == Keyboard.KEY_RETURN) {
                applyFilterIfDirty();
                return;
            } else if (keyCode == Keyboard.KEY_BACK && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                filterDirty = true;
                return;
            } else if (Character.isLetterOrDigit(typedChar) || typedChar == ' ' || typedChar == '_' || typedChar == '-') {
                searchQuery += typedChar;
                filterDirty = true;
                return;
            }
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = false;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int btn, long time) {
        if (dragging) {
            windowX = mouseX - dragX;
            windowY = mouseY - dragY;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dw = Mouse.getDWheel();
        if (dw == 0) return;

        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;

        if (isHovered(mouseX, mouseY, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT)) {
            targetScrollY = MathHelper.clamp(targetScrollY + (dw > 0 ? 40 : -40), maxScrollY, 0);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}