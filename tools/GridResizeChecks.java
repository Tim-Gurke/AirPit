import de.airpit.app.core.*;
import java.util.*;

public final class GridResizeChecks {
    private static int count;
    private static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    private static void add(FreeLayout l,String id,int x,int y,int w,int h){l.items.put(id,new FreeLayout.Item(id,x,y,w,h));}
    public static void main(String[] args){
        for(int width:new int[]{320,360,387,412,619,620,720,1024}){
            LayoutGrid grid=new LayoutGrid(width);check(grid.x(0)==0&&grid.x(24)==width,"24 half-column steps span the board");check(grid.row==(width>=620?15:13),"Half-row matches Watt compact/wide row height");
            for(int column=0;column<=24;column++){int x=grid.x(column);check(grid.snapX(x,width)==x,"Grid columns are stable even at fractional column widths");}
            for(int max=0;max<=width;max++)for(int value:new int[]{0,17,109,width,Integer.MAX_VALUE}){int x=grid.snapX(value,max);check(x>=0&&x<=max&&grid.alignedX(x),"Horizontal snap stays on the grid inside the available space");}
            for(int value:new int[]{0,1,12,27,100,15999,Integer.MAX_VALUE}){int y=grid.snapY(value,16000);check(y>=0&&y<=16000&&y%grid.row==0,"Vertical snap is bounded without overflow");}
        }
        FreeLayout base=new FreeLayout(360,100);add(base,"pad",13,51,200,100);add(base,"left",13,168,100,48);add(base,"right",240,168,100,48);add(base,"wide",13,232,330,48);
        String original=base.encode();FreeLayout taller=base.resizePreview("pad",200,150);
        check(taller!=null&&taller.valid(),"Resizing inserts the extra space without overlap");check(taller.item("pad").x==13&&taller.item("pad").y==51,"Older anchors are not snapped by a size-only gesture");check(taller.item("left").y>168&&taller.item("right").y==168,"Only the intersecting column moves downward");check(taller.item("wide").y>=232,"Wide successor remains valid");check(base.encode().equals(original),"Resize preview does not alter its saved baseline");
        FreeLayout smaller=base.resizePreview("pad",180,80);check(smaller.item("left").y==168,"Shrinking from the baseline restores the displaced neighbor");
        FreeLayout active=base.copy();active.applyFrom(taller);active.applyFrom(base);check(active.encode().equals(original),"Cancel restores size and every displaced position");
        check(base.resizePreview("pad",400,100)==null&&base.resizePreview("pad",100,2001)==null,"Oversized resize fails without mutation");check(base.encode().equals(original),"Failed resize remains atomic");
        LayoutDrafts drafts=new LayoutDrafts();FreeLayout draft=drafts.edit("freeLayout.v1.portrait.0",base);draft.applyFrom(taller);check(drafts.dirty(),"Resize is an unsaved draft");check(FreeLayout.decode(drafts.savedValues().get("freeLayout.v1.portrait.0")).encode().equals(taller.encode()),"Explicit save preserves the size and complete cascade");check(FreeLayout.decode(original).encode().equals(original),"Existing off-grid layout stays unchanged when only opened");
        Random random=new Random(808);FreeLayout stress=new FreeLayout(387,100);for(int n=0;n<18;n++)add(stress,"control."+n,8+(n%3)*126,8+(n/3)*64,100,48);
        for(int n=0;n<400;n++){
            String id="control."+random.nextInt(18);FreeLayout before=stress.copy();FreeLayout next=before.resizePreview(id,32+random.nextInt(180),32+random.nextInt(180));
            check(before.encode().equals(stress.encode()),"Preview remains independent");
            if(next!=null){check(next.valid(),"Random resize never overlaps");check(next.item(id).x==before.item(id).x&&next.item(id).y==before.item(id).y,"Random resize keeps anchor");for(FreeLayout.Item i:before.items.values())if(!i.id.equals(id)){FreeLayout.Item other=next.item(i.id);check(i.x==other.x&&i.width==other.width&&i.height==other.height&&other.y>=i.y,"Neighbors only move down");}stress.applyFrom(next);}
        }
        System.out.println("PASS: "+count+" Watt grid, direct resize, preservation and rollback checks");
    }
}
