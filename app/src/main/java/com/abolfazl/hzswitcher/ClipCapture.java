package com.abolfazl.hzswitcher;

import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** System-server clipboard capture and ordered delivery to PulseXt. */
public final class ClipCapture {
    private static final String TAG = "PulseXt ClipCapture";
    private static final Uri CONTENT_URI =
        Uri.parse("content://com.abolfazl.hzswitcher.clipboard/clips");
    private static volatile Context cachedSystemContext;
    private static final ExecutorService DELIVERY = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "PulseXt-clipboard-delivery");
        thread.setDaemon(true);
        return thread;
    });

    private ClipCapture() {}

    /** Search service implementation classes recursively for every clipboard setter. */
    public static List<Method> findSetPrimaryClipMethods(ClassLoader loader,
                                                          Consumer<String> logError) {
        Class<?> serviceClass = null;
        String[] names = {
            "com.android.server.clipboard.ClipboardService",
            "com.android.server.ClipboardService"
        };
        for (String name : names) {
            try {
                serviceClass = Class.forName(name, false, loader);
                break;
            } catch (Throwable ignored) { }
        }
        if (serviceClass == null) {
            logError.accept("ClipboardService not found; loader=" + String.valueOf(loader));
            return new ArrayList<>();
        }

        ArrayDeque<Class<?>> queue = new ArrayDeque<>();
        Set<Class<?>> visited = new HashSet<>();
        queue.add(serviceClass);
        while (!queue.isEmpty()) {
            Class<?> type = queue.removeFirst();
            if (!visited.add(type)) continue;
            try {
                for (Class<?> nested : type.getDeclaredClasses()) queue.addLast(nested);
            } catch (Throwable error) {
                logError.accept("Cannot inspect nested classes of " + type.getName() + ": " + error);
            }
            Class<?> parent = type.getSuperclass();
            if (parent != null && parent != Object.class) queue.addLast(parent);
        }

        Set<String> seen = new HashSet<>();
        List<Method> methods = new ArrayList<>();
        for (Class<?> type : visited) {
            try {
                for (Method method : type.getDeclaredMethods()) {
                    if (!"setPrimaryClip".equals(method.getName())) continue;
                    String signature = type.getName() + "#" + method.getName()
                        + Arrays.toString(method.getParameterTypes());
                    if (!seen.add(signature)) continue;
                    try {
                        method.setAccessible(true);
                        methods.add(method);
                        logError.accept("Found clipboard target: " + signature);
                    } catch (Throwable error) {
                        logError.accept("Cannot access " + signature + ": " + error);
                    }
                }
            } catch (Throwable error) {
                logError.accept("Cannot inspect " + type.getName() + ": " + error);
            }
        }
        return methods;
    }

    private static Context resolveSystemContext(Consumer<String> logError) {
        Context cached = cachedSystemContext;
        if (cached != null) return cached;
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object current = activityThread.getMethod("currentActivityThread").invoke(null);
            if (current == null) current = activityThread.getMethod("systemMain").invoke(null);
            if (current == null) {
                logError.accept("ActivityThread instance is null in system_server");
                return null;
            }
            Context context = (Context) activityThread.getMethod("getSystemContext").invoke(current);
            if (context == null) {
                logError.accept("ActivityThread returned a null system context");
                return null;
            }
            cachedSystemContext = context;
            return context;
        } catch (Throwable error) {
            logError.accept("Could not resolve system context: " + error);
            return null;
        }
    }

    /** Extract plain text/URI and enqueue delivery without blocking system_server. */
    public static void captureAndForward(Object[] args, Consumer<String> logError) {
        try {
            ClipData clip = null;
            if (args != null) for (Object arg : args) {
                if (arg instanceof ClipData) { clip = (ClipData) arg; break; }
            }
            if (clip == null || clip.getItemCount() <= 0) {
                logError.accept("Hook fired but no ClipData argument was present");
                return;
            }
            ClipData.Item item = clip.getItemAt(0);
            String text = item.getText() == null ? null : item.getText().toString();
            if (text == null || text.trim().isEmpty())
                text = item.getUri() == null ? null : item.getUri().toString();
            if (text == null || text.trim().isEmpty()) {
                logError.accept("Clipboard item has no plain text or URI");
                return;
            }

            long capturedAt = System.currentTimeMillis();
            Context context = resolveSystemContext(logError);
            if (context == null) return;
            final String capturedText = text;
            final Context systemContext = context;
            DELIVERY.execute(() -> {
                try {
                    ContentValues values = new ContentValues();
                    values.put("content", capturedText);
                    values.put("captured_at", capturedAt);
                    Uri result = systemContext.getContentResolver().insert(CONTENT_URI, values);
                    if (result == null) logError.accept("Clipboard provider returned null");
                } catch (Throwable error) {
                    logError.accept("Provider IPC failed: " + error);
                    Log.e(TAG, "Failed to forward clipboard", error);
                }
            });
        } catch (Throwable error) {
            logError.accept("Error handling clipboard change: " + error);
        }
    }
}