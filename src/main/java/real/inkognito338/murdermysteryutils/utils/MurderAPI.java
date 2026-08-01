package real.inkognito338.murdermysteryutils.utils;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.server.SPacketEntityEquipment;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

public class MurderAPI {

    // ── Debug ─────────────────────────────────────────────────────────────────
    private static final boolean DEBUG = false;
    private static final Logger LOGGER = LogManager.getLogger("MurderAPI");
    // ─────────────────────────────────────────────────────────────────────────

    private static MurderAPI instance;

    private static final String HANDLER_NAME = "mmutils_handler";
    private static final String BEFORE_HANDLER = "fml:packet_handler";

    private static final String[] RESET_MESSAGES = {
            "MurderMystery ▸ Перемещаем в следующую игру",
            "Союз с убийцей не допускается!",
            "MurderMystery ▸ Перезагрузка сервера через 10 секунд!"
    };

    public enum Role {
        INNOCENT, DETECTIVE, MURDERER
    }

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Map<String, Role> playerRoles = new HashMap<>();
    private int tickCounter = 0;

    private MurderAPI() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    public static MurderAPI getInstance() {
        if (instance == null) {
            instance = new MurderAPI();
        }
        return instance;
    }

    // ── Debug helper ──────────────────────────────────────────────────────────
    private void debug(String message) {
        if (!DEBUG) return;

        LOGGER.info("[DEBUG] {}", message);

        if (mc.player != null) {
            mc.player.sendMessage(
                    new TextComponentString(
                            TextFormatting.GRAY + "[" +
                                    TextFormatting.GOLD + "MurderAPI" +
                                    TextFormatting.GRAY + "] " +
                                    TextFormatting.WHITE + message
                    )
            );
        }
    }
    // ─────────────────────────────────────────────────────────────────────────

    private void dumpPipeline(ChannelPipeline pipeline) {
        StringBuilder sb = new StringBuilder("Pipeline handlers:\n");
        for (Map.Entry<String, ChannelHandler> entry : pipeline) {
            sb.append("  ").append(entry.getKey())
                    .append(" -> ").append(entry.getValue().getClass().getSimpleName())
                    .append("\n");
        }
        LOGGER.info(sb.toString());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (mc.world == null || mc.player == null) return;

        if (++tickCounter % 20 == 0) {
            injectHandler();
        }
    }

