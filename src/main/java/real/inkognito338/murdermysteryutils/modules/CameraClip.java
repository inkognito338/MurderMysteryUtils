package real.inkognito338.murdermysteryutils.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 15.10.2026
 */

@SuppressWarnings("SpellCheckingInspection")
@SideOnly(Side.CLIENT)
public class CameraClip extends Module {

    /**
     * Стандартная дистанция камеры в ванильном рендере (thirdPersonDistance).
     */
    private static final double VANILLA_DISTANCE = 4.0;

    private final Minecraft mc = Minecraft.getMinecraft();

    private final Setting distance;

    public CameraClip() {
        super("CameraClip");

        this.distance = new Setting("Distance", SettingType.NUMBER, 4.0, 4.0, 20.0);

        addSetting(distance);
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
    public void onCameraSetup(EntityViewRenderEvent.CameraSetup event) {
        if (mc.world == null || mc.player == null) return;

        // Только обычный вид от третьего лица (сзади): 0 — первое лицо, 2 — вид спереди.
        // В виде спереди ванильная камера развёрнута на 180°, и сдвиг по Z шёл бы в обратную сторону.
        if (mc.gameSettings.thirdPersonView != 1) return;
        if (mc.gameSettings.debugCamEnable) return;

        Entity entity = event.getEntity();
        if (entity == null) return;

        // Во сне ванильный код считает камеру по-другому
        if (entity instanceof EntityLivingBase && ((EntityLivingBase) entity).isPlayerSleeping()) return;

        double wanted = Math.max(0.1, getDistance());

        // Сколько камеру отодвинул ванильный код (с учётом столкновений)
        double clipped = vanillaDistance(entity, event.getRenderPartialTicks());

        // Ванильный код уже сдвинул камеру на clipped, добираем до wanted
        double extra = wanted - clipped;
        if (Math.abs(extra) < 1.0E-4) return;

        GlStateManager.translate(0.0F, 0.0F, (float) -extra);
    }

    private double getDistance() {
        Object value = distance.getValue();
        return value instanceof Number ? ((Number) value).doubleValue() : VANILLA_DISTANCE;
    }

    /**
     * Повторяет расчёт дистанции из EntityRenderer.orientCamera (1.12.2):
     * 8 лучей со смещением ±0.1 по каждой оси, берётся ближайшее попадание в блок.
     */
    private double vanillaDistance(Entity entity, double partialTicks) {
        double d3 = VANILLA_DISTANCE;

        double x = entity.prevPosX + (entity.posX - entity.prevPosX) * partialTicks;
        double y = entity.prevPosY + (entity.posY - entity.prevPosY) * partialTicks + entity.getEyeHeight();
        double z = entity.prevPosZ + (entity.posZ - entity.prevPosZ) * partialTicks;

        float yaw = entity.rotationYaw;
        float pitch = entity.rotationPitch;

        double dx = (double) (-MathHelper.sin(yaw * 0.017453292F) * MathHelper.cos(pitch * 0.017453292F)) * d3;
        double dz = (double) (MathHelper.cos(yaw * 0.017453292F) * MathHelper.cos(pitch * 0.017453292F)) * d3;
        double dy = (double) (-MathHelper.sin(pitch * 0.017453292F)) * d3;

        Vec3d eye = new Vec3d(x, y, z);

        for (int i = 0; i < 8; i++) {
            float ox = (float) ((i & 1) * 2 - 1);
            float oy = (float) ((i >> 1 & 1) * 2 - 1);
            float oz = (float) ((i >> 2 & 1) * 2 - 1);
            ox *= 0.1F;
            oy *= 0.1F;
            oz *= 0.1F;

            Vec3d from = new Vec3d(x + ox, y + oy, z + oz);
            Vec3d to = new Vec3d(x - dx + ox + oz, y - dy + oy, z - dz + oz);

            RayTraceResult hit = mc.world.rayTraceBlocks(from, to);
            if (hit != null) {
                double dist = hit.hitVec.distanceTo(eye);
                if (dist < d3) d3 = dist;
            }
        }
        return d3;
    }
}