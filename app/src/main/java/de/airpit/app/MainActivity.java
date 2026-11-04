package de.airpit.app;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.ClipboardManager;
import android.content.res.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import de.airpit.app.core.TileLayout;
import de.airpit.app.core.MotionAxes;
import de.airpit.app.core.FreeLayout;
import de.airpit.app.core.LayoutDrafts;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int BT_PERMISSION=41,ENABLE=42,DISCOVER=43,NOTIFICATIONS=44;
    private static final String TOUCHPAD_SPEED="touchpadSpeed";
    private final Handler main=new Handler(Looper.getMainLooper());
    private AirSession session;private HidController hid;private AirMotion air;
    private android.content.SharedPreferences prefs;
    private boolean dark,resumed,editing,fullscreen,pendingDiscover,pendingConnect,pendingScreenOff,receiverRegistered;
    private int tab,bg,card,text,muted,accent,border,secondary;private float scale;
    private LinearLayout root,body,deviceList;private ScrollView pageScroll;
    private TextView status,statusDetail,airTitle,airDescription,unicodeInfo;
    private Button quick,airToggle,calibrate,grip,dragButton,sendButton,cancelText,restoreButton,retryButton,pairButton,disconnectButton;
    private EditText input;private TouchPad pad;private TileLayout layout;private String loadedLayoutKey="";
    private SeekBar uiScaleBar,touchpadSpeedBar;private AlertDialog layoutDialog;
    private final List<View> connectedViews=new ArrayList<>(),keyboardViews=new ArrayList<>();
    private final List<Button> tabs=new ArrayList<>();
    private String deviceSignature="";
    private FreeBoard freeBoard;
    private LayoutDrafts layoutDrafts;
    private final Map<String,View> individualViews=new LinkedHashMap<>();
    private final Set<View> initiallyHidden=Collections.newSetFromMap(new IdentityHashMap<View,Boolean>());
    private TextView editMessage;
    private int pendingPreset=-1;
    private boolean constructing,rebuildPosted;
    private final Runnable refreshSurface=()->{rebuildPosted=false;build();};

    private final AirSession.Observer observer=this::update;
    private final Runnable hidden=()->{if(!resumed)session.hidden();};
    private final Runnable heartbeat=new Runnable(){public void run(){if(resumed){hid.heartbeat();main.postDelayed(this,100);}}};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);session=AirSession.get(this);hid=session.hid;air=session.motion;prefs=session.prefs;
        dark=prefs.getBoolean("dark",true);scale=prefs.getInt("uiScale",100)/100f;
        if(state!=null){tab=state.getInt("tab",0);fullscreen=state.getBoolean("fullscreen");if(session.textDraft.value.isEmpty()&&session.textDraft.lastSent.isEmpty())session.textDraft.value=state.getString("draft","");}
        if(state!=null&&state.getBoolean("arranging",false)){editing=true;layoutDrafts=LayoutDrafts.decode(state.getString("arrangementDrafts"));pendingPreset=state.getInt("pendingPreset",-1);}
        session.observer=observer;build();
        IntentFilter f=new IntentFilter();f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(bluetoothChanges,f,Context.RECEIVER_EXPORTED);else registerReceiver(bluetoothChanges,f);receiverRegistered=true;
    }
    @Override protected void onStart(){super.onStart();session.observer=observer;session.visible=true;if(hasPermissions())startSession();update();}
    @Override protected void onResume(){super.onResume();resumed=true;session.visible=true;main.removeCallbacks(hidden);main.removeCallbacks(heartbeat);main.post(heartbeat);session.observer=observer;update();}
    @Override protected void onPause(){resumed=false;main.removeCallbacks(heartbeat);saveDraft();main.postDelayed(hidden,180);super.onPause();}
    @Override protected void onStop(){saveDraft();if(!resumed)session.hidden();if(session.observer==observer)session.observer=null;super.onStop();}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);if(receiverRegistered)unregisterReceiver(bluetoothChanges);if(session.observer==observer)session.observer=null;super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle b){saveDraft();b.putInt("tab",tab);b.putBoolean("fullscreen",fullscreen);b.putString("draft",session.textDraft.value);b.putBoolean("arranging",editing);if(editing&&layoutDrafts!=null)b.putString("arrangementDrafts",layoutDrafts.encode());b.putInt("pendingPreset",pendingPreset);super.onSaveInstanceState(b);}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);saveDraft();if(layoutDialog!=null)layoutDialog.dismiss();layout=null;air.rotation=getWindowManager().getDefaultDisplay().getRotation();build();}
    @Override public void onBackPressed(){if(editing){leaveArrangement();}else if(fullscreen){fullscreen=false;build();}else super.onBackPressed();}
    private final BroadcastReceiver bluetoothChanges=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(i.getAction())){
            int value=i.getIntExtra(BluetoothAdapter.EXTRA_STATE,-1);
            if(value==BluetoothAdapter.STATE_OFF){session.pauseAll();hid.stop();update();}
            else if(value==BluetoothAdapter.STATE_ON&&resumed&&hasPermissions())startSession();
        }else{deviceSignature="";update();}
    }};
    private void startSession(){try{session.start();}catch(RuntimeException e){toast("Bluetooth-Dienst konnte nicht gestartet werden. AirPit öffnen und Bluetooth erneut starten.");}}
    private boolean landscape(){return getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;}
    private String layoutKey(){return landscape()?"layout.landscape":"layout.portrait";}
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private int u(float v){return dp(v*scale);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(1);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private GradientDrawable shape(int fill,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(u(radius));d.setStroke(dp(1),stroke);return d;}
    private TextView label(LinearLayout parent,String value,int size,int color,boolean bold){
        TextView v=new TextView(this);v.setTag("label."+value);v.setText(value);v.setTextColor(color);v.setTextSize(size*scale);v.setPadding(0,u(3),0,u(3));if(bold)v.setTypeface(null,Typeface.BOLD);parent.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;
    }
    private TextView hint(LinearLayout parent,String value){return label(parent,value,12,muted,false);}
    private void help(LinearLayout parent,String value){if(prefs.getBoolean("help",false))hint(parent,value);}
    private AlertDialog.Builder dialog(){return new AlertDialog.Builder(new ContextThemeWrapper(this,dark?android.R.style.Theme_Material_Dialog_Alert:android.R.style.Theme_Material_Light_Dialog_Alert));}
    private Button button(LinearLayout parent,String value,Runnable action,boolean primary){
        Button b=new Button(this);b.setText(value);b.setContentDescription(value);b.setAllCaps(false);b.setTextSize(14*scale);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setMaxLines(2);b.setEllipsize(TextUtils.TruncateAt.END);
        b.setTag("button."+value);b.setGravity(Gravity.CENTER);b.setIncludeFontPadding(false);b.setStateListAnimator(null);b.setElevation(0);b.setTranslationZ(0);b.setOutlineProvider(ViewOutlineProvider.BACKGROUND);b.setClipToOutline(true);
        b.setPadding(u(7),u(4),u(7),u(4));style(b,primary);b.setOnClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);action.run();update();});
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,Math.max(dp(32),u(47)));p.topMargin=u(5);parent.addView(b,p);return b;
    }
    private Button rowButton(LinearLayout parent,String value,Runnable action,boolean primary){
        Button b=button(parent,value,action,primary);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Math.max(dp(32),u(47)),1);p.setMargins(u(2),u(4),u(2),0);b.setLayoutParams(p);return b;
    }
    private ImageButton themeButton(LinearLayout parent){
        ImageButton b=new ImageButton(this);b.setTag("theme");b.setStateListAnimator(null);b.setElevation(0);b.setTranslationZ(0);
        b.setImageResource(dark?R.drawable.ic_theme_light:R.drawable.ic_theme_dark);
        b.setColorFilter(text);b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setMinimumWidth(0);b.setMinimumHeight(0);b.setPadding(u(9),u(9),u(9),u(9));
        b.setBackground(shape(secondary,border,12));
        b.setContentDescription(dark?"Helles Design einschalten":"Dunkles Design einschalten");
        b.setOnClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);dark=!dark;prefs.edit().putBoolean("dark",dark).apply();build();});
        parent.addView(b,new LinearLayout.LayoutParams(Math.max(dp(32),u(42)),Math.max(dp(32),u(40))));
        return b;
    }
    private void style(Button b,boolean primary){
        b.setBackground(shape(primary?accent:secondary,primary?accent:border,12));
        b.setBackgroundTintList(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_pressed},new int[]{}},new int[]{dark?0xff24313f:0xffe5eaf0,primary?(dark?0xff91e8d1:0xff005da8):(dark?0xff40536a:0xffd3e2ed),primary?accent:secondary}));
        b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{dark?0xff81909f:0xff8894a2,primary?(dark?bg:0xffffffff):text}));
    }
    private LinearLayout card(LinearLayout parent,String title){
        LinearLayout c=column();c.setPadding(u(13),u(10),u(13),u(11));c.setBackground(shape(card,border,15));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=u(10);parent.addView(c,p);if(title!=null)label(c,title,18,text,true);return c;
    }
    private void build(){
        constructing=true;freeBoard=null;initiallyHidden.clear();
        saveDraft();scale=Math.max(.5f,Math.min(1.5f,prefs.getInt("uiScale",100)/100f));
        bg=dark?0xff101822:0xfff1f5f9;card=dark?0xff1a2634:0xffffffff;text=dark?0xffedf4f8:0xff172738;muted=dark?0xffa6b8c8:0xff586c80;accent=dark?0xff65dbbd:0xff0879c9;border=dark?0xff304152:0xffdbe4ed;secondary=dark?0xff283b4e:0xffe9f0f7;
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root=column();root.setBackgroundColor(bg);
        if(Build.VERSION.SDK_INT>=30)root.setOnApplyWindowInsetsListener((v,i)->{android.graphics.Insets b=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());v.setPadding(b.left,b.top,b.right,b.bottom);return i;});else root.setFitsSystemWindows(true);
        setContentView(root);root.requestApplyInsets();tabs.clear();resetViews();air.rotation=getWindowManager().getDefaultDisplay().getRotation();
        if(fullscreen){buildFullscreen();update();scheduleFreeBoard();constructing=false;return;}
        LinearLayout header=row();header.setPadding(u(12),u(4),u(12),u(4));root.addView(header);
        ImageView icon=new ImageView(this);icon.setTag("brand.icon");icon.setImageResource(R.drawable.ic_airpit);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);header.addView(icon,new LinearLayout.LayoutParams(u(43),u(43)));
        LinearLayout brand=column();LinearLayout.LayoutParams brandP=new LinearLayout.LayoutParams(0,-2,1);brandP.setMarginStart(u(4));header.addView(brand,brandP);
        label(brand,"AirPit",23,text,true).setPadding(0,0,0,0);TextView version=label(brand,"Version "+BuildConfig.VERSION_NAME,10,muted,false);version.setPadding(0,0,0,0);version.setTag("brand.version");
        themeButton(header);
        Button gear=rowButton(header,"⚙",this::settings,false);gear.setContentDescription("Einstellungen");gear.setTextSize(22*scale);LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(u(42),u(40));gp.setMarginStart(u(5));gear.setLayoutParams(gp);
        LinearLayout strip=row();strip.setPadding(u(14),0,u(14),0);root.addView(strip);
        status=new TextView(this);status.setTextColor(muted);status.setTextSize(13*scale);status.setTypeface(null,Typeface.BOLD);status.setMinLines(2);status.setMaxLines(2);status.setEllipsize(TextUtils.TruncateAt.END);strip.addView(status,new LinearLayout.LayoutParams(0,-2,1));
        quick=button(strip,"Verbinden",this::quickConnect,true);LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(u(112),Math.max(dp(32),u(39)));qp.setMarginStart(u(6));quick.setLayoutParams(qp);quick.setTextSize(12*scale);
        LinearLayout detail=column();detail.setPadding(u(14),0,u(14),0);root.addView(detail);
        statusDetail=hint(detail,"");statusDetail.setMinLines(2);statusDetail.setMaxLines(2);statusDetail.setEllipsize(TextUtils.TruncateAt.END);
        statusDetail.setOnClickListener(v->dialog().setTitle("Verbindungsstatus").setMessage(hid.detail()+"\n\n"+hid.unicode.status).setPositiveButton("Schließen",null).show());
        LinearLayout nav=row();nav.setPadding(u(11),0,u(11),u(5));root.addView(nav);
        String[] names={"Bedienung","Tastatur","Verbindung"};for(int i=0;i<3;i++){final int target=i;Button b=rowButton(nav,names[i],()->selectTab(target),tab==i);b.setTextSize(12*scale);tabs.add(b);}
        body=column();root.addView(body,new LinearLayout.LayoutParams(-1,0,1));footer();buildLegacyPage();scheduleFreeBoard();constructing=false;
    }
    private void footer(){TextView f=new TextView(this);f.setTag("footer");f.setText("© Tim Weißbach · AirPit");f.setTextSize(10*scale);f.setTextColor(muted);f.setGravity(Gravity.CENTER);f.setPadding(0,u(4),0,u(5));root.addView(f);}
    private void saveDraft(){if(input!=null)session.textDraft.value=input.getText().toString();}
    private void hideKeyboard(){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0);}
    private void selectTab(int value){if(tab==value)return;saveDraft();hideKeyboard();tab=value;editing=false;session.dragging=false;hid.releaseMouse();if(value!=0)session.pauseAir();for(int i=0;i<tabs.size();i++)style(tabs.get(i),i==tab);showPage();}
    private void resetViews(){connectedViews.clear();keyboardViews.clear();input=null;pad=null;statusDetail=null;status=null;quick=null;airTitle=airDescription=unicodeInfo=null;airToggle=calibrate=grip=dragButton=sendButton=cancelText=restoreButton=retryButton=pairButton=disconnectButton=null;deviceList=null;}
    private void showPage(){build();}
    private void buildLegacyPage(){
        saveDraft();connectedViews.clear();keyboardViews.clear();input=null;pad=null;airTitle=airDescription=unicodeInfo=null;airToggle=calibrate=grip=dragButton=sendButton=cancelText=restoreButton=retryButton=pairButton=disconnectButton=null;deviceList=null;body.removeAllViews();
        pageScroll=new ScrollView(this);pageScroll.setFillViewport(true);body.addView(pageScroll,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout page=column();page.setPadding(u(12),u(5),u(12),u(8));pageScroll.addView(page);
        if(tab!=0){LinearLayout tools=row();page.addView(tools);rowButton(tools,"Anordnen",this::beginArrangement,false);}
        if(tab==0)dashboard(page);else if(tab==1){keyboardTile(card(page,"Mit Gboard schreiben"));keysTile(card(page,"Direkttasten"));}else connectionPage(page);update();
    }
    private void dashboard(LinearLayout page){
        if(layout==null||!loadedLayoutKey.equals(layoutKey())){loadedLayoutKey=layoutKey();layout=TileLayout.decode(prefs.getString(loadedLayoutKey,null),landscape());}
        if(pendingPreset>=0)layout.preset(pendingPreset==4?0:pendingPreset,landscape());
        LinearLayout tools=row();page.addView(tools);rowButton(tools,"Anordnen",this::beginArrangement,false);
        rowButton(tools,"Vorlagen",this::presets,false);rowButton(tools,"Vollbild",()->{saveDraft();fullscreen=true;session.pauseAir();hideKeyboard();build();},false);
        TileBoard board=new TileBoard(this,u(8));LinearLayout.LayoutParams boardP=new LinearLayout.LayoutParams(-1,-2);boardP.topMargin=u(8);page.addView(board,boardP);
        // Build the whole catalog, including currently hidden groups, for individual selection.
        for(TileLayout.Tile spec:layout.tiles){
            LinearLayout c=column();c.setPadding(u(spec.id.equals("pad")?5:12),u(9),u(spec.id.equals("pad")?5:12),u(10));
            if(!spec.visible)initiallyHidden.add(c);
            switch(spec.id){case "pad":pad=touchpad();c.addView(pad,new LinearLayout.LayoutParams(-1,dp(spec.height)));break;case "clicks":clicksTile(c);break;case "wheel":wheelTile(c);break;case "air":airTile(c);break;case "text":label(c,"Mit Gboard schreiben",17,text,true);keyboardTile(c);break;case "keys":label(c,"Direkttasten",17,text,true);keysTile(c);break;default:break;}
            board.tile(c,landscape()?spec.span:Math.max(2,spec.span));
        }
    }
    private void presets(){
        String[] choices=tab==0&&!fullscreen?new String[]{"Großes Touchpad","Air-Maus","Kompakt","Maus und Text","Standard wiederherstellen"}:new String[]{"Standardanordnung wiederherstellen"};
        layoutDialog=dialog().setTitle("Vorlage für "+(landscape()?"Querformat":"Hochformat")).setItems(choices,(d,which)->{
            if(!editing)beginArrangement();pendingPreset=choices.length==1?4:which;layout=null;build();
        }).setNegativeButton("Zurück",null).show();
    }
    private String freeKey(){return "freeLayout.v1."+(landscape()?"landscape.":"portrait.")+(fullscreen?"full":String.valueOf(tab));}
    private void scheduleFreeBoard(){
        final LinearLayout staging=root;
        staging.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){
            if(staging!=root){if(staging.getViewTreeObserver().isAlive())staging.getViewTreeObserver().removeOnPreDrawListener(this);return true;}
            if(staging.getWidth()==0||staging.getHeight()==0)return true;
            staging.getViewTreeObserver().removeOnPreDrawListener(this);mountFreeBoard();return false;
        }});
    }
    private void collectControls(View v,int left,int top,boolean hidden,FreeLayout defaults,Map<String,Integer> duplicates){
        int x=left+v.getLeft(),y=top+v.getTop();hidden|=initiallyHidden.contains(v);
        if("footer".equals(v.getTag()))return;
        if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)collectControls(group.getChildAt(i),x-v.getScrollX(),y-v.getScrollY(),hidden,defaults,duplicates);return;}
        String id=v==status?"status":v==statusDetail?"status.detail":v==quick?"quick":v==airTitle?"air.title":v==airDescription?"air.description":v==unicodeInfo?"unicode.info":v.getTag() instanceof String?(String)v.getTag():"view."+v.getClass().getSimpleName();
        int count=duplicates.containsKey(id)?duplicates.get(id):0;duplicates.put(id,count+1);if(count>0)id+="."+count;
        float density=getResources().getDisplayMetrics().density;
        int width=Math.max(v instanceof Button||v instanceof ImageButton?32:1,Math.round(v.getWidth()/density));
        int height=Math.max(v instanceof Button||v instanceof ImageButton?32:1,Math.round(v.getHeight()/density));
        if(v instanceof Button){Button button=(Button)v;android.graphics.Paint.FontMetricsInt metrics=button.getPaint().getFontMetricsInt();height=Math.max(height,(int)Math.ceil((Math.max(1,button.getLineCount())*(metrics.descent-metrics.ascent)+v.getPaddingTop()+v.getPaddingBottom())/density));}
        FreeLayout.Item item=new FreeLayout.Item(id,Math.round(x/density),Math.round(y/density),width,height);item.visible=!hidden;
        defaults.items.put(id,item);individualViews.put(id,v);
    }
    private void mountFreeBoard(){
        float density=getResources().getDisplayMetrics().density;
        int width=Math.round((root.getWidth()-root.getPaddingLeft()-root.getPaddingRight())/density);
        FreeLayout defaults=new FreeLayout(width,Math.round(scale*100));individualViews.clear();
        Map<String,Integer> duplicates=new HashMap<>();
        for(int i=0;i<root.getChildCount();i++)collectControls(root.getChildAt(i),-root.getPaddingLeft(),-root.getPaddingTop(),false,defaults,duplicates);
        defaults.repair();
        String key=freeKey();FreeLayout saved=FreeLayout.merge(FreeLayout.decode(prefs.getString(key,null)),defaults),model=saved;
        if(editing){
            if(layoutDrafts==null)layoutDrafts=new LayoutDrafts();
            model=layoutDrafts.edit(key,saved);
            model=layoutDrafts.replace(key,pendingPreset>=0?defaults:FreeLayout.merge(model,defaults));
        }
        pendingPreset=-1;layout=null;
        final FreeLayout active=model;
        FreeBoard board=new FreeBoard(this,active,new LinkedHashMap<>(individualViews),editing,accent,new FreeBoard.Editor(){
            public void options(String id){elementOptions(id);}
            public void changed(String message){if(editMessage!=null)editMessage.setText(message);}
        });
        root.removeAllViews();editMessage=null;if(editing)editorToolbar();
        pageScroll=new ScrollView(this);pageScroll.setFillViewport(true);pageScroll.setClipToPadding(false);
        if(editing)pageScroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->board.invalidate());
        root.addView(pageScroll,new LinearLayout.LayoutParams(-1,0,1));pageScroll.addView(board,new ScrollView.LayoutParams(-1,-2));
        freeBoard=board;footer();root.requestApplyInsets();update();
    }
    private void beginArrangement(){
        if(editing)return;saveDraft();hideKeyboard();session.pauseAll();layoutDrafts=new LayoutDrafts();
        if(freeBoard!=null)layoutDrafts.edit(freeKey(),freeBoard.layout);
        editing=true;build();
    }
    private void editorToolbar(){
        LinearLayout bar=column();bar.setPadding(dp(8),dp(3),dp(8),dp(3));bar.setBackgroundColor(card);root.addView(bar);
        LinearLayout actions=row();bar.addView(actions);editorButton(actions,"Speichern & fertig",this::saveArrangement,true);editorButton(actions,"Änderungen verwerfen",this::discardArrangement,false);
        LinearLayout tools=row();bar.addView(tools);editorButton(tools,"Elemente",this::elementSelection,false);editorButton(tools,"Vorlagen",this::presets,false);
        editorButton(tools,"Seite",()->dialog().setTitle("Anordnung bearbeiten").setItems(new String[]{"Bedienung","Tastatur","Verbindung","Vollbild"},(d,which)->{saveDraft();fullscreen=which==3;if(which<3)tab=which;build();}).setNegativeButton("Zurück",null).show(),false);
        editMessage=hint(bar,"Feines Raster · Ziehen: einfügen · Tippen: Größe und Position");editMessage.setTextSize(11);editMessage.setMaxLines(2);editMessage.setMinLines(2);editMessage.setEllipsize(TextUtils.TruncateAt.END);
    }
    private Button editorButton(LinearLayout row,String name,Runnable action,boolean primary){Button b=rowButton(row,name,action,primary);b.setTextSize(12);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(42),1);p.setMargins(dp(2),dp(2),dp(2),0);b.setLayoutParams(p);return b;}
    private void saveArrangement(){
        if(!editing||layoutDrafts==null)return;
        try{
            android.content.SharedPreferences.Editor writer=prefs.edit();
            for(Map.Entry<String,String> value:layoutDrafts.savedValues().entrySet())writer.putString(value.getKey(),value.getValue());
            if(!writer.commit()){toast("Anordnung konnte nicht gespeichert werden. Bitte erneut versuchen.");return;}
            editing=false;layoutDrafts=null;pendingPreset=-1;layout=null;build();toast("Anordnung gespeichert");
        }catch(IllegalStateException error){toast("Bitte überlappende oder ungültige Positionen korrigieren.");}
    }
    private void discardArrangement(){editing=false;layoutDrafts=null;pendingPreset=-1;layout=null;build();toast("Änderungen verworfen");}
    private void leaveArrangement(){
        if(layoutDrafts==null||!layoutDrafts.dirty()){editing=false;layoutDrafts=null;build();return;}
        dialog().setTitle("Anordnung geändert").setMessage("Was möchtest du mit deinen Änderungen machen?")
            .setPositiveButton("Speichern",(d,w)->saveArrangement()).setNegativeButton("Verwerfen",(d,w)->discardArrangement()).setNeutralButton("Weiter bearbeiten",null).show();
    }
    private boolean requiredElement(String id){return id.equals("button.Anordnen")||id.equals("button.← Zurück");}
    private String elementName(String id){
        View v=individualViews.get(id);if(v==pad)return "Touchpad";if(v==input)return "Texteingabe";
        CharSequence description=v==null?null:v.getContentDescription();if(description==null||description.length()==0)description=v instanceof TextView?((TextView)v).getText():id;
        String value=description.toString().replace('\n',' ');return value.length()>65?value.substring(0,62)+"…":value;
    }
    private void elementSelection(){
        if(!editing||freeBoard==null)return;
        List<String> ids=new ArrayList<>(individualViews.keySet());String[] names=new String[ids.size()];boolean[] checked=new boolean[ids.size()];
        for(int i=0;i<ids.size();i++){names[i]=elementName(ids.get(i));checked[i]=freeBoard.layout.item(ids.get(i)).visible;}
        layoutDialog=dialog().setTitle("Sichtbare einzelne Elemente").setMultiChoiceItems(names,checked,(d,index,on)->{
            String id=ids.get(index);if(!on&&requiredElement(id)){((AlertDialog)d).getListView().setItemChecked(index,true);toast("Dieser Knopf bleibt zum Anordnen erreichbar.");return;}
            if(!freeBoard.layout.show(id,on)){((AlertDialog)d).getListView().setItemChecked(index,!on);toast("Am unteren Rand ist kein Platz für dieses Element.");return;}freeBoard.refresh();if(editMessage!=null)editMessage.setText("Sichtbarkeit geändert · noch nicht gespeichert.");
        }).setPositiveButton("Fertig",null).show();
    }
    private EditText geometryField(LinearLayout row,String title,int number){
        LinearLayout field=column();LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(3),0,dp(3),0);row.addView(field,p);
        label(field,title,12,muted,false);EditText value=new EditText(this);value.setTextColor(text);value.setSingleLine(true);value.setTextSize(15);value.setInputType(InputType.TYPE_CLASS_NUMBER);value.setText(String.valueOf(number));value.setSelectAllOnFocus(true);field.addView(value,new LinearLayout.LayoutParams(-1,dp(44)));return value;
    }
    private void elementOptions(String id){
        if(!editing||freeBoard==null)return;FreeLayout.Item item=freeBoard.layout.item(id);if(item==null)return;
        LinearLayout box=column();box.setPadding(dp(12),dp(4),dp(12),dp(8));LinearLayout position=row(),size=row();box.addView(position);box.addView(size);
        EditText x=geometryField(position,"Links",item.x),y=geometryField(position,"Oben",item.y),width=geometryField(size,"Breite",item.width),height=geometryField(size,"Höhe",item.height);
        hint(box,"Positionen rasten am Raster ein. Andere Elemente rücken bei Bedarf nach unten. Erst „Speichern & fertig“ übernimmt die Änderungen.");
        AlertDialog.Builder builder=dialog().setTitle(elementName(id)).setView(box).setPositiveButton("Übernehmen",null).setNegativeButton("Zurück",null);
        if(!requiredElement(id))builder.setNeutralButton("Ausblenden",(d,w)->{freeBoard.layout.show(id,false);freeBoard.refresh();});
        layoutDialog=builder.create();layoutDialog.setOnShowListener(d->layoutDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                int nx=Integer.parseInt(x.getText().toString()),ny=Integer.parseInt(y.getText().toString()),nw=Integer.parseInt(width.getText().toString()),nh=Integer.parseInt(height.getText().toString());
                View control=individualViews.get(id);int minW=control==pad?80:control instanceof EditText?80:control instanceof Button||control instanceof ImageButton?32:10;
                int minH=control==pad?80:control instanceof EditText?48:control instanceof Button||control instanceof ImageButton?Math.max(32,Math.round(40*scale)):Math.max(10,Math.round(12*scale));
                if(control instanceof Button){android.graphics.Paint.FontMetricsInt metrics=((Button)control).getPaint().getFontMetricsInt();float density=getResources().getDisplayMetrics().density;minH=Math.max(minH,(int)Math.ceil((2*(metrics.descent-metrics.ascent)+control.getPaddingTop()+control.getPaddingBottom())/density));}
                if((nw!=item.width||nh!=item.height)&&(nw<minW||nh<minH)){toast("Mindestens "+minW+" × "+minH+" für dieses Element.");return;}
                if(!freeBoard.layout.insert(id,nx,ny,nw,nh)){toast("Die Position liegt außerhalb der Fläche oder am unteren Rand ist kein Platz mehr.");return;}
                freeBoard.refresh();if(editMessage!=null)editMessage.setText("Am Raster eingefügt · noch nicht gespeichert.");layoutDialog.dismiss();
            }catch(NumberFormatException error){toast("Bitte gültige ganze Zahlen eingeben.");}
        }));layoutDialog.show();
    }
    private TouchPad touchpad(){
        TouchPad p=new TouchPad(this,new TouchPad.Sink(){public void move(int x,int y,int w){hid.move(x,y,w);}public void click(int b){if(!session.dragging)hid.click(b);}public void touching(boolean value){session.touching=value;session.hold();}},accent,muted);
        p.setSpeedPercent(prefs.getInt(TOUCHPAD_SPEED,TouchPad.DEFAULT_SPEED_PERCENT));
        p.setTag("pad");p.setClipToOutline(true);p.setMinimumHeight(0);p.uiScale=scale;p.setBackground(shape(card,border,15));connectedViews.add(p);return p;
    }
    private void airTile(LinearLayout c){
        airTitle=label(c,"Air-Maus · pausiert",18,text,true);
        airDescription=hint(c,"");airDescription.setMinLines(2);airDescription.setMaxLines(2);airDescription.setEllipsize(TextUtils.TruncateAt.END);
        airToggle=button(c,"Air-Maus einschalten",()->{session.toggleAir();if(!air.available())toast("Kein Gyroskop gefunden.");},true);
        LinearLayout controls=row();c.addView(controls);
        calibrate=rowButton(controls,"Kalibrieren",()->{air.calibrate();toast("Handy etwa eine Sekunde stillhalten.");update();},false);
        grip=rowButton(controls,"Umgreifen",()->{},false);grip.setContentDescription("Zum Umgreifen gedrückt halten");
        grip.setOnTouchListener((v,e)->{if(!v.isEnabled())return false;if(e.getActionMasked()==MotionEvent.ACTION_DOWN){session.holding=true;session.hold();v.setPressed(true);update();}else if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){session.holding=false;session.hold();v.setPressed(false);if(e.getActionMasked()==MotionEvent.ACTION_UP)v.performClick();update();}return true;});
        help(c,"Handy leicht drehen. Zum Neuausrichten „Umgreifen“ gedrückt halten.");
    }
    private void clicksTile(LinearLayout c){
        LinearLayout clicks=row();c.addView(clicks);pressButton(clicks,"Linksklick",1);pressButton(clicks,"Rechtsklick",2);
        dragButton=rowButton(clicks,"Ziehen",()->{session.dragging=!session.dragging;hid.button(1,session.dragging);},false);connectedViews.add(dragButton);
    }
    private void pressButton(LinearLayout parent,String value,int mask){
        Button b=rowButton(parent,value,()->{},false);connectedViews.add(b);
        b.setOnTouchListener((v,e)->{if(!v.isEnabled())return false;if(e.getActionMasked()==MotionEvent.ACTION_DOWN){if(mask==1&&session.dragging){session.dragging=false;update();}hid.button(mask,true);v.setPressed(true);}else if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){hid.button(mask,false);v.setPressed(false);if(e.getActionMasked()==MotionEvent.ACTION_UP)v.performClick();}return true;});
    }
    private void wheelTile(LinearLayout c){
        LinearLayout r=row();c.addView(r);repeatScroll(rowButton(r,"↑ Scrollen",()->{},false),1);repeatScroll(rowButton(r,"↓ Scrollen",()->{},false),-1);help(c,"Auch zwei Finger auf dem Touchpad scrollen.");
    }
    private void repeatScroll(Button b,int value){
        connectedViews.add(b);final Runnable[] repeat=new Runnable[1];repeat[0]=()->{if(b.isPressed()&&b.isEnabled()&&resumed){hid.move(0,0,value);main.postDelayed(repeat[0],90);}};
        b.setOnTouchListener((v,e)->{if(!v.isEnabled())return false;if(e.getActionMasked()==MotionEvent.ACTION_DOWN){v.setPressed(true);repeat[0].run();}else if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){v.setPressed(false);main.removeCallbacks(repeat[0]);if(e.getActionMasked()==MotionEvent.ACTION_UP)v.performClick();}return true;});
    }
    private void buildFullscreen(){
        LinearLayout top=row();top.setPadding(u(10),u(3),u(10),u(4));root.addView(top);rowButton(top,"← Zurück",()->{fullscreen=false;build();},false);rowButton(top,"Anordnen",this::beginArrangement,false);
        quick=rowButton(top,"Verbinden",this::quickConnect,true);pad=touchpad();LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,0,1);p.setMargins(u(10),u(5),u(10),u(5));root.addView(pad,p);
        LinearLayout clicks=column();clicks.setPadding(u(9),0,u(9),u(3));root.addView(clicks);clicksTile(clicks);wheelTile(clicks);footer();
    }
    private void keyboardTile(LinearLayout c){
        help(c,"Mit Gboard tippen, diktieren oder Emojis auswählen. Anschließend senden.");
        input=new EditText(this);input.setTag("text.input");input.setTextColor(text);input.setHintTextColor(muted);input.setTextSize(16*scale);input.setGravity(Gravity.TOP|Gravity.START);input.setHint("Dein Text für Windows …");input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});input.setMinLines(2);input.setMaxLines(5);input.setPadding(u(10),u(8),u(10),u(8));input.setBackground(shape(bg,border,10));input.setText(session.textDraft.value);input.setEnabled(!editing);
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);ep.topMargin=u(5);c.addView(input,ep);
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){session.textDraft.value=s.toString();update();}public void afterTextChanged(Editable e){}});
        sendButton=button(c,"Text an PC senden",()->{saveDraft();try{hid.text(session.textDraft.value);hideKeyboard();}catch(IllegalArgumentException e){dialog().setTitle("Text noch nicht gesendet").setMessage(e.getMessage()).setPositiveButton("Verstanden",null).show();}},true);
        LinearLayout actions=row();c.addView(actions);cancelText=rowButton(actions,"Abbrechen",hid::cancelText,false);restoreButton=rowButton(actions,"Text zurückholen",()->{session.textDraft.value=session.textDraft.lastSent;update();},false);
        Button emojis=button(c,"😊 Windows-Emojis",()->hid.key(8,55),false);keyboardViews.add(emojis);
        unicodeInfo=hint(c,"");hint(c,"Text wird nach dem Senden geleert; bei Abbruch bleibt er erhalten.");
    }
    private void keysTile(LinearLayout c){
        LinearLayout a=row();c.addView(a);keyButton(a,"Esc",0,41);keyButton(a,"Tab",0,43);keyButton(a,"⌫",0,42);keyButton(a,"Enter",0,40);
        LinearLayout b=row();c.addView(b);keyButton(b,"←",0,80);keyButton(b,"↑",0,82);keyButton(b,"↓",0,81);keyButton(b,"→",0,79);
        LinearLayout d=row();c.addView(d);keyButton(d,"Alle",1,4);keyButton(d,"Kopieren",1,6);keyButton(d,"Einfügen",1,25);
        LinearLayout e=row();c.addView(e);keyButton(e,"Rückgängig",1,28);keyButton(e,"Alt + Tab",4,43);keyButton(e,"Desktop",8,7);
        help(c,"Kopieren/Einfügen verwendet die Zwischenablage des PCs.");
    }
    private void keyButton(LinearLayout p,String value,int mod,int key){Button b=rowButton(p,value,()->hid.key(mod,key),false);b.setTextSize(12*scale);keyboardViews.add(b);}
    private void connectionPage(LinearLayout page){
        LinearLayout c=card(page,"Mit Windows verbinden");
        hint(c,"Einmalig am PC: Bluetooth-Gerät hinzufügen → dein Handy auswählen und Kopplung bestätigen. Später genügt oben „Verbinden“.");
        retryButton=button(c,"Bluetooth starten",()->ensureBluetooth(false),true);pairButton=button(c,"Für PC sichtbar · 2 Minuten",()->ensureBluetooth(true),false);
        disconnectButton=button(c,"PC-Verbindung trennen",()->{session.pauseAll();hid.disconnect();},false);
        button(c,"Android-Bluetooth-Einstellungen",()->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)),false);
        LinearLayout paired=card(page,"Gekoppelte Geräte");deviceList=column();paired.addView(deviceList);deviceSignature="";
        LinearLayout unicode=card(page,"Gboard-Emojis und Unicode");
        unicodeInfo=hint(unicode,hid.unicode.status);hint(unicode,"Für direkte Gboard-Emojis in Windows den optionalen PC-Helfer starten. Er benötigt kein WLAN und keine Installation. Normaler Text und Maus funktionieren auch ohne Helfer.");
        Button connect=button(unicode,"Emoji-Erweiterung verbinden",this::unicodeDialog,false);connectedViews.add(connect);
        button(unicode,"Windows-Helfer herunterladen",()->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/Tim-Gurke/AirPit/releases/download/v"+BuildConfig.VERSION_NAME+"/AirPit-Unicode-Windows.zip"))),false);
        LinearLayout service=card(page,"Verbindung im Hintergrund");hint(service,"Beim App-Wechsel bleibt Bluetooth verbunden. Die Steuerung wird pausiert. Für Air-Maus bei ausgeschaltetem S10-Display die Option in den Einstellungen aktivieren.");
        button(service,"Steuerung stoppen",session::pauseAll,false);button(service,"Verbindung und Dienst beenden",()->{session.ended();stopService(new Intent(this,ControlService.class));},false);
    }
    private void quickConnect(){
        if(hid.connected()){if(air.available())session.toggleAir();else{tab=2;build();}return;}
        pendingConnect=true;ensureBluetooth(false);
    }
    private void choosePc(){
        if(!hasPermissions())return;String last=prefs.getString("lastHost","");if(!last.isEmpty()){hid.reconnectLast();return;}
        List<BluetoothDevice> devices=hid.paired();if(devices.isEmpty()){tab=2;build();toast("Zuerst in Windows das S10 als Bluetooth-Gerät koppeln.");return;}
        String[] names=new String[devices.size()];for(int i=0;i<names.length;i++)names[i]=HidController.name(devices.get(i));
        dialog().setTitle("Windows-PC auswählen").setItems(names,(d,i)->hid.connect(devices.get(i))).setNegativeButton("Zurück",null).show();
    }
    private boolean hasPermissions(){return Build.VERSION.SDK_INT<31||(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)==PackageManager.PERMISSION_GRANTED);}
    private void ensureBluetooth(boolean discover){
        pendingDiscover|=discover;
        if(hid.adapter==null){toast("Dieses Gerät hat kein Bluetooth.");return;}
        if(!hasPermissions()){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE},BT_PERMISSION);return;}
        try{
            if(!hid.adapter.isEnabled()){startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),ENABLE);return;}
            startSession();
            if(pendingDiscover){pendingDiscover=false;startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,120),DISCOVER);}
            if(pendingConnect){pendingConnect=false;choosePc();}
            if(Build.VERSION.SDK_INT>=33&&!prefs.getBoolean("notificationAsked",false)){prefs.edit().putBoolean("notificationAsked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATIONS);}
        }catch(RuntimeException e){toast("Bluetooth-Freigabe oder Systemeinstellungen prüfen.");}
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){super.onRequestPermissionsResult(code,permissions,grants);
        if(code==BT_PERMISSION){if(hasPermissions())ensureBluetooth(pendingDiscover);else{pendingDiscover=pendingConnect=false;toast("Bitte Geräte in der Nähe erlauben.");}}
        if(code==NOTIFICATIONS&&pendingScreenOff){pendingScreenOff=false;boolean allowed=Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;session.setKeepOff(allowed);if(!allowed)toast("Für Display-aus-Steuerung bitte die Benachrichtigung mit Stopptaste erlauben.");}
        update();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==ENABLE){if(result==RESULT_OK)ensureBluetooth(pendingDiscover);else pendingDiscover=pendingConnect=false;}if(request==DISCOVER&&result>0)toast("Jetzt das S10 in Windows koppeln.");update();}
    private void unicodeDialog(){
        if(!hid.connected())return;
        EditText pin=new EditText(this);pin.setInputType(InputType.TYPE_CLASS_NUMBER);pin.setHint("Sechsstelliger Code");pin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        dialog().setTitle("Emoji-Erweiterung koppeln").setMessage("AirPit Unicode auf dem Windows-PC starten und den dort angezeigten Code eingeben. Danach das gewünschte Textfeld am PC anklicken.").setView(pin).setPositiveButton("Verbinden",(d,w)->{String code=pin.getText().toString();if(code.length()!=6){toast("Bitte den sechsstelligen Code eingeben.");return;}hid.unicode.connect(hid.host,code);}).setNegativeButton("Zurück",null).show();
    }
    private void update(){
        if(root==null)return;boolean connected=hid.connected(),typing=hid.typing;
        if(status!=null){status.setText(connected?"● "+HidController.name(hid.host):hid.connecting?"● Verbinde …":"● Nicht verbunden");status.setTextColor(connected?accent:muted);}
        if(quick!=null){quick.setText(connected?(air.available()?(session.airOn?"Pausieren":"Air-Maus an"):"Verbunden"):hid.connecting?"Verbindet …":"Verbinden");quick.setEnabled(!hid.connecting);style(quick,!connected||!session.airOn);}
        if(statusDetail!=null){String d=typing?"Übertragung · "+hid.sent+" / "+hid.total:!hasPermissions()?"Bluetooth über die Taste oben erlauben.":hid.detail();statusDetail.setText(d);statusDetail.setContentDescription(d);}
        for(View v:connectedViews)v.setEnabled(connected&&!editing);for(View v:keyboardViews)v.setEnabled(connected&&!typing&&!editing);if(pad!=null)pad.invalidate();
        if(airToggle!=null){airToggle.setEnabled(connected&&air.available()&&!editing);airToggle.setText(session.airOn?"Air-Maus pausieren":"Air-Maus einschalten");airTitle.setText(!air.available()?"Air-Maus · Sensor fehlt":session.airOn?air.calibrating()?"Air-Maus · kalibriert …":session.holding||session.touching?"Air-Maus · Zeiger hält":"Air-Maus · aktiv":"Air-Maus · pausiert");airTitle.setTextColor(session.airOn?accent:text);airDescription.setText(air.calibrating()?"Handy kurz stillhalten.":session.keepOff()?"Steuerung auch bei ausgeschaltetem Display":"Leicht drehen, um den Mauszeiger zu bewegen.");calibrate.setEnabled(session.airOn&&!air.calibrating()&&!editing);grip.setEnabled(session.airOn&&!air.calibrating()&&!editing);}
        if(dragButton!=null){dragButton.setText(session.dragging?"Ziehen: AN":"Ziehen");style(dragButton,session.dragging);}
        if(input!=null&&!input.getText().toString().equals(session.textDraft.value)){input.setText(session.textDraft.value);input.setSelection(input.length());}
        if(sendButton!=null){sendButton.setText(typing?"Sendet · "+hid.sent+" / "+hid.total:"Text an PC senden");sendButton.setEnabled(connected&&!typing&&!editing&&!session.textDraft.value.isEmpty());cancelText.setEnabled(typing&&!editing);restoreButton.setEnabled(!typing&&!editing&&session.textDraft.value.isEmpty()&&!session.textDraft.lastSent.isEmpty());}
        if(unicodeInfo!=null)unicodeInfo.setText(hid.unicode.ready?"✓ Gboard-Emojis und Unicode bereit":hid.unicode.connecting?hid.unicode.status:tab==2?hid.unicode.status:"Deutsch (Deutschland) · Feststelltaste aus. Gboard-Emojis über den optionalen PC-Helfer.");
        if(retryButton!=null){retryButton.setText(!hasPermissions()?"Bluetooth erlauben":hid.registered?"Bluetooth ist bereit":"Bluetooth starten");retryButton.setEnabled(!hasPermissions()||(!hid.registered&&!hid.registering));pairButton.setEnabled(hasPermissions()&&!connected);disconnectButton.setEnabled(connected);}
        if(deviceList!=null){List<BluetoothDevice> ds=hasPermissions()?hid.paired():new ArrayList<>();String signature=ds.toString()+connected+hid.registered+hid.connecting;if(!signature.equals(deviceSignature)){if(freeBoard!=null){if(!editing&&!constructing&&!rebuildPosted){rebuildPosted=true;main.post(refreshSurface);}return;}deviceSignature=signature;deviceList.removeAllViews();if(ds.isEmpty())hint(deviceList,"Noch keine Geräte. Am PC koppeln.");else for(BluetoothDevice d:ds){Button b=button(deviceList,HidController.name(d)+(connected&&d.equals(hid.host)?" · verbunden":""),()->hid.connect(d),connected&&d.equals(hid.host));b.setTag("pc."+d.getAddress());b.setEnabled(!connected&&!hid.connecting);}}}
        if(editing&&input!=null)input.setEnabled(false);
        if(editing&&freeBoard!=null)for(View v:individualViews.values())v.setEnabled(false);
        if(connected&&!session.keepOff()&&resumed)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void settings(){
        saveDraft();hideKeyboard();LinearLayout box=column();box.setPadding(u(18),u(8),u(18),u(12));box.setBackgroundColor(card);
        slider(box,"Darstellungsgröße","uiScale",100,50,150,()->build());
        LinearLayout reset=row();box.addView(reset);rowButton(reset,"100 %",()->{prefs.edit().putInt("uiScale",100).apply();if(uiScaleBar!=null)uiScaleBar.setProgress(50);build();},false);
        slider(box,"Touchpad-Geschwindigkeit",TOUCHPAD_SPEED,TouchPad.DEFAULT_SPEED_PERCENT,TouchPad.MIN_SPEED_PERCENT,TouchPad.MAX_SPEED_PERCENT,this::applyTouchpadSpeed);
        hint(box,"25–300 % · 100 % ist die bisherige Geschwindigkeit. Kleine Werte für genaues Zielen, große für schnelle Wege.");
        button(box,"Touchpad auf 100 %",()->{prefs.edit().putInt(TOUCHPAD_SPEED,TouchPad.DEFAULT_SPEED_PERCENT).apply();if(touchpadSpeedBar!=null)touchpadSpeedBar.setProgress(TouchPad.DEFAULT_SPEED_PERCENT-TouchPad.MIN_SPEED_PERCENT);applyTouchpadSpeed();},false);
        slider(box,"Air-Maus-Empfindlichkeit","speed",50,0,100,session::configure);slider(box,"Glättung","smooth",35,0,100,session::configure);slider(box,"Zitterfilter","steady",30,0,100,session::configure);
        axisSettings(box);
        toggle(box,"Richtung links / rechts umkehren","invertX",session::configure);toggle(box,"Richtung hoch / runter umkehren","invertY",session::configure);
        toggle(box,"Erklärungen anzeigen","help",()->showPage());
        Switch off=toggle(box,"Bei Display aus weitersteuern","screenOff",()->{if(prefs.getBoolean("screenOff",false)&&Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){prefs.edit().putBoolean("screenOff",false).apply();pendingScreenOff=true;requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATIONS);}else session.setKeepOff(prefs.getBoolean("screenOff",false));});
        hint(box,"Bluetooth bleibt beim App-Wechsel verbunden. Aktive Air-Maus im Display-aus-Modus über die Benachrichtigung stoppen.");
        button(box,"Diagnose ohne Textinhalte",this::diagnostics,false);
        ScrollView scroll=new ScrollView(this);scroll.addView(box);dialog().setTitle("Einstellungen").setView(scroll).setPositiveButton("Fertig",(d,w)->{saveDraft();build();}).show();
    }
    private void applyTouchpadSpeed(){if(pad!=null)pad.setSpeedPercent(prefs.getInt(TOUCHPAD_SPEED,TouchPad.DEFAULT_SPEED_PERCENT));}
    private MotionAxes motionAxes(){return new MotionAxes(prefs.getInt("gyroHorizontal",MotionAxes.TURN),prefs.getInt("gyroVertical",MotionAxes.FORWARD_TILT));}
    private void saveAxes(MotionAxes axes){prefs.edit().putInt("gyroHorizontal",axes.horizontal).putInt("gyroVertical",axes.vertical).apply();session.configure();}
    private static final String[] AXIS_NAMES={"Drehen","Seitlich neigen / schwenken","Vor / zurück neigen"};
    private void axisSettings(LinearLayout box){
        label(box,"Bewegungsachsen",16,text,true);
        Button[] choices=new Button[2];
        for(int i=0;i<2;i++){
            final int direction=i;
            choices[i]=button(box,"",()->{
                MotionAxes axes=motionAxes();
                dialog().setTitle(direction==0?"Links / rechts steuern":"Hoch / runter steuern")
                    .setSingleChoiceItems(AXIS_NAMES,direction==0?axes.horizontal:axes.vertical,(d,which)->{
                        MotionAxes current=motionAxes();saveAxes(direction==0?current.horizontal(which):current.vertical(which));
                        updateAxisLabels(choices);d.dismiss();
                    }).setNegativeButton("Zurück",null).show();
            },false);
            choices[i].setLayoutParams(new LinearLayout.LayoutParams(-1,Math.max(dp(48),u(58))));
        }
        button(box,"Achsen tauschen",()->{saveAxes(motionAxes().swapped());updateAxisLabels(choices);},false);
        hint(box,"Seitlich neigen: die linke oder rechte Handykante absenken. Jede Bewegung steuert eine Richtung; belegte Zuordnungen werden getauscht.");
        updateAxisLabels(choices);
    }
    private void updateAxisLabels(Button[] choices){
        MotionAxes axes=motionAxes();
        String horizontal="Links / rechts: "+AXIS_NAMES[axes.horizontal],vertical="Hoch / runter: "+AXIS_NAMES[axes.vertical];
        choices[0].setText(horizontal);choices[0].setContentDescription(horizontal);choices[1].setText(vertical);choices[1].setContentDescription(vertical);
    }
    private Switch toggle(LinearLayout p,String name,String key,Runnable apply){Switch s=new Switch(this);s.setText(name);s.setTextSize(13*scale);s.setTextColor(text);s.setPadding(0,u(9),0,u(9));s.setChecked(prefs.getBoolean(key,false));p.addView(s,new LinearLayout.LayoutParams(-1,-2));s.setOnCheckedChangeListener((b,v)->{prefs.edit().putBoolean(key,v).apply();apply.run();});return s;}
    private void slider(LinearLayout p,String name,String key,int fallback,int min,int max,Runnable apply){
        int initial=Math.max(min,Math.min(max,prefs.getInt(key,fallback)));TextView title=label(p,name+" · "+initial+" %",14,text,true);SeekBar bar=new SeekBar(this);if(key.equals("uiScale"))uiScaleBar=bar;if(key.equals(TOUCHPAD_SPEED))touchpadSpeedBar=bar;bar.setMax(max-min);bar.setProgress(initial-min);bar.setProgressTintList(ColorStateList.valueOf(accent));bar.setThumbTintList(ColorStateList.valueOf(accent));p.addView(bar,new LinearLayout.LayoutParams(-1,dp(40)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int v,boolean user){title.setText(name+" · "+(v+min)+" %");if(user)prefs.edit().putInt(key,v+min).apply();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){saveDraft();apply.run();}});
    }
    private void diagnostics(){String report=session.diagnostics();dialog().setTitle("Diagnose").setMessage(report).setPositiveButton("Schließen",null).setNeutralButton("Kopieren",(d,w)->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("AirPit Diagnose",report));toast("Diagnose kopiert.");}).show();}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
}
