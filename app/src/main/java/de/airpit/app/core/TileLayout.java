package de.airpit.app.core;

import java.util.*;

/** Small, versioned orientation-specific layout. Text drafts are never serialized here. */
public final class TileLayout {
    public static final String[] IDS={"pad","clicks","wheel","air","text","keys"};
    public static final class Tile {
        public final String id;public int span=4,height;public boolean visible=true;
        Tile(String id){this.id=id;}
    }
    public final List<Tile> tiles=new ArrayList<>();
    public TileLayout(){for(String id:IDS)tiles.add(new Tile(id));preset(0,false);}
    public Tile tile(String id){for(Tile t:tiles)if(t.id.equals(id))return t;return null;}
    public void preset(int preset,boolean landscape){
        tiles.clear();String[] order=preset==1?new String[]{"air","pad","clicks","wheel","text","keys"}:IDS;
        for(String id:order){Tile t=new Tile(id);t.visible=!id.equals("text")&&!id.equals("keys");t.height=id.equals("pad")?(landscape?220:320):0;tiles.add(t);}
        if(preset==1)tile("pad").height=200;
        if(preset==2){tile("pad").height=180;tile("clicks").span=tile("wheel").span=2;}
        if(preset==3){tile("pad").height=240;tile("text").visible=tile("keys").visible=true;tile("air").visible=false;tile("wheel").visible=false;}
    }
    public void move(String from,String before){Tile a=tile(from),b=tile(before);if(a==null||b==null||a==b)return;tiles.remove(a);tiles.add(tiles.indexOf(b),a);}
    public String encode(){StringBuilder s=new StringBuilder("v1;");for(Tile t:tiles)s.append(t.id).append(':').append(t.span).append(':').append(t.height).append(':').append(t.visible?'1':'0').append(';');return s.toString();}
    public static TileLayout decode(String value,boolean landscape){
        TileLayout l=new TileLayout();l.preset(0,landscape);if(value==null||!value.startsWith("v1;"))return l;
        List<Tile> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(String part:value.substring(3).split(";"))try{
            String[] f=part.split(":");if(f.length!=4||!seen.add(f[0]))continue;Tile t=l.tile(f[0]);if(t==null)continue;
            t.span=Math.max(1,Math.min(4,Integer.parseInt(f[1])));t.height=Math.max(0,Math.min(720,Integer.parseInt(f[2])));if(t.id.equals("pad"))t.height=Math.max(80,t.height);t.visible="1".equals(f[3]);result.add(t);
        }catch(RuntimeException ignored){}
        for(Tile t:l.tiles)if(!result.contains(t))result.add(t);l.tiles.clear();l.tiles.addAll(result);return l;
    }
}
