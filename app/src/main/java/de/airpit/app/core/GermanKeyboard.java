package de.airpit.app.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/** HID usages for the Windows German (Germany) layout; no clipboard/PC agent. */
public final class GermanKeyboard {
    public static final int SHIFT=2, ALTGR=64;
    public static final class Stroke {
        public final int modifiers, key;
        public Stroke(int modifiers,int key) { this.modifiers=modifiers;this.key=key; }
    }
    private GermanKeyboard() {}
    public static List<Stroke> encode(String source) {
        String text=Normalizer.normalize(source,Normalizer.Form.NFC).replace("\r\n","\n").replace('\r','\n');
        List<Stroke> result=new ArrayList<>();
        for(int offset=0;offset<text.length();) {
            int c=text.codePointAt(offset); offset+=Character.charCount(c);
            Stroke[] next=character(c);
            if(next==null) throw new IllegalArgumentException("Zeichen nicht unterstützt: "+new String(Character.toChars(c))+" (U+"+Integer.toHexString(c).toUpperCase()+")");
            for(Stroke s:next) result.add(s);
        }
        return result;
    }
    private static Stroke[] key(int modifier,int code) { return new Stroke[]{new Stroke(modifier,code)}; }
    private static Stroke[] dead(int modifier,int code) { return new Stroke[]{new Stroke(modifier,code),new Stroke(0,44)}; }
    private static Stroke[] character(int c) {
        int lower=Character.toLowerCase(c);
        if(lower>='a'&&lower<='z') {
            int k=lower=='y'?29:lower=='z'?28:4+lower-'a';
            return key(Character.isUpperCase(c)?SHIFT:0,k);
        }
        if(c>='1'&&c<='9') return key(0,30+c-'1');
        if(c=='0') return key(0,39);
        switch(c) {
            case ' ': case 0x00A0:return key(0,44);
            case '\n':return key(0,40); case '\t':return key(0,43);
            case 'ä':return key(0,52); case 'Ä':return key(SHIFT,52);
            case 'ö':return key(0,51); case 'Ö':return key(SHIFT,51);
            case 'ü':return key(0,47); case 'Ü':return key(SHIFT,47);
            case 'ß':return key(0,45);
            case '!':return key(SHIFT,30); case '"':return key(SHIFT,31);
            case '§':return key(SHIFT,32); case '$':return key(SHIFT,33);
            case '%':return key(SHIFT,34); case '&':return key(SHIFT,35);
            case '/':return key(SHIFT,36); case '(':return key(SHIFT,37); case ')':return key(SHIFT,38);
            case '=':return key(SHIFT,39); case '?':return key(SHIFT,45);
            case '+':return key(0,48); case '*':return key(SHIFT,48);
            case '#':return key(0,49); case '\'':return key(SHIFT,49);
            case ',':return key(0,54); case ';':return key(SHIFT,54);
            case '.':return key(0,55); case ':':return key(SHIFT,55);
            case '-':return key(0,56); case '_':return key(SHIFT,56);
            case '<':return key(0,100); case '>':return key(SHIFT,100); case '|':return key(ALTGR,100);
            case '@':return key(ALTGR,20); case '€':return key(ALTGR,8);
            case '{':return key(ALTGR,36); case '[':return key(ALTGR,37);
            case ']':return key(ALTGR,38); case '}':return key(ALTGR,39);
            case '\\':return key(ALTGR,45); case '~':return key(ALTGR,48);
            case '^':return dead(0,53); case '°':return key(SHIFT,53);
            case '`':return dead(SHIFT,46); case '´':return dead(0,46);
            case '²':return key(ALTGR,31); case '³':return key(ALTGR,32); case 'µ':return key(ALTGR,16);
            case 'é':return new Stroke[]{new Stroke(0,46),new Stroke(0,8)};
            case 'è':return new Stroke[]{new Stroke(SHIFT,46),new Stroke(0,8)};
            case 'ê':return new Stroke[]{new Stroke(0,53),new Stroke(0,8)};
            // Common Gboard typographic punctuation: explicit documented equivalent.
            case '“':case '”':case '„':return key(SHIFT,31);
            case '‘':case '’':return key(SHIFT,49);
            case '–':case '—':return key(0,56);
            case '…':return new Stroke[]{new Stroke(0,55),new Stroke(0,55),new Stroke(0,55)};
            default:return null;
        }
    }
}
