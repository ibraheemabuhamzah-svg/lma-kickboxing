package com.lma.kickboxing

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lma.kickboxing.databinding.ActivityRegistryGateBinding

class RegistryGateActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegistryGateBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistryGateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Registry"

        if (Store.isRegistryUnlocked(this)) {
            openRegistry()
            return
        }

        binding.btnUnlock.setOnClickListener { tryUnlock() }
    }

    private fun tryUnlock() {
        if (binding.editPassword.text.toString() == Constants.REGISTRY_PASSWORD) {
            Store.setRegistryUnlocked(this, true)
            openRegistry()
        } else {
            binding.tvError.text = "Incorrect password."
        }
    }

    private fun openRegistry() {
        startActivity(Intent(this, RegistryActivity::class.java))
        finish()
    }
}
