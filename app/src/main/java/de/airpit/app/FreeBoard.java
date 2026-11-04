package de.airpit.app;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.ScrollView;
import de.airpit.app.core.FreeLayout;
import java.util.*;

/** A single control surface: every child has its own non-overlapping rectangle. */
public final class FreeBoard extends ViewGroup {
    public interface Editor {void options(String id);void changed(String message);}
    public final FreeLayout layout;
    private final Map<String,View> controls;
    private final Editor editor;
    private final float density;
    private final int accent;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean editing;
    private String selected;
    private FreeLayout.Item start;
    private float downX,downY,lastX,lastY;
    private boolean moved,blocked;
    private int ghostX,ghostY;

    public FreeBoard(Context context,FreeLayout layout,Map<String,View> controls,boolean editing,int accent,Editor editor){
        super(context);this.layout=layout;this.controls=controls;this.editing=editing;this.accent=accent;this.editor=editor;
        density=getResources().getDisplayMetrics().density;setWillNotDraw(false);setClipChildren(false);setClipToPadding(false);
        for(Map.Entry<String,View> entry:controls.entrySet()){
            View child=entry.getValue();if(child.getParent() instanceof ViewGroup)((ViewGroup)child.getParent()).removeView(child);
            addView(child,new LayoutParams(1,1));
        }
        refresh();
    }
    private int px(int units){return Math.round(units*density);}
    public void refresh(){for(Map.Entry<String,View> e:controls.entrySet()){FreeLayout.Item i=layout.item(e.getKey());e.getValue().setVisibility(i!=null&&i.visible?VISIBLE:GONE);}requestLayout();invalidate();}
    @Override protected void onMeasure(int w,int h){
        int width=MeasureSpec.getSize(w);layout.adapt(Math.round(width/density),layout.scale);
        for(Map.Entry<String,View> e:controls.entrySet()){
            FreeLayout.Item i=layout.item(e.getKey());if(i!=null&&i.visible)e.getValue().measure(MeasureSpec.makeMeasureSpec(px(i.width),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(px(i.height),MeasureSpec.EXACTLY));
        }
        int viewport=getSuggestedMinimumHeight(),height=Math.max(viewport,px(layout.bottom()+(editing?260:12)));
        setMeasuredDimension(width,resolveSize(height,h));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        for(Map.Entry<String,View> e:controls.entrySet()){
            FreeLayout.Item i=layout.item(e.getKey());if(i!=null&&i.visible){int x=px(i.x),y=px(i.y);e.getValue().layout(x,y,x+px(i.width),y+px(i.height));}
        }
    }
    @Override public boolean onInterceptTouchEvent(android.view.MotionEvent event){return editing||super.onInterceptTouchEvent(event);}
    private String hit(float x,float y){for(FreeLayout.Item i:layout.items.values())if(i.visible&&x>=px(i.x)&&x<px(i.x+i.width)&&y>=px(i.y)&&y<px(i.y+i.height))return i.id;return null;}
    @Override public boolean onTouchEvent(android.view.MotionEvent e){
        if(!editing)return super.onTouchEvent(e);
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                selected=hit(e.getX(),e.getY());if(selected==null)return false;
                start=layout.item(selected).copy();downX=lastX=e.getX();downY=lastY=e.getY();moved=blocked=false;
                getParent().requestDisallowInterceptTouchEvent(true);invalidate();return true;
            case MotionEvent.ACTION_MOVE:
                if(selected==null)return false;
                lastX=e.getX();lastY=e.getY();
                if(Math.hypot(lastX-downX,lastY-downY)>ViewConfiguration.get(getContext()).getScaledTouchSlop())moved=true;
                if(moved){drag();autoScroll();}return true;
            case MotionEvent.ACTION_UP:
                if(selected==null)return false;
                String id=selected;boolean tap=!moved;
                if(blocked){restore();editor.changed("Platz belegt – vorherige Position beibehalten.");}
                else if(moved)editor.changed("Position geändert · noch nicht gespeichert.");
                finish();if(tap){performClick();editor.options(id);}return true;
            case MotionEvent.ACTION_CANCEL:if(selected!=null){restore();finish();}return true;
            case MotionEvent.ACTION_POINTER_DOWN:if(selected!=null){restore();finish();}return true;
            default:return selected!=null;
        }
    }
    private void drag(){
        ghostX=Math.max(0,Math.min(layout.width-start.width,start.x+Math.round((lastX-downX)/density)));
        ghostY=Math.max(0,Math.min(FreeLayout.MAX_Y-start.height,start.y+Math.round((lastY-downY)/density)));
        blocked=!layout.place(selected,ghostX,ghostY,start.width,start.height);
        requestLayout();invalidate();
    }
    private final Runnable edgeScroll=()->{if(selected!=null&&moved){autoScroll();}};
    private void autoScroll(){
        removeCallbacks(edgeScroll);if(!(getParent() instanceof ScrollView))return;
        ScrollView scroll=(ScrollView)getParent();Rect visible=new Rect();if(!getLocalVisibleRect(visible))return;
        int delta=lastY<visible.top+px(38)?-px(14):lastY>visible.bottom-px(38)?px(14):0;
        if(delta!=0){int before=scroll.getScrollY();scroll.scrollBy(0,delta);int actual=scroll.getScrollY()-before;lastY+=actual;if(actual!=0){drag();postDelayed(edgeScroll,40);}}
    }
    private void restore(){FreeLayout.Item i=layout.item(selected);i.x=start.x;i.y=start.y;i.width=start.width;i.height=start.height;requestLayout();}
    private void finish(){selected=null;start=null;blocked=moved=false;removeCallbacks(edgeScroll);getParent().requestDisallowInterceptTouchEvent(false);invalidate();}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onDetachedFromWindow(){removeCallbacks(edgeScroll);super.onDetachedFromWindow();}
    @Override protected void dispatchDraw(Canvas c){
        super.dispatchDraw(c);if(!editing)return;
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,density));paint.setPathEffect(new DashPathEffect(new float[]{5*density,4*density},0));
        for(FreeLayout.Item i:layout.items.values())if(i.visible){paint.setColor(i.id.equals(selected)?accent:0x887e9aac);float inset=density;c.drawRoundRect(px(i.x)+inset,px(i.y)+inset,px(i.x+i.width)-inset,px(i.y+i.height)-inset,8*density,8*density,paint);}
        paint.setPathEffect(null);
        if(blocked&&start!=null){paint.setColor(0xffff7b80);paint.setStrokeWidth(2*density);c.drawRoundRect(px(ghostX)+density,px(ghostY)+density,px(ghostX+start.width)-density,px(ghostY+start.height)-density,8*density,8*density,paint);}
        paint.setStyle(Paint.Style.FILL);
    }
}
