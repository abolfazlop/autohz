package com.abolfazl.hzswitcher;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Single source of truth for clipboard history.
 * The LSPosed hook writes through ClipboardProvider into this app-private file;
 * the UI reads and updates the same file when opened.
 */
public final class ClipboardStore {
    public static final String FILE_NAME = "clipboard_history.json";
    private static final Object LOCK = new Object();

    private ClipboardStore() {}

    public static JSONArray read(Context context) {
        synchronized (LOCK) {
            File file = new File(context.getFilesDir(), FILE_NAME);
            if (!file.isFile()) return new JSONArray();
            try (FileInputStream in = new FileInputStream(file);
                 BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder data = new StringBuilder();
                char[] buffer = new char[8192];
                int count;
                while ((count = reader.read(buffer)) != -1) data.append(buffer, 0, count);
                return new JSONArray(data.toString());
            } catch (Exception error) {
                return new JSONArray();
            }
        }
    }

    public static boolean write(Context context, JSONArray entries) {
        synchronized (LOCK) {
            File target = new File(context.getFilesDir(), FILE_NAME);
            File temporary = new File(context.getFilesDir(), FILE_NAME + ".tmp");
            try (FileOutputStream out = new FileOutputStream(temporary, false)) {
                out.write(entries.toString().getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.getFD().sync();
            } catch (Exception error) {
                temporary.delete();
                return false;
            }
            if (target.exists() && !target.delete()) {
                temporary.delete();
                return false;
            }
            if (!temporary.renameTo(target)) {
                temporary.delete();
                return false;
            }
            return true;
        }
    }
}