package com.lma.kickboxing

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.lma.kickboxing.databinding.ActivityLookupBinding

class LookupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLookupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLookupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Member Area"

        val prefixLabels = Constants.DIAL_CODES.map { "${it.first} ${it.second}" }
        binding.spinnerPrefix.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, prefixLabels)
        val defaultIdx = Constants.DIAL_CODES.indexOfFirst { it.first == "+971" }
        if (defaultIdx >= 0) binding.spinnerPrefix.setSelection(defaultIdx)

        binding.btnContinue.setOnClickListener {
            val prefix = Constants.DIAL_CODES[binding.spinnerPrefix.selectedItemPosition].first
            val number = binding.editPhoneNumber.text.toString().trim()
            if (number.isEmpty()) {
                Toast.makeText(this, "Please enter a phone number.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val member = Store.getMemberByPhone(this, prefix, number)
            if (member != null) {
                startActivity(Intent(this, ViewProfileActivity::class.java).putExtra("memberId", member.id))
            } else {
                startActivity(
                    Intent(this, ProfileFormActivity::class.java)
                        .putExtra("prefix", prefix)
                        .putExtra("number", number)
                )
            }
        }
    }
}
