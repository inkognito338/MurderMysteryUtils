package real.inkognito338.murdermysteryutils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
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
    public static final String VERSION = "1.2";

    private static final File CONFIG_DIR = new File(Minecraft.getMinecraft().mcDataDir, "MurderMysteryUtils");
    private static final File CONFIG_FILE = new File(CONFIG_DIR, "config.json");

    // Создаём KeyBinding для открытия настроек
    public static final KeyBinding OPEN_SETTINGS_KEY = new KeyBinding(
            "key.murdermysteryutils.open_settings",
            Keyboard.KEY_F6, // дефолтная клавиша
            "key.categories.murdermysteryutils"
    );

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Создание папки конфигурации
        if (!CONFIG_DIR.exists() && !CONFIG_DIR.mkdirs()) {
            System.err.println("Failed to create configuration directory: " + CONFIG_DIR.getAbsolutePath());
        }

        // Регистрируем бинды клавиш
        ClientRegistry.registerKeyBinding(OPEN_SETTINGS_KEY);

        // Регистрируем события
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
        MinecraftForge.EVENT_BUS.register(new Chams());
        MinecraftForge.EVENT_BUS.register(new CommandManager());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // Проверяем нажатие на зарегистрированную клавишу
            if (OPEN_SETTINGS_KEY.isPressed()) {
                Minecraft.getMinecraft().displayGuiScreen(new SettingsGUI());
            }
        }
    }

    public static File getConfigFile() {
        return CONFIG_FILE;
    }
}
