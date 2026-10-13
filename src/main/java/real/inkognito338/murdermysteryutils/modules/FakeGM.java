package real.inkognito338.murdermysteryutils.modules;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.network.play.server.SPacketChangeGameState;
import net.minecraft.network.play.server.SPacketJoinGame;
import net.minecraft.network.play.server.SPacketPlayerAbilities;
import net.minecraft.network.play.server.SPacketRespawn;
import net.minecraft.world.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;

@SideOnly(Side.CLIENT)
public class FakeGM extends Module {

    private static final String HANDLER_NAME = "fakegm_abilities_handler";

    // GameState id для CHANGE_GAME_MODE (см. SPacketChangeGameState)
    private static final int CHANGE_GAME_MODE = 3;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Setting mode;

    // Серверное состояние полёта — обновляется из SPacketPlayerAbilities.
    private volatile boolean serverAllowFlying = false;
    private volatile boolean serverPacketReceived = false;

    public FakeGM() {
        super("FakeGM");
        mode = new Setting("Mode", SettingType.MODE, "Creative",
                "Survival", "Creative", "Adventure", "Spectator");
        addSetting(mode);
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

        // --- 1. Каждый тик проверяем геймтайп ---
        GameType selected = getSelectedGameType();
        if (mc.playerController.getCurrentGameType() != selected) {
            mc.playerController.setGameType(selected);
        }

        // --- 2. Flight отражает серверное состояние ---
        // Пока свежий пакет SPacketPlayerAbilities не пришёл (например
        // после смены мира) — не трогаем allowFlying вообще. Ваниль
        // сама выставит его по геймтайпу из SPacketRespawn.
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
        // --- SPacketPlayerAbilities: запоминаем серверное состояние ---
        if (msg instanceof SPacketPlayerAbilities) {
            SPacketPlayerAbilities abilities = (SPacketPlayerAbilities) msg;
            serverAllowFlying = abilities.isAllowFlying();
            serverPacketReceived = true;
            return msg;
        }

        // --- Смена мира: сбрасываем состояние флая ---
        // SPacketRespawn приходит при смене измерения/респавне.
        // SPacketJoinGame приходит при первом входе в мир.
        // В обоих случаях старый serverAllowFlying уже неактуален —
        // ваниль выставит allowFlying по геймтайпу из этого пакета,
        // а мы не должны ему мешать до следующего SPacketPlayerAbilities.
        if (msg instanceof SPacketRespawn || msg instanceof SPacketJoinGame) {
            serverPacketReceived = false;
            serverAllowFlying = false;
            // Хендлер мог быть потерян при пересоздании pipeline —
            // попробуем переинжектить (на случай, если pipeline уже новый).
            // Делать это прямо тут нельзя (мы в Netty-потоке), поэтому
            // отложим через Minecraft.addScheduledTask.
            Minecraft.getMinecraft().addScheduledTask(this::injectHandler);
            return msg;
        }

        // --- SPacketChangeGameState: смена геймтайпа ---
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