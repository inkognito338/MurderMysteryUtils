package real.inkognito338.murdermysteryutils;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import java.io.File;

// by inkognito338 | forge 1.12.2 - 14.23.5.2860

@Mod(modid = Main.MODID, name = Main.NAME, version = Main.VERSION)
public class Main {
    public static final String MODID = "murdermysteryutils";
    public static final String NAME = "Murder Mystery Utils";
    public static final String VERSION = "1.1";

    private static final File CONFIG_DIR = new File(Minecraft.getMinecraft().mcDataDir, "MurderMysteryUtils");
    private static final File CONFIG_FILE = new File(CONFIG_DIR, "config.json");

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (!CONFIG_DIR.exists() && !CONFIG_DIR.mkdirs()) {
            System.err.println("Failed to create configuration directory: " + CONFIG_DIR.getAbsolutePath());
        }

        ConfigManager.loadSettings();

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new ESPRenderer());
        MinecraftForge.EVENT_BUS.register(new MurderMysteryTracker());
        MinecraftForge.EVENT_BUS.register(new NameRenderer());
        MinecraftForge.EVENT_BUS.register(new HUD());
        MinecraftForge.EVENT_BUS.register(new Fly());
        MinecraftForge.EVENT_BUS.register(new Tracers());
        MinecraftForge.EVENT_BUS.register(new Spammer());
        MinecraftForge.EVENT_BUS.register(new Sprint());
        MinecraftForge.EVENT_BUS.register(new ItemESP());
        MinecraftForge.EVENT_BUS.register(new ChatMessageHandler(Minecraft.getMinecraft(), new ESPRenderer()));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            if (Keyboard.isKeyDown(Keyboard.KEY_F6)) {
                Minecraft.getMinecraft().displayGuiScreen(new SettingsGUI());
            }
        }
    }

    public static File getConfigFile() {
        return CONFIG_FILE;
    }
}

