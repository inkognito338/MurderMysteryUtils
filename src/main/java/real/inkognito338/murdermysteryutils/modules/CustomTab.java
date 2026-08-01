package real.inkognito338.murdermysteryutils.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import real.inkognito338.murdermysteryutils.online.OnlineMode;
import real.inkognito338.murdermysteryutils.online.TabAnimationData;
import real.inkognito338.murdermysteryutils.utils.API;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.NPCValidator;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;

@SideOnly(Side.CLIENT)
public class CustomTab extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();
    private static final Map<String, PlayerData> PLAYER_DATA = new ConcurrentHashMap<>();

    private static final int HEADER_HEIGHT = 18;
    private static final int MARGIN = 10;
    private static final String TAG_SEPARATOR = " ";
    private static final int DEFAULT_COLUMN_GAP = 5;

    private final Setting playersPerColumn = new Setting("Players Per Column", SettingType.NUMBER, 20.0, 1, 100);
    private final Setting sortMode = new Setting("Sort Mode", SettingType.MODE, "Server",
            "Server", "Alphabetical", "Reverse Alphabetical", "CRC32", "Name Length", "Ping");
    private final Setting showPrefix = new Setting("Show Prefix", SettingType.BOOLEAN, true);
    private final Setting showSuffix = new Setting("Show Suffix", SettingType.BOOLEAN, true);
    private final Setting backgroundAlpha = new Setting("Background Alpha", SettingType.NUMBER, 91.25418060200668, 0, 255);
    private final Setting pingReserveWidth = new Setting("Ping Reserve Width", SettingType.NUMBER, 33.61204013377927, 0, 50);
    private final Setting highlightSelf = new Setting("Highlight Self", SettingType.BOOLEAN, true);
    private final Setting filterNPC = new Setting("Filter NPC", SettingType.BOOLEAN, true);
    private final Setting balanceColumns = new Setting("Balance Columns", SettingType.BOOLEAN, true);
    private final Setting playerEntryHeight = new Setting("Player Row Height", SettingType.NUMBER, 15.288135593220339, 10, 20);
    private final Setting tabAnimation = new Setting("Tab Animation", SettingType.TAB_ANIMATION, "Off");

    public CustomTab() {
        super("CustomTab");
        addSetting(playersPerColumn);
        addSetting(sortMode);
        addSetting(showPrefix);
        addSetting(showSuffix);
        addSetting(backgroundAlpha);
        addSetting(pingReserveWidth);
        addSetting(highlightSelf);
        addSetting(filterNPC);
        addSetting(balanceColumns);
        addSetting(playerEntryHeight);
        addSetting(tabAnimation);
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
        OnlineMode.getInstance().addListener(onlineModeListener);

        if (OnlineMode.getInstance().isConnected() && !OnlineMode.getInstance().isGuest()) {
            String anim = (String) tabAnimation.getValue();
            if (!"Off".equals(anim)) {
                OnlineMode.getInstance().setTabAnimation(anim);
                OnlineMode.getInstance().requestAllAnimations();
            }
        }
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        OnlineMode.getInstance().removeListener(onlineModeListener);
        PLAYER_DATA.clear();
        TabAnimationData.clear();
    }

    private final OnlineMode.OnlineModeListener onlineModeListener = new OnlineMode.OnlineModeListener() {
        @Override
        public void onEvent(OnlineMode.OnlineModeListener.Event event) {
            if (event == OnlineMode.OnlineModeListener.Event.CONNECTED
                    || event == OnlineMode.OnlineModeListener.Event.REGISTERED_AND_CONNECTED) {
                String anim = (String) tabAnimation.getValue();
                if (!"Off".equals(anim) && !OnlineMode.getInstance().isGuest()) {
                    OnlineMode.getInstance().setTabAnimation(anim);
                    OnlineMode.getInstance().requestAllAnimations();
                }
            }
        }
    };

    @SubscribeEvent
    public void onWorldJoin(EntityJoinWorldEvent event) {
        if (event.getEntity() == mc.player && event.getWorld().isRemote) {
            if (OnlineMode.getInstance().isConnected() && !OnlineMode.getInstance().isGuest()) {
                OnlineMode.getInstance().requestAllAnimations();
            }
        }
    }

    @SubscribeEvent
    public void onRenderPre(RenderGameOverlayEvent.Pre event) {
        if (!isToggled()) return;

        if (event.getType() == RenderGameOverlayEvent.ElementType.ALL) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindPlayerList.getKeyCode(), false);
        } else if (event.getType() == RenderGameOverlayEvent.ElementType.PLAYER_LIST) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!isToggled() || mc.currentScreen != null || !Keyboard.isKeyDown(Keyboard.KEY_TAB) || !API.isLoaded()) return;

        updatePlayerData();
        renderMinimalTab(new ScaledResolution(mc).getScaledWidth());
    }

    private void updatePlayerData() {
        PLAYER_DATA.clear();
        if (mc.player == null || mc.player.connection == null) return;

        boolean npcFilterEnabled = (boolean) filterNPC.getValue();
        int index = 0;
        boolean hasConnection = OnlineMode.getInstance().isConnected() && !OnlineMode.getInstance().isGuest();

        for (NetworkPlayerInfo info : mc.player.connection.getPlayerInfoMap()) {
            String rawName = info.getGameProfile().getName();
            if (rawName == null) continue;
            if (npcFilterEnabled && NPCValidator.isNPC(rawName)) continue;

            // Маскируем отображаемое имя, если это наш игрок и NameProtect активен.
            // Реальное имя (rawName) сохраняем отдельно — оно нужно для lookup'ов
            // в NetworkPlayerInfo, scoreboard-команде и анимации.
            String displayName = maskNameIfNeeded(rawName);

            ScorePlayerTeam team = mc.world.getScoreboard().getPlayersTeam(rawName);

            PlayerData data = new PlayerData();
            data.name = displayName;
            data.realNetworkName = rawName;
            data.order = index++;
            data.ping = info.getResponseTime();
            data.prefix = (team != null) ? team.getPrefix() : "";
            data.suffix = (team != null) ? team.getSuffix() : "";
            data.teamName = team != null ? team.getName() : "";
            data.hasForge = checkForge(info);
            data.lang = getLanguage(info);

            CRC32 crc = new CRC32();
            crc.update(displayName.getBytes());
            data.crc32 = crc.getValue();

            if (hasConnection) {
                TabAnimationData.AnimationEntry anim = TabAnimationData.get(rawName);
                if (anim != null) {
                    data.animStyle = anim.style;
                    data.animSpeed = anim.speed;
                    data.animColors = anim.colors;
                }
            } else {
                data.animStyle = null;
                data.animColors = null;
            }

            PLAYER_DATA.put(rawName.toLowerCase(), data);
        }
    }

    /**
     * Если модуль NameProtect включён и переданное имя совпадает с реальным
     * ником локального игрока, возвращает замаскированное (фейковое) имя.
     * Во всех остальных случаях возвращает исходное имя без изменений.
     */
    private String maskNameIfNeeded(String rawName) {
        if (rawName == null) return null;

        NameProtect nameProtect = NameProtect.getInstance();
        if (nameProtect == null || !nameProtect.isToggled()) return rawName;

        String realName = NameProtect.getRealName();
        if (realName == null || realName.isEmpty()) return rawName;

        if (rawName.equalsIgnoreCase(realName)) {
            String fake = NameProtect.getFakeName();
            return (fake != null && !fake.isEmpty()) ? fake : rawName;
        }

        return rawName;
    }

    private void renderMinimalTab(int screenWidth) {
        FontRenderer fr = mc.fontRenderer;
        List<PlayerData> sortedPlayers = new ArrayList<>(PLAYER_DATA.values());

        String mode = sortMode.getMode();
        switch (mode) {
            case "Alphabetical":
                sortedPlayers.sort(Comparator.comparing(p -> p.name));
                break;
            case "Reverse Alphabetical":
                sortedPlayers.sort((p1, p2) -> p2.name.compareTo(p1.name));
                break;
            case "CRC32":
                sortedPlayers.sort(Comparator.comparingLong(p -> p.crc32));
                break;
            case "Name Length":
                sortedPlayers.sort(Comparator.comparingInt(p -> p.name.length()));
                break;
            case "Ping":
                sortedPlayers.sort(Comparator.comparingInt(p -> p.ping));
                break;
            default:
                sortedPlayers.sort(Comparator.<PlayerData, String>comparing(p -> p.teamName == null ? "" : p.teamName)
                        .thenComparing(p -> p.name));
                break;
        }

        if (sortedPlayers.isEmpty()) return;

        int totalPlayers = sortedPlayers.size();
        int maxPlayersPerCol = ((Number) playersPerColumn.getValue()).intValue();
        boolean pfx = (boolean) showPrefix.getValue();
        boolean sfx = (boolean) showSuffix.getValue();
        int pingReserve = ((Number) pingReserveWidth.getValue()).intValue();
        int bgAlpha = clampAlpha(((Number) backgroundAlpha.getValue()).intValue());
        int rowHeight = ((Number) playerEntryHeight.getValue()).intValue();
        int headSize = Math.max(6, rowHeight - 6);

        int maxContentWidth = 0;
        for (PlayerData data : sortedPlayers) {
            String infoTags = buildInfoTags(data);
            String displayName = buildDisplayName(data, pfx, sfx);
            String fullLine = displayName + (infoTags.isEmpty() ? "" : TAG_SEPARATOR + infoTags);
            maxContentWidth = Math.max(maxContentWidth, fr.getStringWidth(fullLine));
        }

        int headSectionWidth = headSize + 4;
        int colWidth = headSectionWidth + maxContentWidth + pingReserve;
        int desiredColumns = Math.max(1, (int) Math.ceil((double) totalPlayers / maxPlayersPerCol));
        int maxAvailableColumns = (screenWidth - (MARGIN * 2) + DEFAULT_COLUMN_GAP) / (colWidth + DEFAULT_COLUMN_GAP);
        int columns = Math.min(desiredColumns, Math.max(1, maxAvailableColumns));
        boolean balance = (boolean) balanceColumns.getValue();
        int rowsPerColumn = balance ? (int) Math.ceil((double) totalPlayers / columns) : Math.min(maxPlayersPerCol, totalPlayers);
        rowsPerColumn = Math.max(1, rowsPerColumn);
        columns = Math.max(1, (int) Math.ceil((double) totalPlayers / rowsPerColumn));

        int tabWidth = (columns * colWidth) + ((columns - 1) * DEFAULT_COLUMN_GAP) + (MARGIN * 2);
        int tabHeight = HEADER_HEIGHT + (rowsPerColumn * rowHeight);
        int startX = (screenWidth - tabWidth) / 2;
        int startY = MARGIN;

        Gui.drawRect(startX, startY, startX + tabWidth, startY + tabHeight, (bgAlpha << 24));
        Gui.drawRect(startX, startY, startX + tabWidth, startY + HEADER_HEIGHT, (bgAlpha << 24));
        fr.drawStringWithShadow("Players: " + totalPlayers, startX + MARGIN, startY + 5, 0xFFFFFF);

        String myName = mc.player.getName();
        long nowMs = System.currentTimeMillis();
        boolean hasConnection = OnlineMode.getInstance().isConnected() && !OnlineMode.getInstance().isGuest();

        for (int i = 0; i < sortedPlayers.size(); i++) {
            int col = i / rowsPerColumn;
            int row = i % rowsPerColumn;
            int renderX = startX + MARGIN + (col * (colWidth + DEFAULT_COLUMN_GAP));
            int renderY = startY + HEADER_HEIGHT + (row * rowHeight);

            PlayerData data = sortedPlayers.get(i);
            boolean isMe = data.realNetworkName.equals(myName);
            String displayName = buildDisplayName(data, pfx, sfx);
            String infoTags = buildInfoTags(data);
            String fullLine = displayName + (infoTags.isEmpty() ? "" : TAG_SEPARATOR + infoTags);

            Gui.drawRect(renderX, renderY, renderX + colWidth, renderY + rowHeight,
                    (isMe && (boolean) highlightSelf.getValue()) ? 0x22FFFFFF : 0x1A8899AA);

            NetworkPlayerInfo info = mc.player.connection.getPlayerInfo(data.realNetworkName);
            if (info != null) {
                mc.getTextureManager().bindTexture(info.getLocationSkin());
                GlStateManager.enableBlend();
                float headY = renderY + (rowHeight - headSize) / 2.0f;
                drawHeadRect(renderX, headY, 8, headSize);
                drawHeadRect(renderX, headY, 40, headSize);
            }

            float textX = renderX + headSectionWidth;
            float textY = renderY + (rowHeight - 8) / 2.0f;

            if (hasConnection && data.animStyle != null && !"Off".equals(data.animStyle) && data.animColors != null && data.animColors.length > 0) {
                drawAnimatedName(fr, fullLine, textX, textY, data.animColors, data.animSpeed, nowMs);
            } else {
                fr.drawStringWithShadow(fullLine, textX, textY, 0xFFFFFF);
            }

            String pingStr = data.ping + "ms";
            int pingColor = (data.ping < 100) ? 0x00FF00 : (data.ping < 200) ? 0xFFFF00 : 0xFF0000;
            fr.drawStringWithShadow(pingStr, renderX + colWidth - fr.getStringWidth(pingStr) - 2, textY, pingColor);
        }
    }

    private void drawAnimatedName(FontRenderer fr, String text, float x, float y, int[] colors, int speed, long nowMs) {
        if (colors == null || colors.length == 0) {
            fr.drawStringWithShadow(text, x, y, 0xFFFFFF);
            return;
        }

        double t = (nowMs % 100000L) / 1000.0 * (speed / 100.0);
        float totalWidth = fr.getStringWidth(text);
        if (totalWidth <= 0f) return;

        float offset = (float) ((t % 1.0 + 1.0) % 1.0);

        List<String> visibleChars = new ArrayList<>();
        List<String> formatPrefix = new ArrayList<>();
        StringBuilder currentFormat = new StringBuilder();

        int len = text.length();
        for (int i = 0; i < len; i++) {
            char ch = text.charAt(i);
            if (ch == '\u00A7' && i + 1 < len) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (code == 'r') currentFormat.setLength(0);
                else if ("lonmk".indexOf(code) >= 0) currentFormat.append('\u00A7').append(code);
                i++;
                continue;
            }
            visibleChars.add(String.valueOf(ch));
            formatPrefix.add(currentFormat.toString());
        }

        float cursorX = x;
        for (int i = 0; i < visibleChars.size(); i++) {
            String s = formatPrefix.get(i) + visibleChars.get(i);
            float charWidth = fr.getStringWidth(s);
            float progress = ((cursorX - x) + charWidth / 2.0f) / totalWidth;
            progress = Math.max(0f, Math.min(1f, progress));
            int color = lerpMultiStop(colors, (float) (((progress - offset) % 1.0 + 1.0) % 1.0));
            fr.drawStringWithShadow(s, cursorX, y, color);
            cursorX += charWidth;
        }
    }

    private int lerpMultiStop(int[] stops, float t) {
        if (stops.length == 0) return 0xFFFFFF;
        if (stops.length == 1) return stops[0];
        t = Math.max(0f, Math.min(1f, t));
        float scaled = t * (stops.length - 1);
        int idx = (int) Math.floor(scaled);
        if (idx >= stops.length - 1) return stops[stops.length - 1];
        float localT = scaled - idx;
        int a = stops[idx], b = stops[idx + 1];
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((Math.round(ar + (br - ar) * localT) & 0xFF) << 16) |
                ((Math.round(ag + (bg - ag) * localT) & 0xFF) << 8) |
                (Math.round(ab + (bb - ab) * localT) & 0xFF);
    }

    private String buildDisplayName(PlayerData data, boolean pfx, boolean sfx) {
        StringBuilder sb = new StringBuilder();
        if (pfx && data.prefix != null && !data.prefix.isEmpty()) sb.append(data.prefix);
        sb.append(data.name);
        if (sfx && data.suffix != null && !data.suffix.isEmpty()) sb.append(' ').append(data.suffix);
        return sb.toString();
    }

    private String buildInfoTags(PlayerData data) {
        StringBuilder sb = new StringBuilder();
        if (data.hasForge) sb.append("§6[Forge]");
        if (data.lang != null) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("§7[").append(data.lang).append(']');
        }
        return sb.toString();
    }

    private int clampAlpha(int value) {
        return Math.max(0, Math.min(value, 255));
    }

    private void drawHeadRect(float x, float y, int textureX, int size) {
        float u1 = textureX / 64.0f, u2 = (textureX + 8) / 64.0f;
        float v1 = 8 / 64.0f, v2 = 16 / 64.0f;
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + size, 0).tex(u1, v2).endVertex();
        buffer.pos(x + size, y + size, 0).tex(u2, v2).endVertex();
        buffer.pos(x + size, y, 0).tex(u2, v1).endVertex();
        buffer.pos(x, y, 0).tex(u1, v1).endVertex();
        tessellator.draw();
    }

    private boolean checkForge(NetworkPlayerInfo info) {
        try {
            com.mojang.authlib.properties.Property forge = info.getGameProfile().getProperties().get("forgeClient")
                    .stream().findFirst().orElse(null);
            if (forge != null && "true".equalsIgnoreCase(forge.getValue())) return true;
            com.mojang.authlib.properties.Property extra = info.getGameProfile().getProperties().get("extraData")
                    .stream().findFirst().orElse(null);
            return extra != null && extra.getValue().contains("FML");
        } catch (Exception ignored) {}
        return false;
    }

    private String getLanguage(NetworkPlayerInfo info) {
        try {
            com.mojang.authlib.properties.Property langId = info.getGameProfile().getProperties().get("lang_id")
                    .stream().findFirst().orElse(null);
            if (langId != null) {
                String val = langId.getValue();
                return (val != null && val.contains("_")) ? val.split("_")[0] : val;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static class PlayerData {
        String name, realNetworkName, prefix, suffix, lang, teamName, animStyle;
        boolean hasForge;
        long crc32;
        int order, ping, animSpeed;
        int[] animColors;
    }
}