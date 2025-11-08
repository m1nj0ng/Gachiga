package com.miujong.gachiga10

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.CheckBox

class VoteResultActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vote_result)

        val userRole = intent.getStringExtra("USER_ROLE")
        val doneButton = findViewById<Button>(R.id.btn_done)

        val option1 = findViewById<CheckBox>(R.id.cb_option1)
        val option2 = findViewById<CheckBox>(R.id.cb_option2)
        val option3 = findViewById<CheckBox>(R.id.cb_option3)

        // 어떤게 투표되었는지 추가, 선택된 값 확인

        doneButton.setOnClickListener {
            val intent = Intent(this, LeaderChoiceActivity::class.java)
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