package com.miujong.gachiga10

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent
import android.widget.Button
import android.widget.NumberPicker

class InputEtaActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_input_eta)

        val userRole = intent.getStringExtra("USER_ROLE")

        val npAmPm = findViewById<NumberPicker>(R.id.np_am_pm)
        val npHour = findViewById<NumberPicker>(R.id.np_hour)
        val npMinute = findViewById<NumberPicker>(R.id.np_minute)
        val doneButton = findViewById<Button>(R.id.btn_done)

        npAmPm.minValue = 0
        npAmPm.maxValue = 1
        npAmPm.displayedValues = arrayOf("오전", "오후")

        npHour.minValue = 1
        npHour.maxValue = 12

        npMinute.minValue = 0
        npMinute.maxValue = 59
        npMinute.setFormatter { value -> String.format("%02d", value) }

        doneButton.setOnClickListener {
            // (여기서 선택된 시간  데이터 저장)
            // val amPm = npAmPm.value // 0: 오전, 1: 오후
            // val hour = npHour.value
            // val minute = npMinute.value

            val intent = Intent(this, InputDestActivity::class.java)
            intent.putExtra("USER_ROLE", userRole)
            startActivity(intent)
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}