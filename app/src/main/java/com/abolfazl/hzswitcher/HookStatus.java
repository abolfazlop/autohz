package com.abolfazl.hzswitcher;

/**
 * Reports whether this app process has actually been injected by an
 * Xposed-compatible framework. The module's own entry points additionally
 * hook this method and force a positive result once they are loaded.
 */
public final class HookStatus {
    private HookStatus() {}

    /** Runtime marker set by the LSPosed entry point when available. */
    public static volatile boolean moduleHooked = false;

    public static boolean isActive() {
        if (moduleHooked) return true;

        // These APIs are compileOnly: their presence at runtime means an
        // Xposed/LSPosed framework has injected its bridge into this process.
        ClassLoader loader = HookStatus.class.getClassLoader();
        if (isPresent("de.robv.android.xposed.XposedBridge", loader)) return true;
        if (isPresent("io.github.libxposed.api.XposedModule", loader)) return true;
        return false;
    }

    private static boolean isPresent(String name, ClassLoader loader) {
        try {
            Class.forName(name, false, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
