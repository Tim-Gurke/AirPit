package de.airpit.app.core;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.Instant;
import java.util.*;

/** Versioned UTF-8 backup. Only portable preferences cross this boundary. */
public final class SettingsBackup {
    public static final int MAX_BYTES=2*1024*1024;
    private static final String PREFIX="preference.";
    private static final Map<String,Object> DEFAULTS=new LinkedHashMap<>();
    static {
        DEFAULTS.put("dark",true);DEFAULTS.put("help",false);
        DEFAULTS.put("uiScale",100);DEFAULTS.put("touchpadSpeed",100);
        DEFAULTS.put("speed",50);DEFAULTS.put("smooth",35);DEFAULTS.put("steady",30);
        DEFAULTS.put("gyroHorizontal",MotionAxes.TURN);DEFAULTS.put("gyroVertical",MotionAxes.FORWARD_TILT);
        DEFAULTS.put("invertX",false);DEFAULTS.put("invertY",false);
    }
    public final String appVersion,createdUtc;
    public final Map<String,Object> values;
    private SettingsBackup(String version,String created,Map<String,Object> values){
        appVersion=version;createdUtc=created;this.values=Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
    public int layoutCount(){return values.size()-DEFAULTS.size();}
    public static boolean layoutKey(String key){return key.matches("freeLayout\\.v1\\.(portrait|landscape)\\.(0|1|2|full)")||key.equals("layout.portrait")||key.equals("layout.landscape");}
    public static SettingsBackup capture(Map<String,?> preferences,String version,Instant created){
        Map<String,Object> safe=new LinkedHashMap<>();
        for(Map.Entry<String,Object> field:DEFAULTS.entrySet())safe.put(field.getKey(),preferences.containsKey(field.getKey())?preferences.get(field.getKey()):field.getValue());
        for(String key:new TreeSet<>(preferences.keySet()))if(layoutKey(key)){
            Object raw=preferences.get(key);if(!(raw instanceof String))throw invalid();
            String value=(String)raw;
            if(key.startsWith("freeLayout.")){
                FreeLayout layout=checkedLayout(value);
                // Paired-PC buttons contain Bluetooth addresses and belong to this phone.
                layout.items.keySet().removeIf(id->id.startsWith("pc."));value=layout.encode();
            }
            safe.put(key,value);
        }
        validate(safe,false);return new SettingsBackup(checkedVersion(version),created.toString(),safe);
    }
    public byte[] encode() throws IOException {
        Properties properties=new Properties();properties.setProperty("airpit.format","de.airpit.settings");properties.setProperty("airpit.version","1");
        properties.setProperty("airpit.appVersion",appVersion);properties.setProperty("airpit.createdUtc",createdUtc);
        for(Map.Entry<String,Object> field:values.entrySet())properties.setProperty(PREFIX+field.getKey(),field.getValue().toString());
        StringWriter writer=new StringWriter();properties.store(writer,"AirPit settings and saved arrangements");
        byte[] bytes=writer.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BYTES)throw invalid();return bytes;
    }
    public static SettingsBackup read(InputStream input) throws IOException {
        if(input==null)throw new IOException("Datei kann nicht geöffnet werden.");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int count;
        while((count=input.read(chunk))!=-1){if(bytes.size()>MAX_BYTES-count)throw new IOException("Die Sicherung ist zu groß (höchstens 2 MB).");bytes.write(chunk,0,count);}
        try{
            String source=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
            Properties properties=new Properties(){@Override public synchronized Object put(Object key,Object value){if(containsKey(key))throw invalid();return super.put(key,value);}};
            properties.load(new StringReader(source));
            if(!"de.airpit.settings".equals(properties.getProperty("airpit.format")))throw invalid();
            if(!"1".equals(properties.getProperty("airpit.version")))throw new IllegalArgumentException("Diese Sicherungsversion wird nicht unterstützt. Bitte AirPit aktualisieren.");
            String version=checkedVersion(properties.getProperty("airpit.appVersion")),created=properties.getProperty("airpit.createdUtc");
            if(created==null||!created.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9:.]+Z"))throw invalid();Instant.parse(created);
            Map<String,Object> values=new LinkedHashMap<>();
            for(String name:properties.stringPropertyNames()){
                if(name.equals("airpit.format")||name.equals("airpit.version")||name.equals("airpit.appVersion")||name.equals("airpit.createdUtc"))continue;
                if(!name.startsWith(PREFIX))throw invalid();String key=name.substring(PREFIX.length()),value=properties.getProperty(name);
                Object fallback=DEFAULTS.get(key);
                if(fallback instanceof Boolean){if(!value.equals("true")&&!value.equals("false"))throw invalid();values.put(key,Boolean.valueOf(value));}
                else if(fallback instanceof Integer){int number=Integer.parseInt(value);if(!value.equals(Integer.toString(number)))throw invalid();values.put(key,number);}
                else if(layoutKey(key))values.put(key,value);else throw invalid();
            }
            validate(values,true);return new SettingsBackup(version,created,values);
        }catch(CharacterCodingException|IllegalArgumentException|java.time.DateTimeException|NullPointerException e){throw new IOException(e instanceof IllegalArgumentException&&e.getMessage()!=null?e.getMessage():"Keine gültige AirPit-Sicherung. Es wurde nichts übernommen.",e);}
    }
    private static String checkedVersion(String version){if(version==null||!version.matches("[0-9]{1,4}\\.[0-9]{1,4}\\.[0-9]{1,4}"))throw invalid();return version;}
    private static void validate(Map<String,Object> values,boolean importing){
        for(Map.Entry<String,Object> field:DEFAULTS.entrySet()){
            String key=field.getKey();Object value=values.get(key);if(value==null||value.getClass()!=field.getValue().getClass())throw invalid();
            if(value instanceof Integer){int number=(Integer)value,min=0,max=100;if(key.equals("uiScale")){min=50;max=150;}else if(key.equals("touchpadSpeed")){min=25;max=300;}else if(key.startsWith("gyro"))max=2;if(number<min||number>max)throw invalid();}
        }
        if(values.get("gyroHorizontal").equals(values.get("gyroVertical")))throw invalid();
        for(Map.Entry<String,Object> field:values.entrySet())if(!DEFAULTS.containsKey(field.getKey())){
            String key=field.getKey();if(!layoutKey(key)||!(field.getValue() instanceof String))throw invalid();String value=(String)field.getValue();
            if(key.startsWith("freeLayout.")){
                FreeLayout layout=checkedLayout(value);
                if(importing)for(String id:layout.items.keySet())if(id.startsWith("pc."))throw invalid();
                for(String required:new String[]{"button.Anordnen","button.← Zurück"})if(layout.item(required)!=null&&!layout.item(required).visible)throw invalid();
            }else if(!TileLayout.decode(value,key.endsWith("landscape")).encode().equals(value))throw invalid();
        }
    }
    private static FreeLayout checkedLayout(String value){
        FreeLayout layout=FreeLayout.decode(value);
        // The tolerant on-device decoder repairs old data; imports must be valid in full.
        if(layout==null||layout.width>8192||layout.items.isEmpty()||!layout.valid()||!layout.encode().equals(value))throw invalid();return layout;
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Keine gültige AirPit-Sicherung. Es wurde nichts übernommen.");}
    /** Overlay only the validated portable fields, retaining local pairing and other layouts. */
    public Map<String,Object> mergedWith(Map<String,?> current){Map<String,Object> result=new LinkedHashMap<>(current);result.putAll(values);return result;}
}
