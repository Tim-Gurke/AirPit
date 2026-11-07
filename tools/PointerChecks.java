import de.airpit.app.core.*;
import java.util.*;

/** Virtual-clock tests: slow transports must not replay an old pointer path. */
public final class PointerChecks {
    private static int count;
    private static long ms(long n){return n*1_000_000L;}
    private static void check(boolean ok,String message){count++;if(!ok)throw new AssertionError(message);}
    private static MouseStream clean(){MouseStream s=new MouseStream();MouseStream.Frame f=s.take(0);s.complete(f,0,0,true);return s;}
    private static MouseStream.Frame frame(MouseStream s,long time,int buttons){MouseStream.Frame f=s.take(ms(time));check(f!=null&&!f.stalled&&f.buttons==buttons,"Expected ordered button state "+buttons+" at "+time);return f;}
    private static void simulate(int writeMillis){
        MouseStream stream=clean();MouseStream.Frame inFlight=null;long started=0,finish=0,lastFinish=0,maxAge=0;int reports=0;
        for(int t=1;t<=1500;t++){
            long now=ms(t);
            if(t<=200&&t%2==0)stream.move(30,0,0,now);
            if(t>=350&&t<=500&&t%2==0)stream.move(-30,0,0,now);
            if(inFlight!=null&&now>=finish){stream.complete(inFlight,started,finish,true);lastFinish=finish;inFlight=null;}
            if(inFlight==null&&stream.delay(now)==0){
                MouseStream.Frame f=stream.take(now);check(f!=null&&!f.stalled,"Movement alone cannot create a button stall");
                check(now>=lastFinish+MouseStream.INTERVAL,"No catch-up burst after completing a slow write");
                check(f.motionAgeNanos>=0&&f.motionAgeNanos<=MouseStream.MAX_MOTION_AGE,"Every outgoing motion window is fresh");
                if(t>=390)check(f.x<=0,"No old rightward path after changing direction");
                check(t<=580,"No motion replay after the input stops");
                check(Math.abs(f.x)<=127&&Math.abs(f.y)<=127&&Math.abs(f.wheel)<=127,"Wire format remains signed 8-bit HID");
                maxAge=Math.max(maxAge,f.motionAgeNanos);reports++;started=now;finish=now+ms(writeMillis);inFlight=f;
                if(writeMillis==0){stream.complete(f,started,finish,true);lastFinish=finish;inFlight=null;}
            }
        }
        check(stream.delay(ms(2000))==-1,"Pointer sender sleeps after input ends");
        check(reports>0,"Virtual transport produced motion");
        System.out.println("Virtual transport: write="+writeMillis+" ms, frames="+reports+", oldest input at send="+(maxAge/1_000_000)+" ms");
    }
    public static void main(String[] args){
        RelativeMotion delta=new RelativeMotion();delta.add(1000.25,-1000.25);check(delta.x()==127&&delta.y()==-127,"Fast input is bounded at the wire limit");delta.add(-4.25,4.25);check(delta.x()==-4&&delta.y()==4,"Reversal is immediate instead of replaying clipped input");delta.add(0,0);check(delta.x()==0&&delta.y()==0,"No stored whole-pixel tail after the finger stops");
        for(int n=0;n<1000;n++){delta.add(.125,-.125);if(n%8==7)check(delta.x()==1&&delta.y()==-1,"Subpixel precision is preserved");}
        delta.reset();delta.add(0,0);check(delta.x()==0&&delta.y()==0,"New gesture resets fractional carry");delta.add(Double.NaN,1);check(delta.x()==0&&delta.y()==0,"Invalid input cannot poison the next gesture");
        MouseStream s=clean();check(s.delay(ms(10000))==-1,"Idle requires no mouse timer");s.move(100,0,1,ms(10));s.move(-12,0,-1,ms(100));MouseStream.Frame f=s.take(ms(100));check(f.x==-12&&f.wheel==-1,"A fresh bucket replaces old movement and scrolling");s.complete(f,ms(100),ms(105),true);check(s.delay(ms(105))==-1,"Nothing is left to replay");
        s=clean();s.move(60,0,0,ms(100));check(s.take(ms(181))==null&&s.delay(ms(181))==-1,"Expired movement is discarded without sending");
        s=clean();s.move(1,0,0,ms(10));f=s.take(ms(20));s.move(8,0,0,ms(80));s.complete(f,ms(20),ms(120),true);check(s.take(ms(120))==null,"Completion starts a new pacing interval");s.move(-5,0,0,ms(135));f=s.take(ms(140));check(f!=null&&f.x==-5&&f.motionAgeNanos==ms(5),"Slow write is followed by current input only");check(s.stats().maxWriteNanos==ms(100),"Diagnostics measure local transport calls");
        s=clean();check(s.tap(1,ms(100)),"Tap is queued atomically");f=frame(s,100,1);s.complete(f,ms(100),ms(101),true);check(s.take(ms(135))==null,"Release cannot overtake minimum down time");f=frame(s,136,0);s.complete(f,ms(136),ms(137),true);check(s.buttons()==0&&s.delay(ms(200))==-1,"Tap finishes released and idle");
        s=clean();s.button(1,true,ms(100));s.button(2,true,ms(110));s.button(1,false,ms(120));s.button(2,false,ms(130));
        int[] states={1,3,2,0};for(int i=0;i<states.length;i++){long t=100+i*36;f=frame(s,t,states[i]);s.complete(f,ms(t),ms(t+1),true);}check(s.buttons()==0,"Both buttons retain press/release ordering");
        s=clean();s.button(1,true,ms(100));f=frame(s,100,1);s.complete(f,ms(100),ms(101),true);s.tap(2,ms(120));f=frame(s,136,3);s.complete(f,ms(136),ms(137),true);f=frame(s,172,1);s.complete(f,ms(172),ms(173),true);check(s.buttons()==1,"Right tap preserves a physically held left button");s.reset();f=frame(s,174,0);s.complete(f,ms(174),ms(175),true);check(s.buttons()==0,"Explicit release bypasses pacing and clears held buttons");
        s=clean();s.button(1,true,ms(100));f=frame(s,100,1);s.reset();s.complete(f,ms(100),ms(200),true);check(s.buttons()==0,"A completed old-generation write cannot revive a released button");f=frame(s,200,0);s.complete(f,ms(200),ms(201),true);check(s.delay(ms(500))==-1,"Reset clears all queued input");
        s=clean();for(int n=0;n<4;n++)check(s.tap(1,ms(100)),"Bounded edge queue accepts complete taps");check(!s.tap(1,ms(100)),"Overflow cannot append a partial click");f=s.take(ms(100));check(f.stalled&&f.buttons==0,"Overload requests neutral recovery instead of replaying clicks");s.reset();f=frame(s,101,0);s.complete(f,ms(101),ms(102),true);check(s.buttons()==0,"Recovery always starts neutral");
        s=clean();s.button(1,true,ms(100));f=s.take(ms(351));check(f!=null&&f.stalled,"Outdated button input is stopped, never replayed much later");
        for(int latency:new int[]{0,1,5,25,80,120,250})simulate(latency);
        double smoothing=MotionFilter.smoothingSeconds(35);check(smoothing<.020&&smoothing>.019,"Default smoothing uses a short response window");
        MotionFilter faster=new MotionFilter(),older=new MotionFilter();int fast=0,slow=0;for(int n=0;n<10;n++){fast+=faster.step(1,0,.01,1000,smoothing,0,false,false)[0];slow+=older.step(1,0,.01,1000,.0645,0,false,false)[0];}check(fast>slow+20,"Default gyro starts more directly than the former smoothing");
        int tail=0;for(int n=0;n<10;n++)tail=faster.step(0,0,.01,1000,smoothing,0,false,false)[0];check(tail==0,"Gyro smoothing settles after stopping");
        check(MotionFilter.smoothingSeconds(0)<MotionFilter.smoothingSeconds(35)&&MotionFilter.smoothingSeconds(35)<MotionFilter.smoothingSeconds(100),"Every smoothing level remains meaningful");
        check(MotionFilter.freshSample(ms(100),ms(110),ms(90)),"Fresh sensor data accepted");check(!MotionFilter.freshSample(ms(100),ms(201),ms(90)),"Backlogged sensor data rejected");check(!MotionFilter.freshSample(ms(100),ms(110),ms(105)),"Samples from an earlier session rejected");check(!MotionFilter.freshSample(ms(120),ms(110),ms(90)),"Wrong-clock sensor timestamps rejected");
        System.out.println("PASS: "+count+" pointer freshness, pacing, button order, recovery and filter-response checks");
    }
}
