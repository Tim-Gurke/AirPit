package de.airpit.app.core;

/** Integrates angular rates, preserving subpixel movement across reports. */
public final class MotionFilter {
    /** Full useful adjustment range without the former 65–162 ms smoothing tail. */
    public static double smoothingSeconds(int percent){return .004+Math.max(0,Math.min(100,percent))*.00045;}
    public static boolean freshSample(long timestamp,long now,long started){return timestamp>=started&&timestamp<=now&&now-timestamp<=100_000_000L;}
    private double remainderX,remainderY,smoothX,smoothY;
    public void reset() { remainderX=remainderY=smoothX=smoothY=0; }
    public int[] step(double yaw,double pitch,double seconds,double gain,double smoothing,double deadzone,boolean invertX,boolean invertY) {
        if(!Double.isFinite(yaw)||!Double.isFinite(pitch)||seconds<=0||seconds>0.1) { reset();return new int[]{0,0}; }
        double alpha=1-Math.exp(-seconds/Math.max(0.001,smoothing));
        smoothX+=alpha*(yaw-smoothX);smoothY+=alpha*(pitch-smoothY);
        double x=soft(smoothX,deadzone),y=soft(smoothY,deadzone);
        remainderX+=x*gain*seconds*(invertX?-1:1);
        remainderY+=y*gain*seconds*(invertY?-1:1);
        int dx=HidReports.clamp((int)remainderX),dy=HidReports.clamp((int)remainderY);
        // Drop overflow rather than replaying fast movement later.
        remainderX=Math.max(-0.999,Math.min(0.999,remainderX-dx));
        remainderY=Math.max(-0.999,Math.min(0.999,remainderY-dy));
        return new int[]{dx,dy};
    }
    private static double soft(double n,double d) {return Math.copySign(Math.max(0,Math.abs(n)-d),n);}
}
