package com.abolfazl.hzswitcher;
import android.content.*;import android.database.Cursor;import android.net.Uri;import org.json.*;import java.util.UUID;
/** Signature-protected clipboard mailbox used by the system_server hook. */
public final class ClipboardProvider extends ContentProvider {
 private static final String PREF="pulsext_clipboard",KEY="entries";
 @Override public boolean onCreate(){return true;}
 @Override public Uri insert(Uri uri,ContentValues values){if(getContext()==null||values==null)return null;String text=values.getAsString("content");if(text==null||text.trim().isEmpty())return null;SharedPreferences p=getContext().getSharedPreferences(PREF,0);if(p.getBoolean("private_mode",false))return null;
  synchronized(ClipboardProvider.class){try{JSONArray a=new JSONArray(p.getString(KEY,"[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);if(text.equals(o.optString("text"))){o.put("active",true);for(int j=0;j<a.length();j++)if(j!=i)a.getJSONObject(j).put("active",false);p.edit().putString(KEY,a.toString()).apply();return uri;}}
   for(int i=0;i<a.length();i++)a.getJSONObject(i).put("active",false);JSONObject o=new JSONObject();o.put("id",UUID.randomUUID().toString());o.put("text",text);Long captured=values.getAsLong("captured_at");o.put("time",captured==null?System.currentTimeMillis():captured);o.put("favorite",false);o.put("active",true);a.put(o);
   int limit=p.getInt("history_limit",200);if(limit>0){int count=0;for(int i=0;i<a.length();i++)if(!a.getJSONObject(i).optBoolean("favorite"))count++;for(int i=a.length()-1;i>=0&&count>limit;i--)if(!a.getJSONObject(i).optBoolean("favorite")){a.remove(i);count--;}}
   p.edit().putString(KEY,a.toString()).apply();
  }catch(Exception ignored){}}return uri;}
 @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}@Override public String getType(Uri u){return "vnd.android.cursor.item/vnd.pulsext.clip";}@Override public int delete(Uri u,String s,String[] a){return 0;}@Override public int update(Uri u,ContentValues v,String s,String[] a){return 0;}
}
