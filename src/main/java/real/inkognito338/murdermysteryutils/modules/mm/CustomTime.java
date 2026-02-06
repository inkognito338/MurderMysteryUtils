package real.inkognito338.murdermysteryutils.modules.mm;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.SPacketTimeUpdate;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import real.inkognito338.murdermysteryutils.modules.Module;
import real.inkognito338.murdermysteryutils.modules.settings.Setting;
import real.inkognito338.murdermysteryutils.modules.settings.SettingType;

public class CustomTime extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();
    private static final String HANDLER_NAME = "sky_color_handler";
    private static final int MAX_TIME = 14000; // Исправлено на 14000

    public CustomTime() {
        super("CustomTime");
        // Исправлено: максимальное значение 14000
        addSetting(new Setting("Time", SettingType.NUMBER, 6000, 0, MAX_TIME));
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
            if (mc.getConnection() != null && mc.getConnection().getNetworkManager().channel() != null) {
                if (mc.getConnection().getNetworkManager().channel().pipeline().get(HANDLER_NAME) == null) {
                    mc.getConnection().getNetworkManager().channel().pipeline().addBefore("packet_handler", HANDLER_NAME, new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            if (isToggled()) {
                                if (msg instanceof SPacketTimeUpdate) {
                                    return; // Блокируем пакет обновления времени
                                }
                            }
                            super.channelRead(ctx, msg);
                        }
                    });
                }
            }
        } catch (Exception ignored) {}
    }

    private void removeHandler() {
        try {
            if (mc.getConnection() != null && mc.getConnection().getNetworkManager().channel() != null) {
                if (mc.getConnection().getNetworkManager().channel().pipeline().get(HANDLER_NAME) != null) {
                    mc.getConnection().getNetworkManager().channel().pipeline().remove(HANDLER_NAME);
                }
            }
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || mc.world == null || !isToggled()) return;

        // Периодическая проверка инъекции
        if (mc.player != null && mc.player.ticksExisted % 40 == 0) {
            injectHandler();
        }

        // Получаем значение времени с учетом ограничения 14000
        Object timeValue = getSettingByName("Time").getValue();
        long worldTime;

        if (timeValue instanceof Integer) {
            int time = (Integer) timeValue;
            // Ограничиваем значение до 14000
            worldTime = Math.min(time, MAX_TIME);
        } else if (timeValue instanceof Double) {
            double time = (Double) timeValue;
            // Ограничиваем значение до 14000
            worldTime = (long) Math.min(time, MAX_TIME);
        } else {
            worldTime = 6000L; // Значение по умолчанию (полдень)
        }

        mc.world.setWorldTime(worldTime);
    }
}