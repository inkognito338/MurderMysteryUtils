package real.inkognito338.murdermysteryutils.modules;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.util.concurrent.ScheduledFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketKeepAlive;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 15.10.2026
 * <p>
 * FakePing — искусственное увеличение пинга.
 * Входящий SPacketKeepAlive задерживается на Delay мс перед передачей дальше по пайплайну.
 * Порядок keepalive-пакетов сохраняется.
 * При изменении Delay на лету отложенные пакеты пересчитываются по новому значению.
 * При выключении все отложенные keepalive доставляются немедленно.
 */
@SuppressWarnings("SpellCheckingInspection")
@SideOnly(Side.CLIENT)
public class FakePing extends Module {

    private static final Logger LOGGER = LogManager.getLogger("MurderMysteryUtils/FakePing");

    private static final String HANDLER_NAME = "mmu_fakeping_delay";

    private final Minecraft mc = Minecraft.getMinecraft();

    private volatile boolean active = false;
    private volatile NetworkManager handlerNm;

    // Отложенные пакеты в порядке прихода
    private final Queue<Pending> pending = new ConcurrentLinkedQueue<>();

    // Время (ms), раньше которого нельзя доставлять следующий keepalive. Меняется в Netty-потоке (и сбрасывается при включении).
    private volatile long lastDeliverAt = 0L;

    // Последнее применённое значение задержки (для обнаружения изменений на лету)
    private volatile long appliedDelay = -1L;

    public FakePing() {
        super("FakePing");
        this.addSetting(new Setting("Delay", SettingType.NUMBER, 200.0, 0.0, 30000.0));
    }

    private static class Pending {
        final ChannelHandlerContext ctx;
        final Object msg;
        final long receivedAt;
        volatile ScheduledFuture<?> future;

        Pending(ChannelHandlerContext ctx, Object msg, long receivedAt) {
            this.ctx = ctx;
            this.msg = msg;
            this.receivedAt = receivedAt;
        }
    }

    @Override
    public void onEnable() {
        if (active || mc.getConnection() == null) {
            return;
        }
        try {
            lastDeliverAt = 0L;
            appliedDelay = delayMs();
            if (!installHandler()) {
                return;
            }
            active = true;
            MinecraftForge.EVENT_BUS.register(this);
        } catch (Throwable t) {
            LOGGER.error("Failed to enable FakePing", t);
            active = false;
            MinecraftForge.EVENT_BUS.unregister(this);
            dropPending();
            removeHandler();
        }
    }

