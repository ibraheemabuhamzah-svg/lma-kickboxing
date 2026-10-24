package com.lma.kickboxing

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lma.kickboxing.databinding.ActivityLookupBinding
import kotlinx.coroutines.launch

class LookupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLookupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLookupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Member Area"

        val prefixLabels = Constants.DIAL_CODES.map { "${it.first} ${it.second}" }
        binding.spinnerPrefix.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, prefixLabels)
        val defaultIdx = Constants.DIAL_CODES.indexOfFirst { it.first == "+44" }
        if (defaultIdx >= 0) binding.spinnerPrefix.setSelection(defaultIdx)

        binding.btnContinue.setOnClickListener {
            val prefix = Constants.DIAL_CODES[binding.spinnerPrefix.selectedItemPosition].first
            val number = binding.editPhoneNumber.text.toString().trim()
            if (number.isEmpty()) {
                Toast.makeText(this, "Please enter a phone number.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.btnContinue.isEnabled = false
            lifecycleScope.launch {
                try {
                    val members = Store.getMembersByContact(this@LookupActivity, prefix, number)
                    if (members.isNotEmpty()) {
                        startActivity(
                            Intent(this@LookupActivity, ProfilesListActivity::class.java)
                                .putExtra("prefix", prefix)
                                .putExtra("number", number)
                        )
                    } else {
                        startActivity(
                            Intent(this@LookupActivity, ProfileFormActivity::class.java)
                                .putExtra("prefix", prefix)
                                .putExtra("number", number)
                        )
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@LookupActivity, e.message, Toast.LENGTH_LONG).show()
                } finally {
                    binding.btnContinue.isEnabled = true
                }
            }
        }
    }
}
