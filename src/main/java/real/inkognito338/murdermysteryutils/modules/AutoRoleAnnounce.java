package real.inkognito338.murdermysteryutils.modules;

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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;

import java.util.regex.Pattern;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

@SideOnly(Side.CLIENT)
public class AutoRoleAnnounce extends Module {

    private static AutoRoleAnnounce INSTANCE;
    private final Minecraft mc = Minecraft.getMinecraft();
    private static final String HANDLER_NAME = "auto_role_announce_handler";

    private static final Logger LOGGER = LogManager.getLogger("MurderMysteryUtils");

    private final Pattern innocentPattern = Pattern.compile(
            ".*РОЛЬ:\\s*МИРНЫЙ\\s*ЖИТЕЛЬ.*|.*ROLE:\\s*INNOCENT.*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private final Pattern detectivePattern = Pattern.compile(
            ".*РОЛЬ:\\s*ДЕТЕКТИВ.*|.*ROLE:\\s*DETECTIVE.*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private final Pattern murdererPattern = Pattern.compile(
            ".*РОЛЬ:\\s*УБИЙЦА.*|.*ROLE:\\s*MURDERER.*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // Настройки включения для каждой роли
    private final Setting announceInnocent;
    private final Setting announceDetective;
    private final Setting announceMurderer;

    // Полный текст команды для каждой роли (команда + сообщение)
    private final Setting innocentMessage;
    private final Setting detectiveMessage;
    private final Setting murdererMessage;

    public AutoRoleAnnounce() {
        super("AutoRoleAnnounce");
        INSTANCE = this;

        // ===== ВКЛЮЧЕНИЕ ОТПРАВКИ =====
        announceInnocent = new Setting("AnnounceInnocent", SettingType.BOOLEAN, true);
        addSetting(announceInnocent);

        announceDetective = new Setting("AnnounceDetective", SettingType.BOOLEAN, true);
        addSetting(announceDetective);

        announceMurderer = new Setting("AnnounceMurderer", SettingType.BOOLEAN, true);
        addSetting(announceMurderer);

        // ===== ПОЛНЫЙ ТЕКСТ КОМАНДЫ =====
        innocentMessage = new Setting("InnocentMessage", SettingType.TEXT, "/party chat я мирный");
        addSetting(innocentMessage);

        detectiveMessage = new Setting("DetectiveMessage", SettingType.TEXT, "/party chat я детектив");
        addSetting(detectiveMessage);

        murdererMessage = new Setting("MurdererMessage", SettingType.TEXT, "/party chat я убийца");
        addSetting(murdererMessage);
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
            if (mc.getConnection() == null) {
                return;
            }

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
            LOGGER.error("[AutoRoleAnnounce] Failed to inject channel handler", e);
        }
    }

    private void removeHandler() {
        try {
            if (mc.getConnection() != null) {
                if (mc.getConnection().getNetworkManager()
                        .channel().pipeline().get(HANDLER_NAME) != null) {
                    mc.getConnection().getNetworkManager()
                            .channel().pipeline().remove(HANDLER_NAME);
                }
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

        String text = message.getUnformattedText();
        if (text.isEmpty()) return;

        processRoleText(text);
    }

    /** Основной метод для обработки текста из title */
    private void processRoleText(String text) {
        if (!isToggled() || text == null || text.isEmpty()) return;

        String role = detectRole(text);
        if (role.isEmpty()) return;

        boolean shouldAnnounce = false;
        String fullMessage = "";

        switch (role) {
            case "INNOCENT":
                shouldAnnounce = (boolean) announceInnocent.getValue();
                fullMessage = (String) innocentMessage.getValue();
                break;
            case "DETECTIVE":
                shouldAnnounce = (boolean) announceDetective.getValue();
                fullMessage = (String) detectiveMessage.getValue();
                break;
            case "MURDERER":
                shouldAnnounce = (boolean) announceMurderer.getValue();
                fullMessage = (String) murdererMessage.getValue();
                break;
        }

        if (shouldAnnounce && fullMessage != null && !fullMessage.isEmpty()) {
            sendMessage(fullMessage);
        }
    }

    private String detectRole(String text) {
        String cleanText = text.replaceAll("§[0-9a-fk-or]", "").trim();

        if (innocentPattern.matcher(cleanText).matches()) return "INNOCENT";
        if (detectivePattern.matcher(cleanText).matches()) return "DETECTIVE";
        if (murdererPattern.matcher(cleanText).matches()) return "MURDERER";
        return "";
    }

    private void sendMessage(String fullMessage) {
        if (mc.player == null || fullMessage == null || fullMessage.isEmpty()) return;
        mc.player.sendChatMessage(fullMessage);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isToggled()) return;

        if (mc.player != null && mc.player.ticksExisted % 100 == 0) {
            injectHandler();
        }
    }
}