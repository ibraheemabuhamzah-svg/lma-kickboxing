package com.lma.kickboxing

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Base64
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.lma.kickboxing.databinding.ActivityViewProfileBinding

class ViewProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityViewProfileBinding
    private var memberId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Your Profile"
        memberId = intent.getStringExtra("memberId") ?: ""

        binding.btnEdit.setOnClickListener {
            startActivity(Intent(this, ProfileFormActivity::class.java).putExtra("memberId", memberId))
            finish()
        }
        binding.btnBack.setOnClickListener {
            startActivity(Intent(this, LookupActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val m = Store.getMemberById(this, memberId) ?: return
        if (m.selfieBase64 != null) {
            val bytes = Base64.decode(m.selfieBase64, Base64.DEFAULT)
            binding.imgSelfie.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        }
        binding.tvName.text = "${m.firstName} ${m.lastName}"
        val dobDisplay = m.dob.split("-").let { if (it.size == 3) "${it[2]}/${it[1]}/${it[0]}" else m.dob }
        binding.tvMeta.text = "${m.phonePrefix} ${m.phoneNumber} · Born $dobDisplay"

        binding.tvGradeBadge.text = Constants.gradeLabel(m.grade)
        val bg = binding.tvGradeBadge.background.mutate() as GradientDrawable
        bg.setColor(ContextCompat.getColor(this, Constants.gradeColorRes(m.grade)))

        binding.tvPills.text = listOf(
            Constants.roleLabel(m.role),
            Constants.classLabel(m.classKey),
            Constants.paymentLabel(m.paymentType),
            if (m.gender == "female") "Female" else "Male",
            "${m.trainingYears}y ${m.trainingMonths}m training",
        ).joinToString("   ·   ")
    }
}
