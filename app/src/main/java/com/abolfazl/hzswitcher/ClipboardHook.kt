package com.abolfazl.hzswitcher

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

/** Classic LSPosed entry point, loaded from assets/xposed_init. */
class ClipboardHook : IXposedHookLoadPackage {
    private fun log(message: String) = XposedBridge.log("PulseXt clipboard (classic): $message")

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName == "com.abolfazl.hzswitcher" ||
            lpparam.packageName == "com.abolfazl.hzswitcher.debug") {
            hookSelfStatusMarker(lpparam)
            return
        }
        if (lpparam.packageName != "android") return

        try {
            val methods = ClipCapture.findSetPrimaryClipMethods(lpparam.classLoader) { message ->
                log(message)
            }
            if (methods.isEmpty()) {
                log("No setPrimaryClip overloads found; hook aborted")
                return
            }
            var installed = 0
            methods.forEach { method ->
                try {
                    XposedBridge.hookMethod(method, afterHook)
                    installed++
                } catch (t: Throwable) {
                    log("Could not hook $method: $t")
                }
            }
            log("Clipboard hook installed on " + installed + "/" + methods.size + " overload(s)")
        } catch (t: Throwable) {
            log("Failed to install clipboard hook: $t")
        }
    }

    private fun hookSelfStatusMarker(lpparam: LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.abolfazl.hzswitcher.HookStatus",
                lpparam.classLoader,
                "isActive",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = true
                    }
                }
            )
        } catch (t: Throwable) {
            log("Could not install self-status marker: $t")
        }
    }

    private val afterHook = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            if (param.throwable != null) return
            try {
                ClipCapture.captureAndForward(param.args) { message -> log(message) }
            } catch (t: Throwable) {
                log("Capture callback failed safely: $t")
            }
        }
    }
}
