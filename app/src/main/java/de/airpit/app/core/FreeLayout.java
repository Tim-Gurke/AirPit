package de.airpit.app.core;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Individual control rectangles. Coordinates are density-independent, never text drafts. */
public final class FreeLayout {
    public static final int GAP=3,MAX_Y=16000;
    public static final class Item {
        public final String id;
        public int x,y,width,height;
        public boolean visible=true;
        public Item(String id,int x,int y,int width,int height){this.id=id;this.x=x;this.y=y;this.width=width;this.height=height;}
        public Item copy(){Item r=new Item(id,x,y,width,height);r.visible=visible;return r;}
    }
    public final LinkedHashMap<String,Item> items=new LinkedHashMap<>();
    public int width,scale;
    public FreeLayout(int width,int scale){this.width=Math.max(80,width);this.scale=Math.max(50,Math.min(150,scale));}
    public FreeLayout copy(){FreeLayout r=new FreeLayout(width,scale);for(Item i:items.values())r.items.put(i.id,i.copy());return r;}
    public Item item(String id){return items.get(id);}
    public int bottom(){int b=0;for(Item i:items.values())if(i.visible)b=Math.max(b,i.y+i.height);return b;}
    private static boolean intersects(Item a,Item b){return a.x<b.x+b.width+GAP&&a.x+a.width+GAP>b.x&&a.y<b.y+b.height+GAP&&a.y+a.height+GAP>b.y;}
    private boolean canPlace(Item a,Collection<Item> others){
        if(a.width<1||a.width>width||a.height<1||a.height>2000||a.x<0||a.y<0||(long)a.x+a.width>width||(long)a.y+a.height>MAX_Y)return false;
        if(!a.visible)return true;
        for(Item b:others)if(b.visible&&!a.id.equals(b.id)&&intersects(a,b))return false;
        return true;
    }
    public boolean canPlace(String id,int x,int y,int w,int h){Item old=item(id);if(old==null)return false;Item r=new Item(id,x,y,w,h);r.visible=old.visible;return canPlace(r,items.values());}
    public boolean place(String id,int x,int y,int w,int h){
        if(!canPlace(id,x,y,w,h))return false;
        Item r=item(id);r.x=x;r.y=y;r.width=w;r.height=h;return true;
    }
    private void fit(Item a,Collection<Item> placed){
        a.width=Math.max(1,Math.min(width,a.width));a.height=Math.max(1,Math.min(2000,a.height));
        a.x=Math.max(0,Math.min(width-a.width,a.x));a.y=Math.max(0,Math.min(MAX_Y-a.height,a.y));
        if(!a.visible||canPlace(a,placed))return;
        int desiredX=a.x,desiredY=a.y,bestX=0,bestY=MAX_Y;long best=Long.MAX_VALUE;
        TreeSet<Integer> xs=new TreeSet<>(),ys=new TreeSet<>();xs.add(0);xs.add(desiredX);xs.add(width-a.width);ys.add(0);ys.add(desiredY);
        for(Item b:placed)if(b.visible){xs.add(b.x+b.width+GAP);xs.add(b.x-a.width-GAP);ys.add(b.y+b.height+GAP);ys.add(b.y-a.height-GAP);}
        for(int y:ys)for(int x:xs){a.x=x;a.y=y;if(canPlace(a,placed)){long dx=x-desiredX,dy=y-desiredY,cost=dx*dx+dy*dy;if(cost<best){best=cost;bestX=x;bestY=y;}}}
        if(best!=Long.MAX_VALUE){a.x=bestX;a.y=bestY;return;}
        int bottom=0;for(Item b:placed)if(b.visible)bottom=Math.max(bottom,b.y+b.height+GAP);
        a.x=0;a.y=Math.min(MAX_Y-a.height,bottom);
        if(!canPlace(a,placed))a.visible=false;
    }
    public void repair(){List<Item> placed=new ArrayList<>();for(Item a:items.values()){fit(a,placed);placed.add(a);}}
    public boolean valid(){for(Item a:items.values())if(!canPlace(a,items.values()))return false;return true;}
    public boolean show(String id,boolean visible){Item a=item(id);if(a==null)return false;a.visible=visible;if(visible)fit(a,items.values());return a.visible==visible;}
    public void adapt(int nextWidth,int nextScale){
        nextWidth=Math.max(80,nextWidth);nextScale=Math.max(50,Math.min(150,nextScale));
        if(nextWidth==width&&nextScale==scale)return;
        double horizontal=(double)nextWidth/width,vertical=(double)nextScale/scale;
        for(Item i:items.values()){i.x=(int)Math.round(i.x*horizontal);i.width=(int)Math.round(i.width*horizontal);i.y=(int)Math.round(i.y*vertical);if(!i.id.equals("pad"))i.height=(int)Math.round(i.height*vertical);}
        width=nextWidth;scale=nextScale;repair();
    }
    /** Merge a saved arrangement with the current catalog, adding new controls safely. */
    public static FreeLayout merge(FreeLayout saved,FreeLayout defaults){
        FreeLayout r=saved==null?defaults.copy():saved.copy();r.adapt(defaults.width,defaults.scale);
        r.items.keySet().retainAll(defaults.items.keySet());
        for(Item d:defaults.items.values())if(!r.items.containsKey(d.id))r.items.put(d.id,d.copy());
        r.repair();return r;
    }
    public String encode(){
        StringBuilder b=new StringBuilder("F1;").append(width).append(';').append(scale).append('\n');
        for(Item i:items.values())b.append(encoded(i.id)).append(':').append(i.x).append(':').append(i.y).append(':').append(i.width).append(':').append(i.height).append(':').append(i.visible?1:0).append('\n');
        return b.toString();
    }
    public static FreeLayout decode(String value){
        if(value==null||value.length()>250000||!value.startsWith("F1;"))return null;
        try{
            String[] lines=value.split("\n"),head=lines[0].split(";");
            FreeLayout r=new FreeLayout(Integer.parseInt(head[1]),Integer.parseInt(head[2]));
            for(int n=1;n<lines.length&&n<=400;n++)try{
                String[] f=lines[n].split(":");if(f.length!=6)continue;String id=decoded(f[0]);if(id.length()>1000||r.items.containsKey(id))continue;
                Item i=new Item(id,Integer.parseInt(f[1]),Integer.parseInt(f[2]),Integer.parseInt(f[3]),Integer.parseInt(f[4]));i.visible="1".equals(f[5]);r.items.put(id,i);
            }catch(RuntimeException ignored){}
            r.repair();return r;
        }catch(RuntimeException e){return null;}
    }
    static String encoded(String s){return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
    static String decoded(String s){return new String(Base64.getUrlDecoder().decode(s),StandardCharsets.UTF_8);}
}
