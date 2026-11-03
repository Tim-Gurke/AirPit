package de.airpit.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;

/** Keeps the user-started Bluetooth session visible and controllable outside the Activity. */
public final class ControlService extends Service {
    static ControlService active;
    private AirSession session;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable notificationUpdate=()->{if(active==this)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(7,notification());};
    @Override public void onCreate(){super.onCreate();active=this;session=AirSession.get(this);NotificationChannel c=new NotificationChannel("control","AirPit-Steuerung",NotificationManager.IMPORTANCE_LOW);c.setDescription("Bluetooth-Verbindung und Steuerung beenden");((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        Notification n=notification();if(Build.VERSION.SDK_INT>=29)startForeground(7,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);else startForeground(7,n);
        session.serviceRunning=true;session.updatePower();
        if(intent!=null&&"END".equals(intent.getAction())){session.ended();stopSelf();return START_NOT_STICKY;}
        if(intent!=null&&"PAUSE".equals(intent.getAction()))session.pauseAll();
        return START_NOT_STICKY;
    }
    private Notification notification(){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pause=PendingIntent.getService(this,1,new Intent(this,ControlService.class).setAction("PAUSE"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent end=PendingIntent.getService(this,2,new Intent(this,ControlService.class).setAction("END"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        String state=session!=null&&session.hid.connected()?"Verbunden · "+(session.airOn?"Air-Maus aktiv":"Steuerung pausiert"):"Bluetooth bereit";
        return new Notification.Builder(this,"control").setSmallIcon(R.drawable.ic_airpit_monochrome).setContentTitle("AirPit · "+state).setContentText("Antippen zum Öffnen").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(null,"Steuerung stoppen",pause).build()).addAction(new Notification.Action.Builder(null,"Beenden",end).build()).build();
    }
    void refresh(){main.removeCallbacks(notificationUpdate);main.postDelayed(notificationUpdate,180);}
    @Override public void onDestroy(){active=null;main.removeCallbacksAndMessages(null);if(session!=null)session.ended();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
