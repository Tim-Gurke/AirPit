package de.airpit.app;

import android.content.Context;
import android.view.*;
import java.util.*;

/** Four-column wrapping board; each child keeps its natural content height. */
public final class TileBoard extends ViewGroup {
    private final Map<View,Integer> spans=new IdentityHashMap<>();
    private final Map<View,int[]> positions=new IdentityHashMap<>();
    private final int gap;
    public TileBoard(Context context,int gap){super(context);this.gap=gap;}
    public void tile(View view,int span){spans.put(view,span);addView(view,new LayoutParams(-1,-2));}
    @Override protected void onMeasure(int w,int h){
        int width=MeasureSpec.getSize(w),unit=(width-gap*3)/4,col=0,y=0,row=0;
        positions.clear();
        for(int i=0;i<getChildCount();i++){
            View c=getChildAt(i);int span=spans.get(c);if(col+span>4){y+=row+gap;col=0;row=0;}
            int cw=unit*span+gap*(span-1);c.measure(MeasureSpec.makeMeasureSpec(cw,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
            positions.put(c,new int[]{col*(unit+gap),y,cw,c.getMeasuredHeight()});row=Math.max(row,c.getMeasuredHeight());col+=span;
        }
        setMeasuredDimension(width,y+row);
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){for(int i=0;i<getChildCount();i++){View c=getChildAt(i);int[] p=positions.get(c);if(p!=null)c.layout(p[0],p[1],p[0]+p[2],p[1]+p[3]);}}
}
