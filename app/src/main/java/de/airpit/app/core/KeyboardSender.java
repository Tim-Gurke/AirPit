package de.airpit.app.core;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Key-down/key-up pairs run off the UI. No retries of pressed keys or whole text. */
public final class KeyboardSender {
    public interface Transport {boolean send(byte[] data);void stalled();}
    public interface Listener {void progress(int done,int total);void finished(long id,boolean success,String error);}
    private static final byte[] RELEASE=new byte[8];
    private final Transport transport;
    private final Listener listener;
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
    public KeyboardSender(Transport transport,Listener listener){
        this.transport=transport;this.listener=listener;
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
            for(GermanKeyboard.Stroke s:j.keys){
                if(j.cancelled||current.get()!=j)break;
                j.progress=System.nanoTime();
                boolean pressed=false,released=false;
                try{
                    pressed=transport.send(HidReports.keyboard(s.modifiers,s.key));
                    if(pressed)Thread.sleep(8);
                }finally{
                    // Release even when cancellation interrupted the short press interval.
                    released=release();
                }
                if(!pressed||!released){error="Bluetooth-Sendefehler. Text nicht erneut senden, bevor die PC-Eingabe geprüft ist.";transport.stalled();break;}
                if(j.cancelled||current.get()!=j)break;
                j.progress=System.nanoTime();done++;
                if(done==j.keys.size()||done%4==0)listener.progress(done,j.keys.size());
                if(done==j.keys.size())ok=true;else Thread.sleep(20);
            }
            if(j.keys.isEmpty())ok=true;
        }catch(InterruptedException e){Thread.currentThread().interrupt();}
        catch(RuntimeException e){error="Bluetooth-Übertragung fehlgeschlagen. PC-Eingabe prüfen.";transport.stalled();}
        finally{release();end(j,ok&&!j.cancelled,ok?"":error);}
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
    public void close(){cancel();worker.shutdownNow();guard.shutdownNow();}
}
