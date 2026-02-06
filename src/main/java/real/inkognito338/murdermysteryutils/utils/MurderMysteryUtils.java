package real.inkognito338.murdermysteryutils.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.common.MinecraftForge;

import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("SpellCheckingInspection")
public class MurderMysteryUtils {

    private final Minecraft mc = Minecraft.getMinecraft();

    /** Убийца */
    private String murderer = null;

    /** Детективы */
    private final Set<String> detectives = new HashSet<>();

    /** Включить дебаг в чат */
    public boolean DEBUG = false;
    private int debugTicks = 0;

    /** Сообщения ресета */
    private static final String[] RESET_MESSAGES = {
            "MurderMystery ▸ Перемещаем в следующую игру",
            "Союз с убийцей не допускается!",
    };

    public MurderMysteryUtils() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    /** Сброс ролей */
    private void reset() {
        murderer = null;
        detectives.clear();
        if (DEBUG && mc.player != null) {
            mc.player.sendMessage(new TextComponentString("§e[MM-API] §cRESET — новый раунд"));
        }
    }

    /** Слушаем чат на ресет */
    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent e) {
        String msg = e.getMessage().getUnformattedText();
        for (String trigger : RESET_MESSAGES) {
            if (msg.equals(trigger) || msg.startsWith(trigger)) {
                reset();
                return;
            }
        }
    }

    /** Обновляем каждый тик */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        update();
    }

    /** Основное обновление */
    public void update() {
        if (mc.world == null || mc.player == null) return;

        String self = mc.player.getName();
        debugTicks++;

        // Временные переменные для нового тика
        String newMurderer = null;
        Set<String> newDetectives = new HashSet<>();

        for (EntityPlayer pl : mc.world.playerEntities) {
            if (pl.getName().equals(self)) continue;

            for (ItemStack stack : pl.getHeldEquipment()) {
                if (stack.isEmpty()) continue;

                // Убийца = меч ИЛИ ножницы
                if (newMurderer == null && (stack.getItem() instanceof ItemSword || stack.getItem() instanceof ItemShears)) {
                    newMurderer = pl.getName();
                    if (DEBUG) mc.player.sendMessage(new TextComponentString("§c[MM-API] §6Убийца найден: §e" + newMurderer));
                }

                // Детектив = лук
                if (stack.getItem() instanceof ItemBow) {
                    newDetectives.add(pl.getName());
                    if (DEBUG && !detectives.contains(pl.getName())) {
                        mc.player.sendMessage(new TextComponentString("§b[MM-API] §aДетектив найден: §e" + pl.getName()));
                    }
                }
            }
        }

        // Обновляем убийцу только если нашли нового
        if (newMurderer != null) murderer = newMurderer;
        // Добавляем всех новых детективов
        detectives.addAll(newDetectives);

        // Проверка: не удаляем игроков, если они есть в таб-листе
        retainValidPlayers();

        // Раз в секунду вывод дебага
        if (DEBUG && debugTicks >= 20) {
            debugTicks = 0;
            mc.player.sendMessage(new TextComponentString("§7===== §eMM-API DEBUG §7====="));
            mc.player.sendMessage(new TextComponentString("§cУбийца: §6" + (murderer != null ? murderer : "не найден")));
            mc.player.sendMessage(new TextComponentString("§bДетективы: §e" + (detectives.isEmpty() ? "нет" : detectives.toString())));
            mc.player.sendMessage(new TextComponentString("§7Игроков в мире: " + mc.world.playerEntities.size()));
        }
    }

    /** Удаляем только игроков, которых нет в таб-листе и нет в мире */
    private void retainValidPlayers() {
        // Убийца
        if (murderer != null && !isPlayerValid(murderer)) {
            if (DEBUG) mc.player.sendMessage(new TextComponentString("§c[MM-API] §6Убийца вышел/не найден: §e" + murderer));
            murderer = null;
        }

        // Детективы
        detectives.removeIf(name -> !isPlayerValid(name));
    }

    /** Проверка, что игрок есть в мире или в таб-листе */
    private boolean isPlayerValid(String playerName) {
        if (playerName == null) return false;

        // В мире
        for (EntityPlayer player : mc.world.playerEntities) {
            if (player.getName().equals(playerName)) return true;
        }

        // В таб-листе
        if (mc.player != null && mc.player.connection != null) {
            for (NetworkPlayerInfo info : mc.player.connection.getPlayerInfoMap()) {
                if (info.getGameProfile().getName().equals(playerName)) return true;
            }
        }

        return false;
    }

    // ———————————— API ————————————

    public String getMurderer() {
        return murderer;
    }

    public boolean hasMurderer() {
        return murderer != null;
    }

    public Set<String> getDetectives() {
        return new HashSet<>(detectives);
    }

    public boolean isDetective(String name) {
        return detectives.contains(name);
    }
}