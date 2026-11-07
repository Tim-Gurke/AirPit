package de.airpit.app;

import android.content.*;
import android.os.*;
import de.airpit.app.core.TextDraft;
import de.airpit.app.core.MotionAxes;
import java.text.SimpleDateFormat;
import java.util.*;

/** Owns Bluetooth, motion and the in-memory draft across app switches and rotations. */
public final class AirSession {
    public interface Observer {void changed();}
    private static AirSession instance;
    public static synchronized AirSession get(Context c){if(instance==null)instance=new AirSession(c.getApplicationContext());return instance;}
    public final Context context;
    public final HidController hid;
    public final AirMotion motion;
    public final android.content.SharedPreferences prefs;
    public volatile boolean airOn;
    public boolean dragging,holding,touching,serviceRunning,visible;
    public final TextDraft textDraft=new TextDraft();
    public Observer observer;
    private PowerManager.WakeLock wake;
    private final ArrayDeque<String> events=new ArrayDeque<>();
    private String loggedState="";
    private AirSession(Context c){
        context=c;prefs=c.getSharedPreferences("airpit",0);
        motion=new AirMotion(c,new AirMotion.Sink(){public void move(int x,int y){if(airOn&&hidReady())hid.move(x,y,0);}public void calibrated(){record("Bewegungssensor kalibriert");notifyUi();}});
        hid=new HidController(c,new HidController.Listener(){
            public void changed(){
                if(!hidReady()&&airOn){airOn=false;motion.stop();dragging=false;}
                if(hid!=null&&dragging&&hid.mouseButtons()==0)dragging=false;
                if(hid!=null&&!hid.detail().equals(loggedState)){loggedState=hid.detail();record(loggedState);}
                updatePower();notifyUi();
            }
            public void textFinished(String source,boolean ok){
                if(ok){textDraft.complete(source,true);record("Text übertragen · "+source.codePointCount(0,source.length())+" Zeichen");}
                else record("Textübertragung abgebrochen · Entwurf erhalten");
                notifyUi();
            }
        });configure();
    }
    private boolean hidReady(){return hid!=null&&hid.connected();}
    public void start(){
        if(!serviceRunning){Intent i=new Intent(context,ControlService.class);if(Build.VERSION.SDK_INT>=26)context.startForegroundService(i);else context.startService(i);}
        hid.start();
    }
    public boolean keepOff(){return prefs.getBoolean("screenOff",false);}
    public void setKeepOff(boolean value){prefs.edit().putBoolean("screenOff",value).apply();updatePower();notifyUi();}
    public void toggleAir(){if(airOn){pauseAir();return;}if(!hidReady())return;configure();airOn=motion.start();holding=touching=false;motion.hold(false);record(airOn?"Air-Maus gestartet":"Bewegungssensor nicht verfügbar");updatePower();notifyUi();}
    public void pauseAir(){airOn=false;holding=touching=false;motion.stop();updatePower();notifyUi();}
    public void pauseAll(){pauseAir();dragging=false;hid.releaseAll();notifyUi();}
    public void hidden(){
        visible=false;
        PowerManager p=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
        if(!keepOff()||p==null||p.isInteractive())pauseAll();
        else{dragging=holding=touching=false;motion.hold(false);hid.releaseAll();}
        // Screen-off mode keeps only an explicitly started air session running.
    }
    public void hold(){motion.hold(holding||touching);}
    public void configure(){motion.gain=500+prefs.getInt("speed",50)*24;motion.smoothing=de.airpit.app.core.MotionFilter.smoothingSeconds(prefs.getInt("smooth",35));motion.deadzone=.004+prefs.getInt("steady",30)*.0005;motion.invertX=prefs.getBoolean("invertX",false);motion.invertY=prefs.getBoolean("invertY",false);motion.setAxes(prefs.getInt("gyroHorizontal",MotionAxes.TURN),prefs.getInt("gyroVertical",MotionAxes.FORWARD_TILT));}
    public void updatePower(){
        boolean needed=serviceRunning&&airOn&&keepOff()&&hidReady();
        if(needed&&wake==null){PowerManager p=(PowerManager)context.getSystemService(Context.POWER_SERVICE);if(p!=null){wake=p.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"AirPit:motion");wake.setReferenceCounted(false);}}
        if(wake!=null){if(needed&&!wake.isHeld())wake.acquire();else if(!needed&&wake.isHeld())wake.release();}
    }
    public void ended(){pauseAll();hid.stop();hid.unicode.close();serviceRunning=false;updatePower();notifyUi();}
    public void notifyUi(){if(observer!=null)observer.changed();if(ControlService.active!=null)ControlService.active.refresh();}
    public synchronized void record(String event){if(events.size()>=80)events.removeFirst();events.addLast(new SimpleDateFormat("HH:mm:ss",Locale.GERMANY).format(new Date())+" · "+event);}
    public synchronized String diagnostics(){return "AirPit "+BuildConfig.VERSION_NAME+"\nAndroid "+Build.VERSION.RELEASE+" · "+Build.MANUFACTURER+" "+Build.MODEL+"\nKeine Texte oder Codes im Protokoll.\n\n"+hid.mouseDiagnostics()+"\n\n"+String.join("\n",events);}
}