    @SubscribeEvent
    public void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        try {
            ChannelPipeline pipeline = event.getManager().channel().pipeline();
            dumpPipeline(pipeline);
            injectInto(pipeline);
        } catch (Exception ignored) {
        }
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        debug("Дисконнект — сброс состояния.");
        playerRoles.clear();
    }

    private void injectHandler() {
        if (mc.getConnection() == null) return;
        try {
            ChannelPipeline pipeline = mc.getConnection()
                    .getNetworkManager()
                    .channel()
                    .pipeline();
            injectInto(pipeline);
        } catch (Exception e) {
            debug("Ошибка при инжекте хендлера: " + e.getMessage());
        }
    }

    private void injectInto(ChannelPipeline pipeline) {
        if (pipeline.get(HANDLER_NAME) != null) return;

        // Проверяем наличие целевого хендлера
        if (pipeline.get(BEFORE_HANDLER) == null) {
            debug("Хендлер '" + BEFORE_HANDLER + "' не найден в pipeline, пропускаем инжект.");
            return;
        }

        pipeline.addBefore(BEFORE_HANDLER, HANDLER_NAME, new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof SPacketEntityEquipment) {
                    // Копируем ссылку для лямбды
                    final SPacketEntityEquipment packet = (SPacketEntityEquipment) msg;
                    mc.addScheduledTask(() -> handleEquipment(packet));
                }
                // Обязательно передаём пакет дальше по pipeline
                super.channelRead(ctx, msg);
            }
        });

        debug("Хендлер инжектирован перед '" + BEFORE_HANDLER + "'.");
    }

    private void handleEquipment(SPacketEntityEquipment packet) {
        EntityEquipmentSlot slot = packet.getEquipmentSlot();
        if (slot != EntityEquipmentSlot.MAINHAND && slot != EntityEquipmentSlot.OFFHAND) return;

        if (mc.world == null || mc.player == null) return;

        if (!(mc.world.getEntityByID(packet.getEntityID()) instanceof net.minecraft.entity.player.EntityPlayer)) return;

        net.minecraft.entity.player.EntityPlayer player =
                (net.minecraft.entity.player.EntityPlayer) mc.world.getEntityByID(packet.getEntityID());

        if (player == null) return;
        if (player.getName().equals(mc.player.getName())) return;

        ItemStack stack = packet.getItemStack();
        String name = player.getName();

        Role currentRole = playerRoles.get(name);
        if (currentRole == Role.MURDERER || currentRole == Role.DETECTIVE) {
            debug("Пакет от " + name + " проигнорирован — роль уже: " + currentRole);
            return;
        }

        if (!stack.isEmpty()) {
            if (stack.getItem() instanceof ItemSword || stack.getItem() instanceof ItemShears) {
                playerRoles.put(name, Role.MURDERER);
                debug("Игрок " + name + " → MURDERER ("
                        + stack.getItem().getRegistryName() + ", " + slot.name() + ")");
            } else if (stack.getItem() instanceof ItemBow) {
                playerRoles.put(name, Role.DETECTIVE);
                debug("Игрок " + name + " → DETECTIVE ("
                        + stack.getItem().getRegistryName() + ", " + slot.name() + ")");
            } else {
                debug("Игрок " + name + " — предмет не распознан: "
                        + stack.getItem().getRegistryName() + " (" + slot.name() + ")");
            }
        } else {
            debug("Игрок " + name + " — пустой слот (" + slot.name() + ")");
        }
    }

    public void removeHandler() {
        if (mc.getConnection() == null) return;
        try {
            ChannelPipeline pipeline = mc.getConnection()
                    .getNetworkManager()
                    .channel()
                    .pipeline();
            if (pipeline.get(HANDLER_NAME) != null) {
                pipeline.remove(HANDLER_NAME);
                debug("Хендлер удалён.");
            }
        } catch (Exception e) {
            debug("Ошибка при удалении хендлера: " + e.getMessage());
        }
        playerRoles.clear();
        debug("Роли сброшены.");
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        String msg = event.getMessage().getUnformattedText();
        for (String trigger : RESET_MESSAGES) {
            if (msg.startsWith(trigger)) {
                debug("Триггер сброса: \"" + trigger + "\"");
                playerRoles.clear();
                return;
            }
        }
    }

    @SubscribeEvent
    public void onWorldJoin(EntityJoinWorldEvent event) {
        if (event.getEntity() == Minecraft.getMinecraft().player && event.getWorld().isRemote) {
            playerRoles.clear();
        }
    }

    public Role getRole(String name)        { return playerRoles.getOrDefault(name, Role.INNOCENT); }
    public boolean isMurderer(String name)  { return getRole(name) == Role.MURDERER; }
    public boolean isDetective(String name) { return getRole(name) == Role.DETECTIVE; }
    public boolean isInnocent(String name)  { return getRole(name) == Role.INNOCENT; }

    public Set<String> getMurderers()  { return getByRole(Role.MURDERER);  }
    public Set<String> getDetectives() { return getByRole(Role.DETECTIVE); }
    public Set<String> getInnocents()  { return getByRole(Role.INNOCENT);  }

    private Set<String> getByRole(Role role) {
        Set<String> result = new HashSet<>();
        for (Map.Entry<String, Role> entry : playerRoles.entrySet()) {
            if (entry.getValue() == role) result.add(entry.getKey());
        }
        return Collections.unmodifiableSet(result);
    }

    public Map<String, Role> getAllRoles() {
        return Collections.unmodifiableMap(playerRoles);
    }
}