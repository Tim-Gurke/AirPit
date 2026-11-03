package de.airpit.app;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppQosSettings;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import de.airpit.app.core.GermanKeyboard;
import de.airpit.app.core.HidReports;
import de.airpit.app.core.KeyboardSender;
import java.util.concurrent.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Foreground-only HID session. No server, network permissions, or hidden pairing. */
@SuppressLint("MissingPermission")
public final class HidController {
    public interface Listener { void changed();default void textFinished(String source,boolean success){} }
    private final Context context;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Listener listener;
    public final BluetoothAdapter adapter;
    private volatile BluetoothHidDevice hid;
    public volatile BluetoothDevice host;
    private BluetoothDevice pendingHost;
    public volatile boolean registered,registering,connecting,typing;
    public volatile int sent,total;
    public final UnicodeClient unicode;
    private final KeyboardSender keyboard;
    private final ScheduledExecutorService pointer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"AirPit mouse");t.setDaemon(true);return t;});
    private final ExecutorService administration=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"AirPit Bluetooth control");t.setDaemon(true);return t;});
    private final ScheduledExecutorService linkGuard=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"AirPit link guard");t.setDaemon(true);return t;});
    private volatile long pointerWriteSince;
    private volatile long uiHeartbeat=System.nanoTime();
    private final Object mouseLock=new Object();
    private int pendingX,pendingY,pendingWheel;
    private boolean mouseDirty;
    private long nextTextId,activeTextId;
    private String activeText;
    private volatile boolean active;
    private boolean proxyRequested;
    private volatile int buttons;
    private String detail="Bluetooth noch nicht gestartet";
    private volatile byte[] lastKeyboard=new byte[8];
    private volatile long generation;
    private long lifecycle;

    public HidController(Context context,Listener listener) {
        this.context=context;this.listener=listener;
        BluetoothManager manager=(BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter=manager==null?null:manager.getAdapter();
        keyboard=new KeyboardSender(new KeyboardSender.Transport(){
            public boolean send(byte[] data){lastKeyboard=data.clone();return report(HidReports.KEYBOARD,data);}
            public void stalled(){emergencyDisconnect();}
        },new KeyboardSender.Listener(){
            public void progress(int done,int count){handler.post(()->{sent=done;total=count;listener.changed();});}
            public void finished(long id,boolean ok,String error){handler.post(()->finishText(id,ok,error));}
        });
        unicode=new UnicodeClient(new UnicodeClient.Listener(){
            public void changed(){listener.changed();}
            public void finished(long id,boolean ok,String error){finishText(id,ok,error);}
        });
        pointer.scheduleAtFixedRate(this::flushMouse,0,16,TimeUnit.MILLISECONDS);
        linkGuard.scheduleAtFixedRate(()->{long now=System.nanoTime(),since=pointerWriteSince;if(connected()&&since!=0&&now-since>300_000_000L)emergencyDisconnect();if(buttons!=0&&now-uiHeartbeat>400_000_000L){releaseMouse();handler.post(listener::changed);}},50,50,TimeUnit.MILLISECONDS);
    }
    public String detail() { return detail; }
    public boolean connected() { return registered&&host!=null; }
    public void heartbeat(){uiHeartbeat=System.nanoTime();}
    public int mouseButtons(){return buttons;}
    private void changed(String message) { detail=message;listener.changed(); }

    public void start() {
        active=true;
        if(adapter==null) {changed("Dieses Gerät hat kein Bluetooth.");return;}
        try {
            if(!adapter.isEnabled()) {changed("Bluetooth ist ausgeschaltet.");return;}
            if(hid!=null) { register();return; }
            if(proxyRequested) return;
            proxyRequested=true;
            final long session=++lifecycle;
            changed("Bluetooth-Maus wird vorbereitet …");
            if(!adapter.getProfileProxy(context,profileListener(session),BluetoothProfile.HID_DEVICE)) {
                proxyRequested=false;changed("Diese Android-ROM stellt Bluetooth-HID nicht bereit. Touchpad und Tastatur brauchen ebenfalls HID.");
            } else {
                handler.postDelayed(()->{
                    if(active&&proxyRequested&&hid==null) {proxyRequested=false;changed("Bluetooth-HID antwortet nicht. Bluetooth neu einschalten und erneut versuchen. Die ROM muss den HID-Gerätemodus unterstützen.");}
                },8000);
            }
        } catch(SecurityException|IllegalStateException e) {proxyRequested=false;changed("Bluetooth-Berechtigung fehlt oder Bluetooth ist nicht bereit.");}
    }

    private BluetoothProfile.ServiceListener profileListener(final long session) {return new BluetoothProfile.ServiceListener() {
        @Override public void onServiceConnected(int profile,BluetoothProfile proxy) {
            if(!active||session!=lifecycle) {try{adapter.closeProfileProxy(profile,proxy);}catch(Exception ignored){}return;}
            proxyRequested=false;
            hid=(BluetoothHidDevice)proxy;register();
        }
        @Override public void onServiceDisconnected(int profile) {
            if(!active||session!=lifecycle)return;
            hid=null;registered=registering=connecting=false;host=null;pendingHost=null;
            clearLocalInput();changed("Bluetooth-Dienst getrennt. Verbindung erneut starten.");
        }
    };}

    private void register() {
        if(!active||hid==null||registered||registering)return;
        registering=true;
        try {
            BluetoothHidDeviceAppSdpSettings settings=new BluetoothHidDeviceAppSdpSettings(
                "AirPit","Air-Maus und Tastatur","Tim Weißbach",(byte)0xC0,HidReports.DESCRIPTOR);
            BluetoothHidDeviceAppQosSettings qos=new BluetoothHidDeviceAppQosSettings(
                BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,800,9,0,11250,BluetoothHidDeviceAppQosSettings.MAX);
            final BluetoothHidDevice profile=hid;final long token=lifecycle;
            administration.execute(()->{try{
                if(!active||profile!=hid||token!=lifecycle)return;
                boolean accepted=profile.registerApp(settings,null,qos,context.getMainExecutor(),callback(token));
                if(!accepted)handler.post(()->{if(token==lifecycle){registering=false;changed("HID-Anmeldung abgelehnt. Andere Maus-/Tastatur-Apps schließen und erneut versuchen.");}});
            }catch(RuntimeException e){handler.post(()->{if(token==lifecycle){registering=false;changed("HID-Anmeldung fehlgeschlagen. Oben erneut starten.");}});}});
            changed("Maus und Tastatur werden angemeldet …");
            handler.postDelayed(()->{
                if(active&&registering&&!registered) {registering=false;changed("HID-Anmeldung dauert zu lange. Verbindung erneut starten.");}
            },8000);
        } catch(SecurityException|IllegalStateException e) {registering=false;changed("Bluetooth-HID konnte nicht angemeldet werden.");}
    }

    private BluetoothHidDevice.Callback callback(final long session) {return new BluetoothHidDevice.Callback() {
        @Override public void onAppStatusChanged(BluetoothDevice pluggedDevice,boolean value) {
            if(!active||session!=lifecycle)return;
            registered=value&&active;registering=false;
            if(!value) {host=null;connecting=false;unicode.close();clearLocalInput();changed("HID-Modus beendet. Oben neu verbinden.");return;}
            if(pluggedDevice!=null)try{
                if(hid.getConnectionState(pluggedDevice)==BluetoothProfile.STATE_CONNECTED){host=pluggedDevice;connecting=false;if(!unicode.matches(host))unicode.close();releaseAll();changed("Verbunden mit "+name(host));return;}
            }catch(Exception ignored){}
            if(pendingHost!=null) {BluetoothDevice d=pendingHost;pendingHost=null;connect(d);}
            else changed("Bereit. PC koppeln oder ein gekoppeltes Gerät verbinden.");
        }
        @Override public void onConnectionStateChanged(BluetoothDevice device,int state) {
            if(!active||session!=lifecycle)return;
            if(state==BluetoothProfile.STATE_CONNECTED) {
                if(!unicode.matches(device))unicode.close();
                if(host!=null&&!host.equals(device)){final BluetoothDevice old=host;final BluetoothHidDevice profile=hid;administration.execute(()->{try{profile.disconnect(old);}catch(Exception ignored){}});}
                host=device;connecting=false;clearLocalInput();releaseAll();
                try{context.getSharedPreferences("airpit",0).edit().putString("lastHost",device.getAddress()).apply();}catch(Exception ignored){}
                changed("Verbunden mit "+name(device));
            } else if(state==BluetoothProfile.STATE_CONNECTING) {
                connecting=true;changed("Verbindung zu "+name(device)+" wird aufgebaut …");
            } else if(state==BluetoothProfile.STATE_DISCONNECTED) {
                if(host==null||host.equals(device)) {host=null;connecting=false;unicode.close();clearLocalInput();changed("Getrennt. Oben auf Verbinden tippen.");}
            }
        }
        @Override public void onGetReport(BluetoothDevice device,byte type,byte id,int bufferSize) {
            if(hid==null||!active||session!=lifecycle)return;
            try {
                if(type==BluetoothHidDevice.REPORT_TYPE_INPUT&&(id==HidReports.MOUSE||id==HidReports.KEYBOARD)) {
                    byte[] report=id==HidReports.MOUSE?HidReports.mouse(buttons,0,0,0):lastKeyboard.clone();
                    if(bufferSize>0&&bufferSize<report.length) report=java.util.Arrays.copyOf(report,bufferSize);
                    final byte[] answer=report;final BluetoothHidDevice profile=hid;administration.execute(()->{try{if(active&&session==lifecycle&&profile==hid)profile.replyReport(device,type,id,answer);}catch(Exception ignored){}});
                } else {final BluetoothHidDevice profile=hid;administration.execute(()->{try{profile.reportError(device,BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ);}catch(Exception ignored){}});}
            }catch(Exception ignored){}
        }
        @Override public void onSetReport(BluetoothDevice device,byte type,byte id,byte[] data) {
            if(hid==null||!active||session!=lifecycle)return;
            final BluetoothHidDevice profile=hid;administration.execute(()->{try {if(active&&session==lifecycle&&profile==hid)profile.reportError(device,type==BluetoothHidDevice.REPORT_TYPE_OUTPUT&&id==HidReports.KEYBOARD
                ?BluetoothHidDevice.ERROR_RSP_SUCCESS:BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ);}catch(Exception ignored){}});
        }
        @Override public void onVirtualCableUnplug(BluetoothDevice device) {
            if(!active||session!=lifecycle)return;
            host=null;connecting=false;unicode.close();clearLocalInput();changed("PC hat die HID-Verbindung entfernt. Erneut koppeln.");
        }
    };}

    public void connect(BluetoothDevice device) {
        if(device==null)return;
        if(!registered) {pendingHost=device;start();return;}
        if(connecting)return;
        if(host!=null&&!host.equals(device)) {changed("Zuerst die aktuelle PC-Verbindung trennen.");return;}
        if(host!=null) return;
        try {
            connecting=true;changed("Verbinde "+name(device)+" …");
            final BluetoothHidDevice profile=hid;final long token=lifecycle;
            administration.execute(()->{try{if(active&&token==lifecycle&&profile==hid&&!profile.connect(device))handler.post(()->{if(token==lifecycle){connecting=false;changed("Verbindung konnte nicht gestartet werden. PC eingeschaltet lassen und oben erneut versuchen.");}});}catch(Exception e){handler.post(()->{if(token==lifecycle){connecting=false;changed("Verbindung nicht möglich. Bluetooth prüfen.");}});}});
            handler.postDelayed(()->{
                if(connecting&&host==null) {connecting=false;changed("Noch keine Verbindung. In Windows das Handy als Bluetooth-Eingabegerät koppeln; ggf. alte Kopplung entfernen.");}
            },12000);
        }catch(SecurityException|IllegalStateException e){connecting=false;changed("Verbindung nicht möglich. Bluetooth und Berechtigung prüfen.");}
    }
    public void disconnect() {
        pendingHost=null;releaseAll();unicode.close();
        final BluetoothDevice device=host;final BluetoothHidDevice profile=hid;administration.execute(()->{try{if(profile!=null&&device!=null)profile.disconnect(device);}catch(Exception ignored){}});
        host=null;connecting=false;changed("Verbindung getrennt.");
    }
    public List<BluetoothDevice> paired() {
        try {
            if(adapter==null||!adapter.isEnabled())return Collections.emptyList();
            List<BluetoothDevice> devices=new ArrayList<>(adapter.getBondedDevices());
            Collections.sort(devices,Comparator.comparing(HidController::name,String.CASE_INSENSITIVE_ORDER));return devices;
        }catch(SecurityException e){return Collections.emptyList();}
    }
    public static String name(BluetoothDevice device) {
        try{String n=device.getName();return n==null||n.trim().isEmpty()?"Bluetooth-Gerät":n;}catch(SecurityException e){return "Bluetooth-Gerät";}
    }

    private boolean report(int id,byte[] bytes) {
        BluetoothHidDevice profile=hid;BluetoothDevice device=host;
        if(!registered||profile==null||device==null)return false;
        try{return profile.sendReport(device,id,bytes);}catch(SecurityException|IllegalStateException e){return false;}
    }
    public void move(int x,int y,int wheel) {
        synchronized(mouseLock){pendingX=Math.max(-254,Math.min(254,pendingX+x));pendingY=Math.max(-254,Math.min(254,pendingY+y));pendingWheel=Math.max(-127,Math.min(127,pendingWheel+wheel));}
    }
    private void flushMouse(){
        if(!connected())return;
        int x,y,w,b;
        synchronized(mouseLock){if(!mouseDirty&&(pendingX|pendingY|pendingWheel)==0)return;x=HidReports.clamp(pendingX);y=HidReports.clamp(pendingY);w=pendingWheel;b=buttons;pendingX-=x;pendingY-=y;pendingWheel=0;mouseDirty=false;}
        pointerWriteSince=System.nanoTime();
        try{if(!report(HidReports.MOUSE,HidReports.mouse(b,x,y,w)))emergencyDisconnect();}finally{pointerWriteSince=0;}
    }
    public void button(int mask,boolean down) {
        if(!connected())return;
        synchronized(mouseLock){buttons=down?(buttons|mask):(buttons&~mask);mouseDirty=true;}
    }
    public void click(int mask) {
        if(!connected())return;
        button(mask,true);
        final long g=generation;
        pointer.schedule(()->{if(g==generation)button(mask,false);},45,TimeUnit.MILLISECONDS);
    }
    public void releaseMouse() {synchronized(mouseLock){buttons=0;pendingX=pendingY=pendingWheel=0;mouseDirty=true;}}
    public void releaseAll() {
        clearLocalInput();releaseMouse();keyboard.releaseKeys();
        pointer.execute(()->{if(connected())report(HidReports.MOUSE,HidReports.mouse(0,0,0,0));});
    }
    private void clearLocalInput() {
        generation++;keyboard.cancel();if(unicode.sending)unicode.close();
        if(typing){String source=activeText;typing=false;activeText=null;activeTextId=++nextTextId;if(source!=null)listener.textFinished(source,false);}
        buttons=0;lastKeyboard=new byte[8];
    }

    public void text(String source) {
        if(!connected()||typing)return;
        if(unicode.ready&&!unicode.matches(host))unicode.close();
        if(source.codePointCount(0,source.length())>1000)throw new IllegalArgumentException("Bitte höchstens 1.000 Zeichen auf einmal senden.");
        List<GermanKeyboard.Stroke> strokes=null;
        if(!unicode.ready)try{strokes=GermanKeyboard.encode(source);}catch(IllegalArgumentException e){throw new IllegalArgumentException("Für Emojis und dieses Sonderzeichen bitte unter Verbindung den optionalen Windows-Helfer verbinden. Alternativ öffnet die Taste „Windows-Emojis“ die Emoji-Auswahl am PC.");}
        activeTextId=++nextTextId;activeText=source;typing=true;sent=0;total=source.codePointCount(0,source.length());listener.changed();
        boolean accepted=unicode.ready?unicode.send(activeTextId,source):keyboard.start(activeTextId,strokes);
        if(!accepted)finishText(activeTextId,false,"Die vorherige Eingabe wird noch beendet. Gleich erneut versuchen.");
    }
    public void key(int modifier,int usage) {
        if(!connected()||typing)return;
        activeTextId=++nextTextId;activeText=null;typing=true;sent=0;total=1;listener.changed();
        if(!keyboard.start(activeTextId,Collections.singletonList(new GermanKeyboard.Stroke(modifier,usage))))finishText(activeTextId,false,"Vorherige Eingabe wird beendet.");
    }
    private void finishText(long id,boolean success,String error){
        if(id!=activeTextId||!typing)return;String source=activeText;activeText=null;typing=false;
        if(source!=null)listener.textFinished(source,success);
        changed(success?"Text übertragen.":error);
    }
    private void emergencyDisconnect(){
        final BluetoothHidDevice profile=hid;final BluetoothDevice device=host;
        if(profile==null||device==null)return;
        host=null;connecting=false;unicode.close();
        handler.post(()->{clearLocalInput();changed("Übertragung gestoppt: Bluetooth reagiert nicht rechtzeitig. PC-Eingabe prüfen und oben neu verbinden.");});
        Thread recovery=new Thread(()->{try{if(profile!=null&&device!=null){profile.sendReport(device,HidReports.KEYBOARD,new byte[8]);profile.sendReport(device,HidReports.MOUSE,new byte[4]);}}catch(Exception ignored){}},"AirPit emergency release");recovery.setDaemon(true);recovery.start();
        Thread disconnect=new Thread(()->{try{if(profile!=null&&device!=null)profile.disconnect(device);}catch(Exception ignored){}},"AirPit emergency disconnect");disconnect.setDaemon(true);disconnect.start();
    }
    public void reconnectLast(){
        String address=context.getSharedPreferences("airpit",0).getString("lastHost","");
        if(adapter!=null&&!address.isEmpty())try{connect(adapter.getRemoteDevice(address));}catch(Exception e){changed("Gespeicherten PC erneut auswählen.");}
    }
    public void cancelText() {releaseAll();changed("Textübertragung angehalten. Bisherige Eingabe am PC prüfen.");}

    public void stop() {
        releaseAll();active=false;lifecycle++;pendingHost=null;registered=registering=connecting=false;
        final BluetoothHidDevice profile=hid;
        hid=null;host=null;proxyRequested=false;handler.removeCallbacksAndMessages(null);
        if(profile!=null)administration.execute(()->{try{profile.unregisterApp();adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE,profile);}catch(Exception ignored){}});
    }
}
