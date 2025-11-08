package com.miujong.gachiga10

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowInsetsCompat

class CodeInviteActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_code_invite)

        val userRole = intent.getStringExtra("USER_ROLE")

        val copyButton = findViewById<Button>(R.id.btn_copy_clipboard)
        val doneButton = findViewById<Button>(R.id.btn_done)
        val codeTextView = findViewById<TextView>(R.id.tv_invite_code)

        copyButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            val codeText = codeTextView.text

            val clip = ClipData.newPlainText("초대 코드", codeText)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(this, "초대 코드가 복사되었습니다.", Toast.LENGTH_SHORT).show()
        }

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