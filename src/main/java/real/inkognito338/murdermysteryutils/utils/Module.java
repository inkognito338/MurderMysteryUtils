package real.inkognito338.murdermysteryutils.utils;

import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    private final String name;
    private boolean toggled;
    private boolean restricted = false;
    private final List<Setting> settings = new ArrayList<>();

    public Module(String name) {
        this.name = name;
        this.toggled = false;
    }

    public String getName() { return name; }

    /**
     * Эффективное состояние — работает ли модуль прямо сейчас.
     * Учитывает блокировку: заблокированный модуль не работает,
     * даже если пользователь его включал.
     */
    public boolean isToggled() {
        return toggled && !restricted;
    }

    /** Реальное желание пользователя, без учёта блокировки. */
    public boolean isActuallyToggled() {
        return toggled;
    }

    public boolean isRestricted() {
        return restricted;
    }

    /**
     * Устанавливает/снимает блокировку.
     * ВАЖНО: toggled НЕ меняется. Меняется только эффективное состояние.
     * Если модуль был включён — при блокировке останавливаем его работу,
     * при разблокировке возобновляем. Если был выключен — ничего не делаем.
     */
    public void setRestricted(boolean restricted) {
        if (this.restricted == restricted) return;
        this.restricted = restricted;

        if (toggled) {
            if (restricted) {
                onDisable();   // останавливаем работу, но toggled остаётся true
            } else {
                onEnable();    // возобновляем работу
            }
        }
    }

    public void toggle() {
        if (restricted) return;       // заблокировано — переключение запрещено
        setToggled(!toggled);
    }

    public void setToggled(boolean toggled) {
        if (this.toggled == toggled) return;
        this.toggled = toggled;
        // Реагируем на изменение только если модуль не заблокирован.
        // Если заблокирован — просто запоминаем желание, но не запускаем/не останавливаем.
        if (!restricted) {
            if (toggled) onEnable();
            else onDisable();
        }
    }

    public List<Setting> getSettings() { return settings; }
    public void addSetting(Setting setting) { settings.add(setting); }

    public Setting getSettingByName(String name) {
        for (Setting setting : settings) {
            if (setting.getName().equalsIgnoreCase(name)) return setting;
        }
        return null;
    }

    public void onEnable() {}
    public void onDisable() {}
}