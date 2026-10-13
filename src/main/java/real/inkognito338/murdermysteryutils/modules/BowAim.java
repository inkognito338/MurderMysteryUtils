package real.inkognito338.murdermysteryutils.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.MurderAPI;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;

import java.util.List;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 23.09.2026
 */


@SuppressWarnings("SpellCheckingInspection")
@SideOnly(Side.CLIENT)
public class BowAim extends Module {
    private Minecraft mc;

    private Setting settingAutoShoot;
    private Setting settingOnlyWhenCharged;
    private Setting settingOnlyMurderer;
    private Setting settingPredictMovement;

    // ---- Физика стрелы (ванильные константы 1.12.2, EntityArrow) ----
    private static final double GRAVITY = 0.05D;
    private static final double DRAG = 0.99D;
    private static final double HIT_EXPAND = 0.3D;
    private static final double MIN_MOTION_LENGTH_SQ = 1.0E-4D;
    private static final float MIN_VELOCITY = 0.1F;

    private static final double MAX_ARROW_SPEED = 3.0D;
    private static final int MAX_SIMULATION_TICKS = 200;

    private static final double TARGET_SEARCH_RADIUS = 64.0D;
    private static final double TARGET_CONE_DEG = 8.0D;

    // Число итераций уточнения точки упреждения (сходится очень быстро)
    private static final int PREDICTION_ITERATIONS = 3;

    private int lastAutoShootTick = -1;

