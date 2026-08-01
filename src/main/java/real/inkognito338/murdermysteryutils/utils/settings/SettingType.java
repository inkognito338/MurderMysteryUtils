package real.inkognito338.murdermysteryutils.utils.settings;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

public enum SettingType {
    BOOLEAN,
    NUMBER,
    MODE,
    COLOR,
    TEXT,
    HUD_POSITION,
    TAB_ANIMATION,
    // Новые типы для LocalAPI
    SCRIPT_LIST,      // Список скриптов с переключателями
    SCRIPT_BUTTON,    // Кнопка для действия со скриптом
    SCRIPT_FOLDER     // Кнопка открытия папки
}
