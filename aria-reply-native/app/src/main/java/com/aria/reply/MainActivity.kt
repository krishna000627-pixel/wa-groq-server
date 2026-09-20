package com.aria.reply

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("aria", MODE_PRIVATE) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val auto = findViewById<Switch>(R.id.autoReply)
        val backend = findViewById<EditText>(R.id.backendUrl)
        val prompt = findViewById<EditText>(R.id.prompt)
        val delay = findViewById<EditText>(R.id.delay)
        auto.isChecked = prefs.getBoolean("auto", false)
        backend.setText(prefs.getString("backend", backend.text.toString()))
        prompt.setText(prefs.getString("prompt", prompt.text.toString()))
        delay.setText(prefs.getInt("delay", 2).toString())
        findViewById<Button>(R.id.enableAccess).setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
        findViewById<Button>(R.id.save).setOnClickListener {
            prefs.edit().putBoolean("auto", auto.isChecked).putString("backend", backend.text.toString().trim()).putString("prompt", prompt.text.toString()).putInt("delay", delay.text.toString().toIntOrNull()?.coerceIn(0, 60) ?: 2).apply()
        }
    }
}
