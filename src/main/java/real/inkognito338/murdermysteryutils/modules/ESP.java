package real.inkognito338.murdermysteryutils.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;
import real.inkognito338.murdermysteryutils.utils.MurderAPI;
import real.inkognito338.murdermysteryutils.utils.PlayerListManager;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import real.inkognito338.murdermysteryutils.utils.NPCValidator;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

@SuppressWarnings("SpellCheckingInspection")
@SideOnly(Side.CLIENT)
public class ESP extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final MurderAPI murderAPI = MurderAPI.getInstance();

    public ESP() {
        super("ESP");

        this.addSetting(new Setting("Show Mode", SettingType.MODE, "All Roles", "All Roles", "Only Innocents", "Only Killer & Detective", "Only Killer", "Only Detective"));
        this.addSetting(new Setting("ESP Fade", SettingType.BOOLEAN, false));
        this.addSetting(new Setting("Line Width", SettingType.NUMBER, 2.0, 1.0, 5.0));
        this.addSetting(new Setting("Mode", SettingType.MODE, "Box", "Box", "Outline", "Shader"));
        this.addSetting(new Setting("Through Walls", SettingType.BOOLEAN, true));

        this.addSetting(new Setting("NPC ESP", SettingType.BOOLEAN, true));
        this.addSetting(new Setting("Filter NPC", SettingType.BOOLEAN, true));
        this.addSetting(new Setting("Check Size", SettingType.BOOLEAN, true));
        this.addSetting(new Setting("Check Tab Info", SettingType.BOOLEAN, true));

        this.addSetting(new Setting("Murderer Color", SettingType.COLOR, new float[]{1.0f, 0.0f, 0.0f}));
        this.addSetting(new Setting("Detective Color", SettingType.COLOR, new float[]{0.0f, 0.0f, 1.0f}));
        this.addSetting(new Setting("Innocent Color", SettingType.COLOR, new float[]{0.0f, 1.0f, 0.0f}));
        this.addSetting(new Setting("NPC Color", SettingType.COLOR, new float[]{0.3f, 0.8f, 1.0f}));

        // ── Friend indicator ────────────────────────────────────────────
        // "Outline"  — доп. рамка поверх обычного ESP-бокса роли (роль остаётся видна по цвету).
        // "Priority" — друг рендерится обычным ESP (тем же Mode: Box/Outline/Shader),
        //              но вместо цвета роли используется цвет друга — друг визуально выше любой роли.
        this.addSetting(new Setting("Friend Indicator", SettingType.MODE, "Outline", "None", "Outline", "Priority"));
        this.addSetting(new Setting("Friend Color", SettingType.COLOR, new float[]{1.0f, 1.0f, 0.0f}));
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
        renderMurderMysteryESP(event.getPartialTicks());
    }

    private void renderMurderMysteryESP(float partialTicks) {
        String showMode = (String) getSettingByName("Show Mode").getValue();
        boolean fade = (boolean) getSettingByName("ESP Fade").getValue();
        boolean throughWalls = (boolean) getSettingByName("Through Walls").getValue();
        double lineWidth = (double) getSettingByName("Line Width").getValue();
        String mode = (String) getSettingByName("Mode").getValue();

        boolean npcESP = (boolean) getSettingByName("NPC ESP").getValue();
        boolean checkSize = (boolean) getSettingByName("Check Size").getValue();
        boolean checkTabInfo = (boolean) getSettingByName("Check Tab Info").getValue();

        float[] murdererColor = (float[]) getSettingByName("Murderer Color").getValue();
        float[] detectiveColor = (float[]) getSettingByName("Detective Color").getValue();
        float[] innocentColor = (float[]) getSettingByName("Innocent Color").getValue();
        float[] npcColor = (float[]) getSettingByName("NPC Color").getValue();

        String friendIndicator = (String) getSettingByName("Friend Indicator").getValue();
        float[] friendColor = (float[]) getSettingByName("Friend Color").getValue();

        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.disableLighting();
        GL11.glLineWidth((float) lineWidth);

        if (throughWalls) {
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
        } else {
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
        }

        for (EntityPlayer player : mc.world.playerEntities.toArray(new EntityPlayer[0])) {
            if (player == null || player.isDead || !player.isEntityAlive()) continue;
            if (!(player instanceof AbstractClientPlayer)) continue;
            if (player.equals(mc.player)) continue;

            boolean isNPC = isNPC(player);

            if (isNPC) {
                if (npcESP) {
                    drawEntityBodyESP(player, npcColor[0], npcColor[1], npcColor[2], fade, mode, partialTicks,
                            false, "None", friendColor);
                }
                continue;
            }

            if (checkSize && !hasNormalSize(player)) continue;
            if (checkTabInfo && !hasTabInfo(player)) continue;

            String role = getPlayerRole(player);
            if (!shouldShowPlayer(role, showMode)) continue;

            boolean isFriend = PlayerListManager.isFriend(player.getName());

            float r, g, b;
            if (isFriend && "Priority".equals(friendIndicator)) {
                // друг важнее роли — используем его цвет вместо цвета роли
                r = friendColor[0]; g = friendColor[1]; b = friendColor[2];
            } else {
                switch (role) {
                    case "murderer":
                        r = murdererColor[0]; g = murdererColor[1]; b = murdererColor[2];
                        break;
                    case "detective":
                        r = detectiveColor[0]; g = detectiveColor[1]; b = detectiveColor[2];
                        break;
                    case "innocent":
                    default:
                        r = innocentColor[0]; g = innocentColor[1]; b = innocentColor[2];
                        break;
                }
            }

            drawEntityBodyESP(player, r, g, b, fade, mode, partialTicks, isFriend, friendIndicator, friendColor);
        }

        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GL11.glLineWidth(1.0f);

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        GlStateManager.popAttrib();
        GlStateManager.popMatrix();
    }

    private boolean isNPC(EntityPlayer player) {
        return NPCValidator.isNPC(player);
    }

    private boolean hasNormalSize(EntityPlayer player) {
        float width = player.width;
        float height = player.height;
        float eps = 0.05F;
        return Math.abs(width - 0.6F) <= eps &&
                (Math.abs(height - 1.8F) <= eps ||
                        Math.abs(height - 1.65F) <= eps ||
                        Math.abs(height - 1.5F) <= eps);
    }

    private boolean hasTabInfo(EntityPlayer player) {
        if (mc.getConnection() == null) return false;
        mc.getConnection().getPlayerInfo(player.getUniqueID());
        return true;
    }

    private String getPlayerRole(EntityPlayer player) {
        String playerName = player.getName();

        if (murderAPI.isMurderer(playerName)) return "murderer";
        if (murderAPI.isDetective(playerName)) return "detective";
        return "innocent";
    }

    private boolean shouldShowPlayer(String role, String showMode) {
        switch (showMode) {
            case "Only Innocents": return "innocent".equals(role);
            case "Only Killer & Detective": return "murderer".equals(role) || "detective".equals(role);
            case "Only Killer": return "murderer".equals(role);
            case "Only Detective": return "detective".equals(role);
            default: return true;
        }
    }

    private void drawEntityBodyESP(EntityPlayer player, float red, float green, float blue, boolean fade, String mode,
                                   float partialTicks, boolean isFriend, String friendIndicator, float[] friendColor) {
        if (player == null || player.equals(mc.player) || player.isDead || !player.isEntityAlive()) return;

        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks - mc.getRenderManager().viewerPosX;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks - mc.getRenderManager().viewerPosY;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks - mc.getRenderManager().viewerPosZ;

        if (Math.abs(x) > 10000 || Math.abs(y) > 10000 || Math.abs(z) > 10000) return;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);

        float alpha = fade ? Math.max(0.1f, 1.0f - mc.player.getDistance(player) / 50f) : 0.8f;
        AxisAlignedBB bb = new AxisAlignedBB(
                -0.5, 0, -0.5,
                0.5, player.height + 0.4, 0.5
        );

        // ── Основной ESP-бокс ─────────────────────────────────────────────
        // Цвет (red, green, blue) уже учитывает приоритет друга над ролью,
        // если Friend Indicator == "Priority" (см. renderMurderMysteryESP).
        switch (mode) {
            case "Box":
                drawOutlinedBoundingBox(bb, red, green, blue, alpha);
                drawFilledBoundingBox(bb, red, green, blue, alpha * 0.2f);
                break;
            case "Shader":
                drawShaderESP(bb, red, green, blue, alpha);
                break;
            default:
                drawOutlinedBoundingBox(bb, red, green, blue, alpha);
                break;
        }

        // ── Индикатор друга: доп. рамка (не заменяет бокс, а дополняет) ──
        // При "Priority" отдельная рамка не нужна — цвет друга уже применён к самому боксу.
        if (isFriend && "Outline".equals(friendIndicator)) {
            drawFriendOutline(bb, friendColor);
        }

        GlStateManager.popMatrix();
    }

    // ── Доп. рамка вокруг бокса для режима "Outline" ────────────────────
    private void drawFriendOutline(AxisAlignedBB bb, float[] friendColor) {
        double grow = 0.05;
        AxisAlignedBB outerBb = bb.grow(grow);
        GlStateManager.color(friendColor[0], friendColor[1], friendColor[2], 1.0f);
        GL11.glLineWidth(3.0f);
        drawOutlinedBoundingBoxNoColor(outerBb);
        GL11.glLineWidth((float) (double) getSettingByName("Line Width").getValue());
    }

    private void drawOutlinedBoundingBox(AxisAlignedBB bb, float red, float green, float blue, float alpha) {
        GlStateManager.color(red, green, blue, alpha);
        drawOutlinedBoundingBoxNoColor(bb);
    }

    // Версия без установки цвета — чтобы можно было переиспользовать с уже выставленным GlStateManager.color(...)
    private void drawOutlinedBoundingBoxNoColor(AxisAlignedBB bb) {
        GL11.glBegin(GL11.GL_LINES);

        GL11.glVertex3d(bb.minX, bb.minY, bb.minZ); GL11.glVertex3d(bb.maxX, bb.minY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.minZ); GL11.glVertex3d(bb.maxX, bb.minY, bb.maxZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.maxZ); GL11.glVertex3d(bb.minX, bb.minY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.minY, bb.maxZ); GL11.glVertex3d(bb.minX, bb.minY, bb.minZ);

        GL11.glVertex3d(bb.minX, bb.maxY, bb.minZ); GL11.glVertex3d(bb.maxX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.minZ); GL11.glVertex3d(bb.maxX, bb.maxY, bb.maxZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.maxZ); GL11.glVertex3d(bb.minX, bb.maxY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.maxY, bb.maxZ); GL11.glVertex3d(bb.minX, bb.maxY, bb.minZ);

        GL11.glVertex3d(bb.minX, bb.minY, bb.minZ); GL11.glVertex3d(bb.minX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.minZ); GL11.glVertex3d(bb.maxX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.maxZ); GL11.glVertex3d(bb.maxX, bb.maxY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.minY, bb.maxZ); GL11.glVertex3d(bb.minX, bb.maxY, bb.maxZ);

        GL11.glEnd();
    }

    private void drawFilledBoundingBox(AxisAlignedBB bb, float red, float green, float blue, float alpha) {
        GlStateManager.color(red, green, blue, alpha);
        GL11.glBegin(GL11.GL_QUADS);

        GL11.glVertex3d(bb.minX, bb.minY, bb.minZ); GL11.glVertex3d(bb.maxX, bb.minY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.maxZ); GL11.glVertex3d(bb.minX, bb.minY, bb.maxZ);

        GL11.glVertex3d(bb.minX, bb.maxY, bb.minZ); GL11.glVertex3d(bb.maxX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.maxZ); GL11.glVertex3d(bb.minX, bb.maxY, bb.maxZ);

        GL11.glVertex3d(bb.minX, bb.minY, bb.minZ); GL11.glVertex3d(bb.minX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.minZ); GL11.glVertex3d(bb.maxX, bb.minY, bb.minZ);

        GL11.glVertex3d(bb.minX, bb.minY, bb.maxZ); GL11.glVertex3d(bb.maxX, bb.minY, bb.maxZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.maxZ); GL11.glVertex3d(bb.minX, bb.maxY, bb.maxZ);

        GL11.glEnd();
    }

    private void drawShaderESP(AxisAlignedBB bb, float red, float green, float blue, float alpha) {
        drawOutlinedBoundingBox(bb, red, green, blue, alpha);
        double shrink = 0.1;
        AxisAlignedBB inner = new AxisAlignedBB(
                bb.minX + shrink, bb.minY + shrink, bb.minZ + shrink,
                bb.maxX - shrink, bb.maxY - shrink, bb.maxZ - shrink
        );
        drawFilledBoundingBox(inner, red, green, blue, alpha * 0.1f);
    }
}