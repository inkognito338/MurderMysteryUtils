package real.inkognito338.murdermysteryutils.modules;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;
import real.inkognito338.murdermysteryutils.utils.MurderAPI;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import net.minecraft.network.datasync.DataParameter;
import java.lang.reflect.Field;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

@SuppressWarnings("GrazieInspection")
@SideOnly(Side.CLIENT)
public class HUD extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final MurderAPI murderAPI = MurderAPI.getInstance();
    private static final Logger LOGGER = LogManager.getLogger();

    // Единый источник правды для размеров HUD и отступа от края экрана.
    // Используется и здесь, и в SettingsGUI (редактор позиции), чтобы
    // рамка превью, зона клика и реальный рендер всегда совпадали.
    public static final int HUD_WIDTH = 170;
    public static final int HUD_HEIGHT = 105;
    public static final int HUD_EDGE_MARGIN = 6;

    private final Map<UUID, AbstractClientPlayer> fakePlayerCache = new HashMap<>();
    private static DataParameter<Byte> PLAYER_MODEL_FLAG_PARAM = null;

    static {
        String[] fieldNames = {"PLAYER_MODEL_FLAG", "field_184827_bp"};
        for (String fieldName : fieldNames) {
            try {
                Field f = EntityPlayer.class.getDeclaredField(fieldName);
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                DataParameter<Byte> param = (DataParameter<Byte>) f.get(null);
                PLAYER_MODEL_FLAG_PARAM = param;
                break;
            } catch (NoSuchFieldException ignored) {
            } catch (Exception e) {
                LogManager.getLogger().error("Failed to reflect PLAYER_MODEL_FLAG", e);
                break;
            }
        }
    }

    public HUD() {
        super("HUD");
        this.addSetting(new Setting("Show Distance", SettingType.BOOLEAN, true));
        // Дефолт подобран так, чтобы после клампинга с отступом HUD не прижимался
        // вплотную к правому краю экрана.
        this.addSetting(new Setting("HUD Position", SettingType.HUD_POSITION, new float[]{0.78f, 0.043222003f}));
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        fakePlayerCache.clear();
    }

    public float getHudXPercent() {
        Setting setting = getSettingByName("HUD Position");
        if (setting != null && setting.getValue() instanceof float[]) {
            return ((float[]) setting.getValue())[0];
        }
        return 0.78f;
    }

    public float getHudYPercent() {
        Setting setting = getSettingByName("HUD Position");
        if (setting != null && setting.getValue() instanceof float[]) {
            return ((float[]) setting.getValue())[1];
        }
        return 0.05f;
    }

    public void setHudPosition(float xPercent, float yPercent) {
        Setting setting = getSettingByName("HUD Position");
        if (setting != null) {
            setting.setValue(new float[]{xPercent, yPercent});
        }
    }

    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!isToggled()) return;

        if (mc.gameSettings.hideGUI) return;

        Set<String> murderers = murderAPI.getMurderers();
        Set<String> detectives = murderAPI.getDetectives();

        String murdererName = getClosestOnServer(murderers);
        String detectiveName = getClosestOnServer(detectives);

        AbstractClientPlayer murderer = findPlayerByName(murdererName);
        AbstractClientPlayer detective = findPlayerByName(detectiveName);

        AbstractClientPlayer murdererRender = getRenderablePlayer(murdererName, murderer);
        AbstractClientPlayer detectiveRender = getRenderablePlayer(detectiveName, detective);

        ScaledResolution sr = new ScaledResolution(mc);

        int[] clampedPos = getClampedHudPosition(sr);
        int x = clampedPos[0];
        int y = clampedPos[1];

        drawTable(x, y, murderer, detective, murdererRender, detectiveRender, murdererName, detectiveName);
    }

    private String getClosestOnServer(Set<String> names) {
        if (names == null || names.isEmpty() || mc.player == null) return null;

        String closestName = null;
        double closestDistanceSq = Double.MAX_VALUE;
        String fallbackName = null;

        for (String name : names) {
            if (!isPlayerOnServer(name)) continue;

            AbstractClientPlayer player = findPlayerByName(name);

            if (player != null) {
                double distanceSq = mc.player.getDistanceSq(player);
                if (distanceSq < closestDistanceSq) {
                    closestDistanceSq = distanceSq;
                    closestName = name;
                }
            } else if (fallbackName == null) {
                fallbackName = name;
            }
        }

        return closestName != null ? closestName : fallbackName;
    }

    private NetworkPlayerInfo getPlayerInfo(String name) {
        if (name == null || mc.getConnection() == null) return null;
        for (NetworkPlayerInfo info : mc.getConnection().getPlayerInfoMap()) {
            if (info.getGameProfile().getName().equals(name)) {
                return info;
            }
        }
        return null;
    }

    private boolean isPlayerOnServer(String name) {
        return getPlayerInfo(name) != null;
    }

    private AbstractClientPlayer findPlayerByName(String name) {
        if (name == null || mc.world == null) return null;

        for (EntityPlayer player : mc.world.playerEntities) {
            if (player instanceof AbstractClientPlayer && player.getName().equals(name)) {
                return (AbstractClientPlayer) player;
            }
        }
        return null;
    }

    private AbstractClientPlayer getRenderablePlayer(String name, AbstractClientPlayer real) {
        if (name == null || mc.world == null || mc.player == null) return null;

        NetworkPlayerInfo info = getPlayerInfo(name);
        if (info == null) return null;

        GameProfile profile = info.getGameProfile();

        AbstractClientPlayer fake = fakePlayerCache.computeIfAbsent(
                profile.getId(),
                id -> new EntityOtherPlayerMP(mc.world, profile)
        );

        if (real != null && PLAYER_MODEL_FLAG_PARAM != null) {
            byte modelFlags = real.getDataManager().get(PLAYER_MODEL_FLAG_PARAM);
            fake.getDataManager().set(PLAYER_MODEL_FLAG_PARAM, modelFlags);
        }

        double px = mc.player.posX;
        double py = mc.player.posY;
        double pz = mc.player.posZ;

        fake.setPositionAndRotation(px, py, pz, 0.0F, 0.0F);
        fake.prevPosX = fake.lastTickPosX = px;
        fake.prevPosY = fake.lastTickPosY = py;
        fake.prevPosZ = fake.lastTickPosZ = pz;

        if (real != null) return real;
        return fake;
    }

    private void drawTable(int x, int y, AbstractClientPlayer murderer, AbstractClientPlayer detective,
                           AbstractClientPlayer murdererRender, AbstractClientPlayer detectiveRender,
                           String murdererName, String detectiveName) {
        try {
            int width = 160;
            int height = 100;
            int padding = 5;

            boolean showDistance = (boolean) getSettingByName("Show Distance").getValue();

            Gui.drawRect(x - padding, y - padding, x + width + padding, y + height + padding, 0x90000000);

            int nameYOffset = 2;
            int distanceYOffset = 12;

            if (murdererName != null) {
                int nameWidth = mc.fontRenderer.getStringWidth(murdererName);
                int nameX = x + (width / 4 - nameWidth / 2);
                int nameY = y + nameYOffset;
                mc.fontRenderer.drawStringWithShadow(murdererName, nameX, nameY, 0xFF0000);

                if (murderer != null && showDistance) {
                    double distance = mc.player.getDistance(murderer);
                    String distanceText = String.format("(%.1fm)", distance);
                    int distanceWidth = mc.fontRenderer.getStringWidth(distanceText);
                    int distanceX = x + (width / 4 - distanceWidth / 2);
                    int distanceY = y + distanceYOffset;
                    mc.fontRenderer.drawStringWithShadow(distanceText, distanceX, distanceY, 0x888888);
                }
            }

            if (detectiveName != null) {
                int nameWidth = mc.fontRenderer.getStringWidth(detectiveName);
                int nameX = x + (width * 3 / 4 - nameWidth / 2);
                int nameY = y + nameYOffset;
                mc.fontRenderer.drawStringWithShadow(detectiveName, nameX, nameY, 0xFFD700);

                if (detective != null && showDistance) {
                    double distance = mc.player.getDistance(detective);
                    String distanceText = String.format("(%.1fm)", distance);
                    int distanceWidth = mc.fontRenderer.getStringWidth(distanceText);
                    int distanceX = x + (width * 3 / 4 - distanceWidth / 2);
                    int distanceY = y + distanceYOffset;
                    mc.fontRenderer.drawStringWithShadow(distanceText, distanceX, distanceY, 0x888888);
                }
            }

            Gui.drawRect(x + width / 2 - 1, y, x + width / 2 + 1, y + height, 0xFFAAAAAA);

            int modelY = y + 90;
            float modelScale = 36.0F;

            if (detectiveRender != null) {
                drawFullPlayerModel(detectiveRender, x + width * 3 / 4, modelY, modelScale);
            }
            if (murdererRender != null) {
                drawFullPlayerModel(murdererRender, x + width / 4, modelY, modelScale);
            }

        } catch (Exception e) {
            LOGGER.error("HUD render error: ", e);
        }
    }

    private void drawFullPlayerModel(AbstractClientPlayer player, int x, int y, float scale) {
        if (player == null) return;

        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();

        try {
            GlStateManager.translate(x, y, 100.0F);
            GlStateManager.scale(-scale, scale, scale);
            GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);

            float renderYawOffset = player.renderYawOffset;
            float rotationYaw = player.rotationYaw;
            float rotationPitch = player.rotationPitch;
            float prevRotationYawHead = player.prevRotationYawHead;
            float rotationYawHead = player.rotationYawHead;

            float limbSwing = player.limbSwing;
            float limbSwingAmount = player.limbSwingAmount;
            float prevLimbSwingAmount = player.prevLimbSwingAmount;
            float cameraYaw = player.cameraYaw;

            player.renderYawOffset = 0.0F;
            player.rotationYaw = 0.0F;
            player.rotationPitch = 0.0F;
            player.rotationYawHead = 0.0F;
            player.prevRotationYawHead = 0.0F;

            player.limbSwing = 0.0F;
            player.limbSwingAmount = 0.0F;
            player.prevLimbSwingAmount = 0.0F;
            player.cameraYaw = 0.0F;

            RenderManager renderManager = mc.getRenderManager();
            renderManager.setPlayerViewY(0.0F);
            renderManager.setRenderShadow(false);
            player.setAlwaysRenderNameTag(false);

            RenderHelper.enableStandardItemLighting();

            if (player.world != null) {
                renderManager.renderEntity(player, 0, 0, 0, 0.0F, 1.0F, false);
            }

            RenderHelper.disableStandardItemLighting();

            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GlStateManager.disableTexture2D();
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);

            player.renderYawOffset = renderYawOffset;
            player.rotationYaw = rotationYaw;
            player.rotationPitch = rotationPitch;
            player.rotationYawHead = rotationYawHead;
            player.prevRotationYawHead = prevRotationYawHead;

            player.limbSwing = limbSwing;
            player.limbSwingAmount = limbSwingAmount;
            player.prevLimbSwingAmount = prevLimbSwingAmount;
            player.cameraYaw = cameraYaw;
        } catch (Exception e) {
            LOGGER.error("Error while rendering player model", e);
        }

        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.popAttrib();
        GlStateManager.popMatrix();
    }

    /**
     * Единая точка расчёта позиции HUD с учётом отступа от края экрана.
     * Используется как при реальном рендере, так и (через те же константы)
     * в SettingsGUI при редактировании позиции — так рамка превью,
     * зона клика и фактический рендер никогда не расходятся.
     */
    private int[] getClampedHudPosition(ScaledResolution sr) {
        int screenWidth = sr.getScaledWidth();
        int screenHeight = sr.getScaledHeight();

        float hudXPercent = getHudXPercent();
        float hudYPercent = getHudYPercent();

        int x = (int) (hudXPercent * screenWidth);
        int y = (int) (hudYPercent * screenHeight);

        x = Math.max(HUD_EDGE_MARGIN, Math.min(screenWidth - HUD_WIDTH - HUD_EDGE_MARGIN, x));
        y = Math.max(HUD_EDGE_MARGIN, Math.min(screenHeight - HUD_HEIGHT - HUD_EDGE_MARGIN, y));

        return new int[]{x, y};
    }
}