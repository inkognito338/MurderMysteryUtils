package real.inkognito338.murdermysteryutils.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.ModuleManager;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 12.07.2026
 */

@SideOnly(Side.CLIENT)
public class ArrayListMod extends Module {

    private static final long UPDATE_INTERVAL = 100;
    private final Minecraft mc = Minecraft.getMinecraft();
    // Карты состояний для плавных анимаций (Easing)
    private final Map<String, Float> animatedY = new HashMap<>();
    private final Map<String, Float> animatedSlide = new HashMap<>();
    private List<Module> activeModules = new ArrayList<>();
    private long lastUpdateTime = 0;
    private long lastRenderTime = System.currentTimeMillis();

    public ArrayListMod() {
        super("ArrayList");

        // --- POSITION & ALIGNMENT ---
        this.addSetting(new Setting("Alignment", SettingType.MODE, "Right",
                new String[]{"Right", "Left"}));

        this.addSetting(new Setting("V Align", SettingType.MODE, "Top",
                new String[]{"Top", "Bottom"}));

        this.addSetting(new Setting("Sort", SettingType.MODE, "Width",
                new String[]{"Width", "Alphabetical"}));

        // Ограничения сняты — офсет теперь задаётся в пикселях без искусственного потолка
        this.addSetting(new Setting("X Offset", SettingType.NUMBER, 0.0, 0.0, 500.0));
        this.addSetting(new Setting("Y Offset", SettingType.NUMBER, 0.0, 0.0, 500.0));
        this.addSetting(new Setting("Spacing", SettingType.NUMBER, 0.29096989966555187, 0.0, 3.0));

        // --- DESIGN & RENDER ---
        this.addSetting(new Setting("Text Case", SettingType.MODE, "Normal",
                new String[]{"Normal", "UPPERCASE", "lowercase"}));

        this.addSetting(new Setting("Background", SettingType.BOOLEAN, true));
        this.addSetting(new Setting("BG Alpha", SettingType.NUMBER, 126.18729096989966, 50.0, 220.0));
        this.addSetting(new Setting("Border", SettingType.MODE, "None",
                new String[]{"None", "Left", "Right", "Outline"}));

        // --- COLORS ---
        this.addSetting(new Setting("Color Mode", SettingType.MODE, "Ocean",
                new String[]{"Custom", "Rainbow", "Astolfo", "Pulse", "Wave", "Fade", "Breathe",
                        "Miami", "Lava", "Ocean", "Cotton Candy", "Halloween", "Matrix", "Cherry",
                        "Amethyst", "Emerald", "Ruby", "Sapphire", "Gold", "Cyberpunk", "Vaporwave",
                        "Sunset", "Forest", "Midnight", "Ice", "Fire", "Galaxy", "Toxic", "Barbie"}));

        this.addSetting(new Setting("Color Speed", SettingType.NUMBER, 1.0, 1.0, 8.0));
        this.addSetting(new Setting("Spread", SettingType.NUMBER, 50.0, 50.0, 300.0));
        this.addSetting(new Setting("Primary Color", SettingType.COLOR, new float[]{0.35f, 0.75f, 1.0f}));

        // --- ANIMATION ---
        this.addSetting(new Setting("Anim Speed", SettingType.NUMBER, 5.0, 5.0, 20.0));
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        activeModules.clear();
        animatedY.clear();
        animatedSlide.clear();
    }

    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || !isToggled() || mc.gameSettings.hideGUI || mc.player == null) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - lastRenderTime) / 1000f;
        lastRenderTime = currentTime;

        if (currentTime - lastUpdateTime > UPDATE_INTERVAL) {
            updateActiveModules();
            lastUpdateTime = currentTime;
        }

        if (activeModules.isEmpty() && animatedSlide.isEmpty()) return;

        ScaledResolution sr = new ScaledResolution(mc);

        // Кэширование настроек
        boolean isRight = getSettingByName("Alignment").getValue().equals("Right");
        boolean isBottom = getSettingByName("V Align").getValue().equals("Bottom");
        String textCase = (String) getSettingByName("Text Case").getValue();
        boolean showBg = (boolean) getSettingByName("Background").getValue();
        String borderMode = (String) getSettingByName("Border").getValue();

        float animSpeed = (float) (double) getSettingByName("Anim Speed").getValue();
        int bgAlpha = (int) (double) getSettingByName("BG Alpha").getValue();
        int spacing = (int) (double) getSettingByName("Spacing").getValue();
        int spread = (int) (double) getSettingByName("Spread").getValue();

        int screenWidth = sr.getScaledWidth();
        int screenHeight = sr.getScaledHeight();

        int xOffset = clamp((int) (double) getSettingByName("X Offset").getValue(), screenWidth);
        int yOffset = clamp((int) (double) getSettingByName("Y Offset").getValue(), screenHeight);

        int startX = isRight ? screenWidth - xOffset : xOffset;
        startX = clamp(startX, screenWidth);

        int startY = isBottom ? screenHeight - yOffset : yOffset;
        startY = clamp(startY, screenHeight);

        float[] colorArr = (float[]) getSettingByName("Primary Color").getValue();
        Color customColor = new Color(colorArr[0], colorArr[1], colorArr[2]);
        String colorMode = (String) getSettingByName("Color Mode").getValue();
        float colorSpeed = (float) (double) getSettingByName("Color Speed").getValue();

        int index = 0;
        int rowHeight = mc.fontRenderer.FONT_HEIGHT + 4; // Отступы Y по умолчанию 2 сверху, 2 снизу

        for (Module module : activeModules) {
            String name = formatText(module.getName(), textCase);
            int totalWidth = mc.fontRenderer.getStringWidth(name) + 6; // +6 для X padding

            // Целевые позиции: при Bottom строки растут вверх от нижнего якоря
            float targetY = isBottom
                    ? startY - ((index + 1) * rowHeight) - (index * spacing)
                    : startY + (index * (rowHeight + spacing));

            // Экспоненциальное сглаживание анимации
            float animFactor = (float) (1.0 - Math.exp(-animSpeed * deltaTime));

            float currentY = animatedY.getOrDefault(module.getName(), targetY);
            currentY += (targetY - currentY) * animFactor;
            animatedY.put(module.getName(), currentY);

            float targetSlide = module.isToggled() ? (float) totalWidth : 0f;
            float currentSlide = animatedSlide.getOrDefault(module.getName(), 0f);
            currentSlide += (targetSlide - currentSlide) * animFactor;
            animatedSlide.put(module.getName(), currentSlide);

            // Удаление выключенных модулей после завершения анимации
            if (!module.isToggled() && currentSlide < 1f) {
                animatedY.remove(module.getName());
                animatedSlide.remove(module.getName());
                continue;
            }

            int renderY = Math.round(currentY);
            int slideOffset = Math.round(currentSlide);

            int rectLeft = isRight ? startX - slideOffset : startX;
            int rectRight = isRight ? startX : startX + slideOffset;

            // Динамический цвет
            Color moduleColor = getColor(colorMode, index * spread, customColor, colorSpeed);
            int rgbaColor = moduleColor.getRGB();

            // Обрезка GL_SCISSOR
            prepareScissorBox(rectLeft, renderY, rectRight, renderY + rowHeight, sr);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);

            // Фон
            if (showBg) {
                int bgColor = new Color(15, 15, 15, bgAlpha).getRGB();
                Gui.drawRect(rectLeft, renderY, rectRight, renderY + rowHeight, bgColor);
            }

            // Обводка
            switch (borderMode) {
                case "Left":
                    Gui.drawRect(rectLeft, renderY, rectLeft + 2, renderY + rowHeight, rgbaColor);
                    break;
                case "Right":
                    Gui.drawRect(rectRight - 2, renderY, rectRight, renderY + rowHeight, rgbaColor);
                    break;
                case "Outline":
                    Gui.drawRect(rectLeft, renderY, rectLeft + 1, renderY + rowHeight, rgbaColor);
                    Gui.drawRect(rectRight - 1, renderY, rectRight, renderY + rowHeight, rgbaColor);
                    Gui.drawRect(rectLeft, renderY, rectRight, renderY + 1, rgbaColor);
                    Gui.drawRect(rectLeft, renderY + rowHeight - 1, rectRight, renderY + rowHeight, rgbaColor);
                    break;
            }

            // Текст (Text Shadow полностью удален)
            int textX = isRight ? rectRight - totalWidth + 3 : rectLeft + 3;
            int textY = renderY + 2;

            mc.fontRenderer.drawString(name, textX, textY, rgbaColor);

            GL11.glDisable(GL11.GL_SCISSOR_TEST);

            if (module.isToggled()) {
                index++;
            }
        }
    }

    // --- УТИЛИТЫ ---

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(value, max));
    }

    private String formatText(String text, String mode) {
        if (mode.equals("UPPERCASE")) return text.toUpperCase();
        if (mode.equals("lowercase")) return text.toLowerCase();
        return text;
    }

    private void prepareScissorBox(int x, int y, int x2, int y2, ScaledResolution sr) {
        int factor = sr.getScaleFactor();
        GL11.glScissor(x * factor, (sr.getScaledHeight() - y2) * factor, (x2 - x) * factor, (y2 - y) * factor);
    }

    private void updateActiveModules() {
        String sortMode = (String) getSettingByName("Sort").getValue();
        String textCase = (String) getSettingByName("Text Case").getValue();

        List<Module> filtered = ModuleManager.getModules().stream()
                .filter(m -> !m.getName().equals(this.getName()))
                .filter(m -> m.isToggled() || animatedSlide.getOrDefault(m.getName(), 0f) > 0.5f)
                .collect(Collectors.toList());

        Comparator<Module> comparator = (m1, m2) -> {
            String name1 = formatText(m1.getName(), textCase);
            String name2 = formatText(m2.getName(), textCase);

            if (sortMode.equals("Alphabetical")) {
                return name1.compareToIgnoreCase(name2);
            } else {
                int width1 = mc.fontRenderer.getStringWidth(name1);
                int width2 = mc.fontRenderer.getStringWidth(name2);
                return Integer.compare(width2, width1);
            }
        };

        filtered.sort(comparator);
        activeModules = filtered;
    }

    // --- СИСТЕМА ЦВЕТОВ ---

    private Color getColor(String mode, int delay, Color customColor, float speed) {
        float time = (System.currentTimeMillis() % (int) (3000 / speed)) / (3000f / speed);
        float offsetTime = time + (delay / 2000f);
        // Синусоида от 0.0 до 1.0
        double sine = (Math.sin(offsetTime * Math.PI * 2) + 1.0) / 2.0;

        switch (mode) {
            // --- РЕЖИМ, ЗАВИСЯЩИЙ ОТ PRIMARY COLOR ---
            case "Custom": return customColor;

            // --- АВТОНОМНЫЕ РЕЖИМЫ ---
            case "Rainbow": return Color.getHSBColor(offsetTime % 1.0f, 0.8f, 1.0f);
            case "Astolfo":
                float astolfoTime = offsetTime % 1.0f;
                float astolfoHue = (astolfoTime > 0.5f) ? 0.5f - (astolfoTime - 0.5f) : astolfoTime;
                return Color.getHSBColor(astolfoHue + 0.5f, 0.5f, 1.0f);
            case "Pulse": // Теперь автономный градиент (например, красный пульс)
                return blendColors(new Color(255, 50, 50), new Color(100, 0, 0), (float) sine);
            case "Wave":
                return Color.getHSBColor(0.5f + (float)sine * 0.2f, 0.7f, 1.0f);
            case "Fade": // Статичный переход от синего к фиолетовому
                return blendColors(new Color(0, 100, 255), new Color(150, 0, 255), (float) sine);
            case "Breathe": // Плавное изменение прозрачности белого
                float breatheAlpha = (float) (0.6f + sine * 0.4f);
                return new Color(255, 255, 255, (int) (breatheAlpha * 255));

            // --- ТЕМАТИЧЕСКИЕ ГРАДИЕНТЫ ---
            case "Miami": return blendColors(new Color(255, 102, 204), new Color(0, 255, 255), (float) sine);
            case "Lava": return blendColors(new Color(255, 80, 0), new Color(255, 220, 0), (float) sine);
            case "Ocean": return blendColors(new Color(0, 150, 255), new Color(0, 255, 200), (float) sine);
            case "Cotton Candy": return blendColors(new Color(255, 182, 193), new Color(173, 216, 230), (float) sine);
            case "Halloween": return blendColors(new Color(255, 140, 0), new Color(180, 50, 180), (float) sine);
            case "Matrix": return blendColors(new Color(0, 255, 0), new Color(0, 180, 0), (float) sine);
            case "Cherry": return blendColors(new Color(255, 50, 80), new Color(180, 0, 100), (float) sine);
            case "Amethyst": return blendColors(new Color(180, 120, 255), new Color(220, 160, 255), (float) sine);
            case "Emerald": return blendColors(new Color(60, 200, 100), new Color(0, 255, 127), (float) sine);
            case "Ruby": return blendColors(new Color(200, 30, 50), new Color(255, 60, 100), (float) sine);
            case "Sapphire": return blendColors(new Color(60, 120, 240), new Color(135, 206, 250), (float) sine);
            case "Gold": return blendColors(new Color(255, 215, 0), new Color(255, 255, 153), (float) sine);
            case "Cyberpunk": return blendColors(new Color(255, 255, 0), new Color(220, 50, 255), (float) sine);
            case "Vaporwave": return blendColors(new Color(0, 255, 255), new Color(255, 50, 255), (float) sine);
            case "Sunset": return blendColors(new Color(255, 100, 50), new Color(255, 130, 200), (float) sine);
            case "Forest": return blendColors(new Color(60, 180, 60), new Color(140, 180, 50), (float) sine);
            case "Midnight": return blendColors(new Color(80, 80, 200), new Color(140, 50, 220), (float) sine);
            case "Ice": return blendColors(new Color(200, 255, 255), new Color(50, 220, 255), (float) sine);
            case "Fire": return blendColors(new Color(255, 50, 0), new Color(255, 180, 0), (float) sine);
            case "Galaxy": return blendColors(new Color(120, 50, 220), new Color(180, 100, 255), (float) sine);
            case "Toxic": return blendColors(new Color(80, 220, 50), new Color(200, 255, 50), (float) sine);
            case "Barbie": return blendColors(new Color(255, 50, 180), new Color(255, 182, 193), (float) sine);

            default: return Color.WHITE;
        }
    }

    private Color blendColors(Color color1, Color color2, float ratio) {
        ratio = Math.max(0.0f, Math.min(1.0f, ratio));
        float inverseRatio = 1.0f - ratio;

        int r = (int) (color1.getRed() * inverseRatio + color2.getRed() * ratio);
        int g = (int) (color1.getGreen() * inverseRatio + color2.getGreen() * ratio);
        int b = (int) (color1.getBlue() * inverseRatio + color2.getBlue() * ratio);
        int a = (int) (color1.getAlpha() * inverseRatio + color2.getAlpha() * ratio);

        return new Color(r, g, b, a);
    }
}