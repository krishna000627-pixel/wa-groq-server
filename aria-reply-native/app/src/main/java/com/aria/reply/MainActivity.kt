package com.aria.reply

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat

class MainActivity : Activity() {
    private lateinit var page: FrameLayout
    private lateinit var store: AriaStore
    private var screen = 0
    private val bg = Color.rgb(244,239,230)
    private val ink = Color.rgb(39,49,45)
    private val muted = Color.rgb(102,115,109)
    private val mint = Color.rgb(207,239,225)
    private val lavender = Color.rgb(222,216,245)
    private val peach = Color.rgb(246,213,195)
    private val sky = Color.rgb(207,229,246)
    private val surface = Color.rgb(255,249,240)
    private val green = Color.rgb(47,143,104)
    private val red = Color.rgb(184,92,92)
    private val yellow = Color.rgb(167,123,46)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AriaStore(this)
        setContentView(R.layout.activity_main)
        page = findViewById(R.id.page)
        findViewById<View>(R.id.navHome).setOnClickListener { screen=0; render() }
        findViewById<View>(R.id.navTest).setOnClickListener { screen=1; render() }
        findViewById<View>(R.id.navSettings).setOnClickListener { screen=2; render() }
        findViewById<View>(R.id.navAbout).setOnClickListener { screen=3; render() }
        render()
    }
    override fun onResume(){ super.onResume(); if(::page.isInitialized) render() }
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
    private fun tv(s:String,size:Float=15f,color:Int=ink)=TextView(this).apply{ text=s;textSize=size;setTextColor(color);includeFontPadding=true }
    private fun shell(title:String, subtitle:String):LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(18),dp(18),dp(22));setBackgroundColor(bg);addView(tv("ARIA REPLY",12f,green).apply{typeface=Typeface.DEFAULT_BOLD});addView(tv(title,30f,ink).apply{typeface=Typeface.DEFAULT_BOLD;setPadding(0,dp(4),0,0)});addView(tv(subtitle,14f,muted).apply{setPadding(0,dp(2),0,dp(12))})}
    private fun panel(color:Int, radius:Int=24):GradientDrawable=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius.coerceIn(10,20)).toFloat()}
    private fun clay(title:String,value:String,color:Int,icon:Int?=null,action:(()->Unit)?=null):LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(17),dp(15),dp(17),dp(15));background=panel(color);layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(6),0,dp(6))};addView(LinearLayout(this@MainActivity).apply{gravity=Gravity.CENTER_VERTICAL;if(icon!=null)addView(ImageView(this@MainActivity).apply{setImageResource(icon);layoutParams=LinearLayout.LayoutParams(dp(28),dp(28)).apply{setMargins(0,0,dp(10),0)}});addView(tv(title,12f,muted).apply{typeface=Typeface.DEFAULT_BOLD},LinearLayout.LayoutParams(0,-2,1f))});addView(tv(value,16f,ink).apply{setPadding(0,dp(8),0,0)});if(action!=null){setOnClickListener{action()}}}
    private fun button(label:String, icon:Int, color:Int, action:()->Unit):LinearLayout=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(16),dp(10),dp(16),dp(10));background=panel(color,14);layoutParams=LinearLayout.LayoutParams(-1,dp(54)).apply{setMargins(0,dp(6),0,dp(6))};addView(ImageView(this@MainActivity).apply{setImageResource(icon);layoutParams=LinearLayout.LayoutParams(dp(24),dp(24)).apply{setMargins(0,0,dp(12),0)}});addView(tv(label,15f,ink));setOnClickListener{action()}}
    private fun input(label:String,value:String,password:Boolean=false,multiline:Boolean=false)=EditText(this).apply{hint=label;setText(value);setTextColor(ink);setHintTextColor(muted);textSize=15f;setPadding(dp(14),dp(8),dp(14),dp(8));background=panel(surface,16);isSingleLine=!multiline;if(multiline)minLines=4;if(password)inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD;layoutParams=LinearLayout.LayoutParams(-1,if(multiline)dp(120) else dp(54)).apply{setMargins(0,dp(5),0,dp(8))}}
    private fun show(l:LinearLayout){page.removeAllViews();page.addView(ScrollView(this).apply{isFillViewport=true;addView(l)})}
    private fun status(ok:Boolean)=if(ok)"READY" else "ACTION REQUIRED"

    private fun render(){when(screen){0->home();1->test();2->settings();3->about()}}
    private fun home(){
        val l=shell("Aria Control Center","A calm automation cockpit for notification → AI → reply.")
        l.addView(clay("AUTO REPLY",if(store.autoReply)"Enabled • background engine armed" else "Disabled • configure it in Settings",if(store.autoReply) mint else peach,R.drawable.ic_bolt){screen=2;render()})
        l.addView(clay("AI ENGINE", "${store.provider.uppercase()} • ${if(store.provider.equals("GEMINI",true)) store.geminiModel else store.model} • ${status(store.hasApiKey())}",lavender,R.drawable.ic_key){screen=2;render()})
        l.addView(clay("NOTIFICATION READER",if(hasNotificationAccess())"Connected • listening for message notifications" else "Not connected • grant Notification Access",sky,R.drawable.ic_bell){openNotificationAccess()})
        l.addView(tv("LIVE PIPELINE",12f,muted).apply{typeface=Typeface.DEFAULT_BOLD;setPadding(0,dp(18),0,dp(4))})
        val pipe=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(10),dp(14),dp(10))}
        val steps=listOf("01  Capture notification" to (hasNotificationAccess()),"02  Detect RemoteInput" to (AriaNotificationListener.lastReplyTarget!=null),"03  Generate with ${store.provider}" to store.hasApiKey(),"04  Send reply" to (store.lastReply!="No reply sent yet."))
        steps.forEach{(name,ok)->pipe.addView(tv("${if(ok)"●" else "○"}  $name",14f,if(ok) green else muted).apply{setPadding(dp(6),dp(7),0,dp(7))})};pipe.background=panel(surface,24);pipe.l.addView(pipe)
        l.addView(clay("LAST CAPTURE",store.lastCapture,surface,R.drawable.ic_bell))
        l.addView(clay("LAST REPLY",store.lastReply,mint,R.drawable.ic_play))
        if(store.lastError.isNotBlank())l.addView(clay("LATEST ERROR",store.lastError,peach,R.drawable.ic_info){screen=1;render()})
        l.addView(tv("QUICK ACTIONS",12f,muted).apply{typeface=Typeface.DEFAULT_BOLD;setPadding(0,dp(18),0,dp(4))})
        l.addView(button("RUN REAL CAPTURE + REPLY TEST",R.drawable.ic_play,lavender){runSyntheticTest()})
        l.addView(button("OPEN DIAGNOSTICS",R.drawable.ic_test,sky){screen=1;render()})
        show(l)
    }

    private fun test(){
        val l=shell("Diagnostics","Test the actual notification reader and RemoteInput path.")
        l.addView(clay("NOTIFICATION ACCESS",if(hasNotificationAccess())"Connected" else "Required before capture",if(hasNotificationAccess()) mint else peach,R.drawable.ic_bell){openNotificationAccess()})
        l.addView(button("POST ARIA SYNTHETIC MESSAGE",R.drawable.ic_bell,peach){runSyntheticTest()})
        l.addView(clay("CAPTURED",store.lastCapture,surface,R.drawable.ic_test))
        l.addView(clay("REMOTEINPUT TARGET",if(AriaNotificationListener.lastReplyTarget!=null)"Detected • ${AriaNotificationListener.lastReplyTargetDescription}" else "Waiting for a notification with a reply action",sky,R.drawable.ic_play))
        l.addView(clay("LAST REPLY",store.lastReply,mint,R.drawable.ic_play))
        if(store.lastError.isNotBlank())l.addView(clay("ERROR",store.lastError,peach,R.drawable.ic_info))
        l.addView(button("TEST API ONLY",R.drawable.ic_key,lavender){apiOnlyTest()})
        show(l)
    }

    private fun settings(){
        val l=shell("Settings","Configure automation and choose the AI provider.")
        val sw=Switch(this).apply{text="AUTO REPLY";setTextColor(ink);textSize=16f;isChecked=store.autoReply}
        l.addView(clay("AUTOMATION",if(store.autoReply)"Enabled" else "Disabled",if(store.autoReply) mint else peach,R.drawable.ic_bolt));l.addView(sw)
        l.addView(tv("AI PROVIDER",12f,muted).apply{typeface=Typeface.DEFAULT_BOLD;setPadding(0,dp(16),0,dp(3))})
        val provider=Spinner(this);provider.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,arrayOf("GROQ","GEMINI"));provider.setSelection(if(store.provider.equals("GEMINI",true))1 else 0);l.addView(provider)
        l.addView(tv("GROQ",12f,muted).apply{typeface=Typeface.DEFAULT_BOLD;setPadding(0,dp(14),0,0)})
        val endpoint=input("Groq endpoint",store.endpoint);val model=input("Groq model",store.model);val groqKey=input("Groq API key • encrypted",store.apiKey(),true);l.addView(endpoint);l.addView(model);l.addView(groqKey)
        l.addView(tv("GEMINI",12f,muted).apply{typeface=Typeface.DEFAULT_BOLD;setPadding(0,dp(14),0,0)})
        val gemModel=input("Gemini model",store.geminiModel);val gemKey=input("Gemini API key • encrypted",store.geminiApiKey(),true);l.addView(gemModel);l.addView(gemKey)
        l.addView(tv("RESPONSE",12f,muted).apply{typeface=Typeface.DEFAULT_BOLD;setPadding(0,dp(14),0,0)})
        val prompt=input("System prompt",store.systemPrompt,false,true);val marker=input("Response marker",store.marker);val min=input("Minimum delay",store.minDelay.toString());min.inputType=2;val max=input("Maximum delay",store.maxDelay.toString());max.inputType=2;l.addView(prompt);l.addView(marker);l.addView(min);l.addView(max)
        l.addView(button("SAVE ALL CONFIGURATION",R.drawable.ic_key,mint){store.autoReply=sw.isChecked;store.provider=provider.selectedItem.toString();store.endpoint=endpoint.text.toString().trim();store.model=model.text.toString().trim();store.setApiKey(groqKey.text.toString());store.geminiModel=gemModel.text.toString().trim();store.setGeminiApiKey(gemKey.text.toString());store.systemPrompt=prompt.text.toString().trim();store.marker=marker.text.toString();store.minDelay=min.text.toString().toIntOrNull()?:2;store.maxDelay=max.text.toString().toIntOrNull()?:5;store.logEvent("Configuration saved",true);toast("Configuration saved");render()})
        l.addView(button("TEST ACTIVE AI",R.drawable.ic_play,lavender){apiOnlyTest()})
        l.addView(button("NOTIFICATION ACCESS",R.drawable.ic_bell,sky){openNotificationAccess()})
        l.addView(button("BATTERY OPTIMIZATION",R.drawable.ic_bolt,peach){requestBatteryExemption()})
        show(l)
    }

    private fun about(){val l=shell("About V23","Clay Core • dual-provider AI • real capture diagnostics");l.addView(clay("DESIGN", "Pastel clay surfaces, vector icons, large touch targets and soft elevation.",mint,R.drawable.ic_info));l.addView(clay("AI", "Groq and Gemini are supported independently. Keys are encrypted with Android Keystore.",lavender,R.drawable.ic_key));l.addView(clay("CAPTURE TEST", "Aria posts a synthetic notification containing a real RemoteInput action; the listener captures it and the generated reply is returned to Aria's test receiver.",sky,R.drawable.ic_test));l.addView(clay("ANDROID", "Android 8+; notification direct reply depends on the installed messaging app exposing a RemoteInput action.",peach,R.drawable.ic_info));show(l)}

    private fun runSyntheticTest(){
        if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestNotificationPermission();toast("Allow notifications, then run the test again");return}
        if(!hasNotificationAccess()){openNotificationAccess();toast("Grant Notification Access, then run the test again");return}
        if(!store.hasApiKey()){screen=2;render();toast("Configure the active AI key first");return}
        store.lastError="";store.logEvent("Synthetic capture test posted",null);AriaTestNotification.post(this,"Aria Test Contact","Hello Aria, this is a synthetic capture test.") ;toast("Synthetic message posted")
    }
    private fun apiOnlyTest(){if(!store.hasApiKey()){toast("Configure active AI key first");return};Thread{val r=AriaApi.generate(store,"Aria Test","Reply with a short test confirmation.");runOnUiThread{if(r.ok){store.lastReply=r.reply;store.lastError="";store.logEvent("${store.provider} API test passed",true);toast("${store.provider} API passed")}else{store.lastError="${r.error} (HTTP ${r.code})";store.logEvent("${store.provider} API test failed",false);toast("API test failed")};render()}}.start()}
    private fun hasNotificationAccess():Boolean=(Settings.Secure.getString(contentResolver,"enabled_notification_listeners") ?: "").contains(packageName)
    private fun requestNotificationPermission(){if(android.os.Build.VERSION.SDK_INT>=33)ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.POST_NOTIFICATIONS),1001)}
    private fun openNotificationAccess()=startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
    private fun batteryIgnored():Boolean=(getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
    private fun requestBatteryExemption(){runCatching{startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:$packageName")))}.onFailure{startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))}}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
}
