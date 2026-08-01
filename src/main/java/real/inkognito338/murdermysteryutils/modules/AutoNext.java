package real.inkognito338.murdermysteryutils.modules;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.SPacketChat;
import net.minecraft.network.play.server.SPacketTitle;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import real.inkognito338.murdermysteryutils.utils.API;
import real.inkognito338.murdermysteryutils.utils.MessageWrapper;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

@SuppressWarnings("SpellCheckingInspection")
@SideOnly(Side.CLIENT)
public class AutoNext extends Module {

    private static AutoNext INSTANCE;
    private final Minecraft mc = Minecraft.getMinecraft();
    private static final String HANDLER_NAME = "auto_next_handler";

    // ====== СТРОГИЙ ВАЙТЛИСТ КОМАНД ======
    private static final Set<String> ALLOWED_COMMANDS = new HashSet<>(Arrays.asList(
            "/next",
            "/random-arena"
    ));

    private long roleDetectionTime = 0;
    private long deathDetectionTime = 0;
    private boolean secondCommandSent = false;
    private boolean waitingForSecondCommand = false;
    private boolean waitingForDeathSecondCommand = false;
    private boolean secondDeathCommandSent = false;

    // Состояние из Lua
    private String nextCommand = "/next";
    private boolean autoConfirmEnabled = true;

    public AutoNext() {
        super("AutoNext");
        INSTANCE = this;

        addSetting(new Setting("OnInnocent", SettingType.BOOLEAN, true));
        addSetting(new Setting("OnDetective", SettingType.BOOLEAN, true));
        addSetting(new Setting("OnMurderer", SettingType.BOOLEAN, true));
        addSetting(new Setting("OnDeath", SettingType.BOOLEAN, true));
        addSetting(new Setting("AutoConfirm", SettingType.BOOLEAN, true));
        addSetting(new Setting("Delay", SettingType.NUMBER, 0.4, 0.1, 10.0));
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

    private void resetState() {
        roleDetectionTime = 0;
        deathDetectionTime = 0;
        secondCommandSent = false;
        waitingForSecondCommand = false;
        waitingForDeathSecondCommand = false;
        secondDeathCommandSent = false;
        nextCommand = "/next";
        autoConfirmEnabled = true;
    }

    private boolean shouldExecuteForState(String state) {
        if (state == null) return false;
        switch (state) {
            case "INNOCENT": return (boolean) getSettingByName("OnInnocent").getValue();
            case "DETECTIVE": return (boolean) getSettingByName("OnDetective").getValue();
            case "MURDERER": return (boolean) getSettingByName("OnMurderer").getValue();
            case "DEATH": return (boolean) getSettingByName("OnDeath").getValue();
            case "GAME_END": return (boolean) getSettingByName("AutoNextGameEnd").getValue();
            default: return false;
        }
    }

    private String getCurrentServerIP() {
        if (mc.getCurrentServerData() != null) {
            String ip = mc.getCurrentServerData().serverIP.toLowerCase();
            return ip.contains(":") ? ip.substring(0, ip.lastIndexOf(":")) : ip;
        }
        return "";
    }

    private boolean isCommandAllowed(String command) {
        return ALLOWED_COMMANDS.contains(command);
    }

    private void processMessage(ITextComponent component, String source) {
        if (!isToggled()) return;
        if (mc.player == null) return;
        if (component == null) return;

        // Получаем данные игрока
        String playerName = mc.player.getName();
        String teamName = "";
        String prefix = "";
        String suffix = "";

        // Получаем команду игрока
        if (mc.world != null) {
            ScorePlayerTeam team = mc.world.getScoreboard().getPlayersTeam(playerName);
            if (team != null) {
                teamName = team.getName();
                prefix = team.getPrefix();
                suffix = team.getSuffix();
            }
        }

        // Получаем IP сервера
        String serverIP = getCurrentServerIP();

        // Создаём обёртку с полной информацией
        MessageWrapper wrapper = new MessageWrapper(component);

        // Передаём в API с данными игрока и IP
        // API возвращает Object[] {state, command, autoConfirm} или null
        Object[] result = API.detectAutoNextStateFull(wrapper, source, playerName, teamName, prefix, suffix, serverIP);

        if (result == null) return;

        String state = (String) result[0];
        String command = (String) result[1];
        boolean autoConfirm = (boolean) result[2];

        if (state == null) return;

        // GAME_END обрабатываем отдельно
        if (state.equals("GAME_END")) {
            if (shouldExecuteForState(state)) {
                String cmd = (command != null && isCommandAllowed(command)) ? command : "/next";
                sendCommand(cmd);
            }
            return;
        }

        // Сбрасываем состояние при новой роли/смерти
        resetState();

        if (!shouldExecuteForState(state)) return;

        // Сохраняем команду и autoConfirm из API
        nextCommand = (command != null && isCommandAllowed(command)) ? command : "/next";
        autoConfirmEnabled = autoConfirm;

        // Отправляем первую команду
        sendCommand(nextCommand);

        if (state.equals("DEATH")) {
            deathDetectionTime = System.currentTimeMillis();
            if (autoConfirmEnabled) {
                waitingForDeathSecondCommand = true;
            }
        } else {
            roleDetectionTime = System.currentTimeMillis();
            if (autoConfirmEnabled) {
                waitingForSecondCommand = true;
            }
        }
    }

    private void sendCommand(String command) {
        if (mc.player != null && isCommandAllowed(command)) {
            mc.player.sendChatMessage(command);
        }
    }

    private void sendNextCommand() {
        sendCommand(nextCommand);
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
            if (mc.getConnection() != null) {
                if (mc.getConnection().getNetworkManager().channel().pipeline().get(HANDLER_NAME) != null) {
                    mc.getConnection().getNetworkManager().channel().pipeline().remove(HANDLER_NAME);
                }
            }
        } catch (Exception ignored) {}
    }

