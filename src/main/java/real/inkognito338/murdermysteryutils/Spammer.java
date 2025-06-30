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
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Spammer {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final List<String> messages = new ArrayList<>();

    private static final Random random = new Random();

    private int tickDelay = 0;

    private static final Pattern VALID_NAME = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");

    public Spammer() {
        loadMessages();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (!SettingsOption.SPAMMER.getValue() || mc.player == null || mc.world == null)
            return;
        if (this.tickDelay > 0) {
            this.tickDelay--;
            return;
        }
        String msg = getRandomMessageWithPlayers();
        if (msg != null) {
            mc.player.sendChatMessage(msg);
            this.tickDelay = 100 + random.nextInt(100);
        }
    }

    private void loadMessages() {
        File file = new File(Main.getConfigFile().getParentFile(), "spam.txt");
        if (!file.exists())
            return;
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty())
                    messages.add(line);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load spam messages", e);
        }
    }

    private String getRandomMessageWithPlayers() {
        if (messages.isEmpty())
            return null;

        List<String> validNames = new ArrayList<>();
        try {
            for (EntityPlayer player : mc.world.playerEntities) {
                if (player == mc.player)
                    continue;
                String name = player.getName();
                if (VALID_NAME.matcher(name).matches())
                    validNames.add(name);
            }
        } catch (Exception e) {
            LOGGER.error("Error collecting player names for spam message", e);
        }

        if (validNames.isEmpty())
            validNames.add("inkognito338");

        Collections.shuffle(validNames);

        String raw;
        try {
            raw = messages.get(random.nextInt(messages.size()));
        } catch (Exception e) {
            LOGGER.error("Error selecting spam message", e);
            return null;
        }

        for (int i = 1; i <= 5; i++) {
            String placeholder = (i == 1) ? "{player}" : ("{player" + i + "}");
            String name = (i <= validNames.size()) ? validNames.get(i - 1) : "inkognito338";
            raw = raw.replace(placeholder, name);
        }

        return raw;
    }
}
