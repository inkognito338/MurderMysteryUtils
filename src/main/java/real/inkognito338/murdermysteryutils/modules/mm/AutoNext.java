package real.inkognito338.murdermysteryutils.modules.mm;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.SPacketChat;
import net.minecraft.network.play.server.SPacketTitle;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
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

@SuppressWarnings("SpellCheckingInspection")
@SideOnly(Side.CLIENT)
public class AutoNext extends Module {

    private static AutoNext INSTANCE;
    private final Minecraft mc = Minecraft.getMinecraft();
    private static final String HANDLER_NAME = "auto_next_handler";

    private long roleDetectionTime = 0;
    private boolean firstCommandSent = false;
    private boolean secondCommandSent = false;
    private boolean waitingForSecondCommand = false;

    private final Pattern innocentPattern = Pattern.compile(
            ".*РОЛЬ:\\s*МИРНЫЙ\\s*ЖИТЕЛЬ.*|.*ROLE:\\s*INNOCENT.*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private final Pattern detectivePattern = Pattern.compile(
            ".*РОЛЬ:\\s*ДЕТЕКТИВ.*|.*ROLE:\\s*DETECTIVE.*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private final Pattern murdererPattern = Pattern.compile(
            ".*РОЛЬ:\\s*УБИЙЦА.*|.*ROLE:\\s*MURDERER.*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final Pattern gameStartPattern = Pattern.compile(
            ".*До начала игры.*|.*Game starting.*|.*Игра начинается.*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private final Pattern gameEndPattern = Pattern.compile(
            ".*ВЫ ПОБЕДИЛИ.*|.*Игра завершена.*|.*Game Over.*|.*Победил.*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public AutoNext() {
        super("AutoNext");
        INSTANCE = this;

        addSetting(new Setting("OnInnocent", SettingType.BOOLEAN, true));
        addSetting(new Setting("OnDetective", SettingType.BOOLEAN, true));
        addSetting(new Setting("OnMurderer", SettingType.BOOLEAN, true));
        addSetting(new Setting("AutoConfirm", SettingType.BOOLEAN, true));
        addSetting(new Setting("Delay", SettingType.NUMBER, 1.0, 0.1, 10.0));

        addSetting(new Setting("AutoNextGameEnd", SettingType.BOOLEAN, true));
    }

    public static AutoNext getInstance() {
        return INSTANCE;
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
        resetState();
        injectHandler();
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        resetState();
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

    private void resetState() {
        roleDetectionTime = 0;
        firstCommandSent = false;
        secondCommandSent = false;
        waitingForSecondCommand = false;
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
                                    if (isToggled()) {
                                        handlePacket(msg);
                                    }
                                    super.channelRead(ctx, msg);
                                }
                            });
        } catch (Exception ignored) {}
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

    /** Обработка пакетов */
    private void handlePacket(Object packet) {
        if (!isToggled()) return;

        // Обработка заголовков (титров)
        if (packet instanceof SPacketTitle) {
            handleTitlePacket((SPacketTitle) packet);
        }

        // Обработка сообщений в чате
        else if (packet instanceof SPacketChat) {
            handleChatPacket((SPacketChat) packet);
        }
    }

    private void handleTitlePacket(SPacketTitle packet) {
        SPacketTitle.Type type = packet.getType();

        // Проверяем только TITLE (основной заголовок)
        if (type != SPacketTitle.Type.TITLE) return;

        net.minecraft.util.text.ITextComponent message = packet.getMessage();
        if (message == null) return;

        String text = message.getUnformattedText();
        if (text == null || text.isEmpty()) return;

        processText(text);
    }

    private void handleChatPacket(SPacketChat packet) {
        net.minecraft.util.text.ITextComponent message = packet.getChatComponent();
        if (message == null) return;

        String text = message.getUnformattedText();
        if (text == null || text.isEmpty()) return;

        processText(text);
    }

    /** Основной метод для обработки текста */
    private void processText(String text) {
        if (!isToggled() || text == null || text.isEmpty()) return;

        // Проверка на начало/конец игры
        if (gameStartPattern.matcher(text).matches()) {
            resetState();
            return;
        }

        if (gameEndPattern.matcher(text).matches()) {
            resetState();
            return;
        }

        // Определение роли
        String role = detectRole(text);
        if (role.isEmpty()) return;

        boolean shouldExecute = false;
        switch (role) {
            case "INNOCENT":
                shouldExecute = (boolean) getSettingByName("OnInnocent").getValue();
                break;
            case "DETECTIVE":
                shouldExecute = (boolean) getSettingByName("OnDetective").getValue();
                break;
            case "MURDERER":
                shouldExecute = (boolean) getSettingByName("OnMurderer").getValue();
                break;
        }

        if (!shouldExecute) {
            resetState();
            return;
        }

        // Всегда сбрасываем состояние при обнаружении новой роли
        resetState();

        // Отправляем первый /next
        sendNextCommand();
        firstCommandSent = true;
        roleDetectionTime = System.currentTimeMillis();

        // Если включен AutoConfirm — включаем ожидание повторной команды
        boolean autoConfirm = (boolean) getSettingByName("AutoConfirm").getValue();
        if (autoConfirm) {
            waitingForSecondCommand = true;
        }
    }

    private String detectRole(String text) {
        // Убираем форматирующие коды для более надежного определения
        String cleanText = text.replaceAll("§[0-9a-fk-or]", "").trim();

        if (innocentPattern.matcher(cleanText).matches()) return "INNOCENT";
        if (detectivePattern.matcher(cleanText).matches()) return "DETECTIVE";
        if (murdererPattern.matcher(cleanText).matches()) return "MURDERER";

        return "";
    }

    private void sendNextCommand() {
        if (mc.player != null) {
            mc.player.sendChatMessage("/next");
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isToggled()) return;

        // Периодическая проверка и инжект обработчика
        if (mc.player != null && mc.player.ticksExisted % 100 == 0) {
            injectHandler();
        }

        // AutoConfirm: повтор /next
        if (waitingForSecondCommand && !secondCommandSent) {
            double delaySeconds = ((Number) getSettingByName("Delay").getValue()).doubleValue();
            long currentTime = System.currentTimeMillis();

            if ((currentTime - roleDetectionTime) >= (long) (delaySeconds * 1000)) {
                // Повторяем команду
                sendNextCommand();
                secondCommandSent = true;
                waitingForSecondCommand = false;
            }
        }
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        String message = event.getMessage().getUnformattedText();

        boolean autoNextGameEnd = (boolean) getSettingByName("AutoNextGameEnd").getValue();

        if (autoNextGameEnd && message.contains("MurderMystery ▸ Перезагрузка сервера через 10 секунд!")) {
            mc.player.sendChatMessage("/next");
        }
    }

}