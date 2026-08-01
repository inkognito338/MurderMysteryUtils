package real.inkognito338.murdermysteryutils.utils;

import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import java.util.ArrayList;
import java.util.List;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

public abstract class Module {
    private final String name;
    private boolean toggled;
    private boolean restricted = false;
    private final List<Setting> settings = new ArrayList<>();

    public Module(String name) {
        this.name = name;
        this.toggled = false;
    }

    public String getName() {
        return name;
    }

    public boolean isToggled() {
        return toggled && !restricted;
    }

    public boolean isActuallyToggled() {
        return toggled;
    }

    public boolean isRestricted() {
        return restricted;
    }

    public void setRestricted(boolean restricted) {
        this.restricted = restricted;
        if (restricted && toggled) {
            // Если модуль был включен, вызываем onDisable при ограничении
            onDisable();
        } else if (!restricted && toggled) {
            // Если ограничение снято и модуль включен, вызываем onEnable
            onEnable();
        }
    }

    public void toggle() {
        if (!restricted) {
            setToggled(!toggled);
        }
    }

    public void setToggled(boolean toggled) {
        if (this.toggled != toggled) {
            this.toggled = toggled;
            if (!restricted) {
                if (toggled) {
                    onEnable();
                } else {
                    onDisable();
                }
            }
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

    public void onEnable() {}

    public void onDisable() {}
}