import de.airpit.app.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

public final class BackupChecks {
    private static int checks;
    private static void check(boolean result,String message){checks++;if(!result)throw new AssertionError(message);}
    private static SettingsBackup read(byte[] bytes)throws IOException{return SettingsBackup.read(new ByteArrayInputStream(bytes));}
    private static Properties properties(byte[] bytes)throws IOException{Properties p=new Properties();p.load(new StringReader(new String(bytes,StandardCharsets.UTF_8)));return p;}
    private static byte[] bytes(Properties p)throws IOException{StringWriter out=new StringWriter();p.store(out,null);return out.toString().getBytes(StandardCharsets.UTF_8);}
    private static void rejected(byte[] data,String message)throws Exception{try{read(data);throw new AssertionError(message);}catch(IOException expected){checks++;}}
    public static void main(String[] args)throws Exception{
        Map<String,Object> original=new LinkedHashMap<>();original.put("touchpadSpeed",237);original.put("dark",false);original.put("gyroHorizontal",1);original.put("gyroVertical",2);
        original.put("lastHost","AA:BB:CC:DD:EE:FF");original.put("notificationAsked",true);original.put("screenOff",true);original.put("pin","123456");original.put("textDraft","Privater Text");
        FreeLayout layout=new FreeLayout(360,100);layout.items.put("button.Anordnen",new FreeLayout.Item("button.Anordnen",8,8,100,48));
        layout.items.put("pad",new FreeLayout.Item("pad",8,64,340,240));layout.items.put("pc.AA:BB:CC:DD:EE:FF",new FreeLayout.Item("pc.AA:BB:CC:DD:EE:FF",8,320,120,48));
        FreeLayout.Item hidden=new FreeLayout.Item("button.Ändern",200,320,100,48);hidden.visible=false;layout.items.put(hidden.id,hidden);
        for(String orientation:new String[]{"portrait","landscape"})for(String page:new String[]{"0","1","2","full"})original.put("freeLayout.v1."+orientation+"."+page,layout.encode());
        original.put("layout.portrait",new TileLayout().encode());original.put("layout.landscape",new TileLayout().encode());
        SettingsBackup captured=SettingsBackup.capture(original,"0.0.7",Instant.parse("2026-11-05T08:00:00Z"));byte[] file=captured.encode();SettingsBackup restored=read(file);
        check(captured.values.equals(restored.values),"File round trip preserves all portable values");check(restored.layoutCount()==10,"All eight free and two legacy layouts");
        check(restored.values.get("touchpadSpeed").equals(237),"Custom speed survives");check(restored.values.get("uiScale").equals(100),"Missing settings use app defaults");
        String contents=new String(file,StandardCharsets.UTF_8);for(String secret:new String[]{"AA:BB:CC:DD:EE:FF","123456","Privater Text","lastHost","notificationAsked","screenOff","textDraft"})check(!contents.contains(secret),"Private field excluded: "+secret);
        FreeLayout imported=FreeLayout.decode((String)restored.values.get("freeLayout.v1.portrait.0"));check(imported.items.size()==3,"PC identifiers stripped");check(!imported.item("button.Ändern").visible,"Unicode and hidden tile retained");
        imported.adapt(280,125);check(imported.valid(),"Different display adapts without overlaps");
        Map<String,Object> current=new LinkedHashMap<>();current.put("lastHost","LOCAL-PC");current.put("screenOff",false);current.put("notificationAsked",false);current.put("untouched","local");
        Map<String,Object> merged=restored.mergedWith(current);check(merged.get("lastHost").equals("LOCAL-PC")&&!((Boolean)merged.get("screenOff")),"Local pairing and opt-in preserved");check(merged.get("untouched").equals("local"),"Unrelated fields retained");check(current.size()==4,"Preview does not mutate current state");
        SettingsBackup defaults=SettingsBackup.capture(Collections.emptyMap(),"0.0.7",Instant.EPOCH);check(read(defaults.encode()).layoutCount()==0,"Fresh install backup valid");
        Map<String,Object> partial=defaults.mergedWith(original);check(partial.get("freeLayout.v1.portrait.0").equals(original.get("freeLayout.v1.portrait.0")),"Absent layouts remain untouched");
        for(String key:new String[]{"touchpadSpeed","uiScale","speed","smooth","steady","gyroHorizontal","gyroVertical"})for(String bad:new String[]{"-1","301","1.5","abc","2147483648","01"}){Properties p=properties(file);p.setProperty("preference."+key,bad);rejected(bytes(p),"Reject bad number "+key);}
        for(String key:new String[]{"dark","help","invertX","invertY"}){Properties p=properties(file);p.setProperty("preference."+key,"yes");rejected(bytes(p),"Strict boolean");}
        Properties equalAxes=properties(file);equalAxes.setProperty("preference.gyroVertical","1");rejected(bytes(equalAxes),"Duplicate axes");
        for(String key:new String[]{"lastHost","screenOff","notificationAsked","pin","unknown"}){Properties p=properties(file);p.setProperty("preference."+key,"true");rejected(bytes(p),"Unknown/device-only fields rejected");}
        Properties withAddress=properties(file);withAddress.setProperty("preference.freeLayout.v1.portrait.2",layout.encode());rejected(bytes(withAddress),"Imported PC address rejected");
        for(String bad:new String[]{"garbage","F1;360;100\n","F1;360;100\n!!!:0:0:10:10:1\n",layout.encode().replace(":8:64:",":8:8:")}){Properties p=properties(file);p.setProperty("preference.freeLayout.v1.portrait.0",bad);rejected(bytes(p),"Malformed/overlapping layout");}
        FreeLayout missingAccess=FreeLayout.decode((String)restored.values.get("freeLayout.v1.portrait.0"));missingAccess.item("button.Anordnen").visible=false;Properties hiddenEditor=properties(file);hiddenEditor.setProperty("preference.freeLayout.v1.portrait.0",missingAccess.encode());rejected(bytes(hiddenEditor),"Editor remains reachable");
        for(String key:new String[]{"airpit.format","airpit.version","airpit.appVersion","airpit.createdUtc","preference.touchpadSpeed"}){Properties p=properties(file);p.remove(key);rejected(bytes(p),"Missing required "+key);}
        Properties future=properties(file);future.setProperty("airpit.version","2");rejected(bytes(future),"Future version");
        for(String date:new String[]{"not-a-date","2026-99-99T99:00:00Z",""}){Properties p=properties(file);p.setProperty("airpit.createdUtc",date);rejected(bytes(p),"Invalid date");}
        rejected((contents+"\npreference.dark=true\n").getBytes(StandardCharsets.UTF_8),"Duplicate keys");rejected(new byte[]{(byte)0xc3,(byte)0x28},"Invalid UTF-8");rejected(new byte[0],"Empty file");rejected(new byte[SettingsBackup.MAX_BYTES+1],"Size bound");
        try{SettingsBackup.read(new InputStream(){public int read()throws IOException{throw new IOException("Provider offline");}});throw new AssertionError("Read failure");}catch(IOException expected){checks++;}
        for(int n=0;n<150;n++){Map<String,Object> sample=new HashMap<>(original);sample.put("touchpadSpeed",25+n%276);sample.put("uiScale",50+n%101);sample.put("speed",n%101);sample.put("dark",n%2==0);SettingsBackup b=SettingsBackup.capture(sample,"0.0.7",Instant.ofEpochSecond(n));check(read(b.encode()).values.equals(b.values),"Repeated complete round trip");}
        check(original.get("lastHost").equals("AA:BB:CC:DD:EE:FF"),"Export leaves local preferences unchanged");
        System.out.println("PASS: "+checks+" backup round-trip, validation, privacy and merge checks");
    }
}
