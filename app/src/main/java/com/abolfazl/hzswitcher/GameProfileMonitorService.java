package com.abolfazl.hzswitcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

public final class GameProfileMonitorService extends Service {
    private static final String TAG="PulseXtGameMonitor";
    private static final String PREFS="game_profiles";
    private static final String EXTRA_PACKAGE="game_package";
    private static final String ACTIVE_PACKAGE="monitor_active_package";
    private static final String CHANNEL="game_profile_monitor";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String gamePackage;
    private boolean seenGame;
    private boolean checking;
    private final Runnable poll=new Runnable(){
        @Override public void run(){
            if(checking||gamePackage==null)return;
            checking=true;
            new Thread(()->{
                RootHardwareController.Result r=RootHardwareController.run(
                    "(dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|topResumedActivity' || true); "+
                    "(dumpsys window 2>/dev/null | grep -m1 mCurrentFocus || true); exit 0",5000);
                boolean foreground=r.output.contains(gamePackage);
                Log.d(TAG,"foreground="+foreground+" package="+gamePackage+" output="+r.output);
                handler.post(()->{
                    checking=false;
                    if(foreground){
                        if(!seenGame){
                            seenGame=true;
                            getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                                .putBoolean("monitor_seen_game",true).apply();
                            new Thread(()->applyGameHardwareProfile(gamePackage),"PulseXtHardwareApply").start();
                        }
                        handler.postDelayed(this,1000);
                    }else if(seenGame){
                        restoreAndFinish();
                    }else{
                        handler.postDelayed(this,1000);
                    }
                });
            },"PulseXtGameProbe").start();
        }
    };

    @Override public void onCreate(){
        super.onCreate();
        createChannel();
        startForeground(7301,buildNotification("Waiting for selected game"));
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        android.content.SharedPreferences prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        if(intent!=null&&intent.hasExtra(EXTRA_PACKAGE)){
            gamePackage=intent.getStringExtra(EXTRA_PACKAGE);
            seenGame=false;
            prefs.edit().putString(ACTIVE_PACKAGE,gamePackage).putBoolean("monitor_seen_game",false).apply();
        }else{
            gamePackage=prefs.getString(ACTIVE_PACKAGE,null);
            seenGame=prefs.getBoolean("monitor_seen_game",false);
        }
        if(gamePackage==null||!prefs.getBoolean(gamePackage+".restore_pending",false)){
            stopSelf();
            return START_NOT_STICKY;
        }
        handler.removeCallbacks(poll);
        handler.post(poll);
        return START_STICKY;
    }

    /** Apply only controls enabled by the saved game profile, using live kernel bounds. */
    private void applyGameHardwareProfile(String pkg){
        android.content.SharedPreferences prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        java.util.List<RootHardwareController.CpuPolicy> policies=RootHardwareController.discoverCpuPolicies();
        String[] ids={"little","big","prime"};
        for(int i=0;i<policies.size()&&i<ids.length;i++){
            RootHardwareController.CpuPolicy p=policies.get(i);
            String id=ids[i],prefix=pkg+".";
            boolean minOn=prefs.getBoolean(prefix+id+"_min_enabled",false);
            boolean maxOn=prefs.getBoolean(prefix+id+"_max_enabled",false);
            if(!minOn&&!maxOn)continue;
            String key="hw_"+id+"_"+p.path.substring(p.path.lastIndexOf('/')+1);
            prefs.edit().putLong(pkg+".restore_"+key+"_min",p.minKhz)
                .putLong(pkg+".restore_"+key+"_max",p.maxKhz)
                .putString(pkg+".restore_"+key+"_gov",p.governor).apply();
            long min=minOn?(long)prefs.getInt(prefix+id+"_min_mhz",0)*1000L:p.minKhz;
            long max=maxOn?(long)prefs.getInt(prefix+id+"_max_mhz",0)*1000L:p.maxKhz;
            min=Math.max(p.hardwareMinKhz,Math.min(min,p.hardwareMaxKhz));
            max=Math.max(p.hardwareMinKhz,Math.min(max,p.hardwareMaxKhz));
            if(min>max){Log.e(TAG,"Invalid "+id+" CPU limits; skip "+p.path);continue;}
            RootHardwareController.Result result=RootHardwareController.setCpuLimits(p.path,min,max);
            Log.i(TAG,"CPU profile "+p.path+" result="+result.output+" exit="+result.exitCode);
            String governor=prefs.getString(prefix+id+"_governor","");
            if(!governor.isEmpty()){
                RootHardwareController.Result gr=RootHardwareController.setCpuGovernor(p.path,governor);
                Log.i(TAG,"CPU governor "+p.path+" result="+gr.output+" exit="+gr.exitCode);
            }
        }
        java.util.List<RootHardwareController.GpuDevice> gpus=RootHardwareController.discoverGpuDevices();
        if(!gpus.isEmpty()){
            RootHardwareController.GpuDevice gpu=gpus.get(0);
            boolean minOn=prefs.getBoolean(pkg+".gpu_min_enabled",false);
            boolean maxOn=prefs.getBoolean(pkg+".gpu_max_enabled",false);
            if(minOn||maxOn){
                String id="restore_gpu_"+gpu.path.hashCode();
                prefs.edit().putLong(pkg+"."+id+"_min",gpu.minHz)
                    .putLong(pkg+"."+id+"_max",gpu.maxHz)
                    .putString(pkg+"."+id+"_gov",gpu.governor).apply();
                long min=minOn?(long)prefs.getInt(pkg+".gpu_min_mhz",0)*1000000L:gpu.minHz;
                long max=maxOn?(long)prefs.getInt(pkg+".gpu_max_mhz",0)*1000000L:gpu.maxHz;
                RootHardwareController.Result gr=RootHardwareController.setGpuLimits(gpu,min,max);
                Log.i(TAG,"GPU profile result="+gr.output+" exit="+gr.exitCode);
            }
            String governor=prefs.getString(pkg+".gpu_governor","");
            if(!governor.isEmpty()){
                RootHardwareController.Result gr=RootHardwareController.setGpuGovernor(gpu,governor);
                Log.i(TAG,"GPU governor result="+gr.output+" exit="+gr.exitCode);
            }
        }
    }

