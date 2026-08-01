package real.inkognito338.murdermysteryutils.online;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 24.07.2026
 */

public class TabAnimationData {

    private static final Map<String, AnimationEntry> animations = new ConcurrentHashMap<>();
    private static final List<String> availableStyles = new ArrayList<>();
    // Кеш цветов и скоростей для стилей (заполняется при получении с сервера)
    private static final Map<String, int[]> styleColors = new HashMap<>();
    private static final Map<String, Integer> styleSpeeds = new HashMap<>();

    public static void update(String nick, String style, int speed, int[] colors) {
        animations.put(nick.toLowerCase(), new AnimationEntry(style, speed, colors));
        // Сохраняем цвета и скорость для стиля
        if (style != null && !"Off".equalsIgnoreCase(style)) {
            if (colors != null && colors.length > 0) {
                styleColors.put(style.toLowerCase(), colors);
            }
            if (speed > 0) {
                styleSpeeds.put(style.toLowerCase(), speed);
            }
        }
    }

    public static void update(String nick, String style, int speed) {
        animations.put(nick.toLowerCase(), new AnimationEntry(style, speed, null));
        if (style != null && !"Off".equalsIgnoreCase(style) && speed > 0) {
            styleSpeeds.put(style.toLowerCase(), speed);
        }
    }

    public static void remove(String nick) {
        animations.remove(nick.toLowerCase());
    }

    public static AnimationEntry get(String nick) {
        return animations.get(nick.toLowerCase());
    }

    public static boolean has(String nick) {
        return animations.containsKey(nick.toLowerCase());
    }

    public static void clear() {
        animations.clear();
    }

    public static List<String> getAvailableStyles() {
        return availableStyles;
    }

    public static void setAvailableStyles(List<String> styles) {
        availableStyles.clear();
        if (styles != null) {
            availableStyles.addAll(styles);
        }
    }

    /**
     * Регистрирует метаданные стиля (цвета/скорость), пришедшие вместе со
     * списком доступных стилей (например, в расширенном формате ответа
     * get_all, где сервер отдаёт не просто имена стилей, а объекты
     * {style, colors, speed}). Это позволяет превью в GUI отрисовываться
     * сразу при открытии меню, даже если ни один игрок сейчас не использует
     * данный стиль.
     */
    public static void registerStyleMeta(String style, int speed, int[] colors) {
        if (style == null || "Off".equalsIgnoreCase(style)) return;
        if (colors != null && colors.length > 0) {
            styleColors.put(style.toLowerCase(), colors);
        }
        if (speed > 0) {
            styleSpeeds.put(style.toLowerCase(), speed);
        }
    }

    /**
     * Получить цвета для стиля анимации.
     * Сначала ищет в кеше стилей, потом в анимациях игроков.
     * <p>
     * ВАЖНО: возвращает {@code null}, если данных о цветах для стиля ещё
     * нет (а не фиктивный белый цвет), чтобы GUI мог отличить
     * "анимация ещё не загружена" от "анимация реально одноцветная".
     * Ранее здесь возвращался {@code new int[]{0xFFFFFF}} даже для
     * отсутствующих данных, из-за чего drawAnimatedPreview() считал, что
     * цвета "есть", и рисовал статичный белый текст вместо анимации —
     * именно поэтому в GUI выбора анимаций превью не проигрывалось для
     * стилей, которые ещё не были "прогреты" через клик или sync_request.
     */
    public static int[] getColorsForStyle(String style) {
        if (style == null || "Off".equalsIgnoreCase(style)) {
            return null;
        }
        // Ищем в кеше стилей
        int[] cached = styleColors.get(style.toLowerCase());
        if (cached != null && cached.length > 0) {
            return cached;
        }
        // Ищем в анимациях игроков
        for (AnimationEntry entry : animations.values()) {
            if (entry.style.equalsIgnoreCase(style) && entry.colors != null && entry.colors.length > 0) {
                styleColors.put(style.toLowerCase(), entry.colors);
                return entry.colors;
            }
        }
        return null;
    }

    /**
     * Получить скорость для стиля анимации.
     * Сначала ищет в кеше стилей, потом в анимациях игроков.
     * Если данных нет — возвращает разумное значение по умолчанию (100),
     * так как скорость 0 или отрицательная сломала бы расчёт в
     * drawAnimatedPreview().
     */
    public static int getSpeedForStyle(String style) {
        if (style == null) return 100;
        // Ищем в кеше стилей
        Integer cached = styleSpeeds.get(style.toLowerCase());
        if (cached != null && cached > 0) {
            return cached;
        }
        // Ищем в анимациях игроков
        for (AnimationEntry entry : animations.values()) {
            if (entry.style.equalsIgnoreCase(style) && entry.speed > 0) {
                styleSpeeds.put(style.toLowerCase(), entry.speed);
                return entry.speed;
            }
        }
        return 100;
    }

    /**
     * Есть ли уже загруженные данные (цвета) для данного стиля.
     * Используется GUI, чтобы понять, нужно ли показывать
     * "заглушку загрузки" вместо анимированного превью.
     */
    public static boolean hasStyleMeta(String style) {
        if (style == null || "Off".equalsIgnoreCase(style)) return true;
        if (styleColors.containsKey(style.toLowerCase())) return true;
        for (AnimationEntry entry : animations.values()) {
            if (entry.style.equalsIgnoreCase(style) && entry.colors != null && entry.colors.length > 0) {
                return true;
            }
        }
        return false;
    }

    public static class AnimationEntry {
        public final String style;
        public final int speed;
        public final int[] colors;

        public AnimationEntry(String style, int speed, int[] colors) {
            this.style = style;
            this.speed = speed;
            this.colors = colors;
        }
    }
}