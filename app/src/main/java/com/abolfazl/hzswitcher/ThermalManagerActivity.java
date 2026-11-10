package com.abolfazl.hzswitcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Scene-compatible MIUI thermal profile selector.
 * Profile names and supported device combinations are discovered from Scene's
 * own per-platform/per-device options endpoint; no generic thermal values are guessed.
 */
public final class ThermalManagerActivity extends Activity {
    private static final int BG=0xFF08090B, PANEL=0xFF191A1D, TEXT=0xFFF5F3FA,
        MUTED=0xFFA7A1B3, ACCENT=0xFFB58CFF;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout root, options;
    private TextView status, deviceInfo;
    private final ArrayList<Profile> profiles=new ArrayList<>();
    private String platform="", device="";
    private boolean busy=false;

    static final class Profile {
        final String id,label;
        Profile(String i,String l){id=i;label=l;}
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF000000);
        getWindow().setNavigationBarColor(0xFF101010);
        build();
        discover();
    }

    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable shape(int color,int radius,int stroke){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));
        if(stroke!=0)d.setStroke(dp(1),stroke);return d;
    }
    private TextView text(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return t;
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private void build(){
        root=column();root.setBackgroundColor(BG);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18),dp(14),dp(18),dp(12));
        TextView back=text("‹",34,TEXT,false);header.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));
        back.setOnClickListener(v->finish());
        TextView title=text("Thermal Manager",25,TEXT,false);
        title.setTypeface(Typeface.create("sans-serif-light",0));header.addView(title);
        root.addView(header);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
        LinearLayout body=column();body.setPadding(dp(18),dp(8),dp(18),dp(24));
        LinearLayout info=column();info.setPadding(dp(16),dp(16),dp(16),dp(16));
        info.setBackground(shape(0xFF211A2D,22,0xFF493765));
        info.addView(text("DEVICE-SPECIFIC THERMAL PROFILES",10,ACCENT,true));
        deviceInfo=text("Detecting device…",13,TEXT,false);
        LinearLayout.LayoutParams dip=new LinearLayout.LayoutParams(-1,-2);dip.topMargin=dp(8);info.addView(deviceInfo,dip);
        status=text("Checking root access and Scene profile support…",12,MUTED,false);
        LinearLayout.LayoutParams stp=new LinearLayout.LayoutParams(-1,-2);stp.topMargin=dp(8);info.addView(status,stp);
        body.addView(info);
        TextView label=text("AVAILABLE PROFILES",10,MUTED,true);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(22);lp.bottomMargin=dp(10);body.addView(label,lp);
        options=column();body.addView(options);
        TextView note=text("Profiles are fetched for this exact board platform and device, following Scene’s thermal_conf3 method. If the device is unsupported, no profile will be applied.",11,MUTED,false);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(14);body.addView(note,np);
        scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }

    private void discover(){
        status.setText("Checking root and fetching device-specific profiles…");
        worker.execute(()->{
            RootHardwareController.Result r=RootHardwareController.run(
                "printf 'ROOT='; id -u; printf '\\nPLATFORM='; getprop ro.board.platform | tr 'A-Z' 'a-z'; printf '\\nDEVICE='; getprop ro.product.device; "+
                "printf '\\nCURL='; command -v curl 2>/dev/null || true; printf '\\nWGET='; command -v wget 2>/dev/null || true; printf '\\nTOYBOX='; command -v toybox 2>/dev/null || true",8000);
            String out=r.output;
            String p=field(out,"PLATFORM"),d=field(out,"DEVICE");
            boolean rootOk=field(out,"ROOT").equals("0");
            String profileOut="",fetchError="";
            if(rootOk&&!p.isEmpty()&&!d.isEmpty()){
                String url="https://vtools.oss-cn-beijing.aliyuncs.com/Scene-Online/Thermal/"+safe(p)+"/"+safe(d)+"/options.ini";
                String script="U='"+url+"'; T=/data/local/tmp/pulsext-scene-options.txt; rm -f \\"$T\\"; "+
                    "if command -v curl >/dev/null 2>&1; then curl -LsS --connect-timeout 5 --max-time 15 \\"$U\\" -o \\"$T\\" 2>&1 || true; "+
                    "elif command -v wget >/dev/null 2>&1; then wget -q -T 15 -O \\"$T\\" \\"$U\\" 2>&1 || true; "+
                    "elif command -v toybox >/dev/null 2>&1; then toybox wget -q -T 15 -O \\"$T\\" \\"$U\\" 2>&1 || true; "+
                    "else echo FETCH_ERROR:no_curl_or_wget; exit 21; fi; "+
                    "if [ -s \\"$T\\" ]; then if grep -qi 'Error' \\"$T\\"; then echo 'default|System Default'; else cat \\"$T\\"; fi; "+
                    "else echo FETCH_ERROR:empty_response_or_network_error; fi";
                RootHardwareController.Result fetched=RootHardwareController.run(script,22000);
                profileOut=fetched.output;
                if(!fetched.ok())fetchError="Scene profile request failed (exit "+fetched.exitCode+")";
            } else fetchError="Root access or device properties unavailable";
            final String fp=p,fd=d,fo=profileOut,fe=fetchError;final boolean fr=rootOk;
            runOnUiThread(()->{
                platform=fp;device=fd;
                deviceInfo.setText("Platform: "+(fp.isEmpty()?"unknown":fp)+"   •   Device: "+(fd.isEmpty()?"unknown":fd));
                profiles.clear();
                if(fr){
                    for(String line:fo.split("\\r?\\n")){
                        String[] pair=line.trim().split("\\|",2);
                        if(pair.length<1)continue;
                        String id=pair[0].trim();
                        if(!id.matches("[A-Za-z0-9_-]+"))continue;
                        String label=pair.length>1?pair[1].trim():"";
                        profiles.add(new Profile(id,displayName(id,label)));
                    }
                }
                renderOptions();
                if(!fr)status.setText("Root access is required. Grant AutoHz root permission and retry.");
                else if(fo.startsWith("FETCH_ERROR:"))status.setText(fe+": "+fo.substring("FETCH_ERROR:".length())+". Device: "+fp+"/"+fd+". Nothing was changed.");
                else if(profiles.isEmpty())status.setText("Scene returned no usable profiles for "+fp+"/"+fd+". Response: "+(fo.isEmpty()?"<empty>":fo.substring(0,Math.min(90,fo.length())))+". Nothing was changed.");
                else status.setText("Scene profile list loaded for this device. Select a profile to apply.");
            });
        });
    }

    private String field(String output,String key){
        for(String line:output.split("\\r?\\n"))if(line.startsWith(key+"="))return line.substring(key.length()+1).trim();
        return "";
    }
    private String safe(String s){return s.matches("[A-Za-z0-9_.-]+")?s:"";}
    private String displayName(String id,String source){
        String x=id.toLowerCase(Locale.ROOT);
        if(x.equals("default"))return "System Default";
        if(x.equals("cool"))return "Cool";
        if(x.equals("pro"))return "Pro";
        if(x.equals("extreme"))return "Extreme";
        if(x.equals("danger"))return "Danger";
        if(source.startsWith("@string:"))return id;
        return source.isEmpty()?id:source;
    }

    private void renderOptions(){
        options.removeAllViews();
        for(Profile profile:profiles){
            LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(16),dp(15),dp(16),dp(15));card.setBackground(shape(PANEL,18,0xFF292B30));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(68));cp.bottomMargin=dp(9);options.addView(card,cp);
            LinearLayout labels=column();card.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
            String subtitle=profile.id.equalsIgnoreCase("default")?"Restore the stock thermal configuration":"Device-specific profile provided by Scene";
            labels.addView(text(profile.label,16,TEXT,true));TextView sub=text(subtitle,11,MUTED,false);
            LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(3);labels.addView(sub,sp);
            TextView arrow=text("›",27,ACCENT,false);card.addView(arrow);
            card.setOnClickListener(v->confirm(profile));
        }
    }

    private void confirm(Profile profile){
        if(busy)return;
        new AlertDialog.Builder(this).setTitle(profile.label)
            .setMessage(profile.id.equalsIgnoreCase("default")
                ?"Restore the original thermal configuration backed up before the first profile change?"
                :"Apply the Scene thermal profile for "+device+"? Original configuration files will be backed up first.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Apply",(d,w)->apply(profile)).show();
    }

    private void apply(Profile profile){
        busy=true;status.setText("Applying "+profile.label+"…");
        worker.execute(()->{
            String script;
            if(profile.id.equalsIgnoreCase("default"))script=restoreScript();
            else script=applyScript(profile.id);
            RootHardwareController.Result result=RootHardwareController.run(script,90000);
            runOnUiThread(()->{
                busy=false;
                status.setText(result.ok()?"Applied and verified: "+profile.label:
                    "Could not apply profile: "+result.output);
            });
        });
    }

    /**
     * Mirrors Scene thermal_conf3: getprop platform/device -> per-device
     * options.ini -> thermal_files.ini + mode/files.ini -> populate
     * /data/vendor/thermal/config -> restart mi_thermald/thermal-engine.
     * Unlike Scene's cleanup, only files listed for this device are replaced,
     * and original files are backed up once so System Default can restore them.
     */
    private String applyScript(String mode){
        String p=safe(platform),d=safe(device),m=safe(mode);
        return "set -eu; P='"+p+"'; D='"+d+"'; M='"+m+"'; "+
            "BASE=\"/data/adb/pulsext/thermal-backup/$D\"; "+
            "DIR='/data/vendor/thermal/config'; U='https://vtools.oss-cn-beijing.aliyuncs.com/Scene-Online/Thermal'; "+
            "test \"$(id -u)\" = 0; test -n \"$P\"; test -n \"$D\"; "+
            "command -v curl >/dev/null 2>&1 || { echo curl_missing; exit 10; }; "+
            "mkdir -p \"$BASE/files\" \"$DIR\"; "+
            "curl -fLsS --max-time 20 \"$U/$P/$D/thermal_files.ini\" -o \"$BASE/thermal_files.ini\"; "+
            "curl -fLsS --max-time 20 \"$U/$P/$D/$M/files.ini\" -o \"$BASE/profile_files.ini\"; "+
            "grep -q '^thermal' \"$BASE/thermal_files.ini\" || { echo invalid_device_file_list; exit 11; }; "+
            "while IFS= read -r F; do case \"$F\" in thermal*.conf) ;; *) continue;; esac; "+
            "if [ ! -e \"$BASE/files/$F.saved\" ]; then if [ -f \"$DIR/$F\" ]; then cp -p \"$DIR/$F\" \"$BASE/files/$F.saved\"; else : > \"$BASE/files/$F.absent\"; fi; fi; done < \"$BASE/thermal_files.ini\"; "+
            "mkdir -p \"$BASE/download\"; "+
            "while IFS= read -r F; do case \"$F\" in thermal*.conf) ;; *) continue;; esac; "+
            "curl -fLsS --max-time 20 \"$U/$P/$D/$M/$F\" -o \"$BASE/download/$F\"; "+
            "test -s \"$BASE/download/$F\"; "+
            "cp \"$BASE/download/$F\" \"$DIR/$F\"; chmod 0444 \"$DIR/$F\"; done < \"$BASE/profile_files.ini\"; "+
            "printf '%s\\n' \"$M\" > \"$DIR/thermal.current.ini\"; "+
            "chown -R root:system \"$DIR\" 2>/dev/null || true; restorecon -RF \"$DIR\" 2>/dev/null || true; "+
            "for S in mi_thermald thermal-engine; do if command -v \"$S\" >/dev/null 2>&1; then stop \"$S\" 2>/dev/null || true; start \"$S\" 2>/dev/null || true; fi; done; "+
            "echo applied:$M";
    }

    private String restoreScript(){
        String d=safe(device);
        return "set -eu; D='"+d+"'; BASE=\"/data/adb/pulsext/thermal-backup/$D\"; DIR='/data/vendor/thermal/config'; "+
            "test -d \"$BASE/files\" || { echo no_backup_found; exit 20; }; "+
            "while IFS= read -r F; do case \"$F\" in thermal*.conf) ;; *) continue;; esac; "+
            "if [ -f \"$BASE/files/$F.saved\" ]; then cp -p \"$BASE/files/$F.saved\" \"$DIR/$F\"; "+
            "elif [ -f \"$BASE/files/$F.absent\" ]; then rm -f \"$DIR/$F\"; fi; done < \"$BASE/thermal_files.ini\"; "+
            "rm -f \"$DIR/thermal.current.ini\"; restorecon -RF \"$DIR\" 2>/dev/null || true; "+
            "for S in mi_thermald thermal-engine; do if command -v \"$S\" >/dev/null 2>&1; then stop \"$S\" 2>/dev/null || true; start \"$S\" 2>/dev/null || true; fi; done; echo restored_original";
    }

    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
