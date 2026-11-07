package de.airpit.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import de.airpit.app.core.HidReports;
import de.airpit.app.core.RelativeMotion;

/** One-finger pointer, tap to click, two-finger scroll/right tap. */
public final class TouchPad extends View {
    public static final int MIN_SPEED_PERCENT=25,MAX_SPEED_PERCENT=300,DEFAULT_SPEED_PERCENT=100;
    public interface Sink {void move(int x,int y,int wheel);void click(int button);void touching(boolean value);}
    private final Sink sink;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private int accent,muted;
    private boolean touched,two,scrolled;
    private float x,y,startX,startY,focusY,maxDistance;
    private final RelativeMotion motion=new RelativeMotion();
    private double remainderWheel;
    private long startTime;
    private int speedPercent=DEFAULT_SPEED_PERCENT;
    public float uiScale=1;
    public TouchPad(Context context,Sink sink,int accent,int muted) {
        super(context);this.sink=sink;this.accent=accent;this.muted=muted;
        density=getResources().getDisplayMetrics().density;
        setContentDescription("Touchpad. Ein Finger bewegt den Mauszeiger. Tippen klickt links. Zwei Finger scrollen; kurzes Tippen mit zwei Fingern klickt rechts.");
        setFocusable(true);setClickable(true);setMinimumHeight(Math.round(180*density));
    }
    public void setSpeedPercent(int percent){
        int next=Math.max(MIN_SPEED_PERCENT,Math.min(MAX_SPEED_PERCENT,percent));
        if(next!=speedPercent){speedPercent=next;motion.reset();}
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        paint.setColor(muted);paint.setAlpha(35);
        for(float px=18*density;px<getWidth();px+=22*density)for(float py=16*density;py<getHeight();py+=22*density)c.drawCircle(px,py,density,paint);
        paint.setAlpha(255);paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));
        paint.setTextSize(14*density*uiScale);paint.setColor(isEnabled()?muted:0xff77828e);
        c.drawText(fit("TOUCHPAD"),getWidth()/2f,getHeight()/2f-3*density,paint);
        paint.setTextSize(11*density*uiScale);paint.setTypeface(android.graphics.Typeface.create("sans-serif",0));
        c.drawText(fit(isEnabled()?"Ein Finger bewegt · zwei scrollen":"Zuerst mit dem PC verbinden"),getWidth()/2f,getHeight()/2f+20*density,paint);
        if(touched&&isEnabled()) {
            paint.setColor(accent);paint.setAlpha(40);c.drawCircle(x,y,27*density,paint);
            paint.setAlpha(220);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*density);c.drawCircle(x,y,12*density,paint);paint.setStyle(Paint.Style.FILL);
        }
    }
    private String fit(String value){float width=Math.max(0,getWidth()-16*density);if(paint.measureText(value)<=width)return value;int end=paint.breakText(value,true,Math.max(0,width-paint.measureText("…")),null);return value.substring(0,end)+"…";}
    @Override public boolean onTouchEvent(MotionEvent e) {
        if(!isEnabled())return false;
        switch(e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                touched=true;two=false;scrolled=false;maxDistance=0;startTime=e.getEventTime();
                startX=x=e.getX();startY=y=e.getY();focusY=y;motion.reset();remainderWheel=0;sink.touching(true);break;
            case MotionEvent.ACTION_POINTER_DOWN:
                two=true;focusY=(e.getY(0)+e.getY(1))/2;break;
            case MotionEvent.ACTION_MOVE:
                if(e.getPointerCount()>=2) {
                    two=true;float focus=(e.getY(0)+e.getY(1))/2;
                    remainderWheel+=(focusY-focus)/(22*density);focusY=focus;
                    int steps=HidReports.clamp((int)remainderWheel);remainderWheel=Math.max(-.999,Math.min(.999,remainderWheel-steps));
                    if(steps!=0){scrolled=true;sink.move(0,0,steps);}
                } else {
                    maxDistance=Math.max(maxDistance,(float)Math.hypot(e.getX()-startX,e.getY()-startY));
                    if(!two) {
                        double factor=speedPercent/100.0;
                        motion.add((e.getX()-x)*1.7/density*factor,(e.getY()-y)*1.7/density*factor);
                        sink.move(motion.x(),motion.y(),0);
                    }
                }
                x=e.getX();y=e.getY();break;
            case MotionEvent.ACTION_POINTER_UP:
                int remaining=e.getActionIndex()==0?1:0;
                x=e.getX(remaining);y=e.getY(remaining);break;
            case MotionEvent.ACTION_UP:
                if(!scrolled&&maxDistance<8*density&&e.getEventTime()-startTime<350) {
                    if(two)sink.click(2);else performClick();
                }
                finishTouch();break;
            case MotionEvent.ACTION_CANCEL:finishTouch();break;
            default:break;
        }
        invalidate();return true;
    }
    private void finishTouch(){touched=false;sink.touching(false);getParent().requestDisallowInterceptTouchEvent(false);}
    @Override public boolean performClick() {super.performClick();if(isEnabled())sink.click(1);return true;}
}
