package com.lma.kickboxing

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.lma.kickboxing.databinding.ActivityProfilesListBinding
import com.lma.kickboxing.databinding.RowProfileListItemBinding
import kotlinx.coroutines.launch

// Shows every profile linked to one contact phone number (a parent can
// register several children under the same number), with Edit/Delete per
// profile and a choice to add another or go back home.
class ProfilesListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfilesListBinding
    private var prefix: String = "+44"
    private var number: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfilesListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Your Profiles"
        prefix = intent.getStringExtra("prefix") ?: "+44"
        number = intent.getStringExtra("number") ?: ""

        binding.btnAddAnother.setOnClickListener {
            startActivity(
                Intent(this, ProfileFormActivity::class.java)
                    .putExtra("prefix", prefix)
                    .putExtra("number", number)
            )
            finish()
        }
        binding.btnBackHome.setOnClickListener {
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
                val members = Store.getMembersByContact(this@ProfilesListActivity, prefix, number)
                binding.profilesList.removeAllViews()
                binding.tvNoProfiles.visibility = if (members.isEmpty()) View.VISIBLE else View.GONE

                val inflater = LayoutInflater.from(this@ProfilesListActivity)
                members.forEach { m ->
                    val row = RowProfileListItemBinding.inflate(inflater, binding.profilesList, false)

                    if (m.selfieBase64 != null) {
                        val bytes = Base64.decode(m.selfieBase64, Base64.DEFAULT)
                        row.imgSelfie.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                    }
                    row.tvName.text = "${m.firstName} ${m.lastName}"
                    val dobDisplay = m.dob.split("-").let { if (it.size == 3) "${it[2]}/${it[1]}/${it[0]}" else m.dob }
                    row.tvMeta.text = "Born $dobDisplay"

                    row.tvGradeBadge.text = Constants.gradeLabel(m.grade)
                    val bg = row.tvGradeBadge.background.mutate() as GradientDrawable
                    bg.setColor(ContextCompat.getColor(this@ProfilesListActivity, Constants.gradeColorRes(m.grade)))

                    row.tvPills.text = listOf(
                        Constants.roleLabel(m.role),
                        Constants.classLabel(m.classCategory),
                        Constants.paymentLabel(m.paymentType),
                        if (m.gender == "female") "Female" else "Male",
                        "${m.trainingYears}y ${m.trainingMonths}m training",
                    ).joinToString("   ·   ")

                    row.btnEdit.setOnClickListener {
                        startActivity(Intent(this@ProfilesListActivity, ProfileFormActivity::class.java).putExtra("memberId", m.id))
                        finish()
                    }
                    row.btnDelete.setOnClickListener {
                        AlertDialog.Builder(this@ProfilesListActivity)
                            .setTitle("Delete profile")
                            .setMessage("Delete the profile for ${m.firstName} ${m.lastName}? This cannot be undone.")
                            .setPositiveButton("Delete") { _, _ ->
                                lifecycleScope.launch {
                                    try {
                                        Store.deleteMember(this@ProfilesListActivity, m.id)
                                        render()
                                    } catch (e: Exception) {
                                        Toast.makeText(this@ProfilesListActivity, e.message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }

                    binding.profilesList.addView(row.root)
                }
            } catch (e: Exception) {
                Toast.makeText(this@ProfilesListActivity, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
