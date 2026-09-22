package com.aria.reply;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    static final int CONTACTS=22, NOTIFS=23;
    final int cocoa=Color.rgb(36,23,20), surface=Color.rgb(73,48,38), surface2=Color.rgb(90,58,47);
    final int cream=Color.rgb(240,226,208), muted=Color.rgb(189,170,152), sage=Color.rgb(168,181,138), terr=Color.rgb(201,122,90);
    LinearLayout root,body; AriaStore store; SecurePrefs secure; int page=0;
    String[] tabs={"⌂  Home","◉  Chats","✓  Actions","⌁  Tests","⚙  Settings"};

    @Override public void onCreate(Bundle b){super.onCreate(b);store=new AriaStore(this);secure=new SecurePrefs(this);renderHome();}
    TextView tv(String s,float z){TextView v=new TextView(this);v.setText(s);v.setTextColor(cream);v.setTextSize(z);return v;}
    GradientDrawable bg(int color,float r){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(r);return g;}
    Button btn(String label,String icon,View.OnClickListener l){
        Button b=new Button(this);b.setText(icon+"  "+label);b.setTextColor(cream);b.setTextSize(14);b.setAllCaps(false);b.setGravity(Gravity.CENTER_VERTICAL);
        b.setBackground(bg(surface2,24));b.setPadding(24,0,24,0);b.setOnClickListener(l);
        b.setMinHeight(54); return b;
    }
    LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(20,18,20,18);c.setBackground(bg(surface,28));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(14,8,14,8);c.setLayoutParams(p);return c;}
    void base(String title,String sub){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(cocoa);
        LinearLayout top=new LinearLayout(this);top.setPadding(22,28,22,14);top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);TextView a=tv(title,25);a.setTypeface(null,1);t.addView(a);t.addView(tv(sub,12));top.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(top);
        ScrollView sv=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(0,4,0,12);sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=new LinearLayout(this);nav.setPadding(8,8,8,8);nav.setBackgroundColor(surface);
        for(int i=0;i<tabs.length;i++){final int n=i;Button b=new Button(this);b.setText(tabs[i]);b.setTextColor(i==page?sage:muted);b.setAllCaps(false);b.setTextSize(11);b.setBackgroundColor(Color.TRANSPARENT);b.setOnClickListener(v->{page=n;navigate(n);});nav.addView(b,new LinearLayout.LayoutParams(0,58,1));}
        root.addView(nav);setContentView(root);
    }
    void navigate(int n){if(n==0)renderHome();else if(n==1)renderChats();else if(n==2)renderTasks();else if(n==3)diagnostics();else renderSettings();}
    void renderHome(){
        page=0;base("ARIA","WhatsApp response system • V28");
        LinearLayout hero=card();hero.addView(tv("SYSTEM STATUS",11));TextView st=tv(store.bool("auto_reply",false)?"● AUTO REPLY ACTIVE":"○ AUTO REPLY OFF",22);st.setTextColor(store.bool("auto_reply",false)?sage:terr);hero.addView(st);hero.addView(tv("Capture → Context → AI → Reply",13));body.addView(hero);
        body.addView(btn("Auto Reply","↯",v->{store.bool("auto_reply",!store.bool("auto_reply",false));renderHome();}));
        body.addView(btn("Chats & Context","◉",v->renderChats()));
        body.addView(btn("API Test Lab","◇",v->apiLab()));
        body.addView(btn("Run Diagnostics","✓",v->diagnostics()));
        body.addView(btn("Synthetic Capture","⚡",v->{store.put("last_capture","Synthetic Contact: Bhai mujhe bhej de");store.addMessage("Synthetic Contact","user","Bhai mujhe bhej de");Toast.makeText(this,"Synthetic message captured",Toast.LENGTH_SHORT).show();}));
        body.addView(btn("Pending Follow-ups","□",v->renderTasks()));
    }
    void renderChats(){
        page=1;base("CHATS","Persistent contact context");
        try{
            JSONArray a=new JSONArray(store.get("messages","[]")); java.util.HashSet<String> names=new java.util.HashSet<>();
            for(int i=0;i<a.length();i++)names.add(a.getJSONObject(i).optString("contact"));
            for(String n:names){LinearLayout c=card();c.addView(tv("◉  "+n,17));c.addView(tv(store.context(n,6),12));c.setOnClickListener(v->chat(n));body.addView(c);}
        }catch(Exception ignored){}
        body.addView(btn("API Test Lab","◇",v->apiLab()));
    }
    void chat(String contact){
        page=1;base(contact,"Conversation context");
        body.addView(tv("Stored context",14));
        TextView c=tv(store.context(contact,30),13);c.setPadding(18,18,18,18);body.addView(c);
        body.addView(btn("Back to Chats","‹",v->renderChats()));
    }
    void renderTasks(){
        page=2;base("ACTIONS","Persistent Aria commitments and follow-ups");,"Persistent Aria actions");
        try{
            JSONArray a=new JSONArray(store.actions());boolean any=false;
            for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);if(o.optBoolean("done"))continue;any=true;long id=o.optLong("id");
                LinearLayout c=card();CheckBox cb=new CheckBox(this);cb.setText(o.optString("contact")+" • "+o.optString("title"));cb.setTextColor(cream);cb.setTextSize(15);c.addView(cb);c.addView(tv(o.optString("detail"),12));
                cb.setOnCheckedChangeListener((x,checked)->{if(checked){store.completeAction(id);Notifications.clear(this);renderTasks();}});body.addView(c);
            }
            if(!any)body.addView(tv("No pending actions.",16));
        }catch(Exception ignored){}
    }
    void renderSettings(){
        page=4;base("SETTINGS","Provider • permissions • behavior");
        body.addView(btn("Groq API Key","◇",v->keyDialog("Groq")));
        body.addView(btn("Gemini API Key","◇",v->keyDialog("Gemini")));
        body.addView(btn("API Test Lab","▶",v->apiLab()));
        body.addView(btn("Notification Access","●",v->{startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));}));
        body.addView(btn("Contacts Access","◎",v->{if(Build.VERSION.SDK_INT>=23)requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},CONTACTS);}));
        body.addView(btn("Hide Raw WhatsApp Notifications","◈",v->{store.bool("hide_raw",!store.bool("hide_raw",false));renderSettings();}));
        body.addView(btn("Clear API Error","×",v->{store.put("last_error","");Toast.makeText(this,"Cleared",Toast.LENGTH_SHORT).show();}));
        String err=store.get("last_error","");if(!err.isEmpty())body.addView(tv("LAST API ERROR\n"+err,13));
    }
    void keyDialog(String provider){
        EditText e=new EditText(this);e.setHint(provider+" API key");e.setTextColor(cream);e.setHintTextColor(muted);
        new AlertDialog.Builder(this).setTitle(provider+" API Key").setView(e).setPositiveButton("SAVE",(d,w)->{secure.put(provider.equals("Groq")?"groq_key":"gemini_key",e.getText().toString().trim());Toast.makeText(this,"Saved securely",Toast.LENGTH_SHORT).show();}).setNegativeButton("CANCEL",null).show();
    }
    void apiLab(){
        page=3;base("API TEST LAB","Manual provider test with context");
        Spinner sp=new Spinner(this);sp.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Groq","Gemini"}));body.addView(sp);
        EditText msg=new EditText(this);msg.setHint("Write a test message");msg.setTextColor(cream);msg.setHintTextColor(muted);msg.setMinHeight(130);msg.setGravity(Gravity.TOP);msg.setBackground(bg(surface,18));body.addView(msg);
        EditText ctx=new EditText(this);ctx.setHint("Optional context");ctx.setTextColor(cream);ctx.setHintTextColor(muted);ctx.setMinHeight(100);ctx.setGravity(Gravity.TOP);ctx.setBackground(bg(surface,18));body.addView(ctx);
        TextView result=tv("Ready",13);body.addView(result);
        body.addView(btn("SEND TEST","▶",v->{String p=sp.getSelectedItem().toString();String k=secure.get(p.equals("Groq")?"groq_key":"gemini_key");String m=p.equals("Groq")?"llama-3.1-8b-instant":"gemini-2.0-flash";AriaApi.call(p,k,m,"You are Aria. Reply concisely. Context: "+ctx.getText(),msg.getText().toString(),(ok,r,e)->{result.setText(ok?"200 OK\n"+r:"FAILED\n"+e);if(!ok)store.put("last_error",e);});}));
        body.addView(btn("BACK","‹",v->renderHome()));
    }
    void diagnostics(){
        page=3;base("DIAGNOSTICS","Independent subsystem checks");
        body.addView(tv("Notification listener: "+(AriaNotificationListener.live()?"RUNNING":"Open system access"),14));
        body.addView(tv("Auto reply: "+store.bool("auto_reply",false),14));
        body.addView(tv("Last capture: "+store.get("last_capture","None"),14));
        body.addView(tv("Last API error: "+store.get("last_error","None"),14));
        body.addView(btn("Synthetic capture","⚡",v->{store.put("last_capture","Synthetic Contact: Bhai mujhe bhej de");store.addMessage("Synthetic Contact","user","Bhai mujhe bhej de");renderHome();}));
        body.addView(btn("Direct responder","↗",v->Toast.makeText(this,"Responder uses latest WhatsApp RemoteInput",Toast.LENGTH_LONG).show()));
        body.addView(btn("BACK","‹",v->renderHome()));
    }
}
