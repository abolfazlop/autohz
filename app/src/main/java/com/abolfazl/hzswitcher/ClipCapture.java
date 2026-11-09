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

/** Shared implementation copied from ClipVault's system_server capture pipeline. */
public final class ClipCapture {
    private static final Uri CONTENT_URI =
        Uri.parse("content://com.abolfazl.hzswitcher.clipboard/clips");
    private static volatile Context cachedSystemContext;

    // A single worker preserves clipboard-write order across the provider IPC.
    private static final ExecutorService DELIVERY = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "PulseXt-clipboard-delivery");
        thread.setDaemon(true);
        return thread;
    });

    private ClipCapture() {}

    /** Find every setPrimaryClip overload on ClipboardService and nested classes. */
    public static List<Method> findSetPrimaryClipMethods(ClassLoader loader,
                                                          Consumer<String> logError) {
        Class<?> serviceClass;
        try {
            serviceClass = Class.forName(
                "com.android.server.clipboard.ClipboardService", false, loader);
        } catch (Throwable error) {
            logError.accept("ClipboardService class not found: " + error);
            return new ArrayList<>();
        }

        List<Class<?>> candidates = new ArrayList<>();
        candidates.add(serviceClass);
        candidates.addAll(Arrays.asList(serviceClass.getDeclaredClasses()));

        Set<String> seen = new HashSet<>();
        List<Method> methods = new ArrayList<>();
        for (Class<?> candidate : candidates) {
            for (Method method : candidate.getDeclaredMethods()) {
                if (!"setPrimaryClip".equals(method.getName())) continue;
                String signature = candidate.getName() + "#" + method.getName()
                    + Arrays.toString(method.getParameterTypes());
                if (!seen.add(signature)) continue;
                try {
                    method.setAccessible(true);
                    methods.add(method);
                } catch (Throwable error) {
                    logError.accept("Could not access " + method + ": " + error);
                }
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
            Context context = (Context) activityThread.getMethod("getSystemContext")
                .invoke(current);
            cachedSystemContext = context;
            return context;
        } catch (Throwable error) {
            logError.accept("Could not resolve system context: " + error);
            return null;
        }
    }

    /** Extract text/URI and enqueue provider delivery without blocking system_server. */
    public static void captureAndForward(Object[] args, Consumer<String> logError) {
        try {
            ClipData clip = null;
            for (Object arg : args) {
                if (arg instanceof ClipData) {
                    clip = (ClipData) arg;
                    break;
                }
            }
            if (clip == null || clip.getItemCount() <= 0) return;

            ClipData.Item item = clip.getItemAt(0);
            String text = item.getText() == null ? null : item.getText().toString();
            if (text == null || text.trim().isEmpty()) {
                text = item.getUri() == null ? null : item.getUri().toString();
            }
            if (text == null || text.trim().isEmpty()) return;

            long capturedAt = System.currentTimeMillis();
            Context systemContext = resolveSystemContext(logError);
            if (systemContext == null) return;

            final String capturedText = text;
            final Context context = systemContext;
            DELIVERY.execute(() -> {
                try {
                    ContentValues values = new ContentValues();
                    values.put("content", capturedText);
                    values.put("captured_at", capturedAt);
                    context.getContentResolver().insert(CONTENT_URI, values);
                } catch (Throwable error) {
                    logError.accept("Failed to forward clipboard to PulseXt: " + error);
                }
            });
        } catch (Throwable error) {
            logError.accept("Error handling clipboard change: " + error);
        }
    }
}