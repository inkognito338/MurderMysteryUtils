package real.inkognito338.murdermysteryutils.modules;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.CPacketAnimation;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.network.play.client.CPacketPlayerTryUseItem;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.network.play.client.CPacketUseEntity;
import net.minecraft.network.play.server.SPacketChangeGameState;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.network.play.server.SPacketJoinGame;
import net.minecraft.network.play.server.SPacketPlayerAbilities;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.network.play.server.SPacketPlayerPosLook;
import net.minecraft.network.play.server.SPacketRespawn;
import net.minecraft.network.play.server.SPacketUnloadChunk;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 15.10.2026
 * <p>
 * Freecam — клиентская имитация гейммода 3 (спектатор), как будто сервер выдал /gamemode 3.
 * Пока модуль включён, исходящие пакеты движения/поворота/шифта/ПКМ-ЛКМ-атаки отбрасываются,
 * поэтому сервер движения и действий не видит.
 * Сам модуль серверу никаких пакетов не отправляет.
 * При выключении игрок возвращается на реальную позицию, а режим и abilities — к тем,
 * которые сервер считает настоящими.
 * <p>
 * На реальной позиции стоит клиентский фейковый игрок (копия внешности и экипировки)
 * с ESP-боксом, видимым сквозь стены, чтобы было видно, где игрок стоит на самом деле.
 * <p>
 * Входящие пакеты, меняющие состояние (телепорт, смена мира, режим игры, abilities),
 * НЕ обрабатываются вручную: они проходят по пайплайну штатно (ванильная обработка и Forge-хендлеры
 * работают как обычно), а наша логика ставится в очередь главного потока строго до и строго после
 * ванильной задачи: before -> ванилла -> after -> applySpectator.
 * <p>
 * Скорость, noClip и abilities выставляются заново КАЖДЫЙ тик (обе фазы ClientTickEvent),
 * в начале тика игрока (PlayerTickEvent.START) и прямо перед движением (InputUpdateEvent).
 * <p>
 * Телепорт считается «близким» (камера остаётся) только если чанк назначения загружен И лежит
 * в пределах дальности прорисовки от камеры; иначе камера переносится на место телепорта.
 * <p>
 * SPacketUnloadChunk во время freecam не применяются (чанки остаются на клиенте),
 * при выключении накопленные выгрузки применяются.
 */
@SuppressWarnings({"SpellCheckingInspection", "unchecked"})
@SideOnly(Side.CLIENT)
public class Freecam extends Module {

    // ===================== DEBUG =====================
    /** Главный переключатель дебаг-лога (true = пишем в консоль/latest.log). */
    private static final boolean DEBUG = false;
    /** Подробный лог каждого тика / каждого заблокированного пакета (очень много строк). Работает только при DEBUG = true. */
    private static final boolean DEBUG_VERBOSE = false;
    /** Как часто (в тиках) писать сводку состояния, когда DEBUG_VERBOSE = false. 0 = выключено. */
    private static final int DEBUG_STATE_INTERVAL = 100;
    private static final String LOG_PREFIX = "[Freecam]";



    private static void dbg(String fmt, Object... args) {
        if (!DEBUG) {
            return;
        }
        try {
            String text = args == null || args.length == 0 ? fmt : String.format(Locale.ROOT, fmt, args);
            System.out.println(LOG_PREFIX + "[" + Thread.currentThread().getName() + "] " + text);
        } catch (Throwable t) {
            System.out.println(LOG_PREFIX + " <log format error: " + fmt + " / " + t + ">");
        }
    }

    private static void dbgErr(String where, Throwable t) {
        if (!DEBUG) {
            // Ошибки печатаем всегда, чтобы не терять их при выключенном дебаге
            t.printStackTrace();
            return;
        }
        System.out.println(LOG_PREFIX + "[" + Thread.currentThread().getName() + "] ERROR in " + where + ": " + t);
        t.printStackTrace();
    }

    private static String pkName(Object o) {
        return o == null ? "null" : o.getClass().getSimpleName();
    }

    // Счётчики для сводок
    private int dbgTickCounter = 0;
    private volatile int dbgBlockedOut = 0;
    private volatile int dbgPassedIn = 0;
    // ==================================================

    private static final String HANDLER_NAME = "mmu_freecam_blocker";

    // Отрицательный id, чтобы не пересечься с серверными сущностями
    private static final int FAKE_ENTITY_ID = -133742;

    // Цвет ESP-бокса (RGBA)
    private static final float ESP_R = 0.2f, ESP_G = 1.0f, ESP_B = 0.4f, ESP_A = 1.0f;

    // SPacketChangeGameState.CHANGE_GAME_MODE (значение 3)
    private static final int CHANGE_GAME_MODE_STATE = 3;

    // Поле gameType в NetworkPlayerInfo ищем по типу, чтобы не зависеть от MCP/SRG-имён
    private static Field infoGameTypeField;

    static {
        try {
            for (Field f : NetworkPlayerInfo.class.getDeclaredFields()) {
                if (f.getType() == GameType.class) {
                    f.setAccessible(true);
                    infoGameTypeField = f;
                    break;
                }
            }
        } catch (Throwable t) {
            infoGameTypeField = null;
            dbgErr("static init (reflection)", t);
        }
        dbg("static init: infoGameTypeField = %s", infoGameTypeField);
    }

