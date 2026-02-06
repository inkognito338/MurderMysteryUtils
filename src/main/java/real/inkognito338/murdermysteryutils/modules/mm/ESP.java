package real.inkognito338.murdermysteryutils.modules.mm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import real.inkognito338.murdermysteryutils.modules.Module;
import real.inkognito338.murdermysteryutils.modules.settings.Setting;
import real.inkognito338.murdermysteryutils.modules.settings.SettingType;
import real.inkognito338.murdermysteryutils.utils.MurderMysteryUtils;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SuppressWarnings("SpellCheckingInspection")
@SideOnly(Side.CLIENT)
public class ESP extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final MurderMysteryUtils mmUtils = new MurderMysteryUtils();

    public ESP() {
        super("ESP");

        this.addSetting(new Setting("Show Mode", SettingType.MODE, "All Roles", "All Roles", "Only Innocents", "Only Killer & Detective"));
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

        mmUtils.update();
        renderMurderMysteryESP(event.getPartialTicks());
    }

    private void renderMurderMysteryESP(float partialTicks) {
        String showMode = (String) getSettingByName("Show Mode").getValue();
        boolean fade = (boolean) getSettingByName("ESP Fade").getValue();
        boolean throughWalls = (boolean) getSettingByName("Through Walls").getValue();
        double lineWidth = (double) getSettingByName("Line Width").getValue();
        String mode = (String) getSettingByName("Mode").getValue();

        boolean npcESP = (boolean) getSettingByName("NPC ESP").getValue();
        boolean filterNPC = (boolean) getSettingByName("Filter NPC").getValue();
        boolean checkSize = (boolean) getSettingByName("Check Size").getValue();
        boolean checkTabInfo = (boolean) getSettingByName("Check Tab Info").getValue();

        float[] murdererColor = (float[]) getSettingByName("Murderer Color").getValue();
        float[] detectiveColor = (float[]) getSettingByName("Detective Color").getValue();
        float[] innocentColor = (float[]) getSettingByName("Innocent Color").getValue();
        float[] npcColor = (float[]) getSettingByName("NPC Color").getValue();

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
            if (player == null || player.isDead || !player.isEntityAlive()) {
                continue;
            }

            if (!(player instanceof AbstractClientPlayer)) continue;

            if (player.equals(mc.player)) continue;

            boolean isNPC = isNPC(player);

            if (isNPC) {
                if (npcESP) drawEntityBodyESP(player, npcColor[0], npcColor[1], npcColor[2], fade, mode, partialTicks);
                continue;
            }

            if (filterNPC && isNPC) continue;
            if (checkSize && !hasNormalSize(player)) continue;
            if (checkTabInfo && !hasTabInfo(player)) continue;

            String role = getPlayerRole(player);
            if (!shouldShowPlayer(role, showMode)) continue;

            float r, g, b;
            switch (role) {
                case "murderer":
                    r = murdererColor[0]; g = murdererColor[1]; b = murdererColor[2]; break;
                case "detective":
                    r = detectiveColor[0]; g = detectiveColor[1]; b = detectiveColor[2]; break;
                case "innocent":
                default:
                    r = innocentColor[0]; g = innocentColor[1]; b = innocentColor[2]; break;
            }

            drawEntityBodyESP(player, r, g, b, fade, mode, partialTicks);
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
        if (player.equals(mc.player)) return false;
        String rawDisplay = player.getDisplayName().getUnformattedText();
        String cleanDisplay = TextFormatting.getTextWithoutFormattingCodes(rawDisplay);
        return cleanDisplay != null && (cleanDisplay.matches("^NPC \\[\\d+\\]$") ||
                cleanDisplay.contains("NPC") || player.getGameProfile().getName().contains("NPC"));
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
        return mc.getConnection() != null &&
                mc.getConnection().getPlayerInfo(player.getUniqueID()) != null;
    }

    private String getPlayerRole(EntityPlayer player) {
        String playerName = player.getName();
        if (mmUtils.hasMurderer() && playerName.equals(mmUtils.getMurderer())) return "murderer";
        if (mmUtils.isDetective(playerName)) return "detective";
        return "innocent";
    }

    private boolean shouldShowPlayer(String role, String showMode) {
        switch (showMode) {
            case "All Roles": return true;
            case "Only Innocents": return "innocent".equals(role);
            case "Only Killer & Detective": return "murderer".equals(role) || "detective".equals(role);
            default: return true;
        }
    }

    private void drawEntityBodyESP(EntityPlayer player, float red, float green, float blue, boolean fade, String mode, float partialTicks) {
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

        switch (mode) {
            case "Box":
                drawOutlinedBoundingBox(bb, red, green, blue, alpha);
                drawFilledBoundingBox(bb, red, green, blue, alpha * 0.2f);
                break;
            case "Outline":
                drawOutlinedBoundingBox(bb, red, green, blue, alpha);
                break;
            case "Shader":
                drawShaderESP(bb, red, green, blue, alpha);
                break;
            default:
                drawOutlinedBoundingBox(bb, red, green, blue, alpha);
                break;
        }

        GlStateManager.popMatrix();
    }

    private void drawOutlinedBoundingBox(AxisAlignedBB bb, float red, float green, float blue, float alpha) {
        GlStateManager.color(red, green, blue, alpha);
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