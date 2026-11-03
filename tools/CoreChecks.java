import de.airpit.app.core.*;
import java.util.*;

public final class CoreChecks {
    private static int count;
    private static void check(boolean v,String name){count++;if(!v)throw new AssertionError(name);}
    private static GermanKeyboard.Stroke first(String s){return GermanKeyboard.encode(s).get(0);}
    public static void main(String[] args){
        check(first("y").key==29&&first("z").key==28,"German Y/Z");
        check(first("Z").key==28&&first("Z").modifiers==2,"German uppercase Z");
        check(first("ä").key==52&&first("Ö").key==51&&first("Ö").modifiers==2,"Umlauts");
        check(first("ß").key==45,"Sharp s");
        check(first("@").modifiers==64&&first("@").key==20,"AltGr @");
        check(first("€").modifiers==64&&first("€").key==8,"AltGr euro");
        check(first("\\").modifiers==64&&first("\\").key==45,"Windows paths");
        check(GermanKeyboard.encode("^~`´").size()==7,"Dead keys released with space, tilde direct");
        check(first("#").key==49&&first("'").key==49&&first("'").modifiers==2,"German hash/apostrophe");
        check(GermanKeyboard.encode("a\r\nb\rc").size()==5,"Line endings");
        check(GermanKeyboard.encode("a\u0308").size()==1&&first("a\u0308").key==52,"Composed Unicode");
        boolean rejected=false;try{GermanKeyboard.encode("ok 😀");}catch(IllegalArgumentException e){rejected=true;}check(rejected,"Reject entire unsupported text");
        for(int c=32;c<127;c++)check(!GermanKeyboard.encode(String.valueOf((char)c)).isEmpty(),"Printable ASCII "+c);
        check(GermanKeyboard.encode("Hallo Tim! Grüße aus Hockenheim: 123,45 €").size()==41,"Realistic German phrase");
        byte[] mouse=HidReports.mouse(255,900,-900,-2);
        check(mouse.length==4&&mouse[0]==7&&mouse[1]==127&&mouse[2]==-127&&mouse[3]==-2,"Signed mouse reports");
        byte[] key=HidReports.keyboard(64,20);check(key.length==8&&key[0]==64&&key[2]==20&&key[7]==0,"Keyboard report layout");
        // Independently decode HID item lengths; check actual report byte counts.
        Map<Integer,Integer> inputBits=new HashMap<>(),outputBits=new HashMap<>();
        byte[] d=HidReports.DESCRIPTOR;int id=0,size=0,n=0,depth=0;
        for(int p=0;p<d.length;){int tag=d[p++]&255,len=tag&3;if(len==3)len=4;int value=0;for(int j=0;j<len;j++)value|=(d[p++]&255)<<(8*j);
            switch(tag&252){case 0x84:id=value;break;case 0x74:size=value;break;case 0x94:n=value;break;
                case 0x80:inputBits.put(id,inputBits.getOrDefault(id,0)+size*n);break;
                case 0x90:outputBits.put(id,outputBits.getOrDefault(id,0)+size*n);break;
                case 0xA0:depth++;break;case 0xC0:depth--;break;default:break;}
        }
        check(depth==0,"Descriptor collections balanced");
        check(inputBits.get(1)==32&&inputBits.get(2)==64&&outputBits.get(2)==8,"Descriptor agrees with wire reports");
        MotionFilter f=new MotionFilter();int total=0;
        for(int i=0;i<100;i++)total+=f.step(0.1,0,0.01,100,0.001,0,false,false)[0];
        check(total>=9&&total<=10,"Subpixel integration");
        f.reset();int[] zero=f.step(0.001,0.001,0.02,1300,0.01,0.01,false,false);check(zero[0]==0&&zero[1]==0,"Jitter deadzone");
        f.reset();int[] normal=f.step(1,1,0.02,1000,0.001,0,false,false);f.reset();int[] inverted=f.step(1,1,0.02,1000,0.001,0,true,true);
        check(normal[0]==-inverted[0]&&normal[1]==-inverted[1],"Axis inversion");
        check(Arrays.equals(f.step(1,1,1,1000,0.01,0,false,false),new int[]{0,0}),"No jump after pause");
        f.reset();check(f.step(100,100,0.02,100000,0.001,0,false,false)[0]==127,"Motion saturates safely");
        check(Arrays.equals(f.step(Double.NaN,0,0.02,1000,0.01,0,false,false),new int[]{0,0}),"Invalid sensor data");
        System.out.println("PASS: "+count+" HID, German keyboard and motion checks");
    }
}
