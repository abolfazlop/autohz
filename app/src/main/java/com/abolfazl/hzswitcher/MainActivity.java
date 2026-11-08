package com.abolfazl.hzswitcher;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.*;
import android.hardware.display.DisplayManager;
import android.view.Display;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int BG=0xFF090D14, PANEL=0xFF141C28, PANEL2=0xFF1B2736;
    private static final int TEXT=0xFFF3F6FA, MUTED=0xFF98A7B8, ACCENT=0xFF9BE7D0;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private android.content.SharedPreferences prefs;
    private TextView rootLabel, rootDetail, rateValue, rateCaption, resultLabel, deviceLabel, brightnessValue;
    private SeekBar brightnessSlider;
    private final int[] rateOptions={30,60,90,120};
    private final ArrayList<Button> rateButtons=new ArrayList<>();
    private Button restoreButton;
    private int selectedHz=120;
    private boolean rootReady=false, busy=false;
    private String lastLog="Ready";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences("hz_backup_v2",MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if(android.os.Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(true);
        if(android.os.Build.VERSION.SDK_INT>=29){
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        buildUi();
        refreshDisplay();
        checkRoot();
    }

    private GradientDrawable shape(int color,int radius,int strokeColor) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius));
        if(strokeColor!=0) d.setStroke(dp(1),strokeColor);
        return d;
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private TextView text(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        return t;
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void pad(View v,int a,int b,int c,int d){v.setPadding(dp(a),dp(b),dp(c),dp(d));}
    private TextView addLabel(LinearLayout parent,String s,int size,int color,boolean bold,int top) {
        TextView t=text(s,size,color,bold); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.topMargin=dp(top); parent.addView(t,p); return t;
    }

    private LinearLayout appRoot, pageHost, bottomBar;
    private String currentPage="Home";

    private void buildUi() {
        appRoot=column(); appRoot.setBackgroundColor(0xFF000000);
        LinearLayout top=row(); pad(top,24,18,24,12);
        TextView appName=text("AutoHz",14,TEXT,true);
        top.addView(appName,new LinearLayout.LayoutParams(0,-2,1));
        TextView topIcon=text("☼   ⚙",22,0xFFBBBBBB,false); top.addView(topIcon);
        topIcon.setOnClickListener(v->showSettings());
        appRoot.addView(top,new LinearLayout.LayoutParams(-1,-2));
        pageHost=column();
        LinearLayout.LayoutParams hostParams=new LinearLayout.LayoutParams(-1,0,1);
        appRoot.addView(pageHost,hostParams);
        bottomBar=row(); bottomBar.setBackgroundColor(0xFF101010); pad(bottomBar,12,8,12,8);
        appRoot.addView(bottomBar,new LinearLayout.LayoutParams(-1,dp(68)));
        setContentView(appRoot); showHome();
    }

    private void showPage(String title, View content){
        currentPage=title; pageHost.removeAllViews();
        LinearLayout page=column(); pad(page,22,18,22,24);
        LinearLayout heading=row();
        if(!title.equals("Home")&&!title.equals("Settings")&&!title.equals("About")){
            TextView back=text("‹",34,TEXT,false); back.setGravity(Gravity.CENTER);
            heading.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));
            back.setOnClickListener(v->showHome());
        }
        TextView name=text(title,32,TEXT,false);
        heading.addView(name,new LinearLayout.LayoutParams(0,-2,1)); page.addView(heading);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(false);
        scroll.addView(content); page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        pageHost.addView(page,new LinearLayout.LayoutParams(-1,-1));
        updateBottomNav();
    }

    private LinearLayout pageContent(){return column();}
    private void addFeatureRow(LinearLayout list,String icon,String title,String subtitle,Runnable action){
        LinearLayout item=row(); pad(item,18,18,18,18); item.setBackground(shape(0xFF252525,24,0));
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,dp(92)); ip.bottomMargin=dp(10); list.addView(item,ip);
        TextView glyph=text(icon,26,0xFFFFFFFF,true); glyph.setGravity(Gravity.CENTER);
        glyph.setBackground(shape(0xFF343434,50,0)); item.addView(glyph,new LinearLayout.LayoutParams(dp(52),dp(52)));
        LinearLayout labels=column(); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1); lp.leftMargin=dp(16); item.addView(labels,lp);
        labels.addView(text(title,18,TEXT,false)); TextView sub=text(subtitle,13,MUTED,false);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2); sp.topMargin=dp(4); labels.addView(sub,sp);
        item.addView(text("›",32,0xFF888888,false)); item.setOnClickListener(v->action.run());
    }

    private void updateBottomNav(){
        if(bottomBar==null)return; bottomBar.removeAllViews();
        String[] names={"Home","Settings","About"}; String[] icons={"⌂","⬡","ⓘ"};
        for(int i=0;i<names.length;i++){ final String n=names[i];
            LinearLayout tab=column(); tab.setGravity(Gravity.CENTER);
            tab.addView(text(icons[i],25,currentPage.equals(n)?TEXT:0xFF777777,true));
            TextView label=text(n,12,currentPage.equals(n)?TEXT:0xFF777777,false); tab.addView(label);
            tab.setOnClickListener(v->{if(n.equals("Home"))showHome();else if(n.equals("Settings"))showSettings();else showAbout();});
            bottomBar.addView(tab,new LinearLayout.LayoutParams(0,-1,1));
        }
    }

    private void showHome(){
        LinearLayout c=pageContent();
        EditText search=new EditText(this); search.setSingleLine(true); search.setTextSize(16); search.setHint("⌕  Search");
        search.setHintTextColor(0xFF888888); search.setTextColor(TEXT); pad(search,18,4,18,4); search.setBackground(shape(0xFF252525,40,0));
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(56)); ep.bottomMargin=dp(14); c.addView(search,ep);
        TextView tip=text("Tip: Tune your display settings from separate sections.",14,0xFFAAAAAA,false); pad(tip,18,18,18,18); tip.setBackground(shape(0xFF252525,22,0));
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2); tp.bottomMargin=dp(18); c.addView(tip,tp);
        LinearLayout list=column(); c.addView(list);
        addFeatureRow(list,"☼","Display Brightness","Adjust screen brightness",()->showBrightness());
        addFeatureRow(list,"▣","Refresh Rate","Choose 30 / 60 / 90 / 120 Hz",()->showRefreshRate());
        addFeatureRow(list,"⚙","System Tools","Device status and system utilities",()->showSettings());
        addFeatureRow(list,"ⓘ","About AutoHz","Version and app information",()->showAbout());
        showPage("Home",c);
    }

    private void showBrightness(){
        LinearLayout c=pageContent();
        addLabel(c,"Display brightness",18,TEXT,true,8);
        addLabel(c,"Adjust the brightness of your entire screen.",14,MUTED,false,8);
        LinearLayout card=column(); pad(card,20,20,20,20); card.setBackground(shape(0xFF252525,24,0));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.topMargin=dp(20); c.addView(card,cp);
        LinearLayout hdr=row(); hdr.addView(text("Brightness level",16,TEXT,false),new LinearLayout.LayoutParams(0,-2,1));
        brightnessValue=text("--%",16,TEXT,true); hdr.addView(brightnessValue); card.addView(hdr);
        brightnessSlider=new SeekBar(this); brightnessSlider.setMax(255);
        if(android.os.Build.VERSION.SDK_INT>=21){brightnessSlider.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFDDDDDD));brightnessSlider.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFFDDDDDD));}
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2); bp.topMargin=dp(18); card.addView(brightnessSlider,bp);
        brightnessSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar seekBar,int progress,boolean fromUser){if(fromUser)brightnessValue.setText(Math.round(progress*100f/255f)+"%");}
            public void onStartTrackingTouch(SeekBar seekBar){}
            public void onStopTrackingTouch(SeekBar seekBar){applyBrightness(seekBar.getProgress());}
        });
        addStatusLog(c); showPage("Display Brightness",c); loadBrightness();
    }

    private void showRefreshRate(){
        LinearLayout c=pageContent();
        addLabel(c,"Refresh rate",18,TEXT,true,8); addLabel(c,"Select a refresh rate supported by your display.",14,MUTED,false,8);
        LinearLayout choices=column(); LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.topMargin=dp(18); c.addView(choices,cp);
        rateButtons.clear();
        for(int i=0;i<rateOptions.length;i+=2){
            LinearLayout line=row(); LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(58)); if(i>0)rp.topMargin=dp(8); choices.addView(line,rp);
            for(int j=i;j<Math.min(i+2,rateOptions.length);j++){final int hz=rateOptions[j]; Button b=new Button(this); rateButtons.add(b); b.setText(hz+" Hz"); b.setTextSize(14); b.setAllCaps(false); b.setTextColor(TEXT); b.setPadding(0,0,0,0); b.setMinHeight(0); b.setMinimumHeight(0); b.setStateListAnimator(null);
                LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-1,1); if(j>i)bp.leftMargin=dp(8); line.addView(b,bp); b.setOnClickListener(v->applyRate(hz));}
            while(line.getChildCount()<2)line.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        }
        Button diag=new Button(this); diag.setText("Run refresh-rate diagnostics"); diag.setAllCaps(false); diag.setTextColor(TEXT); diag.setBackground(shape(0xFF303030,18,0));
        LinearLayout.LayoutParams dgp=new LinearLayout.LayoutParams(-1,dp(52)); dgp.topMargin=dp(14); c.addView(diag,dgp);
        diag.setOnClickListener(v->runRefreshDiagnostics());
        addStatusLog(c); showPage("Refresh Rate",c); refreshDisplay();
    }

    private void addStatusLog(LinearLayout c){
        LinearLayout card=column(); pad(card,16,16,16,16); card.setBackground(shape(0xFF252525,20,0));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(22); c.addView(card,p);
        card.addView(text("Activity",15,TEXT,true)); resultLabel=text("Ready",13,MUTED,false); LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2); rp.topMargin=dp(8); card.addView(resultLabel,rp);
    }

    private void showSettings(){
        LinearLayout c=pageContent();
        LinearLayout rootCard=column(); pad(rootCard,18,18,18,18); rootCard.setBackground(shape(0xFF252525,22,0)); c.addView(rootCard);
        LinearLayout rr=row(); rr.addView(text("Root access",16,TEXT,true),new LinearLayout.LayoutParams(0,-2,1)); rootLabel=text("Checking…",13,MUTED,true); rr.addView(rootLabel); rootCard.addView(rr);
        rootDetail=text("Checking superuser permission.",13,MUTED,false); LinearLayout.LayoutParams dp0=new LinearLayout.LayoutParams(-1,-2); dp0.topMargin=dp(10); rootCard.addView(rootDetail,dp0);
        restoreButton=new Button(this); restoreButton.setText("Restore previous settings"); restoreButton.setAllCaps(false); restoreButton.setTextColor(TEXT); restoreButton.setBackground(shape(0xFF303030,18,0));
        LinearLayout.LayoutParams rb=new LinearLayout.LayoutParams(-1,dp(54)); rb.topMargin=dp(16); c.addView(restoreButton,rb); restoreButton.setOnClickListener(v->restoreSettings());
        rootLabel.setText(rootReady?"GRANTED":"NOT GRANTED");
        rootLabel.setTextColor(rootReady?ACCENT:0xFFFF9B9B);
        rootDetail.setText(rootReady?"Root shell verified.":"Root access is required for system changes.");
        showPage("Settings",c); renderChoices();
    }

    private void showAbout(){
        LinearLayout c=pageContent(); TextView logo=text("AutoHz",42,TEXT,false); logo.setGravity(Gravity.CENTER);
        c.addView(logo,new LinearLayout.LayoutParams(-1,dp(110)));
        TextView desc=text("Display control and system tools for HyperOS.",15,MUTED,false); desc.setGravity(Gravity.CENTER); c.addView(desc);
        TextView version=text("Version 1.0  •  POCO X7 Pro profile",13,MUTED,false); version.setGravity(Gravity.CENTER); LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,-2); vp.topMargin=dp(18); c.addView(version,vp);
        showPage("About",c);
    }

    private void renderChoices(){
        for(int i=0;i<rateOptions.length&&i<rateButtons.size();i++){
            int hz=rateOptions[i];boolean ok=isSupported(hz);boolean active=selectedHz==hz;
            Button b=rateButtons.get(i);b.setEnabled(rootReady&&!busy&&ok);b.setAlpha(ok?1f:0.42f);
            b.setText(hz+" Hz"+(ok?"":" · N/A"));
            b.setBackground(shape(active?0xFF235D50:PANEL2,16,active?ACCENT:0));
        }
        if(restoreButton!=null)restoreButton.setEnabled(rootReady&&!busy&&prefs.getBoolean("has_backup",false));
        if(brightnessSlider!=null)brightnessSlider.setEnabled(rootReady&&!busy);
    }

    private Display.Mode[] modes(){
        Display d=((DisplayManager)getSystemService(DISPLAY_SERVICE)).getDisplay(Display.DEFAULT_DISPLAY);
        return d==null?new Display.Mode[0]:d.getSupportedModes();
    }
    private boolean isSupported(int hz){for(Display.Mode m:modes())if(Math.abs(m.getRefreshRate()-hz)<1.2f)return true;return false;}
    private Display.Mode modeFor(int hz){for(Display.Mode m:modes())if(Math.abs(m.getRefreshRate()-hz)<1.2f)return m;return null;}
    private void refreshDisplay(){
        Display d=((DisplayManager)getSystemService(DISPLAY_SERVICE)).getDisplay(Display.DEFAULT_DISPLAY);
        if(d!=null&&rateValue!=null){rateValue.setText(String.format(Locale.US,"%.0f",d.getRefreshRate()));}
        renderChoices();
    }
    private void checkRoot(){
        executor.execute(()->{
            String out=runSu("id");
            boolean ok=out.contains("uid=0");
            runOnUiThread(()->{
                rootReady=ok;
                if(rootLabel!=null){
                    rootLabel.setText(ok?"GRANTED":"NOT GRANTED");
                    rootLabel.setTextColor(ok?ACCENT:0xFFFF9B9B);
                }
                if(rootDetail!=null){
                    rootDetail.setText(ok?"Root shell verified. Select a refresh rate to apply.":"Root access was not granted. Approve the superuser request in Magisk / KernelSU.");
                }
                if(resultLabel!=null){
                    resultLabel.setText(ok?"• Root shell verified. Ready to apply a display mode.":"• Root access not granted. Check your superuser manager.");
                }
                renderChoices();
                refreshDisplay();
                if(brightnessSlider!=null)loadBrightness();
            });
        });
    }

    private static class CommandResult {String output; int exit; boolean timeout; CommandResult(String o,int e,boolean t){output=o;exit=e;timeout=t;}}
    private CommandResult execRoot(String command){
        java.lang.Process p=null;
        try{
            p=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            InputStream in=p.getInputStream();byte[] buf=new byte[2048];long end=System.currentTimeMillis()+12000;
            while(System.currentTimeMillis()<end){
                while(in.available()>0){int n=in.read(buf);if(n<0)break;out.write(buf,0,n);}
                if(!p.isAlive())break;Thread.sleep(40);
            }
            boolean timeout=p.isAlive();if(timeout)p.destroyForcibly();
            if(!timeout)p.waitFor();
            return new CommandResult(out.toString("UTF-8").trim(),timeout?124:p.exitValue(),timeout);
        }catch(Exception e){if(p!=null)p.destroyForcibly();return new CommandResult(e.toString(),1,false);}
    }
    private String runSu(String cmd){CommandResult r=execRoot(cmd);return r.output;}
    private String readSetting(String table,String key){
        CommandResult r=execRoot("settings get "+table+" "+key);
        return r.exit==0?r.output:"__ERROR__";
    }
    private String quote(String s){return "'"+s.replace("'","'\\''")+"'";}

    private void saveBackup(){
        String[] pairs={"secure miui_refresh_rate","secure user_refresh_rate","system min_refresh_rate","system peak_refresh_rate","system user_refresh_rate","system screen_brightness"};
        StringBuilder b=new StringBuilder();
        for(String pair:pairs){String[] a=pair.split(" ");String val=readSetting(a[0],a[1]);if(val.equals("__ERROR__"))throw new RuntimeException("Could not read "+pair);b.append(pair).append("=").append(val).append("\n");}
        prefs.edit().putString("backup",b.toString()).putBoolean("has_backup",true).apply();
    }

    private void loadBrightness(){
        if(!rootReady||brightnessSlider==null)return;
        executor.execute(()->{
            CommandResult r=execRoot("settings get system screen_brightness");
            int value=128;
            try{value=Math.max(0,Math.min(255,Integer.parseInt(r.output.trim())));}catch(Exception ignored){}
            final int level=value;
            runOnUiThread(()->{brightnessSlider.setProgress(level);brightnessValue.setText(Math.round(level*100f/255f)+"%");});
        });
    }

    private void applyBrightness(int value){
        if(!rootReady||busy)return;
        final int level=Math.max(0,Math.min(255,value));
        brightnessValue.setText(Math.round(level*100f/255f)+"%");
        executor.execute(()->{
            String message;
            try{
                if(!prefs.getBoolean("has_backup",false))saveBackup();
                CommandResult r=execRoot("settings put system screen_brightness "+level);
                if(r.exit!=0||r.timeout)throw new RuntimeException(r.output);
                CommandResult check=execRoot("settings get system screen_brightness");
                if(!check.output.trim().equals(String.valueOf(level)))throw new RuntimeException("Brightness value could not be verified: "+check.output);
                message="Brightness set to "+Math.round(level*100f/255f)+"%. User brightness was verified.";
            }catch(Exception e){message="Brightness failed: "+e.getMessage();}
            final String result=message;runOnUiThread(()->resultLabel.setText(result));
        });
    }
    private void applyRate(int hz){
        if(!rootReady||busy)return;
        Display.Mode mode=modeFor(hz);
        if(mode==null){resultLabel.setText(hz+" Hz is not reported as a supported physical display mode.");return;}
        selectedHz=hz;busy=true;renderChoices();
        executor.execute(()->{
            String message="";
            try{
                if(!prefs.getBoolean("has_backup",false))saveBackup();
                // HyperOS 3: use the vendor secure policy and Android's GLOBAL
                // preferred display mode. Omitting DISPLAY_ID avoids a display-local override.
                String cmd="settings put secure user_refresh_rate 1"+
                    " && settings put secure miui_refresh_rate "+hz+
                    " && cmd display set-user-preferred-display-mode "+mode.getPhysicalWidth()+" "+mode.getPhysicalHeight()+" "+hz+" 0;
                CommandResult applied=execRoot(cmd);
                if(applied.exit!=0||applied.timeout)throw new RuntimeException("Apply command failed: "+applied.output);
                Thread.sleep(1400);
                CommandResult settingCheck=execRoot("settings get secure miui_refresh_rate");
                if(!settingCheck.output.trim().equals(String.valueOf(hz)))
                    throw new RuntimeException("HyperOS changed miui_refresh_rate to "+settingCheck.output+" (requested "+hz+")");
                CommandResult preferredCheck=execRoot("cmd display get-user-preferred-display-mode 0");
                prefs.edit().putInt("selected_refresh_rate",hz).apply();
                CommandResult verify=execRoot("dumpsys display");
                String dump=verify.output;
                java.util.regex.Matcher active=java.util.regex.Pattern.compile("mActiveRenderFrameRate=([0-9]+(?:\\\\.[0-9]+)?)").matcher(dump);
                boolean confirmed=false;
                while(active.find()){try{if(Math.abs(Float.parseFloat(active.group(1))-hz)<1.2f){confirmed=true;break;}}catch(Exception ignored){}}
                if(!confirmed){java.util.regex.Matcher peak=java.util.regex.Pattern.compile("peakRefreshRate=([0-9]+(?:\\\\.[0-9]+)?)").matcher(dump);while(peak.find()){try{if(Math.abs(Float.parseFloat(peak.group(1))-hz)<1.2f){confirmed=true;break;}}catch(Exception ignored){}}}
                message=confirmed?"Applied and verified: "+hz+" Hz.":"Preference saved ("+hz+" Hz), but active mode is currently different. HyperOS policy may be overriding it.\n"+preferredCheck.output;
                lastLog=message;
            }catch(Exception e){message="FAILED: "+e.getMessage();lastLog=message;}
            final String m=message;
            runOnUiThread(()->{busy=false;resultLabel.setText(m);renderChoices();refreshDisplay();});
        });
    }


    private void runRefreshDiagnostics(){
        if(!rootReady||busy){if(resultLabel!=null)resultLabel.setText("Root access is required.");return;}
        if(resultLabel!=null)resultLabel.setText("Collecting display diagnostics…");
        executor.execute(()->{
            StringBuilder out=new StringBuilder();
            String[] commands={"settings get secure user_refresh_rate","settings get secure miui_refresh_rate","settings get system min_refresh_rate","settings get system peak_refresh_rate","cmd display get-user-preferred-display-mode 0","dumpsys display"};
            String[] labels={"secure user_refresh_rate","secure miui_refresh_rate","system min_refresh_rate","system peak_refresh_rate","preferred display mode","display dump"};
            for(int i=0;i<commands.length;i++){
                CommandResult r=execRoot(commands[i]);
                out.append(labels[i]).append(": ").append(r.output).append("\\n");
            }
            String dump=out.toString();
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("mActiveRenderFrameRate=([0-9]+(?:\\\\.[0-9]+)?)").matcher(dump);
            String active=m.find()?m.group(1):"not found";
            final String report="Active render rate: "+active+" Hz\\n\\n"+dump;
            runOnUiThread(()->{if(resultLabel!=null)resultLabel.setText(report);});
        });
    }

    private void restoreSettings(){
        if(!rootReady||busy||!prefs.getBoolean("has_backup",false))return;
        busy=true;renderChoices();
        executor.execute(()->{
            StringBuilder errors=new StringBuilder();
            String backup=prefs.getString("backup","");
            for(String line:backup.split("\n")){
                int eq=line.indexOf('=');if(eq<1)continue;
                String pair=line.substring(0,eq),value=line.substring(eq+1);
                String[] a=pair.split(" ");
                String cmd=value.equals("null")||value.isEmpty()?"settings delete "+a[0]+" "+a[1]:"settings put "+a[0]+" "+a[1]+" "+quote(value);
                CommandResult r=execRoot(cmd);if(r.exit!=0)errors.append(pair).append(": ").append(r.output).append("\n");
            }
            CommandResult clear=execRoot("cmd display clear-user-preferred-display-mode 0");
            prefs.edit().remove("backup").putBoolean("has_backup",false).apply();
            String msg=errors.length()==0?"Previous settings restored.":"Restore completed with warnings: "+errors;
            runOnUiThread(()->{busy=false;resultLabel.setText(msg);renderChoices();refreshDisplay();});
        });
    }

    @Override protected void onResume(){
        super.onResume(); refreshDisplay();
        if(rootReady&&brightnessSlider!=null)loadBrightness();
        if(rootReady&&"Refresh Rate".equals(currentPage))loadSavedRefreshRate();
    }
    private void loadSavedRefreshRate(){
        executor.execute(()->{
            CommandResult r=execRoot("settings get secure miui_refresh_rate");
            int value=prefs.getInt("selected_refresh_rate",120);
            try{int stored=Integer.parseInt(r.output.trim());if(stored==30||stored==60||stored==90||stored==120)value=stored;}catch(Exception ignored){}
            final int hz=value;
            runOnUiThread(()->{selectedHz=hz;renderChoices();});
        });
    }
    @Override protected void onDestroy(){super.onDestroy();executor.shutdownNow();}
}
