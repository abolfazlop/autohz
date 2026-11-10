package com.abolfazl.hzswitcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public final class GameProfileBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if(intent==null)return;
        String action=intent.getAction();
        if(!Intent.ACTION_BOOT_COMPLETED.equals(action)&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))return;
        android.content.SharedPreferences prefs=context.getSharedPreferences("game_profiles",Context.MODE_PRIVATE);
        if(!prefs.getBoolean("auto_monitor_enabled",false))return;
        boolean any=false;
        for(java.util.Map.Entry<String,?> entry:prefs.getAll().entrySet()){
            if(entry.getKey().endsWith(".auto")&&Boolean.TRUE.equals(entry.getValue())){any=true;break;}
        }
        if(!any)return;
        try{
            Intent service=new Intent(context,GameProfileMonitorService.class);
            service.setAction(GameProfileMonitorService.ACTION_AUTO);
            if(Build.VERSION.SDK_INT>=26)context.startForegroundService(service);
            else context.startService(service);
        }catch(Exception e){Log.e("PulseXtGameMonitor","Could not restart game monitor after boot",e);}
    }
}
