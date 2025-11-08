package com.miujong.gachiga10

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import android.widget.RadioGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat

class LeaderChoiceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leader_choice)

        val radioGroup = findViewById<RadioGroup>(R.id.rg_leader_choice)
        val doneButton = findViewById<Button>(R.id.btn_done)
        val subtitle = findViewById<TextView>(R.id.tv_subtitle)

        val userRole = intent.getStringExtra("USER_ROLE")

        if (userRole != "LEADER") {
            radioGroup.isEnabled = false
            for (i in 0 until radioGroup.childCount) {
                radioGroup.getChildAt(i).isEnabled = false
            }
            doneButton.isEnabled = false

            subtitle.text = "리더가 선택 중입니다..."
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}