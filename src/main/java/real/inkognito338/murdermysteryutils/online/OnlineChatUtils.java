package real.inkognito338.murdermysteryutils.online;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * OnlineChatUtils — мост между OnlineMode и игровым чатом Minecraft.
 * <p>
 * Сервер отдаёт ЧИСТЫЙ JSON с компонентом чата.
 * Клиент просто парсит JSON и выводит в чат.
 *
 * @author inkognito338
 * @version 6.0.0
 */
public class OnlineChatUtils {

    private static final Logger LOGGER = LogManager.getLogger("OnlineChatUtils");
    private static final String SYSTEM_PREFIX = "§7[§6Онлайн§7] ";
    private static OnlineChatUtils instance;
    private boolean enabled = true;
    private boolean showSystemMessages = true;
    private boolean showNotifications = true;

    private OnlineChatUtils() {
        MinecraftForge.EVENT_BUS.register(this);

        OnlineMode.getInstance().addListener(new OnlineMode.OnlineModeListener() {
            @Override
            public void onEvent(Event event, Object data) {
                switch (event) {
                    case SYSTEM_MESSAGE:
                        if (showSystemMessages && data instanceof String) {
                            addSystemMessage((String) data);
                        }
                        break;
                    case NOTIFICATION:
                        if (showNotifications && data instanceof String) {
                            addNotification((String) data);
                        }
                        break;
                    default:
                        break;
                }
            }
        });
    }

    public static OnlineChatUtils getInstance() {
        if (instance == null) {
            instance = new OnlineChatUtils();
        }
        return instance;
    }

    public boolean sendMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }

        message = message.trim();

        if (message.length() > 256) {
            message = message.substring(0, 256);
        }

        OnlineMode onlineMode = OnlineMode.getInstance();

        if (!onlineMode.isConnected()) {
            addSystemMessage("§cНет подключения к онлайн-чату");
            return false;
        }

        onlineMode.sendChatMessage(message);
        return true;
    }

    public void handleIncomingMessage(String rawJson) {
        try {
            ITextComponent component = ITextComponent.Serializer.jsonToComponent(rawJson);

            if (component != null) {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.player != null) {
                    mc.player.sendMessage(component);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse chat JSON: {}", e.getMessage());
        }
    }

    private void addSystemMessage(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString(SYSTEM_PREFIX + "§e" + text));
        }
    }

    private void addNotification(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString(SYSTEM_PREFIX + "§a" + text));
        }
    }
}