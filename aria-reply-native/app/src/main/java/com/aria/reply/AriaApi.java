package com.aria.reply;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AriaApi {
    public interface Callback { void done(boolean ok,String result,String error); }
    private static void post(Callback cb, boolean ok,String r,String e){ new Handler(Looper.getMainLooper()).post(()->cb.done(ok,r,e)); }

    public static void call(String provider,String key,String model,String system,String user,Callback cb){
        new Thread(()->{
            if(key==null||key.trim().isEmpty()){post(cb,false,"","API key missing");return;}
            try{
                String endpoint = provider.equals("Gemini")
                    ? "https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent?key="+key
                    : "https://api.groq.com/openai/v1/chat/completions";
                HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();
                c.setRequestMethod("POST"); c.setConnectTimeout(15000); c.setReadTimeout(30000);
                c.setRequestProperty("Content-Type","application/json"); c.setDoOutput(true);
                JSONObject body=new JSONObject();
                if(provider.equals("Gemini")){
                    body.put("contents",new JSONArray().put(new JSONObject().put("role","user").put("parts",new JSONArray()
                        .put(new JSONObject().put("text",system+"\n\n"+user)))));
                    body.put("generationConfig",new JSONObject().put("temperature",0.7));
                }else{
                    body.put("model",model);
                    body.put("messages",new JSONArray()
                        .put(new JSONObject().put("role","system").put("content",system))
                        .put(new JSONObject().put("role","user").put("content",user)));
                    body.put("temperature",0.7);
                }
                try(OutputStream os=c.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}
                int code=c.getResponseCode();
                InputStream is=code>=200&&code<300?c.getInputStream():c.getErrorStream();
                String out=new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8)).lines()
                    .reduce("",(a,b)->a+b);
                if(code<200||code>=300){post(cb,false,"","HTTP "+code+": "+out);return;}
                JSONObject j=new JSONObject(out);
                String answer;
                if(provider.equals("Gemini"))
                    answer=j.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
                else answer=j.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
                post(cb,true,answer,"");
            }catch(Exception e){post(cb,false,"",e.getClass().getSimpleName()+": "+e.getMessage());}
        }).start();
    }
}
