package real.inkognito338.murdermysteryutils.modules;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.network.play.server.SPacketChangeGameState;
import net.minecraft.network.play.server.SPacketJoinGame;
import net.minecraft.network.play.server.SPacketPlayerAbilities;
import net.minecraft.network.play.server.SPacketRespawn;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.GameType;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.ModuleManager;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;

@SideOnly(Side.CLIENT)
public class FakeGM extends Module {

    private static final String HANDLER_NAME = "fakegm_abilities_handler";

    // GameState id для CHANGE_GAME_MODE (см. SPacketChangeGameState)
    private static final int CHANGE_GAME_MODE = 3;

    // Минимальный интервал между ломаниями одного и того же блока при удержании
    private static final int SAME_BLOCK_COOLDOWN_TICKS = 4;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Setting mode;
    private final Setting instantBreak;

    // Серверное состояние полёта — обновляется из SPacketPlayerAbilities.
    private volatile boolean serverAllowFlying = false;
    private volatile boolean serverPacketReceived = false;

    // Своё состояние ЛКМ — чтобы ловить момент нажатия (фронт) и удержание.
    private boolean prevAttackDown = false;

    // Последний сломанный блок и тик, когда это было — для кулдауна
    // при удержании ЛКМ по одному и тому же месту.
    private BlockPos lastBrokenPos = null;
    private int lastBreakTick = -1000;

    public FakeGM() {
        super("FakeGM");
        mode = new Setting("Mode", SettingType.MODE, "Creative",
                "Survival", "Creative", "Adventure", "Spectator");
        addSetting(mode);

        instantBreak = new Setting("InstantBreak", SettingType.BOOLEAN, false);
        addSetting(instantBreak);
    }

    private GameType getSelectedGameType() {
        Setting s = getSettingByName("Mode");
        String val = (s != null && s.getValue() instanceof String) ? (String) s.getValue() : "Creative";
        switch (val) {
            case "Survival":  return GameType.SURVIVAL;
            case "Adventure": return GameType.ADVENTURE;
            case "Spectator": return GameType.SPECTATOR;
            default:          return GameType.CREATIVE;
        }
    }

