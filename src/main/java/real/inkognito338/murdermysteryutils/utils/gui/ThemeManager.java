package real.inkognito338.murdermysteryutils.utils.gui;

import real.inkognito338.murdermysteryutils.utils.ConfigManager;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 09.07.2026
 */

public class ThemeManager {
    // Текущая тема (индекс в списке)
    private static int currentThemeIndex = 0; // Midnight по умолчанию

    // Названия тем
    private static final String[] THEME_NAMES = {
            "Midnight", "Dark", "Nord", "Dracula", "Rose Pine",
            "Gruvbox", "Moonlight", "Matcha", "Ocean", "Nebula"
    };

    // Цвета для каждой темы (19 цветов на тему)
    private static final int[][] THEME_COLORS = {
            // Midnight
            {0xFF121212, 0xFF0A0A0A, 0xFF1F1F1F, 0xFF1F1F1F, 0xFF2A2A2A, 0xFF383838,
                    0xFFFFFFFF, 0xFFB3B3B3, 0xFF666666, 0xFF0A84FF, 0xFF005BB5,
                    0xFF2A2A2A, 0xFF30D158, 0xFF4A4A4A, 0x40666666,
                    0xFF121212, 0xFF1F1F1F, 0xFF181818, 0xFF2A2A2A},

            // Dark
            {0xFF1A1A1A, 0xFF212121, 0xFF2A2A2A, 0xFF1F1F1F, 0xFF262626, 0xFF2A2A2A,
                    0xFFFFFFFF, 0xFFE0E0E0, 0xFF707070, 0xFF569CD6, 0xFF1A3A5A,
                    0xFF2E2E2E, 0xFF4CAF50, 0xFF333333, 0x35FFFFFF,
                    0xFF1A1A1A, 0xFF141414, 0xFF141414, 0xFF252525},
            // Nord
            {0xFF2E3440, 0xFF242933, 0xFF3B4252, 0xFF3B4252, 0xFF434C5E, 0xFF4C566A,
                    0xFFECEFF4, 0xFFE5E9F0, 0xFFD8DEE9, 0xFF88C0D0, 0xFF5E81AC,
                    0xFF4C566A, 0xFFA3BE8C, 0xFF4C566A, 0x40D8DEE9,
                    0xFF2E3440, 0xFF3B4252, 0xFF3B4252, 0xFF434C5E},
            // Dracula
            {0xFF282A36, 0xFF21222C, 0xFF44475A, 0xFF44475A, 0xFF52556C, 0xFF6272A4,
                    0xFFF8F8F2, 0xFFE2E2DC, 0xFF6272A4, 0xFFBD93F9, 0xFFFF79C6,
                    0xFF44475A, 0xFF50FA7B, 0xFF6272A4, 0x406272A4,
                    0xFF282A36, 0xFF44475A, 0xFF282A36, 0xFF44475A},
            // Rose Pine
            {0xFF191724, 0xFF15131E, 0xFF26233A, 0xFF26233A, 0xFF312E4A, 0xFF403D52,
                    0xFFE0DEF4, 0xFF908CAA, 0xFF6E6A86, 0xFFC4A7E7, 0xFF9CCFD8,
                    0xFF26233A, 0xFF31748F, 0xFF6E6A86, 0x406E6A86,
                    0xFF191724, 0xFF26233A, 0xFF1F1D2E, 0xFF26233A},
            // Gruvbox
            {0xFF282828, 0xFF1D2021, 0xFF3C3836, 0xFF3C3836, 0xFF504945, 0xFF665C54,
                    0xFFEBDBB2, 0xFFD5C4A1, 0xFFA89984, 0xFFFABD2F, 0xFFD79921,
                    0xFF504945, 0xFFB8BB26, 0xFF928374, 0x40A89984,
                    0xFF282828, 0xFF3C3836, 0xFF3C3836, 0xFF504945},
            // Moonlight
            {0xFF0F111A, 0xFF090A0F, 0xFF1F2233, 0xFF1F2233, 0xFF2A2F45, 0xFF3B4261,
                    0xFFC8D3F5, 0xFF828BB8, 0xFF545C7E, 0xFF82AAFF, 0xFF3654A5,
                    0xFF222436, 0xFFC3E88D, 0xFF545C7E, 0x40545C7E,
                    0xFF0F111A, 0xFF1F2233, 0xFF1E2030, 0xFF2F334D},
            // Matcha
            {0xFF1A201A, 0xFF121612, 0xFF252D25, 0xFF252D25, 0xFF2F392F, 0xFF3A473A,
                    0xFFD3E0D3, 0xFF9CB39C, 0xFF658065, 0xFF89B482, 0xFF5C8557,
                    0xFF2F392F, 0xFFA9C9A4, 0xFF4A5F4A, 0x40658065,
                    0xFF1A201A, 0xFF252D25, 0xFF202720, 0xFF2D382D},
            // Ocean
            {0xFF0F1C2E, 0xFF0B1422, 0xFF1B2A41, 0xFF1B2A41, 0xFF243653, 0xFF324A6D,
                    0xFFE0E8F5, 0xFF98ADC9, 0xFF5C7A9E, 0xFF4BA3E3, 0xFF2A6D9E,
                    0xFF243653, 0xFF47D19E, 0xFF5C7A9E, 0x405C7A9E,
                    0xFF0F1C2E, 0xFF1B2A41, 0xFF152336, 0xFF20334E},
            // Nebula
            {0xFF130B1C, 0xFF1A1025, 0xFF2A1A40, 0xFF201430, 0xFF2D1C44, 0xFF3D2659,
                    0xFFF0E6FF, 0xFFB899D9, 0xFF7A5E99, 0xFFFF42A1, 0xFFB5287B,
                    0xFF3D2659, 0xFFC742FF, 0xFF4A3066, 0x40B899D9,
                    0xFF130B1C, 0xFF1A1025, 0xFF201430, 0xFF2D1C44}
    };

