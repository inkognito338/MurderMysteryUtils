package real.inkognito338.murdermysteryutils.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

public class RenderUtils {

    // ───────────────────────────────────────────────────────────────────
    //  Скругленные прямоугольники (Заливка и Обводка)
    // ───────────────────────────────────────────────────────────────────

    public static void drawRoundedRect(double x, double y, double x2, double y2, double radius, int color) {
        setupRenderState();
        setColor(color);

        // Перешли на GL_TRIANGLE_FAN вместо GL_POLYGON — это гарантирует стабильный рендер
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);

        // Первая точка — центр веера, чтобы полигон правильно закрашивал всю площадь
        GL11.glVertex2d(x + (x2 - x) / 2.0, y + (y2 - y) / 2.0);

        addRoundedVertices(x, y, x2, y2, radius);

        // Замыкаем веер на начальной точке скругления (180 градусов)
        double rad = Math.toRadians(180);
        GL11.glVertex2d(x + radius + Math.cos(rad) * radius, y + radius + Math.sin(rad) * radius);

        GL11.glEnd();

        restoreRenderState();
    }

    public static void drawRoundedRectOutline(double x, double y, double x2, double y2, double radius, int color, float lineWidth) {
        setupRenderState();
        GL11.glLineWidth(lineWidth);
        setColor(color);

        GL11.glBegin(GL11.GL_LINE_LOOP);
        addRoundedVertices(x, y, x2, y2, radius);
        GL11.glEnd();

        restoreRenderState();
    }

    private static void addRoundedVertices(double x, double y, double x2, double y2, double radius) {
        // Top Left Corner (180° to 270°)
        for (int i = 180; i <= 270; i += 4) {
            double rad = Math.toRadians(i);
            GL11.glVertex2d(x + radius + Math.cos(rad) * radius, y + radius + Math.sin(rad) * radius);
        }
        // Top Right Corner (270° to 360°)
        for (int i = 270; i <= 360; i += 4) {
            double rad = Math.toRadians(i);
            GL11.glVertex2d(x2 - radius + Math.cos(rad) * radius, y + radius + Math.sin(rad) * radius);
        }
        // Bottom Right Corner (0° to 90°)
        for (int i = 0; i <= 90; i += 4) {
            double rad = Math.toRadians(i);
            GL11.glVertex2d(x2 - radius + Math.cos(rad) * radius, y2 - radius + Math.sin(rad) * radius);
        }
        // Bottom Left Corner (90° to 180°)
        for (int i = 90; i <= 180; i += 4) {
            double rad = Math.toRadians(i);
            GL11.glVertex2d(x + radius + Math.cos(rad) * radius, y2 - radius + Math.sin(rad) * radius);
        }
    }

    // ───────────────────────────────────────────────────────────────────
    //  Круги
    // ───────────────────────────────────────────────────────────────────

    public static void drawCircle(double x, double y, double radius, int color) {
        setupRenderState();
        setColor(color);

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2d(x, y); // Центр круга
        for (int i = 0; i <= 360; i += 6) {
            double rad = Math.toRadians(i);
            GL11.glVertex2d(x + Math.cos(rad) * radius, y + Math.sin(rad) * radius);
        }
        GL11.glEnd();

        restoreRenderState();
    }

    public static void drawCircleOutline(double x, double y, double radius, int color, float lineWidth) {
        setupRenderState();
        GL11.glLineWidth(lineWidth);
        setColor(color);

        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i <= 360; i += 6) {
            double rad = Math.toRadians(i);
            GL11.glVertex2d(x + Math.cos(rad) * radius, y + Math.sin(rad) * radius);
        }
        GL11.glEnd();

        restoreRenderState();
    }

    // ───────────────────────────────────────────────────────────────────
    //  Scissor System
    // ───────────────────────────────────────────────────────────────────

    public static void startScissor(int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int scale = sr.getScaleFactor();

        int scissorX = x * scale;
        int scissorY = (sr.getScaledHeight() - (y + height)) * scale;
        int scissorWidth = width * scale;
        int scissorHeight = height * scale;

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, Math.max(0, scissorWidth), Math.max(0, scissorHeight));
    }

    public static void stopScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    // ───────────────────────────────────────────────────────────────────
    //  Утилиты состояний OpenGL
    // ───────────────────────────────────────────────────────────────────

    public static void setColor(int color) {
        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8 & 255) / 255.0F;
        float b = (color & 255) / 255.0F;
        GlStateManager.color(r, g, b, a);
        GL11.glColor4f(r, g, b, a); // Дублируем напрямую в OpenGL для надежности
    }

    private static void setupRenderState() {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // ФИКС ТЕКСТУРЫ: Отключаем куллинг майна, чтобы задний фон не исчезал
        GL11.glDisable(GL11.GL_CULL_FACE);

        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
    }

    private static void restoreRenderState() {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_CULL_FACE); // Возвращаем куллинг обратно в исходное состояние
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_BLEND);
    }
}