    public BowAim() {
        super("BowAim");

        settingAutoShoot = new Setting("AutoShoot", SettingType.BOOLEAN, false);
        settingOnlyWhenCharged = new Setting("OnlyWhenCharged", SettingType.BOOLEAN, true);
        settingOnlyMurderer = new Setting("OnlyMurderer", SettingType.BOOLEAN, false);
        settingPredictMovement = new Setting("PredictMovement", SettingType.BOOLEAN, false);

        addSetting(settingAutoShoot);
        addSetting(settingOnlyWhenCharged);
        addSetting(settingOnlyMurderer);
        addSetting(settingPredictMovement);
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
        mc = Minecraft.getMinecraft();
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    private boolean isAutoShoot() {
        Object v = settingAutoShoot.getValue();
        return v instanceof Boolean && (Boolean) v;
    }

    private boolean isOnlyWhenCharged() {
        Object v = settingOnlyWhenCharged.getValue();
        return v instanceof Boolean && (Boolean) v;
    }

    private boolean isOnlyMurderer() {
        Object v = settingOnlyMurderer.getValue();
        return v instanceof Boolean && (Boolean) v;
    }

    private boolean isPredictMovement() {
        Object v = settingPredictMovement.getValue();
        return v instanceof Boolean && (Boolean) v;
    }

    /**
     * AutoShoot.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (mc.player == null || mc.world == null) return;
        if (!isAutoShoot()) return;

        EntityPlayerSP player = mc.player;
        if (!isDrawingBow(player)) return;

        int tick = player.ticksExisted;
        if (tick == lastAutoShootTick) return;

        ItemStack active = player.getActiveItemStack();
        if (active.isEmpty()) return;
        if (active.getItemUseAction() != EnumAction.BOW) return;

        float velocityFactor = getArrowVelocity(player);
        if (velocityFactor < MIN_VELOCITY) return;

        if (!canHitTarget(player, velocityFactor)) return;

        player.stopActiveHand();
        mc.getConnection().sendPacket(new CPacketPlayerDigging(
                CPacketPlayerDigging.Action.RELEASE_USE_ITEM,
                BlockPos.ORIGIN,
                EnumFacing.DOWN
        ));

        lastAutoShootTick = tick;
    }

    private boolean isDrawingBow(EntityPlayer player) {
        if (!player.isHandActive()) return false;
        ItemStack active = player.getActiveItemStack();
        return !active.isEmpty() && active.getItem() instanceof ItemBow;
    }

    private float getArrowVelocity(EntityPlayer player) {
        ItemStack activeStack = player.getActiveItemStack();
        if (activeStack.isEmpty()) return 0.0F;

        int remainingTicks = player.getItemInUseCount();
        int ticksInUse = activeStack.getMaxItemUseDuration() - remainingTicks;

        return ItemBow.getArrowVelocity(ticksInUse);
    }

    // =====================================================================
    //                     ПОИСК ЦЕЛИ
    // =====================================================================

    private Entity findTarget(EntityPlayerSP self) {
        Vec3d eyePos = new Vec3d(self.posX, self.posY + self.getEyeHeight(), self.posZ);
        Vec3d look = self.getLook(1.0F).normalize();

        AxisAlignedBB searchBox = new AxisAlignedBB(
                self.posX - TARGET_SEARCH_RADIUS, self.posY - TARGET_SEARCH_RADIUS, self.posZ - TARGET_SEARCH_RADIUS,
                self.posX + TARGET_SEARCH_RADIUS, self.posY + TARGET_SEARCH_RADIUS, self.posZ + TARGET_SEARCH_RADIUS
        );
        List<Entity> candidates = mc.world.getEntitiesWithinAABBExcludingEntity(self, searchBox);

        double cosCone = Math.cos(Math.toRadians(TARGET_CONE_DEG));

        boolean onlyMurderer = isOnlyMurderer();

        Entity nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (Entity entity : candidates) {
            if (!(entity instanceof EntityLivingBase)) continue;
            if (entity == self) continue;
            if (entity.isDead) continue;

            // Если включён режим "только убийца" — целимся исключительно
            // в игроков с ролью MURDERER по данным MurderAPI. Не-игроков
            // (мобы и т.п.) и любые другие роли пропускаем.
            if (onlyMurderer) {
                if (!(entity instanceof EntityPlayer)) continue;
                String name = ((EntityPlayer) entity).getName();
                if (!MurderAPI.getInstance().isMurderer(name)) continue;
            }

            Vec3d toTarget = new Vec3d(
                    entity.posX - eyePos.x,
                    entity.posY + entity.height * 0.5D - eyePos.y,
                    entity.posZ - eyePos.z
            );
            double distSq = toTarget.lengthSquared();
            if (distSq < 1.0E-6D) continue;

            double dist = Math.sqrt(distSq);
            if (dist > TARGET_SEARCH_RADIUS) continue;

            double dot = look.dotProduct(toTarget.scale(1.0D / dist));
            if (dot < cosCone) continue;

            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = entity;
            }
        }

        return nearest;
    }

    // =====================================================================
    //                ПРОВЕРКА "МОЖНО ЛИ ПОПАСТЬ"
    // =====================================================================

    /**
     * Реальная стрела летит строго по текущему направлению взгляда игрока
     * (см. ItemBow.onPlayerStoppedUsing -> player.setThrowableHeading,
     * которое использует getLookVec()), а НЕ по подобранному углу "прямо
     * на цель".
     */
    private boolean canHitTarget(EntityPlayerSP player, float velocityFactor) {
        if (velocityFactor < MIN_VELOCITY) return false;

        Entity target = findTarget(player);
        if (target == null) return false;

        double speed = velocityFactor * MAX_ARROW_SPEED;
        Vec3d lookDir = player.getLook(1.0F).normalize();

        return simulateHitAlong(player, lookDir, speed, target);
    }

    /**
     * Оценивает точку, в которой окажется цель к моменту подлёта стрелы,
     * если она продолжит двигаться с текущей горизонтальной скоростью
     * (motionX/motionZ). Используется только для проверки "долетела бы
     * стрела ТУДА, где цель окажется", а НЕ меняет направление реального
     * выстрела (оно всё равно идёт по взгляду игрока) — так что режим
     * работает как дополнительный фильтр ложных срабатываний AutoShoot:
     * если цель убегает и предсказанная позиция уже вне досягаемости
     * текущего направления взгляда, выстрел не будет засчитан как точный.
     *
     * Итеративно уточняем время полёта: сначала считаем время до текущей
     * позиции цели, затем сдвигаем цель на это время вперёд, пересчитываем
     * время снова и т.д. — обычно 2-3 итераций достаточно для сходимости.
     */
    private Vec3d predictTargetPosition(EntityPlayerSP player, Entity target, double speed) {
        Vec3d eyePos = new Vec3d(
                player.posX,
                player.posY + player.getEyeHeight() - 0.1D,
                player.posZ
        );

        Vec3d currentCenter = new Vec3d(
                target.posX,
                target.posY + target.height * 0.5D,
                target.posZ
        );

        // Только горизонтальное движение — вертикальную скорость (прыжки,
        // падение) предсказывать ненадёжно, гравитация сама её погасит/
        // изменит непредсказуемо, поэтому берём только motionX/motionZ.
        double vx = target.motionX;
        double vz = target.motionZ;

        Vec3d predicted = currentCenter;

        for (int i = 0; i < PREDICTION_ITERATIONS; i++) {
            double dist = eyePos.distanceTo(predicted);
            if (speed < 1.0E-6D) break;

            // Приближённое время полёта в тиках (без учёта гравитации,
            // этого достаточно для оценки упреждения по горизонтали).
            double timeTicks = dist / speed;

            predicted = new Vec3d(
                    currentCenter.x + vx * timeTicks,
                    currentCenter.y,
                    currentCenter.z + vz * timeTicks
            );
        }

        return predicted;
    }

    /**
     * Симулирует полёт стрелы так же, как ванильный EntityArrow:
     * стартовая позиция — позиция игрока со смещением по высоте глаз,
     * начальная скорость — направление * speed, плюс собственная скорость
     * игрока (инерция), которую EntityArrow действительно учитывает.
     *
     * Если включён PredictMovement, хитбокс цели для проверки попадания
     * дополнительно сдвигается в сторону предсказанной точки — это не
     * меняет саму физику стрелы, а лишь делает проверку "долетит ли"
     * строже для убегающих целей, снижая число ложных выстрелов.
     *
     * Дополнительно: если на пути стрелы к цели оказывается ДРУГАЯ живая
     * сущность (например, невинный игрок случайно встал между стрелком
     * и убийцей), считаем это блокировкой — иначе AutoShoot стрелял бы,
     * ожидая попадание в убийцу, хотя стрела на самом деле поразит того,
     * кто встал на линию огня раньше.
     *
     * @param speed начальная скорость (блоков/тик)
     */
    private boolean simulateHitAlong(EntityPlayerSP player, Vec3d direction, double speed, Entity target) {
        Vec3d pos = new Vec3d(
                player.posX,
                player.posY + player.getEyeHeight() - 0.1D,
                player.posZ
        );

        Vec3d motion = new Vec3d(
                direction.x * speed + player.motionX,
                direction.y * speed + player.motionY,
                direction.z * speed + player.motionZ
        );

        AxisAlignedBB targetBox = target.getEntityBoundingBox().grow(HIT_EXPAND);

        if (isPredictMovement()) {
            Vec3d currentCenter = new Vec3d(
                    target.posX,
                    target.posY + target.height * 0.5D,
                    target.posZ
            );
            Vec3d predictedCenter = predictTargetPosition(player, target, speed);
            Vec3d offset = predictedCenter.subtract(currentCenter);

            targetBox = targetBox.offset(offset.x, offset.y, offset.z);
        }

        // Собираем потенциальных "блокировщиков" один раз до цикла:
        // расширяем зону поиска вдоль всего возможного пути полёта стрелы.
        AxisAlignedBB pathBounds = new AxisAlignedBB(pos, pos)
                .expand(motion.x * MAX_SIMULATION_TICKS, motion.y * MAX_SIMULATION_TICKS, motion.z * MAX_SIMULATION_TICKS)
                .grow(1.0D);
        List<Entity> possibleBlockers = mc.world.getEntitiesWithinAABBExcludingEntity(player, pathBounds);

        if (targetBox.contains(pos)) {
            return true;
        }

        for (int tick = 0; tick < MAX_SIMULATION_TICKS; tick++) {
            Vec3d nextPos = pos.add(motion);

            // Проверяем, не загораживает ли путь другая живая сущность —
            // если да, выстрел считается заблокированным (не стреляем).
            if (findBlockingEntity(pos, nextPos, target, possibleBlockers) != null) {
                return false;
            }

            RayTraceResult intercept = targetBox.calculateIntercept(pos, nextPos);
            if (intercept != null) {
                return true;
            }

            if (targetBox.contains(nextPos)) {
                return true;
            }

            RayTraceResult blockHit = mc.world.rayTraceBlocks(pos, nextPos, false, true, false);
            if (blockHit != null && blockHit.typeOfHit == RayTraceResult.Type.BLOCK) {
                return false;
            }

            if (nextPos.y < 0.0D || nextPos.y > 256.0D) {
                return false;
            }

            if (!mc.world.isBlockLoaded(new BlockPos(nextPos))) {
                return false;
            }

            pos = nextPos;

            motion = new Vec3d(
                    motion.x * DRAG,
                    motion.y * DRAG - GRAVITY,
                    motion.z * DRAG
            );

            if (motion.lengthSquared() < MIN_MOTION_LENGTH_SQ) {
                return false;
            }
        }

        return false;
    }

    /**
     * Проверяет, пересекает ли отрезок [pos, nextPos] хитбокс какой-либо
     * живой сущности, кроме самой цели. Используется, чтобы не засчитывать
     * попадание, если по пути стрела на самом деле упрётся в кого-то ещё
     * (например, невинного игрока перед убийцей).
     */
    private Entity findBlockingEntity(Vec3d pos, Vec3d nextPos, Entity target, List<Entity> candidates) {
        for (Entity entity : candidates) {
            if (entity == target) continue;
            if (!(entity instanceof EntityLivingBase)) continue;
            if (entity.isDead) continue;

            AxisAlignedBB box = entity.getEntityBoundingBox().grow(HIT_EXPAND);
            if (box.calculateIntercept(pos, nextPos) != null) {
                return entity;
            }
        }
        return null;
    }
}