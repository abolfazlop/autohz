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
    private static final int BG=0xFF08090B, PANEL=0xFF191A1D, PANEL2=0xFF24262A;
    private static final int TEXT=0xFFF5F3FA, MUTED=0xFFA7A1B3, ACCENT=0xFFB58CFF;
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
        // Use the classic, inset-safe layout. Target SDK 34 below prevents
        // Android 15+ forced edge-to-edge from placing our custom tab bar
        // underneath the system gesture-navigation area.
        getWindow().getDecorView().setSystemUiVisibility(0);
        getWindow().setStatusBarColor(0xFF000000);
        getWindow().setNavigationBarColor(0xFF101010);
        if(android.os.Build.VERSION.SDK_INT>=29){
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        if(android.os.Build.VERSION.SDK_INT>=28)getWindow().setNavigationBarDividerColor(0xFF101010);
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
        appRoot=column(); appRoot.setBackgroundColor(BG);
        appRoot.setClipToPadding(false);
        LinearLayout top=row(); pad(top,22,14,22,10);
        TextView appName=text("PULSEXT",13,ACCENT,true);
        appName.setLetterSpacing(0.14f);
        top.addView(appName,new LinearLayout.LayoutParams(0,-2,1));
        TextView live=text("●  DISPLAY CONTROL",10,MUTED,true);
        live.setLetterSpacing(0.08f); top.addView(live);
        appRoot.addView(top,new LinearLayout.LayoutParams(-1,dp(46)));
        pageHost=column();
        appRoot.addView(pageHost,new LinearLayout.LayoutParams(-1,0,1));
        bottomBar=row(); bottomBar.setBackgroundColor(0xFF101114);
        bottomBar.setPadding(dp(10),dp(8),dp(10),dp(8));
        bottomBar.setElevation(dp(10)); bottomBar.setClipToPadding(false);
        appRoot.addView(bottomBar,new LinearLayout.LayoutParams(-1,dp(72)));
        setContentView(appRoot); showHome();
    }

    private void showPage(String title, View content){
        currentPage=title; pageHost.removeAllViews();
        LinearLayout page=column(); pad(page,20,12,20,20);
        LinearLayout heading=row();
        if(!title.equals("Home")&&!title.equals("Settings")&&!title.equals("About")){
            TextView back=text("‹",34,TEXT,false); back.setGravity(Gravity.CENTER);
            heading.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));
            back.setOnClickListener(v->showHome());
        }
        TextView name=text(title,30,TEXT,false);
        name.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
        heading.addView(name,new LinearLayout.LayoutParams(0,-2,1)); page.addView(heading);
        View accent=new View(this); accent.setBackground(shape(ACCENT,4,0));
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(38),dp(3)); ap.topMargin=dp(4); ap.bottomMargin=dp(14); page.addView(accent,ap);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(false); scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content); page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        pageHost.addView(page,new LinearLayout.LayoutParams(-1,-1));
        page.setAlpha(0f); page.setTranslationY(dp(12));
        page.animate().alpha(1f).translationY(0).setDuration(260).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        updateBottomNav();
    }

    private LinearLayout pageContent(){return column();}
    private void addFeatureRow(LinearLayout list,String icon,String title,String subtitle,Runnable action){
        LinearLayout item=row(); pad(item,16,15,16,15); item.setBackground(shape(PANEL,22,0xFF292B30));
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,dp(94)); ip.bottomMargin=dp(11); list.addView(item,ip);
        TextView glyph=text(icon,24,ACCENT,true); glyph.setGravity(Gravity.CENTER);
        glyph.setBackground(shape(0xFF30263F,18,0)); item.addView(glyph,new LinearLayout.LayoutParams(dp(54),dp(54)));
        LinearLayout labels=column(); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1); lp.leftMargin=dp(15); item.addView(labels,lp);
        labels.addView(text(title,18,TEXT,true)); TextView sub=text(subtitle,12,MUTED,false);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2); sp.topMargin=dp(5); labels.addView(sub,sp);
        TextView arrow=text("›",30,MUTED,false); item.addView(arrow);
        item.setOnClickListener(v->{v.animate().scaleX(.98f).scaleY(.98f).setDuration(80).withEndAction(()->{v.animate().scaleX(1f).scaleY(1f).setDuration(110).start();action.run();}).start();});
    }

    private void updateBottomNav(){
        if(bottomBar==null)return; bottomBar.removeAllViews();
        String[] names={"Home","Settings","About"}; String[] icons={"⌂","⬡","ⓘ"};
        for(int i=0;i<names.length;i++){ final String n=names[i]; boolean active=currentPage.equals(n);
            LinearLayout tab=column(); tab.setGravity(Gravity.CENTER); tab.setBackground(shape(active?0xFF302443:0x00000000,18,0));
            TextView glyph=text(icons[i],23,active?ACCENT:0xFF777A80,true); glyph.setGravity(Gravity.CENTER);
            tab.addView(glyph);
            TextView label=text(n,11,active?TEXT:0xFF777A80,active); label.setGravity(Gravity.CENTER); tab.addView(label);
            tab.setOnClickListener(v->{v.animate().alpha(.55f).setDuration(70).withEndAction(()->{if(n.equals("Home"))showHome();else if(n.equals("Settings"))showSettings();else showAbout();}).start();});
            bottomBar.addView(tab,new LinearLayout.LayoutParams(0,-1,1));
        }
    }

    private void showHome(){
        LinearLayout c=pageContent();
        LinearLayout hero=column(); pad(hero,18,18,18,18); hero.setBackground(shape(0xFF211A2D,24,0xFF493765));
        TextView eyebrow=text("YOUR DISPLAY, YOUR RULES",10,ACCENT,true); eyebrow.setLetterSpacing(.12f); hero.addView(eyebrow);
        TextView headline=text("Make every frame\ncount.",27,TEXT,false); headline.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2); hp.topMargin=dp(8); hero.addView(headline,hp);
        TextView sub=text("Fine-tune the way your POCO feels.",12,MUTED,false);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2); sp.topMargin=dp(6); hero.addView(sub,sp);
        LinearLayout.LayoutParams herop=new LinearLayout.LayoutParams(-1,-2); herop.bottomMargin=dp(16); c.addView(hero,herop);
        EditText search=new EditText(this); search.setSingleLine(true); search.setTextSize(14); search.setHint("⌕   Search features");
        search.setHintTextColor(MUTED); search.setTextColor(TEXT); pad(search,18,2,18,2); search.setBackground(shape(PANEL,30,0xFF292B30));
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(52)); ep.bottomMargin=dp(17); c.addView(search,ep);
        TextView section=text("CONTROL CENTER",10,MUTED,true); section.setLetterSpacing(.14f);
        LinearLayout.LayoutParams secp=new LinearLayout.LayoutParams(-1,-2); secp.bottomMargin=dp(10); c.addView(section,secp);
        LinearLayout list=column(); c.addView(list);
        addFeatureRow(list,"☼","Display Brightness","Smoothly adjust screen brightness",()->showBrightness());
        addFeatureRow(list,"▣","Refresh Rate","30  /  60  /  90  /  120 Hz",()->showRefreshRate());
        addFeatureRow(list,"⚙","System Tools","Device status and utilities",()->showSettings());
        addFeatureRow(list,"ⓘ","About PulseXt","Version and developer links",()->showAbout());
        showPage("Home",c);
        for(int i=0;i<list.getChildCount();i++){View child=list.getChildAt(i);child.setAlpha(0f);child.setTranslationY(dp(10));child.animate().alpha(1f).translationY(0).setStartDelay(45L*i).setDuration(220).start();}
        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            public void onTextChanged(CharSequence q,int st,int before,int count){
                String term=q.toString().toLowerCase(Locale.ROOT);
                for(int i=0;i<list.getChildCount();i++){View v=list.getChildAt(i);View labelsView=((LinearLayout)v).getChildAt(1);TextView label=null;if(labelsView instanceof LinearLayout)label=(TextView)((LinearLayout)labelsView).getChildAt(0);
                    if(label!=null)v.setVisibility(label.getText().toString().toLowerCase(Locale.ROOT).contains(term)?View.VISIBLE:View.GONE);
                }
            }
            public void afterTextChanged(android.text.Editable e){}
        });
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
        LinearLayout c=pageContent(); TextView logo=text("PulseXt",42,TEXT,false); logo.setGravity(Gravity.CENTER);
        c.addView(logo,new LinearLayout.LayoutParams(-1,dp(110)));
        TextView desc=text("A refined display control toolkit for HyperOS.",15,MUTED,false); desc.setGravity(Gravity.CENTER); c.addView(desc);
        TextView version=text("Version 1.0.0  •  Built for Android / HyperOS",13,MUTED,false); version.setGravity(Gravity.CENTER); LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,-2); vp.topMargin=dp(18); c.addView(version,vp);
        LinearLayout links=column(); LinearLayout.LayoutParams linksP=new LinearLayout.LayoutParams(-1,-2); linksP.topMargin=dp(28); c.addView(links,linksP);
        addDeveloperLink(links,"CREATOR","@U_GamerXt","https://t.me/U_GamerXt");
        addDeveloperLink(links,"DEVELOPER CHANNEL","@magiskmodulext","https://t.me/magiskmodulext");
        TextView footer=text("Made with care • PulseXt",11,MUTED,false); footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2); fp.topMargin=dp(24); c.addView(footer,fp);
        showPage("About",c);
    }

    private void addDeveloperLink(LinearLayout parent,String title,String handle,String url){
        LinearLayout card=row(); pad(card,16,14,16,14); card.setBackground(shape(PANEL,18,0xFF3C3150));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(76)); p.bottomMargin=dp(10); parent.addView(card,p);
        LinearLayout labels=column(); card.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView caption=text(title,10,MUTED,true); caption.setLetterSpacing(.1f); labels.addView(caption);
        TextView value=text(handle,16,ACCENT,true); LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,-2); vp.topMargin=dp(5); labels.addView(value,vp);
        TextView arrow=text("↗",22,ACCENT,true); card.addView(arrow);
        card.setOnClickListener(v->{try{android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(url));startActivity(intent);}catch(Exception e){Toast.makeText(this,"Telegram link could not be opened.",Toast.LENGTH_SHORT).show();}});
    }

    private void renderChoices(){
        for(int i=0;i<rateOptions.length&&i<rateButtons.size();i++){
            int hz=rateOptions[i];boolean ok=isSupported(hz);boolean active=selectedHz==hz;
            Button b=rateButtons.get(i);b.setEnabled(rootReady&&!busy&&ok);b.setAlpha(ok?1f:0.42f);
            b.setText(hz+" Hz"+(ok?"":" · N/A"));
            b.setBackground(shape(active?0xFF49336A:PANEL2,16,active?ACCENT:0));
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
                // Match the reference app's four-setting method. HyperOS reads
                // the vendor key plus Android's min/peak/user refresh-rate policy.
                String cmd="settings put secure miui_refresh_rate "+hz+
                    " && settings put system min_refresh_rate "+hz+
                    " && settings put system peak_refresh_rate "+hz+
                    " && settings put system user_refresh_rate "+hz;
                CommandResult applied=execRoot(cmd);
                if(applied.exit!=0||applied.timeout)throw new RuntimeException("Apply command failed: "+applied.output);
                Thread.sleep(1600);
                String verifySettings="settings get secure miui_refresh_rate; "+
                    "settings get system min_refresh_rate; "+
                    "settings get system peak_refresh_rate; "+
                    "settings get system user_refresh_rate";
                CommandResult settingCheck=execRoot(verifySettings);
                String[] values=settingCheck.output.trim().split("\\s+");
                if(values.length<4)throw new RuntimeException("Could not verify all four refresh settings: "+settingCheck.output);
                for(String value:values)if(!value.equals(String.valueOf(hz)))
                    throw new RuntimeException("Refresh settings were overridden. Expected "+hz+" Hz, got: "+settingCheck.output);
                prefs.edit().putInt("selected_refresh_rate",hz).apply();
                CommandResult verify=execRoot("dumpsys display");
                String dump=verify.output;
                java.util.regex.Matcher active=java.util.regex.Pattern.compile("mActiveRenderFrameRate=([0-9]+(?:\\\\.[0-9]+)?)").matcher(dump);
                boolean confirmed=false;
                while(active.find()){try{if(Math.abs(Float.parseFloat(active.group(1))-hz)<1.2f){confirmed=true;break;}}catch(Exception ignored){}}
                message=confirmed?"Applied and verified: "+hz+" Hz.":"Four system settings accepted ("+hz+" Hz), but the active display mode still reports a different rate. HyperOS may be overriding the request.";
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
            String[] commands={"settings get system user_refresh_rate","settings get secure miui_refresh_rate","settings get system min_refresh_rate","settings get system peak_refresh_rate","cmd display get-user-preferred-display-mode 0","dumpsys display"};
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
