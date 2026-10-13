package real.inkognito338.murdermysteryutils.modules;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.SPacketTabComplete;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;

import java.util.LinkedHashSet;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 26.09.2026
 *
 * Фикс TAB-complete ответа сервера:
 *   FixDuplicates  — убирает дублирующиеся варианты
 *   HideColonCmds  — скрывает варианты, содержащие двоеточие (например "minecraft:give")
 */
public class TabCompleteFix extends Module {

    private static final String HANDLER_NAME = "tab_complete_fix_handler";
    private final Minecraft mc = Minecraft.getMinecraft();

    private final Setting fixDuplicates;
    private final Setting hideColonCmds;

    public TabCompleteFix() {
        super("TabCompleteFix");

        this.fixDuplicates = new Setting("FixDuplicates", SettingType.BOOLEAN, true);
        this.hideColonCmds = new Setting("HideColonCmds", SettingType.BOOLEAN, true);

        addSetting(fixDuplicates);
        addSetting(hideColonCmds);
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

    // ── Forge events ─────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onServerConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        injectHandler();
    }

    @SubscribeEvent
    public void onServerDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        removeHandler();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isToggled()) return;
        if (mc.player == null) return;

        // Каждые 100 тиков проверяем, что handler всё ещё в pipeline.
        // Сервер или другой мод может пересобрать pipeline — инжектим заново.
        if (mc.player.ticksExisted % 100 == 0) {
            injectHandler();
        }
    }

    // ── Netty pipeline ───────────────────────────────────────────────────────

    private void injectHandler() {
        try {
            if (mc.getConnection() == null) return;
            if (mc.getConnection().getNetworkManager().channel()
                    .pipeline().get(HANDLER_NAME) != null) return;

            mc.getConnection().getNetworkManager().channel()
                    .pipeline().addBefore("packet_handler", HANDLER_NAME,
                            new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx,
                                                        Object msg) throws Exception {
                                    if (isToggled() && msg instanceof SPacketTabComplete) {
                                        msg = process((SPacketTabComplete) msg);
                                    }
                                    super.channelRead(ctx, msg);
                                }
                            });
        } catch (Exception ignored) {}
    }

    private void removeHandler() {
        try {
            if (mc.getConnection() == null) return;
            if (mc.getConnection().getNetworkManager().channel()
                    .pipeline().get(HANDLER_NAME) != null) {
                mc.getConnection().getNetworkManager().channel()
                        .pipeline().remove(HANDLER_NAME);
            }
        } catch (Exception ignored) {}
    }

    // ── Обработка пакета ─────────────────────────────────────────────────────

    private SPacketTabComplete process(SPacketTabComplete packet) {
        String[] original = packet.getMatches();
        if (original == null || original.length == 0) return packet;

        boolean doDedupe = (boolean) fixDuplicates.getValue();
        boolean doColon  = (boolean) hideColonCmds.getValue();

        if (!doDedupe && !doColon) return packet;

        LinkedHashSet<String> seen = doDedupe ? new LinkedHashSet<>() : null;

        int kept = 0;
        String[] buffer = new String[original.length];

        for (String s : original) {
            if (s == null) continue;
            if (doColon && s.contains(":")) continue;
            if (doDedupe) {
                if (seen.add(s)) buffer[kept++] = s;
            } else {
                buffer[kept++] = s;
            }
        }

        if (kept == original.length) return packet;

        String[] filtered = new String[kept];
        System.arraycopy(buffer, 0, filtered, 0, kept);
        return new SPacketTabComplete(filtered);
    }
}