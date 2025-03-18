package real.inkognito338.murdermysteryutils;

// by inkognito338 | forge 1.12.2 - 14.23.5.2860


public enum SettingsOption {
    MURDERER_ESP("Murderer ESP"),
    DETECTIVE_ESP("Detective ESP"),
    OTHER_ESP("Other ESP"),
    //RESET_ON_MESSAGES("Reset on messages (not work)"),
    INFO_GUI("Info GUI"),
    SPRINT("Sprint"),
    GOLD_INGOT_ESP("Gold Ingot ESP"),
    BOW_ESP("Bow ESP"),
    NAME_TAGS("NameTags"), // Включает/выключает отображение имён игроков
    MURDERER_ITEM_SWORD("Murderer Item (Iron Sword)"),  // Новый параметр для выбора железного меча
    MURDERER_ITEM_SHEARS("Murderer Item (Shears)"); // Новый параметр для выбора ножниц



    private final String displayName;
    private boolean value;

    SettingsOption(String displayName) {
        this.displayName = displayName;
        this.value = false;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean getValue() {
        return value;
    }

    public void setValue(boolean value) {
        this.value = value;
    }

    public void toggle() {
        this.value = !this.value;
    }
}
