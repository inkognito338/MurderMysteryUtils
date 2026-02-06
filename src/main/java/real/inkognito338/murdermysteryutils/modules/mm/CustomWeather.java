package real.inkognito338.murdermysteryutils.modules.mm;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.SPacketChangeGameState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import real.inkognito338.murdermysteryutils.modules.Module;
import real.inkognito338.murdermysteryutils.modules.settings.Setting;
import real.inkognito338.murdermysteryutils.modules.settings.SettingType;

public class CustomWeather extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();
    private static final String HANDLER_NAME = "weather_handler";

    // Состояния погоды
    public enum WeatherType {
        CLEAR("Clear", 0),
        RAIN("Rain", 1),
        THUNDER("Thunder", 2);

        private final String name;
        private final int id;

        WeatherType(String name, int id) {
            this.name = name;
            this.id = id;
        }

        @Override
        public String toString() {
            return name;
        }

        public int getId() {
            return id;
        }

        public static WeatherType fromId(int id) {
            for (WeatherType type : values()) {
                if (type.id == id) return type;
            }
            return CLEAR;
        }
    }

    public CustomWeather() {
        super("CustomWeather");
        addSetting(new Setting("Weather", SettingType.MODE, "Clear", "Clear", "Rain", "Thunder"));
        addSetting(new Setting("Strength", SettingType.NUMBER, 1.0, 0.0, 1.0));
        addSetting(new Setting("Rain", SettingType.BOOLEAN, true));
        addSetting(new Setting("Thunder", SettingType.BOOLEAN, true));
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
        injectHandler();
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        removeHandler();
        // Восстанавливаем нормальную погоду при выключении
        if (mc.world != null) {
            mc.world.setRainStrength(0.0f);
            mc.world.setThunderStrength(0.0f);
        }
    }

    private void injectHandler() {
        try {
            if (mc.getConnection() != null && mc.getConnection().getNetworkManager().channel() != null) {
                if (mc.getConnection().getNetworkManager().channel().pipeline().get(HANDLER_NAME) == null) {
                    mc.getConnection().getNetworkManager().channel().pipeline().addBefore("packet_handler", HANDLER_NAME, new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            // Блокируем пакеты изменения состояния игры (погода)
                            if (isToggled()) {
                                if (msg instanceof SPacketChangeGameState) {
                                    SPacketChangeGameState packet = (SPacketChangeGameState) msg;
                                    // Проверяем тип пакета: 7 - дождь, 8 - гроза
                                    int stateId = packet.getGameState();
                                    if (stateId == 7 || stateId == 8) {
                                        return; // Блокируем пакет погоды
                                    }
                                }
                            }
                            super.channelRead(ctx, msg);
                        }
                    });
                }
            }
        } catch (Exception ignored) {}
    }

    private void removeHandler() {
        try {
            if (mc.getConnection() != null && mc.getConnection().getNetworkManager().channel() != null) {
                if (mc.getConnection().getNetworkManager().channel().pipeline().get(HANDLER_NAME) != null) {
                    mc.getConnection().getNetworkManager().channel().pipeline().remove(HANDLER_NAME);
                }
            }
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (mc.world == null || !isToggled()) return;

        // Периодическая проверка инъекции
        if (mc.player != null && mc.player.ticksExisted % 40 == 0) {
            injectHandler();
        }

        // Получаем настройки
        String weatherMode = (String) getSettingByName("Weather").getValue();
        float strength = ((Number) getSettingByName("Strength").getValue()).floatValue();
        boolean allowRain = (Boolean) getSettingByName("Rain").getValue();
        boolean allowThunder = (Boolean) getSettingByName("Thunder").getValue();

        // Применяем выбранную погоду
        applyWeather(weatherMode, strength, allowRain, allowThunder);
    }

    private void applyWeather(String weatherMode, float strength, boolean allowRain, boolean allowThunder) {
        if (mc.world == null) return;

        // Устанавливаем погоду в зависимости от выбранного режима
        switch (weatherMode) {
            case "Clear":
                mc.world.setRainStrength(0.0f);
                mc.world.setThunderStrength(0.0f);
                break;

            case "Rain":
                if (allowRain) {
                    mc.world.setRainStrength(strength);
                    mc.world.setThunderStrength(0.0f);
                } else {
                    mc.world.setRainStrength(0.0f);
                    mc.world.setThunderStrength(0.0f);
                }
                break;

            case "Thunder":
                if (allowThunder) {
                    // Для грозы устанавливаем и дождь, и грозу
                    mc.world.setRainStrength(strength);
                    mc.world.setThunderStrength(strength);
                } else if (allowRain) {
                    // Если гром запрещен, но дождь разрешен
                    mc.world.setRainStrength(strength);
                    mc.world.setThunderStrength(0.0f);
                } else {
                    mc.world.setRainStrength(0.0f);
                    mc.world.setThunderStrength(0.0f);
                }
                break;
        }
    }

    // Дополнительный метод для быстрого изменения погоды через команды
    public void setWeather(WeatherType type, float strength) {
        if (isToggled() && mc.world != null) {
            switch (type) {
                case CLEAR:
                    getSettingByName("Weather").setValue("Clear");
                    break;
                case RAIN:
                    getSettingByName("Weather").setValue("Rain");
                    break;
                case THUNDER:
                    getSettingByName("Weather").setValue("Thunder");
                    break;
            }
            getSettingByName("Strength").setValue((double) strength);
        }
    }

    // Метод для быстрого включения/выключения дождя
    public void toggleRain() {
        Setting rainSetting = getSettingByName("Rain");
        if (rainSetting != null) {
            boolean current = (Boolean) rainSetting.getValue();
            rainSetting.setValue(!current);
        }
    }

    // Метод для быстрого включения/выключения грома
    public void toggleThunder() {
        Setting thunderSetting = getSettingByName("Thunder");
        if (thunderSetting != null) {
            boolean current = (Boolean) thunderSetting.getValue();
            thunderSetting.setValue(!current);
        }
    }
}