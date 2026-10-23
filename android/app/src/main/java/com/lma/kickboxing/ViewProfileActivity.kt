package com.lma.kickboxing

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.lma.kickboxing.databinding.ActivityViewProfileBinding
import kotlinx.coroutines.launch

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
        binding.btnDeleteProfile.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val m = Store.getMemberById(this@ViewProfileActivity, memberId) ?: return@launch
                    AlertDialog.Builder(this@ViewProfileActivity)
                        .setTitle("Delete profile")
                        .setMessage("Delete the profile for ${m.firstName} ${m.lastName}? This cannot be undone.")
                        .setPositiveButton("Delete") { _, _ ->
                            lifecycleScope.launch {
                                try {
                                    Store.deleteMember(this@ViewProfileActivity, memberId)
                                    startActivity(Intent(this@ViewProfileActivity, LookupActivity::class.java))
                                    finish()
                                } catch (e: Exception) {
                                    Toast.makeText(this@ViewProfileActivity, e.message, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } catch (e: Exception) {
                    Toast.makeText(this@ViewProfileActivity, e.message, Toast.LENGTH_LONG).show()
                }
            }
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
        lifecycleScope.launch {
            try {
                val m = Store.getMemberById(this@ViewProfileActivity, memberId) ?: return@launch
                if (m.selfieBase64 != null) {
                    val bytes = Base64.decode(m.selfieBase64, Base64.DEFAULT)
                    binding.imgSelfie.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                }
                binding.tvName.text = "${m.firstName} ${m.lastName}"
                val dobDisplay = m.dob.split("-").let { if (it.size == 3) "${it[2]}/${it[1]}/${it[0]}" else m.dob }
                binding.tvMeta.text = "${m.phonePrefix} ${m.phoneNumber} · Born $dobDisplay"

                binding.tvGradeBadge.text = Constants.gradeLabel(m.grade)
                val bg = binding.tvGradeBadge.background.mutate() as GradientDrawable
                bg.setColor(ContextCompat.getColor(this@ViewProfileActivity, Constants.gradeColorRes(m.grade)))

                binding.tvPills.text = listOf(
                    Constants.roleLabel(m.role),
                    Constants.classLabel(m.classCategory),
                    Constants.paymentLabel(m.paymentType),
                    if (m.gender == "female") "Female" else "Male",
                    "${m.trainingYears}y ${m.trainingMonths}m training",
                ).joinToString("   ·   ")
            } catch (e: Exception) {
                Toast.makeText(this@ViewProfileActivity, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
