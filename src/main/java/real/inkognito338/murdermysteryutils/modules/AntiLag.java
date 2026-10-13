package real.inkognito338.murdermysteryutils.modules;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketCustomSound;
import net.minecraft.network.play.server.SPacketMultiBlockChange;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import real.inkognito338.murdermysteryutils.Main;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@SideOnly(Side.CLIENT)
public class AntiLag extends Module {

    private static final boolean DEBUG = false;
    private static final boolean DEBUG_VERBOSE = false;

    private static final String LOG_FILE_NAME = "subway_antilag.log";
    private static final String HANDLER_NAME = "subway_antilag_handler";

    private static final String TRIGGER_MESSAGE =
            "MurderMystery ▸ Остерегайтесь рельсов, скоро поедет поезд, он может вас убить!";
    private static final String END_TRIGGER_MESSAGE =
            "MurderMystery ▸ Поезд проехал, следующий будет через 30 секунд";

    // Subway AABB
    private static final int SUB_MIN_X = -70;
    private static final int SUB_MIN_Y = 89;
    private static final int SUB_MIN_Z = -2;
    private static final int SUB_MAX_X = 100;
    private static final int SUB_MAX_Y = 100;
    private static final int SUB_MAX_Z = 14;

    // Ancient Tomb AABB
    private static final int TOMB_MIN_X = 32;
    private static final int TOMB_MIN_Y = 107;
    private static final int TOMB_MIN_Z = 5;
    private static final int TOMB_MAX_X = 53;
    private static final int TOMB_MAX_Y = 110;
    private static final int TOMB_MAX_Z = 35;

    private final Minecraft mc = Minecraft.getMinecraft();

    private final Setting subwayAntiLag = new Setting("SubwayAntiLag", SettingType.BOOLEAN, true);
    private final Setting ancientTombAntiLag = new Setting("AncientTombAntiLag", SettingType.BOOLEAN, true);

    private volatile boolean subwayActive = false;

    // Счётчики (только для subway)
    private volatile int statSoundTotal = 0;
    private volatile int statSoundCancelled = 0;
    private volatile int statBlocksTotal = 0;
    private volatile int statBlocksCancelled = 0;
    private volatile int statTileTotal = 0;
    private volatile int statTileCancelled = 0;

    // Ancient Tomb: бэкап брони арморстендов
    private final Map<UUID, ItemStack[]> armorBackup = new HashMap<>();
    private final Set<UUID> hiddenStands = new HashSet<>();

