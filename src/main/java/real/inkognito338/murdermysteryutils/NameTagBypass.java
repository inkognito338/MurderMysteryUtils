package real.inkognito338.murdermysteryutils;

import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.common.MinecraftForge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * NameTagHook — пытается "обойти" серверное скрытие nameplate (Team NameTagVisibility = NEVER)
 * путём рефлексивного принудительного выставления видимости nameTag'ов на клиенте.
 *
 * Регистрировать в Main: MinecraftForge.EVENT_BUS.register(new NameTagHook());
 */
public class NameTagBypass {

    private final Minecraft mc = Minecraft.getMinecraft();

    // Кешируем найденные рефлекс-поле/метод, чтобы не искать их каждый тик (опционально)
    private Field nameTagVisibilityField = null;
    private Method setNameTagVisibilityMethod = null;
    private Class<?> enumNameTagVisibilityClass = null;
    private Object enumAlwaysConstant = null;

    public NameTagBypass() {
        // Попытка инициализировать рефлексию заранее
        findReflectionTargets();
    }

    private void findReflectionTargets() {
        try {
            // Находим внутренний enum класса ScorePlayerTeam (обычно EnumNameTagVisibility)
            for (Class<?> inner : ScorePlayerTeam.class.getDeclaredClasses()) {
                if (inner.isEnum() && inner.getSimpleName().toLowerCase().contains("nametag")) {
                    enumNameTagVisibilityClass = inner;
                    break;
                }
            }
            // Если не нашли по имени, берем любой enum-внутренний как запасной вариант
            if (enumNameTagVisibilityClass == null) {
                for (Class<?> inner : ScorePlayerTeam.class.getDeclaredClasses()) {
                    if (inner.isEnum()) {
                        enumNameTagVisibilityClass = inner;
                        break;
                    }
                }
            }

            // Если нашли enum, найдем константу "ALWAYS"
            if (enumNameTagVisibilityClass != null) {
                Object[] consts = enumNameTagVisibilityClass.getEnumConstants();
                for (Object c : consts) {
                    if (c.toString().equalsIgnoreCase("always") || c.toString().equalsIgnoreCase("always")) {
                        enumAlwaysConstant = c;
                        break;
                    }
                }
                // как запасная попытка — берем первый константный элемент (меньше шансов на успех, но лучше чем ничего)
                if (enumAlwaysConstant == null && consts.length > 0) enumAlwaysConstant = consts[0];
            }

            // Попробуем найти поле, хранящее видимость (в разных маппингах имя может отличаться)
            String[] candidateNames = new String[] {
                    "nameTagVisibility",            // human-friendly
                    "field_178775_j",               // MCP/obf варианты
                    "field_179815_e",
                    "i",
                    "visibility",
                    "nameVisibility"
            };
            for (String n : candidateNames) {
                try {
                    Field f = ScorePlayerTeam.class.getDeclaredField(n);
                    f.setAccessible(true);
                    nameTagVisibilityField = f;
                    break;
                } catch (NoSuchFieldException ignored) {}
            }

            // Если поле не найдено, ищем сеттер вида setNameTagVisibility(String/Enum)
            for (Method m : ScorePlayerTeam.class.getDeclaredMethods()) {
                if (m.getName().toLowerCase().contains("set") &&
                        m.getParameterTypes().length == 1 &&
                        (m.getParameterTypes()[0] == String.class || (enumNameTagVisibilityClass != null && m.getParameterTypes()[0] == enumNameTagVisibilityClass))) {
                    m.setAccessible(true);
                    setNameTagVisibilityMethod = m;
                    break;
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
            // не фатально — будем пытаться каждый тик
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        // выполняем на END фейзе, чтобы изменения пакетов уже применились
        if (event.phase != TickEvent.Phase.END) return;

        if (mc.world == null) return;

        Scoreboard scoreboard = mc.world.getScoreboard();
        if (scoreboard == null) return;

        // если ранее рефлексия не инициализирована, пробуем заново
        if (nameTagVisibilityField == null && setNameTagVisibilityMethod == null) {
            findReflectionTargets();
        }

        for (ScorePlayerTeam team : scoreboard.getTeams()) {
            try {
                // 1) Если нашли enum и поле типа enum — записываем enum constant
                if (nameTagVisibilityField != null && enumAlwaysConstant != null && nameTagVisibilityField.getType().isEnum()) {
                    nameTagVisibilityField.set(team, enumAlwaysConstant);
                    continue;
                }

                // 2) Если поле найдено и это строка — записываем "always"
                if (nameTagVisibilityField != null && nameTagVisibilityField.getType() == String.class) {
                    nameTagVisibilityField.set(team, "always");
                    continue;
                }

                // 3) Если есть метод setNameTagVisibility, вызываем (с enum или с строкой)
                if (setNameTagVisibilityMethod != null) {
                    Class<?> param = setNameTagVisibilityMethod.getParameterTypes()[0];
                    if (param == String.class) {
                        setNameTagVisibilityMethod.invoke(team, "always");
                    } else if (enumNameTagVisibilityClass != null && param == enumNameTagVisibilityClass && enumAlwaysConstant != null) {
                        setNameTagVisibilityMethod.invoke(team, enumAlwaysConstant);
                    }
                    continue;
                }

                // 4) Последняя попытка — пробуем найти и установить известное поле напрямую (без заранее найденного имени)
                // Перебираем все declared fields и если найдем поле enum/string подходящего типа, ставим
                for (Field f : ScorePlayerTeam.class.getDeclaredFields()) {
                    f.setAccessible(true);
                    Class<?> t = f.getType();
                    if (t.isEnum() && enumAlwaysConstant != null) {
                        f.set(team, enumAlwaysConstant);
                        break;
                    } else if (t == String.class) {
                        f.set(team, "always");
                        break;
                    }
                }

            } catch (Throwable ex) {
                // не прерываем цикл — просто логируем ошибку
                ex.printStackTrace();
            }
        }
    }
}
