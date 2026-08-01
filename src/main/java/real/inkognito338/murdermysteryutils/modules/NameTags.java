package real.inkognito338.murdermysteryutils.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.MurderAPI;
import real.inkognito338.murdermysteryutils.utils.NPCValidator;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;

import java.text.DecimalFormat;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */
@SideOnly(Side.CLIENT)
public class NameTags extends Module {

    private static final DecimalFormat PING_FORMAT = new DecimalFormat("#");
    private static final float BASE_SCALE = 0.045f;
    private static final float MIN_SCALE = 0.035f;
    private static final float MAX_SCALE = 0.22f;
    private static final float MIN_DISTANCE = 3f;
    private static final float ROUNDED_RECT_RADIUS = 2.5f;
    private static final float ROUNDED_RECT_LINE_WIDTH = 0.6f;
    private static final int SKIN_TEXTURE_Y = 8;
    private static final int SKIN_TEXTURE_SIZE = 8;
    private static final int SKIN_MAP_SIZE = 64;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final MurderAPI murderAPI = MurderAPI.getInstance();

    // ================= Настройки модуля =================
    private final Setting roleMode = new Setting("Role Mode", SettingType.MODE, "Full", "Full", "Short", "Hidden");
    private final Setting colorMode = new Setting("Color Mode", SettingType.MODE, "Role Text", "Role Text", "Nickname", "Background", "Outline");
    private final Setting showPing = new Setting("Show Ping", SettingType.BOOLEAN, true);
    private final Setting showHead = new Setting("Show Head", SettingType.BOOLEAN, true);
    private final Setting throughWalls = new Setting("Through Walls", SettingType.BOOLEAN, false);

    public NameTags() {
        super("NameTags");
        addSetting(roleMode);
        addSetting(colorMode);
        addSetting(showPing);
        addSetting(showHead);
        addSetting(throughWalls);
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

        // Считываем исходные системные состояния OpenGL перед рендером
        boolean isBlendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean isLightingEnabled = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean isDepthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean isCullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        for (EntityPlayer player : mc.world.playerEntities.toArray(new EntityPlayer[0])) {
            if (player == null || player.isDead || !player.isEntityAlive()) continue;
            if (!(player instanceof AbstractClientPlayer)) continue;
            if (player.equals(mc.player)) continue;
            if (isNPC(player)) continue;

            renderPlayerName(player, event.getPartialTicks());
        }

        // Мягкий и безопасный ручной сброс глобальных состояний через кэш Minecraft (GlStateManager)
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.bindTexture(0);

        if (isLightingEnabled) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
        if (isBlendEnabled) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
        if (isDepthEnabled) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
        if (isCullEnabled) GlStateManager.enableCull(); else GlStateManager.disableCull();
        GlStateManager.depthMask(true);
    }

    private boolean isNPC(EntityPlayer player) {
        return NPCValidator.isNPC(player);
    }

    private String getPlayerRole(EntityPlayer player) {
        String playerName = player.getName();
        if (murderAPI.isMurderer(playerName)) return "murderer";
        if (murderAPI.isDetective(playerName)) return "detective";
        return "innocent";
    }

    private String getRoleDisplayText(String role) {
        String mode = (String) roleMode.getValue();
        if (mode.equalsIgnoreCase("Hidden")) return "";

        if (mode.equalsIgnoreCase("Short")) {
            switch (role) {
                case "murderer": return "[M]";
                case "detective": return "[D]";
                default: return "[I]";
            }
        }

        switch (role) {
            case "murderer": return "[Убийца]";
            case "detective": return "[Детектив]";
            default: return "[Мирный]";
        }
    }

    private int getRoleColor(String role) {
        switch (role) {
            case "murderer": return 0xFFFF5555;
            case "detective": return 0xFF5555FF;
            default: return 0xFF55FF55;
        }
    }

