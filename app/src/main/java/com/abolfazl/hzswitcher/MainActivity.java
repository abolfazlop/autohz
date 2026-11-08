package com.abolfazl.hzswitcher;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
    private final int[] rateOptions={30,40,48,50,60,72,80,90,96,100,120};
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

    private void buildUi() {
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout page=column(); pad(page,22,24,22,28); scroll.addView(page);
        LinearLayout header=row();
        LinearLayout brand=column();
        brand.addView(text("HZ SWITCHER",12,ACCENT,true));
        TextView subtitle=text("Refresh rate control",14,MUTED,false);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);sp.topMargin=dp(4);brand.addView(subtitle,sp);
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView chip=text("ROOT EDITION",10,ACCENT,true);pad(chip,12,8,12,8);chip.setBackground(shape(0xFF17352F,30,0));header.addView(chip);
        page.addView(header);

        LinearLayout hero=column();pad(hero,20,20,20,20);hero.setBackground(shape(PANEL,26,0xFF263647));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(28);page.addView(hero,hp);
        LinearLayout heroTop=row();heroTop.addView(text("LIVE DISPLAY",11,MUTED,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView live=text("●  MONITORING",10,ACCENT,true);heroTop.addView(live);hero.addView(heroTop);
        rateValue=text("--",58,TEXT,true);LinearLayout.LayoutParams rv=new LinearLayout.LayoutParams(-2,-2);rv.topMargin=dp(12);hero.addView(rateValue,rv);
        rateCaption=text("Hz  •  Current reported refresh rate",13,MUTED,false);hero.addView(rateCaption);
        View line=new View(this);line.setBackgroundColor(0xFF2B394A);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.topMargin=dp(20);lp.bottomMargin=dp(16);hero.addView(line,lp);
        deviceLabel=text("POCO X7 Pro  ·  HyperOS 3 profile",12,0xFFC5D0DC,false);hero.addView(deviceLabel);

        addLabel(page,"Display brightness",18,TEXT,true,28);
        addLabel(page,"Adjust your device screen brightness.",13,MUTED,false,6);
        LinearLayout brightnessCard=column();pad(brightnessCard,18,16,18,16);brightnessCard.setBackground(shape(PANEL,20,0));
        LinearLayout.LayoutParams bcp=new LinearLayout.LayoutParams(-1,-2);bcp.topMargin=dp(14);page.addView(brightnessCard,bcp);
        LinearLayout brightnessHeader=row();
        brightnessHeader.addView(text("Brightness level",14,TEXT,true),new LinearLayout.LayoutParams(0,-2,1));
        brightnessValue=text("--%",14,ACCENT,true);brightnessHeader.addView(brightnessValue);
        brightnessCard.addView(brightnessHeader);
        brightnessSlider=new SeekBar(this);brightnessSlider.setMax(255);
        if(android.os.Build.VERSION.SDK_INT>=21)brightnessSlider.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        if(android.os.Build.VERSION.SDK_INT>=21)brightnessSlider.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        LinearLayout.LayoutParams bsp=new LinearLayout.LayoutParams(-1,-2);bsp.topMargin=dp(10);brightnessCard.addView(brightnessSlider,bsp);
        brightnessSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar seekBar,int progress,boolean fromUser){
                if(fromUser)brightnessValue.setText(Math.round(progress*100f/255f)+"%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar){}
            @Override public void onStopTrackingTouch(SeekBar seekBar){applyBrightness(seekBar.getProgress());}
        });

        addLabel(page,"Refresh rate",18,TEXT,true,28);
        addLabel(page,"Choose a mode supported by your display.",13,MUTED,false,6);
        LinearLayout choices=column();LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(14);page.addView(choices,cp);
        for(int i=0;i<rateOptions.length;i+=3){
            LinearLayout choiceRow=row();LinearLayout.LayoutParams rowp=new LinearLayout.LayoutParams(-1,dp(58));if(i>0)rowp.topMargin=dp(8);choices.addView(choiceRow,rowp);
            for(int j=i;j<Math.min(i+3,rateOptions.length);j++){
                final int hz=rateOptions[j];Button b=new Button(this);rateButtons.add(b);
                b.setText(hz+" Hz");b.setTextSize(14);b.setAllCaps(false);b.setTextColor(TEXT);
                b.setPadding(0,0,0,0);b.setMinHeight(0);b.setMinimumHeight(0);b.setStateListAnimator(null);
                LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-1,1);if(j>i)bp.leftMargin=dp(8);choiceRow.addView(b,bp);
                b.setOnClickListener(v->applyRate(hz));
            }
            while(choiceRow.getChildCount()<3){View spacer=new View(this);choiceRow.addView(spacer,new LinearLayout.LayoutParams(0,1,1));}
        }

        LinearLayout rootCard=column();pad(rootCard,16,16,16,16);rootCard.setBackground(shape(PANEL,20,0));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(24);page.addView(rootCard,rp);
        LinearLayout rootRow=row();rootRow.addView(text("Root access",15,TEXT,true),new LinearLayout.LayoutParams(0,-2,1));
        rootLabel=text("Checking…",12,MUTED,true);rootRow.addView(rootLabel);rootCard.addView(rootRow);
        rootDetail=text("Waiting for device check.",12,MUTED,false);LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,-2);rlp.topMargin=dp(10);rootCard.addView(rootDetail,rlp);

        restoreButton=new Button(this);restoreButton.setText("Restore previous settings");restoreButton.setAllCaps(false);
        restoreButton.setTextColor(TEXT);restoreButton.setTextSize(14);restoreButton.setBackground(shape(PANEL2,16,0));
        LinearLayout.LayoutParams rbp=new LinearLayout.LayoutParams(-1,dp(54));rbp.topMargin=dp(14);page.addView(restoreButton,rbp);
        restoreButton.setOnClickListener(v->restoreSettings());

        addLabel(page,"Activity log",16,TEXT,true,26);
        TextView log=text("• Ready for root and display checks.",12,MUTED,false);pad(log,14,14,14,14);log.setBackground(shape(PANEL,16,0));
        LinearLayout.LayoutParams logp=new LinearLayout.LayoutParams(-1,-2);logp.topMargin=dp(10);page.addView(log,logp);
        resultLabel=log;
        addLabel(page,"Only the physical display refresh rate is controlled. App FPS may be lower.",11,MUTED,false,18);
        setContentView(scroll);
        renderChoices();
    }

    private void renderChoices(){
        for(int i=0;i<rateOptions.length;i++){
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
        if(d!=null){rateValue.setText(String.format(Locale.US,"%.0f",d.getRefreshRate()));}
        renderChoices();
    }
    private void checkRoot(){
        executor.execute(()->{
            String out=runSu("id");
            boolean ok=out.contains("uid=0");
            runOnUiThread(()->{
                rootReady=ok;
                rootLabel.setText(ok?"GRANTED":"NOT GRANTED");
                rootLabel.setTextColor(ok?ACCENT:0xFFFF9B9B);
                rootDetail.setText(ok?"Root shell verified. Select a refresh rate to apply.":"Root access was not granted. Approve the superuser request in Magisk / KernelSU.");
                resultLabel.setText(ok?"• Root shell verified. Ready to apply a display mode.":"• Root access not granted. Check your superuser manager.");
                renderChoices();refreshDisplay();loadBrightness();
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
                String cmd="settings put secure user_refresh_rate 1"+
                    " && settings put secure miui_refresh_rate "+hz+
                    " && cmd display set-user-preferred-display-mode "+mode.getPhysicalWidth()+" "+mode.getPhysicalHeight()+" "+hz+" 0";
                CommandResult applied=execRoot(cmd);
                if(applied.exit!=0||applied.timeout)throw new RuntimeException("Apply command failed: "+applied.output);
                Thread.sleep(900);
                CommandResult verify=execRoot("dumpsys display");
                String dump=verify.output;
                java.util.regex.Matcher active=java.util.regex.Pattern.compile("mActiveRenderFrameRate=([0-9]+(?:\\\\.[0-9]+)?)").matcher(dump);
                boolean confirmed=false;
                while(active.find()){try{if(Math.abs(Float.parseFloat(active.group(1))-hz)<1.2f){confirmed=true;break;}}catch(Exception ignored){}}
                if(!confirmed){java.util.regex.Matcher peak=java.util.regex.Pattern.compile("peakRefreshRate=([0-9]+(?:\\\\.[0-9]+)?)").matcher(dump);while(peak.find()){try{if(Math.abs(Float.parseFloat(peak.group(1))-hz)<1.2f){confirmed=true;break;}}catch(Exception ignored){}}}
                message=confirmed?"Applied. Display diagnostics contain the requested rate.":"Command accepted, but active refresh rate could not be confirmed. Check the live display mode.";
                lastLog=message;
            }catch(Exception e){message="FAILED: "+e.getMessage();lastLog=message;}
            final String m=message;
            runOnUiThread(()->{busy=false;resultLabel.setText(m);renderChoices();refreshDisplay();});
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

    @Override protected void onResume(){super.onResume();if(rateValue!=null){refreshDisplay();if(rootReady)loadBrightness();}}
    @Override protected void onDestroy(){super.onDestroy();executor.shutdownNow();}
}
