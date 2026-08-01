package real.inkognito338.murdermysteryutils.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;
import real.inkognito338.murdermysteryutils.utils.MurderAPI;
import real.inkognito338.murdermysteryutils.utils.NPCValidator;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

@SuppressWarnings("SpellCheckingInspection")
@SideOnly(Side.CLIENT)
public class Tracers extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final MurderAPI murderAPI = MurderAPI.getInstance();

    public Tracers() {
        super("Tracers");

        this.addSetting(new Setting("Show Mode", SettingType.MODE, "All Roles", "All Roles", "Only Innocents", "Only Killer & Detective", "Only Killer", "Only Detective"));
        this.addSetting(new Setting("Tracer Fade", SettingType.BOOLEAN, false));
        this.addSetting(new Setting("Line Width", SettingType.NUMBER, 2.0, 1.0, 5.0));
        this.addSetting(new Setting("Through Walls", SettingType.BOOLEAN, true));

        this.addSetting(new Setting("NPC Tracers", SettingType.BOOLEAN, true));
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
        super.onEnable();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.getRenderManager().options == null) return;
        renderTracers(event.getPartialTicks());
    }

    private void renderTracers(float partialTicks) {
        String showMode = (String) getSettingByName("Show Mode").getValue();
        boolean fade = (boolean) getSettingByName("Tracer Fade").getValue();
        boolean throughWalls = (boolean) getSettingByName("Through Walls").getValue();
        float lineWidth = ((Double) getSettingByName("Line Width").getValue()).floatValue();

        boolean npcTracers = (boolean) getSettingByName("NPC Tracers").getValue();
        boolean filterNPC = (boolean) getSettingByName("Filter NPC").getValue();
        boolean checkSize = (boolean) getSettingByName("Check Size").getValue();
        boolean checkTabInfo = (boolean) getSettingByName("Check Tab Info").getValue();

        float[] murdererColor = (float[]) getSettingByName("Murderer Color").getValue();
        float[] detectiveColor = (float[]) getSettingByName("Detective Color").getValue();
        float[] innocentColor = (float[]) getSettingByName("Innocent Color").getValue();
        float[] npcColor = (float[]) getSettingByName("NPC Color").getValue();

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();

        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
            GL11.glLineWidth(lineWidth);

            if (throughWalls) {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(false);
            }

            for (EntityPlayer player : mc.world.playerEntities.toArray(new EntityPlayer[0])) {
                if (player == null || player.isDead || !player.isEntityAlive()) continue;
                if (!(player instanceof AbstractClientPlayer)) continue;
                if (player.equals(mc.player)) continue;

                if (filterNPC) {
                    boolean isNPC = NPCValidator.isNPC(player);
                    if (isNPC) {
                        if (npcTracers) {
                            drawTracer(player, npcColor[0], npcColor[1], npcColor[2],
                                    computeAlpha(player, fade), partialTicks);
                        }
                        continue;
                    }
                }

                if (checkSize && !hasNormalSize(player)) continue;
                if (checkTabInfo && !hasTabInfo(player)) continue;

                String role = getPlayerRole(player);
                if (!shouldShowPlayer(role, showMode)) continue;

                float r, g, b;
                switch (role) {
                    case "murderer":
                        r = murdererColor[0];
                        g = murdererColor[1];
                        b = murdererColor[2];
                        break;
                    case "detective":
                        r = detectiveColor[0];
                        g = detectiveColor[1];
                        b = detectiveColor[2];
                        break;
                    default:
                        r = innocentColor[0];
                        g = innocentColor[1];
                        b = innocentColor[2];
                        break;
                }

                drawTracer(player, r, g, b, computeAlpha(player, fade), partialTicks);
            }

        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib(); // Это полностью восстановит всё состояние, включая DEPTH_TEST и DepthMask
        }
    }

    private void drawTracer(EntityPlayer player, float r, float g, float b,
                            float alpha, float partialTicks) {
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks
                - mc.getRenderManager().viewerPosX;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks
                - mc.getRenderManager().viewerPosY
                + player.height * 0.5;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks
                - mc.getRenderManager().viewerPosZ;

        if (Math.abs(x) > 10000 || Math.abs(y) > 10000 || Math.abs(z) > 10000) return;

        GL11.glBegin(GL11.GL_LINES);
        GL11.glColor4f(r, g, b, alpha);
        GL11.glVertex3d(0, 0, 0);
        GL11.glVertex3d(x, y, z);
        GL11.glEnd();
    }

    private float computeAlpha(EntityPlayer player, boolean fade) {
        return fade ? Math.max(0.1f, 1.0f - mc.player.getDistance(player) / 50f) : 0.8f;
    }

    private boolean hasNormalSize(EntityPlayer player) {
        float eps = 0.05F;
        return Math.abs(player.width - 0.6F) <= eps &&
                (Math.abs(player.height - 1.8F) <= eps ||
                        Math.abs(player.height - 1.65F) <= eps ||
                        Math.abs(player.height - 1.5F) <= eps);
    }

    private boolean hasTabInfo(EntityPlayer player) {
        if (mc.getConnection() == null) return false;
        mc.getConnection().getPlayerInfo(player.getUniqueID());
        return true;
    }

    private String getPlayerRole(EntityPlayer player) {
        String name = player.getName();
        if (murderAPI.isMurderer(name)) return "murderer";
        if (murderAPI.isDetective(name)) return "detective";
        return "innocent";
    }

    private boolean shouldShowPlayer(String role, String showMode) {
        switch (showMode) {
            case "Only Innocents":
                return "innocent".equals(role);
            case "Only Killer & Detective":
                return "murderer".equals(role) || "detective".equals(role);
            case "Only Killer":
                return "murderer".equals(role);
            case "Only Detective":
                return "detective".equals(role);
            default:
                return true;
        }
    }
}