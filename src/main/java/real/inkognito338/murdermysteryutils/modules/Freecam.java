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
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.client.CPacketPlayer;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 26.09.2026
 * <p>
 * Freecam — клиентская имитация гейммода 3 (спектатор), как будто сервер выдал /gamemode 3.
 * Пока модуль включён, исходящие CPacketPlayer отбрасываются, поэтому сервер движения не видит.
 * Сам модуль серверу никаких пакетов не отправляет.
 * При выключении игрок возвращается на реальную позицию, а режим и abilities — к тем,
 * которые сервер считает настоящими.
 * <p>
 * На реальной позиции стоит клиентский фейковый игрок (копия внешности и экипировки)
 * с ESP-боксом, видимым сквозь стены, чтобы было видно, где игрок стоит на самом деле.
 * <p>
 * Входящие пакеты, меняющие состояние (телепорт, смена мира, режим игры, abilities),
 * обрабатываются атомарно: одна задача главного потока выполняет ванильную обработку пакета
 * и сразу за ней возвращает клиентский спектатор. Ванильная обработка выполняется всегда,
 * даже если наша логика до/после неё упала с исключением.
 * <p>
 * Скорость, noClip и abilities выставляются заново КАЖДЫЙ тик (обе фазы ClientTickEvent),
 * в начале тика игрока (PlayerTickEvent.START) и прямо перед движением (InputUpdateEvent),
 * поэтому смена мира, телепорт или abilities от сервера не могут надолго сбить скорость.
 * Это не зависит от записи NetworkPlayerInfo и от snapshot'ов.
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

    private static final String HANDLER_NAME = "mmu_freecam_blocker";

    // Отрицательный id, чтобы не пересечься с серверными сущностями
    private static final int FAKE_ENTITY_ID = -133742;

    // Цвет ESP-бокса (RGBA)
    private static final float ESP_R = 0.2f, ESP_G = 1.0f, ESP_B = 0.4f, ESP_A = 1.0f;

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
        }
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
    }

    @Override
    public void onEnable() {
        // active: повторный onEnable запомнил бы позицию/режим КАМЕРЫ как «реальные»
        if (active || mc.player == null || mc.playerController == null || mc.getConnection() == null) {
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

        snapshotCapabilities();
        forceNextTeleport = false;
        pendingUnloads.clear();

        // Фейк создаём до смены режима, пока скин/слои/экипировка ещё «обычные»
        spawnFake();

        try {
            installBlocker();
            active = true;
            MinecraftForge.EVENT_BUS.register(this);
            applySpectator();
        } catch (Throwable t) {
            // Откат: не оставляем хендлер/фейка/режим в полуживом состоянии
            t.printStackTrace();
            active = false;
            MinecraftForge.EVENT_BUS.unregister(this);
            removeBlocker();
            removeFake();
        }
    }

    @Override
    public void onDisable() {
        if (!active) {
            return;
        }
        active = false;
        MinecraftForge.EVENT_BUS.unregister(this);
        removeBlocker();
        removeFake();

        // Применяем выгрузки, которые сервер присылал, пока мы летали
        flushPendingUnloads();

        // Возвращаем реальный режим
        GameType infoType = realInfoType != null ? realInfoType : realControllerType;
        setInfoType(infoType);
        if (mc.playerController != null && realControllerType != null) {
            mc.playerController.setGameType(realControllerType);
        }

        EntityPlayerSP p = mc.player;
        if (p != null) {
            p.setPositionAndRotation(realX, realY, realZ, realYaw, realPitch);
            p.noClip = false;
            p.motionX = p.motionY = p.motionZ = 0;

            // setGameType выставил abilities по режиму; накладываем то, что прислал сервер
            p.capabilities.allowFlying = capAllowFlying;
            p.capabilities.isFlying = capAllowFlying && capIsFlying;
            p.capabilities.disableDamage = capDisableDamage;
            p.capabilities.setFlySpeed(capFlySpeed);
            p.capabilities.setPlayerWalkSpeed(capWalkSpeed);
        }
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
            active = false;
            MinecraftForge.EVENT_BUS.unregister(this);
            removeBlocker();
            removeFake();
            pendingUnloads.clear();
            toggle(); // onDisable вернётся сразу (active уже false), toggled-флаг синхронизируется
            return;
        }

        ensureBlocker();
        applySpectator();

        if (event.phase == TickEvent.Phase.END) {
            updateFake();
        }
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
        removeBlocker();
        installBlocker();
        if (connectionChanged && mc.playerController != null) {
            // Новое соединение = новый мир/игрок: считаем это сменой мира
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
        enforceFlight();
    }

    /**
     * Срабатывает внутри EntityPlayerSP.onLivingUpdate, прямо перед движением.
     * Дублируем принудительную установку, не завися от таба.
     */
    @SubscribeEvent
    public void onInputUpdate(InputUpdateEvent event) {
        if (!active || mc.player == null || event.getEntityPlayer() != mc.player) {
            return;
        }
        mc.player.onGround = false;
        enforceFlight();
    }

    /**
     * Срабатывает в начале EntityLivingBase.onLivingUpdate — уже ПОСЛЕ логики спринта в EntityPlayerSP
     * и непосредственно перед travel. Ванильный travel удваивает скорость полёта при isSprinting(),
     * а флаг спринта сбрасывается/выставляется заново при телепорте, смене мира и респавне,
     * из-за чего скорость «скачет» вдвое. Гасим спринт, чтобы скорость всегда равнялась настройке Speed.
     */
    @SubscribeEvent
    public void onLivingUpdate(net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent event) {
        if (!active || mc.player == null || event.getEntityLiving() != mc.player) {
            return;
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
            return;
        }

        float pt = event.getPartialTicks();
        double vx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double vy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        double vz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;

        AxisAlignedBB box = fake.getEntityBoundingBox().grow(0.002).offset(-vx, -vy, -vz);

        // Запоминаем реальные состояния GL и возвращаем ровно их: в RenderWorldLastEvent, например,
        // освещение выключено, и безусловный enableLighting() ломает весь GUI (чат, таб, текстуры)
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
            return;
        }
        fake = new FakePlayer(mc.world, mc.player);
        syncFake();
        mc.world.addEntityToWorld(FAKE_ENTITY_ID, fake);
    }

    private void removeFake() {
        if (mc.world != null) {
            mc.world.removeEntityFromWorld(FAKE_ENTITY_ID);
        }
        fake = null;
    }

    /** Пересоздаёт фейка, если он пропал из мира, иначе просто синхронизирует. */
    private void updateFake() {
        if (mc.world == null || mc.player == null) {
            return;
        }
        if (fake == null || fake.world != mc.world || mc.world.getEntityByID(FAKE_ENTITY_ID) != fake) {
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
    }

    /**
     * Клиентская копия игрока. UUID случайный, чтобы world.getPlayerEntityByUUID не вернул фейка вместо
     * настоящего игрока; скин берём у mc.player. isSpectator = false, иначе ванильный рендер нарисует
     * только голову (в NetworkPlayerInfo настоящего игрока во freecam стоит SPECTATOR).
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
        applySpectator();
    }

    private void onGameModeState(SPacketChangeGameState pk) {
        if (!active) {
            return;
        }
        GameType type = GameType.getByID((int) pk.getValue());
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
                    realInfoType = entry.getGameMode();
                }
            }
        }
        applySpectator();
    }

    /**
     * cam = позиция/поворот камеры до телепорта (снята перед ванильной обработкой пакета).
     */
    private void onServerTeleport(SPacketPlayerPosLook pk, double[] cam) {
        EntityPlayerSP p = mc.player;
        if (!active || p == null) {
            return;
        }

        // Относительные флаги сервер считает от своей (реальной) позиции, а не от камеры
        double x = pk.getFlags().contains(SPacketPlayerPosLook.EnumFlags.X) ? realX + pk.getX() : pk.getX();
        double y = pk.getFlags().contains(SPacketPlayerPosLook.EnumFlags.Y) ? realY + pk.getY() : pk.getY();
        double z = pk.getFlags().contains(SPacketPlayerPosLook.EnumFlags.Z) ? realZ + pk.getZ() : pk.getZ();
        float yaw = pk.getFlags().contains(SPacketPlayerPosLook.EnumFlags.Y_ROT) ? realYaw + pk.getYaw() : pk.getYaw();
        float pitch = pk.getFlags().contains(SPacketPlayerPosLook.EnumFlags.X_ROT) ? realPitch + pk.getPitch() : pk.getPitch();

        // Точка возврата теперь там, куда телепортировал сервер
        realX = x;
        realY = y;
        realZ = z;
        realYaw = yaw;
        realPitch = pitch;

        // Телепорт «близкий», только если чанк назначения загружен И он в пределах
        // дальности прорисовки от камеры. Сервер часто шлёт чанки назначения ДО пакета
        // телепорта, поэтому одной проверки «загружен ли чанк» мало.
        int destChunkX = MathHelper.floor(x) >> 4;
        int destChunkZ = MathHelper.floor(z) >> 4;
        int camChunkX = MathHelper.floor(cam[0]) >> 4;
        int camChunkZ = MathHelper.floor(cam[2]) >> 4;

        boolean targetLoaded = mc.world != null
                && mc.world.getChunkProvider().getLoadedChunk(destChunkX, destChunkZ) != null;
        int chunkDist = Math.max(Math.abs(destChunkX - camChunkX), Math.abs(destChunkZ - camChunkZ));
        boolean near = targetLoaded && chunkDist <= Math.max(2, mc.gameSettings.renderDistanceChunks);

        if (forceNextTeleport || !near) {
            // Смена мира или телепорт за пределы прогруженных чанков: freecam продолжается с нового места
            forceNextTeleport = false;
            p.setPositionAndRotation(x, y, z, yaw, pitch);
            p.motionX = p.motionY = p.motionZ = 0;
        } else {
            // Близкий телепорт: камера остаётся там, где была, вместе с инерцией и интерполяцией.
            // Ванильный handlePlayerPosLook обнуляет motion (флаги пустые), а сервер в фазах «заморозки»
            // шлёт PosLook КАЖДЫЙ тик — без восстановления motion камера не разгоняется и скорость падает ~в 10 раз.
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
        if (mc.world != null) {
            for (ChunkPos c : pendingUnloads) {
                mc.world.doPreChunk(c.x, c.z, false);
            }
        }
        pendingUnloads.clear();
    }

    /**
     * Обрабатывает входящий пакет атомарно в главном потоке:
     * before -> ванильная обработка -> after -> applySpectator. Порядок относительно остальных пакетов
     * сохраняется, т.к. задача ставится в ту же очередь, в том порядке, в котором пакеты пришли.
     * <p>
     * Обработчик берём у NetworkManager соединения, на которое мы повесили хендлер, а не через
     * mc.getConnection(): тот возвращает null, когда mc.player ещё/уже null (смена сервера, респавн),
     * и пакет молча терялся. Ванильная обработка выполняется всегда, наша логика до/после её не блокирует.
     * Даже если after упал, режим и скорость freecam всё равно восстанавливаются.
     */
    private void runAtomic(NetworkManager nm, Packet<INetHandlerPlayClient> pk, Runnable before, Runnable after) {
        mc.addScheduledTask(() -> {
            if (!nm.isChannelOpen()) {
                return; // соединение закрыто — ванилла делает то же самое
            }
            INetHandler listener = nm.getNetHandler();
            if (!(listener instanceof INetHandlerPlayClient)) {
                // Раньше пакет пропадал молча; теперь хотя бы видно в логе
                System.err.println("[Freecam] пакет " + pk.getClass().getSimpleName()
                        + " пропущен: netHandler=" + listener);
                return;
            }
            if (before != null) {
                try {
                    before.run();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            try {
                pk.processPacket((INetHandlerPlayClient) listener);
            } finally {
                try {
                    if (after != null) {
                        after.run();
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                } finally {
                    if (active) {
                        try {
                            applySpectator();
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                }
            }
        });
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
            return;
        }
        try {
            infoGameTypeField.set(info, type);
        } catch (IllegalAccessException ignored) {
        }
    }

    private void installBlocker() {
        // Если старый хендлер остался на другом соединении — снимаем его там
        removeBlocker();

        final NetworkManager nm = mc.getConnection().getNetworkManager();
        ChannelPipeline pipeline = nm.channel().pipeline();
        if (pipeline.get(HANDLER_NAME) != null) {
            pipeline.remove(HANDLER_NAME);
        }
        blockerNm = nm;

        // Новый экземпляр на каждое включение: нешаренный хендлер нельзя добавлять повторно
        ChannelDuplexHandler handler = new ChannelDuplexHandler() {
            // Исходящие: режем все move/rotation пакеты
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                if (active && msg instanceof CPacketPlayer) {
                    // trySuccess: setSuccess бросает IllegalStateException, если промис уже завершён
                    promise.trySuccess();
                    return;
                }
                super.write(ctx, msg, promise);
            }

            // Входящие: пакеты, меняющие состояние игрока, обрабатываем сами и атомарно
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
                    return;
                }

                // Сервер заново прислал чанк целиком: выгрузка этого чанка отменяется
                if (msg instanceof SPacketChunkData) {
                    SPacketChunkData pk = (SPacketChunkData) msg;
                    if (pk.isFullChunk()) {
                        pendingUnloads.remove(new ChunkPos(pk.getChunkX(), pk.getChunkZ()));
                    }
                    super.channelRead(ctx, msg);
                    return;
                }

                if (msg instanceof SPacketPlayerPosLook) {
                    final SPacketPlayerPosLook pk = (SPacketPlayerPosLook) msg;
                    // 0-4 поза камеры, 5-7 motion, 8-10 prevPos, 11-13 lastTickPos, 14-15 prevRotation
                    final double[] cam = new double[16];
                    runAtomic(nm, pk, () -> {
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
                        }
                    }, () -> onServerTeleport(pk, cam));
                    return;
                }

                if (msg instanceof SPacketJoinGame) {
                    final SPacketJoinGame pk = (SPacketJoinGame) msg;
                    runAtomic(nm, pk, null, () -> onWorldChange(pk.getGameType()));
                    return;
                }

                if (msg instanceof SPacketRespawn) {
                    final SPacketRespawn pk = (SPacketRespawn) msg;
                    runAtomic(nm, pk, null, () -> onWorldChange(pk.getGameType()));
                    return;
                }

                if (msg instanceof SPacketPlayerAbilities) {
                    final SPacketPlayerAbilities pk = (SPacketPlayerAbilities) msg;
                    runAtomic(nm, pk, null, () -> onAbilities(pk));
                    return;
                }

                if (msg instanceof SPacketChangeGameState
                        && ((SPacketChangeGameState) msg).getGameState() == 3) {
                    final SPacketChangeGameState pk = (SPacketChangeGameState) msg;
                    runAtomic(nm, pk, null, () -> onGameModeState(pk));
                    return;
                }

                if (msg instanceof SPacketPlayerListItem) {
                    final SPacketPlayerListItem pk = (SPacketPlayerListItem) msg;
                    runAtomic(nm, pk, null, () -> onPlayerList(pk));
                    return;
                }

                super.channelRead(ctx, msg);
            }
        };

        // Перед packet_handler, иначе входящие пакеты до нас не дойдут
        if (pipeline.get("packet_handler") != null) {
            pipeline.addBefore("packet_handler", HANDLER_NAME, handler);
        } else {
            pipeline.addLast(HANDLER_NAME, handler);
        }
    }

    private void removeBlocker() {
        // Снимаем с того соединения, на которое ставили. Через mc.getConnection() это не работало:
        // он null при mc.player == null и мог указывать на другое соединение — хендлер оставался висеть.
        NetworkManager nm = blockerNm;
        blockerNm = null;
        if (nm == null || nm.channel() == null) {
            return;
        }
        ChannelPipeline pipeline = nm.channel().pipeline();
        if (pipeline.get(HANDLER_NAME) != null) {
            pipeline.remove(HANDLER_NAME);
        }
    }
}