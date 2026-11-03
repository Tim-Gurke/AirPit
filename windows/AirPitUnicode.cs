// AirPit Unicode, copyright (c) 2026 Tim Weißbach. MIT License.
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;
using System.Windows.Forms;
using InTheHand.Net.Sockets;

internal static class UnicodePlan {
    internal struct Key {internal ushort Code;internal bool Unicode,Up;internal Key(ushort c,bool u,bool up){Code=c;Unicode=u;Up=up;}}
    internal static List<Key> Build(string source) {
        if(source==null)throw new ArgumentException("Text fehlt.");
        string text=source.Replace("\r\n","\n").Replace('\r','\n');int count=0;
        for(int i=0;i<text.Length;i++){
            char c=text[i];if(Char.IsHighSurrogate(c)){if(i+1>=text.Length||!Char.IsLowSurrogate(text[++i]))throw new ArgumentException("Ungültiges Unicode-Zeichen.");}
            else if(Char.IsLowSurrogate(c))throw new ArgumentException("Ungültiges Unicode-Zeichen.");
            else if(Char.IsControl(c)&&c!='\n'&&c!='\t')throw new ArgumentException("Steuerzeichen nicht unterstützt.");
            if(++count>1000)throw new ArgumentException("Höchstens 1.000 Zeichen pro Sendung.");
        }
        List<Key> keys=new List<Key>();
        foreach(char c in text){ushort code=c=='\n'?(ushort)13:c=='\t'?(ushort)9:(ushort)c;bool unicode=c!='\n'&&c!='\t';keys.Add(new Key(code,unicode,false));keys.Add(new Key(code,unicode,true));}
        return keys;
    }
    internal static bool CodeMatches(string expected,string actual){if(actual==null||actual.Length!=expected.Length)return false;int difference=0;for(int i=0;i<expected.Length;i++)difference|=expected[i]^actual[i];return difference==0;}
    internal static string ReadLine(TextReader reader){StringBuilder b=new StringBuilder();for(int i=0;i<32768;i++){int c=reader.Read();if(c<0)throw new EndOfStreamException();if(c==10)return b.ToString();b.Append((char)c);}throw new IOException("Nachricht zu groß.");}
    internal static void SelfTest(){
        string family="👨‍👩‍👧‍👦",skin="👍🏽";
        List<Key> keys=Build("Hallo äöü ß € 😀 "+skin+" "+family+"\n");
        if(keys.Count%2!=0)throw new Exception("Pair count");
        for(int i=0;i<keys.Count;i+=2)if(keys[i].Up||!keys[i+1].Up||keys[i].Code!=keys[i+1].Code)throw new Exception("Key release");
        List<Key> emoji=Build("😀");if(emoji.Count!=4||emoji[0].Code!=0xD83D||emoji[2].Code!=0xDE00)throw new Exception("Surrogate pair");
        if(Build(skin).Count!=8||Build(family).Count!=family.Length*2)throw new Exception("Emoji sequences");
        if(Build("\r\n\t").Count!=4||Build("\n")[0].Unicode)throw new Exception("Line endings");
        bool rejected=false;try{Build("\uD800");}catch(ArgumentException){rejected=true;}if(!rejected)throw new Exception("Invalid surrogate");
        rejected=false;try{Build(new string('a',1001));}catch(ArgumentException){rejected=true;}if(!rejected)throw new Exception("Text bound");
        if(!CodeMatches("123456","123456")||CodeMatches("123456","123450")||CodeMatches("123456","12345"))throw new Exception("Pair code");
        if(ReadLine(new StringReader("hello\nworld"))!="hello")throw new Exception("Framing");
        rejected=false;try{ReadLine(new StringReader(new string('x',32768)));}catch(IOException){rejected=true;}if(!rejected)throw new Exception("Frame bound");
    }
}

