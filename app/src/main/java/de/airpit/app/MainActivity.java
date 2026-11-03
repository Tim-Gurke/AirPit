package de.airpit.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int PERMISSION=41,ENABLE=42,DISCOVER=43;
    private final Handler main=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private HidController hid;
    private AirMotion air;
    private boolean dark,foreground,airOn,dragging,holding,touching,pendingDiscover,receiverRegistered;
    private int tab=0,bg,card,text,muted,accent,border,secondary;
    private LinearLayout root,body,deviceList;
    private TextView status,statusDetail,airTitle,airDescription,sensitivity;
    private Button airToggle,calibrate,grip,dragButton,sendButton,cancelText,retryButton,pairButton,disconnectButton;
    private EditText input;
    private TouchPad pad;
    private String draft="",deviceSignature="";
    private final List<View> connectionRequired=new ArrayList<>(),keyboardRequired=new ArrayList<>();
    private final List<Button> tabs=new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences("airpit",MODE_PRIVATE);
        dark=prefs.getBoolean("dark",true);
        if(state!=null){draft=state.getString("draft","");tab=state.getInt("tab",0);pendingDiscover=state.getBoolean("pendingDiscover");}
        if(Build.VERSION.SDK_INT>=28)hid=new HidController(this,this::update);
        air=new AirMotion(this,new AirMotion.Sink(){
            @Override public void move(int x,int y){if(airOn&&hid!=null)hid.move(x,y,0);}
            @Override public void calibrated(){toast("Kalibriert. Du kannst das Handy wieder bewegen.");update();}
        });
        configureMotion();build();
        IntentFilter filter=new IntentFilter();filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(bluetoothChanges,filter,Context.RECEIVER_EXPORTED);else registerReceiver(bluetoothChanges,filter);
        receiverRegistered=true;
    }
    @Override protected void onStart(){super.onStart();foreground=true;if(hid!=null&&hasPermissions())hid.start();update();}
    @Override protected void onResume(){super.onResume();foreground=true;if(hid!=null&&hasPermissions())hid.start();update();}
    @Override protected void onPause(){pauseAir();dragging=false;if(hid!=null)hid.releaseAll();super.onPause();}
    @Override protected void onStop(){foreground=false;saveDraft();if(hid!=null)hid.stop();main.removeCallbacksAndMessages(null);super.onStop();}
    @Override protected void onDestroy(){if(receiverRegistered)unregisterReceiver(bluetoothChanges);air.stop();if(hid!=null)hid.stop();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){saveDraft();state.putString("draft",draft);state.putInt("tab",tab);state.putBoolean("pendingDiscover",pendingDiscover);super.onSaveInstanceState(state);}
    private void saveDraft(){if(input!=null)draft=input.getText().toString();}

    private final BroadcastReceiver bluetoothChanges=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(hid==null)return;
            if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(i.getAction())) {
                int s=i.getIntExtra(BluetoothAdapter.EXTRA_STATE,-1);
                if(s==BluetoothAdapter.STATE_OFF){pauseAir();hid.stop();update();}
                else if(s==BluetoothAdapter.STATE_ON&&foreground&&hasPermissions())hid.start();
            } else {deviceSignature="";update();}
        }
    };

    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);return c;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private GradientDrawable shape(int fill,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));d.setStroke(dp(1),stroke);return d;}
    private TextView label(LinearLayout parent,String value,int size,int color,boolean bold){
        TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(3),0,dp(3));
        if(bold)v.setTypeface(null,Typeface.BOLD);parent.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;
    }
    private TextView hint(LinearLayout parent,String value){return label(parent,value,13,muted,false);}
    private LinearLayout card(LinearLayout parent){
        LinearLayout c=column();c.setPadding(dp(15),dp(12),dp(15),dp(12));c.setBackground(shape(card,border,16));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);parent.addView(c,p);return c;
    }
    private Button button(LinearLayout parent,String title,Runnable action,boolean primary){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(14);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setMinWidth(0);b.setMinimumWidth(0);
        b.setPadding(dp(10),dp(6),dp(10),dp(6));b.setOnClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);action.run();update();});
        style(b,primary);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(49));p.topMargin=dp(7);parent.addView(b,p);return b;
    }
    private Button rowButton(LinearLayout parent,String title,Runnable action,boolean primary){
        Button b=button(parent,title,action,primary);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(49),1);p.setMargins(dp(3),dp(5),dp(3),0);b.setLayoutParams(p);return b;
    }
    private void style(Button b,boolean primary){
        int[][] states={new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_pressed},new int[]{}};
        b.setBackground(shape(primary?accent:secondary,primary?accent:border,12));
        b.setBackgroundTintList(new ColorStateList(states,new int[]{dark?0xff24313f:0xffe5eaf0,primary?(dark?0xff99ebd6:0xff005da8):(dark?0xff40536a:0xffd3e2ed),primary?accent:secondary}));
        b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{dark?0xff81909f:0xff8894a2,primary?(dark?bg:0xffffffff):text}));
    }
    private void build(){
        bg=dark?0xff101822:0xfff1f5f9;card=dark?0xff1a2634:0xffffffff;text=dark?0xffedf4f8:0xff172738;
        muted=dark?0xffa6b8c8:0xff586c80;accent=dark?0xff65dbbd:0xff0879c9;border=dark?0xff304152:0xffdbe4ed;secondary=dark?0xff283b4e:0xffe9f0f7;
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root=column();root.setBackgroundColor(bg);
        if(Build.VERSION.SDK_INT>=30)root.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });else root.setFitsSystemWindows(true);
        setContentView(root);root.requestApplyInsets();
        LinearLayout header=row();header.setPadding(dp(16),dp(8),dp(13),dp(5));root.addView(header,new LinearLayout.LayoutParams(-1,dp(73)));
        ImageView icon=new ImageView(this);icon.setImageResource(getApplicationInfo().icon);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(icon,new LinearLayout.LayoutParams(dp(52),dp(52)));
        LinearLayout brand=column();LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-2,1);bp.setMarginStart(dp(8));header.addView(brand,bp);
        label(brand,"AirPit",26,text,true).setPadding(0,0,0,0);
        label(brand,"Version "+BuildConfig.VERSION_NAME,11,muted,false).setPadding(0,0,0,0);
        Button theme=rowButton(header,dark?"Hell":"Dunkel",()->{saveDraft();pauseAir();dark=!dark;prefs.edit().putBoolean("dark",dark).apply();build();},false);
        theme.setLayoutParams(new LinearLayout.LayoutParams(dp(68),dp(43)));theme.setContentDescription(dark?"Helles Design einschalten":"Dunkles Design einschalten");
        Button settings=rowButton(header,"⚙",this::settings,false);settings.setTextSize(23);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(46),dp(43));sp.setMarginStart(dp(6));settings.setLayoutParams(sp);settings.setContentDescription("Einstellungen");
        LinearLayout strip=column();strip.setPadding(dp(18),dp(4),dp(18),dp(3));root.addView(strip);
        status=label(strip,"● Nicht verbunden",15,muted,true);
        statusDetail=hint(strip,"Öffne Verbindung und kopple deinen Windows-PC.");statusDetail.setMinLines(2);statusDetail.setMaxLines(2);statusDetail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout nav=row();nav.setPadding(dp(13),0,dp(13),dp(7));root.addView(nav);tabs.clear();
        String[] titles={"Maus","Tastatur","Verbindung"};
        for(int i=0;i<titles.length;i++){final int target=i;Button b=rowButton(nav,titles[i],()->selectTab(target),i==tab);b.setTextSize(13);tabs.add(b);}
        body=column();root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        TextView footer=new TextView(this);footer.setText("© Tim Weißbach · AirPit");footer.setGravity(Gravity.CENTER);footer.setTextColor(muted);footer.setTextSize(11);footer.setPadding(0,dp(5),0,dp(7));root.addView(footer);
        showPage();
    }
    private void selectTab(int target){
        if(tab==target)return;saveDraft();pauseAir();dragging=false;if(hid!=null)hid.releaseAll();
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0);
        tab=target;for(int i=0;i<tabs.size();i++)style(tabs.get(i),i==tab);showPage();
    }
    private void showPage(){
        body.removeAllViews();connectionRequired.clear();keyboardRequired.clear();input=null;sendButton=cancelText=airToggle=calibrate=grip=dragButton=retryButton=pairButton=disconnectButton=null;pad=null;deviceList=null;
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);body.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout page=column();page.setPadding(dp(16),dp(7),dp(16),dp(8));scroll.addView(page);
        if(tab==0)mousePage(page);else if(tab==1)keyboardPage(page);else connectionPage(page);
        update();
    }
    private void mousePage(LinearLayout page){
        LinearLayout airCard=card(page);
        airTitle=label(airCard,"Air-Maus · pausiert",20,text,true);
        airDescription=hint(airCard,"Handy wie eine Fernbedienung halten und zum Bewegen leicht drehen.");airDescription.setMinLines(2);airDescription.setMaxLines(2);airDescription.setEllipsize(android.text.TextUtils.TruncateAt.END);
        airToggle=button(airCard,"Air-Maus einschalten",this::toggleAir,true);
        LinearLayout controls=row();airCard.addView(controls);
        calibrate=rowButton(controls,"Kalibrieren",()->{
            if(airOn){air.calibrate();toast("Handy etwa eine Sekunde stillhalten.");main.postDelayed(()->{if(airOn&&air.calibrating()){toast("Noch nicht ruhig genug. Handy still auflegen oder Air-Maus pausieren.");update();}},5000);}
        },false);
        grip=rowButton(controls,"Umgreifen",()->{},false);
        grip.setOnTouchListener((v,e)->{
            if(!v.isEnabled())return false;
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){holding=true;updateAirHold();v.setPressed(true);update();return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){holding=false;updateAirHold();v.setPressed(false);v.performClick();update();return true;}
            return true;
        });grip.setContentDescription("Zum Umgreifen gedrückt halten. Der Mauszeiger bleibt dabei stehen.");
        pad=new TouchPad(this,new TouchPad.Sink(){
            public void move(int x,int y,int wheel){if(hid!=null)hid.move(x,y,wheel);}
            public void click(int button){if(hid!=null&&!dragging)hid.click(button);}
            public void touching(boolean value){touching=value;updateAirHold();}
        },accent,muted);pad.setBackground(shape(card,border,16));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(180));pp.bottomMargin=dp(5);page.addView(pad,pp);connectionRequired.add(pad);
        LinearLayout clicks=row();page.addView(clicks);
        pressButton(clicks,"Linksklick",1);pressButton(clicks,"Rechtsklick",2);
        LinearLayout extra=row();page.addView(extra);
        dragButton=rowButton(extra,"Ziehen: aus",()->{dragging=!dragging;if(hid!=null)hid.button(1,dragging);},false);connectionRequired.add(dragButton);
        Button up=rowButton(extra,"↑ Scroll",()->{},false),down=rowButton(extra,"↓ Scroll",()->{},false);repeatScroll(up,1);repeatScroll(down,-1);connectionRequired.add(up);connectionRequired.add(down);
        hint(page,"Tippen: Linksklick · zwei Finger: Scrollen / Rechtsklick");
    }
    private void pressButton(LinearLayout parent,String title,int mask){
        Button b=rowButton(parent,title,()->{},false);connectionRequired.add(b);
        b.setOnTouchListener((v,e)->{
            if(!v.isEnabled())return false;
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){if(dragging&&mask==1){dragging=false;update();}hid.button(mask,true);v.setPressed(true);return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){hid.button(mask,false);v.setPressed(false);if(e.getActionMasked()==MotionEvent.ACTION_UP)v.performClick();return true;}return true;
        });
    }
    private void repeatScroll(Button b,int direction){
        final Runnable[] repeat=new Runnable[1];
        repeat[0]=()->{if(b.isPressed()&&b.isEnabled()&&foreground){if(hid!=null)hid.move(0,0,direction);main.postDelayed(repeat[0],85);}};
        b.setOnTouchListener((v,e)->{
            if(!v.isEnabled())return false;
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){v.setPressed(true);repeat[0].run();return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){v.setPressed(false);main.removeCallbacks(repeat[0]);if(e.getActionMasked()==MotionEvent.ACTION_UP)v.performClick();return true;}return true;
        });
    }
    private void toggleAir(){
        if(airOn){pauseAir();return;}
        if(hid==null||!hid.connected())return;
        airOn=air.start();if(!airOn){toast("Der Bewegungssensor konnte nicht gestartet werden.");return;}
        holding=touching=false;updateAirHold();getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);update();
    }
    private void pauseAir(){airOn=false;holding=touching=false;if(air!=null)air.stop();if(root!=null)getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);update();}
    private void updateAirHold(){if(air!=null)air.hold(holding||touching);}
    private void configureMotion(){
        air.gain=500+prefs.getInt("speed",50)*24;air.smoothing=0.012+prefs.getInt("smooth",35)*0.0015;
        air.deadzone=0.004+prefs.getInt("steady",30)*0.0005;air.invertX=prefs.getBoolean("invertX",false);air.invertY=prefs.getBoolean("invertY",false);
    }
    private void keyboardPage(LinearLayout page){
        LinearLayout typing=card(page);label(typing,"Mit Gboard schreiben",20,text,true);
        hint(typing,"Tippen oder diktieren, danach den fertigen Text senden.");
        input=new EditText(this);input.setTextColor(text);input.setHintTextColor(muted);input.setTextSize(16);input.setGravity(Gravity.TOP|Gravity.START);
        input.setHint("Dein Text für Windows …");input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});input.setMinLines(3);input.setMaxLines(6);input.setPadding(dp(12),dp(10),dp(12),dp(10));input.setBackground(shape(bg,border,10));input.setText(draft);
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);ep.topMargin=dp(9);typing.addView(input,ep);
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){draft=s.toString();update();}public void afterTextChanged(Editable e){}});
        sendButton=button(typing,"Text an PC senden",()->{
            if(input==null||hid==null)return;pauseAir();
            try{hid.text(input.getText().toString());((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);input.clearFocus();}
            catch(IllegalArgumentException e){new AlertDialog.Builder(this).setTitle("Text noch nicht gesendet").setMessage(e.getMessage()+"\n\nEmojis und beliebige Unicode-Zeichen werden in dieser Version nicht übertragen.").setPositiveButton("Verstanden",null).show();}
        },true);
        cancelText=button(typing,"Übertragung abbrechen",()->{if(hid!=null)hid.cancelText();},false);
        hint(typing,"Windows-Tastaturlayout: Deutsch (Deutschland). Feststelltaste ausschalten. Der Text bleibt hier zum Bearbeiten stehen.");
        LinearLayout keys=card(page);label(keys,"Direkttasten",18,text,true);
        LinearLayout first=row();keys.addView(first);keyButton(first,"Esc",0,41);keyButton(first,"Tab",0,43);keyButton(first,"⌫",0,42);keyButton(first,"Enter",0,40);
        LinearLayout arrows=row();keys.addView(arrows);keyButton(arrows,"←",0,80);keyButton(arrows,"↑",0,82);keyButton(arrows,"↓",0,81);keyButton(arrows,"→",0,79);
        LinearLayout editing=row();keys.addView(editing);keyButton(editing,"Alle",1,4);keyButton(editing,"Kopieren",1,6);keyButton(editing,"Einfügen",1,25);
        LinearLayout windows=row();keys.addView(windows);keyButton(windows,"Rückgängig",1,28);keyButton(windows,"Alt + Tab",4,43);keyButton(windows,"Desktop",8,7);
        hint(keys,"Kopieren und Einfügen verwenden die Zwischenablage des PCs.");
    }
    private void keyButton(LinearLayout parent,String label,int mod,int key){Button b=rowButton(parent,label,()->{if(hid!=null)hid.key(mod,key);},false);b.setTextSize(13);keyboardRequired.add(b);}

    private void connectionPage(LinearLayout page){
        LinearLayout c=card(page);label(c,"Mit Windows verbinden",20,text,true);
        hint(c,"1  Bluetooth am PC einschalten.\n2  Unten „Für PC sichtbar“ antippen.\n3  In Windows: Bluetooth-Gerät hinzufügen → dein S10 auswählen und Kopplung bestätigen.\n4  AirPit wieder öffnen und geöffnet lassen.");
        hint(c,"Windows kann den Bluetooth-Namen deines Handys anzeigen, auch wenn die Maus AirPit heißt.");
        retryButton=button(c,"Bluetooth starten",()->ensureBluetooth(false),true);
        pairButton=button(c,"Für PC sichtbar · 2 Minuten",()->ensureBluetooth(true),false);
        button(c,"Bluetooth-Einstellungen",()->{try{startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));}catch(Exception e){toast("Bluetooth-Einstellungen konnten nicht geöffnet werden.");}},false);
        disconnectButton=button(c,"PC-Verbindung trennen",()->{pauseAir();dragging=false;if(hid!=null)hid.disconnect();},false);
        LinearLayout paired=card(page);label(paired,"Gekoppelte Geräte",18,text,true);hint(paired,"Deinen Windows-PC zum Verbinden auswählen.");deviceList=column();paired.addView(deviceList);deviceSignature="";
        LinearLayout help=card(page);label(help,"Lokal und direkt",18,text,true);
        hint(help,"Kein PC-Zusatzprogramm, kein WLAN und kein Konto. AirPit nutzt Bluetooth-Maus und -Tastatur (HID). Die Android-ROM muss diesen Gerätemodus unterstützen.");
        hint(help,"AirPit bleibt während der Bedienung sichtbar. Beim Verlassen werden die Air-Maus pausiert und alle Tasten losgelassen. Nach dem Zurückkehren bei Bedarf den PC erneut verbinden.");
    }
    private boolean hasPermissions(){return Build.VERSION.SDK_INT<31||(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)==PackageManager.PERMISSION_GRANTED);}
    private void ensureBluetooth(boolean discover){
        pendingDiscover|=discover;
        if(hid==null){toast("Für AirPit ist Android 9 oder neuer nötig.");return;}
        if(hid.adapter==null){toast("Dieses Gerät hat kein Bluetooth.");return;}
        if(!hasPermissions()){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE},PERMISSION);return;}
        try {
            if(!hid.adapter.isEnabled()){startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),ENABLE);return;}
            hid.start();
            if(pendingDiscover){pendingDiscover=false;Intent show=new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);show.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,120);startActivityForResult(show,DISCOVER);}
        }catch(SecurityException|android.content.ActivityNotFoundException e){pendingDiscover=false;toast("Bluetooth-Freigabe oder Systemeinstellungen prüfen.");}
        update();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==PERMISSION){if(hasPermissions())ensureBluetooth(pendingDiscover);else {pendingDiscover=false;toast("Bitte „Geräte in der Nähe“ erlauben, damit Bluetooth funktioniert.");}update();}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==ENABLE){if(result==RESULT_OK)ensureBluetooth(pendingDiscover);else{pendingDiscover=false;toast("Bluetooth bleibt ausgeschaltet.");}}if(request==DISCOVER){if(result>0)toast("Jetzt am Windows-PC dein Handy koppeln.");else toast("Sichtbarkeit wurde nicht freigegeben.");}update();}

    private void update(){
        if(status==null)return;
        boolean connected=hid!=null&&hid.connected();
        if(!connected&&airOn){airOn=false;air.stop();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);dragging=false;}
        boolean typing=hid!=null&&hid.typing;
        status.setText(connected?"● "+HidController.name(hid.host):(hid!=null&&hid.connecting?"● Verbinde …":"● Nicht verbunden"));
        status.setTextColor(connected?accent:muted);
        String message=hid==null?"Android 9 oder neuer und Bluetooth-HID sind erforderlich.":!hasPermissions()?"Unter Verbindung Bluetooth erlauben und den Windows-PC koppeln.":typing?"Tastatureingabe wird übertragen …":hid.detail();
        statusDetail.setText(message);statusDetail.setContentDescription(message);
        for(View v:connectionRequired)v.setEnabled(connected);
        for(View v:keyboardRequired)v.setEnabled(connected&&!typing);
        if(pad!=null)pad.invalidate();
        if(airToggle!=null){
            airToggle.setEnabled(connected&&air.available());airToggle.setText(airOn?"Air-Maus pausieren":"Air-Maus einschalten");
            airTitle.setText(!air.available()?"Air-Maus · Sensor fehlt":airOn?(air.calibrating()?"Air-Maus · kalibriert …":holding||touching?"Air-Maus · Zeiger hält":"Air-Maus · aktiv"):"Air-Maus · pausiert");
            airTitle.setTextColor(airOn?accent:text);
            airDescription.setText(!air.available()?"Kein Gyroskop erkannt. Touchpad und Tastatur bleiben nutzbar.":air.calibrating()?"Handy ruhig halten. Die Bewegung startet danach wieder.":"Handy wie eine Fernbedienung halten und zum Bewegen leicht drehen.");
            calibrate.setEnabled(airOn&&!air.calibrating());grip.setEnabled(airOn&&!air.calibrating());
            grip.setText(holding?"Zeiger hält":"Umgreifen");
        }
        if(dragButton!=null){dragButton.setText(dragging?"Ziehen: AN":"Ziehen: aus");style(dragButton,dragging);}
        if(sendButton!=null){sendButton.setEnabled(connected&&!typing&&input!=null&&input.length()>0);cancelText.setEnabled(typing);}
        if(retryButton!=null){
            retryButton.setText(!hasPermissions()?"Bluetooth erlauben":hid!=null&&hid.registered?"Bluetooth ist bereit":"Bluetooth starten");
            retryButton.setEnabled(hid!=null&&(!hasPermissions()||(!hid.registered&&!hid.registering)));
            pairButton.setEnabled(hid!=null&&hasPermissions()&&hid.registered&&!connected);
            disconnectButton.setEnabled(connected);
        }
        if(deviceList!=null){
            List<BluetoothDevice> devices=hid!=null&&hasPermissions()?hid.paired():new ArrayList<>();
            StringBuilder sig=new StringBuilder();for(BluetoothDevice d:devices)sig.append(d.hashCode()).append(HidController.name(d));sig.append(connected).append(hid!=null&&hid.registered).append(hid!=null&&hid.connecting);
            if(!sig.toString().equals(deviceSignature)){deviceSignature=sig.toString();deviceList.removeAllViews();
                if(devices.isEmpty())hint(deviceList,"Noch keine Geräte verfügbar. Zuerst vom PC aus koppeln.");
                else for(BluetoothDevice d:devices){boolean current=connected&&d.equals(hid.host);Button b=button(deviceList,HidController.name(d)+(current?" · verbunden":""),()->{hid.connect(d);prefs.edit().putString("lastHost",d.getAddress()).apply();},current);b.setEnabled(hid.registered&&!hid.connecting&&!connected);}
            }
        }
        if(connected&&foreground)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else if(!airOn)getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void settings(){
        saveDraft();pauseAir();
        LinearLayout content=column();content.setPadding(dp(23),dp(8),dp(23),dp(16));content.setBackgroundColor(card);
        hint(content,"Deutsches Windows-Tastaturlayout · Feststelltaste aus");
        slider(content,"Empfindlichkeit","speed",50);
        slider(content,"Glättung","smooth",35);
        slider(content,"Zitterfilter","steady",30);
        toggle(content,"Links / rechts umkehren","invertX");toggle(content,"Hoch / runter umkehren","invertY");
        hint(content,"Kalibrieren gleicht Sensordrift aus. „Umgreifen“ gedrückt halten, um das Handy neu auszurichten.");
        hint(content,"© Tim Weißbach · AirPit "+BuildConfig.VERSION_NAME);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Einstellungen").setView(content).setPositiveButton("Fertig",null).create();dialog.show();
    }
    private void slider(LinearLayout parent,String title,String key,int fallback){
        int value=prefs.getInt(key,fallback);TextView caption=label(parent,title+" · "+value+" %",15,text,true);
        SeekBar bar=new SeekBar(this);bar.setMax(100);bar.setProgress(value);bar.setProgressTintList(ColorStateList.valueOf(accent));bar.setThumbTintList(ColorStateList.valueOf(accent));
        parent.addView(bar,new LinearLayout.LayoutParams(-1,dp(44)));bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int v,boolean user){caption.setText(title+" · "+v+" %");if(user){prefs.edit().putInt(key,v).apply();configureMotion();}}
            public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}
        });
    }
    private void toggle(LinearLayout parent,String title,String key){Switch s=new Switch(this);s.setText(title);s.setTextColor(text);s.setTextSize(14);s.setChecked(prefs.getBoolean(key,false));s.setPadding(0,dp(10),0,dp(10));parent.addView(s);s.setOnCheckedChangeListener((b,value)->{prefs.edit().putBoolean(key,value).apply();configureMotion();});}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
}
