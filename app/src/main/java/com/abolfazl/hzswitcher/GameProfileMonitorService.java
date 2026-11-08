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
                        seenGame=true;
                        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                            .putBoolean("monitor_seen_game",true).apply();
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

    private void restoreAndFinish(){
        if(!getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean(gamePackage+".restore_pending",false)){
            stopSelf();return;
        }
        new Thread(()->{
            android.content.SharedPreferences prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
            int brightness=prefs.getInt(gamePackage+".restore_brightness",-1);
            int mode=prefs.getInt(gamePackage+".restore_brightness_mode",-1);
            int volume=prefs.getInt(gamePackage+".restore_volume",-1);
            int volumeMax=prefs.getInt(gamePackage+".restore_volume_max",-1);
            boolean displayOk=false;
            if(brightness>=0&&brightness<=255&&(mode==0||mode==1)){
                RootHardwareController.Result display=RootHardwareController.run(
                    "settings put system screen_brightness_mode 0 && "+
                    "settings put system screen_brightness "+brightness+" && "+
                    "settings put system screen_brightness_mode "+mode+" && "+
                    "[ \"$(settings get system screen_brightness)\" = \""+brightness+"\" ] && "+
                    "[ \"$(settings get system screen_brightness_mode)\" = \""+mode+"\" ]",7000);
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
            boolean success=displayOk&&volumeOk;
            if(success){
                prefs.edit().putBoolean(gamePackage+".restore_pending",false)
                    .putBoolean("monitor_seen_game",false).remove(ACTIVE_PACKAGE).apply();
            }
            handler.post(()->{
                if(success){
                    Log.i(TAG,"Original game settings restored for "+gamePackage);
                    stopSelf();
                }else{
                    Log.w(TAG,"Restore incomplete; retrying displayOk="+displayOk+" volumeOk="+volumeOk);
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
