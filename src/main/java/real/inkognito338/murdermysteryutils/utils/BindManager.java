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

    /** keyCode → строка-действие (.t fly -s  или  обычное сообщение) */
    private final Map<Integer, String> binds = new LinkedHashMap<>();

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

        String action = binds.get(key);
        if (action == null) return;

        execute(action);
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

    public void addBind(int keyCode, String action) {
        binds.put(keyCode, action);
        save();
    }

    /** @return true если бинд был и удалён */
    public boolean removeBind(int keyCode) {
        boolean had = binds.remove(keyCode) != null;
        if (had) save();
        return had;
    }

    public Map<Integer, String> getBinds() {
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
            for (Map.Entry<Integer, String> e : binds.entrySet()) {
                obj.addProperty(Keyboard.getKeyName(e.getKey()), e.getValue());
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
                if (code != Keyboard.KEY_NONE) {
                    binds.put(code, e.getValue().getAsString());
                }
            }
            System.out.println("[MurderMysteryUtils] Binds loaded: " + binds.size());
        } catch (Exception e) {
            System.err.println("[MurderMysteryUtils] Binds load failed: " + e.getMessage());
        }
    }
}