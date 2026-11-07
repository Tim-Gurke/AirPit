package de.airpit.app.core;

import java.util.ArrayDeque;

/** Fresh relative motion plus ordered button edges, with completion-based pacing. */
public final class MouseStream {
    public static final long INTERVAL=20_000_000L,WINDOW=40_000_000L,MAX_MOTION_AGE=80_000_000L;
    public static final long BUTTON_GAP=35_000_000L,MAX_EDGE_AGE=250_000_000L;
    public static final int MAX_EDGES=8;
    private static final class Edge {
        final int buttons;final long time;
        Edge(int buttons,long time){this.buttons=buttons;this.time=time;}
    }
    public static final class Frame {
        public final int buttons,x,y,wheel;
        public final boolean stalled;
        public final long motionAgeNanos;
        private final long epoch;
        private final boolean neutral;
        private Frame(int buttons,int x,int y,int wheel,long epoch,boolean neutral,boolean stalled,long age){
            this.buttons=buttons;this.x=x;this.y=y;this.wheel=wheel;this.epoch=epoch;this.neutral=neutral;this.stalled=stalled;motionAgeNanos=age;
        }
    }
    public static final class Stats {
        public final long packets,discarded,limited,stalls,lastWriteNanos,maxWriteNanos;
        public final int queuedEdges;
        private Stats(MouseStream s){packets=s.packets;discarded=s.discarded;limited=s.limited;stalls=s.stalls;lastWriteNanos=s.lastWrite;maxWriteNanos=s.maxWrite;queuedEdges=s.edges.size();}
    }
    private final ArrayDeque<Edge> edges=new ArrayDeque<>();
    private int x,y,wheel,logicalButtons,sentButtons,inFlightButtons;
    private long epoch,windowStart,lastMotion,lastPacket,lastButton;
    private boolean motion,hasPacket,hasButton,inFlight,neutral=true,stalled;
    private long packets,discarded,limited,stalls,lastWrite,maxWrite;

    public synchronized void move(int dx,int dy,int scroll,long now){
        if(stalled||(dx|dy|scroll)==0)return;
        if(motion&&(now<windowStart||now-windowStart>WINDOW)){discarded++;clearMotion();}
        if(!motion){windowStart=now;motion=true;}
        long nx=(long)x+dx,ny=(long)y+dy,nw=(long)wheel+scroll;
        if(Math.abs(nx)>127||Math.abs(ny)>127||Math.abs(nw)>127)limited++;
        x=(int)Math.max(-127,Math.min(127,nx));y=(int)Math.max(-127,Math.min(127,ny));wheel=(int)Math.max(-127,Math.min(127,nw));lastMotion=now;
        if((x|y|wheel)==0)clearMotion();
    }
    public synchronized boolean button(int mask,boolean down,long now){
        mask&=7;int next=down?logicalButtons|mask:logicalButtons&~mask;
        if(stalled)return false;if(next==logicalButtons)return true;
        if(edges.size()>=MAX_EDGES){trip();return false;}
        edges.add(new Edge(next,now));logicalButtons=next;return true;
    }
    public synchronized boolean tap(int mask,long now){
        mask&=7;if(stalled)return false;if(mask==0||(logicalButtons&mask)!=0)return true;
        if(edges.size()+2>MAX_EDGES){trip();return false;}
        edges.add(new Edge(logicalButtons|mask,now));edges.add(new Edge(logicalButtons,now));return true;
    }
    public synchronized void reset(){
        epoch++;edges.clear();clearMotion();logicalButtons=sentButtons=inFlightButtons=0;
        hasPacket=hasButton=inFlight=stalled=false;neutral=true;
    }
    public synchronized int buttons(){return logicalButtons|sentButtons|(inFlight?inFlightButtons:0);}
    private void clearMotion(){x=y=wheel=0;motion=false;}
    private void trip(){if(!stalled)stalls++;stalled=true;edges.clear();logicalButtons=0;clearMotion();}
    private long edgeDue(){return hasButton?lastButton+BUTTON_GAP:Long.MIN_VALUE;}
    private long motionDue(){return hasPacket?lastPacket+INTERVAL:Long.MIN_VALUE;}
    /** -1 means idle. There is no periodic timer and no catch-up after a slow write. */
    public synchronized long delay(long now){
        if(motion&&(now<windowStart||now-windowStart>MAX_MOTION_AGE)){discarded++;clearMotion();}
        if(stalled||neutral)return 0;
        while(!edges.isEmpty()&&edges.peek().buttons==sentButtons)edges.remove();
        if(!edges.isEmpty()&&(now<edges.peek().time||now-edges.peek().time>MAX_EDGE_AGE)){trip();return 0;}
        if(edges.isEmpty()&&!motion)return -1;
        long due=edges.isEmpty()?motionDue():motion?Math.min(edgeDue(),motionDue()):edgeDue();
        return due<=now?0:due-now;
    }
    public synchronized Frame take(long now){
        if(delay(now)!=0)return null;
        if(stalled)return new Frame(0,0,0,0,epoch,true,true,-1);
        int b=sentButtons,dx=0,dy=0,w=0;long age=-1;boolean forced=neutral;
        if(forced)neutral=false;
        else{
            if(!edges.isEmpty()&&edgeDue()<=now)b=edges.remove().buttons;
            if(motion){dx=x;dy=y;w=wheel;age=now-windowStart;clearMotion();}
        }
        inFlight=true;inFlightButtons=b;
        return new Frame(b,dx,dy,w,epoch,forced,false,age);
    }
    public synchronized void complete(Frame frame,long started,long finished,boolean accepted){
        lastWrite=Math.max(0,finished-started);maxWrite=Math.max(maxWrite,lastWrite);if(accepted)packets++;
        if(frame.epoch!=epoch||!accepted)return;
        inFlight=false;hasPacket=true;lastPacket=finished;
        if(frame.neutral||frame.buttons!=sentButtons){hasButton=true;lastButton=finished;}
        sentButtons=frame.buttons;
    }
    public synchronized Stats stats(){return new Stats(this);}
}
