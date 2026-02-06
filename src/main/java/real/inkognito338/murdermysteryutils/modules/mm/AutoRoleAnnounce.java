package real.inkognito338.murdermysteryutils.modules.mm;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.SPacketTitle;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import real.inkognito338.murdermysteryutils.modules.Module;
import real.inkognito338.murdermysteryutils.modules.settings.Setting;
import real.inkognito338.murdermysteryutils.modules.settings.SettingType;

import java.util.regex.Pattern;

@SideOnly(Side.CLIENT)
public class AutoRoleAnnounce extends Module {

    private static AutoRoleAnnounce INSTANCE;
    private final Minecraft mc = Minecraft.getMinecraft();
    private static final String HANDLER_NAME = "auto_role_announce_handler";

    private final Pattern innocentPattern = Pattern.compile(
            ".*РОЛЬ:\\s*МИРНЫЙ\\s*ЖИТЕЛЬ.*|.*ROLE:\\s*INNOCENT.*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private final Pattern detectivePattern = Pattern.compile(
            ".*РОЛЬ:\\s*ДЕТЕКТИВ.*|.*ROLE:\\s*DETECTIVE.*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private final Pattern murdererPattern = Pattern.compile(
            ".*РОЛЬ:\\s*УБИЙЦА.*|.*ROLE:\\s*MURDERER.*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public AutoRoleAnnounce() {
        super("AutoRoleAnnounce");
        INSTANCE = this;

        addSetting(new Setting("AnnounceInnocent", SettingType.BOOLEAN, true));
        addSetting(new Setting("AnnounceDetective", SettingType.BOOLEAN, true));
        addSetting(new Setting("AnnounceMurderer", SettingType.BOOLEAN, true));
    }

    public static AutoRoleAnnounce getInstance() {
        return INSTANCE;
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

    @SubscribeEvent
    public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        injectHandler();
    }

    @SubscribeEvent
    public void onClientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        removeHandler();
    }

    /* ================= NETTY HANDLER ================= */
    private void injectHandler() {
        try {
            if (mc.getConnection() == null ||
                    mc.getConnection().getNetworkManager() == null ||
                    mc.getConnection().getNetworkManager().channel() == null)
                return;

            if (mc.getConnection().getNetworkManager()
                    .channel().pipeline().get(HANDLER_NAME) != null)
                return;

            mc.getConnection().getNetworkManager().channel()
                    .pipeline().addBefore("packet_handler", HANDLER_NAME,
                            new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                    if (isToggled() && msg instanceof SPacketTitle) {
                                        handleTitlePacket((SPacketTitle) msg);
                                    }
                                    super.channelRead(ctx, msg);
                                }
                            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void removeHandler() {
        try {
            if (mc.getConnection() != null &&
                    mc.getConnection().getNetworkManager() != null &&
                    mc.getConnection().getNetworkManager().channel() != null &&
                    mc.getConnection().getNetworkManager()
                            .channel().pipeline().get(HANDLER_NAME) != null) {
                mc.getConnection().getNetworkManager()
                        .channel().pipeline().remove(HANDLER_NAME);
            }
        } catch (Exception ignored) {}
    }

    /** Метод для обработки пакета SPacketTitle */
    private void handleTitlePacket(SPacketTitle packet) {
        if (!isToggled()) return;

        SPacketTitle.Type type = packet.getType();

        // Проверяем только TITLE (основной заголовок)
        if (type != SPacketTitle.Type.TITLE) return;

        net.minecraft.util.text.ITextComponent message = packet.getMessage();
        if (message == null) return;

        String text = message.getUnformattedText();
        if (text == null || text.isEmpty()) return;

        processRoleText(text);
    }

    /** Основной метод для обработки текста из title */
    private void processRoleText(String text) {
        if (!isToggled() || text == null || text.isEmpty()) return;

        String role = detectRole(text);
        if (role.isEmpty()) return;

        boolean shouldAnnounce = false;
        switch (role) {
            case "INNOCENT":
                shouldAnnounce = (boolean) getSettingByName("AnnounceInnocent").getValue();
                break;
            case "DETECTIVE":
                shouldAnnounce = (boolean) getSettingByName("AnnounceDetective").getValue();
                break;
            case "MURDERER":
                shouldAnnounce = (boolean) getSettingByName("AnnounceMurderer").getValue();
                break;
        }

        if (shouldAnnounce) {
            sendRoleMessage(role);
        }
    }

    private String detectRole(String text) {
        String cleanText = text.replaceAll("§[0-9a-fk-or]", "").trim();

        if (innocentPattern.matcher(cleanText).matches()) return "INNOCENT";
        if (detectivePattern.matcher(cleanText).matches()) return "DETECTIVE";
        if (murdererPattern.matcher(cleanText).matches()) return "MURDERER";
        return "";
    }

    private void sendRoleMessage(String role) {
        if (mc.player == null || role.isEmpty()) return;

        String message = getRoleMessage(role);
        mc.player.sendChatMessage("/party chat " + message);
    }

    private String getRoleMessage(String role) {
        switch (role) {
            case "INNOCENT":
                return "я мирный";
            case "DETECTIVE":
                return "я детектив";
            case "MURDERER":
                return "я убийца";
            default:
                return "Моя роль: " + role;
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isToggled()) return;

        if (mc.player != null && mc.player.ticksExisted % 100 == 0) {
            injectHandler();
        }
    }
}