    // Индексы цветов
    private static final int BACKGROUND = 0;
    private static final int SIDEBAR = 1;
    private static final int SIDEBAR_SEPARATOR = 2;
    private static final int ELEMENT_BG = 3;
    private static final int ELEMENT_BG_HOVER = 4;
    private static final int ELEMENT_BG_ACTIVE = 5;
    private static final int TEXT_PRIMARY = 6;
    private static final int TEXT_SECONDARY = 7;
    private static final int TEXT_DIM = 8;
    private static final int ACCENT = 9;
    private static final int ACCENT_DARK = 10;
    private static final int BORDER = 11;
    private static final int SWITCH_ON = 12;
    private static final int SWITCH_OFF = 13;
    private static final int SCROLLBAR = 14;
    private static final int SEARCH_BG = 15;
    private static final int SEARCH_BG_FOCUSED = 16;
    private static final int DROPDOWN_BG = 17;
    private static final int DROPDOWN_HOVER = 18;

    public static String getCurrentThemeName() {
        return THEME_NAMES[currentThemeIndex];
    }

    public static void setCurrentTheme(int index) {
        if (index >= 0 && index < THEME_NAMES.length) {
            currentThemeIndex = index;
            ConfigManager.saveThemeName(THEME_NAMES[index]);
        }
    }

    public static void setCurrentThemeByName(String name) {
        for (int i = 0; i < THEME_NAMES.length; i++) {
            if (THEME_NAMES[i].equalsIgnoreCase(name)) {
                currentThemeIndex = i;
                ConfigManager.saveThemeName(THEME_NAMES[i]);
                return;
            }
        }
    }

    public static void loadThemeByName(String name) {
        for (int i = 0; i < THEME_NAMES.length; i++) {
            if (THEME_NAMES[i].equalsIgnoreCase(name)) {
                currentThemeIndex = i;
                return;
            }
        }
        currentThemeIndex = 1; // Midnight по умолчанию
    }

    public static int getBackground() {
        return THEME_COLORS[currentThemeIndex][BACKGROUND];
    }

    public static int getSidebar() {
        return THEME_COLORS[currentThemeIndex][SIDEBAR];
    }

    public static int getSidebarSeparator() {
        return THEME_COLORS[currentThemeIndex][SIDEBAR_SEPARATOR];
    }

    public static int getElementBg() {
        return THEME_COLORS[currentThemeIndex][ELEMENT_BG];
    }

    public static int getElementBgHover() {
        return THEME_COLORS[currentThemeIndex][ELEMENT_BG_HOVER];
    }

    public static int getElementBgActive() {
        return THEME_COLORS[currentThemeIndex][ELEMENT_BG_ACTIVE];
    }

    public static int getTextPrimary() {
        return THEME_COLORS[currentThemeIndex][TEXT_PRIMARY];
    }

    public static int getTextSecondary() {
        return THEME_COLORS[currentThemeIndex][TEXT_SECONDARY];
    }

    public static int getTextDim() {
        return THEME_COLORS[currentThemeIndex][TEXT_DIM];
    }

    public static int getAccent() {
        return THEME_COLORS[currentThemeIndex][ACCENT];
    }

    public static int getAccentDark() {
        return THEME_COLORS[currentThemeIndex][ACCENT_DARK];
    }

    public static int getBorder() {
        return THEME_COLORS[currentThemeIndex][BORDER];
    }

    public static int getSwitchOn() {
        return THEME_COLORS[currentThemeIndex][SWITCH_ON];
    }

    public static int getSwitchOff() {
        return THEME_COLORS[currentThemeIndex][SWITCH_OFF];
    }

    public static int getScrollbar() {
        return THEME_COLORS[currentThemeIndex][SCROLLBAR];
    }

    public static int getSearchBg() {
        return THEME_COLORS[currentThemeIndex][SEARCH_BG];
    }

    public static int getSearchBgFocused() {
        return THEME_COLORS[currentThemeIndex][SEARCH_BG_FOCUSED];
    }

    public static int getDropdownBg() {
        return THEME_COLORS[currentThemeIndex][DROPDOWN_BG];
    }

    public static int getDropdownHover() {
        return THEME_COLORS[currentThemeIndex][DROPDOWN_HOVER];
    }

    public static String[] getThemeNames() {
        return THEME_NAMES.clone();
    }

    public static int getThemeCount() {
        return THEME_NAMES.length;
    }

    public static int getCurrentThemeIndex() {
        return currentThemeIndex;
    }

    public static int getColorByTheme(int themeIndex, int colorIndex) {
        if (themeIndex >= 0 && themeIndex < THEME_COLORS.length) {
            return THEME_COLORS[themeIndex][colorIndex];
        }
        return THEME_COLORS[1][colorIndex];
    }

    public static int[] getThemeColors(int themeIndex) {
        if (themeIndex >= 0 && themeIndex < THEME_COLORS.length) {
            return THEME_COLORS[themeIndex].clone();
        }
        return THEME_COLORS[1].clone();
    }
}