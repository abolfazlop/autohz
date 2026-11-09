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

                JSONArray entries = ClipboardStore.read(getContext());
                int existing = -1;
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject item = entries.optJSONObject(i);
                    if (item != null && text.equals(item.optString("text"))) {
                        existing = i;
                        break;
                    }
                }

                JSONObject current;
                if (existing >= 0) {
                    current = entries.getJSONObject(existing);
                    entries.remove(existing);
                    Long capturedAt = values.getAsLong("captured_at");
                    current.put("time", capturedAt == null
                        ? System.currentTimeMillis() : capturedAt);
                } else {
                    current = new JSONObject();
                    current.put("id", UUID.randomUUID().toString());
                    current.put("text", text);
                    Long capturedAt = values.getAsLong("captured_at");
                    current.put("time", capturedAt == null
                        ? System.currentTimeMillis() : capturedAt);
                    current.put("favorite", false);
                }
                current.put("active", true);
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject item = entries.optJSONObject(i);
                    if (item != null) item.put("active", false);
                }
                // Most recently copied clip is always first. Distinct clips
                // remain separate; copying an older clip promotes that item.
                entries.put(0, current);

                int limit = prefs.getInt("history_limit", 200);
                if (limit > 0) {
                    int count = 0;
                    for (int i = 0; i < entries.length(); i++) {
                        if (!entries.getJSONObject(i).optBoolean("favorite")) count++;
                    }
                    for (int i = entries.length() - 1; i >= 0 && count > limit; i--) {
                        if (!entries.getJSONObject(i).optBoolean("favorite")) {
                            entries.remove(i);
                            count--;
                        }
                    }
                }
                if (!ClipboardStore.write(getContext(), entries)) {
                    Log.e(TAG, "Failed writing clipboard history file");
                    return null;
                }
                // Keep a compatibility mirror for older screens/settings, but the
                // JSON file is authoritative and is always read first by the UI.
                prefs.edit().putString("entries", entries.toString()).apply();
                Log.i(TAG, "Saved clipboard text to " + ClipboardStore.FILE_NAME
                    + " (entries=" + entries.length() + ")");
                // Wake any open ClipboardActivity immediately after the durable write.
                getContext().getContentResolver().notifyChange(uri, null);
                return uri;
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