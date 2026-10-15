package real.inkognito338.murdermysteryutils.modules;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
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
import net.minecraft.network.play.server.SPacketJoinGame;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.network.play.server.SPacketRespawn;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.GameType;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import real.inkognito338.murdermysteryutils.online.OnlineMode;
import real.inkognito338.murdermysteryutils.online.TabAnimationData;
import real.inkognito338.murdermysteryutils.utils.API;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.ModuleManager;
import real.inkognito338.murdermysteryutils.utils.MurderAPI;
import real.inkognito338.murdermysteryutils.utils.NPCValidator;
import real.inkognito338.murdermysteryutils.utils.PlayerListManager;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;

@SideOnly(Side.CLIENT)
public class CustomTab extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();
    private static final Map<String, PlayerData> PLAYER_DATA = new ConcurrentHashMap<>();

    // ====== PACKET STATE ======
    private static final String HANDLER_NAME = "customtab_playerlist_handler";

    /** Список игроков, который ведётся по SPacketPlayerListItem. Пишет netty-поток, читает render-поток. */
    private final Map<UUID, TabEntry> entries = new ConcurrentHashMap<>();
    private final AtomicLong entrySeq = new AtomicLong();

    private static final int HEADER_HEIGHT = 18;
    private static final int MARGIN = 10;
    private static final String TAG_SEPARATOR = "  "; // ровно 2 пробела между ником и тегами
    private static final int DEFAULT_COLUMN_GAP = 5;
    private static final int TEAM_ID_LENGTH = 10; // длина ID команды перед символом 'Q' (CMTabList)

    private static final int LEFT_PAD = 4; // отступ слева в строке (место под акцентную полоску)

    private final Setting playersPerColumn = new Setting("Players Per Column", SettingType.NUMBER, 20.0, 1, 100);
    private final Setting sortMode = new Setting("Sort Mode", SettingType.MODE, "Server",
            "Server", "Alphabetical", "Reverse Alphabetical", "CRC32", "Name Length", "Ping", "CMTabList");
    private final Setting showPrefix = new Setting("Show Prefix", SettingType.BOOLEAN, true);
    private final Setting showSuffix = new Setting("Show Suffix", SettingType.BOOLEAN, true);
    private final Setting backgroundAlpha = new Setting("Background Alpha", SettingType.NUMBER, 91.25418060200668, 0, 255);
    private final Setting pingReserveWidth = new Setting("Ping Reserve Width", SettingType.NUMBER, 33.61204013377927, 0, 50);
    private final Setting highlightSelf = new Setting("Highlight Self", SettingType.BOOLEAN, true);
    private final Setting filterNPC = new Setting("Filter NPC", SettingType.BOOLEAN, true);
    private final Setting balanceColumns = new Setting("Balance Columns", SettingType.BOOLEAN, true);
    private final Setting playerEntryHeight = new Setting("Player Row Height", SettingType.NUMBER, 15.288135593220339, 10, 20);
    private final Setting showSkinStats = new Setting("Show Skin Stats", SettingType.BOOLEAN, true);
    private final Setting showLangStats = new Setting("Show Lang Stats", SettingType.BOOLEAN, true);
    private final Setting highlightFriends = new Setting("Highlight Friends", SettingType.BOOLEAN, true);
    private final Setting friendTag = new Setting("Friend Tag", SettingType.BOOLEAN, false);
    private final Setting murderMysteryRoles = new Setting("MurderMysteryRoles", SettingType.BOOLEAN, true);
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
        addSetting(highlightFriends);
        addSetting(friendTag);
        addSetting(showSkinStats);
        addSetting(showLangStats);
        addSetting(murderMysteryRoles);
        addSetting(tabAnimation);
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
        OnlineMode.getInstance().addListener(onlineModeListener);

        bootstrapEntries();
        injectHandler();

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
        removeHandler();
        entries.clear();
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

    // ====== NETTY HANDLER ======

    /**
     * Пакеты, пришедшие до инжекта хендлера, мы пропустили — поэтому один раз
     * заполняем список из уже существующих данных клиента.
     */
    private void bootstrapEntries() {
        entries.clear();
        if (mc.getConnection() == null) return;
        try {
            for (NetworkPlayerInfo info : mc.getConnection().getPlayerInfoMap()) {
                GameProfile profile = info.getGameProfile();
                if (profile.getId() == null) continue;
                TabEntry e = new TabEntry(profile, entrySeq.getAndIncrement());
                e.ping = info.getResponseTime();
                e.gameType = info.getGameType();
                e.displayName = info.getDisplayName();
                entries.put(profile.getId(), e);
            }
        } catch (Exception ignored) {}
    }

    private void injectHandler() {
        try {
            if (mc.getConnection() == null) return;
            ChannelPipeline pipeline = mc.getConnection().getNetworkManager().channel().pipeline();
            if (pipeline.get(HANDLER_NAME) != null) return;

            pipeline.addBefore("packet_handler", HANDLER_NAME, new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    try {
                        handlePacket(msg);
                    } catch (Throwable ignored) {}
                    // Пакет ВСЕГДА пропускаем дальше: иначе сломается playerInfoMap клиента (скины, getPlayerInfo и т.д.)
                    super.channelRead(ctx, msg);
                }
            });

        } catch (Exception ignored) {}
    }

    private void removeHandler() {
        try {
            if (mc.getConnection() != null) {
                ChannelPipeline pipeline = mc.getConnection().getNetworkManager().channel().pipeline();
                if (pipeline.get(HANDLER_NAME) != null) {
                    pipeline.remove(HANDLER_NAME);
                }
            }
        } catch (Exception ignored) {}
    }

    /** Вызывается в netty-потоке. Не трогать мир/GUI напрямую — только через addScheduledTask. */
    private void handlePacket(Object msg) {
        if (msg instanceof SPacketJoinGame) {
            // Новый сервер/мир: сервер пришлёт список игроков заново
            entries.clear();
            mc.addScheduledTask(this::injectHandler);
            return;
        }
        if (msg instanceof SPacketRespawn) {
            mc.addScheduledTask(this::injectHandler);
            return;
        }
        if (!(msg instanceof SPacketPlayerListItem)) return;

        SPacketPlayerListItem packet = (SPacketPlayerListItem) msg;
        SPacketPlayerListItem.Action action = packet.getAction();

        for (SPacketPlayerListItem.AddPlayerData data : packet.getEntries()) {
            GameProfile profile = data.getProfile();
            if (profile.getId() == null) continue;
            UUID id = profile.getId();

            switch (action) {
                case ADD_PLAYER: {
                    TabEntry e = new TabEntry(profile, entrySeq.getAndIncrement());
                    e.ping = data.getPing();
                    e.gameType = data.getGameMode();
                    e.displayName = data.getDisplayName();
                    entries.put(id, e);
                    break;
                }
                case UPDATE_GAME_MODE: {
                    TabEntry e = entries.get(id);
                    if (e != null) e.gameType = data.getGameMode();
                    break;
                }
                case UPDATE_LATENCY: {
                    TabEntry e = entries.get(id);
                    if (e != null) e.ping = data.getPing();
                    break;
                }
                case UPDATE_DISPLAY_NAME: {
                    TabEntry e = entries.get(id);
                    if (e != null) e.displayName = data.getDisplayName();
                    break;
                }
                case REMOVE_PLAYER:
                    entries.remove(id);
                    break;
            }
        }
    }

    // ====== FORGE EVENTS ======

    @SubscribeEvent
    public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        entries.clear();
        injectHandler();
        bootstrapEntries();
    }

    @SubscribeEvent
    public void onClientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        removeHandler();
        entries.clear();
        PLAYER_DATA.clear();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isToggled() || mc.player == null) return;

        // Pipeline может быть пересоздан — периодически проверяем хендлер
        if (mc.player.ticksExisted % 100 == 0) {
            injectHandler();
            if (entries.isEmpty() && mc.getConnection() != null) {
                bootstrapEntries();
            }
        }
    }

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

    // ====== DATA ======

    private void updatePlayerData() {
        PLAYER_DATA.clear();
        if (mc.player == null || mc.player.connection == null || mc.world == null) return;

        boolean npcFilterEnabled = (boolean) filterNPC.getValue();
        MurderAPI murderAPI = MurderAPI.getInstance();
        boolean hasConnection = OnlineMode.getInstance().isConnected() && !OnlineMode.getInstance().isGuest();

        // Снимок списка, собранного из пакетов
        List<TabEntry> snapshot = new ArrayList<>(entries.values());

        for (TabEntry entry : snapshot) {
            GameProfile profile = entry.profile;
            String rawName = profile.getName();
            if (rawName == null) continue;
            if (npcFilterEnabled && NPCValidator.isNPC(rawName)) continue;

            String displayName = maskNameIfNeeded(rawName);

            ScorePlayerTeam team = mc.world.getScoreboard().getPlayersTeam(rawName);

            PlayerData data = new PlayerData();
            data.uuid = profile.getId();
            data.name = displayName;
            data.realNetworkName = rawName;
            data.order = (int) entry.seq;
            data.ping = entry.ping;
            // Чистим лишние пробелы в префиксе/суффиксе
            data.prefix = (team != null) ? cleanSpaces(team.getPrefix(), false) : "";
            data.suffix = (team != null) ? cleanSpaces(team.getSuffix(), true) : "";
            data.teamName = team != null ? team.getName() : "";
            data.teamId = extractTeamId(data.teamName);
            data.hasForge = checkForge(profile);
            data.lang = getLanguage(profile);
            data.isFriend = PlayerListManager.isFriend(rawName);

            NetworkPlayerInfo vanillaInfo = mc.player.connection.getPlayerInfo(profile.getId());
            if (vanillaInfo != null) {
                String skinPath = vanillaInfo.getLocationSkin().getResourcePath();
                data.defaultSteve = "textures/entity/steve.png".equals(skinPath);
                data.defaultAlex = "textures/entity/alex.png".equals(skinPath);
            }

            data.role = murderAPI.getRole(rawName);

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
     * Нормализует пробелы в строке с цветовыми кодами (§x):
     *  - подряд идущие пробелы схлопываются в один;
     *  - пробелы в начале удаляются;
     *  - trimEnd = true  -> пробелы и "пустые" коды в конце удаляются полностью;
     *  - trimEnd = false -> в конце остаётся максимум один пробел.
     */
    private static String cleanSpaces(String s, boolean trimEnd) {
        if (s == null || s.isEmpty()) return "";

        StringBuilder sb = new StringBuilder(s.length());
        boolean lastSpace = true; // true => пробелы в начале пропускаются
        int len = s.length();

        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c == '\u00A7' && i + 1 < len) {
                sb.append(c).append(s.charAt(i + 1));
                i++;
                continue;
            }
            if (c == ' ' || c == '\t' || c == '\u00A0') {
                if (lastSpace) continue;
                sb.append(' ');
                lastSpace = true;
            } else {
                sb.append(c);
                lastSpace = false;
            }
        }

        if (trimEnd) {
            while (true) {
                int l = sb.length();
                if (l > 0 && sb.charAt(l - 1) == ' ') {
                    sb.setLength(l - 1);
                } else if (l >= 2 && sb.charAt(l - 2) == '\u00A7') {
                    sb.setLength(l - 2);
                } else {
                    break;
                }
            }
        }
        return sb.toString();
    }

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

    private float[] getEspColor(String settingName, float[] fallback) {
        try {
            Module esp = ModuleManager.getModuleByName("ESP");
            if (esp == null) return fallback;
            Setting s = esp.getSettingByName(settingName);
            if (s == null) return fallback;
            Object v = s.getValue();
            if (v instanceof float[]) return (float[]) v;
        } catch (Exception ignored) {}
        return fallback;
    }

    // ====== RENDER ======

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
            case "CMTabList":
                sortedPlayers.sort(Comparator.<PlayerData, String>comparing(p -> p.teamId, Comparator.nullsLast(Comparator.<String>naturalOrder()))
                        .thenComparing(p -> p.teamName == null ? "" : p.teamName)
                        .thenComparing(p -> p.name));
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
        boolean rolesEnabled = (boolean) murderMysteryRoles.getValue();

        float[] murdererRgb  = getEspColor("Murderer Color",  new float[]{1.0f, 0.0f, 0.0f});
        float[] detectiveRgb = getEspColor("Detective Color", new float[]{0.0f, 0.0f, 1.0f});
        float[] innocentRgb  = getEspColor("Innocent Color",  new float[]{0.0f, 1.0f, 0.0f});

        int maxContentWidth = 0;
        int friendCount = 0;
        int steveCount = 0;
        int alexCount = 0;
        int ruCount = 0;
        int enCount = 0;

        for (PlayerData data : sortedPlayers) {
            String infoTags = buildInfoTags(data);
            String displayName = buildDisplayName(data, pfx, sfx);
            String fullLine = displayName + (infoTags.isEmpty() ? "" : TAG_SEPARATOR + infoTags);
            maxContentWidth = Math.max(maxContentWidth, fr.getStringWidth(fullLine));
            if (data.isFriend) friendCount++;
            if (data.defaultSteve) steveCount++;
            else if (data.defaultAlex) alexCount++;
            if ("ru".equalsIgnoreCase(data.lang)) ruCount++;
            else if ("en".equalsIgnoreCase(data.lang)) enCount++;
        }

        int headSectionWidth = LEFT_PAD + headSize + 4;
        int colWidth = headSectionWidth + maxContentWidth + pingReserve;
        int desiredColumns = Math.max(1, (int) Math.ceil((double) totalPlayers / maxPlayersPerCol));
        int maxAvailableColumns = (screenWidth - (MARGIN * 2) + DEFAULT_COLUMN_GAP) / (colWidth + DEFAULT_COLUMN_GAP);
        int columns = Math.min(desiredColumns, Math.max(1, maxAvailableColumns));
        boolean balance = (boolean) balanceColumns.getValue();
        int rowsPerColumn = balance ? (int) Math.ceil((double) totalPlayers / columns) : Math.min(maxPlayersPerCol, totalPlayers);
        rowsPerColumn = Math.max(1, rowsPerColumn);
        columns = Math.max(1, (int) Math.ceil((double) totalPlayers / rowsPerColumn));

        String header = "Players: " + totalPlayers;
        if ((boolean) highlightFriends.getValue() && friendCount > 0) header += " §8| §a" + friendCount + " §7friends";
        if ((boolean) showSkinStats.getValue()) {
            header += " §8| §bSteve §f" + steveCount + " §8/ §dAlex §f" + alexCount;
        }
        if ((boolean) showLangStats.getValue()) {
            header += " §8| §cRU §f" + ruCount + " §8/ §9EN §f" + enCount;
        }

        int tabWidth = (columns * colWidth) + ((columns - 1) * DEFAULT_COLUMN_GAP) + (MARGIN * 2);
        int headerWidth = Math.max(fr.getStringWidth(header), measureDrawnWidth(fr, header));
        tabWidth = Math.max(tabWidth, headerWidth + MARGIN * 2 + 2);

        colWidth = Math.max(colWidth, (tabWidth - MARGIN * 2 - (columns - 1) * DEFAULT_COLUMN_GAP) / columns);
        int tabHeight = HEADER_HEIGHT + (rowsPerColumn * rowHeight);
        int startX = (screenWidth - tabWidth) / 2;
        int startY = MARGIN;

        Gui.drawRect(startX, startY, startX + tabWidth, startY + tabHeight, (bgAlpha << 24));
        Gui.drawRect(startX, startY, startX + tabWidth, startY + HEADER_HEIGHT, (bgAlpha << 24));

        fr.drawStringWithShadow(header, startX + MARGIN, startY + 5, 0xFFFFFF);

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
            String infoTags = buildInfoTags(data);

            // Фон строки
            int rowColor;
            if (isMe && (boolean) highlightSelf.getValue()) {
                rowColor = 0x22FFFFFF;
            } else {
                rowColor = 0x1A8899AA;
            }
            Gui.drawRect(renderX, renderY, renderX + colWidth, renderY + rowHeight, rowColor);

            // Голова
            NetworkPlayerInfo info = mc.player.connection.getPlayerInfo(data.uuid);
            if (info != null) {
                mc.getTextureManager().bindTexture(info.getLocationSkin());
                GlStateManager.enableBlend();
                float headY = renderY + (rowHeight - headSize) / 2.0f;
                drawHeadRect(renderX + LEFT_PAD, headY, 8, headSize);
                drawHeadRect(renderX + LEFT_PAD, headY, 40, headSize);
            }

            float textX = renderX + headSectionWidth;
            float textY = renderY + (rowHeight - 8) / 2.0f;

            int nameColor = 0xFFFFFF;
            boolean useRoleColor = false;
            if (rolesEnabled && "1_default".equals(data.teamName)) {
                useRoleColor = true;
                switch (data.role) {
                    case MURDERER:
                        nameColor = rgbToInt(murdererRgb);
                        break;
                    case DETECTIVE:
                        nameColor = rgbToInt(detectiveRgb);
                        break;
                    default:
                        nameColor = rgbToInt(innocentRgb);
                        break;
                }
            }

            boolean animating = hasConnection
                    && data.animStyle != null && !"Off".equals(data.animStyle)
                    && data.animColors != null && data.animColors.length > 0;

            if (animating) {
                String fullLine = buildDisplayName(data, pfx, sfx)
                        + (infoTags.isEmpty() ? "" : TAG_SEPARATOR + infoTags);
                drawAnimatedName(fr, fullLine, textX, textY, data.animColors, data.animSpeed, nowMs);
            } else if (useRoleColor) {
                float cursorX = textX;

                if (pfx && data.prefix != null && !data.prefix.isEmpty()) {
                    fr.drawStringWithShadow(data.prefix, cursorX, textY, 0xFFFFFF);
                    cursorX += fr.getStringWidth(data.prefix);
                }

                String namePart = "§r" + data.name;
                fr.drawStringWithShadow(namePart, cursorX, textY, nameColor);
                cursorX += fr.getStringWidth(namePart);

                if (sfx && data.suffix != null && !data.suffix.isEmpty()) {
                    String suffixPart = " " + data.suffix;
                    fr.drawStringWithShadow(suffixPart, cursorX, textY, 0xFFFFFF);
                    cursorX += fr.getStringWidth(suffixPart);
                }

                if (!infoTags.isEmpty()) {
                    String tagPart = TAG_SEPARATOR + infoTags;
                    fr.drawStringWithShadow(tagPart, cursorX, textY, 0xFFFFFF);
                }
            } else {
                String fullLine = buildDisplayName(data, pfx, sfx)
                        + (infoTags.isEmpty() ? "" : TAG_SEPARATOR + infoTags);
                fr.drawStringWithShadow(fullLine, textX, textY, 0xFFFFFF);
            }

            String pingStr = data.ping + "ms";
            int pingColor = (data.ping < 100) ? 0x00FF00 : (data.ping < 200) ? 0xFFFF00 : 0xFF0000;
            fr.drawStringWithShadow(pingStr, renderX + colWidth - fr.getStringWidth(pingStr) - 3, textY, pingColor);
        }
    }

    private int rgbToInt(float[] rgb) {
        int r = (int) (Math.max(0f, Math.min(1f, rgb[0])) * 255f) & 0xFF;
        int g = (int) (Math.max(0f, Math.min(1f, rgb[1])) * 255f) & 0xFF;
        int b = (int) (Math.max(0f, Math.min(1f, rgb[2])) * 255f) & 0xFF;
        return (r << 16) | (g << 8) | b;
    }

    private int measureDrawnWidth(FontRenderer fr, String text) {
        return fr.drawStringWithShadow(text, 0f, -1000f, 0xFFFFFF);
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

    /** Теги разделяются ровно одним пробелом. */
    private String buildInfoTags(PlayerData data) {
        StringBuilder sb = new StringBuilder();
        if (data.isFriend && (boolean) highlightFriends.getValue() && (boolean) friendTag.getValue()) {
            sb.append("§2[Friend]");
        }
        if (data.hasForge) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("§6[Forge]");
        }
        if (data.lang != null && !data.lang.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("§7[").append(data.lang.trim()).append(']');
        }
        return sb.toString();
    }

    private int clampAlpha(int value) {
        return Math.max(0, Math.min(255, value));
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

    private String extractTeamId(String teamName) {
        if (teamName == null || teamName.isEmpty()) return null;

        int qIndex = teamName.indexOf('Q');
        if (qIndex < TEAM_ID_LENGTH) return null;

        String candidate = teamName.substring(qIndex - TEAM_ID_LENGTH, qIndex);
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            boolean isHex = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!isHex) return null;
        }
        return candidate.toLowerCase();
    }

    private boolean checkForge(GameProfile profile) {
        try {
            com.mojang.authlib.properties.Property forge = profile.getProperties().get("forgeClient")
                    .stream().findFirst().orElse(null);
            if (forge != null && "true".equalsIgnoreCase(forge.getValue())) return true;
            com.mojang.authlib.properties.Property extra = profile.getProperties().get("extraData")
                    .stream().findFirst().orElse(null);
            return extra != null && extra.getValue().contains("FML");
        } catch (Exception ignored) {}
        return false;
    }

    private String getLanguage(GameProfile profile) {
        try {
            com.mojang.authlib.properties.Property langId = profile.getProperties().get("lang_id")
                    .stream().findFirst().orElse(null);
            if (langId != null) {
                String val = langId.getValue();
                return (val != null && val.contains("_")) ? val.split("_")[0] : val;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static class TabEntry {
        final GameProfile profile;
        final long seq;
        volatile int ping;
        volatile GameType gameType;
        volatile ITextComponent displayName;

        TabEntry(GameProfile profile, long seq) {
            this.profile = profile;
            this.seq = seq;
        }
    }

    private static class PlayerData {
        UUID uuid;
        String name, realNetworkName, prefix, suffix, lang, teamName, teamId, animStyle;
        boolean hasForge, isFriend, defaultSteve, defaultAlex;
        MurderAPI.Role role = MurderAPI.Role.INNOCENT;
        long crc32;
        int order, ping, animSpeed;
        int[] animColors;
    }
}