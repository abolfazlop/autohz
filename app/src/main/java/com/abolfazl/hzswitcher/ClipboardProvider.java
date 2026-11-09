package com.abolfazl.hzswitcher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Write-only mailbox for clipboard data sent by the LSPosed hook in system_server.
 * Access is protected by the signature permission declared in AndroidManifest.xml,
 * matching the original ClipVault implementation.
 */
public final class ClipboardProvider extends ContentProvider {
    private static final String PREF = "pulsext_clipboard";
    private static final String KEY = "entries";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (getContext() == null || values == null) return null;
        String text = values.getAsString("content");
        if (text == null || text.trim().isEmpty()) return null;

        SharedPreferences prefs = getContext().getSharedPreferences(PREF, 0);
        if (prefs.getBoolean("private_mode", false)) return null;

        synchronized (ClipboardProvider.class) {
            try {
                JSONArray entries = new JSONArray(prefs.getString(KEY, "[]"));

                // Match the existing PulseXt history behavior: update duplicate
                // entries instead of creating repeated rows.
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject old = entries.getJSONObject(i);
                    if (text.equals(old.optString("text"))) {
                        old.put("active", true);
                        for (int j = 0; j < entries.length(); j++) {
                            if (j != i) entries.getJSONObject(j).put("active", false);
                        }
                        prefs.edit().putString(KEY, entries.toString()).apply();
                        return uri;
                    }
                }

                for (int i = 0; i < entries.length(); i++) {
                    entries.getJSONObject(i).put("active", false);
                }

                JSONObject item = new JSONObject();
                item.put("id", UUID.randomUUID().toString());
                item.put("text", text);
                Long capturedAt = values.getAsLong("captured_at");
                item.put("time", capturedAt == null ? System.currentTimeMillis() : capturedAt);
                item.put("favorite", false);
                item.put("active", true);
                entries.put(item);

                int limit = prefs.getInt("history_limit", 200);
                if (limit > 0) {
                    int nonFavorites = 0;
                    for (int i = 0; i < entries.length(); i++) {
                        if (!entries.getJSONObject(i).optBoolean("favorite")) nonFavorites++;
                    }
                    for (int i = entries.length() - 1; i >= 0 && nonFavorites > limit; i--) {
                        if (!entries.getJSONObject(i).optBoolean("favorite")) {
                            entries.remove(i);
                            nonFavorites--;
                        }
                    }
                }
                prefs.edit().putString(KEY, entries.toString()).apply();
            } catch (Exception ignored) {
                // Never crash system_server's clipboard path because of history IO.
            }
        }
        return uri;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd.pulsext.clip";
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override public int update(Uri uri, ContentValues values, String selection,
                                String[] selectionArgs) {
        return 0;
    }
}