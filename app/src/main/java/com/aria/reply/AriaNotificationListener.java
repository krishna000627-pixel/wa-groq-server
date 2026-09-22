package com.aria.reply;

import android.app.*;
import android.content.*;
import android.os.*;
import android.service.notification.*;
import android.text.TextUtils;
import android.app.RemoteInput;
import android.graphics.drawable.Icon;
import java.util.*;
import org.json.JSONObject;

public class AriaNotificationListener extends NotificationListenerService {
    public static final String WA="com.whatsapp";
    private static final long DEFAULT_DELAY=1800;
    private static AriaNotificationListener instance;
    private AriaStore store;
    private final Map<String,ArrayList<String>> burst=new HashMap<>();
    private final Map<String,Long> timers=new HashMap<>();
    private final Map<String,String> lastKeys=new HashMap<>();
    private Handler h;
    public static boolean live(){return instance!=null;}

    @Override public void onCreate(){super.onCreate();instance=this;store=new AriaStore(this);h=new Handler(Looper.getMainLooper());}
    @Override public void onDestroy(){instance=null;super.onDestroy();}

    @Override public void onNotificationPosted(StatusBarNotification sbn){
        if(!WA.equals(sbn.getPackageName())) return;
        Bundle e=sbn.getNotification().extras;
        String sender=e.getString(Notification.EXTRA_TITLE,"").trim();
        String text=e.getCharSequence(Notification.EXTRA_TEXT)==null?"":e.getCharSequence(Notification.EXTRA_TEXT).toString().trim();
        if(sender.isEmpty()||text.isEmpty())return;
        if(sender.equalsIgnoreCase("You")||sender.equalsIgnoreCase("Aria"))return;
        if(isSummary(text))return;

        String key=sbn.getKey()+"|"+text;
        String old=lastKeys.get(sender);
        if(key.equals(old))return;
        lastKeys.put(sender,key);

        String contact=Contacts.resolve(this,sender);
        store.addMessage(contact,"user",text);
        ArrayList<String> q=burst.computeIfAbsent(contact,k->new ArrayList<>());
        q.add(text);

        if(timers.containsKey(contact)) h.removeCallbacksAndMessages(contact);
        Runnable job=()->generate(contact);
        h.postAtTime(job,contact,SystemClock.uptimeMillis()+DEFAULT_DELAY);
        timers.put(contact,System.currentTimeMillis()+DEFAULT_DELAY);

        if(store.bool("hide_raw",false)){ try{cancelNotification(sbn.getKey());}catch(Exception ignored){} }
    }

    private boolean isSummary(String s){
        String x=s.toLowerCase(Locale.US);
        return x.matches(".*\\b\\d+\\s+(new\\s+)?messages?\\b.*") ||
               x.contains("notifications from") || x.equals("checking for new messages");
    }

    private void generate(String contact){
        ArrayList<String> q=burst.remove(contact); timers.remove(contact);
        if(q==null||q.isEmpty())return;
        String provider=store.get("provider","Groq");
        String key=new SecurePrefs(this).get(provider.equals("Gemini")?"gemini_key":"groq_key");
        String model=store.get(provider.equals("Gemini")?"gemini_model":"groq_model",
            provider.equals("Gemini")?"gemini-2.0-flash":"llama-3.1-8b-instant");
        String context=store.context(contact,16);
        String user=String.join("\n",q);
        String system="You are Aria, a concise WhatsApp assistant. Contact: "+contact+
            ". Use context below. Never claim to have sent something unless you actually did.\nCONTEXT:\n"+context+
            "\nIf the user asks for something ambiguous, ask a clarification question. "+
            "If you promise a future action, phrase the promise clearly.";
        AriaApi.call(provider,key,model,system,user,(ok,reply,error)->{
            if(!ok){store.put("last_error",error);return;}
            store.addMessage(contact,"assistant",reply);
            extractAction(contact,reply);
            if(store.bool("auto_reply",false)) sendToLatest(contact,reply);
        });
    }

    private void extractAction(String contact,String reply){
        String x=reply.toLowerCase(Locale.US);
        if(x.contains("i'll")||x.contains("i will")||x.contains("i’ll")||
           x.contains("i'll inform")||x.contains("bata dunga")||x.contains("inform kar dunga")||
           x.contains("bhej dunga")||x.contains("remind")){
            store.addAction(contact,"Aria follow-up",reply);
            Notifications.task(this,store);
        }
    }

    private void sendToLatest(String contact,String reply){
        // Find the latest WhatsApp notification with a RemoteInput action.
        try{
            for(StatusBarNotification s:getActiveNotifications()){
                if(!WA.equals(s.getPackageName()))continue;
                Bundle e=s.getNotification().extras;
                String t=e.getString(Notification.EXTRA_TITLE,"");
                if(!Contacts.resolve(this,t).equals(contact))continue;
                for(Notification.Action a:s.getNotification().actions){
                    if(a.getRemoteInputs()!=null&&a.getRemoteInputs().length>0){
                        Intent i=new Intent(); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        RemoteInput.addResultsToIntent(a.getRemoteInputs(),i,new Bundle(){{
                            putCharSequence(a.getRemoteInputs()[0].getResultKey(),reply);
                        }});
                        a.actionIntent.send(this,0,i);
                        return;
                    }
                }
            }
        }catch(Exception e){store.put("last_error","Direct responder: "+e.getMessage());}
    }
}
