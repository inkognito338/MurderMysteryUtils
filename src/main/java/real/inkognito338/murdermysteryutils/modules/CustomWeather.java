package real.inkognito338.murdermysteryutils.modules;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.SPacketChangeGameState;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

public class CustomWeather extends Module {

    private static final Logger LOGGER = LogManager.getLogger("MurderMysteryUtils");
    private static final String HANDLER_NAME = "weather_handler";

    /** Статический инстанс для доступа из Mixin-а */
    public static CustomWeather INSTANCE;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Map<Biome, Float> originalTemperatures = new HashMap<>();

    // ---- Reflection-поля ----

    private Field temperatureField;
    private Field gameStateField;

    private Field prevRainingStrengthField;
    private Field rainingStrengthField;
    private Field prevThunderingStrengthField;
    private Field thunderingStrengthField;

    private Field rainTimeField;
    private Field thunderTimeField;
    private Field thunderingField;
    private Field rainingField;

    private boolean snowMode = false;

    // =====================================================================
    //  Конструктор
    // =====================================================================

    public CustomWeather() {
        super("CustomWeather");
        INSTANCE = this;

        addSetting(new Setting("Weather",  SettingType.MODE,   "Clear", "Clear", "Rain", "Thunder", "Snow"));
        addSetting(new Setting("Strength", SettingType.NUMBER, 1.0, 0.0, 1.0));

        initReflection();
    }

    // =====================================================================
    //  Инициализация reflection
    // =====================================================================

    private void initReflection() {
        temperatureField = findField(Biome.class, "temperature", "field_76750_F");
        gameStateField   = findField(SPacketChangeGameState.class, "state", "field_149141_a");

        Class<?> world = net.minecraft.world.World.class;
        prevRainingStrengthField    = findField(world, "prevRainingStrength",    "field_73312_A");
        rainingStrengthField        = findField(world, "rainingStrength",        "field_73311_y");
        prevThunderingStrengthField = findField(world, "prevThunderingStrength", "field_96444_z");
        thunderingStrengthField     = findField(world, "thunderingStrength",     "field_96445_A");
        rainTimeField               = findField(world, "rainTime",               "field_73314_C");
        thunderTimeField            = findField(world, "thunderingTime",         "field_73318_D");
        thunderingField             = findField(world, "thundering",             "field_73301_E");
        rainingField                = findField(world, "raining",                "field_147442_T");
    }