    private boolean isInstantBreakEnabled() {
        Setting s = getSettingByName("InstantBreak");
        return s != null && Boolean.TRUE.equals(s.getValue());
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
        injectHandler();
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        restoreRealGameType();
        restoreFlight();
        removeHandler();

        prevAttackDown = false;
        lastBrokenPos = null;
        lastBreakTick = -1000;
        if (mc.playerController != null) {
            mc.playerController.resetBlockRemoving();
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (mc.playerController == null || mc.player == null) return;

        // Переинжект хендлера — pipeline может быть пересоздан после
        // респавна/смены мира, и наш хендлер мог потеряться.
        if (mc.player.ticksExisted % 100 == 0) {
            injectHandler();
        }

        Module freecam = ModuleManager.getModuleByName("Freecam");
        if (freecam != null && freecam.isToggled()) return;

        // --- 1. Каждый тик проверяем геймтайп ---
        GameType selected = getSelectedGameType();
        if (mc.playerController.getCurrentGameType() != selected) {
            mc.playerController.setGameType(selected);
        }

        // --- 1.5. Instant break ---
        handleInstantBreak();

        // --- 2. Flight отражает серверное состояние ---
        if (!serverPacketReceived) return;

        boolean allowed = serverAllowFlying;

        boolean changed = false;
        if (mc.player.capabilities.allowFlying != allowed) {
            mc.player.capabilities.allowFlying = allowed;
            changed = true;
        }
        if (!allowed && mc.player.capabilities.isFlying) {
            mc.player.capabilities.isFlying = false;
            changed = true;
        }
        if (changed) {
            mc.player.sendPlayerAbilities();
        }
    }

    // ====== INSTANT BREAK ======

    private int blockHitDelay = 0;

    private void handleInstantBreak() {
        if (!isInstantBreakEnabled()) {
            prevAttackDown = mc.gameSettings.keyBindAttack.isKeyDown();
            return;
        }

        GameType current = mc.playerController.getCurrentGameType();
        if (current != GameType.SURVIVAL && current != GameType.ADVENTURE) {
            prevAttackDown = mc.gameSettings.keyBindAttack.isKeyDown();
            return;
        }

        if (mc.currentScreen != null || !mc.inGameHasFocus) {
            prevAttackDown = mc.gameSettings.keyBindAttack.isKeyDown();
            return;
        }

        // ваниль декрементит blockHitDelay каждый тик
        if (blockHitDelay > 0) {
            --blockHitDelay;
        }

        boolean attackDown = mc.gameSettings.keyBindAttack.isKeyDown();
        boolean justPressed = attackDown && !prevAttackDown;
        prevAttackDown = attackDown;

        if (!attackDown) {
            lastBrokenPos = null;
            return;
        }

        RayTraceResult mop = mc.objectMouseOver;
        if (mop == null || mop.typeOfHit != RayTraceResult.Type.BLOCK) return;

        BlockPos pos = mop.getBlockPos();
        EnumFacing side = mop.sideHit;

        if (mc.world.isAirBlock(pos)) return;
        if (!mc.world.getWorldBorder().contains(pos)) return;

        // === ВАНИЛЬ: onPlayerDamageBlock ===
        // при удержании — сначала blockHitDelay
        if (!justPressed && blockHitDelay > 0) {
            return;   // ← это и есть "задержка как в гм1" при удержании
        }

        // === clickBlockCreative 1:1 ===
        mc.getTutorial().onHitBlock(mc.world, pos, mc.world.getBlockState(pos), 1.0F);
        mc.player.connection.sendPacket(new CPacketPlayerDigging(
                CPacketPlayerDigging.Action.START_DESTROY_BLOCK, pos, side));

        if (!ForgeHooks.onLeftClickBlock(
                mc.player, pos, side,
                ForgeHooks.rayTraceEyeHitVec(mc.player, mc.playerController.getBlockReachDistance() + 1.0F)
        ).isCanceled()) {
            if (!mc.world.extinguishFire(mc.player, pos, side)) {
                mc.playerController.onPlayerDestroyBlock(pos);
            }
        }

        // ваниль ставит blockHitDelay = 5 после каждого слома
        blockHitDelay = 5;

        lastBrokenPos = pos.toImmutable();
        lastBreakTick = mc.player.ticksExisted;

        mc.effectRenderer.addBlockHitEffects(pos, mop);
        mc.player.swingArm(EnumHand.MAIN_HAND);
    }

    // ====== NETTY HANDLER ======

    private void injectHandler() {
        try {
            if (mc.getConnection() == null) return;
            if (mc.getConnection().getNetworkManager().channel().pipeline().get(HANDLER_NAME) != null) return;

            mc.getConnection().getNetworkManager().channel()
                    .pipeline().addBefore("packet_handler", HANDLER_NAME,
                            new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                    Object result = handlePacket(msg);
                                    if (result == null) {
                                        return;
                                    }
                                    super.channelRead(ctx, result);
                                }
                            });
        } catch (Exception ignored) {}
    }

    private void removeHandler() {
        try {
            if (mc.getConnection() != null) {
                if (mc.getConnection().getNetworkManager().channel().pipeline().get(HANDLER_NAME) != null) {
                    mc.getConnection().getNetworkManager().channel().pipeline().remove(HANDLER_NAME);
                }
            }
        } catch (Exception ignored) {}
    }

    private Object handlePacket(Object msg) {
        if (msg instanceof SPacketPlayerAbilities) {
            SPacketPlayerAbilities abilities = (SPacketPlayerAbilities) msg;
            serverAllowFlying = abilities.isAllowFlying();
            serverPacketReceived = true;
            return msg;
        }

        if (msg instanceof SPacketRespawn || msg instanceof SPacketJoinGame) {
            serverPacketReceived = false;
            serverAllowFlying = false;
            prevAttackDown = false;
            lastBrokenPos = null;
            lastBreakTick = -1000;
            Minecraft.getMinecraft().addScheduledTask(this::injectHandler);
            return msg;
        }

        if (msg instanceof SPacketChangeGameState) {
            SPacketChangeGameState state = (SPacketChangeGameState) msg;
            if (state.getGameState() == CHANGE_GAME_MODE) {
                return null;
            }
            return msg;
        }

        return msg;
    }

    // ====== FORGE EVENTS ======

    @SubscribeEvent
    public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        injectHandler();
    }

    @SubscribeEvent
    public void onClientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        removeHandler();
        serverPacketReceived = false;
        serverAllowFlying = false;
        prevAttackDown = false;
        lastBrokenPos = null;
        lastBreakTick = -1000;
    }

    private GameType getRealGameType() {
        if (mc.player == null || mc.getConnection() == null) return null;

        NetHandlerPlayClient connection = mc.getConnection();
        NetworkPlayerInfo info = connection.getPlayerInfo(mc.player.getGameProfile().getId());

        return (info != null) ? info.getGameType() : null;
    }

    private void restoreRealGameType() {
        if (mc.playerController == null) return;

        GameType real = getRealGameType();
        if (real != null) {
            mc.playerController.setGameType(real);
        }
    }

    private void restoreFlight() {
        if (mc.player == null) return;

        if (!serverPacketReceived) return;

        boolean allowed = serverAllowFlying;
        mc.player.capabilities.allowFlying = allowed;
        if (!allowed) {
            mc.player.capabilities.isFlying = false;
        }
        mc.player.sendPlayerAbilities();
    }
}