    @Override
    public void onDisable() {
        if (!active) {
            return;
        }
        active = false;
        MinecraftForge.EVENT_BUS.unregister(this);

        final NetworkManager nm = handlerNm;
        handlerNm = null;
        if (nm == null) {
            dropPending();
            return;
        }

        // Одной задачей в Netty-потоке: сначала отдаём всё отложенное по порядку, потом снимаем хендлер.
        try {
            nm.channel().eventLoop().execute(() -> {
                flushPending();
                removeFromPipeline(nm);
            });
        } catch (Throwable t) {
            // Event loop недоступен (канал закрыт)
            dropPending();
            removeFromPipeline(nm);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (!active || event.phase != TickEvent.Phase.END) {
            return;
        }
        // Дисконнект: выключаемся сами
        if (mc.player == null || mc.world == null || mc.getConnection() == null) {
            active = false;
            MinecraftForge.EVENT_BUS.unregister(this);
            dropPending();
            removeHandler();
            toggle();
            return;
        }
        ensureHandler();

        // Delay изменили на лету — пересчитываем уже отложенные пакеты
        long d = delayMs();
        if (d != appliedDelay) {
            appliedDelay = d;
            NetworkManager nm = handlerNm;
            if (nm != null && !pending.isEmpty()) {
                try {
                    nm.channel().eventLoop().execute(this::rescheduleAll);
                } catch (Throwable t) {
                    LOGGER.error("Failed to reschedule keepalive packets", t);
                }
            }
        }
    }

    /** Если соединение сменилось или хендлер пропал — ставим заново. */
    private void ensureHandler() {
        NetHandlerPlayClient connection = mc.getConnection();
        if (connection == null) {
            return;
        }
        NetworkManager cur = connection.getNetworkManager();
        NetworkManager nm = handlerNm;
        boolean missing = nm == null || nm != cur || nm.channel().pipeline().get(HANDLER_NAME) == null;
        if (!missing) {
            return;
        }
        dropPending();
        removeHandler();
        lastDeliverAt = 0L;
        installHandler();
    }

    private long delayMs() {
        Setting s = getSettingByName("Delay");
        double v = 0;
        if (s != null && s.getValue() instanceof Number) {
            v = ((Number) s.getValue()).doubleValue();
        }
        return (long) Math.max(0, Math.min(60000, v));
    }

    /** Только Netty-поток: немедленно доставить всё отложенное по порядку. */
    private void flushPending() {
        Pending[] all = pending.toArray(new Pending[0]);
        for (Pending p : all) {
            if (pending.remove(p)) {
                ScheduledFuture<?> f = p.future;
                if (f != null) {
                    f.cancel(false);
                }
                deliver(p);
            }
        }
        lastDeliverAt = 0L;
    }

    /** Только Netty-поток: пересчитать сроки всех отложенных пакетов по текущему Delay. */
    private void rescheduleAll() {
        long delay = delayMs();
        long now = System.currentTimeMillis();
        long prev = 0L;

        Pending[] all = pending.toArray(new Pending[0]);
        for (Pending p : all) {
            ScheduledFuture<?> f = p.future;
            if (f != null) {
                f.cancel(false);
            }
            long at = Math.max(p.receivedAt + delay, prev);
            prev = at;
            if (at <= now) {
                if (pending.remove(p)) {
                    deliver(p);
                }
            } else {
                schedule(p, at - now);
            }
        }
        lastDeliverAt = prev;
    }

    private void schedule(final Pending p, long wait) {
        p.future = p.ctx.executor().schedule(() -> {
            if (pending.remove(p)) {
                deliver(p);
            }
        }, Math.max(0, wait), TimeUnit.MILLISECONDS);
    }

    private void deliver(Pending p) {
        try {
            if (p.ctx.channel().isActive()) {
                p.ctx.fireChannelRead(p.msg);
            }
        } catch (Throwable t) {
            LOGGER.error("Failed to deliver delayed keepalive packet", t);
        }
    }

    /** Отбросить отложенные пакеты (соединение сменилось / закрыто). */
    private void dropPending() {
        Pending p;
        while ((p = pending.poll()) != null) {
            ScheduledFuture<?> f = p.future;
            if (f != null) {
                f.cancel(false);
            }
        }
    }

    /** @return true, если хендлер установлен; false, если соединения нет. */
    private boolean installHandler() {
        removeHandler();

        NetHandlerPlayClient connection = mc.getConnection();
        if (connection == null) {
            return false;
        }

        final NetworkManager nm = connection.getNetworkManager();
        ChannelPipeline pipeline = nm.channel().pipeline();
        if (pipeline.get(HANDLER_NAME) != null) {
            pipeline.remove(HANDLER_NAME);
        }
        handlerNm = nm;

        ChannelDuplexHandler handler = new ChannelDuplexHandler() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (!(msg instanceof SPacketKeepAlive)) {
                    super.channelRead(ctx, msg);
                    return;
                }

                // Модуль выключается: сначала отдаём то, что уже ждёт, чтобы не нарушить порядок
                if (!active) {
                    flushPending();
                    super.channelRead(ctx, msg);
                    return;
                }

                long now = System.currentTimeMillis();
                long deliverAt = Math.max(now + delayMs(), lastDeliverAt);
                lastDeliverAt = deliverAt;
                long wait = deliverAt - now;

                if (wait <= 0 && pending.isEmpty()) {
                    super.channelRead(ctx, msg);
                    return;
                }

                final Pending p = new Pending(ctx, msg, now);
                pending.add(p);
                schedule(p, wait);
            }
        };

        // Перед packet_handler, иначе входящие пакеты до нас не дойдут
        if (pipeline.get("packet_handler") != null) {
            pipeline.addBefore("packet_handler", HANDLER_NAME, handler);
        } else {
            pipeline.addLast(HANDLER_NAME, handler);
        }
        return true;
    }

    private void removeHandler() {
        NetworkManager nm = handlerNm;
        handlerNm = null;
        if (nm != null) {
            removeFromPipeline(nm);
        }
    }

    private void removeFromPipeline(NetworkManager nm) {
        try {
            ChannelPipeline pipeline = nm.channel().pipeline();
            if (pipeline.get(HANDLER_NAME) != null) {
                pipeline.remove(HANDLER_NAME);
            }
        } catch (Throwable t) {
            LOGGER.error("Failed to remove FakePing handler", t);
        }
    }
}