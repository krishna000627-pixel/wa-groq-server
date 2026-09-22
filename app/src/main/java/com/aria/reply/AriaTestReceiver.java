package com.aria.reply;
import android.content.*;
import android.app.*;
import android.os.Bundle;
public class AriaTestReceiver extends BroadcastReceiver{
 public static final String ACTION="com.aria.reply.SYNTHETIC";
 @Override public void onReceive(Context c,Intent i){
   AriaStore s=new AriaStore(c);
   s.addMessage("Synthetic Contact","user","Bhai mujhe bhej de");
   s.put("last_capture","Synthetic Contact: Bhai mujhe bhej de");
 }
}