    private final Minecraft mc = Minecraft.getMinecraft();

    // Реальное состояние (то, что считает сервер). Меняется только из главного потока.
    private double realX, realY, realZ;
    private float realYaw, realPitch;
    private GameType realControllerType;
    private GameType realInfoType;

    // Реальные abilities от сервера
    private boolean capAllowFlying, capIsFlying, capDisableDamage;
    private float capFlySpeed, capWalkSpeed;

    // После смены мира следующий телепорт всегда переносит камеру
    private boolean forceNextTeleport = false;

    // Выгрузки чанков, которые сервер прислал во время freecam (применим при выключении)
    private final Set<ChunkPos> pendingUnloads = ConcurrentHashMap.newKeySet();

    // Фейковый игрок на реальной позиции (только главный поток)
    private FakePlayer fake;

    private volatile boolean active = false;

    // Соединение, на которое реально повешен хендлер (снимаем именно с него, а не через mc.getConnection())
    private volatile NetworkManager blockerNm;

    public Freecam() {
        super("Freecam");
        this.addSetting(new Setting("Speed", SettingType.NUMBER, 3.0, 1.0, 10.0));
        dbg("constructed, DEBUG=%s VERBOSE=%s", DEBUG, DEBUG_VERBOSE);
    }

    @Override
    public void onEnable() {
        dbg("onEnable() called: active=%s player=%s controller=%s connection=%s",
                active, mc.player != null, mc.playerController != null, mc.getConnection() != null);

        // active: повторный onEnable запомнил бы позицию/режим КАМЕРЫ как «реальные»
        if (active || mc.player == null || mc.playerController == null || mc.getConnection() == null) {
            dbg("onEnable() aborted (already active or world not ready)");
            return;
        }

        EntityPlayerSP p = mc.player;
        realX = p.posX;
        realY = p.posY;
        realZ = p.posZ;
        realYaw = p.rotationYaw;
        realPitch = p.rotationPitch;
        realControllerType = mc.playerController.getCurrentGameType();

        NetworkPlayerInfo info = getInfo();
        realInfoType = info != null ? info.getGameType() : null;

        dbg("onEnable: saved real pos=(%.3f, %.3f, %.3f) yaw=%.2f pitch=%.2f controllerType=%s infoType=%s",
                realX, realY, realZ, realYaw, realPitch, realControllerType, realInfoType);

        snapshotCapabilities();
        forceNextTeleport = false;
        pendingUnloads.clear();
        dbgTickCounter = 0;
        dbgBlockedOut = 0;
        dbgPassedIn = 0;

        // Фейк создаём до смены режима, пока скин/слои/экипировка ещё «обычные»
        spawnFake();

        try {
            installBlocker();
            active = true;
            MinecraftForge.EVENT_BUS.register(this);
            dbg("onEnable: blocker installed, event bus registered, active=true");
            applySpectator();
            dbg("onEnable: DONE");
        } catch (Throwable t) {
            // Откат: не оставляем хендлер/фейка/режим в полуживом состоянии
            dbgErr("onEnable (rollback)", t);
            active = false;
            MinecraftForge.EVENT_BUS.unregister(this);
            removeBlocker();
            removeFake();
        }
    }

    @Override
    public void onDisable() {
        dbg("onDisable() called: active=%s", active);
        if (!active) {
            return;
        }
        active = false;
        MinecraftForge.EVENT_BUS.unregister(this);
        removeBlocker();
        removeFake();
        dbg("onDisable: stats blockedOut=%d passedIn(state packets)=%d pendingUnloads=%d",
                dbgBlockedOut, dbgPassedIn, pendingUnloads.size());

        // Применяем выгрузки, которые сервер присылал, пока мы летали
        flushPendingUnloads();

        // Возвращаем реальный режим
        GameType infoType = realInfoType != null ? realInfoType : realControllerType;
        dbg("onDisable: restoring infoType=%s controllerType=%s", infoType, realControllerType);
        setInfoType(infoType);
        if (mc.playerController != null && realControllerType != null) {
            mc.playerController.setGameType(realControllerType);
        }

        EntityPlayerSP p = mc.player;
        if (p != null) {
            dbg("onDisable: camera was at (%.3f, %.3f, %.3f), returning to (%.3f, %.3f, %.3f)",
                    p.posX, p.posY, p.posZ, realX, realY, realZ);
            p.setPositionAndRotation(realX, realY, realZ, realYaw, realPitch);
            p.noClip = false;
            p.motionX = p.motionY = p.motionZ = 0;

            // setGameType выставил abilities по режиму; накладываем то, что прислал сервер
            p.capabilities.allowFlying = capAllowFlying;
            p.capabilities.isFlying = capAllowFlying && capIsFlying;
            p.capabilities.disableDamage = capDisableDamage;
            p.capabilities.setFlySpeed(capFlySpeed);
            p.capabilities.setPlayerWalkSpeed(capWalkSpeed);
            dbg("onDisable: restored abilities allowFlying=%s isFlying=%s disableDamage=%s flySpeed=%.4f walkSpeed=%.4f",
                    p.capabilities.allowFlying, p.capabilities.isFlying, p.capabilities.disableDamage,
                    capFlySpeed, capWalkSpeed);
        } else {
            dbg("onDisable: mc.player == null, nothing to restore");
        }
        dbg("onDisable: DONE");
    }

