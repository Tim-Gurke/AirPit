import de.airpit.app.core.*;
import java.util.*;

public final class InsertionChecks {
    private static int count;
    private static void check(boolean ok,String message){count++;if(!ok)throw new AssertionError(message);}
    private static void add(FreeLayout l,String id,int x,int y,int w,int h){l.items.put(id,new FreeLayout.Item(id,x,y,w,h));}
    private static FreeLayout fixture(){
        FreeLayout l=new FreeLayout(360,100);add(l,"moving",12,300,100,40);add(l,"above",12,4,100,40);add(l,"first",12,80,100,40);add(l,"second",12,128,100,40);add(l,"other-column",140,80,100,40);add(l,"hidden",12,80,100,40);l.item("hidden").visible=false;return l;
    }
    private static boolean noOverlap(FreeLayout l){
        List<FreeLayout.Item> placed=new ArrayList<>();
        for(FreeLayout.Item i:l.items.values())if(i.visible){
            if(i.x<0||i.y<0||i.width<1||i.height<1||(long)i.x+i.width>l.width||(long)i.y+i.height>FreeLayout.MAX_Y)return false;
            for(FreeLayout.Item p:placed)if(Math.max(i.x,p.x)<Math.min((long)i.x+i.width,(long)p.x+p.width)&&Math.max(i.y,p.y)<Math.min((long)i.y+i.height,(long)p.y+p.height))return false;
            placed.add(i);
        }return true;
    }
    private static void unchangedExceptDown(FreeLayout before,FreeLayout after,String selected){
        for(FreeLayout.Item i:before.items.values())if(!i.id.equals(selected)){
            FreeLayout.Item n=after.item(i.id);
            check(i.x==n.x&&i.width==n.width&&i.height==n.height&&i.visible==n.visible,"Only vertical position changes for displaced controls");
            check(n.y>=i.y,"Other controls never move upward");
            if(n.y!=i.y)check(n.y%FreeLayout.GRID==0,"Pushed controls align to fine grid");
        }
    }
    public static void main(String[] args){
        check(FreeLayout.snap(1)==0&&FreeLayout.snap(3)==4&&FreeLayout.snap(9)==8,"Fine 4-unit grid selects nearest position");
        check(FreeLayout.snapBounded(319,318)==316,"Right-edge snap stays inside surface");
        check(FreeLayout.snap(Integer.MAX_VALUE)>=0&&FreeLayout.snap(Integer.MAX_VALUE)%4==0,"Grid arithmetic does not overflow");
        FreeLayout base=fixture();String original=base.encode();check(noOverlap(base),"Original arrangement is valid");
        FreeLayout preview=base.insertionPreview("moving",11,73,100,40);
        check(preview!=null&&preview.item("moving").x==12&&preview.item("moving").y==72,"Insertion snaps both coordinates");
        check(preview.item("first").y==116&&preview.item("second").y==160,"Small gap grows by pushing a chain of controls downward");
        check(preview.item("above").y==4&&preview.item("other-column").y==80,"Unrelated upper control and other column stay put");
        check(preview.item("hidden").y==80&&!preview.item("hidden").visible,"Hidden controls do not participate in displacement");
        check(base.encode().equals(original),"Drag preview never mutates its baseline");check(noOverlap(preview)&&preview.valid(),"Inserted arrangement contains no overlaps");
        unchangedExceptDown(base,preview,"moving");
        for(int n=0;n<30;n++)check(base.insertionPreview("moving",11,73,100,40).encode().equals(preview.encode()),"Repeated preview does not accumulate movement");
        FreeLayout elsewhere=base.insertionPreview("moving",244,73,100,40);
        check(elsewhere.item("first").y==80&&elsewhere.item("second").y==128,"Moving away restores previewed neighbors");
        FreeLayout active=base.copy();FreeLayout.Item reference=active.item("first");active.applyFrom(preview);
        check(reference==active.item("first")&&reference.y==116,"Preview preserves references used by editor");
        active.applyFrom(base);check(active.encode().equals(original),"Gesture cancellation restores every displaced item");

        FreeLayout bridge=new FreeLayout(360,100);add(bridge,"moving",0,300,160,40);add(bridge,"left",0,80,100,40);add(bridge,"right",200,80,100,40);add(bridge,"wide",0,128,350,40);add(bridge,"below-right",200,176,100,40);
        FreeLayout moved=bridge.insertionPreview("moving",0,80,160,40);
        check(moved.item("left").y==124&&moved.item("right").y==80,"Insertion affects only intersecting columns");
        check(moved.item("wide").y==168&&moved.item("below-right").y==212,"Displacement propagates through a wide control into another row");
        check(noOverlap(moved),"Wide bridge remains overlap-free");unchangedExceptDown(bridge,moved,"moving");

        FreeLayout legacy=new FreeLayout(360,100);add(legacy,"moving",0,240,100,40);add(legacy,"above",0,0,100,46);add(legacy,"below",0,80,100,40);
        FreeLayout gap=legacy.insertionPreview("moving",0,49,100,40);
        check(gap.item("moving").y==52&&gap.item("above").y==0,"Snapping into a legacy gap never pushes the item above the gap");
        check(gap.item("below").y==96&&noOverlap(gap),"Only lower rows move when enlarging a legacy gap");

        FreeLayout edge=new FreeLayout(360,100);add(edge,"moving",0,0,100,40);add(edge,"last",0,15920,100,80);String edgeBefore=edge.encode();
        check(!edge.insert("moving",0,15916,100,40),"Insertion fails atomically if displaced content exceeds maximum height");
        check(edge.encode().equals(edgeBefore),"No item disappears on failed insertion");
        check(!edge.insert("moving",-1,0,100,40)&&!edge.insert("moving",320,0,100,40)&&!edge.insert("moving",0,0,Integer.MAX_VALUE,40),"Out-of-bounds and overflowing insertions rejected");

        LayoutDrafts edit=new LayoutDrafts();String key="freeLayout.v1.portrait.0";FreeLayout draft=edit.edit(key,base);
        check(draft.insert("moving",11,73,100,40)&&edit.dirty(),"Inserted layout remains an unsaved editing draft");
        check(base.encode().equals(original),"Saved arrangement is not changed by cascade");
        FreeLayout saved=FreeLayout.decode(LayoutDrafts.decode(edit.encode()).savedValues().get(key));
        check(saved.item("first").y==116&&saved.item("second").y==160,"Save and activity-state recovery retain complete cascade");
        check(FreeLayout.decode(original).item("first").y==80&&FreeLayout.decode(original).item("second").y==128,"Discard restores all previous neighbor positions");
        FreeLayout showing=fixture();check(showing.show("hidden",true),"Showing hidden item inserts into its old slot");
        check(showing.item("hidden").y==80&&showing.item("first").y>80&&noOverlap(showing),"Showing an item pushes existing controls instead of overlapping");

        Random random=new Random(505);FreeLayout stress=new FreeLayout(360,100);
        for(int n=0;n<24;n++)add(stress,"control."+n,8+(n%3)*116,8+(n/3)*56,100,48);
        for(int n=0;n<500;n++){
            String id="control."+random.nextInt(24);FreeLayout before=stress.copy();String encoded=before.encode();
            FreeLayout next=before.insertionPreview(id,random.nextInt(300),random.nextInt(800),100,48);
            check(before.encode().equals(encoded),"Random preview keeps baseline immutable "+n);
            if(next!=null){
                check(noOverlap(next),"Random insertion never overlaps "+n);
                check(next.item(id).x%4==0&&next.item(id).y%4==0,"Random insertion snaps selected control "+n);
                unchangedExceptDown(before,next,id);stress.applyFrom(next);
                check(FreeLayout.decode(stress.encode()).encode().equals(stress.encode()),"Random saved cascade round trips "+n);
                if(n%5==0){stress.applyFrom(before);check(stress.encode().equals(encoded),"Random cancellation restores original arrangement "+n);}
            }
        }
        System.out.println("PASS: "+count+" grid, insertion, cascade and rollback checks");
    }
}
