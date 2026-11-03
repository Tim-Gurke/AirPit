package de.airpit.app;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.os.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;

/** Optional authenticated Bluetooth RFCOMM channel for literal Windows Unicode input. */
@SuppressLint("MissingPermission")
public final class UnicodeClient {
    public interface Listener {void changed();void finished(long id,boolean ok,String error);}
    public static final UUID SERVICE=UUID.fromString("31670e87-2978-4b54-8b63-cf02b2bbd5a1");
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"AirPit Unicode");t.setDaemon(true);return t;});
    private final Listener listener;
    private volatile BluetoothSocket socket;
    private volatile BufferedReader reader;
    private volatile BufferedWriter writer;
    public volatile boolean ready,connecting,sending;
    public volatile String status="Emoji-Erweiterung nicht verbunden";
    private volatile long epoch;
    private volatile String peer="";
    public UnicodeClient(Listener listener){this.listener=listener;}
    private void changed(String value){status=value;main.post(listener::changed);}
    public void connect(BluetoothDevice device,String code){
        close();final long e=epoch;peer=device.getAddress();connecting=true;changed("Verbinde Emoji-Erweiterung …");
        main.postDelayed(()->{if(epoch==e&&connecting){close();changed("PC-Helfer antwortet nicht. Auf Windows starten und den aktuellen Code eingeben.");}},15000);
        io.execute(()->{
            try{
                BluetoothSocket s=device.createRfcommSocketToServiceRecord(SERVICE);socket=s;
                if(e!=epoch){s.close();return;}
                s.connect();
                BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));
                BufferedWriter w=new BufferedWriter(new OutputStreamWriter(s.getOutputStream(),StandardCharsets.UTF_8));
                write(w,new JSONObject().put("type","hello").put("code",code).put("version",1));
                JSONObject answer=read(r);
                if(!"ready".equals(answer.optString("type")))throw new IOException("Code nicht bestätigt");
                if(e!=epoch){s.close();return;}
                reader=r;writer=w;ready=true;connecting=false;changed("Emoji-Erweiterung verbunden");
            }catch(Exception ex){if(e==epoch){close();changed("Emoji-Verbindung fehlgeschlagen. PC-Helfer, Code und Bluetooth prüfen.");}}
        });
    }
    public boolean send(long id,String text){
        if(!ready||sending)return false;sending=true;final long e=epoch;
        main.postDelayed(()->{if(e==epoch&&sending){close();changed("Unicode-Übertragung unterbrochen. PC-Eingabe prüfen.");listener.finished(id,false,status);}},5000);
        io.execute(()->{
            try{
                write(writer,new JSONObject().put("type","text").put("id",id).put("text",text));
                JSONObject answer=read(reader);
                boolean ok="ok".equals(answer.optString("type"))&&answer.optLong("id",-1)==id;
                if(e!=epoch)return;sending=false;
                String error=ok?"":answer.optString("error","PC hat die Texteingabe nicht bestätigt.");
                changed(ok?"Unicode-Text übertragen":error);main.post(()->listener.finished(id,ok,error));
            }catch(Exception ex){if(e==epoch){close();changed("Unicode-Verbindung unterbrochen. PC-Eingabe prüfen.");main.post(()->listener.finished(id,false,status));}}
        });return true;
    }
    public boolean matches(BluetoothDevice device){return device!=null&&peer.equals(device.getAddress());}
    private static JSONObject read(BufferedReader r)throws Exception{
        StringBuilder b=new StringBuilder();for(int i=0;i<32768;i++){int c=r.read();if(c<0)throw new EOFException();if(c=='\n')return new JSONObject(b.toString());b.append((char)c);}throw new IOException("Antwort zu groß");
    }
    private static void write(BufferedWriter w,JSONObject object)throws Exception{w.write(object.toString());w.write('\n');w.flush();}
    public void close(){epoch++;peer="";ready=connecting=sending=false;status="Emoji-Erweiterung nicht verbunden";BluetoothSocket s=socket;socket=null;reader=null;writer=null;if(s!=null)try{s.close();}catch(IOException ignored){}}
    public void shutdown(){close();io.shutdownNow();main.removeCallbacksAndMessages(null);}
}
