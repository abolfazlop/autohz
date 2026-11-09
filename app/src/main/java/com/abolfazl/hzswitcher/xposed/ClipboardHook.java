package com.abolfazl.hzswitcher.xposed;

import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public final class ClipboardHook implements IXposedHookLoadPackage {
 private static final String TAG="PulseXt Clipboard LSPosed";
 private static final ExecutorService delivery=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"PulseXt-clipboard-delivery");t.setDaemon(true);return t;});
 private static volatile Context systemContext;
 private void log(String s){XposedBridge.log(TAG+": "+s);}
 @Override public void handleLoadPackage(LoadPackageParam p){
  if(!"android".equals(p.packageName))return;
  try{
   Class<?> service=Class.forName("com.android.server.clipboard.ClipboardService",false,p.classLoader);
   int hooked=0;
   for(Class<?> c:all(service))for(Method m:c.getDeclaredMethods())if("setPrimaryClip".equals(m.getName())){
    try{m.setAccessible(true);XposedBridge.hookMethod(m,new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam param){if(param.hasThrowable())return;capture(param.args);}});hooked++;}catch(Throwable t){log("Cannot hook "+m+": "+t);}
   }
   log("Installed "+hooked+" ClipboardService hook(s)");
  }catch(Throwable t){log("Hook install failed: "+t);}
 }
 private static Class<?>[] all(Class<?> c){Class<?>[] nested=c.getDeclaredClasses();Class<?>[] out=new Class<?>[nested.length+1];out[0]=c;System.arraycopy(nested,0,out,1,nested.length);return out;}
 private static void capture(Object[] args){
  try{ClipData clip=null;for(Object a:args)if(a instanceof ClipData){clip=(ClipData)a;break;}if(clip==null||clip.getItemCount()==0)return;
   ClipData.Item item=clip.getItemAt(0);CharSequence cs=item.getText();String value=cs==null?null:cs.toString();if(value==null||value.trim().isEmpty())return;
   Context ctx=context();if(ctx==null)return;final String text=value;final long time=System.currentTimeMillis();
   delivery.execute(()->{try{ContentValues v=new ContentValues();v.put("content",text);v.put("captured_at",time);ctx.getContentResolver().insert(UriHolder.URI,v);}catch(Throwable t){XposedBridge.log(TAG+": delivery failed: "+t);}});
  }catch(Throwable t){XposedBridge.log(TAG+": capture failed: "+t);}
 }
 private static Context context(){
  if(systemContext!=null)return systemContext;
  try{Class<?> at=Class.forName("android.app.ActivityThread");Object thread=at.getMethod("currentActivityThread").invoke(null);systemContext=(Context)at.getMethod("getSystemContext").invoke(thread);return systemContext;}catch(Throwable t){XposedBridge.log(TAG+": no system context: "+t);return null;}
 }
 private static final class UriHolder {static final android.net.Uri URI=android.net.Uri.parse("content://com.abolfazl.hzswitcher.clipboard.provider/clips");}
}
