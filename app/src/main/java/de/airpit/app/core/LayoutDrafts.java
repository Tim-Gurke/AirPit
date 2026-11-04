package de.airpit.app.core;

import java.util.*;

/** Editing transaction across pages and orientations. Only the caller's Save persists it. */
public final class LayoutDrafts {
    private final Map<String,String> originals=new LinkedHashMap<>();
    private final Map<String,FreeLayout> drafts=new LinkedHashMap<>();
    public FreeLayout edit(String key,FreeLayout saved){
        if(!drafts.containsKey(key)){originals.put(key,saved.encode());drafts.put(key,saved.copy());}
        return drafts.get(key);
    }
    public FreeLayout replace(String key,FreeLayout layout){FreeLayout draft=layout.copy();drafts.put(key,draft);return draft;}
    public boolean dirty(){for(String key:drafts.keySet())if(!drafts.get(key).encode().equals(originals.get(key)))return true;return false;}
    public Map<String,String> savedValues(){
        Map<String,String> values=new LinkedHashMap<>();
        for(String key:drafts.keySet()){FreeLayout l=drafts.get(key);if(!l.valid())throw new IllegalStateException("Invalid layout");values.put(key,l.encode());}
        return values;
    }
    public String encode(){StringBuilder b=new StringBuilder("D1\n");for(String key:drafts.keySet())b.append(FreeLayout.encoded(key)).append('\t').append(FreeLayout.encoded(originals.get(key))).append('\t').append(FreeLayout.encoded(drafts.get(key).encode())).append('\n');return b.toString();}
    public static LayoutDrafts decode(String value){
        LayoutDrafts r=new LayoutDrafts();if(value==null||value.length()>1500000||!value.startsWith("D1\n"))return r;
        for(String line:value.substring(3).split("\n"))try{
            String[] f=line.split("\t");if(f.length!=3)continue;String key=FreeLayout.decoded(f[0]),original=FreeLayout.decoded(f[1]);FreeLayout draft=FreeLayout.decode(FreeLayout.decoded(f[2]));
            if(key.startsWith("freeLayout.")&&FreeLayout.decode(original)!=null&&draft!=null){r.originals.put(key,original);r.drafts.put(key,draft);}
        }catch(RuntimeException ignored){}
        return r;
    }
}
