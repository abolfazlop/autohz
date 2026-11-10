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
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

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

    private static final String SCENE_BASE="https://vtools.oss-cn-beijing.aliyuncs.com/Scene-Online/Thermal";

    private String httpGet(String address) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
        connection.setConnectTimeout(10000); connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent","AutoHz-ThermalManager/1.0");
        try {
            int code=connection.getResponseCode();
            InputStream stream=(code>=200&&code<300)?connection.getInputStream():connection.getErrorStream();
            if(stream==null) throw new IOException("HTTP "+code);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            byte[] buffer=new byte[8192]; int n;
            while((n=stream.read(buffer))!=-1) bytes.write(buffer,0,n);
            String body=new String(bytes.toByteArray(),StandardCharsets.UTF_8).trim();
            if(code<200||code>=300) throw new IOException("HTTP "+code+": "+body);
            return body;
        } finally { connection.disconnect(); }
    }

    private File thermalWorkDir() throws IOException {
        File base=getExternalFilesDir("thermal-manager");
        if(base==null) throw new IOException("External app storage is unavailable");
        if(!base.exists()&&!base.mkdirs()) throw new IOException("Cannot create thermal staging directory");
        return base;
    }

    private void writeText(File file,String value) throws IOException {
        try(FileOutputStream out=new FileOutputStream(file,false)){
            out.write(value.getBytes(StandardCharsets.UTF_8)); out.flush(); out.getFD().sync();
        }
    }

    private void downloadTo(String address,File target) throws Exception {
        String body=httpGet(address);
        if(body.isEmpty()||body.toLowerCase(Locale.ROOT).contains("<error")||
            body.toLowerCase(Locale.ROOT).startsWith("error")) throw new IOException("Scene returned an error for "+target.getName());
        writeText(target,body);
    }

    private void discover(){
        status.setText("Detecting device and connecting to Scene…");
        worker.execute(()->{
            RootHardwareController.Result r=RootHardwareController.run(
                "printf 'ROOT='; id -u; printf '\\nPLATFORM='; getprop ro.board.platform | tr 'A-Z' 'a-z'; printf '\\nDEVICE='; getprop ro.product.device",8000);
            String out=r.output;
            String p=field(out,"PLATFORM"),d=field(out,"DEVICE");
            boolean rootOk=field(out,"ROOT").equals("0");
            String profileOut="",fetchError="";
            if(!p.isEmpty()&&!d.isEmpty()){
                try {
                    profileOut=httpGet(SCENE_BASE+"/"+safe(p)+"/"+safe(d)+"/options.ini");
                    if(profileOut.toLowerCase(Locale.ROOT).contains("error"))
                        profileOut="default|@string:kr_system_default";
                } catch(Exception e) {
                    fetchError="Scene connection failed: "+e.getMessage();
                }
            } else fetchError="Could not read board platform/device properties";
            final String fp=p,fd=d,fo=profileOut,fe=fetchError;final boolean fr=rootOk;
            runOnUiThread(()->{
                platform=fp;device=fd;
                deviceInfo.setText("Platform: "+(fp.isEmpty()?"unknown":fp)+"   •   Device: "+(fd.isEmpty()?"unknown":fd));
                profiles.clear();
                for(String line:fo.split("\\r?\\n")){
                    String[] pair=line.trim().split("\\|",2);
                    if(pair.length<1)continue;
                    String id=pair[0].trim();
                    if(!id.matches("[A-Za-z0-9_-]+"))continue;
                    String label=pair.length>1?pair[1].trim():"";
                    profiles.add(new Profile(id,displayName(id,label)));
                }
                renderOptions();
                if(!fr)status.setText("Root access is required to apply profiles. Grant AutoHz root permission.");
                else if(!fe.isEmpty())status.setText(fe+". Nothing was changed.");
                else if(profiles.isEmpty())status.setText("Scene has no published profile list for "+fp+"/"+fd+". Nothing was changed.");
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
        busy=true;status.setText("Preparing "+profile.label+"…");
        worker.execute(()->{
            RootHardwareController.Result result;
            try {
                String script;
                if(profile.id.equalsIgnoreCase("default")) script=restoreScript();
                else script=prepareAndApply(profile.id);
                result=RootHardwareController.run(script,90000);
            } catch(Exception e) {
                result=RootHardwareController.Result.failure("Download failed: "+e.getMessage());
            }
            final RootHardwareController.Result finalResult=result;
            runOnUiThread(()->{
                busy=false;
                status.setText(finalResult.ok()?"Applied: "+profile.label:
                    "Could not apply profile: "+finalResult.output);
            });
        });
    }

    /**
     * Scene thermal_conf3 download.sh/config.sh sequence, but HTTP downloads
     * run in Android's Java network stack instead of assuming curl/wget exists
     * in the device shell (many MIUI Toybox builds omit both).
     */
    private String prepareAndApply(String mode) throws Exception {
        String p=safe(platform),d=safe(device),m=safe(mode);
        if(p.isEmpty()||d.isEmpty()||m.isEmpty()) throw new IOException("Invalid device/profile identifier");
        File work=thermalWorkDir();
        File backupList=new File(work,"thermal_files.ini");
        File profileList=new File(work,"profile_files.ini");
        downloadTo(SCENE_BASE+"/"+p+"/"+d+"/thermal_files.ini",backupList);
        downloadTo(SCENE_BASE+"/"+p+"/"+d+"/"+m+"/files.ini",profileList);
        List<String> thermalFiles=readSafeFileList(backupList);
        List<String> profileFiles=readSafeFileList(profileList);
        if(thermalFiles.isEmpty()||profileFiles.isEmpty()) throw new IOException("Scene file list is empty or invalid");
        File staged=new File(work,"profile_"+m);
        if(!staged.exists()&&!staged.mkdirs()) throw new IOException("Cannot create staging directory");
        for(String name:profileFiles) {
            if(!name.startsWith("thermal")||!name.endsWith(".conf")) continue;
            downloadTo(SCENE_BASE+"/"+p+"/"+d+"/"+m+"/"+name,new File(staged,name));
        }
        StringBuilder sh=new StringBuilder("set -eu; ");
        sh.append("BASE='/data/adb/pulsext/thermal-backup/").append(d).append("'; DIR='/data/vendor/thermal/config'; STAGE='")
          .append(shellQuote(staged.getAbsolutePath())).append("'; mkdir -p \"$BASE/files\" \"$DIR\"; ");
        for(String name:thermalFiles) {
            sh.append("F='").append(name).append("'; ");
            sh.append("if [ ! -e \"$BASE/files/$F.saved\" ] && [ ! -e \"$BASE/files/$F.absent\" ]; then ");
            sh.append("if [ -f \"$DIR/$F\" ]; then cp -p \"$DIR/$F\" \"$BASE/files/$F.saved\"; else : > \"$BASE/files/$F.absent\"; fi; fi; ");
        }
        int copied=0;
        for(String name:profileFiles) {
            if(!name.startsWith("thermal")||!name.endsWith(".conf")) continue;
            File file=new File(staged,name);
            if(!file.isFile()||file.length()==0) continue;
            sh.append("cp '").append(shellQuote(file.getAbsolutePath())).append("' '")
              .append("/data/vendor/thermal/config/").append(name).append("'; chmod 0444 '/data/vendor/thermal/config/")
              .append(name).append("'; ");
            copied++;
        }
        if(copied==0) throw new IOException("Scene profile contains no thermal .conf files");
        sh.append("printf '%s\\n' '").append(m).append("' > \"$DIR/thermal.current.ini\"; ")
          .append("chown -R root:system \"$DIR\" 2>/dev/null || true; restorecon -RF \"$DIR\" 2>/dev/null || true; ")
          .append("for S in mi_thermald thermal-engine; do if command -v \"$S\" >/dev/null 2>&1; then stop \"$S\" 2>/dev/null || true; start \"$S\" 2>/dev/null || true; fi; done; echo applied:").append(m);
        return sh.toString();
    }

    private List<String> readSafeFileList(File file) throws IOException {
        ArrayList<String> names=new ArrayList<>();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8))){
            String line;
            while((line=reader.readLine())!=null){
                String name=line.trim();
                if(name.matches("thermal[A-Za-z0-9_.-]*\\.conf")&&!names.contains(name)) names.add(name);
            }
        }
        return names;
    }

    private String shellQuote(String value) { return value.replace("'","'\\''"); }

    private String restoreScript(){
        String d=safe(device);
        return "set -eu; D='"+d+"'; BASE=\"/data/adb/pulsext/thermal-backup/$D\"; DIR='/data/vendor/thermal/config'; "+
            "test -d \"$BASE/files\" || { echo no_backup_found; exit 20; }; "+
            "for F in \"$BASE\"/files/*.saved; do [ -f \"$F\" ] || continue; N=\u0024{F##*/}; N=\u0024{N%.saved}; cp -p \"$F\" \"$DIR/$N\"; done; "+
            "for F in \"$BASE\"/files/*.absent; do [ -f \"$F\" ] || continue; N=\u0024{F##*/}; N=\u0024{N%.absent}; rm -f \"$DIR/$N\"; done; "+
            "rm -f \"$DIR/thermal.current.ini\"; restorecon -RF \"$DIR\" 2>/dev/null || true; "+
            "for S in mi_thermald thermal-engine; do if command -v \"$S\" >/dev/null 2>&1; then stop \"$S\" 2>/dev/null || true; start \"$S\" 2>/dev/null || true; fi; done; echo restored_original";
    }

    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