    private void renderPlayerName(EntityPlayer entity, float partialTicks) {
        String name = entity.getName();
        if (name.isEmpty()) return;

        NetworkPlayerInfo playerInfo = mc.getConnection() != null
                ? mc.getConnection().getPlayerInfo(entity.getUniqueID())
                : null;
        if (playerInfo == null) return;

        FontRenderer fontRenderer = mc.fontRenderer;

        double x = interpolate(entity.prevPosX, entity.posX, partialTicks) - mc.getRenderManager().viewerPosX;
        double y = interpolate(entity.prevPosY, entity.posY, partialTicks) - mc.getRenderManager().viewerPosY + entity.height + 0.7;
        double z = interpolate(entity.prevPosZ, entity.posZ, partialTicks) - mc.getRenderManager().viewerPosZ;

        double distance = mc.player.getDistance(entity);

        float scale = calculateScale(distance);

        String role = getPlayerRole(entity);
        String roleText = getRoleDisplayText(role);
        int roleColor = getRoleColor(role);

        boolean renderPing = (Boolean) showPing.getValue();
        String pingText = renderPing ? PING_FORMAT.format(playerInfo.getResponseTime()) + "ms" : "";

        boolean renderHead = (Boolean) showHead.getValue();

        float padding = 3.0f;
        float textSpacing = 4.0f;
        float shadowOffset = 1.0f;
        float headSpacing = renderHead ? 2.5f : 0.0f;
        float headSize = renderHead ? 10.0f : 0.0f;

        int nameWidth = fontRenderer.getStringWidth(name);
        int roleWidth = roleText.isEmpty() ? 0 : fontRenderer.getStringWidth(roleText);
        int pingWidth = pingText.isEmpty() ? 0 : fontRenderer.getStringWidth(pingText);

        float totalTextWidth = nameWidth + shadowOffset;
        if (!roleText.isEmpty()) totalTextWidth += textSpacing + roleWidth;
        if (!pingText.isEmpty()) totalTextWidth += textSpacing + pingWidth;

        float backgroundWidth = headSize + headSpacing + totalTextWidth + padding * 2;
        float backgroundHeight = Math.max(10.0f, fontRenderer.FONT_HEIGHT * 1.05f) + padding * 2;

        float startX = -backgroundWidth / 2f;
        float startY = -backgroundHeight / 2f - 2;

        String currentColorMode = (String) colorMode.getValue();
        int finalNameColor = 0xFFFFFFFF;
        int finalRoleColor = roleColor;

        int configAlpha = 144;
        int backgroundColor = configAlpha << 24;
        int outlineAlpha = calculateOutlineAlpha(distance);
        int outlineColor = (outlineAlpha << 24) | 0xFFFFFF;

        if (currentColorMode.equalsIgnoreCase("Nickname")) {
            finalNameColor = roleColor;
            finalRoleColor = 0xFFCCCCCC;
        } else if (currentColorMode.equalsIgnoreCase("Background")) {
            backgroundColor = (configAlpha << 24) | (roleColor & 0x00FFFFFF);
        } else if (currentColorMode.equalsIgnoreCase("Outline")) {
            outlineColor = (outlineAlpha << 24) | (roleColor & 0x00FFFFFF);
        }

        // --- ТОЛЬКО ТРАНСФОРМАЦИЯ МАТРИЦЫ ---
        GlStateManager.pushMatrix();

        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, mc.gameSettings.thirdPersonView == 2 ? -1.0F : 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-scale, -scale, scale);