internal static class WindowsInput {
    [StructLayout(LayoutKind.Sequential)] internal struct Mouse {public int x,y;public uint data,flags,time;public UIntPtr extra;}
    [StructLayout(LayoutKind.Sequential)] internal struct Keyboard {public ushort vk,scan;public uint flags,time;public UIntPtr extra;}
    [StructLayout(LayoutKind.Explicit)] internal struct Union {[FieldOffset(0)]public Mouse mouse;[FieldOffset(0)]public Keyboard keyboard;}
    [StructLayout(LayoutKind.Sequential)] internal struct Input {public uint type;public Union data;}
    [DllImport("user32.dll",SetLastError=true)] private static extern uint SendInput(uint count,Input[] inputs,int size);
    [DllImport("user32.dll")] private static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr window,out uint process);
    [DllImport("user32.dll")] private static extern short GetAsyncKeyState(int key);
    internal static void Send(string text) {
        uint process;IntPtr target=GetForegroundWindow();GetWindowThreadProcessId(target,out process);
        if(target==IntPtr.Zero||process==(uint)Process.GetCurrentProcess().Id)throw new InvalidOperationException("Zuerst das gewünschte Textfeld im Windows-Zielprogramm anklicken.");
        foreach(int key in new int[]{16,17,18,91,92})if((GetAsyncKeyState(key)&0x8000)!=0)throw new InvalidOperationException("Bitte Umschalt-, Strg-, Alt- und Windows-Tasten loslassen.");
        List<UnicodePlan.Key> plan=UnicodePlan.Build(text);Input[] inputs=new Input[plan.Count];
        for(int i=0;i<inputs.Length;i++){UnicodePlan.Key key=plan[i];inputs[i].type=1;inputs[i].data.keyboard.vk=key.Unicode?(ushort)0:key.Code;inputs[i].data.keyboard.scan=key.Unicode?key.Code:(ushort)0;inputs[i].data.keyboard.flags=(key.Unicode?4u:0u)|(key.Up?2u:0u);}
        uint inserted=SendInput((uint)inputs.Length,inputs,Marshal.SizeOf(typeof(Input)));
        if(inserted!=inputs.Length){
            if(inserted>0&&inserted%2==1){Input up=inputs[inserted-1];up.data.keyboard.flags|=2;SendInput(1,new Input[]{up},Marshal.SizeOf(typeof(Input)));}
            throw new InvalidOperationException("Windows hat die Eingabe nicht vollständig angenommen. Zielprogramm und bereits eingegebenen Text prüfen; bei Programmen mit Administratorrechten kann Windows die Eingabe blockieren.");
        }
    }
}

