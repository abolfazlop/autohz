package com.abolfazl.hzswitcher;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

public final class ClipboardCaptureProvider extends ContentProvider {
 public static final String AUTHORITY="com.abolfazl.hzswitcher.clipboard.provider";
 public static final Uri CONTENT_URI=Uri.parse("content://"+AUTHORITY+"/clips");
 @Override public boolean onCreate(){return true;}
 @Override public Uri insert(Uri uri,ContentValues values){
  if(getContext()==null||values==null)return null;
  String text=values.getAsString("content");long time=values.getAsLong("captured_at")==null?System.currentTimeMillis():values.getAsLong("captured_at");
  ClipboardActivity.record(getContext(),text,time);return CONTENT_URI;
 }
 @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
 @Override public String getType(Uri u){return "vnd.android.cursor.item/vnd.pulsext.clip";}
 @Override public int delete(Uri u,String s,String[] a){return 0;}
 @Override public int update(Uri u,ContentValues v,String s,String[] a){return 0;}
}
