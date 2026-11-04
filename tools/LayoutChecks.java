import de.airpit.app.core.*;
import java.util.*;

public final class LayoutChecks {
    private static int count;
    private static void check(boolean ok,String message){count++;if(!ok)throw new AssertionError(message);}
    private static FreeLayout layout(){
        FreeLayout l=new FreeLayout(360,100);
        l.items.put("button.Linksklick",new FreeLayout.Item("button.Linksklick",12,12,100,48));
        l.items.put("button.Rechtsklick",new FreeLayout.Item("button.Rechtsklick",124,12,100,48));
        l.items.put("pad",new FreeLayout.Item("pad",12,80,336,240));return l;
    }
    private static boolean geometryValid(FreeLayout l){
        List<FreeLayout.Item> visible=new ArrayList<>();
        for(FreeLayout.Item i:l.items.values())if(i.visible){
            if(i.x<0||i.y<0||i.width<=0||i.height<=0||(long)i.x+i.width>l.width||(long)i.y+i.height>FreeLayout.MAX_Y)return false;
            for(FreeLayout.Item other:visible)if(Math.max(i.x,other.x)<Math.min((long)i.x+i.width,(long)other.x+other.width)&&Math.max(i.y,other.y)<Math.min((long)i.y+i.height,(long)other.y+other.height))return false;
            visible.add(i);
        }return true;
    }
    public static void main(String[] args){
        FreeLayout saved=layout();check(saved.valid()&&geometryValid(saved),"Initial individual controls do not overlap");
        String original=saved.encode();
        check(!saved.place("button.Linksklick",130,15,100,48),"Cannot overlap another button");check(saved.encode().equals(original),"Rejected move leaves arrangement untouched");
        check(!saved.place("button.Linksklick",20,100,100,48),"Cannot overlap touchpad");
        check(!saved.place("button.Linksklick",-1,2,100,48),"Cannot move outside left edge");
        check(!saved.place("button.Linksklick",300,2,100,48),"Cannot move outside right edge");
        check(!saved.place("button.Linksklick",100,2,Integer.MAX_VALUE,48),"Overflowing geometry rejected");
        check(!saved.place("button.Linksklick",2,10,100,Integer.MAX_VALUE),"Overflowing height rejected");
        check(!saved.place("button.Linksklick",12,12,220,48),"Cannot resize across another button");
        check(saved.place("button.Linksklick",236,12,100,48),"An individual button can move across its original group");
        check(saved.place("button.Linksklick",0,0,100,48),"Entire usable surface includes upper corner");
        check(saved.item("button.Rechtsklick").x==124&&saved.item("pad").y==80,"Moving one button leaves others independent");
        check(FreeLayout.decode(saved.encode()).encode().equals(saved.encode()),"Geometry and visibility survive reload");
        saved=layout();FreeLayout.Item hidden=new FreeLayout.Item("button.Ziehen",12,12,100,48);hidden.visible=false;saved.items.put(hidden.id,hidden);
        check(saved.show(hidden.id,true)&&geometryValid(saved),"Showing a button finds free space instead of overlapping");
        check(hidden.x==12&&hidden.y==12&&saved.item("button.Linksklick").y>12,"Showing a button inserts it at its old slot and pushes the occupant down");
        FreeLayout scaled=saved.copy();scaled.adapt(720,150);check(scaled.valid()&&geometryValid(scaled),"Scale and width changes preserve non-overlap");
        check(scaled.item("pad").height==saved.item("pad").height,"Touchpad height remains independent of text scaling");
        FreeLayout broken=layout();broken.item("pad").x=-100;broken.item("pad").y=0;broken.item("pad").width=10000;broken.repair();
        check(broken.valid()&&geometryValid(broken),"Damaged geometry is safely repaired");
        check(FreeLayout.decode("garbage")==null,"Unknown stored format falls back safely");
        FreeLayout template=layout();template.items.put("button.Neu",new FreeLayout.Item("button.Neu",12,12,100,48));
        FreeLayout merged=FreeLayout.merge(layout(),template);check(merged.items.size()==4&&geometryValid(merged),"New controls merge into saved arrangement without overlap");

        saved=layout();original=saved.encode();String portrait="freeLayout.v1.portrait.0",landscape="freeLayout.v1.landscape.0",keyboard="freeLayout.v1.portrait.1";
        LayoutDrafts edit=new LayoutDrafts();FreeLayout draft=edit.edit(portrait,saved);check(!edit.dirty(),"Entering editor does not change saved layout");
        check(draft.place("button.Linksklick",236,12,100,48),"Draft accepts a free position");draft.show("pad",false);
        check(edit.dirty()&&saved.encode().equals(original),"Changes stay provisional until Save");
        check(FreeLayout.decode(original).item("button.Linksklick").x==12&&FreeLayout.decode(original).item("pad").visible,"Discard restores original position and visibility");
        edit.edit(landscape,new FreeLayout(720,100)).items.put("button.Other",new FreeLayout.Item("button.Other",10,10,80,48));
        edit.edit(keyboard,layout()).place("button.Linksklick",236,12,100,48);
        LayoutDrafts restored=LayoutDrafts.decode(edit.encode());check(restored.dirty(),"Activity recreation preserves unsaved editing transaction");
        Map<String,String> committed=restored.savedValues();
        check(committed.size()==3,"Save includes edited pages and both orientations");
        check(FreeLayout.decode(committed.get(portrait)).item("button.Linksklick").x==236&&!FreeLayout.decode(committed.get(portrait)).item("pad").visible,"Explicit Save commits the intended draft");
        check(FreeLayout.decode(committed.get(landscape)).width==720,"Landscape draft stays independent");
        check(saved.encode().equals(original),"Collecting saved values never mutates prior stored state");
        LayoutDrafts clean=new LayoutDrafts();draft=clean.edit(portrait,saved);draft.place("button.Linksklick",236,12,100,48);draft.place("button.Linksklick",12,12,100,48);check(!clean.dirty(),"Returning to original layout needs no unsaved-changes prompt");
        check(LayoutDrafts.decode("bad").savedValues().isEmpty(),"Malformed activity state does not create saved layouts");

        Random random=new Random(417);
        FreeLayout stress=new FreeLayout(420,100);
        for(int i=0;i<24;i++)stress.items.put("control."+i,new FreeLayout.Item("control."+i,random.nextInt(400),random.nextInt(600),35+random.nextInt(80),32+random.nextInt(60)));
        stress.repair();check(geometryValid(stress),"Overlapping initial positions repair independently");
        for(int n=0;n<1500;n++){
            String id="control."+random.nextInt(24),before=stress.encode();
            boolean accepted=stress.place(id,random.nextInt(500)-30,random.nextInt(1100)-20,32+random.nextInt(180),32+random.nextInt(120));
            check(geometryValid(stress),"Random move/resize never creates overlap "+n);
            if(!accepted)check(before.equals(stress.encode()),"Rejected random move is atomic "+n);
        }
        System.out.println("PASS: "+count+" individual-control geometry and save/discard transaction checks");
    }
}
