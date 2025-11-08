package com.miujong.gachiga10

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText

class CodeJoinActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_code_join)

        val userRole = intent.getStringExtra("USER_ROLE")

        val doneButton = findViewById<Button>(R.id.btn_done)
        val codeInput = findViewById<EditText>(R.id.et_code_input)

        codeInput.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(codeInput, InputMethodManager.SHOW_IMPLICIT)

        doneButton.setOnClickListener {
            val intent = Intent(this, InputStartActivity::class.java)
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