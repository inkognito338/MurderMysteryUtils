package real.inkognito338.murdermysteryutils.modules.mm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import real.inkognito338.murdermysteryutils.modules.Module;

import java.text.DecimalFormat;

@SideOnly(Side.CLIENT)
public class NameTags extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat PING_FORMAT = new DecimalFormat("#");

    // Улучшенные настройки масштабирования для лучшей видимости
    private static final float BASE_SCALE = 0.045f;
    private static final float MIN_SCALE = 0.035f;
    private static final float MAX_SCALE = 0.22f;
    private static final float MIN_DISTANCE = 3f;

    public NameTags() {
        super("NameTags");
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (mc.player == null || mc.world == null) return;

        // Сохраняем ПОЛНОЕ состояние OpenGL перед началом рендера
        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();

        for (EntityPlayer player : mc.world.playerEntities.toArray(new EntityPlayer[0])) {
            if (player == null || player.isDead || !player.isEntityAlive()) {
                continue;
            }

            if (!(player instanceof AbstractClientPlayer)) continue;
            if (player.equals(mc.player)) continue;
            if (isNPC(player)) continue;

            renderPlayerName(player, event.getPartialTicks());
        }

        // Полностью восстанавливаем состояние OpenGL
        GlStateManager.popAttrib();
        GlStateManager.popMatrix();

        // КРИТИЧЕСКИ ВАЖНО: Сбрасываем цвет в белый
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        // Восстанавливаем стандартное состояние
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
    }

    private boolean isNPC(EntityPlayer player) {
        String raw = player.getDisplayName().getUnformattedText();
        String clean = TextFormatting.getTextWithoutFormattingCodes(raw);

        if (clean == null) return true;

        NetworkPlayerInfo playerInfo = mc.getConnection() != null ?
                mc.getConnection().getPlayerInfo(player.getUniqueID()) : null;

        if (playerInfo == null) return true;

        return clean.matches("^NPC[ \\[\\]].*") ||
                clean.equals("NPC") ||
                player.getGameProfile().getName().contains("NPC") ||
                clean.startsWith("CIT-");
    }

    private void renderPlayerName(EntityPlayer entity, float partialTicks) {
        String name = entity.getName();
        if (name == null || name.isEmpty()) return;

        // Интерполяция позиции
        double x = interpolate(entity.prevPosX, entity.posX, partialTicks) - mc.getRenderManager().viewerPosX;
        double y = interpolate(entity.prevPosY, entity.posY, partialTicks) - mc.getRenderManager().viewerPosY + entity.height + 0.7;
        double z = interpolate(entity.prevPosZ, entity.posZ, partialTicks) - mc.getRenderManager().viewerPosZ;

        double distance = mc.player.getDistance(entity);
        float scale = calculateScale(distance);

        FontRenderer fontRenderer = mc.fontRenderer;

        NetworkPlayerInfo playerInfo = mc.getConnection() != null ?
                mc.getConnection().getPlayerInfo(entity.getUniqueID()) : null;

        if (playerInfo == null) return;

        int ping = playerInfo.getResponseTime();
        String pingText = PING_FORMAT.format(ping) + "ms";

        // Сохраняем состояние перед началом рендера этого тега
        GlStateManager.pushMatrix();

        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mc.getRenderManager().playerViewX,
                mc.gameSettings.thirdPersonView == 2 ? -1.0F : 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-scale, -scale, scale);

        // Настраиваем состояние для рендера
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );
        GlStateManager.enableTexture2D();
        GlStateManager.disableCull();

        // ВАЖНО: Сброс цвета перед рендером
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        // Размеры элементов с адаптивным масштабом
        float headSize = 10.0f;
        float padding = 3.0f;
        float spacing = 2.5f;
        float namePingSpacing = 3.0f;

        int nameWidth = fontRenderer.getStringWidth(name);
        int pingWidth = fontRenderer.getStringWidth(pingText);
        int totalTextWidth = nameWidth + pingWidth + (int)namePingSpacing;

        float backgroundWidth = headSize + spacing + totalTextWidth + padding * 2;
        float backgroundHeight = Math.max(headSize, fontRenderer.FONT_HEIGHT * 1.05f) + padding * 2;

        float startX = -backgroundWidth / 2f;
        float startY = -backgroundHeight / 2f - 2;

        // Адаптивная прозрачность фона - на дальних дистанциях делаем темнее
        int backgroundAlpha = calculateBackgroundAlpha(distance);
        int backgroundColor = (backgroundAlpha << 24) | 0x000000;

        // Отрисовка фона
        drawRoundedRect(startX, startY, startX + backgroundWidth, startY + backgroundHeight,
                2.5f, backgroundColor);

        float headX = startX + padding;
        float headY = startY + (backgroundHeight - headSize) / 2f;

        // Отрисовка головы игрока
        ResourceLocation skin = playerInfo.getLocationSkin();
        if (skin != null) {
            mc.getTextureManager().bindTexture(skin);

            // ВАЖНО: Сброс цвета перед каждой текстурой
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

            // Тень
            drawModalRect(headX + 0.5f, headY + 0.5f, headSize, headSize,
                    8, 8, 8, 8, 64, 64, 0x40000000);

            // Основной слой
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            drawModalRect(headX, headY, headSize, headSize,
                    8, 8, 8, 8, 64, 64, 0xFFFFFFFF);

            // Слой шляпы
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            drawModalRect(headX, headY, headSize, headSize,
                    40, 8, 8, 8, 64, 64, 0xFFFFFFFF);

            // Сброс цвета после текстур
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        }

        // Позиция текста
        float textX = headX + headSize + spacing;
        float textY = startY + (backgroundHeight - fontRenderer.FONT_HEIGHT) / 2f;

        // Отрисовка ника с тенью для лучшей читаемости
        fontRenderer.drawString(name, textX, textY, 0xFFFFFFFF, true);

        // Отрисовка пинга с тенью
        float pingX = textX + nameWidth + namePingSpacing;
        int pingColor = getPingColor(ping);
        fontRenderer.drawString(pingText, pingX, textY, pingColor, true);

        // Обводка с адаптивной прозрачностью
        int outlineAlpha = calculateOutlineAlpha(distance);
        int outlineColor = (outlineAlpha << 24) | 0xFFFFFF;
        drawRoundedRectOutline(startX, startY, startX + backgroundWidth,
                startY + backgroundHeight, 2.5f, 0.6f, outlineColor);

        // КРИТИЧЕСКИ ВАЖНО: Полностью восстанавливаем состояние
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.depthMask(true);

        // Сброс цвета на белый
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        GlStateManager.popMatrix();
    }

    private double interpolate(double prev, double current, float partialTicks) {
        return prev + (current - prev) * partialTicks;
    }

    /**
     * Улучшенная функция расчета масштаба с агрессивным увеличением на дальних дистанциях
     */
    private float calculateScale(double distance) {
        float scale = BASE_SCALE;

        if (distance <= MIN_DISTANCE) {
            return BASE_SCALE;
        }

        // 3-10 метров: плавное увеличение
        if (distance <= 10) {
            float distanceIncrease = (float)(distance - MIN_DISTANCE);
            scale += distanceIncrease * 0.002f;
        }
        // 10-20 метров: быстрее увеличиваем
        else if (distance <= 20) {
            scale += (10 - MIN_DISTANCE) * 0.002f; // базовая часть до 10м
            float distanceIncrease = (float)(distance - 10);
            scale += distanceIncrease * 0.0035f;
        }
        // 20-35 метров: ещё быстрее
        else if (distance <= 35) {
            scale += (10 - MIN_DISTANCE) * 0.002f; // до 10м
            scale += 10 * 0.0035f; // 10-20м
            float distanceIncrease = (float)(distance - 20);
            scale += distanceIncrease * 0.005f;
        }
        // 35-50 метров: очень агрессивное увеличение
        else if (distance <= 50) {
            scale += (10 - MIN_DISTANCE) * 0.002f; // до 10м
            scale += 10 * 0.0035f; // 10-20м
            scale += 15 * 0.005f; // 20-35м
            float distanceIncrease = (float)(distance - 35);
            scale += distanceIncrease * 0.0070f;
        }
        // 50+ метров: максимальное увеличение
        else {
            scale += (10 - MIN_DISTANCE) * 0.002f;
            scale += 10 * 0.0035f;
            scale += 15 * 0.005f;
            scale += 15 * 0.0070f;
            float distanceIncrease = (float)(distance - 50);
            scale += distanceIncrease * 0.0090f;
        }

        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));

        return scale;
    }

    /**
     * Рассчитывает прозрачность фона в зависимости от дистанции
     * На дальних дистанциях фон темнее для лучшей читаемости
     */
    private int calculateBackgroundAlpha(double distance) {
        if (distance < 10) {
            return 144; // 0x90
        } else if (distance < 20) {
            return 160; // 0xA0
        } else if (distance < 35) {
            return 176; // 0xB0
        } else {
            return 200; // 0xC8 - почти непрозрачный на дальних дистанциях
        }
    }

    /**
     * Рассчитывает прозрачность обводки в зависимости от дистанции
     */
    private int calculateOutlineAlpha(double distance) {
        if (distance < 10) {
            return 48; // 0x30
        } else if (distance < 20) {
            return 64; // 0x40
        } else if (distance < 35) {
            return 80; // 0x50
        } else {
            return 96; // 0x60 - ярче на дальних дистанциях
        }
    }

    private void drawModalRect(float x, float y, float width, float height,
                               int textureX, int textureY, int textureWidth, int textureHeight,
                               int mapWidth, int mapHeight, int color) {
        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8 & 255) / 255.0F;
        float b = (color & 255) / 255.0F;

        float u1 = textureX / (float) mapWidth;
        float u2 = (textureX + textureWidth) / (float) mapWidth;
        float v1 = textureY / (float) mapHeight;
        float v2 = (textureY + textureHeight) / (float) mapHeight;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        GlStateManager.color(r, g, b, a);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + height, 0).tex(u1, v2).endVertex();
        buffer.pos(x + width, y + height, 0).tex(u2, v2).endVertex();
        buffer.pos(x + width, y, 0).tex(u2, v1).endVertex();
        buffer.pos(x, y, 0).tex(u1, v1).endVertex();
        tessellator.draw();

        // ВАЖНО: Сброс цвета после рендера
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private int getPingColor(int ping) {
        if (ping < 100) {
            return 0xFF55FF55;
        } else if (ping < 150) {
            return 0xFFFFFF55;
        } else if (ping < 350) {
            return 0xFFFFAA00;
        } else {
            return 0xFFFF5555;
        }
    }

    private void drawRect(float left, float top, float right, float bottom, int color) {
        if (left > right) {
            float temp = left;
            left = right;
            right = temp;
        }

        if (top > bottom) {
            float temp = top;
            top = bottom;
            bottom = temp;
        }

        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8 & 255) / 255.0F;
        float b = (color & 255) / 255.0F;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        GlStateManager.disableTexture2D();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(left, bottom, 0.0D).color(r, g, b, a).endVertex();
        buffer.pos(right, bottom, 0.0D).color(r, g, b, a).endVertex();
        buffer.pos(right, top, 0.0D).color(r, g, b, a).endVertex();
        buffer.pos(left, top, 0.0D).color(r, g, b, a).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();

        // ВАЖНО: Сброс цвета
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void drawRoundedRect(float left, float top, float right, float bottom, float radius, int color) {
        drawRect(left + radius, top, right - radius, bottom, color);
        drawRect(left, top + radius, left + radius, bottom - radius, color);
        drawRect(right - radius, top + radius, right, bottom - radius, color);

        drawRect(left, top, left + radius, top + radius, color);
        drawRect(right - radius, top, right, top + radius, color);
        drawRect(left, bottom - radius, left + radius, bottom, color);
        drawRect(right - radius, bottom - radius, right, bottom, color);
    }

    private void drawRoundedRectOutline(float left, float top, float right, float bottom,
                                        float radius, float lineWidth, int color) {
        drawRect(left + radius, top, right - radius, top + lineWidth, color);
        drawRect(left + radius, bottom - lineWidth, right - radius, bottom, color);
        drawRect(left, top + radius, left + lineWidth, bottom - radius, color);
        drawRect(right - lineWidth, top + radius, right, bottom - radius, color);

        drawRect(left, top, left + radius, top + lineWidth, color);
        drawRect(left, top, left + lineWidth, top + radius, color);

        drawRect(right - radius, top, right, top + lineWidth, color);
        drawRect(right - lineWidth, top, right, top + radius, color);

        drawRect(left, bottom - radius, left + lineWidth, bottom, color);
        drawRect(left, bottom - lineWidth, left + radius, bottom, color);

        drawRect(right - radius, bottom - lineWidth, right, bottom, color);
        drawRect(right - lineWidth, bottom - radius, right, bottom, color);
    }
}