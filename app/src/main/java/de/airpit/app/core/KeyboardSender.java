package de.airpit.app.core;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Key-down/key-up pairs run off the UI. No retries of pressed keys or whole text. */
public final class KeyboardSender {
    public interface Transport {boolean send(byte[] data);void stalled();}
    public interface Listener {void progress(int done,int total);void finished(long id,boolean success,String error);}
    public interface Pause {void sleep(long millis)throws InterruptedException;}
    private static final byte[] RELEASE=new byte[8];
    // sendReport accepts a command; it does not acknowledge a Windows keystroke.
    // Keep both states long enough for the radio/host, including between jobs.
    private static final long KEY_DOWN_MS=40,KEY_UP_MS=40;
    private final Transport transport;
    private final Listener listener;
    private final Pause pause;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(threads("AirPit keyboard"));
    private final ScheduledExecutorService guard=Executors.newSingleThreadScheduledExecutor(threads("AirPit key guard"));
    private final AtomicReference<Job> current=new AtomicReference<>();
    private static final class Job {
        final long id;final List<GermanKeyboard.Stroke> keys;
        final AtomicBoolean ended=new AtomicBoolean();
        volatile boolean cancelled;volatile long progress=System.nanoTime();volatile Thread thread;
        Job(long id,List<GermanKeyboard.Stroke> keys){this.id=id;this.keys=keys;}
    }
    private static ThreadFactory threads(String name){return r->{Thread t=new Thread(r,name);t.setDaemon(true);return t;};}
    public KeyboardSender(Transport transport,Listener listener){this(transport,listener,Thread::sleep);}
    public KeyboardSender(Transport transport,Listener listener,Pause pause){
        this.transport=transport;this.listener=listener;this.pause=pause;
        guard.scheduleAtFixedRate(()->{
            Job j=current.get();
            if(j!=null&&System.nanoTime()-j.progress>300_000_000L){
                j.cancelled=true;if(j.thread!=null)j.thread.interrupt();
                end(j,false,"Bluetooth reagiert zu langsam. Übertragung gestoppt; PC-Eingabe prüfen.");
                transport.stalled();
            }
        },50,50,TimeUnit.MILLISECONDS);
    }
    public boolean start(long id,List<GermanKeyboard.Stroke> keys){
        Job j=new Job(id,keys);if(!current.compareAndSet(null,j))return false;
        worker.execute(()->run(j));return true;
    }
    public boolean busy(){return current.get()!=null;}
    private void run(Job j){
        j.thread=Thread.currentThread();boolean ok=false;String error="Übertragung abgebrochen. PC-Eingabe prüfen.";
        try{
            int done=0;listener.progress(0,j.keys.size());
            if(!release())throw new IllegalStateException("Initial key release failed");
            pause.sleep(KEY_UP_MS);
            for(GermanKeyboard.Stroke s:j.keys){
                if(j.cancelled||current.get()!=j)break;
                j.progress=System.nanoTime();
                boolean pressed=false,released=false;
                try{
                    pressed=transport.send(HidReports.keyboard(s.modifiers,s.key));
                    if(pressed)pause.sleep(KEY_DOWN_MS);
                }finally{
                    // Release even when cancellation interrupted the short press interval.
                    released=release();
                }
                if(!pressed||!released){error="Bluetooth-Sendefehler. Text nicht erneut senden, bevor die PC-Eingabe geprüft ist.";transport.stalled();break;}
                j.progress=System.nanoTime();
                // Also settle the final key-up before enabling another send.
                pause.sleep(KEY_UP_MS);
                if(j.cancelled||current.get()!=j)break;
                j.progress=System.nanoTime();done++;
                if(done==j.keys.size()||done%4==0)listener.progress(done,j.keys.size());
                if(done==j.keys.size())ok=true;
            }
            if(j.keys.isEmpty())ok=true;
        }catch(InterruptedException e){Thread.currentThread().interrupt();}
        catch(RuntimeException e){error="Bluetooth-Übertragung fehlgeschlagen. PC-Eingabe prüfen.";transport.stalled();}
        finally{if(!ok||j.cancelled)release();end(j,ok&&!j.cancelled,ok?"":error);}
    }
    private boolean release(){
        try{return transport.send(RELEASE.clone());}catch(RuntimeException e){return false;}
    }
    private void end(Job j,boolean ok,String error){
        if(j.ended.compareAndSet(false,true)){current.compareAndSet(j,null);listener.finished(j.id,ok,error);}
    }
    public void cancel(){
        Job j=current.get();if(j==null)return;j.cancelled=true;
        if(j.thread!=null)j.thread.interrupt();
        // The worker releases its active key in finally. The guard catches blocked binder calls.
    }
    /** Serialize idle/emergency UI releases with text, never on the mouse worker. */
    public void releaseKeys(){cancel();worker.execute(this::release);}
    public void close(){cancel();worker.shutdownNow();guard.shutdownNow();}
}