    private boolean restoreHardwareProfile(String pkg){
        android.content.SharedPreferences prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        boolean ok=true;
        java.util.List<RootHardwareController.CpuPolicy> policies=RootHardwareController.discoverCpuPolicies();
        String[] ids={"little","big","prime"};
        for(int i=0;i<policies.size()&&i<ids.length;i++){
            RootHardwareController.CpuPolicy p=policies.get(i);
            String key="hw_"+ids[i]+"_"+p.path.substring(p.path.lastIndexOf('/')+1);
            long min=prefs.getLong(pkg+".restore_"+key+"_min",-1);
            long max=prefs.getLong(pkg+".restore_"+key+"_max",-1);
            String gov=prefs.getString(pkg+".restore_"+key+"_gov","");
            if(min>=p.hardwareMinKhz&&max<=p.hardwareMaxKhz&&min<=max){
                RootHardwareController.Result r=RootHardwareController.setCpuLimits(p.path,min,max);
                if(!r.ok()){ok=false;Log.e(TAG,"CPU restore failed "+p.path+": "+r.output);}
            }
            if(!gov.isEmpty()){
                RootHardwareController.Result r=RootHardwareController.setCpuGovernor(p.path,gov);
                if(!r.ok()){ok=false;Log.e(TAG,"CPU governor restore failed "+p.path+": "+r.output);}
            }
        }
        java.util.List<RootHardwareController.GpuDevice> gpus=RootHardwareController.discoverGpuDevices();
        if(!gpus.isEmpty()){
            RootHardwareController.GpuDevice gpu=gpus.get(0);
            String id="restore_gpu_"+gpu.path.hashCode();
            long min=prefs.getLong(pkg+"."+id+"_min",-1),max=prefs.getLong(pkg+"."+id+"_max",-1);
            String gov=prefs.getString(pkg+"."+id+"_gov","");
            if(min>0&&max>=min){
                RootHardwareController.Result r=RootHardwareController.setGpuLimits(gpu,min,max);
                if(!r.ok()){ok=false;Log.e(TAG,"GPU restore failed: "+r.output);}
            }
            if(!gov.isEmpty()){
                RootHardwareController.Result r=RootHardwareController.setGpuGovernor(gpu,gov);
                if(!r.ok()){ok=false;Log.e(TAG,"GPU governor restore failed: "+r.output);}
            }
        }
        return ok;
    }