    /**
     * Главный источник истины: каждый тик, в обеих фазах (START и END), заново выставляем
     * режим, abilities, noClip и скорость.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (!active) {
            return;
        }
        // Дисконнект / выгрузка мира: выключаемся сами, чтобы не осталось висящего состояния
        if (mc.player == null || mc.world == null) {
            dbg("onClientTick: player/world is null (disconnect?) -> auto disable");
            active = false;
            MinecraftForge.EVENT_BUS.unregister(this);
            removeBlocker();
            removeFake();
            pendingUnloads.clear();
            toggle(); // onDisable вернётся сразу (active уже false), toggled-флаг синхронизируется
            return;
        }

        if (DEBUG && event.phase == TickEvent.Phase.END) {
            dbgTickCounter++;
            if (DEBUG_VERBOSE || (DEBUG_STATE_INTERVAL > 0 && dbgTickCounter % DEBUG_STATE_INTERVAL == 0)) {
                dumpState("tick#" + dbgTickCounter);
            }
        }

        ensureBlocker();
        applySpectator();

        if (event.phase == TickEvent.Phase.END) {
            updateFake();
        }
    }

    /** Сводка текущего состояния (только для дебага). */
    private void dumpState(String reason) {
        if (!DEBUG) {
            return;
        }
        EntityPlayerSP p = mc.player;
        if (p == null) {
            dbg("STATE[%s]: player=null", reason);
            return;
        }
        dbg("STATE[%s]: cam=(%.2f, %.2f, %.2f) real=(%.2f, %.2f, %.2f) dist=%.2f | ctrl=%s info=%s | "
                        + "fly=%s/%s dmg=%s flySpd=%.4f noClip=%s | fake=%s pendingUnloads=%d blockedOut=%d passedIn=%d",
                reason, p.posX, p.posY, p.posZ, realX, realY, realZ,
                Math.sqrt((p.posX - realX) * (p.posX - realX) + (p.posY - realY) * (p.posY - realY)
                        + (p.posZ - realZ) * (p.posZ - realZ)),
                mc.playerController != null ? mc.playerController.getCurrentGameType() : null,
                getInfo() != null ? getInfo().getGameType() : null,
                p.capabilities.allowFlying, p.capabilities.isFlying, p.capabilities.disableDamage,
                p.capabilities.getFlySpeed(), p.noClip,
                fake != null, pendingUnloads.size(), dbgBlockedOut, dbgPassedIn);
    }

    /**
     * Страховка жизненного цикла хендлера: если соединение сменилось (новый NetworkManager) или хендлера
     * в пайплайне больше нет, ставим его заново — иначе пакеты шли бы мимо модуля.
     */
    private void ensureBlocker() {
        if (mc.getConnection() == null) {
            return;
        }
        NetworkManager cur = mc.getConnection().getNetworkManager();
        NetworkManager nm = blockerNm;
        boolean missing = nm == null || nm != cur || nm.channel() == null
                || nm.channel().pipeline().get(HANDLER_NAME) == null;
        if (!missing) {
            return;
        }
        boolean connectionChanged = nm != cur;
        dbg("ensureBlocker: handler missing! connectionChanged=%s (old nm=%s, cur nm=%s) -> reinstalling",
                connectionChanged, nm, cur);
        removeBlocker();
        installBlocker();
        if (connectionChanged && mc.playerController != null) {
            // Новое соединение = новый мир/игрок: считаем это сменой мира
            dbg("ensureBlocker: connection changed -> treating as world change");
            onWorldChange(mc.playerController.getCurrentGameType());
        }
    }

