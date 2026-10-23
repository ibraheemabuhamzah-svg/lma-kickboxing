package com.lma.kickboxing

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lma.kickboxing.databinding.ActivityProfileFormBinding
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Calendar

class ProfileFormActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileFormBinding
    private var existingMember: Member? = null
    private var dobIso: String = ""
    private var selfieBase64: String? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { loadAndEncodeImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Profile"

        val prefixLabels = Constants.DIAL_CODES.map { "${it.first} ${it.second}" }
        binding.spinnerPrefix.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, prefixLabels)
        binding.spinnerPayment.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Constants.PAYMENT_TYPES.map { it.label })

        binding.btnDob.setOnClickListener { showDatePicker() }
        binding.btnPickSelfie.setOnClickListener { pickImage.launch("image/*") }
        binding.btnSave.setOnClickListener { save() }

        val memberId = intent.getStringExtra("memberId")
        if (memberId != null) {
            lifecycleScope.launch {
                try {
                    existingMember = Store.getMemberById(this@ProfileFormActivity, memberId)
                    val m = existingMember
                    if (m != null) fillForm(m) else setupForNewMember()
                } catch (e: Exception) {
                    Toast.makeText(this@ProfileFormActivity, e.message, Toast.LENGTH_LONG).show()
                }
            }
        } else {
            setupForNewMember()
        }
    }

    private fun setupForNewMember() {
        val prefix = intent.getStringExtra("prefix") ?: "+971"
        val number = intent.getStringExtra("number") ?: ""
        setPrefixSelection(prefix)
        binding.editPhoneNumber.setText(number)
        updateGradeBadge("white")
    }

    private fun setPrefixSelection(prefix: String) {
        val idx = Constants.DIAL_CODES.indexOfFirst { it.first == prefix }
        if (idx >= 0) binding.spinnerPrefix.setSelection(idx)
    }

    private fun fillForm(m: Member) {
        binding.tvFormTitle.text = "Edit your profile"
        when (m.role) {
            "coach" -> binding.radioCoach.isChecked = true
            "assistant" -> binding.radioAssistant.isChecked = true
            else -> binding.radioStudent.isChecked = true
        }
        binding.editFirstName.setText(m.firstName)
        binding.editLastName.setText(m.lastName)
        dobIso = m.dob
        if (m.dob.isNotEmpty()) binding.btnDob.text = isoToDisplay(m.dob)
        if (m.gender == "female") binding.radioFemale.isChecked = true else if (m.gender == "male") binding.radioMale.isChecked = true
        setPrefixSelection(m.phonePrefix)
        binding.editPhoneNumber.setText(m.phoneNumber)
        binding.editWhatsapp.setText(m.whatsapp)
        binding.editEmergencyName.setText(m.emergencyName)
        binding.editEmergencyPhone.setText(m.emergencyPhone)
        when (m.classCategory) {
            "girls" -> binding.radioGirls.isChecked = true
            "kids" -> binding.radioKids.isChecked = true
            "teens_adults" -> binding.radioTeensAdults.isChecked = true
        }
        val payIdx = Constants.PAYMENT_TYPES.indexOfFirst { it.key == m.paymentType }
        if (payIdx >= 0) binding.spinnerPayment.setSelection(payIdx)
        binding.editYears.setText(m.trainingYears.toString())
        binding.editMonths.setText(m.trainingMonths.toString())
        updateGradeBadge(m.grade)
        selfieBase64 = m.selfieBase64
        if (selfieBase64 != null) {
            val bytes = Base64.decode(selfieBase64, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            binding.imgSelfie.setImageBitmap(bmp)
        }
    }

    private fun updateGradeBadge(grade: String) {
        binding.tvGradeBadge.text = Constants.gradeLabel(grade)
        val bg = binding.tvGradeBadge.background.mutate() as GradientDrawable
        bg.setColor(resolveColor(Constants.gradeColorRes(grade)))
    }

    private fun resolveColor(resId: Int) = androidx.core.content.ContextCompat.getColor(this, resId)

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        if (dobIso.isNotEmpty()) {
            val parts = dobIso.split("-")
            cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        } else {
            cal.add(Calendar.YEAR, -20)
        }
        DatePickerDialog(this, { _, year, month, day ->
            dobIso = "%04d-%02d-%02d".format(year, month + 1, day)
            binding.btnDob.text = isoToDisplay(dobIso)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun isoToDisplay(iso: String): String {
        val p = iso.split("-")
        return if (p.size == 3) "${p[2]}/${p[1]}/${p[0]}" else iso
    }

    private fun loadAndEncodeImage(uri: Uri) {
        val input = contentResolver.openInputStream(uri) ?: return
        val original = BitmapFactory.decodeStream(input)
        input.close()
        val maxDim = 300
        val scale = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true) else original
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        selfieBase64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
        binding.imgSelfie.setImageBitmap(scaled)
    }

    private fun save() {
        val firstName = binding.editFirstName.text.toString().trim()
        val lastName = binding.editLastName.text.toString().trim()
        val prefix = Constants.DIAL_CODES[binding.spinnerPrefix.selectedItemPosition].first
        val number = binding.editPhoneNumber.text.toString().trim()
        val gender = when (binding.radioGender.checkedRadioButtonId) {
            R.id.radioMale -> "male"
            R.id.radioFemale -> "female"
            else -> ""
        }
        val classCategory = when (binding.radioClass.checkedRadioButtonId) {
            R.id.radioGirls -> "girls"
            R.id.radioKids -> "kids"
            R.id.radioTeensAdults -> "teens_adults"
            else -> ""
        }
        val role = when (binding.radioRole.checkedRadioButtonId) {
            R.id.radioCoach -> "coach"
            R.id.radioAssistant -> "assistant"
            else -> "student"
        }

        if (firstName.isEmpty() || lastName.isEmpty() || number.isEmpty() || dobIso.isEmpty() || gender.isEmpty() || classCategory.isEmpty()) {
            Toast.makeText(this, "Please fill in all required fields.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSave.isEnabled = false
        lifecycleScope.launch {
            try {
                val duplicate = Store.getMemberByPhone(this@ProfileFormActivity, prefix, number)
                if (duplicate != null && duplicate.id != existingMember?.id) {
                    Toast.makeText(this@ProfileFormActivity, "A profile already exists with this phone number.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val member = existingMember ?: Member()
                member.role = role
                member.firstName = firstName
                member.lastName = lastName
                member.dob = dobIso
                member.gender = gender
                member.phonePrefix = prefix
                member.phoneNumber = number
                member.whatsapp = binding.editWhatsapp.text.toString().trim()
                member.emergencyName = binding.editEmergencyName.text.toString().trim()
                member.emergencyPhone = binding.editEmergencyPhone.text.toString().trim()
                member.classCategory = classCategory
                member.paymentType = Constants.PAYMENT_TYPES[binding.spinnerPayment.selectedItemPosition].key
                member.trainingYears = binding.editYears.text.toString().toIntOrNull() ?: 0
                member.trainingMonths = binding.editMonths.text.toString().toIntOrNull() ?: 0
                member.selfieBase64 = selfieBase64
                if (existingMember == null) {
                    member.grade = "white"
                } else {
                    // Re-read the grade fresh from storage right before saving, rather
                    // than trusting the in-memory snapshot captured when this form was
                    // opened — otherwise a grade change a coach made in the Registry
                    // while this member had their edit screen open would silently be
                    // overwritten back to the stale value.
                    member.grade = Store.getMemberById(this@ProfileFormActivity, member.id)?.grade ?: member.grade
                }

                Store.upsertMember(this@ProfileFormActivity, member)
                Toast.makeText(this@ProfileFormActivity, "Profile saved successfully.", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@ProfileFormActivity, ViewProfileActivity::class.java).putExtra("memberId", member.id))
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@ProfileFormActivity, e.message, Toast.LENGTH_LONG).show()
            } finally {
                binding.btnSave.isEnabled = true
            }
        }
    }
}
