package real.inkognito338.murdermysteryutils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;
import real.inkognito338.murdermysteryutils.modules.ModuleManager;
import real.inkognito338.murdermysteryutils.modules.mm.*;

import java.io.File;

@SuppressWarnings("SpellCheckingInspection")
@Mod(modid = Main.MODID, name = Main.NAME, version = Main.VERSION)
public class Main {

    public Main() {
        // Инициализация Mixin
        MixinBootstrap.init();
        Mixins.addConfiguration("mixins.murdermysteryutils.json");
    }

    public static final String MODID = "murdermysteryutils";
    public static final String NAME = "MurderMystery Utils";
    public static final String VERSION = "2.0";

    private static final File CONFIG_DIR = new File(Minecraft.getMinecraft().mcDataDir, "MurderMysteryUtils");
    private static final File CONFIG_FILE = new File(CONFIG_DIR, "config.json");

    public static final KeyBinding OPEN_SETTINGS_KEY = new KeyBinding(
            "Open MurderMystery Utils GUI",
            Keyboard.KEY_F6,
            "MurderMystery Utils"
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
        MinecraftForge.EVENT_BUS.register(new CommandManager());

        // Регистрируем модули
        ModuleManager.addModule(new AntiBlind());
        ModuleManager.addModule(new AutoNext());
        ModuleManager.addModule(new AutoRoleAnnounce());
        ModuleManager.addModule(new BarrierVision());
        ModuleManager.addModule(new BowESP());
        ModuleManager.addModule(new CustomTime());
        ModuleManager.addModule(new CustomWeather());
        ModuleManager.addModule(new ESP());
        ModuleManager.addModule(new FakeGM1());
        ModuleManager.addModule(new FlowerPotESP());
        ModuleManager.addModule(new Fly());
        ModuleManager.addModule(new HUD());
        ModuleManager.addModule(new ItemESP());
        ModuleManager.addModule(new NameTags());
        ModuleManager.addModule(new ShowNames());
        ModuleManager.addModule(new Spammer());
        ModuleManager.addModule(new Sprint());
        ModuleManager.addModule(new Tracers());
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // Загружаем настройки ПОСЛЕ регистрации всех модулей
        ConfigManager.init(); // Инициализируем и загружаем настройки
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            if (OPEN_SETTINGS_KEY.isPressed()) {
                Minecraft.getMinecraft().displayGuiScreen(new SettingsGUI());
            }
        }
    }

    public static File getConfigFile() {
        return CONFIG_FILE;
    }
}