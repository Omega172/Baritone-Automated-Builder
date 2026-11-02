package xyz.omegaware.builder.core;

import net.fabricmc.loader.api.FabricLoader;
import xyz.omegaware.builder.util.Log;

import java.lang.reflect.Method;

public final class MeteorInterop {
    private static final String s_szModulesClass = "meteordevelopment.meteorclient.systems.modules.Modules";
    private static final String s_szPrinterName = "printer";

    private static boolean s_bActivatedByUs = false;

    private MeteorInterop() {}

    public static boolean hasMeteorPrinter() {
        return module() != null;
    }

    private static Object module() {
        if (!FabricLoader.getInstance().isModLoaded("meteor-client")) return null;
        if (!FabricLoader.getInstance().isModLoaded("meteor-litematica-printer")) return null;
        try {
            Class<?> modules = Class.forName(s_szModulesClass);
            Object instance = modules.getMethod("get").invoke(null);
            return modules.getMethod("get", String.class).invoke(instance, s_szPrinterName);
        } catch (ReflectiveOperationException | LinkageError e) {
            Log.LOGGER.warn("Meteor printer lookup failed", e);
            return null;
        }
    }

    public static boolean isPrinterActive() {
        Object module = module();
        if (module == null) return false;
        try {
            return (boolean) module.getClass().getMethod("isActive").invoke(module);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    public static void setPrinterActive(boolean bActive) {
        Object module = module();
        if (module == null) return;
        try {
            Method isActive = module.getClass().getMethod("isActive");
            boolean bCurrent = (boolean) isActive.invoke(module);
            if (bCurrent == bActive) return;
            if (!bActive && !s_bActivatedByUs) return;
            module.getClass().getMethod("toggle").invoke(module);
            s_bActivatedByUs = bActive;
        } catch (ReflectiveOperationException e) {
            Log.LOGGER.warn("Meteor printer toggle failed", e);
        }
    }

    public static void release() {
        if (s_bActivatedByUs) setPrinterActive(false);
    }
}
