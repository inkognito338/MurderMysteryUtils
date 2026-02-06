package real.inkognito338.murdermysteryutils.modules;

import real.inkognito338.murdermysteryutils.ConfigManager;
import real.inkognito338.murdermysteryutils.modules.settings.Setting;
import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    private String name;
    private boolean toggled;
    private List<Setting> settings = new ArrayList<>();

    public Module(String name) {
        this.name = name;
        this.toggled = false;
    }

    public String getName() {
        return name;
    }

    public boolean isToggled() {
        return toggled;
    }

    public void toggle() {
        toggled = !toggled;
        if (toggled) {
            onEnable();
        } else {
            onDisable();
        }
        ConfigManager.save();
    }

    public void setToggled(boolean toggled) {
        if (this.toggled != toggled) {
            this.toggled = toggled;
            if (toggled) {
                onEnable();
            } else {
                onDisable();
            }
            ConfigManager.save();
        }
    }

    public List<Setting> getSettings() {
        return settings;
    }

    public void addSetting(Setting setting) {
        settings.add(setting);
    }

    public Setting getSettingByName(String name) {
        for (Setting setting : settings) {
            if (setting.getName().equalsIgnoreCase(name)) {
                return setting;
            }
        }
        return null;
    }

    public void enable() {
        setToggled(true);
    }

    public void disable() {
        setToggled(false);
    }

    public void onEnable() {
        // Пустая реализация по умолчанию
    }

    public void onDisable() {
        // Пустая реализация по умолчанию
    }
}