    // Лог
    private static PrintWriter logWriter = null;
    private static final Object LOG_LOCK = new Object();
    private static final SimpleDateFormat LOG_TS =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT);

    public AntiLag() {
        super("AntiLag");
        addSetting(subwayAntiLag);
        addSetting(ancientTombAntiLag);
    }

    private boolean isSubwayEnabled() {
        return (boolean) subwayAntiLag.getValue();
    }

    private boolean isTombEnabled() {
        return (boolean) ancientTombAntiLag.getValue();
    }

    // ====== LOG ======

    private static File getLogFile() {
        File dir = Main.getConfigDir();
        if (dir != null && !dir.exists()) dir.mkdirs();
        return new File(dir, LOG_FILE_NAME);
    }

    private static void openLog() {
        if (!DEBUG) return;
        synchronized (LOG_LOCK) {
            if (logWriter != null) return;
            try {
                File f = getLogFile();
                logWriter = new PrintWriter(new BufferedWriter(new FileWriter(f, true)));
                logWriter.println();
                logWriter.println("==================================================");
                logWriter.println("[" + LOG_TS.format(new Date()) + "] AntiLag log opened");
                logWriter.println("[" + LOG_TS.format(new Date()) + "] File: " + f.getAbsolutePath());
                logWriter.println("==================================================");
                logWriter.flush();
            } catch (IOException e) {
                System.err.println("[AntiLag] Failed to open log file: " + e);
                e.printStackTrace();
                logWriter = null;
            }
        }
    }

    private static void closeLog() {
        synchronized (LOG_LOCK) {
            if (logWriter != null) {
                try {
                    logWriter.println("[" + LOG_TS.format(new Date()) + "] AntiLag log closed");
                    logWriter.println("==================================================");
                    logWriter.flush();
                    logWriter.close();
                } catch (Throwable ignored) {}
                logWriter = null;
            }
        }
    }

    private static void log(String msg) {
        if (!DEBUG) return;
        synchronized (LOG_LOCK) {
            if (logWriter == null) openLog();
            if (logWriter != null) {
                logWriter.println("[" + LOG_TS.format(new Date()) + "] " + msg);
                logWriter.flush();
            }
            if (Main.LOGGER != null) Main.LOGGER.info("[AntiLag] {}", msg);
        }
    }

    private static void logVerbose(String msg) {
        if (!DEBUG_VERBOSE) return;
        log(msg);
    }

    private static void logError(String msg, Throwable t) {
        if (!DEBUG) return;
        log("[ERROR] " + msg);
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            synchronized (LOG_LOCK) {
                if (logWriter != null) {
                    logWriter.print(sw.toString());
                    logWriter.flush();
                }
            }
            if (Main.LOGGER != null) Main.LOGGER.error("[AntiLag] " + msg, t);
        }
    }

    private void dumpStats() {
        log("Stats: subway=" + subwayActive
                + " | Sound " + statSoundCancelled + "/" + statSoundTotal
                + " | Blocks " + statBlocksCancelled + "/" + statBlocksTotal
                + " | Tile " + statTileCancelled + "/" + statTileTotal
                + " | tombHidden=" + hiddenStands.size());
    }

    private void resetStats() {
        statSoundTotal = 0;
        statSoundCancelled = 0;
        statBlocksTotal = 0;
        statBlocksCancelled = 0;
        statTileTotal = 0;
        statTileCancelled = 0;
    }

    // ====== LIFECYCLE ======

    @Override
    public void onEnable() {
        log("onEnable");
        openLog();
        log("Config dir: " + Main.getConfigDir().getAbsolutePath());
        MinecraftForge.EVENT_BUS.register(this);
        subwayActive = false;
        resetStats();
        injectHandler();
    }

    @Override
    public void onDisable() {
        log("onDisable");
        dumpStats();
        MinecraftForge.EVENT_BUS.unregister(this);
        subwayActive = false;
        removeHandler();
        restoreAllTombStands();
        closeLog();
    }

    // ====== CHAT TRIGGER (Subway) ======

    @SubscribeEvent
    public void onClientChat(net.minecraftforge.client.event.ClientChatReceivedEvent event) {
        if (!isSubwayEnabled()) return;
        if (event.getMessage() == null) return;
        String text = event.getMessage().getUnformattedText();
        log("Chat: \"" + text + "\"");
        if (text == null) return;

        if (text.contains(TRIGGER_MESSAGE)) {
            if (!subwayActive) {
                subwayActive = true;
                resetStats();
                log("SUBWAY TRIGGER -> active");
            }
        } else if (text.contains(END_TRIGGER_MESSAGE)) {
            if (subwayActive) {
                subwayActive = false;
                log("SUBWAY END -> inactive");
                dumpStats();
            }
        }
    }

    // ====== WORLD ======

    @SubscribeEvent
    public void onWorldJoin(EntityJoinWorldEvent event) {
        if (mc.player == null) return;
        if (event.getEntity() != mc.player) return;

        log("World join. subway=" + subwayActive);
        subwayActive = false;
        resetStats();
        restoreAllTombStands();
        injectHandler();
    }

    // ====== TICK ======

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (mc.player == null || mc.world == null) return;

        if (mc.player.ticksExisted % 100 == 0) {
            injectHandler();
        }

        // AncientTombAntiLag: обрабатываем сущности в зоне
        if (isTombEnabled()) {
            processTombStands();
        } else {
            // если выключили — вернуть всё назад
            restoreAllTombStands();
        }

        if (subwayActive && mc.player.ticksExisted % 100 == 0) {
            dumpStats();
        }
    }

    // ====== ANCIENT TOMB (сущности) ======

    private void processTombStands() {
        for (Entity e : mc.world.loadedEntityList) {
            if (!(e instanceof EntityArmorStand)) continue;
            EntityArmorStand stand = (EntityArmorStand) e;

            boolean inTomb = isInTomb(stand.posX, stand.posY, stand.posZ);
            boolean isBow = isBowStand(stand);

            if (inTomb && !isBow) {
                hideTombStand(stand);
            } else {
                restoreTombStand(stand);
            }
        }
    }

    private void hideTombStand(EntityArmorStand stand) {
        UUID id = stand.getUniqueID();

        if (!armorBackup.containsKey(id)) {
            EntityEquipmentSlot[] slots = EntityEquipmentSlot.values();
            ItemStack[] saved = new ItemStack[slots.length];
            for (EntityEquipmentSlot slot : slots) {
                ItemStack cur = stand.getItemStackFromSlot(slot);
                saved[slot.ordinal()] = cur.isEmpty() ? ItemStack.EMPTY : cur.copy();
            }
            armorBackup.put(id, saved);
            log("TOMB: backup armor for stand " + id);
        }

        for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
            stand.setItemStackToSlot(slot, ItemStack.EMPTY);
        }
        stand.setInvisible(true);
        hiddenStands.add(id);
    }

    private void restoreTombStand(EntityArmorStand stand) {
        UUID id = stand.getUniqueID();
        if (!armorBackup.containsKey(id) && !hiddenStands.contains(id)) return;

        ItemStack[] saved = armorBackup.remove(id);
        if (saved != null) {
            for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
                int idx = slot.ordinal();
                if (idx >= saved.length) continue;
                ItemStack st = saved[idx];
                stand.setItemStackToSlot(slot, (st == null || st.isEmpty()) ? ItemStack.EMPTY : st.copy());
            }
            log("TOMB: restore armor for stand " + id);
        }
        stand.setInvisible(false);
        hiddenStands.remove(id);
    }

    private void restoreAllTombStands() {
        if (mc.world == null) return;
        for (Entity e : mc.world.loadedEntityList) {
            if (e instanceof EntityArmorStand) {
                restoreTombStand((EntityArmorStand) e);
            }
        }
        armorBackup.clear();
        hiddenStands.clear();
    }

    private boolean isBowStand(EntityArmorStand stand) {
        if (stand == null) return false;
        boolean hasBow = false;
        for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
            ItemStack stack = stand.getItemStackFromSlot(slot);
            if (stack.isEmpty()) continue;
            if (stack.getItem() == Items.BOW) {
                hasBow = true;
            } else {
                return false;
            }
        }
        return hasBow;
    }

    private boolean isInTomb(double x, double y, double z) {
        return x >= TOMB_MIN_X && x <= TOMB_MAX_X
                && y >= TOMB_MIN_Y && y <= TOMB_MAX_Y
                && z >= TOMB_MIN_Z && z <= TOMB_MAX_Z;
    }

    private boolean isInSubway(double x, double y, double z) {
        return x >= SUB_MIN_X && x <= SUB_MAX_X
                && y >= SUB_MIN_Y && y <= SUB_MAX_Y
                && z >= SUB_MIN_Z && z <= SUB_MAX_Z;
    }

    // ====== NETTY (только Subway) ======

    private void injectHandler() {
        try {
            if (mc.getConnection() == null) return;
            if (mc.getConnection().getNetworkManager() == null) return;
            if (mc.getConnection().getNetworkManager().channel() == null) return;

            if (mc.getConnection().getNetworkManager().channel().pipeline().get(HANDLER_NAME) != null) return;

            mc.getConnection().getNetworkManager().channel()
                    .pipeline().addBefore("packet_handler", HANDLER_NAME,
                            new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                    Object result = handlePacket(msg);
                                    if (result == null) return;
                                    super.channelRead(ctx, result);
                                }
                            });
            log("Handler injected");
        } catch (Exception e) {
            logError("injectHandler failed", e);
        }
    }

    private void removeHandler() {
        try {
            if (mc.getConnection() != null
                    && mc.getConnection().getNetworkManager() != null
                    && mc.getConnection().getNetworkManager().channel() != null
                    && mc.getConnection().getNetworkManager().channel().pipeline().get(HANDLER_NAME) != null) {
                mc.getConnection().getNetworkManager().channel().pipeline().remove(HANDLER_NAME);
                log("Handler removed");
            }
        } catch (Exception e) {
            logError("removeHandler failed", e);
        }
    }

    private Object handlePacket(Object msg) {
        if (!subwayActive) return msg;
        if (!isSubwayEnabled()) return msg;

        // --- SPacketCustomSound ---
        if (msg instanceof SPacketCustomSound) {
            SPacketCustomSound sound = (SPacketCustomSound) msg;
            statSoundTotal++;
            double x = sound.getX(), y = sound.getY(), z = sound.getZ();
            if (isInSubway(x, y, z)) {
                statSoundCancelled++;
                return null;
            }
            return msg;
        }

        // --- SPacketMultiBlockChange ---
        if (msg instanceof SPacketMultiBlockChange) {
            SPacketMultiBlockChange change = (SPacketMultiBlockChange) msg;
            statBlocksTotal++;
            try {
                SPacketMultiBlockChange.BlockUpdateData[] data = change.getChangedBlocks();
                if (data == null || data.length == 0) return msg;
                for (SPacketMultiBlockChange.BlockUpdateData d : data) {
                    if (d == null) continue;
                    BlockPos pos = d.getPos();
                    if (pos != null && isInSubway(pos.getX(), pos.getY(), pos.getZ())) {
                        statBlocksCancelled++;
                        return null;
                    }
                }
            } catch (Throwable t) {
                logError("BLOCKS error", t);
            }
            return msg;
        }

        // --- SPacketUpdateTileEntity ---
        if (msg instanceof SPacketUpdateTileEntity) {
            SPacketUpdateTileEntity tile = (SPacketUpdateTileEntity) msg;
            statTileTotal++;
            BlockPos pos = tile.getPos();
            if (pos != null && isInSubway(pos.getX(), pos.getY(), pos.getZ())) {
                statTileCancelled++;
                return null;
            }
            return msg;
        }

        return msg;
    }

    // ====== CONNECTION ======

    @SubscribeEvent
    public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        log("Client connected");
        subwayActive = false;
        resetStats();
        restoreAllTombStands();
        injectHandler();
    }

    @SubscribeEvent
    public void onClientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        log("Client disconnected");
        dumpStats();
        removeHandler();
        subwayActive = false;
        resetStats();
        restoreAllTombStands();
    }
}