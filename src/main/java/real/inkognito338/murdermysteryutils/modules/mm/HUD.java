package real.inkognito338.murdermysteryutils.modules.mm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import real.inkognito338.murdermysteryutils.modules.Module;
import real.inkognito338.murdermysteryutils.modules.settings.Setting;
import real.inkognito338.murdermysteryutils.modules.settings.SettingType;
import real.inkognito338.murdermysteryutils.utils.MurderMysteryUtils;

import java.util.HashMap;
import java.util.Map;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class HUD extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final MurderMysteryUtils mmUtils = new MurderMysteryUtils();
    private static final Logger LOGGER = LogManager.getLogger();

    private final Map<String, AbstractClientPlayer> playerCache = new HashMap<>();

    public HUD() {
        super("HUD");

        this.addSetting(new Setting("Show Distance", SettingType.BOOLEAN, true));
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
        playerCache.clear();
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        playerCache.clear();
    }

    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!isEnabled()) return;

        mmUtils.update();

        String murdererName = mmUtils.hasMurderer() ? mmUtils.getMurderer() : null;
        String detectiveName = !mmUtils.getDetectives().isEmpty() ? mmUtils.getDetectives().iterator().next() : null;

        // Обновляем кеш актуальными игроками (если они онлайн)
        AbstractClientPlayer murderer = getOrUpdatePlayer(murdererName);
        AbstractClientPlayer detective = getOrUpdatePlayer(detectiveName);

        ScaledResolution sr = new ScaledResolution(mc);
        int screenWidth = sr.getScaledWidth();
        int x = screenWidth - 180;
        int y = 20;

        drawTable(x, y, murderer, detective, murdererName, detectiveName);
    }

    private AbstractClientPlayer getOrUpdatePlayer(String name) {
        if (name == null) return null;

        // Ищем игрока в мире
        AbstractClientPlayer onlinePlayer = findPlayerByName(name);

        if (onlinePlayer != null) {
            // Если игрок онлайн - обновляем кеш
            playerCache.put(name, onlinePlayer);
            return onlinePlayer;
        } else {
            // Если игрок не онлайн - возвращаем из кеша (если есть)
            return playerCache.get(name);
        }
    }

    private AbstractClientPlayer findPlayerByName(String name) {
        if (name == null) return null;

        for (EntityPlayer player : mc.world.playerEntities) {
            if (player instanceof AbstractClientPlayer && player.getName().equals(name)) {
                return (AbstractClientPlayer) player;
            }
        }
        return null;
    }

    private void drawTable(int x, int y, AbstractClientPlayer murderer, AbstractClientPlayer detective, String murdererName, String detectiveName) {
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

                // Показываем дистанцию только если игрок онлайн
                AbstractClientPlayer onlineMurderer = findPlayerByName(murdererName);
                if (onlineMurderer != null && showDistance) {
                    double distance = mc.player.getDistance(onlineMurderer);
                    String distanceText = String.format("(%.1fm)", distance);
                    int distanceWidth = mc.fontRenderer.getStringWidth(distanceText);
                    int distanceX = x + (width / 4 - distanceWidth / 2);
                    int distanceY = y + distanceYOffset;
                    mc.fontRenderer.drawStringWithShadow(distanceText, distanceX, distanceY, 0x888888);
                }
            } else {
                String unknownText = "";
                int nameWidth = mc.fontRenderer.getStringWidth(unknownText);
                int nameX = x + (width / 4 - nameWidth / 2);
                int nameY = y + nameYOffset;
                mc.fontRenderer.drawStringWithShadow(unknownText, nameX, nameY, 0x888888);
            }

            if (detectiveName != null) {
                int nameWidth = mc.fontRenderer.getStringWidth(detectiveName);
                int nameX = x + (width * 3 / 4 - nameWidth / 2);
                int nameY = y + nameYOffset;
                mc.fontRenderer.drawStringWithShadow(detectiveName, nameX, nameY, 0xFFD700);

                // Показываем дистанцию только если игрок онлайн
                AbstractClientPlayer onlineDetective = findPlayerByName(detectiveName);
                if (onlineDetective != null && showDistance) {
                    double distance = mc.player.getDistance(onlineDetective);
                    String distanceText = String.format("(%.1fm)", distance);
                    int distanceWidth = mc.fontRenderer.getStringWidth(distanceText);
                    int distanceX = x + (width * 3 / 4 - distanceWidth / 2);
                    int distanceY = y + distanceYOffset;
                    mc.fontRenderer.drawStringWithShadow(distanceText, distanceX, distanceY, 0x888888);
                }
            } else {
                String unknownText = "";
                int nameWidth = mc.fontRenderer.getStringWidth(unknownText);
                int nameX = x + (width * 3 / 4 - nameWidth / 2);
                int nameY = y + nameYOffset;
                mc.fontRenderer.drawStringWithShadow(unknownText, nameX, nameY, 0x888888);
            }

            Gui.drawRect(x + width / 2 - 1, y, x + width / 2 + 1, y + height, 0xFFAAAAAA);

            int modelY = y + 90;
            float modelScale = 36.0F;

            // Рендерим модели из кеша (даже если игрок не онлайн)
            if (detective != null) {
                drawFullPlayerModel(detective, x + width * 3 / 4, modelY, modelScale);
            }
            if (murderer != null) {
                drawFullPlayerModel(murderer, x + width / 4, modelY, modelScale);
            }

        } catch (Exception e) {
            LOGGER.error("HUD render error: ", e);
        }
    }

    private void drawFullPlayerModel(AbstractClientPlayer player, int x, int y, float scale) {
        if (player == null) return;
        if (mc == null) return;
        if (mc.getRenderManager() == null) return;

        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();

        try {
            GlStateManager.translate(x, y, 100.0F);
            GlStateManager.scale(-scale, scale, scale);
            GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);

            // Сохраняем оригинальные значения
            float renderYawOffset = player.renderYawOffset;
            float rotationYaw = player.rotationYaw;
            float rotationPitch = player.rotationPitch;
            float prevRotationYawHead = player.rotationYawHead;
            float rotationYawHead = player.rotationYawHead;

            float limbSwing = player.limbSwing;
            float limbSwingAmount = player.limbSwingAmount;
            float prevLimbSwingAmount = player.prevLimbSwingAmount;
            float cameraYaw = player.cameraYaw;

            // Устанавливаем статичную позу
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

            // Рендерим с partialTicks = 1.0F для актуального состояния
            if (player.world != null) {
                renderManager.renderEntity(player, 0, 0, 0, 0.0F, 1.0F, false);
            }

            // Восстанавливаем оригинальные значения
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

    private boolean isEnabled() {
        return true;
    }
}