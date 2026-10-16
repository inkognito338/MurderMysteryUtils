package real.inkognito338.murdermysteryutils.utils;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;
import real.inkognito338.murdermysteryutils.commands.CommandManager;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

public class BindManager {

    // ── Синглтон ──────────────────────────────────────────────────
    private static final BindManager INSTANCE = new BindManager();
    public static BindManager getInstance() { return INSTANCE; }

    // ── Поля ──────────────────────────────────────────────────────
    private final Minecraft mc = Minecraft.getMinecraft();

    /** keyCode → список действий (.t fly -s  или  обычное сообщение) */
    private final Map<Integer, List<String>> binds = new LinkedHashMap<>();

    private File bindsFile;
    private boolean initialized = false;

    private BindManager() {}

    // ── Инициализация (вызвать из главного класса мода) ───────────

    /**
     * Вызвать один раз при старте мода:
     *   BindManager.getInstance().init(event.getModConfigurationDirectory());
     */
    public void init(File mcDataDir) {
        if (initialized) return;
        initialized = true;

        bindsFile = new File(mcDataDir, "binds.json");
        load();
        MinecraftForge.EVENT_BUS.register(this);
    }

    // ── Обработка клавиш ──────────────────────────────────────────

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        // Не срабатывать в GUI (чат, инвентарь и т.д.)
        if (mc.currentScreen != null) return;
        if (!Keyboard.getEventKeyState()) return;          // только нажатие

        int key = Keyboard.getEventKey();
        if (key == Keyboard.KEY_NONE) return;

        List<String> actions = binds.get(key);
        if (actions == null || actions.isEmpty()) return;

        // Выполняем все действия, привязанные к клавише
        for (String action : actions) {
            execute(action);
        }
    }

    /**
     * Выполняет привязанное действие:
     *  - если начинается с "." → команда MurderMysteryUtils
     *  - иначе → отправка сообщения в чат
     */
    private void execute(String action) {
        if (mc.player == null || mc.world == null) return;

        if (action.startsWith(".")) {
            // Команда — делегируем в CommandManager
            CommandManager.getInstance().execute(action);
        } else {
            // Обычное сообщение в чат
            mc.player.sendChatMessage(action);
        }
    }

    // ── CRUD ──────────────────────────────────────────────────────

    /**
     * Добавляет бинд. На одну клавишу можно повесить сколько угодно действий.
     * Если такое же действие уже есть — не дублируем.
     */
    public void addBind(int keyCode, String action) {
        List<String> list = binds.computeIfAbsent(keyCode, k -> new ArrayList<>());
        if (!list.contains(action)) {
            list.add(action);
            save();
        }
    }

    /**
     * Удаляет конкретное действие с клавиши.
     * @return true если действие было и удалено
     */
    public boolean removeBind(int keyCode, String action) {
        List<String> list = binds.get(keyCode);
        if (list == null) return false;
        boolean removed = list.remove(action);
        if (removed) {
            if (list.isEmpty()) binds.remove(keyCode);
            save();
        }
        return removed;
    }

    /**
     * Удаляет все бинды с клавиши.
     * @return true если что-то было удалено
     */
    public boolean removeAllBinds(int keyCode) {
        boolean had = binds.remove(keyCode) != null;
        if (had) save();
        return had;
    }

    public Map<Integer, List<String>> getBinds() {
        return Collections.unmodifiableMap(binds);
    }

    // ── Сохранение / загрузка ─────────────────────────────────────

    private void save() {
        try {
            File parent = bindsFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                System.err.println("[MurderMysteryUtils] Failed to create config directory: " + parent);
                return;
            }
            JsonObject obj = new JsonObject();
            for (Map.Entry<Integer, List<String>> e : binds.entrySet()) {
                JsonArray arr = new JsonArray();
                for (String action : e.getValue()) {
                    arr.add(action);
                }
                obj.add(Keyboard.getKeyName(e.getKey()), arr);
            }
            try (FileWriter fw = new FileWriter(bindsFile)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(obj, fw);
            }
        } catch (Exception e) {
            System.err.println("[MurderMysteryUtils] Binds save failed: " + e.getMessage());
        }
    }

    private void load() {
        if (!bindsFile.exists()) return;
        try {
            String raw = new String(Files.readAllBytes(bindsFile.toPath()));
            JsonObject obj = new Gson().fromJson(raw, JsonObject.class);
            if (obj == null) return;

            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                int code = Keyboard.getKeyIndex(e.getKey().toUpperCase());
                if (code == Keyboard.KEY_NONE) continue;

                List<String> list = new ArrayList<>();
                JsonElement value = e.getValue();

                // Поддержка нового формата (массив)
                if (value.isJsonArray()) {
                    for (JsonElement el : value.getAsJsonArray()) {
                        list.add(el.getAsString());
                    }
                }
                // Поддержка старого формата (одна строка) — для совместимости
                else if (value.isJsonPrimitive()) {
                    list.add(value.getAsString());
                }

                if (!list.isEmpty()) {
                    binds.put(code, list);
                }
            }
            System.out.println("[MurderMysteryUtils] Binds loaded: " + binds.size());
        } catch (Exception e) {
            System.err.println("[MurderMysteryUtils] Binds load failed: " + e.getMessage());
        }
    }
}