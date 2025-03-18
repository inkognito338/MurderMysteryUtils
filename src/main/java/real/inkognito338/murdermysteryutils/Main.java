package real.inkognito338.murdermysteryutils;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderWorldLastEvent;
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
    public static final String VERSION = "1.0";

    @Mod.Instance
    public static Main instance;

    private static final File CONFIG_DIR = new File(Minecraft.getMinecraft().mcDataDir, "MurderMysteryUtils");
    private static final File CONFIG_FILE = new File(CONFIG_DIR, "config.json");

    // Ссылка на ESPRenderer для добавления сообщений
    private static ESPRenderer espRenderer;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Создаем папку конфигурации, если её нет
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
        }

        // Загружаем настройки
        ConfigManager.loadSettings();

        // Регистрируем события
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new ESPRenderer());
        MinecraftForge.EVENT_BUS.register(new MurderMysteryTracker());
        MinecraftForge.EVENT_BUS.register(new NameRenderer());
        MinecraftForge.EVENT_BUS.register(new InfoGUI());
        MinecraftForge.EVENT_BUS.register(new Sprint());
        MinecraftForge.EVENT_BUS.register(new ItemESP());
        MinecraftForge.EVENT_BUS.register(new ChatMessageHandler(Minecraft.getMinecraft(), new ESPRenderer()));
    }

    public static void setEspRenderer(ESPRenderer renderer) {
        espRenderer = renderer;
    }

    // Обработчик нажатия клавиши F6 для открытия GUI
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
