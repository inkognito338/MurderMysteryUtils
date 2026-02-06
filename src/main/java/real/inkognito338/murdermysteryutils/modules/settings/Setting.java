package real.inkognito338.murdermysteryutils.modules.settings;

import java.util.Arrays;
import java.util.List;

public class Setting {
    private final String name;
    private final SettingType type;
    private Object value;
    private final Object defaultValue;
    private final Object min;
    private final Object max;
    private final List<String> modes;

    public Setting(String name, SettingType type, Object defaultValue) {
        this(name, type, defaultValue, null, null, null);
    }

    public Setting(String name, SettingType type, Object defaultValue, Object min, Object max) {
        this(name, type, defaultValue, min, max, null);
    }

    public Setting(String name, SettingType type, Object defaultValue, String... modes) {
        this(name, type, defaultValue, null, null, Arrays.asList(modes));
    }

    private Setting(String name, SettingType type, Object defaultValue, Object min, Object max, List<String> modes) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.min = min;
        this.max = max;
        this.modes = modes;
    }

    public String getName() { return name; }
    public SettingType getType() { return type; }
    public Object getValue() { return value; }
    public Object getDefaultValue() { return defaultValue; }
    public Object getMin() { return min; }
    public Object getMax() { return max; }
    public List<String> getModes() { return modes; }

    public String getMode() {
        if (type == SettingType.MODE && value instanceof String) {
            return (String) value;
        }
        return null;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    // Цикл по режимам
    public void cycle(int direction) {
        if (type != SettingType.MODE || modes == null || modes.isEmpty()) return;

        int index = modes.indexOf(value);
        if (index == -1) index = 0;

        int newIndex = (index + direction + modes.size()) % modes.size();
        value = modes.get(newIndex);
    }

    public void resetToDefault() {
        this.value = defaultValue;
    }
}