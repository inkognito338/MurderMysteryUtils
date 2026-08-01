package real.inkognito338.murdermysteryutils.modules;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.SPacketTimeUpdate;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;

import java.time.LocalTime;
import java.time.ZoneId;

public class CustomTime extends Module {

    private static final boolean DEBUG = false;

    private final Minecraft mc = Minecraft.getMinecraft();
    private static final String HANDLER_NAME = "custom_time_handler";

    private static final long TICKS_PER_DAY = 24000L;
    private static final long MS_PER_DAY = 24L * 60L * 60L * 1000L;
    private static final long OFFSET_MS = 6L * 60L * 60L * 1000L;

    private final Setting mode = new Setting("Mode", SettingType.MODE, "Custom", new String[]{"Custom", "RealTime"});
    private final Setting time = new Setting("Time", SettingType.NUMBER, 6000, 0, 24000);

    public CustomTime() {
        super("CustomTime");
        addSetting(mode);
        addSetting(time);
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
        injectHandler();
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        removeHandler();
    }

    private void injectHandler() {
        try {
            if (mc.getConnection() != null && mc.getConnection().getNetworkManager().channel().pipeline().get(HANDLER_NAME) == null) {
                mc.getConnection().getNetworkManager().channel().pipeline().addBefore("packet_handler", HANDLER_NAME, new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        if (msg instanceof SPacketTimeUpdate) {
                            return;
                        }
                        super.channelRead(ctx, msg);
                    }
                });
            }
        } catch (Exception ignored) {}
    }

    private void removeHandler() {
        try {
            if (mc.getConnection() != null && mc.getConnection().getNetworkManager().channel().pipeline().get(HANDLER_NAME) != null) {
                mc.getConnection().getNetworkManager().channel().pipeline().remove(HANDLER_NAME);
            }
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || mc.world == null || !isToggled()) return;

        if (mc.player != null && mc.player.ticksExisted % 40 == 0) {
            injectHandler();
        }

        long worldTime;
        if ("RealTime".equalsIgnoreCase(mode.getMode())) {
            worldTime = getRealTimeInTicks();
        } else {
            worldTime = ((Number) time.getValue()).longValue();
        }

        mc.world.setWorldTime(worldTime);

        if (DEBUG && "RealTime".equalsIgnoreCase(mode.getMode())
                && mc.player != null && mc.player.ticksExisted % 20 == 0) {
            LocalTime now = LocalTime.now(ZoneId.systemDefault());
            System.out.println("[CustomTime DEBUG] real=" + now
                    + " zone=" + ZoneId.systemDefault()
                    + " millisOfDay=" + (now.toNanoOfDay() / 1_000_000L)
                    + " -> tick=" + worldTime);
        }
    }

    private static long getRealTimeInTicks() {
        LocalTime now = LocalTime.now(ZoneId.systemDefault());
        long millisOfDay = now.toNanoOfDay() / 1_000_000L;

        long shifted = millisOfDay - OFFSET_MS;
        if (shifted < 0) shifted += MS_PER_DAY;

        return (shifted * TICKS_PER_DAY) / MS_PER_DAY;
    }
}