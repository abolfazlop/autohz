package com.abolfazl.hzswitcher;

import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Java port of ClipVault's proven LSPosed capture path.
 * Shared by the classic XposedBridge and modern libxposed entry points.
 */
public final class ClipCapture {
    private static final Uri CONTENT_URI =
        Uri.parse("content://com.abolfazl.hzswitcher.clipboard/clips");

    private ClipCapture() {}

    /**
     * Match ClipVault exactly: inspect ClipboardService itself and its direct
     * declared nested classes for every setPrimaryClip overload.
     */
    public static List<Method> findSetPrimaryClipMethods(
            ClassLoader loader, Consumer<String> logError) {
        final Class<?> serviceClass;
        try {
            serviceClass = Class.forName(
                "com.android.server.clipboard.ClipboardService", false, loader);
        } catch (Throwable error) {
            logError.accept("ClipboardService class not found: " + error);
            return new ArrayList<>();
        }

        List<Class<?>> candidates = new ArrayList<>();
        candidates.add(serviceClass);
        try {
            for (Class<?> nested : serviceClass.getDeclaredClasses()) {
                candidates.add(nested);
            }
        } catch (Throwable error) {
            logError.accept("Could not inspect ClipboardService nested classes: " + error);
        }

        Set<String> seen = new HashSet<>();
        List<Method> methods = new ArrayList<>();
        for (Class<?> candidate : candidates) {
            try {
                for (Method method : candidate.getDeclaredMethods()) {
                    if (!"setPrimaryClip".equals(method.getName())) continue;
                    StringBuilder signature = new StringBuilder(candidate.getName())
                        .append("#").append(method.getName()).append("(");
                    Class<?>[] types = method.getParameterTypes();
                    for (int i = 0; i < types.length; i++) {
                        if (i > 0) signature.append(",");
                        signature.append(types[i].getName());
                    }
                    signature.append(")");
                    if (!seen.add(signature.toString())) continue;
                    try {
                        method.setAccessible(true);
                        methods.add(method);
                        logError.accept("Found clipboard target: " + signature);
                    } catch (Throwable error) {
                        logError.accept("Could not access " + signature + ": " + error);
                    }
                }
            } catch (Throwable error) {
                logError.accept("Could not inspect " + candidate.getName() + ": " + error);
            }
        }
        return methods;
    }

    private static volatile Context cachedSystemContext;

    /** Same ActivityThread -> getSystemContext resolution used by ClipVault. */
    public static Context resolveSystemContext(Consumer<String> logError) {
        Context cached = cachedSystemContext;
        if (cached != null) return cached;
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object current = activityThread.getMethod("currentActivityThread").invoke(null);
            if (current == null) {
                logError.accept("ActivityThread.currentActivityThread() returned null");
                return null;
            }
            Context context = (Context) activityThread
                .getMethod("getSystemContext").invoke(current);
            cachedSystemContext = context;
            return context;
        } catch (Throwable error) {
            logError.accept("Could not resolve system context: " + error);
            return null;
        }
    }

    /**
     * ClipVault-compatible extraction: first item's text, then URI string.
     * Do not coerce ContentProvider streams on the system_server call thread.
     */
    public static void captureAndForward(Object[] args, Consumer<String> logError) {
        try {
            ClipData clipData = null;
            if (args != null) {
                for (Object arg : args) {
                    if (arg instanceof ClipData) {
                        clipData = (ClipData) arg;
                        break;
                    }
                }
            }
            if (clipData == null || clipData.getItemCount() <= 0) return;

            ClipData.Item item = clipData.getItemAt(0);
            String text = item.getText() == null ? null : item.getText().toString();
            if (text == null || text.trim().isEmpty()) {
                text = item.getUri() == null ? null : item.getUri().toString();
            }
            if (text == null || text.trim().isEmpty()) return;

            long capturedAt = System.currentTimeMillis();
            forwardToApp(text, capturedAt, logError);
        } catch (Throwable error) {
            logError.accept("Error handling clipboard change: " + error);
        }
    }

    /** A single worker preserves clipboard event ordering exactly as in ClipVault. */
    private static final ExecutorService DELIVERY =
        Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "PulseXt-clipboard-delivery");
            thread.setDaemon(true);
            return thread;
        });

    private static void forwardToApp(String text, long capturedAt,
                                     Consumer<String> logError) {
        Context context = resolveSystemContext(logError);
        if (context == null) return;
        DELIVERY.execute(() -> {
            try {
                ContentValues values = new ContentValues();
                values.put("content", text);
                values.put("captured_at", capturedAt);
                context.getContentResolver().insert(CONTENT_URI, values);
            } catch (Throwable error) {
                logError.accept("Failed to forward clipboard to app: " + error);
            }
        });
    }
}