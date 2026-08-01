package real.inkognito338.murdermysteryutils.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

public class ModuleManager {
    private static final List<Module> modules = new ArrayList<>();
    private static final ConcurrentHashMap<String, Module> moduleMap = new ConcurrentHashMap<>();

    public static void addModule(Module module) {
        modules.add(module);
        moduleMap.put(module.getName().toLowerCase(), module);
    }

    public static List<Module> getModules() {
        return new ArrayList<>(modules);
    }

    public static Module getModuleByName(String name) {
        return moduleMap.get(name.toLowerCase());
    }

    public static void setEnabled(String name, boolean enabled) {
        Module module = getModuleByName(name);
        if (module != null) {
            module.setToggled(enabled);
        }
    }

    public static void setRestricted(String name, boolean restricted) {
        Module module = getModuleByName(name);
        if (module != null) {
            module.setRestricted(restricted);
        }
    }

    public static boolean isRestricted(String name) {
        Module module = getModuleByName(name);
        return module != null && module.isRestricted();
    }

    public static boolean isToggled(String name) {
        Module module = getModuleByName(name);
        return module != null && module.isToggled();
    }
}