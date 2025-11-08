package com.miujong.gachiga10

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton

class InputTransportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_input_transport)

        val walkToggle = findViewById<MaterialButton>(R.id.toggle_walk)
        val publicToggle = findViewById<MaterialButton>(R.id.toggle_public)
        val bikeToggle = findViewById<MaterialButton>(R.id.toggle_bike)
        val carToggle = findViewById<MaterialButton>(R.id.toggle_car)

        val walkInputLayout = findViewById<LinearLayout>(R.id.ll_walk_input)

        val userRole = intent.getStringExtra("USER_ROLE")

        val doneButton = findViewById<Button>(R.id.btn_done)

        walkToggle.addOnCheckedChangeListener { button, isChecked ->
            if (isChecked) {
                walkInputLayout.visibility = View.VISIBLE
            } else {
                walkInputLayout.visibility = View.GONE
            }
        }

        publicToggle.addOnCheckedChangeListener { button, isChecked ->
            // isChecked로 선택 상태 확인
        }
        bikeToggle.addOnCheckedChangeListener { button, isChecked ->
            // isChecked로 선택 상태 확인
        }
        carToggle.addOnCheckedChangeListener { button, isChecked ->
            // isChecked로 선택 상태 확인
        }

        doneButton.setOnClickListener {
            // (나중에 여기서 선택된 교통수단 저장)
            // (예: publicToggle.isChecked, bikeToggle.isChecked 등)

            if (userRole == "LEADER") {
                val intent = Intent(this, InputEtaActivity::class.java)
                intent.putExtra("USER_ROLE", userRole)
                startActivity(intent)
            } else {
                val intent = Intent(this, CheckResultActivity::class.java)
                intent.putExtra("USER_ROLE", userRole)
                startActivity(intent)
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}