internal sealed class HelperWindow : Form {
    private static readonly Guid Service=new Guid("31670e87-2978-4b54-8b63-cf02b2bbd5a1");
    private readonly Label status=new Label();
    private readonly string code;
    private BluetoothListener listener;private BluetoothClient client;private volatile bool running=true;
    internal HelperWindow(){
        Text="AirPit Unicode · 0.0.2";ClientSize=new Size(445,330);MinimumSize=new Size(420,350);MaximizeBox=false;BackColor=Color.FromArgb(16,24,34);ForeColor=Color.FromArgb(237,244,248);Font=new Font("Segoe UI",10);AutoScaleMode=AutoScaleMode.Dpi;
        byte[] random=new byte[4];using(RandomNumberGenerator rng=RandomNumberGenerator.Create())rng.GetBytes(random);code=(BitConverter.ToUInt32(random,0)%1000000).ToString("D6");
        AddLabel("AirPit · Emojis und Unicode",new Rectangle(20,17,405,31),15,true,Color.FromArgb(101,219,189));
        AddLabel("Diesen Code in AirPit unter Verbindung eingeben:",new Rectangle(20,57,405,25),10,false,ForeColor);
        AddLabel(code,new Rectangle(20,84,405,57),31,true,Color.FromArgb(101,219,189));
        AddLabel("Danach am PC das gewünschte Textfeld anklicken.\nBluetooth bleibt lokal; keine Cloud und kein WLAN.\nKeine Tastatur- oder Texteingaben werden protokolliert.",new Rectangle(20,145,405,64),10,false,ForeColor);
        status.SetBounds(20,216,405,45);status.Text="Bluetooth wird gestartet …";status.ForeColor=Color.FromArgb(166,184,200);Controls.Add(status);
        Button end=new Button();end.Text="Beenden";end.SetBounds(305,274,120,34);end.FlatStyle=FlatStyle.Flat;end.BackColor=Color.FromArgb(40,59,78);end.ForeColor=ForeColor;end.Click+=delegate{Close();};Controls.Add(end);
        AddLabel("© Tim Weißbach · AirPit",new Rectangle(20,282,275,24),9,false,Color.FromArgb(166,184,200));
        Shown+=delegate{Thread t=new Thread(Listen);t.IsBackground=true;t.Start();};
        FormClosing+=delegate{running=false;try{if(client!=null)client.Close();if(listener!=null)listener.Stop();}catch{}};
        Icon=MakeIcon();
    }
    private void AddLabel(string value,Rectangle bounds,float size,bool bold,Color color){Label l=new Label();l.Text=value;l.Bounds=bounds;l.Font=new Font("Segoe UI",size,bold?FontStyle.Bold:FontStyle.Regular);l.ForeColor=color;Controls.Add(l);}
    private static Icon MakeIcon(){Bitmap b=new Bitmap(64,64);using(Graphics g=Graphics.FromImage(b)){g.SmoothingMode=SmoothingMode.AntiAlias;g.Clear(Color.FromArgb(16,24,34));using(Pen p=new Pen(Color.FromArgb(101,219,189),3))g.DrawEllipse(p,9,7,46,46);Point[] points={new Point(24,18),new Point(25,44),new Point(32,37),new Point(37,48),new Point(43,45),new Point(38,34),new Point(49,33)};g.FillPolygon(new SolidBrush(Color.FromArgb(101,219,189)),points);}return Icon.FromHandle(b.GetHicon());}
    private void State(string message){if(IsDisposed||!IsHandleCreated)return;try{BeginInvoke((Action)delegate{status.Text=message;});}catch(InvalidOperationException){}}
    private void Listen(){
        try{
            listener=new BluetoothListener(Service);listener.ServiceName="AirPit Unicode";listener.Start();State("Bereit. Mit dem S10 verbinden.");
            while(running){
                try{
                    using(BluetoothClient connected=listener.AcceptBluetoothClient()){
                        client=connected;connected.Authenticate=true;connected.Encrypt=true;
                        using(Stream stream=connected.GetStream())using(StreamReader read=new StreamReader(stream,new UTF8Encoding(false,true)))using(StreamWriter write=new StreamWriter(stream,new UTF8Encoding(false))){
                            if(stream.CanTimeout)stream.ReadTimeout=15000;
                            write.AutoFlush=true;JavaScriptSerializer json=new JavaScriptSerializer();json.MaxJsonLength=32768;json.RecursionLimit=8;
                            Dictionary<string,object> hello=json.Deserialize<Dictionary<string,object>>(UnicodePlan.ReadLine(read));
                            object token,type;bool valid=hello.TryGetValue("type",out type)&&Convert.ToString(type)=="hello"&&hello.TryGetValue("code",out token)&&UnicodePlan.CodeMatches(code,Convert.ToString(token));
                            if(!valid){write.WriteLine("{\"type\":\"error\",\"error\":\"Code nicht bestätigt\"}");continue;}
                            write.WriteLine("{\"type\":\"ready\",\"version\":1}");if(stream.CanTimeout)stream.ReadTimeout=Timeout.Infinite;State("S10 verbunden. Zieltextfeld am PC anklicken.");
                            long lastId=-1;
                            while(running){
                                Dictionary<string,object> message=json.Deserialize<Dictionary<string,object>>(UnicodePlan.ReadLine(read));long id=Convert.ToInt64(message["id"]);
                                if(id<=lastId)throw new IOException("Doppelte Sendung verworfen.");lastId=id;
                                Dictionary<string,object> reply=new Dictionary<string,object>();reply["id"]=id;
                                try{
                                    if(Convert.ToString(message["type"])!="text")throw new ArgumentException("Nachricht nicht unterstützt.");
                                    string value=Convert.ToString(message["text"]);WindowsInput.Send(value);reply["type"]="ok";State("Text übertragen. Bereit für die nächste Sendung.");
                                }catch(Exception error){reply["type"]="error";reply["error"]=error.Message;State(error.Message);}
                                write.WriteLine(json.Serialize(reply));
                            }
                        }
                    }
                }catch(Exception){if(running)State("Verbindung getrennt. Aktuellen Code erneut im S10 eingeben.");}
                finally{client=null;}
            }
        }catch(Exception){State("Bluetooth nicht verfügbar. Windows-Bluetooth einschalten und Helfer neu starten.");}
    }
}

internal static class Program {
    [STAThread] private static int Main(string[] args){
        if(args.Length>0&&args[0]=="--self-test"){
            try{UnicodePlan.SelfTest();int expected=IntPtr.Size==8?40:28;if(Marshal.SizeOf(typeof(WindowsInput.Input))!=expected)throw new Exception("Native INPUT size");File.WriteAllText("AirPit-Unicode-self-test.txt","PASS: Unicode, surrogate pairs, skin tones, ZWJ families, release pairs, bounds, protocol and native Windows INPUT layout");return 0;}catch(Exception e){File.WriteAllText("AirPit-Unicode-self-test.txt","FAIL: "+e.ToString());return 1;}
        }
        Application.EnableVisualStyles();Application.SetCompatibleTextRenderingDefault(false);Application.Run(new HelperWindow());return 0;
    }
}
