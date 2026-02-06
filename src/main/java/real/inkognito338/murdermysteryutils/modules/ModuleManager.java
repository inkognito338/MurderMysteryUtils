package real.inkognito338.murdermysteryutils.modules;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private static final List<Module> modules = new ArrayList<>();

    public static void addModule(Module module) {
        modules.add(module);
    }

    public static List<Module> getModules() {
        return modules;
    }

    public static Module getModuleByName(String name) {
        for (Module m : modules) {
            if (m.getName().equalsIgnoreCase(name)) {
                return m;
            }
        }
        return null;
    }

    public static Module getModuleByClass(Class<?> clazz) {
        for (Module m : modules) {
            if (m.getClass() == clazz) {
                return m;
            }
        }
        return null;
    }
}