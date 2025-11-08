package com.miujong.gachiga10

import android.os.Bundle
import android.content.Intent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView

class InviteActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_invite)

        val userRole = intent.getStringExtra("USER_ROLE")

        val codeCard = findViewById<MaterialCardView>(R.id.card_code)
        val linkCard = findViewById<MaterialCardView>(R.id.card_link)

        codeCard.setOnClickListener {
            val intent = Intent(this, CodeInviteActivity::class.java)
            intent.putExtra("USER_ROLE", userRole)
            startActivity(intent)
        }

        // 3. '링크' 버튼 클릭 이벤트
        linkCard.setOnClickListener {
            val intent = Intent(this, LinkInviteActivity::class.java)
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