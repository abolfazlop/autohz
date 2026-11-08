package com.abolfazl.hzswitcher;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.view.Display;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
 private TextView status, rate;
 private Button b60,b90,b120,auto;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final String[] keys={"system|min_refresh_rate","system|peak_refresh_rate","system|user_refresh_rate","secure|miui_refresh_rate"};
 private boolean root=false,busy=false;
 private android.content.SharedPreferences prefs;
 public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("backup",0);build(); checkRoot();}
 private void build(){
  LinearLayout l=new LinearLayout(this); l.setOrientation(1); l.setPadding(28,36,28,24); l.setGravity(Gravity.CENTER_HORIZONTAL); l.setBackgroundColor(Color.rgb(16,24,38));
  TextView title=new TextView(this); title.setText("Hz Switcher"); title.setTextSize(28); title.setTextColor(-1); l.addView(title);
  status=label("در حال بررسی Root..."); l.addView(status);
  rate=label("نرخ فعلی: ..."); l.addView(rate);
  TextView hint=label("فقط نرخ‌های پشتیبانی‌شده نمایشگر فعال می‌شوند."); l.addView(hint);
  LinearLayout row=new LinearLayout(this); row.setOrientation(0);
  b60=button("60 Hz",()->apply(60)); b90=button("90 Hz",()->apply(90)); b120=button("120 Hz",()->apply(120));
  row.addView(b60,new LinearLayout.LayoutParams(0,120,1)); row.addView(b90,new LinearLayout.LayoutParams(0,120,1)); row.addView(b120,new LinearLayout.LayoutParams(0,120,1)); l.addView(row);
  auto=button("بازگردانی تنظیمات قبلی (Auto)",this::restore); l.addView(auto);
  setContentView(l); updateRate(); setEnabled(false);
 }
 private TextView label(String s){TextView t=new TextView(this);t.setText(s);t.setTextColor(-1);t.setTextSize(16);t.setPadding(4,24,4,24);return t;}
 private Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setOnClickListener(v->r.run());return b;}
 private void setEnabled(boolean e){if(b60==null)return;b60.setEnabled(e&&supported(60));b90.setEnabled(e&&supported(90));b120.setEnabled(e&&supported(120));auto.setEnabled(e&&prefs.getBoolean("saved",false));}
 private void checkRoot(){worker.execute(()->{String out=su("id");root=out.contains("uid=0");runOnUiThread(()->{status.setText(root?"دسترسی Root فعال است ✓":"دسترسی Root موجود نیست ✕");setEnabled(root);updateRate();});});}
 private String su(String cmd){try{java.lang.Process p=new ProcessBuilder("su","-c",cmd).redirectErrorStream(true).start();ByteArrayOutputStream o=new ByteArrayOutputStream();InputStream in=p.getInputStream();byte[] buf=new byte[1024];int n;while((n=in.read(buf))>0)o.write(buf,0,n);p.waitFor(15,TimeUnit.SECONDS);return o.toString("UTF-8");}catch(Exception e){return "ERROR "+e;}}
 private boolean supported(int hz){Display d=((DisplayManager)getSystemService(DISPLAY_SERVICE)).getDisplay(Display.DEFAULT_DISPLAY);if(d==null)return false;for(Display.Mode m:d.getSupportedModes())if(Math.abs(m.getRefreshRate()-hz)<1.5)return true;return false;}
 private void apply(int hz){if(!root||busy||!supported(hz))return;busy=true;setEnabled(false);worker.execute(()->{
  if(!prefs.getBoolean("saved",false)){StringBuilder snap=new StringBuilder();for(String k:keys){String[] a=k.split("\\|");String v=su("settings get "+a[0]+" "+a[1]).trim();snap.append(k).append("=").append(v).append("\n");}prefs.edit().putString("snapshot",snap.toString()).putBoolean("saved",true).apply();}
  String cmd="settings put system min_refresh_rate "+hz+"; settings put system peak_refresh_rate "+hz+"; ";if(android.os.Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("xiaomi"))cmd+="settings put secure miui_refresh_rate "+hz+";";String result=su(cmd);
  runOnUiThread(()->{busy=false;status.setText("درخواست "+hz+"Hz ارسال شد");setEnabled(root);updateRate();});
 });}
 private void restore(){if(!root||busy||!prefs.getBoolean("saved",false))return;busy=true;setEnabled(false);worker.execute(()->{String snap=prefs.getString("snapshot","");for(String line:snap.split("\n")){int i=line.indexOf('=');if(i<1)continue;String[] a=line.substring(0,i).split("\\|");String v=line.substring(i+1);String cmd=v.equals("null")?"settings delete "+a[0]+" "+a[1]:"settings put "+a[0]+" "+a[1]+" '"+v.replace("'","")+"'";su(cmd);}prefs.edit().clear().apply();runOnUiThread(()->{busy=false;status.setText("تنظیمات قبلی بازیابی شد ✓");setEnabled(root);updateRate();});});}
 private void updateRate(){if(rate==null)return;Display d=((DisplayManager)getSystemService(DISPLAY_SERVICE)).getDisplay(Display.DEFAULT_DISPLAY);if(d!=null)rate.setText(String.format(Locale.US,"نرخ گزارش‌شده: %.2f Hz",d.getRefreshRate()));}
 protected void onResume(){super.onResume();updateRate();}
}
