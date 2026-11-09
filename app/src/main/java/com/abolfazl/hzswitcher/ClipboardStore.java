package com.abolfazl.hzswitcher;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;

/**
 * SQLite-backed clipboard history. This replaces the old JSON-file store.
 * The JSON methods are only an adapter for the existing UI/provider contract;
 * all durable reads and writes go through one transactional SQLite database.
 */
public final class ClipboardStore {
    public static final String FILE_NAME = "clipboard_history.db";
    private static final String DB_NAME = "clipboard_history.db";
    private static final int DB_VERSION = 1;
    private static final Object LOCK = new Object();
    private static volatile Helper helper;

    private ClipboardStore() {}

    private static Helper db(Context context) {
        Helper value = helper;
        if (value == null) {
            synchronized (LOCK) {
                value = helper;
                if (value == null) helper = value =
                    new Helper(context.getApplicationContext());
            }
        }
        return value;
    }

    public static JSONArray read(Context context) {
        synchronized (LOCK) {
            JSONArray result = new JSONArray();
            SQLiteDatabase database = db(context).getReadableDatabase();
            try (Cursor cursor = database.query("clips",
                    new String[]{"clip_id","content","timestamp","favorite","active"},
                    null,null,null,null,"timestamp DESC")) {
                while (cursor.moveToNext()) {
                    JSONObject item = new JSONObject();
                    item.put("id", cursor.getString(0));
                    item.put("text", cursor.getString(1));
                    item.put("time", cursor.getLong(2));
                    item.put("favorite", cursor.getInt(3) != 0);
                    item.put("active", cursor.getInt(4) != 0);
                    result.put(item);
                }
            } catch (Exception ignored) {}
            return result;
        }
    }

    /** Replace the table atomically with the supplied current UI snapshot. */
    public static boolean write(Context context, JSONArray entries) {
        synchronized (LOCK) {
            SQLiteDatabase database = db(context).getWritableDatabase();
            database.beginTransaction();
            try {
                database.delete("clips", null, null);
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject item = entries.optJSONObject(i);
                    if (item == null) continue;
                    String text = item.optString("text", "");
                    if (text.trim().isEmpty()) continue;
                    ContentValues values = new ContentValues();
                    values.put("clip_id", item.optString("id",
                        java.util.UUID.randomUUID().toString()));
                    values.put("content", text);
                    values.put("timestamp", item.optLong("time",
                        System.currentTimeMillis()));
                    values.put("favorite", item.optBoolean("favorite") ? 1 : 0);
                    values.put("active", item.optBoolean("active") ? 1 : 0);
                    database.insertWithOnConflict("clips", null, values,
                        SQLiteDatabase.CONFLICT_REPLACE);
                }
                database.setTransactionSuccessful();
                return true;
            } catch (Exception error) {
                return false;
            } finally {
                database.endTransaction();
            }
        }
    }

    private static final class Helper extends SQLiteOpenHelper {
        Helper(Context context) { super(context, DB_NAME, null, DB_VERSION); }

        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE clips (" +
                "clip_id TEXT PRIMARY KEY NOT NULL," +
                "content TEXT NOT NULL UNIQUE," +
                "timestamp INTEGER NOT NULL," +
                "favorite INTEGER NOT NULL DEFAULT 0," +
                "active INTEGER NOT NULL DEFAULT 0)");
            db.execSQL("CREATE INDEX clips_timestamp_idx ON clips(timestamp DESC)");
        }

        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // No destructive migration is needed for version 1.
        }
    }
}