    /**
     * Начало тика самого игрока (EntityPlayer.onUpdate) — раньше onLivingUpdate и travel.
     */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!active || event.side != Side.CLIENT || event.player != mc.player) {
            return;
        }
        if (DEBUG_VERBOSE) {
            dbg("onPlayerTick phase=%s", event.phase);
        }
        enforceFlight();
    }

    /**
     * Срабатывает внутри EntityPlayerSP.onLivingUpdate, прямо перед движением.
     */
    @SubscribeEvent
    public void onInputUpdate(InputUpdateEvent event) {
        if (!active || mc.player == null || event.getEntityPlayer() != mc.player) {
            return;
        }
        if (DEBUG_VERBOSE) {
            dbg("onInputUpdate");
        }
        mc.player.onGround = false;
        enforceFlight();
    }

    /**
     * Срабатывает в начале EntityLivingBase.onLivingUpdate — уже ПОСЛЕ логики спринта в EntityPlayerSP
     * и непосредственно перед travel. Гасим спринт, чтобы скорость всегда равнялась настройке Speed.
     */
    @SubscribeEvent
    public void onLivingUpdate(net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent event) {
        if (!active || mc.player == null || event.getEntityLiving() != mc.player) {
            return;
        }
        if (DEBUG_VERBOSE) {
            dbg("onLivingUpdate (sprint=%s)", mc.player.isSprinting());
        }
        mc.player.setSprinting(false);
        mc.player.onGround = false;
        enforceFlight();
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!active || fake == null || mc.world == null) {
            return;
        }
        Entity view = mc.getRenderViewEntity();
        if (view == null) {
            if (DEBUG_VERBOSE) {
                dbg("onRenderWorld: renderViewEntity == null, skip");
            }
            return;
        }

        float pt = event.getPartialTicks();
        double vx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double vy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        double vz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;

        AxisAlignedBB box = fake.getEntityBoundingBox().grow(0.002).offset(-vx, -vy, -vz);

        if (DEBUG_VERBOSE) {
            dbg("onRenderWorld: pt=%.3f view=(%.2f, %.2f, %.2f) box=%s", pt, vx, vy, vz, box);
        }

        // Запоминаем реальные состояния GL и возвращаем ровно их
        final boolean wasLighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        final boolean wasTexture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        final boolean wasDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        final boolean wasBlend = GL11.glIsEnabled(GL11.GL_BLEND);

        GlStateManager.pushMatrix();
        try {
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GL11.glLineWidth(2.0f);

            RenderGlobal.drawSelectionBoundingBox(box, ESP_R, ESP_G, ESP_B, ESP_A);
        } catch (Throwable t) {
            dbgErr("onRenderWorld", t);
            throw t;
        } finally {
            GL11.glLineWidth(1.0f);
            GlStateManager.depthMask(true);

            if (wasBlend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            if (wasDepth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            if (wasLighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            if (wasTexture) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();

            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.popMatrix();
        }
    }

    // ---------- фейковый игрок ----------

    private void spawnFake() {
        removeFake();
        if (mc.world == null || mc.player == null) {
            dbg("spawnFake: world/player null, skip");
            return;
        }
        fake = new FakePlayer(mc.world, mc.player);
        syncFake();
        mc.world.addEntityToWorld(FAKE_ENTITY_ID, fake);
        dbg("spawnFake: spawned id=%d at (%.3f, %.3f, %.3f)", FAKE_ENTITY_ID, realX, realY, realZ);
    }

    private void removeFake() {
        if (mc.world != null) {
            mc.world.removeEntityFromWorld(FAKE_ENTITY_ID);
        }
        if (fake != null) {
            dbg("removeFake: removed id=%d", FAKE_ENTITY_ID);
        }
        fake = null;
    }

    /** Пересоздаёт фейка, если он пропал из мира, иначе просто синхронизирует. */
    private void updateFake() {
        if (mc.world == null || mc.player == null) {
            return;
        }
        if (fake == null || fake.world != mc.world || mc.world.getEntityByID(FAKE_ENTITY_ID) != fake) {
            dbg("updateFake: fake lost (null=%s, otherWorld=%s) -> respawn",
                    fake == null, fake != null && fake.world != mc.world);
            spawnFake();
            return;
        }
        syncFake();
    }

    /** Ставит фейка на реальную точку и копирует экипировку игрока. */
    private void syncFake() {
        if (fake == null) {
            return;
        }
        fake.setLocationAndAngles(realX, realY, realZ, realYaw, realPitch);
        fake.prevPosX = fake.lastTickPosX = realX;
        fake.prevPosY = fake.lastTickPosY = realY;
        fake.prevPosZ = fake.lastTickPosZ = realZ;
        fake.prevRotationYaw = realYaw;
        fake.prevRotationPitch = realPitch;
        fake.rotationYawHead = fake.prevRotationYawHead = realYaw;
        fake.renderYawOffset = fake.prevRenderYawOffset = realYaw;
        fake.motionX = fake.motionY = fake.motionZ = 0;

        EntityPlayerSP p = mc.player;
        if (p != null) {
            for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
                fake.setItemStackToSlot(slot, p.getItemStackFromSlot(slot).copy());
            }
        }
        if (DEBUG_VERBOSE) {
            dbg("syncFake: placed at (%.3f, %.3f, %.3f)", realX, realY, realZ);
        }
    }

    /**
     * Клиентская копия игрока. UUID случайный, чтобы world.getPlayerEntityByUUID не вернул фейка вместо
     * настоящего игрока; скин берём у mc.player. isSpectator = false, иначе ванильный рендер нарисует
     * только голову.
     */
    private static class FakePlayer extends EntityOtherPlayerMP {

        private final EntityPlayerSP source;

        FakePlayer(World world, EntityPlayerSP source) {
            super(world, new GameProfile(UUID.randomUUID(), source.getName()));
            this.source = source;
            // Слои скина (куртка, рукава, штаны, шапка)
            this.dataManager.set(PLAYER_MODEL_FLAG, source.getDataManager().get(PLAYER_MODEL_FLAG));
        }

        @Override
        public ResourceLocation getLocationSkin() {
            return source.getLocationSkin();
        }

        @Override
        public String getSkinType() {
            return source.getSkinType();
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }

        @Override
        public boolean canBeCollidedWith() {
            return false;
        }

        @Override
        public boolean canBePushed() {
            return false;
        }
    }

    // ---------- обработчики серверных пакетов (главный поток, сразу после ванили) ----------

    /** Смена мира / измерения / переход между серверами: создан новый игрок. */
    private void onWorldChange(GameType serverType) {
        dbg("onWorldChange: serverType=%s active=%s player=%s", serverType, active, mc.player != null);
        if (!active || mc.player == null) {
            return;
        }
        EntityPlayerSP p = mc.player;
        realX = p.posX;
        realY = p.posY;
        realZ = p.posZ;
        realYaw = p.rotationYaw;
        realPitch = p.rotationPitch;
        realControllerType = serverType;
        realInfoType = serverType;
        dbg("onWorldChange: new real pos=(%.3f, %.3f, %.3f), dropping %d pending unloads",
                realX, realY, realZ, pendingUnloads.size());

        // Мир новый: накопленные выгрузки относились к старому
        pendingUnloads.clear();

        snapshotCapabilities();
        forceNextTeleport = true;
        applySpectator();

        // Старый фейк остался в прежнем мире, создаём нового
        spawnFake();
    }

    private void onAbilities(SPacketPlayerAbilities pk) {
        if (!active) {
            return;
        }
        capAllowFlying = pk.isAllowFlying();
        capIsFlying = pk.isFlying();
        capDisableDamage = pk.isInvulnerable();
        capFlySpeed = pk.getFlySpeed();
        capWalkSpeed = pk.getWalkSpeed();
        dbg("onAbilities: allowFlying=%s isFlying=%s invulnerable=%s flySpeed=%.4f walkSpeed=%.4f",
                capAllowFlying, capIsFlying, capDisableDamage, capFlySpeed, capWalkSpeed);
        applySpectator();
    }

    private void onGameModeState(SPacketChangeGameState pk) {
        if (!active) {
            return;
        }
        GameType type = GameType.getByID((int) pk.getValue());
        dbg("onGameModeState: server set gamemode %s (raw=%.1f)", type, pk.getValue());
        realControllerType = type;
        realInfoType = type;
        applySpectator();
    }

    private void onPlayerList(SPacketPlayerListItem pk) {
        if (!active || mc.player == null) {
            return;
        }
        if (pk.getAction() == SPacketPlayerListItem.Action.UPDATE_GAME_MODE
                || pk.getAction() == SPacketPlayerListItem.Action.ADD_PLAYER) {
            for (SPacketPlayerListItem.AddPlayerData entry : pk.getEntries()) {
                if (entry.getProfile() != null
                        && mc.player.getUniqueID().equals(entry.getProfile().getId())
                        && entry.getGameMode() != null) {
                    dbg("onPlayerList: action=%s own entry gamemode %s -> %s",
                            pk.getAction(), realInfoType, entry.getGameMode());
                    realInfoType = entry.getGameMode();
                }
            }
        } else if (DEBUG_VERBOSE) {
            dbg("onPlayerList: action=%s (ignored)", pk.getAction());
        }
        applySpectator();
    }

    /**
     * cam = позиция/поворот камеры до телепорта (снята перед ванильной обработкой пакета).
     */
    private void onServerTeleport(SPacketPlayerPosLook pk, double[] cam) {
        EntityPlayerSP p = mc.player;
        if (!active || p == null) {
            dbg("onServerTeleport: skipped (active=%s player=%s)", active, p != null);
            return;
        }

        // Относительные флаги сервер считает от своей (реальной) позиции, а не от камеры
        double x = pk.getFlags().contains(SPacketPlayerPosLook.EnumFlags.X) ? realX + pk.getX() : pk.getX();
        double y = pk.getFlags().contains(SPacketPlayerPosLook.EnumFlags.Y) ? realY + pk.getY() : pk.getY();
        double z = pk.getFlags().contains(SPacketPlayerPosLook.EnumFlags.Z) ? realZ + pk.getZ() : pk.getZ();
        float yaw = pk.getFlags().contains(SPacketPlayerPosLook.EnumFlags.Y_ROT) ? realYaw + pk.getYaw() : pk.getYaw();
        float pitch = pk.getFlags().contains(SPacketPlayerPosLook.EnumFlags.X_ROT) ? realPitch + pk.getPitch() : pk.getPitch();

        dbg("onServerTeleport: packet pos=(%.3f, %.3f, %.3f) yaw=%.2f pitch=%.2f flags=%s",
                pk.getX(), pk.getY(), pk.getZ(), pk.getYaw(), pk.getPitch(), pk.getFlags());
        dbg("onServerTeleport: resolved dest=(%.3f, %.3f, %.3f) yaw=%.2f pitch=%.2f (old real=(%.3f, %.3f, %.3f))",
                x, y, z, yaw, pitch, realX, realY, realZ);

        // Точка возврата теперь там, куда телепортировал сервер
        realX = x;
        realY = y;
        realZ = z;
        realYaw = yaw;
        realPitch = pitch;

        int destChunkX = MathHelper.floor(x) >> 4;
        int destChunkZ = MathHelper.floor(z) >> 4;
        int camChunkX = MathHelper.floor(cam[0]) >> 4;
        int camChunkZ = MathHelper.floor(cam[2]) >> 4;

        boolean targetLoaded = mc.world != null
                && mc.world.getChunkProvider().getLoadedChunk(destChunkX, destChunkZ) != null;
        int chunkDist = Math.max(Math.abs(destChunkX - camChunkX), Math.abs(destChunkZ - camChunkZ));
        int maxDist = Math.max(2, mc.gameSettings.renderDistanceChunks);
        boolean near = targetLoaded && chunkDist <= maxDist;

        dbg("onServerTeleport: destChunk=(%d, %d) camChunk=(%d, %d) targetLoaded=%s chunkDist=%d maxDist=%d near=%s forceNext=%s",
                destChunkX, destChunkZ, camChunkX, camChunkZ, targetLoaded, chunkDist, maxDist, near, forceNextTeleport);

        if (forceNextTeleport || !near) {
            // Смена мира или телепорт за пределы прогруженных чанков: freecam продолжается с нового места
            dbg("onServerTeleport: -> MOVING CAMERA to destination (%s)", forceNextTeleport ? "forced after world change" : "far/unloaded");
            forceNextTeleport = false;
            p.setPositionAndRotation(x, y, z, yaw, pitch);
            p.motionX = p.motionY = p.motionZ = 0;
        } else {
            // Близкий телепорт: камера остаётся там, где была, вместе с инерцией и интерполяцией.
            dbg("onServerTeleport: -> KEEPING camera at (%.3f, %.3f, %.3f), restoring inertia", cam[0], cam[1], cam[2]);
            p.setPositionAndRotation(cam[0], cam[1], cam[2], (float) cam[3], (float) cam[4]);
            p.motionX = cam[5];
            p.motionY = cam[6];
            p.motionZ = cam[7];
            p.prevPosX = cam[8];
            p.prevPosY = cam[9];
            p.prevPosZ = cam[10];
            p.lastTickPosX = cam[11];
            p.lastTickPosY = cam[12];
            p.lastTickPosZ = cam[13];
            p.prevRotationYaw = (float) cam[14];
            p.prevRotationPitch = (float) cam[15];
        }
        applySpectator();

        // Фейк переезжает на новую реальную точку
        syncFake();
    }

    // ---------- вспомогательное ----------

    private void flushPendingUnloads() {
        dbg("flushPendingUnloads: applying %d chunk unloads (world=%s)", pendingUnloads.size(), mc.world != null);
        if (mc.world != null) {
            for (ChunkPos c : pendingUnloads) {
                if (DEBUG_VERBOSE) {
                    dbg("flushPendingUnloads: unload chunk (%d, %d)", c.x, c.z);
                }
                mc.world.doPreChunk(c.x, c.z, false);
            }
        }
        pendingUnloads.clear();
    }

    /**
     * Пропускает пакет дальше по пайплайну ШТАТНО (ванильная обработка + хендлеры Forge), а нашу логику
     * ставит в очередь главного потока строго вокруг ванильной задачи:
     * before -> ванилла -> after -> applySpectator.
     * <p>
     * Вызывается из Netty-потока канала. Он один на соединение, а ctx.fireChannelRead синхронно
     * доходит до NetworkManager, который ставит ванильную задачу в ту же очередь, поэтому между
     * before, ванильной задачей и after другие пакеты не вклиниваются.
     * Вручную processPacket больше не вызывается: прежний обходной путь через nm.getNetHandler()
     * мог терять пакеты и ломал обработку, из-за чего приём от сервера «замирал».
     */
    private void passAround(ChannelHandlerContext ctx, Object msg, Runnable before, Runnable after) {
        dbgPassedIn++;
        dbg("passAround: IN  %s (before=%s, after=%s)", pkName(msg), before != null, after != null);
        if (before != null) {
            mc.addScheduledTask(() -> {
                dbg("passAround: BEFORE task for %s", pkName(msg));
                runSafe(before);
            });
        }
        try {
            ctx.fireChannelRead(msg);
            dbg("passAround: fired %s down the pipeline", pkName(msg));
        } finally {
            mc.addScheduledTask(() -> {
                dbg("passAround: AFTER task for %s", pkName(msg));
                if (after != null) {
                    runSafe(after);
                }
                if (active) {
                    runSafe(this::applySpectator);
                }
            });
        }
    }

    private static void runSafe(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            dbgErr("runSafe", t);
        }
    }

    private void snapshotCapabilities() {
        if (mc.player == null) {
            return;
        }
        capAllowFlying = mc.player.capabilities.allowFlying;
        capIsFlying = mc.player.capabilities.isFlying;
        capDisableDamage = mc.player.capabilities.disableDamage;
        capFlySpeed = mc.player.capabilities.getFlySpeed();
        capWalkSpeed = mc.player.capabilities.getWalkSpeed();
        dbg("snapshotCapabilities: allowFlying=%s isFlying=%s disableDamage=%s flySpeed=%.4f walkSpeed=%.4f",
                capAllowFlying, capIsFlying, capDisableDamage, capFlySpeed, capWalkSpeed);
    }

    /** Скорость полёта freecam из настройки Speed (читается каждый раз, поэтому смена на лету работает). */
    private float flySpeedValue() {
        Setting s = getSettingByName("Speed");
        double speed = 3.0;
        if (s != null && s.getValue() instanceof Number) {
            speed = ((Number) s.getValue()).doubleValue();
        }
        return (float) (speed * 0.02);
    }

    /** Принудительно выставляет всё, от чего зависит freecam. Не зависит от snapshot'ов и состояния таба. */
    private void enforceFlight() {
        EntityPlayerSP p = mc.player;
        if (p == null) {
            return;
        }
        p.capabilities.allowFlying = true;
        p.capabilities.isFlying = true;
        p.capabilities.disableDamage = true;
        p.capabilities.setFlySpeed(flySpeedValue());
        p.noClip = true;
        p.fallDistance = 0;
    }

    private void applySpectator() {
        if (mc.player == null || mc.playerController == null) {
            return;
        }
        if (mc.playerController.getCurrentGameType() != GameType.SPECTATOR) {
            dbg("applySpectator: controller type was %s -> SPECTATOR", mc.playerController.getCurrentGameType());
            mc.playerController.setGameType(GameType.SPECTATOR);
        }
        setInfoType(GameType.SPECTATOR);
        // Всегда после setGameType: он сбрасывает abilities (включая flySpeed) по режиму
        enforceFlight();
    }

    private NetworkPlayerInfo getInfo() {
        if (mc.getConnection() == null || mc.player == null) {
            return null;
        }
        return mc.getConnection().getPlayerInfo(mc.player.getUniqueID());
    }

    private void setInfoType(GameType type) {
        NetworkPlayerInfo info = getInfo();
        if (type == null || info == null || infoGameTypeField == null) {
            if (DEBUG_VERBOSE) {
                dbg("setInfoType: skipped (type=%s info=%s field=%s)", type, info != null, infoGameTypeField != null);
            }
            return;
        }
        try {
            infoGameTypeField.set(info, type);
        } catch (IllegalAccessException e) {
            dbgErr("setInfoType", e);
        }
    }

    /**
     * Отменяем только «управляющие» пакеты игрока:
     * 1. Движение/позиция        — CPacketPlayer.Position, CPacketPlayer.PositionRotation
     * 2. Поворот камеры          — CPacketPlayer.Rotation, CPacketPlayer.PositionRotation
     * 3. Шифт (sneak/sprint)     — CPacketEntityAction (START/STOP_SNEAKING, START/STOP_SPRINTING)
     * 4. ПКМ/ЛКМ и атака         — CPacketPlayerDigging, CPacketPlayerTryUseItem,
     *                              CPacketPlayerTryUseItemOnBlock, CPacketUseEntity, CPacketAnimation
     * <p>
     * Базовый CPacketPlayer (только onGround/полёт) тоже режем — он шлётся каждый тик
     * и раскрывает движение в воздухе/на земле.
     * <p>
     * CPacketEntityAction режем НЕ целиком: riding-jump, open-inventory, elytra и прочее
     * не относятся к движению/шифту и должны доходить до сервера.
     */
    private static boolean shouldBlock(Object msg) {
        // 4: удары, копание, использование предмета/блока, анимация руки (свинг)
        if (msg instanceof CPacketPlayerDigging
                || msg instanceof CPacketPlayerTryUseItem
                || msg instanceof CPacketPlayerTryUseItemOnBlock
                || msg instanceof CPacketUseEntity
                || msg instanceof CPacketEntityAction
                || msg instanceof CPacketAnimation) {
            return true;
        }
        return false;
    }

    private void installBlocker() {
        // Если старый хендлер остался на другом соединении — снимаем его там
        removeBlocker();

        final NetworkManager nm = mc.getConnection().getNetworkManager();
        ChannelPipeline pipeline = nm.channel().pipeline();
        dbg("installBlocker: nm=%s pipeline=%s", nm, pipeline.names());
        if (pipeline.get(HANDLER_NAME) != null) {
            dbg("installBlocker: stale handler found in pipeline, removing");
            pipeline.remove(HANDLER_NAME);
        }
        blockerNm = nm;

        // Новый экземпляр на каждое включение: нешаренный хендлер нельзя добавлять повторно
        ChannelDuplexHandler handler = new ChannelDuplexHandler() {
            // Исходящие: режем только «управляющие» пакеты игрока (см. shouldBlock)
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                if (active && shouldBlock(msg)) {
                    dbgBlockedOut++;
                    if (DEBUG_VERBOSE) {
                        dbg("OUT BLOCKED %s", pkName(msg));
                    } else if (DEBUG && !(msg instanceof CPacketPlayer)) {
                        // Базовые пакеты движения шлются каждый тик, их не спамим; остальное логируем
                        dbg("OUT BLOCKED %s", pkName(msg));
                    }
                    // trySuccess: setSuccess бросает IllegalStateException, если промис уже завершён
                    promise.trySuccess();
                    return;
                }
                if (DEBUG_VERBOSE) {
                    dbg("OUT pass %s", pkName(msg));
                }
                super.write(ctx, msg, promise);
            }

            // Входящие: пакеты, меняющие состояние игрока, пропускаем штатно, но с нашими задачами вокруг
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (!active) {
                    super.channelRead(ctx, msg);
                    return;
                }

                // Выгрузку чанка не применяем: запоминаем и применим при выключении
                if (msg instanceof SPacketUnloadChunk) {
                    SPacketUnloadChunk pk = (SPacketUnloadChunk) msg;
                    pendingUnloads.add(new ChunkPos(pk.getX(), pk.getZ()));
                    if (DEBUG_VERBOSE) {
                        dbg("IN  SPacketUnloadChunk (%d, %d) SWALLOWED, pending=%d", pk.getX(), pk.getZ(), pendingUnloads.size());
                    }
                    return;
                }

                // Сервер заново прислал чанк целиком: выгрузка этого чанка отменяется
                if (msg instanceof SPacketChunkData) {
                    SPacketChunkData pk = (SPacketChunkData) msg;
                    if (pk.isFullChunk()) {
                        boolean removed = pendingUnloads.remove(new ChunkPos(pk.getChunkX(), pk.getChunkZ()));
                        if (DEBUG_VERBOSE || removed) {
                            dbg("IN  SPacketChunkData full (%d, %d) cancelPendingUnload=%s", pk.getChunkX(), pk.getChunkZ(), removed);
                        }
                    }
                    super.channelRead(ctx, msg);
                    return;
                }

                if (msg instanceof SPacketPlayerPosLook) {
                    final SPacketPlayerPosLook pk = (SPacketPlayerPosLook) msg;
                    dbg("IN  SPacketPlayerPosLook pos=(%.3f, %.3f, %.3f) yaw=%.2f pitch=%.2f flags=%s teleportId=%d",
                            pk.getX(), pk.getY(), pk.getZ(), pk.getYaw(), pk.getPitch(), pk.getFlags(), pk.getTeleportId());
                    // 0-4 поза камеры, 5-7 motion, 8-10 prevPos, 11-13 lastTickPos, 14-15 prevRotation
                    final double[] cam = new double[16];
                    passAround(ctx, msg, () -> {
                        EntityPlayerSP p = mc.player;
                        if (p != null) {
                            cam[0] = p.posX;
                            cam[1] = p.posY;
                            cam[2] = p.posZ;
                            cam[3] = p.rotationYaw;
                            cam[4] = p.rotationPitch;
                            cam[5] = p.motionX;
                            cam[6] = p.motionY;
                            cam[7] = p.motionZ;
                            cam[8] = p.prevPosX;
                            cam[9] = p.prevPosY;
                            cam[10] = p.prevPosZ;
                            cam[11] = p.lastTickPosX;
                            cam[12] = p.lastTickPosY;
                            cam[13] = p.lastTickPosZ;
                            cam[14] = p.prevRotationYaw;
                            cam[15] = p.prevRotationPitch;
                            dbg("PosLook BEFORE: camera snapshot (%.3f, %.3f, %.3f) yaw=%.2f pitch=%.2f",
                                    cam[0], cam[1], cam[2], cam[3], cam[4]);
                        } else {
                            dbg("PosLook BEFORE: mc.player == null, snapshot empty");
                        }
                    }, () -> onServerTeleport(pk, cam));
                    return;
                }

                if (msg instanceof SPacketJoinGame) {
                    final SPacketJoinGame pk = (SPacketJoinGame) msg;
                    dbg("IN  SPacketJoinGame gameType=%s dimension=%d", pk.getGameType(), pk.getDimension());
                    passAround(ctx, msg, null, () -> onWorldChange(pk.getGameType()));
                    return;
                }

                if (msg instanceof SPacketRespawn) {
                    final SPacketRespawn pk = (SPacketRespawn) msg;
                    dbg("IN  SPacketRespawn gameType=%s dimension=%d", pk.getGameType(), pk.getDimensionID());
                    passAround(ctx, msg, null, () -> onWorldChange(pk.getGameType()));
                    return;
                }

                if (msg instanceof SPacketPlayerAbilities) {
                    final SPacketPlayerAbilities pk = (SPacketPlayerAbilities) msg;
                    dbg("IN  SPacketPlayerAbilities");
                    passAround(ctx, msg, null, () -> onAbilities(pk));
                    return;
                }

                if (msg instanceof SPacketChangeGameState
                        && ((SPacketChangeGameState) msg).getGameState() == CHANGE_GAME_MODE_STATE) {
                    final SPacketChangeGameState pk = (SPacketChangeGameState) msg;
                    dbg("IN  SPacketChangeGameState(gamemode) value=%.1f", pk.getValue());
                    passAround(ctx, msg, null, () -> onGameModeState(pk));
                    return;
                }

                if (msg instanceof SPacketPlayerListItem) {
                    final SPacketPlayerListItem pk = (SPacketPlayerListItem) msg;
                    if (DEBUG_VERBOSE) {
                        dbg("IN  SPacketPlayerListItem action=%s entries=%d", pk.getAction(), pk.getEntries().size());
                    }
                    passAround(ctx, msg, null, () -> onPlayerList(pk));
                    return;
                }

                super.channelRead(ctx, msg);
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                dbgErr("netty handler exceptionCaught", cause);
                super.exceptionCaught(ctx, cause);
            }
        };

        // Перед packet_handler, иначе входящие пакеты до нас не дойдут
        if (pipeline.get("packet_handler") != null) {
            pipeline.addBefore("packet_handler", HANDLER_NAME, handler);
            dbg("installBlocker: added BEFORE packet_handler");
        } else {
            pipeline.addLast(HANDLER_NAME, handler);
            dbg("installBlocker: packet_handler not found, added LAST");
        }
        dbg("installBlocker: pipeline now = %s", pipeline.names());
    }

    private void removeBlocker() {
        // Снимаем с того соединения, на которое ставили
        NetworkManager nm = blockerNm;
        blockerNm = null;
        if (nm == null || nm.channel() == null) {
            return;
        }
        ChannelPipeline pipeline = nm.channel().pipeline();
        if (pipeline.get(HANDLER_NAME) != null) {
            pipeline.remove(HANDLER_NAME);
            dbg("removeBlocker: handler removed from nm=%s", nm);
        } else {
            dbg("removeBlocker: handler was already absent on nm=%s", nm);
        }
    }
}