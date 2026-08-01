package real.inkognito338.murdermysteryutils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;
import real.inkognito338.murdermysteryutils.commands.CommandManager;
import real.inkognito338.murdermysteryutils.modules.*;
import real.inkognito338.murdermysteryutils.online.OnlineChatUtils;
import real.inkognito338.murdermysteryutils.utils.*;
import real.inkognito338.murdermysteryutils.utils.gui.SettingsGUI;

import java.io.File;

@SuppressWarnings("SpellCheckingInspection")
@Mod(modid = Main.MODID, name = Main.NAME, version = Main.VERSION)
public class Main {

    public static final Logger LOGGER = LogManager.getLogger("MurderMysteryUtils");
    public static final String MODID = "murdermysteryutils";
    public static final String NAME = "MurderMysteryUtils";
    public static final String VERSION = "3.0";
    public static final String SOURCE_URL = "https://github.com/inkognito338/MurderMysteryUtils";
    public static final String ISSUES_URL = "https://github.com/inkognito338/MurderMysteryUtils/issues";

    public static final KeyBinding OPEN_SETTINGS_KEY = new KeyBinding(
            "Open " + NAME + " GUI",
            Keyboard.KEY_F6,
            NAME
    );

    private static final File CONFIG_DIR = new File(Minecraft.getMinecraft().mcDataDir, NAME);
    private static final File CONFIG_FILE = new File(CONFIG_DIR, "config.json");

    private static boolean updateSuccess = false;
    private static boolean modulesLoaded = false;

    static {
        System.setProperty("java.rmi.server.codebase", SOURCE_URL);
        System.setProperty("java.rmi.server.useCodebaseOnly", "false");
        System.setProperty("http.agent", NAME + "/" + VERSION + " (+" + SOURCE_URL + ")");
        System.setProperty("jsse.enableSNIExtension", "true");
    }

    public Main() {
        MixinBootstrap.init();
        Mixins.addConfiguration("mixins." + MODID + ".json");
        setupUncaughtExceptionHandler();
    }

    public static File getConfigFile() { return CONFIG_FILE; }
    public static File getConfigDir() { return CONFIG_DIR; }
    public static boolean isModulesLoaded() { return modulesLoaded; }
    public static boolean isUpdateSuccess() { return updateSuccess; }

    private void setupUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            LOGGER.error("Uncaught exception - report to " + ISSUES_URL, throwable);
            if (Minecraft.getMinecraft().player != null) {
                Minecraft.getMinecraft().player.sendMessage(
                        new net.minecraft.util.text.TextComponentString(
                                "§c[" + NAME + "] Error occurred! Report: " + ISSUES_URL
                        )
                );
            }
        });
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (!CONFIG_DIR.exists() && !CONFIG_DIR.mkdirs()) {
            System.err.println("Failed to create config directory: " + CONFIG_DIR.getAbsolutePath());
        }

        ConfigManager.init();
        DiscordRPC.init();
        BindManager.getInstance().init(CONFIG_DIR);
        LOGGER.info("{} v{} initialized | {}", NAME, VERSION, SOURCE_URL);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Регистрируем ключ
        ClientRegistry.registerKeyBinding(OPEN_SETTINGS_KEY);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new CommandManager());

        // Сначала добавляем все модули
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
        ModuleManager.addModule(new MurderAlert());
        ModuleManager.addModule(new FullBright());
        ModuleManager.addModule(new ChatTranslator());
        ModuleManager.addModule(new ChatIgnore());
        ModuleManager.addModule(new ChatColor());
        ModuleManager.addModule(new NameProtect());
        ModuleManager.addModule(new ArrayListMod());
        ModuleManager.addModule(new CustomTab());
        ModuleManager.addModule(new Scripts());

        // Теперь загружаем настройки (модули уже зарегистрированы)
        ConfigManager.loadSettings();

        OnlineChatUtils.getInstance();
        modulesLoaded = true;
        LOGGER.info("Modules loaded successfully");

        // Запускаем проверку обновлений и применяем ограничения
        new Thread(() -> {
            int attempts = 0;
            while (attempts < 120) {
                if (UpdateChecker.isUpdateSuccess()) {
                    updateSuccess = true;
                    LOGGER.info("Update check completed successfully");
                    applyRestrictions();
                    return;
                }
                try {
                    Thread.sleep(500);
                    attempts++;
                } catch (InterruptedException ignored) {}
            }

            LOGGER.warn("Update check timeout, loading modules with restrictions");
            updateSuccess = false;
            applyRestrictions();
        }).start();
    }

    public static void applyRestrictions() {
        String[] restrictedModules = {
                "AutoNext", "AutoRoleAnnounce", "CustomTab", "CustomTime",
                "CustomWeather", "FakeGM1", "ChatTranslator", "MurderAlert",
                "NameProtect", "ChatColor", "ChatIgnore", "Scripts", "ESP",
                "NameTags", "HUD", "ShowNames"
        };

        boolean shouldRestrict = !updateSuccess || ConfigManager.isStaticItems();

        for (String moduleName : restrictedModules) {
            ModuleManager.setRestricted(moduleName, shouldRestrict);
            if (shouldRestrict) {
                ModuleManager.setEnabled(moduleName, true);
            }
        }
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        LOGGER.info("{} loaded successfully", NAME);
        new Thread(CertManager::refreshCerts, NAME + "-CertRefresh").start();

        UpdateChecker.register();
        API.init();

        Runtime.getRuntime().addShutdownHook(new Thread(ConfigManager::save, NAME + "-SaveOnExit"));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (!modulesLoaded) return;

        if (event.phase == TickEvent.Phase.END && OPEN_SETTINGS_KEY.isPressed()) {
            //if (!ConfigManager.isStaticItems()) {
                Minecraft.getMinecraft().displayGuiScreen(new SettingsGUI());
            //}
        }
    }
}