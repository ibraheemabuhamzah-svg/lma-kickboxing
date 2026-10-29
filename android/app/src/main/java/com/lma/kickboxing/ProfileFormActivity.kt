package com.lma.kickboxing

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
        setupDobInput()
        binding.btnPickSelfie.setOnClickListener { pickImage.launch("image/*") }
        binding.btnSave.setOnClickListener { save() }
        binding.radioRole.setOnCheckedChangeListener { _, _ -> updateTrainingVisibility() }
        updateTrainingVisibility()

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
        val prefix = intent.getStringExtra("prefix") ?: "+44"
        val number = intent.getStringExtra("number") ?: ""
        setPrefixSelection(prefix)
        binding.editPhoneNumber.setText(number)
        updateGradeBadge("white")
    }

    private fun setPrefixSelection(prefix: String) {
        val idx = Constants.DIAL_CODES.indexOfFirst { it.first == prefix }
        if (idx >= 0) binding.spinnerPrefix.setSelection(idx)
    }

    // Coaches don't have a class, payment type or training-seniority —
    // those fields only make sense for students/assistants attending a class.
    private fun updateTrainingVisibility() {
        val isCoach = binding.radioRole.checkedRadioButtonId == R.id.radioCoach
        binding.trainingSection.visibility = if (isCoach) android.view.View.GONE else android.view.View.VISIBLE
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
        if (m.dob.isNotEmpty()) binding.editDob.setText(isoToDisplay(m.dob))
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

    // Lets members type their date of birth directly (dd/mm/yyyy) instead of
    // only through the calendar picker, which is slow to navigate decades
    // back for an adult's birth year. Auto-inserts the "/" separators as
    // digits are typed.
    private fun setupDobInput() {
        binding.editDob.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isFormatting || s == null) return
                isFormatting = true
                val digits = s.toString().filter { it.isDigit() }.take(8)
                val formatted = buildString {
                    digits.forEachIndexed { i, c ->
                        append(c)
                        if ((i == 1 || i == 3) && i != digits.length - 1) append('/')
                    }
                }
                if (formatted != s.toString()) {
                    binding.editDob.setText(formatted)
                    binding.editDob.setSelection(formatted.length)
                }
                isFormatting = false

                dobIso = if (digits.length == 8) {
                    val day = digits.substring(0, 2).toIntOrNull()
                    val month = digits.substring(2, 4).toIntOrNull()
                    val year = digits.substring(4, 8).toIntOrNull()
                    if (day != null && month != null && year != null && day in 1..31 && month in 1..12 && year in 1900..2100) {
                        "%04d-%02d-%02d".format(year, month, day)
                    } else ""
                } else ""
            }
        })
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        if (dobIso.isNotEmpty()) {
            val parts = dobIso.split("-")
            cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        } else {
            cal.add(Calendar.YEAR, -20)
        }
        val dialog = DatePickerDialog(this, { _, year, month, day ->
            dobIso = "%04d-%02d-%02d".format(year, month + 1, day)
            binding.editDob.setText(isoToDisplay(dobIso))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        // Best-effort: show day/month/year as scrollable spinners so the
        // year can be dialed in directly instead of paging through the
        // calendar grid month by month. Some theme/OEM combinations refuse
        // this switch at runtime, so it's wrapped and just falls back to
        // whatever the system shows by default.
        try {
            dialog.datePicker.calendarViewShown = false
            dialog.datePicker.spinnersShown = true
        } catch (e: Exception) { /* fall back to the system default picker */ }
        dialog.show()
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
        val role = when (binding.radioRole.checkedRadioButtonId) {
            R.id.radioCoach -> "coach"
            R.id.radioAssistant -> "assistant"
            else -> "student"
        }
        val isCoach = role == "coach"
        val classCategory = when (binding.radioClass.checkedRadioButtonId) {
            R.id.radioGirls -> "girls"
            R.id.radioKids -> "kids"
            R.id.radioTeensAdults -> "teens_adults"
            else -> ""
        }

        if (firstName.isEmpty() || lastName.isEmpty() || number.isEmpty() || dobIso.isEmpty() || gender.isEmpty() || (!isCoach && classCategory.isEmpty())) {
            Toast.makeText(this, "Please fill in all required fields.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSave.isEnabled = false
        lifecycleScope.launch {
            try {
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
                // Coaches don't have a class, payment type or training-seniority —
                // save these as empty/zero rather than whatever the hidden fields
                // still happen to hold.
                member.classCategory = if (isCoach) "" else classCategory
                member.paymentType = if (isCoach) "" else Constants.PAYMENT_TYPES[binding.spinnerPayment.selectedItemPosition].key
                member.trainingYears = if (isCoach) 0 else binding.editYears.text.toString().toIntOrNull() ?: 0
                member.trainingMonths = if (isCoach) 0 else binding.editMonths.text.toString().toIntOrNull() ?: 0
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
                // Back to the app's very first screen (Member Area / Registry
                // chooser) rather than the profiles list, clearing the whole
                // back stack so Back from there exits the app instead of
                // stepping through Lookup/ProfileForm again.
                startActivity(
                    Intent(this@ProfileFormActivity, MainActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@ProfileFormActivity, e.message, Toast.LENGTH_LONG).show()
            } finally {
                binding.btnSave.isEnabled = true
            }
        }
    }
}
