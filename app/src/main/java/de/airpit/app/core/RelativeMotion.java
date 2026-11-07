package de.airpit.app.core;

/** Subpixel carry only: clipped fast movement must never become later movement. */
public final class RelativeMotion {
    private double remainderX,remainderY;
    private int x,y;
    public void reset(){remainderX=remainderY=0;x=y=0;}
    public void add(double dx,double dy){
        if(!Double.isFinite(dx)||!Double.isFinite(dy)){reset();return;}
        double totalX=remainderX+dx,totalY=remainderY+dy;
        double wholeX=totalX<0?Math.ceil(totalX):Math.floor(totalX),wholeY=totalY<0?Math.ceil(totalY):Math.floor(totalY);
        x=HidReports.clamp((int)wholeX);y=HidReports.clamp((int)wholeY);
        remainderX=totalX-wholeX;remainderY=totalY-wholeY;
    }
    public int x(){return x;}
    public int y(){return y;}
}