    private void restoreAndFinish(){
        if(!getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean(gamePackage+".restore_pending",false)){
            stopSelf();return;
        }
        new Thread(()->{
            android.content.SharedPreferences prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
            int brightness=prefs.getInt(gamePackage+".restore_brightness",-1);
            int mode=prefs.getInt(gamePackage+".restore_brightness_mode",-1);
            int batterySaver=prefs.getInt(gamePackage+".restore_battery_saver",-1);
            int volume=prefs.getInt(gamePackage+".restore_volume",-1);
            int volumeMax=prefs.getInt(gamePackage+".restore_volume_max",-1);
            boolean displayOk=false;
            if(brightness>=0&&brightness<=255&&(mode==0||mode==1)){
                RootHardwareController.Result display=RootHardwareController.run(
                    "settings --user 0 put system screen_brightness_mode 0 && "+
                    "settings --user 0 put system screen_brightness "+brightness+" && "+
                    "settings --user 0 put system screen_brightness_mode "+mode+" && "+
                    "[ \"$(settings --user 0 get system screen_brightness)\" = \""+brightness+"\" ] && "+
                    "[ \"$(settings --user 0 get system screen_brightness_mode)\" = \""+mode+"\" ]",7000);
                displayOk=display.ok();
                if(!displayOk)Log.e(TAG,"Brightness restore failed: "+display.output);
            }else Log.e(TAG,"Saved brightness state invalid");
            boolean volumeOk=false;
            if(volume>=0&&volumeMax>0){
                try{
                    AudioManager audio=(AudioManager)getSystemService(Context.AUDIO_SERVICE);
                    int target=Math.max(0,Math.min(volume,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)));
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC,target,0);
                    volumeOk=audio.getStreamVolume(AudioManager.STREAM_MUSIC)==target;
                    if(!volumeOk)Log.e(TAG,"Volume restore mismatch target="+target+" actual="+audio.getStreamVolume(AudioManager.STREAM_MUSIC));
                }catch(Exception e){Log.e(TAG,"Volume restore failed",e);}
            }else Log.e(TAG,"Saved volume state invalid");
            final boolean displayRestored=displayOk;
            final boolean volumeRestored=volumeOk;
            boolean batterySaverRestored=false;
            if(batterySaver==0||batterySaver==1){
                String low=batterySaver==1?"1":"0";
                String restorePowerScript="cmd power set-mode "+low+" && sleep 1 && settings put global low_power "+low;
                if(batterySaver==0)restorePowerScript+=" && settings put global low_power_sticky 0 && settings put global low_power 0";
                restorePowerScript+=" && sleep 1 && [ \"$(settings get global low_power)\" = \""+low+"\" ]";
                RootHardwareController.Result power=RootHardwareController.run(restorePowerScript,7000);
                batterySaverRestored=power.ok();
                if(!batterySaverRestored)Log.e(TAG,"Battery Saver restore failed: "+power.output);
            }
            final boolean batterySaverRestoredFinal = (batterySaver == 0 || batterySaver == 1) && batterySaverRestored;
            final boolean hardwareRestored=restoreHardwareProfile(gamePackage);
            final boolean success = displayRestored && volumeRestored && batterySaverRestoredFinal && hardwareRestored;
            if(success){
                prefs.edit().putBoolean(gamePackage+".restore_pending",false)
                    .putBoolean("monitor_seen_game",false).remove(ACTIVE_PACKAGE).apply();
            }
            handler.post(()->{
                if(success){
                    Log.i(TAG,"Original game settings restored for "+gamePackage);
                    stopSelf();
                }else{
                    Log.w(TAG,"Restore incomplete; retrying displayOk="+displayRestored+" volumeOk="+volumeRestored+" batterySaverOk="+batterySaverRestoredFinal+" hardwareOk="+hardwareRestored);
                    handler.postDelayed(()->restoreAndFinish(),2500);
                }
            });
        },"PulseXtGameRestore").start();
    }

    private Notification buildNotification(String text){
        if(Build.VERSION.SDK_INT>=26){
            return new Notification.Builder(this,CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentTitle("PulseXt Game Profile")
                .setContentText(text)
                .setOngoing(true).build();
        }
        return new Notification.Builder(this)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("PulseXt Game Profile")
            .setContentText(text).setOngoing(true).build();
    }

    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel channel=new NotificationChannel(CHANNEL,"Game profile monitor",NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Restores your original display and volume settings after a game closes.");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    @Override public void onTaskRemoved(Intent rootIntent){
        super.onTaskRemoved(rootIntent);
        // Keep monitoring the explicitly started game session.
    }

    @Override public void onDestroy(){
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent){return null;}
}