    private static Field findField(Class<?> clazz, String mcp, String srg) {
        for (String name : new String[]{mcp, srg}) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Exception ignored) {}
        }
        // Поиск в супер-классах
        for (String name : new String[]{mcp, srg}) {
            Class<?> c = clazz.getSuperclass();
            while (c != null) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (Exception ignored) {}
                c = c.getSuperclass();
            }
        }
        return null;
    }

    // =====================================================================
    //  Включение / выключение
    // =====================================================================

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
        injectHandler();
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        removeHandler();
        snowMode = false;

        if (mc.world != null) {
            mc.world.setRainStrength(0.0f);
            mc.world.setThunderStrength(0.0f);
        }

        restoreBiomeTemperatures();
    }

    // =====================================================================
    //  Netty-перехватчик
    // =====================================================================

    private void injectHandler() {
        try {
            if (mc.getConnection() == null) return;

            ChannelPipeline pipeline = mc.getConnection().getNetworkManager().channel().pipeline();
            if (pipeline.get(HANDLER_NAME) != null) return;

            pipeline.addBefore("packet_handler", HANDLER_NAME, new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    if (isToggled() && msg instanceof SPacketChangeGameState && gameStateField != null) {
                        try {
                            int stateId = gameStateField.getInt(msg);
                            // 1 = начало дождя, 2 = конец дождя, 7 = сила дождя, 8 = сила грозы
                            if (stateId == 1 || stateId == 2 || stateId == 7 || stateId == 8) {
                                return;
                            }
                        } catch (IllegalAccessException ignored) {}
                    }
                    super.channelRead(ctx, msg);
                }
            });
        } catch (Exception ignored) {}
    }

    private void removeHandler() {
        try {
            if (mc.getConnection() == null) return;

            ChannelPipeline pipeline = mc.getConnection().getNetworkManager().channel().pipeline();
            if (pipeline.get(HANDLER_NAME) != null) {
                pipeline.remove(HANDLER_NAME);
            }
        } catch (Exception ignored) {}
    }

    // =====================================================================
    //  Тик
    // =====================================================================

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (mc.world == null || !isToggled()) return;

        if (mc.player != null && mc.player.ticksExisted % 40 == 0) {
            injectHandler();
        }

        String weatherMode = (String) getSettingByName("Weather").getValue();
        float  strength    = ((Number) getSettingByName("Strength").getValue()).floatValue();

        applyWeather(weatherMode, strength);
        forceWorldWeatherFields(weatherMode, strength);
        forceWorldWeatherTimers(weatherMode);
    }

    // =====================================================================
    //  Применение погоды
    // =====================================================================

    private void applyWeather(String weatherMode, float strength) {
        if (mc.world == null) return;

        switch (weatherMode) {
            case "Clear":
                mc.world.setRainStrength(0.0f);
                mc.world.setThunderStrength(0.0f);
                if (snowMode) { snowMode = false; restoreBiomeTemperatures(); }
                break;

            case "Rain":
                mc.world.setRainStrength(strength);
                mc.world.setThunderStrength(0.0f);
                if (snowMode) { snowMode = false; restoreBiomeTemperatures(); }
                break;

            case "Thunder":
                mc.world.setRainStrength(strength);
                mc.world.setThunderStrength(strength);
                if (snowMode) { snowMode = false; restoreBiomeTemperatures(); }
                break;

            case "Snow":
                mc.world.setRainStrength(strength);
                mc.world.setThunderStrength(0.0f);
                if (!snowMode) { snowMode = true; makeAllBiomesCold(); }
                break;

            default:
                break;
        }
    }

    // =====================================================================
    //  Принудительная перезапись float-полей
    // =====================================================================

    private void forceWorldWeatherFields(String weatherMode, float strength) {
        if (mc.world == null) return;

        float rain, thunder;
        switch (weatherMode) {
            case "Rain":
            case "Snow":
                rain = strength; thunder = 0f;
                break;
            case "Thunder":
                rain = strength; thunder = strength;
                break;
            default:
                rain = 0f; thunder = 0f;
                break;
        }

        try {
            if (rainingStrengthField        != null) rainingStrengthField.setFloat(mc.world, rain);
            if (prevRainingStrengthField    != null) prevRainingStrengthField.setFloat(mc.world, rain);
            if (thunderingStrengthField     != null) thunderingStrengthField.setFloat(mc.world, thunder);
            if (prevThunderingStrengthField != null) prevThunderingStrengthField.setFloat(mc.world, thunder);
        } catch (Exception e) {
            LOGGER.error("[CustomWeather] forceWorldWeatherFields failed", e);
        }
    }

    // =====================================================================
    //  Обнуление счётчиков тиков (фикс против сервера)
    // =====================================================================

    private void forceWorldWeatherTimers(String weatherMode) {
        if (mc.world == null) return;

        boolean wantRain    = !weatherMode.equals("Clear");
        boolean wantThunder = weatherMode.equals("Thunder");

        try {
            if (rainTimeField    != null) rainTimeField.setInt(mc.world, 0);
            if (thunderTimeField != null) thunderTimeField.setInt(mc.world, 0);
            if (rainingField     != null) rainingField.setBoolean(mc.world, wantRain);
            if (thunderingField  != null) thunderingField.setBoolean(mc.world, wantThunder);
        } catch (Exception e) {
            LOGGER.error("[CustomWeather] forceWorldWeatherTimers failed", e);
        }
    }

    // =====================================================================
    //  Биомы → снег
    // =====================================================================

    private void makeAllBiomesCold() {
        if (temperatureField == null) return;
        try {
            for (Biome biome : Biome.REGISTRY) {
                if (biome == null) continue;
                if (!originalTemperatures.containsKey(biome)) {
                    originalTemperatures.put(biome, biome.getDefaultTemperature());
                }
                temperatureField.setFloat(biome, 0.0f);
            }
        } catch (Exception e) {
            LOGGER.error("[CustomWeather] makeAllBiomesCold failed", e);
        }
    }

    private void restoreBiomeTemperatures() {
        if (temperatureField == null || originalTemperatures.isEmpty()) return;
        try {
            for (Map.Entry<Biome, Float> entry : originalTemperatures.entrySet()) {
                temperatureField.setFloat(entry.getKey(), entry.getValue());
            }
            originalTemperatures.clear();
        } catch (Exception ignored) {}
    }

    // =====================================================================
    //  Геттер для Mixin-а
    // =====================================================================

    /** Возвращает true если сейчас должен идти снег (используется в RenderWeatherMixin). */
    public boolean isSnowMode() {
        return isToggled() && snowMode;
    }
}