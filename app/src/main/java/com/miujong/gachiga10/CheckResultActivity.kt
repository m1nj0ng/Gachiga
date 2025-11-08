package com.miujong.gachiga10

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.google.android.material.card.MaterialCardView

class CheckResultActivity : AppCompatActivity() {
    private lateinit var mapViewPlaceholder: View
    private lateinit var infoCard: MaterialCardView
    private lateinit var infoTitle: TextView
    private lateinit var infoTime: TextView
    private lateinit var infoDistance: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_check_result)

        val userRole = intent.getStringExtra("USER_ROLE")
        val doneButton = findViewById<Button>(R.id.btn_done)

        mapViewPlaceholder = findViewById(R.id.map_view_placeholder)
        infoCard = findViewById(R.id.card_route_info)
        infoTitle = findViewById(R.id.tv_info_title)
        infoTime = findViewById(R.id.tv_info_time)
        infoDistance = findViewById(R.id.tv_info_distance)

        doneButton.setOnClickListener {
            val intent = Intent(this, VoteResultActivity::class.java)
            intent.putExtra("USER_ROLE", userRole)
            startActivity(intent)
        }

        //임시 정보
        infoTitle.text = "최적 합류 지점: 강남역"
        infoTime.text = "총 소요시간: 30분"
        infoDistance.text = "총 거리: 5.0km"

        infoCard.visibility = View.VISIBLE

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}