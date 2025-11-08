package com.miujong.gachiga10

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.google.android.material.card.MaterialCardView

class RoleSelectActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_role_select)

        val leaderCard = findViewById<MaterialCardView>(R.id.card_leader)
        val memberCard = findViewById<MaterialCardView>(R.id.card_member)

        leaderCard.setOnClickListener {
            val intent = Intent(this, InviteActivity::class.java)
            intent.putExtra("USER_ROLE", "LEADER")
            startActivity(intent)
        }

        memberCard.setOnClickListener {
            val intent = Intent(this, JoinActivity::class.java)
            intent.putExtra("USER_ROLE", "MEMBER")
            startActivity(intent)
        }
    }
}