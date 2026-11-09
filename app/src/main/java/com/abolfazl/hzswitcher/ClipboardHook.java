package com.abolfazl.hzswitcher;
import android.content.*;import android.net.Uri;import java.lang.reflect.Method;import java.util.concurrent.*;import de.robv.android.xposed.*;import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
/** LSPosed hook capturing successful system clipboard writes. */
public final class ClipboardHook implements IXposedHookLoadPackage {
 private static final String TAG="PulseXt Clipboard";private static final Uri BRIDGE=Uri.parse("content://com.abolfazl.hzswitcher.clipboard/clips");
 private static final ExecutorService DELIVERY=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"PulseXt-clipboard-delivery");t.setDaemon(true);return t;});
 private static volatile Context systemContext;private void log(String s){XposedBridge.log(TAG+": "+s);}
 @Override public void handleLoadPackage(LoadPackageParam p){
  if("com.abolfazl.hzswitcher".equals(p.packageName)){try{XposedHelpers.findAndHookMethod(HookStatus.class.getName(),p.classLoader,"isActive",new XC_MethodHook(){@Override protected void beforeHookedMethod(MethodHookParam x){x.setResult(true);}});}catch(Throwable t){log("status marker: "+t);}return;}
  if(!"android".equals(p.packageName))return;
  try{Class<?> service=Class.forName("com.android.server.clipboard.ClipboardService",false,p.classLoader);ArrayList<Class<?>> classes=new ArrayList<>();classes.add(service);classes.addAll(Arrays.asList(service.getDeclaredClasses()));int count=0;
   for(Class<?> c:classes)for(Method m:c.getDeclaredMethods())if("setPrimaryClip".equals(m.getName()))try{m.setAccessible(true);XposedBridge.hookMethod(m,new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam x){if(x.hasThrowable())return;capture(x.args);}});count++;}catch(Throwable t){log("method hook failed: "+t);}
   log("installed "+count+" clipboard hook(s)");
  }catch(Throwable t){log("ClipboardService hook failed: "+t);}
 }
 private void capture(Object[] args){try{android.content.ClipData clip=null;for(Object a:args)if(a instanceof android.content.ClipData){clip=(android.content.ClipData)a;break;}if(clip==null||clip.getItemCount()==0)return;android.content.ClipData.Item item=clip.getItemAt(0);CharSequence cs=item.getText();String text=cs!=null?cs.toString():(item.getUri()!=null?item.getUri().toString():null);if(text==null||text.trim().isEmpty())return;long when=System.currentTimeMillis();Context ctx=systemContext;if(ctx==null){Class<?> at=Class.forName("android.app.ActivityThread");Object thread=at.getMethod("currentActivityThread").invoke(null);ctx=(Context)at.getMethod("getSystemContext").invoke(thread);systemContext=ctx;}final Context c=ctx;final String value=text;DELIVERY.execute(()->{try{ContentValues v=new ContentValues();v.put("content",value);v.put("captured_at",when);c.getContentResolver().insert(BRIDGE,v);}catch(Throwable t){XposedBridge.log(TAG+": delivery failed: "+t);}});}catch(Throwable t){log("capture failed: "+t);}}
}
