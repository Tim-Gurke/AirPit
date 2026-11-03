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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Foreground-only HID session. No server, network permissions, or hidden pairing. */
@SuppressLint("MissingPermission")
public final class HidController {
    public interface Listener { void changed(); }
    private final Context context;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Listener listener;
    public final BluetoothAdapter adapter;
    private BluetoothHidDevice hid;
    public BluetoothDevice host;
    private BluetoothDevice pendingHost;
    public boolean registered,registering,connecting,typing;
    private boolean active,proxyRequested;
    private int buttons,keyboardFailures;
    private String detail="Bluetooth noch nicht gestartet";
    private byte[] lastKeyboard=new byte[8];
    private final ArrayDeque<byte[]> keyQueue=new ArrayDeque<>();
    private long generation,lifecycle;

    public HidController(Context context,Listener listener) {
        this.context=context;this.listener=listener;
        BluetoothManager manager=(BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter=manager==null?null:manager.getAdapter();
    }
    public String detail() { return detail; }
    public boolean connected() { return registered&&host!=null; }
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
            if(!hid.registerApp(settings,null,qos,context.getMainExecutor(),callback(lifecycle))) {
                registering=false;changed("HID-Anmeldung abgelehnt. Andere Bluetooth-Maus-/Tastatur-Apps schließen und erneut versuchen.");return;
            }
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
            if(!value) {host=null;connecting=false;clearLocalInput();changed("HID-Modus beendet. AirPit geöffnet lassen und erneut starten.");return;}
            if(pendingHost!=null) {BluetoothDevice d=pendingHost;pendingHost=null;connect(d);}
            else changed("Bereit. PC koppeln oder ein gekoppeltes Gerät verbinden.");
        }
        @Override public void onConnectionStateChanged(BluetoothDevice device,int state) {
            if(!active||session!=lifecycle)return;
            if(state==BluetoothProfile.STATE_CONNECTED) {
                if(host!=null&&!host.equals(device))try{hid.disconnect(host);}catch(Exception ignored){}
                host=device;connecting=false;clearLocalInput();releaseAll();
                changed("Verbunden mit "+name(device));
            } else if(state==BluetoothProfile.STATE_CONNECTING) {
                connecting=true;changed("Verbindung zu "+name(device)+" wird aufgebaut …");
            } else if(state==BluetoothProfile.STATE_DISCONNECTED) {
                if(host==null||host.equals(device)) {host=null;connecting=false;clearLocalInput();changed("Getrennt. PC auswählen, um erneut zu verbinden.");}
            }
        }
        @Override public void onGetReport(BluetoothDevice device,byte type,byte id,int bufferSize) {
            if(hid==null||!active||session!=lifecycle)return;
            try {
                if(type==BluetoothHidDevice.REPORT_TYPE_INPUT&&(id==HidReports.MOUSE||id==HidReports.KEYBOARD)) {
                    byte[] report=id==HidReports.MOUSE?HidReports.mouse(buttons,0,0,0):lastKeyboard.clone();
                    if(bufferSize>0&&bufferSize<report.length) report=java.util.Arrays.copyOf(report,bufferSize);
                    hid.replyReport(device,type,id,report);
                } else hid.reportError(device,BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ);
            }catch(Exception ignored){}
        }
        @Override public void onSetReport(BluetoothDevice device,byte type,byte id,byte[] data) {
            if(hid==null||!active||session!=lifecycle)return;
            try {hid.reportError(device,type==BluetoothHidDevice.REPORT_TYPE_OUTPUT&&id==HidReports.KEYBOARD
                ?BluetoothHidDevice.ERROR_RSP_SUCCESS:BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ);}catch(Exception ignored){}
        }
        @Override public void onVirtualCableUnplug(BluetoothDevice device) {
            if(!active||session!=lifecycle)return;
            host=null;connecting=false;clearLocalInput();changed("PC hat die HID-Verbindung entfernt. Erneut koppeln.");
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
            if(!hid.connect(device)) {connecting=false;changed("Verbindung konnte nicht gestartet werden. Am PC Bluetooth-Geräte öffnen und dort verbinden.");return;}
            handler.postDelayed(()->{
                if(connecting&&host==null) {connecting=false;changed("Noch keine Verbindung. In Windows das Handy als Bluetooth-Eingabegerät koppeln; ggf. alte Kopplung entfernen.");}
            },12000);
        }catch(SecurityException|IllegalStateException e){connecting=false;changed("Verbindung nicht möglich. Bluetooth und Berechtigung prüfen.");}
    }
    public void disconnect() {
        pendingHost=null;releaseAll();
        try{if(host!=null&&hid!=null)hid.disconnect(host);}catch(Exception ignored){}
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
        if(!connected()||hid==null)return false;
        try{return hid.sendReport(host,id,bytes);}catch(SecurityException|IllegalStateException e){return false;}
    }
    public void move(int x,int y,int wheel) { if((x|y|wheel)!=0)report(HidReports.MOUSE,HidReports.mouse(buttons,x,y,wheel)); }
    public void button(int mask,boolean down) {
        if(!connected())return;
        buttons=down?(buttons|mask):(buttons&~mask);
        final byte[] state=HidReports.mouse(buttons,0,0,0);
        if(!report(HidReports.MOUSE,state)&&!down)retryRelease(generation,0);
    }
    public void click(int mask) {
        if(!connected())return;
        button(mask,true);
        final long g=generation;
        handler.postDelayed(()->{if(g==generation)button(mask,false);},45);
    }
    public void releaseMouse() {buttons=0;if(connected()&&!report(HidReports.MOUSE,HidReports.mouse(0,0,0,0)))retryRelease(generation,0);}
    public void releaseAll() {
        clearLocalInput();
        if(connected()) {
            boolean mouse=report(HidReports.MOUSE,HidReports.mouse(0,0,0,0));
            boolean key=report(HidReports.KEYBOARD,lastKeyboard);
            if(!mouse||!key)retryRelease(generation,0);
        }
    }
    private void retryRelease(long g,int attempt) {
        if(attempt>=4)return;
        handler.postDelayed(()->{
            if(g!=generation||!connected())return;
            boolean mouse=report(HidReports.MOUSE,HidReports.mouse(buttons,0,0,0));
            boolean key=report(HidReports.KEYBOARD,lastKeyboard);
            if(!mouse||!key)retryRelease(g,attempt+1);
        },35);
    }
    private void clearLocalInput() {generation++;buttons=0;keyQueue.clear();typing=false;lastKeyboard=new byte[8];keyboardFailures=0;handler.removeCallbacks(typeNext);}

    public void text(String source) {
        if(!connected()||typing)return;
        if(source.codePointCount(0,source.length())>1000)throw new IllegalArgumentException("Bitte höchstens 1.000 Zeichen auf einmal senden.");
        List<GermanKeyboard.Stroke> strokes=GermanKeyboard.encode(source); // validate all before first key
        for(GermanKeyboard.Stroke s:strokes) enqueue(s.modifiers,s.key);
        startTyping();
    }
    public void key(int modifier,int usage) {
        if(!connected()||typing)return;
        enqueue(modifier,usage);startTyping();
    }
    private void enqueue(int modifier,int usage) {keyQueue.add(HidReports.keyboard(modifier,usage));keyQueue.add(new byte[8]);}
    private void startTyping() {
        if(keyQueue.isEmpty())return;
        typing=true;keyboardFailures=0;listener.changed();handler.post(typeNext);
    }
    private final Runnable typeNext=new Runnable() {
        @Override public void run() {
            if(!typing)return;
            if(!connected()) {clearLocalInput();changed("Textübertragung durch Trennung abgebrochen.");return;}
            byte[] next=keyQueue.peek();
            if(next==null) {typing=false;changed("Tasten an Windows übertragen.");return;}
            if(report(HidReports.KEYBOARD,next)) {lastKeyboard=next.clone();keyQueue.remove();keyboardFailures=0;}
            else if(++keyboardFailures>=4) {releaseAll();changed("Textübertragung abgebrochen. Die bisherige Eingabe am PC prüfen.");return;}
            handler.postDelayed(this,18);
        }
    };
    public void cancelText() {releaseAll();changed("Textübertragung angehalten. Bisherige Eingabe am PC prüfen.");}

    public void stop() {
        releaseAll();active=false;lifecycle++;pendingHost=null;registered=registering=connecting=false;
        try {if(hid!=null){hid.unregisterApp();adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE,hid);}}catch(Exception ignored){}
        hid=null;host=null;proxyRequested=false;handler.removeCallbacksAndMessages(null);
    }
}
