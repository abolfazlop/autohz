package com.abolfazl.hzswitcher;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import java.lang.reflect.Method;
import java.util.List;

/** Classic LSPosed entry point. The modern API entry point shares ClipCapture. */
public final class ClipboardHook implements IXposedHookLoadPackage {
    private static final String TAG = "PulseXt Clipboard";

    private void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    @Override
    public void handleLoadPackage(LoadPackageParam param) {
        if ("com.abolfazl.hzswitcher".equals(param.packageName)) {
            hookSelfStatus(param);
            return;
        }
        if (!"android".equals(param.packageName)) return;

        try {
            List<Method> methods = ClipCapture.findSetPrimaryClipMethods(param.classLoader, this::log);
            if (methods.isEmpty()) {
                log("No setPrimaryClip overloads found; hook aborted");
                return;
            }
            XC_MethodHook callback = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam hookParam) {
                    // Never record a clipboard operation that failed.
                    if (hookParam.hasThrowable()) return;
                    ClipCapture.captureAndForward(hookParam.args, ClipboardHook.this::log);
                }
            };
            int installed = 0;
            for (Method method : methods) {
                try {
                    XposedBridge.hookMethod(method, callback);
                    installed++;
                } catch (Throwable error) {
                    log("Could not hook " + method + ": " + error);
                }
            }
            log("Installed clipboard hook on " + installed + " overload(s)");
        } catch (Throwable error) {
            log("Failed to install clipboard hook: " + error);
        }
    }

    private void hookSelfStatus(LoadPackageParam param) {
        try {
            HookStatus.moduleHooked = true;
            XposedHelpers.findAndHookMethod(
                HookStatus.class.getName(),
                param.classLoader,
                "isActive",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam hookParam) {
                        hookParam.setResult(true);
                    }
                }
            );
        } catch (Throwable error) {
            log("Could not install self-status marker: " + error);
        }
    }
}