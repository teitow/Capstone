package com.ktm.capstone

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class ObjectModeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_object_mode)

        val basicModeButton: Button = findViewById(R.id.basicModeButton)
        val detailedModeButton: Button = findViewById(R.id.detailedModeButton)

        basicModeButton.setOnClickListener {
            val intent = Intent(this, ObjectRecognitionActivity::class.java)
            intent.putExtra("MODE", "BASIC")
            startActivity(intent)
        }

        detailedModeButton.setOnClickListener {
            val intent = Intent(this, ObjectRecognitionActivity::class.java)
            intent.putExtra("MODE", "DETAILED")
            startActivity(intent)
        }
    }
}
