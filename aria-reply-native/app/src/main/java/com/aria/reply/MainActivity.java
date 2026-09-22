package com.aria.reply;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayDeque;

public class MainActivity extends Activity {
    static final int CONTACTS = 22, NOTIFS = 23;
    final int cocoa=Color.rgb(36,23,20), surface=Color.rgb(73,48,38), surface2=Color.rgb(90,58,47);
    final int cream=Color.rgb(240,226,208), muted=Color.rgb(189,170,152), sage=Color.rgb(168,181,138), terr=Color.rgb(201,122,90), teal=Color.rgb(134,170,160);
    LinearLayout root, body; AriaStore store; SecurePrefs secure;
    String current="home", chatContact="";
    final ArrayDeque<String> backStack = new ArrayDeque<>();
    final String[] tabs={"⌂\nHome","◇\nTest","▤\nChats","✓\nActions","⚙\nSystem"};

    @Override public void onCreate(Bundle b){ super.onCreate(b); store=new AriaStore(this); secure=new SecurePrefs(this); render(); }
    @Override public void onBackPressed(){ if(!backStack.isEmpty()){ current=backStack.pop(); render(); } else if(!"home".equals(current)){ current="home"; render(); } else super.onBackPressed(); }

    TextView tv(String s,float z){ TextView v=new TextView(this);v.setText(s);v.setTextColor(cream);v.setTextSize(z);v.setLineSpacing(0,1.05f);return v; }
    GradientDrawable bg(int color,float r){ GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(r);return g; }
    Button btn(String label,String icon,View.OnClickListener l){ Button b=new Button(this);b.setText(icon+"  "+label);b.setTextColor(cream);b.setTextSize(14);b.setAllCaps(false);b.setGravity(Gravity.CENTER_VERTICAL);b.setBackground(bg(surface2,24));b.setPadding(24,0,24,0);b.setOnClickListener(l);b.setMinHeight(54);return b; }
    LinearLayout card(){ LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(20,18,20,18);c.setBackground(bg(surface,28));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(14,8,14,8);c.setLayoutParams(p);return c; }
    void base(String title,String sub,int selected){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(cocoa);
        LinearLayout top=new LinearLayout(this);top.setPadding(22,24,22,12);top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);TextView a=tv(title,25);a.setTypeface(null,1);t.addView(a);TextView s=tv(sub,12);s.setTextColor(muted);t.addView(s);top.addView(t,new LinearLayout.LayoutParams(0,-2,1));root.addView(top);
        ScrollView sv=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(0,4,0,16);sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=new LinearLayout(this);nav.setPadding(6,6,6,6);nav.setBackgroundColor(surface);
        for(int i=0;i<tabs.length;i++){final int n=i;Button b=new Button(this);b.setText(tabs[i]);b.setTextColor(i==selected?sage:muted);b.setTextSize(10);b.setAllCaps(false);b.setGravity(Gravity.CENTER);b.setBackground(i==selected?bg(surface2,18):bg(Color.TRANSPARENT,18));b.setOnClickListener(v->{rootNav(n);});nav.addView(b,new LinearLayout.LayoutParams(0,64,1));}
        root.addView(nav);setContentView(root);
    }
    void rootNav(int n){ backStack.clear(); current=new String[]{"home","api","chats","tasks","system"}[n]; render(); }
    void open(String target){ if(target.equals(current)) return; backStack.push(current); current=target; render(); }
    void render(){
        if(current.startsWith("chat:")){chatContact=current.substring(5);renderChat();return;}
        switch(current){case "api":renderApi();break;case "chats":renderChats();break;case "tasks":renderTasks();break;case "system":renderSystem();break;case "diagnostics":renderDiagnostics();break;default:renderHome();}
    }

    void renderHome(){
        base("ARIA","WhatsApp response system • V30",0);
        LinearLayout hero=card();hero.addView(tv("SYSTEM STATUS",11));TextView st=tv(store.bool("auto_reply",false)?"● AUTO REPLY ACTIVE":"○ AUTO REPLY OFF",22);st.setTextColor(store.bool("auto_reply",false)?sage:terr);hero.addView(st);hero.addView(tv("Capture → Burst → Context → AI → Reply",13));body.addView(hero);
        body.addView(btn("Auto Reply", "↯",v->{store.setBool("auto_reply",!store.bool("auto_reply",false));render();}));
        body.addView(btn("Chats & Context","▤",v->open("chats")));
        body.addView(btn("Pending Actions ("+store.pendingActions()+")","✓",v->open("tasks")));
        body.addView(btn("API Test Lab","◇",v->open("api")));
        body.addView(btn("System Diagnostics","⚙",v->open("diagnostics")));
        body.addView(btn("Synthetic Capture","⚡",v->{store.put("last_capture","Synthetic Contact: Bhai mujhe bhej de");store.addMessage("Synthetic Contact","user","Bhai mujhe bhej de");Toast.makeText(this,"Synthetic message captured",Toast.LENGTH_SHORT).show();}));
    }

    void renderChats(){
        base("CHATS","Persistent sender-specific context",2);
        try{
            JSONArray a=store.contacts();
            if(a.length()==0) body.addView(tv("No captured conversations yet.",15));
            for(int i=0;i<a.length();i++){
                String n=a.getString(i);LinearLayout c=card();c.addView(tv("▤  "+n,17));c.addView(tv(store.messageCount(n)+" messages",12));String preview=store.context(n,2);TextView p=tv(preview,12);p.setTextColor(muted);c.addView(p);c.setOnClickListener(v->open("chat:"+n));body.addView(c);
            }
        }catch(Exception ignored){}
    }

    void renderChat(){
        base(chatContact,"Conversation • local context",2);
        body.addView(btn("Back to Chats","‹",v->back()));
        LinearLayout info=card();info.addView(tv("CONTACT",11));info.addView(tv(chatContact,19));info.addView(tv("Messages: "+store.messageCount(chatContact),12));body.addView(info);
        LinearLayout c=card();c.addView(tv("FULL CONTEXT",12));TextView context=tv(store.context(chatContact,60),13);context.setTextIsSelectable(true);c.addView(context);body.addView(c);
        body.addView(btn("API Test With This Context","◇",v->{store.put("api_sender",chatContact);open("api");}));
        renderPendingForContact(chatContact);
    }
    void renderPendingForContact(String contact){
        try{JSONArray a=new JSONArray(store.actions());for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);if(o.optBoolean("done")||!contact.equals(o.optString("contact")))continue;LinearLayout c=card();c.addView(tv("✓  PENDING FOLLOW-UP",12));c.addView(tv(o.optString("detail"),13));body.addView(c);}}catch(Exception ignored){}
    }
    void back(){onBackPressed();}

    void renderTasks(){
        base("ACTIONS","Persistent Aria follow-ups",3);
        try{
            JSONArray a=new JSONArray(store.actions());boolean any=false;
            for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);if(o.optBoolean("done"))continue;any=true;long id=o.optLong("id");LinearLayout c=card();CheckBox cb=new CheckBox(this);cb.setText(o.optString("contact")+" • "+o.optString("title"));cb.setTextColor(cream);cb.setTextSize(15);c.addView(cb);c.addView(tv(o.optString("detail"),12));cb.setOnCheckedChangeListener((x,checked)->{if(checked){store.completeAction(id);Notifications.task(this,store);render();}});body.addView(c);}
            if(!any)body.addView(tv("No pending actions.",16));
        }catch(Exception ignored){}
    }

    void renderSystem(){
        base("SYSTEM","Settings • permissions • diagnostics",4);
        body.addView(btn("Diagnostics","✓",v->open("diagnostics")));
        body.addView(btn("Groq API Key","◇",v->keyDialog("Groq")));
        body.addView(btn("Gemini API Key","◇",v->keyDialog("Gemini")));
        body.addView(btn("Notification Access","●",v->{startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));}));
        body.addView(btn("Contacts Access","◎",v->{if(Build.VERSION.SDK_INT>=23)requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},CONTACTS);}));
        if(Build.VERSION.SDK_INT>=33) body.addView(btn("Post Notifications","▣",v->requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFS)));
        body.addView(btn("Battery Optimization","◌",v->batterySettings()));
        body.addView(btn("Hide Raw WhatsApp Notifications: "+(store.bool("hide_raw",false)?"ON":"OFF"),"◈",v->{store.setBool("hide_raw",!store.bool("hide_raw",false));render();}));
        body.addView(btn("Auto Reply: "+(store.bool("auto_reply",false)?"ON":"OFF"),"↯",v->{store.setBool("auto_reply",!store.bool("auto_reply",false));render();}));
    }
    void batterySettings(){try{startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:"+getPackageName())));}catch(Exception e){startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}}

    void keyDialog(String provider){
        EditText e=new EditText(this);e.setHint(provider+" API key");e.setTextColor(cream);e.setHintTextColor(muted);
        new AlertDialog.Builder(this).setTitle(provider+" API Key").setView(e).setPositiveButton("SAVE",(d,w)->{secure.put(provider.equals("Groq")?"groq_key":"gemini_key",e.getText().toString().trim());Toast.makeText(this,"Saved with Android Keystore",Toast.LENGTH_SHORT).show();}).setNegativeButton("CANCEL",null).show();
    }

    void renderApi(){
        base("API TEST LAB","Groq active • Gemini independent",1);
        Spinner sp=new Spinner(this);sp.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Groq (ACTIVE)","Gemini"}));body.addView(sp);
        EditText sender=new EditText(this);sender.setHint("Custom sender / contact");sender.setText(store.get("api_sender","Test Contact"));sender.setTextColor(cream);sender.setHintTextColor(muted);sender.setBackground(bg(surface,18));body.addView(sender);
        EditText msg=new EditText(this);msg.setHint("Custom message");msg.setTextColor(cream);msg.setHintTextColor(muted);msg.setMinHeight(120);msg.setGravity(Gravity.TOP);msg.setBackground(bg(surface,18));body.addView(msg);
        CheckBox use= new CheckBox(this);use.setText("Inject stored contact context");use.setTextColor(cream);use.setChecked(true);body.addView(use);
        TextView preview=tv("Context preview will appear here.",12);preview.setTextColor(muted);preview.setPadding(18,14,18,14);preview.setBackground(bg(surface,18));body.addView(preview);
        View.OnClickListener refresh=v->{String who=sender.getText().toString().trim();preview.setText(use.isChecked()?store.context(who,20):"Context injection OFF");};
        sender.setOnFocusChangeListener((v,f)->{if(!f)refresh.onClick(v);});use.setOnCheckedChangeListener((b,c)->refresh.onClick(b));
        TextView result=tv("READY • HTTP diagnostics enabled",13);result.setPadding(8,16,8,16);body.addView(result);
        body.addView(btn("SEND TEST","▶",v->{
            String selected=sp.getSelectedItem().toString();String provider=selected.startsWith("Gemini")?"Gemini":"Groq";String k=secure.get(provider.equals("Gemini")?"gemini_key":"groq_key");String m=store.get(provider.equals("Gemini")?"gemini_model":"groq_model",provider.equals("Gemini")?"gemini-2.0-flash":"llama-3.1-8b-instant");String who=sender.getText().toString().trim();String context=use.isChecked()?store.context(who,20):"";String system="You are Aria. Sender: "+who+". Context:\n"+context;result.setText("REQUESTING...");AriaApi.call(provider,k,m,system,msg.getText().toString(),(ok,r,e)->{result.setText(ok?"HTTP 200 OK\n\n"+r:"API FAILURE\n"+e);if(!ok)store.put("last_error",e);});
        }));
        body.addView(btn("BACK","‹",v->back()));
    }

    void renderDiagnostics(){
        base("DIAGNOSTICS","Subsystem state",4);
        body.addView(status("Notification access",AriaNotificationListener.live()?"RUNNING":"NOT CONNECTED",AriaNotificationListener.live()?sage:terr));
        body.addView(status("Contacts",Contacts.granted(this)?"GRANTED":"NOT GRANTED",Contacts.granted(this)?sage:terr));
        body.addView(status("Battery optimization",batteryIgnored()?"IGNORED":"ACTIVE",batteryIgnored()?sage:muted));
        body.addView(status("API provider",store.get("provider","Groq")+" / Groq active",sage));
        body.addView(status("RemoteInput","CHECK ACTIVE WHATSAPP",teal));
        body.addView(status("Capture",store.get("last_capture","None"),muted));
        body.addView(status("Context","Stored per contact • "+store.contacts().length()+" contacts",sage));
        body.addView(status("Follow-ups",store.pendingActions()+" pending",store.pendingActions()>0?sage:muted));
        String err=store.get("last_error","");body.addView(status("Last API error",err.isEmpty()?"None":err,err.isEmpty()?muted:terr));
        body.addView(btn("Synthetic capture","⚡",v->{store.put("last_capture","Synthetic Contact: Bhai mujhe bhej de");store.addMessage("Synthetic Contact","user","Bhai mujhe bhej de");render();}));
        body.addView(btn("Back","‹",v->back()));
    }
    TextView status(String a,String b,int color){TextView t=tv(a+"\n"+b,13);t.setTextColor(color);t.setPadding(18,14,18,14);t.setBackground(bg(surface,18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(14,5,14,5);t.setLayoutParams(p);return t;}
    boolean batteryIgnored(){if(Build.VERSION.SDK_INT<23)return true;android.os.PowerManager pm=(android.os.PowerManager)getSystemService(POWER_SERVICE);return pm.isIgnoringBatteryOptimizations(getPackageName());}
}
