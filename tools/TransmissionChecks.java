import de.airpit.app.core.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class TransmissionChecks {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static boolean released(byte[] bytes){for(byte b:bytes)if(b!=0)return false;return true;}
    static final class Result implements KeyboardSender.Listener{
        final CountDownLatch done=new CountDownLatch(1);volatile boolean ok;volatile int calls;
        public void progress(int n,int total){}
        public void finished(long id,boolean success,String error){ok=success;calls++;done.countDown();}
        void await()throws Exception{check(done.await(3,TimeUnit.SECONDS),"Transmission has bounded completion");}
    }
    public static void main(String[] args)throws Exception{
        List<byte[]> reports=Collections.synchronizedList(new ArrayList<>());AtomicBoolean onMain=new AtomicBoolean();Result normal=new Result();
        KeyboardSender sender=new KeyboardSender(new KeyboardSender.Transport(){public boolean send(byte[] b){reports.add(b.clone());if(Thread.currentThread().getName().equals("main"))onMain.set(true);return true;}public void stalled(){throw new AssertionError("Normal transfer stalled");}},normal);
        check(sender.start(1,GermanKeyboard.encode("Test äöü")),"Start accepted");normal.await();check(normal.ok,"Completed transfer succeeds");
        int downs=0;boolean held=false;for(byte[] b:reports){if(released(b))held=false;else{check(!held,"No new key before previous release");held=true;downs++;}}
        check(downs==8&&!held,"All characters sent once and final key released");check(!onMain.get(),"No key packets on UI/caller thread");check(normal.calls==1,"Single completion callback");sender.close();

        CountDownLatch pressed=new CountDownLatch(1);List<byte[]> cancelled=Collections.synchronizedList(new ArrayList<>());Result cancel=new Result();
        KeyboardSender stopping=new KeyboardSender(new KeyboardSender.Transport(){public boolean send(byte[] b){cancelled.add(b.clone());if(!released(b)){pressed.countDown();try{new CountDownLatch(1).await(1,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}return true;}public void stalled(){}},cancel);
        stopping.start(2,GermanKeyboard.encode("abcdefghijklmnopqrstuvwxyz"));check(pressed.await(1,TimeUnit.SECONDS),"Cancellation reaches active key");stopping.cancel();cancel.await();check(!cancel.ok,"Cancelled text is not acknowledged as sent");check(released(cancelled.get(cancelled.size()-1)),"Cancellation releases active key");check(cancelled.stream().filter(b->!released(b)).count()==1,"Cancellation does not replay remaining text");stopping.close();

        AtomicInteger failedDowns=new AtomicInteger(),disconnects=new AtomicInteger();Result failure=new Result();
        KeyboardSender broken=new KeyboardSender(new KeyboardSender.Transport(){public boolean send(byte[] b){if(!released(b)){failedDowns.incrementAndGet();return false;}return true;}public void stalled(){disconnects.incrementAndGet();}},failure);
        broken.start(3,GermanKeyboard.encode("Test"));failure.await();check(!failure.ok&&failedDowns.get()==1,"Failed key is never blindly retried");check(disconnects.get()==1,"Send failure closes bad session");broken.close();

        CountDownLatch emergency=new CountDownLatch(1),releaseAfterStall=new CountDownLatch(1);Result timeout=new Result();AtomicBoolean blocking=new AtomicBoolean(true);
        KeyboardSender stalled=new KeyboardSender(new KeyboardSender.Transport(){public boolean send(byte[] b){
            if(!released(b)&&blocking.get()){long end=System.nanoTime()+2_000_000_000L;while(blocking.get()&&System.nanoTime()<end)try{Thread.sleep(30);}catch(InterruptedException ignored){} }
            if(released(b))releaseAfterStall.countDown();return true;
        }public void stalled(){blocking.set(false);emergency.countDown();}},timeout);
        stalled.start(4,GermanKeyboard.encode("Test"));timeout.await();check(!timeout.ok,"Blocked Bluetooth call cannot report success");check(emergency.await(1,TimeUnit.SECONDS),"Independent guard requests emergency disconnect");check(releaseAfterStall.await(1,TimeUnit.SECONDS),"Recovered worker releases key");check(timeout.calls==1,"Timeout reports exactly once");stalled.close();

        TextDraft draft=new TextDraft();draft.value="Hallo 😀";draft.complete("Hallo 😀",true);check(draft.value.isEmpty(),"Sent text clears input");draft.restore();check(draft.value.equals("Hallo 😀"),"Successful text can be restored");draft.value="Neu";draft.complete("Alt",true);check(draft.value.equals("Neu"),"New edits survive old send completion");draft.complete("Neu",false);check(draft.value.equals("Neu"),"Failure retains original input");
        TileLayout portrait=new TileLayout();portrait.tile("pad").height=480;portrait.tile("text").visible=true;portrait.move("text","pad");String encoded=portrait.encode();TileLayout restored=TileLayout.decode(encoded,false);
        check(restored.tiles.get(0).id.equals("text")&&restored.tile("pad").height==480,"Tile order and pad size persist");check(restored.tile("text").visible,"Tile visibility persists");
        TileLayout landscape=TileLayout.decode(null,true);check(landscape.tile("pad").height==220&&portrait.tile("pad").height==480,"Orientation layouts stay independent");
        TileLayout damaged=TileLayout.decode("v1;pad:99:-300:1;keys:0:9999:1;unknown:2:50:1;",false);check(damaged.tile("pad").span==4&&damaged.tile("pad").height==80,"Invalid pad geometry clamped");check(damaged.tile("keys").span==1&&damaged.tile("keys").height==720,"Invalid tile dimensions clamped");check(damaged.tiles.size()==6,"Unknown tiles ignored, known tiles retained");
        for(int i=0;i<4;i++){portrait.preset(i,false);check(TileLayout.decode(portrait.encode(),false).encode().equals(portrait.encode()),"Preset round trip "+i);}
        System.out.println("PASS: "+checks+" transport, cancellation, timeout, draft and layout checks");
    }
}
