package de.airpit.app;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.ScrollView;
import de.airpit.app.core.FreeLayout;
import de.airpit.app.core.LayoutGrid;
import java.util.*;

/** A single control surface: every child has its own non-overlapping rectangle. */
public final class FreeBoard extends ViewGroup {
    public interface Editor {void options(String id);void changed(String message);String name(String id);int minWidth(String id);int minHeight(String id);}
    public final FreeLayout layout;
    private final Map<String,View> controls;
    private final Editor editor;
    private final float density;
    private final int accent;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect gridClip=new Rect(),gridVisible=new Rect(),cachedGridClip=new Rect(-1,-1,-1,-1);
    private float[] gridPoints=new float[0],gridLines=new float[0];
    private final boolean editing;
    private String selected,focused;
    private FreeLayout.Item start;
    private FreeLayout dragBase;
    private float downX,downY,lastX,lastY;
    private boolean moved,blocked,resizing;
    private int ghostX,ghostY,ghostWidth,ghostHeight,pushed;

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
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);if(!editing)return;
        c.getClipBounds(gridClip);if(!getLocalVisibleRect(gridVisible)||!gridClip.intersect(gridVisible)||gridClip.isEmpty())return;
        if(!gridClip.equals(cachedGridClip)){
            cachedGridClip.set(gridClip);LayoutGrid grid=layout.grid();float row=grid.row*density;
            int top=Math.max(0,(int)Math.floor(gridClip.top/row)),bottom=(int)Math.ceil(Math.min(getHeight(),gridClip.bottom)/row);
            gridPoints=new float[Math.max(0,(LayoutGrid.COLUMNS+1)*(bottom-top+1)*2)];int point=0;
            for(int y=top;y<=bottom;y++)for(int x=0;x<=LayoutGrid.COLUMNS;x++){gridPoints[point++]=px(grid.x(x));gridPoints[point++]=y*row;}
            List<Float> lines=new ArrayList<>();
            for(int x=0;x<=LayoutGrid.COLUMNS;x++)if(x%2==0){lines.add((float)px(grid.x(x)));lines.add((float)gridClip.top);lines.add((float)px(grid.x(x)));lines.add((float)gridClip.bottom);}
            for(int y=top;y<=bottom;y++)if(y%2==0){lines.add((float)gridClip.left);lines.add(y*row);lines.add((float)gridClip.right);lines.add(y*row);}
            gridLines=new float[lines.size()];for(int i=0;i<lines.size();i++)gridLines[i]=lines.get(i);
        }
        gridPaint.setColor((accent&0x00ffffff)|0x25000000);gridPaint.setStrokeWidth(Math.max(1,density*.4f));c.drawLines(gridLines,gridPaint);
        gridPaint.setColor((accent&0x00ffffff)|0x65000000);gridPaint.setStrokeWidth(Math.max(1,density*.7f));c.drawPoints(gridPoints,gridPaint);
    }
    @Override public boolean onInterceptTouchEvent(android.view.MotionEvent event){return editing||super.onInterceptTouchEvent(event);}
    private String hit(float x,float y){for(FreeLayout.Item i:layout.items.values())if(i.visible&&x>=px(i.x)&&x<px(i.x+i.width)&&y>=px(i.y)&&y<px(i.y+i.height))return i.id;return null;}
    @Override public boolean onTouchEvent(android.view.MotionEvent e){
        if(!editing)return super.onTouchEvent(e);
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                selected=hit(e.getX(),e.getY());if(selected==null)return false;
                focused=selected;start=layout.item(selected).copy();dragBase=layout.copy();pushed=0;downX=lastX=e.getX();downY=lastY=e.getY();moved=blocked=false;
                float handle=handleSize(start);resizing=lastX>=px(start.x+start.width)-handle&&lastY>=px(start.y+start.height)-handle;
                editor.changed(editor.name(selected)+" · "+start.width+" × "+start.height+" · "+(resizing?"Ecke ziehen: Größe ändern":"Ziehen: verschieben"));
                getParent().requestDisallowInterceptTouchEvent(true);invalidate();return true;
            case MotionEvent.ACTION_MOVE:
                if(selected==null)return false;
                lastX=e.getX();lastY=e.getY();
                if(Math.hypot(lastX-downX,lastY-downY)>(resizing?2*density:ViewConfiguration.get(getContext()).getScaledTouchSlop()))moved=true;
                if(moved){drag();autoScroll();}return true;
            case MotionEvent.ACTION_UP:
                if(selected==null)return false;
                lastX=e.getX();lastY=e.getY();
                if(Math.hypot(lastX-downX,lastY-downY)>(resizing?2*density:ViewConfiguration.get(getContext()).getScaledTouchSlop()))moved=true;
                if(moved)drag();
                String id=selected;boolean tap=!moved;
                if(blocked){restore();editor.changed("Einfügen hier nicht möglich · vorherige Anordnung beibehalten.");}
                else if(moved)editor.changed((resizing?"Größe geändert":"Am Raster ausgerichtet")+(pushed>0?" · "+pushed+" Elemente nach unten verschoben":"")+" · noch nicht gespeichert.");
                finish();if(tap){performClick();editor.options(id);}return true;
            case MotionEvent.ACTION_CANCEL:if(selected!=null){restore();finish();}return true;
            case MotionEvent.ACTION_POINTER_DOWN:if(selected!=null){restore();finish();}return true;
            default:return selected!=null;
        }
    }
    private float handleSize(FreeLayout.Item item){return Math.min(28*density,Math.min(px(item.width),px(item.height))/2f);}
    private void drag(){
        LayoutGrid grid=dragBase.grid();
        int rawX=Math.max(0,Math.min(dragBase.width-start.width,start.x+Math.round((lastX-downX)/density)));
        int rawY=Math.max(0,Math.min(FreeLayout.MAX_Y-start.height,start.y+Math.round((lastY-downY)/density)));
        ghostX=resizing?start.x:grid.snapX(rawX,dragBase.width-start.width);ghostY=resizing?start.y:grid.snapY(rawY,FreeLayout.MAX_Y-start.height);
        ghostWidth=start.width;ghostHeight=start.height;
        if(resizing){
            int maxW=dragBase.width-start.x,maxH=Math.min(2000,FreeLayout.MAX_Y-start.y);
            int width=start.width+Math.round((lastX-downX)/density),height=start.height+Math.round((lastY-downY)/density);
            ghostWidth=Math.max(Math.min(editor.minWidth(selected),maxW),grid.snapX(width,maxW));
            ghostHeight=Math.max(Math.min(editor.minHeight(selected),maxH),grid.snapY(height,maxH));
        }
        FreeLayout preview=resizing?dragBase.resizePreview(selected,ghostWidth,ghostHeight):dragBase.insertionPreview(selected,rawX,rawY,start.width,start.height);
        if(preview!=null){ghostX=preview.item(selected).x;ghostY=preview.item(selected).y;}
        blocked=preview==null;layout.applyFrom(blocked?dragBase:preview);pushed=0;
        if(!blocked)for(FreeLayout.Item item:layout.items.values())if(item.visible&&!item.id.equals(selected)&&item.y>dragBase.item(item.id).y)pushed++;
        if(!blocked){FreeLayout.Item item=layout.item(selected);editor.changed(editor.name(selected)+" · "+item.width+" × "+item.height+(pushed>0?" · "+pushed+" Elemente rücken nach unten":" · am Raster"));}
        requestLayout();invalidate();
    }
    private final Runnable edgeScroll=()->{if(selected!=null&&moved){autoScroll();}};
    private void autoScroll(){
        removeCallbacks(edgeScroll);if(!(getParent() instanceof ScrollView))return;
        ScrollView scroll=(ScrollView)getParent();Rect visible=new Rect();if(!getLocalVisibleRect(visible))return;
        int delta=lastY<visible.top+px(38)?-px(14):lastY>visible.bottom-px(38)?px(14):0;
        if(delta!=0){int before=scroll.getScrollY();scroll.scrollBy(0,delta);int actual=scroll.getScrollY()-before;lastY+=actual;if(actual!=0){drag();postDelayed(edgeScroll,40);}}
    }
    private void restore(){if(dragBase!=null)layout.applyFrom(dragBase);requestLayout();invalidate();}
    private void finish(){selected=null;start=null;dragBase=null;blocked=moved=resizing=false;pushed=0;removeCallbacks(edgeScroll);getParent().requestDisallowInterceptTouchEvent(false);invalidate();}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onDetachedFromWindow(){removeCallbacks(edgeScroll);if(selected!=null)restore();super.onDetachedFromWindow();}
    @Override protected void dispatchDraw(Canvas c){
        super.dispatchDraw(c);if(!editing)return;
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,density));paint.setPathEffect(new DashPathEffect(new float[]{5*density,4*density},0));
        for(FreeLayout.Item i:layout.items.values())if(i.visible){boolean shifted=dragBase!=null&&dragBase.item(i.id)!=null&&i.y!=dragBase.item(i.id).y;paint.setColor(i.id.equals(focused)||shifted?accent:0x887e9aac);float inset=density;c.drawRoundRect(px(i.x)+inset,px(i.y)+inset,px(i.x+i.width)-inset,px(i.y+i.height)-inset,8*density,8*density,paint);}
        paint.setPathEffect(null);
        // The same visible bottom-right size grip used by Watt'n'Pit's tile editor.
        paint.setColor(accent);paint.setStrokeWidth(2*density);
        for(FreeLayout.Item i:layout.items.values())if(i.visible){float size=Math.min(12*density,handleSize(i)*.6f),right=px(i.x+i.width)-5*density,bottom=px(i.y+i.height)-5*density;c.drawLine(right-size,bottom,right,bottom,paint);c.drawLine(right,bottom-size,right,bottom,paint);}
        if(selected!=null&&moved&&!blocked){FreeLayout.Item i=layout.item(selected);paint.setColor((accent&0x00ffffff)|0x95000000);paint.setStrokeWidth(Math.max(1,density*.5f));c.drawLine(px(i.x),gridClip.top,px(i.x),gridClip.bottom,paint);c.drawLine(gridClip.left,px(i.y),gridClip.right,px(i.y),paint);}
        if(blocked&&start!=null){paint.setColor(0xffff7b80);paint.setStrokeWidth(2*density);c.drawRoundRect(px(ghostX)+density,px(ghostY)+density,px(ghostX+ghostWidth)-density,px(ghostY+ghostHeight)-density,8*density,8*density,paint);}
        paint.setStyle(Paint.Style.FILL);
    }
}