    private void handlePacket(Object packet) {
        if (!isToggled()) return;

        // Обработка заголовков (титров)
        if (packet instanceof SPacketTitle) {
            SPacketTitle titlePacket = (SPacketTitle) packet;
            SPacketTitle.Type type = titlePacket.getType();

            String source = type == SPacketTitle.Type.TITLE ? "title" :
                    type == SPacketTitle.Type.SUBTITLE ? "subtitle" : null;

            if (source == null) return;

            ITextComponent message = titlePacket.getMessage();

            processMessage(message, source);
        }

        // Обработка сообщений в чате
        else if (packet instanceof SPacketChat) {
            SPacketChat chatPacket = (SPacketChat) packet;
            ITextComponent message = chatPacket.getChatComponent();

            processMessage(message, "chat");
        }
    }

    // ====== FORGE EVENTS ======

    @SubscribeEvent
    public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        injectHandler();
    }

    @SubscribeEvent
    public void onClientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        removeHandler();
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        processMessage(event.getMessage(), "chat");
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isToggled()) return;
        if (mc.player == null) return;

        // Периодическая проверка и инжект обработчика
        if (mc.player.ticksExisted % 100 == 0) {
            injectHandler();
        }

        double delay = ((Number) getSettingByName("Delay").getValue()).doubleValue();

        if (waitingForSecondCommand && !secondCommandSent) {
            long currentTime = System.currentTimeMillis();
            if ((currentTime - roleDetectionTime) >= (long) (delay * 1000)) {
                sendNextCommand();
                secondCommandSent = true;
                waitingForSecondCommand = false;
            }
        }

        if (waitingForDeathSecondCommand && !secondDeathCommandSent) {
            long currentTime = System.currentTimeMillis();
            if ((currentTime - deathDetectionTime) >= (long) (delay * 1000)) {
                sendNextCommand();
                secondDeathCommandSent = true;
                waitingForDeathSecondCommand = false;
            }
        }
    }
}