        boolean isThroughWalls = (Boolean) throughWalls.getValue();
        if (isThroughWalls) {
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
        } else {
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
        }

        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );
        GlStateManager.enableTexture2D();
        GlStateManager.disableCull();

        drawRoundedRect(startX, startY, startX + backgroundWidth, startY + backgroundHeight, backgroundColor);

        float currentX = startX + padding;
        float centerYOffset = startY + (backgroundHeight - fontRenderer.FONT_HEIGHT) / 2f;

        if (renderHead) {
            float headY = startY + (backgroundHeight - headSize) / 2f;
            ResourceLocation skin = playerInfo.getLocationSkin();
            mc.getTextureManager().bindTexture(skin);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

            drawHeadRect(currentX + 0.5f, headY + 0.5f, headSize, 8, 0x40000000);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            drawHeadRect(currentX, headY, headSize, 8, 0xFFFFFFFF);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            drawHeadRect(currentX, headY, headSize, 40, 0xFFFFFFFF);

            currentX += headSize + headSpacing;
        }

        // Рендер Ника
        fontRenderer.drawString(name, currentX, centerYOffset, finalNameColor, true);
        currentX += nameWidth;

        // Рендер Роли
        if (!roleText.isEmpty()) {
            currentX += textSpacing;
            fontRenderer.drawString(roleText, currentX, centerYOffset, finalRoleColor, true);
            currentX += roleWidth;
        }

        // Рендер Пинга
        if (renderPing) {
            currentX += textSpacing;
            int ping = playerInfo.getResponseTime();
            int pingColor = getPingColor(ping);
            fontRenderer.drawString(pingText, currentX, centerYOffset, pingColor, true);
        }

        // --- АККУРАТНЫЙ СБРОС ИЗМЕНЕННЫХ ПАРАМЕТРОВ ---
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.bindTexture(0);

        GlStateManager.popMatrix();
    }

    private void drawHeadRect(float x, float y, float size, int textureX, int color) {
        drawModalRect(x, y, size, size, textureX, color);
    }

    private double interpolate(double prev, double current, float partialTicks) {
        return prev + (current - prev) * partialTicks;
    }

    private float calculateScale(double distance) {
        float scale = BASE_SCALE;
        if (distance <= MIN_DISTANCE) return BASE_SCALE;

        if (distance <= 10) {
            scale += (float) (distance - MIN_DISTANCE) * 0.002f;
        } else if (distance <= 20) {
            scale += (10 - MIN_DISTANCE) * 0.002f + (float) (distance - 10) * 0.0035f;
        } else if (distance <= 35) {
            scale += (10 - MIN_DISTANCE) * 0.002f + 10 * 0.0035f + (float) (distance - 20) * 0.005f;
        } else if (distance <= 50) {
            scale += (10 - MIN_DISTANCE) * 0.002f + 10 * 0.0035f + 15 * 0.005f + (float) (distance - 35) * 0.0070f;
        } else {
            scale += (10 - MIN_DISTANCE) * 0.002f + 10 * 0.0035f + 15 * 0.005f + 15 * 0.0070f + (float) (distance - 50) * 0.0090f;
        }
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }

    private int calculateOutlineAlpha(double distance) {
        if (distance < 10) return 48;
        if (distance < 20) return 64;
        if (distance < 35) return 80;
        return 96;
    }

    private void drawModalRect(float x, float y, float width, float height, int textureX, int color) {
        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8 & 255) / 255.0F;
        float b = (color & 255) / 255.0F;

        float u1 = textureX / (float) SKIN_MAP_SIZE;
        float u2 = (textureX + SKIN_TEXTURE_SIZE) / (float) SKIN_MAP_SIZE;
        float v1 = SKIN_TEXTURE_Y / (float) SKIN_MAP_SIZE;
        float v2 = (SKIN_TEXTURE_Y + SKIN_TEXTURE_SIZE) / (float) SKIN_MAP_SIZE;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        GlStateManager.color(r, g, b, a);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + height, 0).tex(u1, v2).endVertex();
        buffer.pos(x + width, y + height, 0).tex(u2, v2).endVertex();
        buffer.pos(x + width, y, 0).tex(u2, v1).endVertex();
        buffer.pos(x, y, 0).tex(u1, v1).endVertex();
        tessellator.draw();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private int getPingColor(int ping) {
        if (ping < 100) return 0xFF55FF55;
        if (ping < 150) return 0xFFFFFF55;
        if (ping < 350) return 0xFFFFAA00;
        return 0xFFFF5555;
    }

    private void drawRect(float left, float top, float right, float bottom, int color) {
        if (left > right) { float t = left; left = right; right = t; }
        if (top > bottom) { float t = top; top = bottom; bottom = t; }

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
    }

    private void drawRoundedRect(float left, float top, float right, float bottom, int color) {
        float r = ROUNDED_RECT_RADIUS;
        drawRect(left + r, top, right - r, bottom, color);
        drawRect(left, top + r, left + r, bottom - r, color);
        drawRect(right - r, top + r, right, bottom - r, color);
        drawRect(left, top, left + r, top + r, color);
        drawRect(right - r, top, right, top + r, color);
        drawRect(left, bottom - r, left + r, bottom, color);
        drawRect(right - r, bottom - r, right, bottom, color);
    }

    private void drawRoundedRectOutline(float left, float top, float right, float bottom, int color) {
        float r = ROUNDED_RECT_RADIUS;
        float lw = ROUNDED_RECT_LINE_WIDTH;
        drawRect(left + r, top, right - r, top + lw, color);
        drawRect(left + r, bottom - lw, right - r, bottom, color);
        drawRect(left, top + r, left + lw, bottom - r, color);
        drawRect(right - lw, top + r, right, bottom - r, color);
        drawRect(left, top, left + r, top + lw, color);
        drawRect(left, top, left + lw, top + r, color);
        drawRect(right - r, top, right, top + lw, color);
        drawRect(right - lw, top, right, top + r, color);
        drawRect(left, bottom - r, left + lw, bottom, color);
        drawRect(left, bottom - lw, left + r, bottom, color);
        drawRect(right - r, bottom - lw, right, bottom, color);
        drawRect(right - lw, bottom - r, right, bottom, color);
    }
}