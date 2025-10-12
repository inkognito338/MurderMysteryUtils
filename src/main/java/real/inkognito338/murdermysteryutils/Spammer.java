package real.inkognito338.murdermysteryutils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Spammer {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final List<String> messages = new ArrayList<>();
    private static final Random random = new Random();
    private final Pattern VALID_NAME = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");

    private int tickDelay = 0;

    // Список для текущей очереди сообщений
    private final List<String> queue = new ArrayList<>();

    public Spammer() {
        loadMessages();
        shuffleQueue();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (!SettingsOption.SPAMMER.getValue() || mc.player == null || mc.world == null)
            return;

        if (tickDelay > 0) {
            tickDelay--;
            return;
        }

        if (queue.isEmpty()) {
            shuffleQueue();
        }

        String raw = queue.remove(0); // Берём следующее сообщение из очереди
        raw = replacePlaceholders(raw);

        if (raw != null) {
            mc.player.sendChatMessage(raw);
            this.tickDelay = 120 + random.nextInt(21);
        }
    }

    private void loadMessages() {
        File file = new File(Main.getConfigFile().getParentFile(), "spam.txt");
        if (!file.exists()) return;

        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) messages.add(line);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load spam messages", e);
        }
    }

    private void shuffleQueue() {
        queue.clear();
        queue.addAll(messages);
        Collections.shuffle(queue); // перемешиваем порядок сообщений
    }

    private String replacePlaceholders(String raw) {
        if (raw == null) return null;

        List<String> validNames = getPlayersFromTab();
        for (int i = 1; i <= 5; i++) {
            String placeholder = (i == 1) ? "{player}" : "{player" + i + "}";
            String name = validNames.isEmpty() ? "inkognito338"
                    : validNames.get(random.nextInt(validNames.size()));
            raw = raw.replace(placeholder, name);
        }
        return raw;
    }

    private List<String> getPlayersFromTab() {
        List<String> validNames = new ArrayList<>();
        try {
            if (mc.player.connection != null) {
                for (NetworkPlayerInfo info : mc.player.connection.getPlayerInfoMap()) {
                    String name = info.getGameProfile().getName();
                    if (VALID_NAME.matcher(name).matches() && !name.equals(mc.player.getName())) {
                        validNames.add(name);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error collecting player names from tab", e);
        }
        return validNames;
    }
}
