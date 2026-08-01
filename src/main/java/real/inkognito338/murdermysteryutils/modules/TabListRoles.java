package real.inkognito338.murdermysteryutils.modules;

import com.mojang.authlib.properties.Property;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.luaj.vm2.LuaValue;
import real.inkognito338.murdermysteryutils.utils.API;
import real.inkognito338.murdermysteryutils.utils.Module;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

@Deprecated
@SideOnly(Side.CLIENT)
public class TabListRoles extends Module {

    private static final boolean DEBUG = false;

    private static TabListRoles INSTANCE;
    private final Minecraft mc = Minecraft.getMinecraft();

    private static final Map<String, String> NAME_COLORS = new ConcurrentHashMap<>();

    private int tickCounter = 0;
    private static int logCounter = 0;

    public TabListRoles() {
        super("TabListRoles");
        INSTANCE = this;
    }

    public static TabListRoles getInstance() { return INSTANCE; }

    public static String getModifiedTabName(String playerName, String originalFormatted) {
        Minecraft mc = Minecraft.getMinecraft();

        ScorePlayerTeam team = mc.world != null ? mc.world.getScoreboard().getPlayersTeam(playerName) : null;
        String teamName = team != null ? team.getName() : "";
        String prefix = team != null ? team.getPrefix() : "";
        String suffix = team != null ? team.getSuffix() : "";

        // Get player info for additional data
        NetworkPlayerInfo playerInfo = getPlayerInfo(playerName);

        // Check for Forge and language
        boolean hasForge = false;
        String lang = null;

        if (playerInfo != null) {
            // Check Forge via forgeClient property
            Property forgeProperty = playerInfo.getGameProfile().getProperties().get("forgeClient").stream()
                    .findFirst()
                    .orElse(null);
            if (forgeProperty != null && "true".equalsIgnoreCase(forgeProperty.getValue())) {
                hasForge = true;
            }

            // Also check extraData for FML (additional check)
            if (!hasForge) {
                Property extraData = playerInfo.getGameProfile().getProperties().get("extraData").stream()
                        .findFirst()
                        .orElse(null);
                if (extraData != null && extraData.getValue().contains("FML")) {
                    hasForge = true;
                }
            }

            // Get language
            Property cmlangProperty = playerInfo.getGameProfile().getProperties().get("cmlang").stream()
                    .findFirst()
                    .orElse(null);
            if (cmlangProperty != null) {
                lang = cmlangProperty.getValue();
            } else {
                Property langIdProperty = playerInfo.getGameProfile().getProperties().get("lang_id").stream()
                        .findFirst()
                        .orElse(null);
                if (langIdProperty != null) {
                    lang = langIdProperty.getValue();
                }
            }

            // Extract language code before underscore (en_GB -> en, ru_RU -> ru)
            if (lang != null && lang.contains("_")) {
                lang = lang.split("_")[0];
            }
        }

        // Build additional suffix info
        StringBuilder additionalInfo = new StringBuilder();

        // Add Forge indicator if present
        if (hasForge) {
            additionalInfo.append("§6[Forge]");
        }

        // Add language if present
        if (lang != null && !lang.isEmpty()) {
            if (additionalInfo.length() > 0) {
                additionalInfo.append(" ");
            }
            additionalInfo.append("§7[").append(lang).append("]");
        }

        // If there's additional info, append it to the suffix
        String modifiedSuffix = suffix;
        if (additionalInfo.length() > 0) {
            if (!suffix.isEmpty()) {
                modifiedSuffix = suffix + " " + additionalInfo.toString();
            } else {
                modifiedSuffix = additionalInfo.toString();
            }
        }

        if (DEBUG && logCounter % 50 == 0) {
            System.out.println("[TabListRoles] getModifiedTabName:");
            System.out.println("  playerName: " + playerName);
            System.out.println("  originalFormatted: '" + originalFormatted + "'");
            System.out.println("  serverIP: " + API.getServerIP());
            System.out.println("  teamName: '" + teamName + "'");
            System.out.println("  prefix: '" + prefix + "'");
            System.out.println("  original suffix: '" + suffix + "'");
            System.out.println("  modified suffix: '" + modifiedSuffix + "'");
            System.out.println("  hasForge: " + hasForge);
            System.out.println("  lang: " + lang);
        }

        LuaValue result = API.callFunction("getModifiedTabName",
                playerName,
                playerName.toLowerCase(),
                originalFormatted,
                API.getServerIP(),
                teamName,
                prefix,
                modifiedSuffix
        );

        if (DEBUG && logCounter % 50 == 0) {
            System.out.println("  Lua result: " + (result == null || result.isnil() ? "NULL" : "'" + result.tojstring() + "'"));
        }

        if (result != null && !result.isnil() && result.isstring()) {
            String modified = result.tojstring().replace("&", "§");
            if (DEBUG && logCounter % 50 == 0) {
                System.out.println("  Modified: '" + modified + "'");
                System.out.println("  CHANGED: " + !modified.equals(originalFormatted));
            }
            logCounter++;
            return modified;
        }

        if (DEBUG && logCounter % 50 == 0) {
            System.out.println("  Returning NULL (no changes)");
        }

        logCounter++;
        return null;
    }

    private static NetworkPlayerInfo getPlayerInfo(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.player.connection == null) return null;

        for (NetworkPlayerInfo info : mc.player.connection.getPlayerInfoMap()) {
            if (info.getGameProfile().getName().equalsIgnoreCase(name)) {
                return info;
            }
        }
        return null;
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
        if (DEBUG) System.out.println("[TabListRoles] ENABLED");
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        NAME_COLORS.clear();
        if (DEBUG) System.out.println("[TabListRoles] DISABLED");
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (mc.player == null || mc.player.connection == null || mc.world == null) return;
        if (!API.isLoaded()) return;

        tickCounter++;
        if (tickCounter % 5 != 0) return;

        NAME_COLORS.clear();

        for (NetworkPlayerInfo info : mc.player.connection.getPlayerInfoMap()) {
            String name = info.getGameProfile().getName();
            if (name == null) continue;

            ScorePlayerTeam team = mc.world.getScoreboard().getPlayersTeam(name);
            String teamName = team != null ? team.getName() : "";
            String prefix = team != null ? team.getPrefix() : "";
            String suffix = team != null ? team.getSuffix() : "";

            String color = API.getTabNameColor(name, teamName, prefix, suffix);
            if (color != null) {
                NAME_COLORS.put(name.toLowerCase(), color);
            }
        }
    }
}