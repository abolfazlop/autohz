package com.abolfazl.hzswitcher;

import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Shared clipboard capture pipeline used by both classic and modern LSPosed APIs. */
public final class ClipCapture {
    private static final Uri BRIDGE = Uri.parse("content://com.abolfazl.hzswitcher.clipboard/clips");
    private static volatile Context cachedSystemContext;
    private static final ExecutorService DELIVERY = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PulseXt-clipboard-delivery");
        t.setDaemon(true);
        return t;
    });

    private ClipCapture() {}

    /** Find every setPrimaryClip overload on ClipboardService and its nested classes. */
    public static List<Method> findSetPrimaryClipMethods(ClassLoader loader, Consumer<String> log) {
        Class<?> service;
        try {
            service = Class.forName("com.android.server.clipboard.ClipboardService", false, loader);
        } catch (Throwable t) {
            log.accept("ClipboardService class not found: " + t);
            return new ArrayList<>();
        }
        List<Class<?>> candidates = new ArrayList<>();
        candidates.add(service);
        candidates.addAll(Arrays.asList(service.getDeclaredClasses()));
        Set<String> seen = new HashSet<>();
        List<Method> methods = new ArrayList<>();
        for (Class<?> type : candidates) {
            for (Method method : type.getDeclaredMethods()) {
                if (!"setPrimaryClip".equals(method.getName())) continue;
                String signature = type.getName() + "#" + method.getName()
                    + Arrays.toString(method.getParameterTypes());
                if (seen.add(signature)) {
                    try {
                        method.setAccessible(true);
                        methods.add(method);
                    } catch (Throwable t) {
                        log.accept("cannot access " + method + ": " + t);
                    }
                }
            }
        }
        return methods;
    }

    private static Context resolveSystemContext(Consumer<String> log) {
        Context result = cachedSystemContext;
        if (result != null) return result;
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object thread = activityThread.getMethod("currentActivityThread").invoke(null);
            result = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
            cachedSystemContext = result;
            return result;
        } catch (Throwable t) {
            log.accept("could not resolve system context: " + t);
            return null;
        }
    }

    /** Extract only safe text/URI values and forward asynchronously in observation order. */
    public static void captureAndForward(Object[] args, Consumer<String> log) {
        try {
            ClipData clip = null;
            for (Object arg : args) if (arg instanceof ClipData) { clip = (ClipData) arg; break; }
            if (clip == null || clip.getItemCount() <= 0) return;
            ClipData.Item item = clip.getItemAt(0);
            CharSequence sequence = item.getText();
            String text = sequence != null ? sequence.toString()
                : item.getUri() != null ? item.getUri().toString() : null;
            if (text == null || text.trim().isEmpty()) return;
            long capturedAt = System.currentTimeMillis();
            Context context = resolveSystemContext(log);
            if (context == null) return;
            final String capturedText = text;
            final Context systemContext = context;
            DELIVERY.execute(() -> {
                try {
                    ContentValues values = new ContentValues();
                    values.put("content", capturedText);
                    values.put("captured_at", capturedAt);
                    systemContext.getContentResolver().insert(BRIDGE, values);
                } catch (Throwable t) {
                    log.accept("failed to forward clip to app: " + t);
                }
            });
        } catch (Throwable t) {
            log.accept("error handling clipboard change: " + t);
        }
    }
}