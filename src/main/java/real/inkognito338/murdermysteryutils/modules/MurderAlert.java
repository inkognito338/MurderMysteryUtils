package real.inkognito338.murdermysteryutils.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.network.play.client.CPacketChatMessage;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;
import real.inkognito338.murdermysteryutils.utils.PlayerListManager;
import real.inkognito338.murdermysteryutils.utils.MurderAPI;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

@SideOnly(Side.CLIENT)
public class MurderAlert extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final MurderAPI murderAPI = MurderAPI.getInstance();

    private boolean registered = false;
    private final Map<String, Boolean> localAnnounced = new HashMap<>();
    private final Map<String, Long> playerMessageTime = new HashMap<>();
    private final Map<String, Boolean> playerMessageSent = new HashMap<>();

    private final Setting announceAsPlayer;
    private final Setting ignoreFriends;
    private final Setting chatTemplate;
    private final Setting timeout;
    private final Setting checkSkin;
    private final Setting chatTemplateSteve;
    private final Setting chatTemplateAlex;

    public MurderAlert() {
        super("MurderAlert");

        this.announceAsPlayer = new Setting("AnnounceAsPlayer", SettingType.BOOLEAN, false);
        this.addSetting(announceAsPlayer);

        this.ignoreFriends = new Setting("IgnoreFriends", SettingType.BOOLEAN, true);
        this.addSetting(ignoreFriends);

        this.timeout = new Setting("Timeout", SettingType.NUMBER, 4.0, 0.0, 25.0);
        this.addSetting(timeout);

        this.chatTemplate = new Setting("ChatText", SettingType.TEXT, "Убийца: %player%");
        this.addSetting(chatTemplate);

        this.checkSkin = new Setting("CheckSkin", SettingType.BOOLEAN, false);
        this.addSetting(checkSkin);

        this.chatTemplateSteve = new Setting("ChatTextSteve", SettingType.TEXT, "Убийца: %player% (Стив)");
        this.addSetting(chatTemplateSteve);

        this.chatTemplateAlex = new Setting("ChatTextAlex", SettingType.TEXT, "Убийца: %player% (Алекс)");
        this.addSetting(chatTemplateAlex);
    }

    @Override
    public void onEnable() {
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(this);
            registered = true;
        }
        localAnnounced.clear();
        playerMessageTime.clear();
        playerMessageSent.clear();
    }

    @Override
    public void onDisable() {
        if (registered) {
            MinecraftForge.EVENT_BUS.unregister(this);
            registered = false;
        }
        localAnnounced.clear();
        playerMessageTime.clear();
        playerMessageSent.clear();
    }

    private String getPlayerSkinType(String name) {
        if (mc.player == null || mc.player.connection == null) return null;
        for (NetworkPlayerInfo info : mc.player.connection.getPlayerInfoMap()) {
            if (info.getGameProfile().getName().equals(name)) {
                info.getLocationSkin();
                String url = info.getLocationSkin().toString();
                if (url.endsWith("/steve.png")) return "steve";
                if (url.endsWith("/alex.png")) return "alex";
                return null;
            }
        }
        return null;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isToggled() || mc.player == null || mc.world == null) return;

        Set<String> murderers = murderAPI.getMurderers();
        long currentTime = System.currentTimeMillis();
        boolean asPlayer = (Boolean) announceAsPlayer.getValue();
        boolean skipFriends = (Boolean) ignoreFriends.getValue();
        boolean skinCheck = (Boolean) checkSkin.getValue();
        double timeoutSeconds = ((Number) timeout.getValue()).doubleValue();
        long timeoutMillis = (long) (timeoutSeconds * 1000);

        for (String murderer : murderers) {
            boolean isFriend = PlayerListManager.isFriend(murderer);

            // Локальное сообщение
            if (!localAnnounced.containsKey(murderer)) {
                localAnnounced.put(murderer, true);

                String skinSuffix = "";
                if (skinCheck) {
                    String skin = getPlayerSkinType(murderer);
                    if ("steve".equals(skin)) skinSuffix = " §7(§bСтив§7)";
                    else if ("alex".equals(skin)) skinSuffix = " §7(§dАлекс§7)";
                }
                String friendSuffix = isFriend ? " §7(§afriend§7)" : "";

                mc.player.sendMessage(new TextComponentString(
                        "§7[§cMurderAlert§7] §fУбийца: §c" + murderer + skinSuffix + friendSuffix
                ));
            }

            // Сообщение от имени игрока
            if (asPlayer && !playerMessageSent.containsKey(murderer)) {
                if (skipFriends && isFriend) continue;

                Long sendTime = playerMessageTime.get(murderer);

                if (sendTime == null) {
                    playerMessageTime.put(murderer, currentTime);
                } else if (currentTime - sendTime >= timeoutMillis) {
                    playerMessageSent.put(murderer, true);

                    String template;
                    if (skinCheck) {
                        String skin = getPlayerSkinType(murderer);
                        if ("steve".equals(skin)) {
                            template = (String) chatTemplateSteve.getValue();
                        } else if ("alex".equals(skin)) {
                            template = (String) chatTemplateAlex.getValue();
                        } else {
                            template = (String) chatTemplate.getValue();
                        }
                    } else {
                        template = (String) chatTemplate.getValue();
                    }

                    String message = template.replace("%player%", murderer);
                    if (message.length() > 256) message = message.substring(0, 256);

                    mc.player.connection.sendPacket(new CPacketChatMessage(message));
                }
            }
        }

        localAnnounced.keySet().removeIf(murderer -> !murderers.contains(murderer));
        playerMessageTime.keySet().removeIf(murderer -> !murderers.contains(murderer));
        playerMessageSent.keySet().removeIf(murderer -> !murderers.contains(murderer));
    }
}