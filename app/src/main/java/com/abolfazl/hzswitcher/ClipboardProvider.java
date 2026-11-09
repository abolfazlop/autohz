package com.abolfazl.hzswitcher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Process;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;

/** LSPosed system_server -> app file bridge. Only system/root callers may write. */
public final class ClipboardProvider extends ContentProvider {
    private static final String TAG = "PulseXt ClipboardProvider";

    @Override public boolean onCreate() { return true; }

    @Override public Uri insert(Uri uri, ContentValues values) {
        int uid = Binder.getCallingUid();
        if (uid != Process.SYSTEM_UID && uid != Process.ROOT_UID) {
            Log.w(TAG, "Rejected clipboard writer uid=" + uid);
            return null;
        }
        if (getContext() == null || values == null) return null;
        String text = values.getAsString("content");
        if (text == null || text.trim().isEmpty()) return null;

        synchronized (ClipboardStore.class) {
            try {
                android.content.SharedPreferences prefs =
                    getContext().getSharedPreferences("pulsext_clipboard", 0);
                if (prefs.getBoolean("private_mode", false)) return null;

                Long capturedAt = values.getAsLong("captured_at");
                long timestamp = capturedAt == null
                    ? System.currentTimeMillis() : capturedAt;
                int limit = prefs.getInt("history_limit", 200);

                // Append directly to SQLite in one transaction. Do not load a
                // JSON snapshot, modify it in memory, or rewrite the full list.
                if (!ClipboardStore.append(getContext(), text, timestamp, limit)) {
                    Log.e(TAG, "Atomic SQLite append failed");
                    return null;
                }

                Uri changed = Uri.parse("content://com.abolfazl.hzswitcher.clipboard/clips");
                Log.i(TAG, "Appended clipboard event to SQLite database");
                getContext().getContentResolver().notifyChange(changed, null);
                return changed;
            } catch (Throwable error) {
                Log.e(TAG, "Could not append clipboard history", error);
                return null;
            }
        }
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.pulsext.clip"; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection,
                                String[] selectionArgs) { return 0; }
}