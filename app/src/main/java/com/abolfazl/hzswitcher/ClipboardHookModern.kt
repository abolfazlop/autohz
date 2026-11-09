package com.abolfazl.hzswitcher;

import android.util.Log;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;

private const val TAG = "PulseXt Clipboard (modern)"

class ClipboardHookModern : XposedModule() {
    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        super.onSystemServerStarting(param)
        Log.i(TAG, "system_server callback reached; classLoader=${param.classLoader}")
        try {
            val methods = ClipCapture.findSetPrimaryClipMethods(param.classLoader) {
                Log.w(TAG, it)
            }
            if (methods.isEmpty()) {
                Log.w(TAG, "No ClipboardService#setPrimaryClip overloads found; capture hook not installed")
                return
            }
            Log.i(TAG, "Found ${methods.size} setPrimaryClip target(s)")
            methods.forEach { method ->
                try {
                    hook(method)
                        .setPriority(XposedInterface.PRIORITY_DEFAULT)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept { chain ->
                            val result = chain.proceed()
                            ClipCapture.captureAndForward(chain.args.toTypedArray()) { message ->
                                Log.w(TAG, message)
                            }
                            result
                        }
                } catch (t: Throwable) {
                    Log.w(TAG, "Could not hook $method", t)
                }
            }
            Log.i(TAG, "Installed clipboard hook on ${methods.size} overload(s)")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to install system_server clipboard hook", t)
        }
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)
        if (param.packageName == "com.abolfazl.hzswitcher") {
            try {
                val status = param.defaultClassLoader.loadClass("com.abolfazl.hzswitcher.HookStatus")
                status.getField("moduleHooked").setBoolean(null, true)
                val method = status.getDeclaredMethod("isActive")
                hook(method)
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .intercept { true }
            } catch (t: Throwable) {
                Log.w(TAG, "Could not install module status marker", t)
            }
        }
    }
}