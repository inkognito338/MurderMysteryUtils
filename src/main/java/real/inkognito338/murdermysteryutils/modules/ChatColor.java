package real.inkognito338.murdermysteryutils.modules;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

@SideOnly(Side.CLIENT)
public class ChatColor extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();

    // Порядок: код амперсанда → название галочки
    private static final char[] COLOR_CODES = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    private static final String[] COLOR_NAMES = {
            "Black (&0)",       "Dark Blue (&1)",    "Dark Green (&2)", "Dark Aqua (&3)",
            "Dark Red (&4)",    "Dark Purple (&5)",  "Gold (&6)",       "Gray (&7)",
            "Dark Gray (&8)",   "Blue (&9)",         "Green (&a)",      "Aqua (&b)",
            "Red (&c)",         "Light Purple (&d)", "Yellow (&e)",     "White (&f)"
    };

    // Пул для анти-повтора
    private final List<Character> colorPool = new ArrayList<>();
    private char lastUsed = '\0';

    /** Префиксные символы, которые остаются без цвета в начале сообщения. */
    private static final String PREFIX_CHARS = "!@#$";

    public ChatColor() {
        super("ChatColor");

        // Тип чата — лимит символов
        addSetting(new Setting("ChatType", SettingType.MODE, "Auto",
                "Auto", "1.10 (100 chars)", "1.11+ (255 chars)"));

        // Галочки цветов (по умолчанию все включены)
        for (int i = 0; i < COLOR_CODES.length; i++) {
            addSetting(new Setting(COLOR_NAMES[i], SettingType.BOOLEAN, true));
        }
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    /** Лимит символов по настройке ChatType. */
    private int getMaxLength() {
        Setting s = getSettingByName("ChatType");
        String mode = (s != null && s.getValue() instanceof String) ? (String) s.getValue() : "Auto";
        switch (mode) {
            case "1.10 (100 chars)":  return 100;
            case "1.11+ (255 chars)": return 256;
            default: // Auto — определяем по версии
                try {
                    String ver = Minecraft.getMinecraft().getVersion();
                    if (ver.startsWith("1.8")) return 100;
                } catch (Exception ignored) {}
                return 256;
        }
    }

    /** Возвращает список активных цветовых кодов по текущим галочкам. */
    private List<Character> getActiveColors() {
        List<Character> active = new ArrayList<>();
        for (int i = 0; i < COLOR_CODES.length; i++) {
            Setting s = getSettingByName(COLOR_NAMES[i]);
            if (s != null && Boolean.TRUE.equals(s.getValue())) {
                active.add(COLOR_CODES[i]);
            }
        }
        return active;
    }

    /** Заново перетасовывает пул из активных цветов (анти-повтор на стыке). */
    private void refillPool(List<Character> active) {
        colorPool.clear();
        colorPool.addAll(active);
        Collections.shuffle(colorPool);

        // Первый цвет нового пула не должен совпадать с последним использованным
        if (colorPool.size() > 1 && colorPool.get(0) == lastUsed) {
            Collections.swap(colorPool, 0, 1);
        }
    }

    /** Берёт следующий цвет из пула; пополняет пул, когда он иссякает. */
    private char nextColor(List<Character> active) {
        if (colorPool.isEmpty()) {
            refillPool(active);
        }
        char c = colorPool.remove(0);
        lastUsed = c;
        return c;
    }

    /**
     * Подсчитывает, сколько символов займёт текст если красить каждый
     * non-space символ: каждый такой символ + "&X" = +2 символа.
     * Используется для авто-адаптации процента.
     */
    private int estimateLength(String text, double percent) {
        // Если 1 цвет — добавляем только 2 символа (&X) один раз в начало
        List<Character> active = getActiveColors();
        if (active.size() == 1) {
            return text.length() + 2;
        }
        int nonSpace = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != ' ') nonSpace++;
        }
        int colored = (int) Math.ceil(nonSpace * percent / 100.0);
        // каждый покрашенный символ добавляет 2 символа (&X)
        return text.length() + colored * 2;
    }

    /**
     * Ищет максимальный процент покраски, при котором текст влезает в лимит.
     * Если даже 0% не помогает (сам текст длиннее лимита) — возвращает 0.
     *
     * @param text   текст БЕЗ префиксного символа (уже отрезан)
     * @param maxLen лимит сервера минус длина префиксного символа (если есть)
     */
    private double calcOptimalPercent(String text, int maxLen) {
        if (text.length() > maxLen) return 0; // сам текст не влезает
        if (estimateLength(text, 100) <= maxLen) return 100; // 100% влезает

        // Бинарный поиск оптимального процента
        double lo = 0, hi = 100;
        for (int i = 0; i < 12; i++) {
            double mid = (lo + hi) / 2.0;
            if (estimateLength(text, mid) <= maxLen) lo = mid;
            else hi = mid;
            if (hi - lo < 0.5) break;
        }
        return lo;
    }

    /**
     * Красит текст с заданным процентом покраски.
     * percent=100 → каждый non-space символ получает цвет.
     * percent<100 → случайные позиции среди non-space символов.
     * Пробелы никогда не получают цветовой код.
     */
    private String colorize(String text, double percent) {
        List<Character> active = getActiveColors();
        if (active.isEmpty()) return text;

        // Если выбран только 1 цвет — ставим его один раз в начало
        if (active.size() == 1) {
            return "&" + active.get(0) + text;
        }

        // Свежий пул на каждое сообщение
        colorPool.clear();
        lastUsed = '\0';
        refillPool(active);

        // При проценте < 100 выбираем случайные позиции
        boolean fullColor = percent >= 99.9;
        java.util.Set<Integer> coloredPos = new java.util.HashSet<>();
        if (!fullColor) {
            List<Integer> nonSpacePos = new ArrayList<>();
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) != ' ') nonSpacePos.add(i);
            }
            Collections.shuffle(nonSpacePos);
            int count = (int) Math.ceil(nonSpacePos.size() * percent / 100.0);
            for (int i = 0; i < count && i < nonSpacePos.size(); i++) {
                coloredPos.add(nonSpacePos.get(i));
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ' ') {
                sb.append(ch);
            } else if (fullColor || coloredPos.contains(i)) {
                sb.append('&').append(nextColor(active)).append(ch);
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    // ─── Event ──────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onChat(ClientChatEvent event) {
        String msg = event.getMessage();
        if (msg == null || msg.isEmpty()) return;

        // Не трогаем команды и точечные/дефисные префиксы
        char first = msg.charAt(0);
        if (first == '/' || first == '.' || first == '-') return;

        // Отменяем стандартную отправку (она же сохраняет сообщение в историю)
        event.setCanceled(true);

        // В историю чата кладём оригинальный текст
        mc.ingameGUI.getChatGUI().addToSentMessages(msg);

        // Проверяем, есть ли префиксный символ (!@#$)
        String prefix = "";
        String body = msg;

        if (PREFIX_CHARS.indexOf(first) >= 0) {
            prefix = String.valueOf(first); // сохраняем ! @ # или $
            body = msg.substring(1);        // остаток — под раскраску
        }

        // Если после префикса ничего нет — отправляем как есть
        if (body.isEmpty()) {
            mc.player.sendChatMessage(msg);
            return;
        }

        // Авто-адаптация процента под лимит сервера.
        // Из лимита вычитаем длину префикса, чтобы он не мешал расчёту.
        int maxLen = getMaxLength() - prefix.length();
        double percent = calcOptimalPercent(body, maxLen);

        // Отправляем: префикс (без цвета) + покрашенный остаток
        mc.player.sendChatMessage(prefix + colorize(body, percent));
    }
}