package com.lma.kickboxing

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.lma.kickboxing.databinding.ActivityRegistryBinding
import com.lma.kickboxing.databinding.RowRevenueBinding
import com.lma.kickboxing.databinding.RowScheduleBinding
import com.lma.kickboxing.databinding.RowStudentBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

class RegistryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegistryBinding
    private var revDateIso: String = ""
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var lastDashboardMembers: List<Member> = emptyList()
    private var lastDashboardRevenue: List<RevenueEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Registry"

        binding.btnLockRegistry.setOnClickListener {
            Store.setRegistryUnlocked(this, false)
            startActivity(Intent(this, RegistryGateActivity::class.java))
            finish()
        }

        binding.panelDashboard.btnGoToRevenue.setOnClickListener {
            binding.tabLayout.getTabAt(2)?.select()
        }

        setupTabs()
        setupDashboardPanel()
        setupStudentsPanel()
        setupRevenuePanel()
        setupTrendsPanel()
        renderSchedule()

        lifecycleScope.launch {
            try {
                renderDashboard()
                renderStudents()
                renderRevenueList()
                renderTrends()
            } catch (e: Exception) {
                Toast.makeText(this@RegistryActivity, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun onDataError(e: Exception) {
        Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
    }

    // Colors the grade spinner's own box (not the dropdown list) to match
    // the selected belt, like the badges used elsewhere in the app.
    private fun applyGradeSpinnerColor(spinner: Spinner, grade: String) {
        val bg = (ContextCompat.getDrawable(this, R.drawable.bg_badge)?.mutate() as? GradientDrawable) ?: return
        bg.setColor(ContextCompat.getColor(this, Constants.gradeColorRes(grade)))
        spinner.background = bg
        val pad = (12 * resources.displayMetrics.density).toInt()
        val padV = (6 * resources.displayMetrics.density).toInt()
        spinner.setPadding(pad, padV, pad, padV)
    }

    private inner class GradeSpinnerAdapter : ArrayAdapter<String>(this@RegistryActivity, android.R.layout.simple_spinner_dropdown_item, Constants.GRADES.map { it.label }) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent) as TextView
            view.setTextColor(if (Constants.GRADES[position].key == "yellow") Color.parseColor("#3A2E00") else Color.WHITE)
            view.setTypeface(view.typeface, android.graphics.Typeface.BOLD)
            return view
        }
    }

    // ---------- TABS ----------

    private fun setupTabs() {
        val tabs = listOf("Dashboard", "Students", "Log Revenue", "Schedule & Trends")
        tabs.forEach { binding.tabLayout.addTab(binding.tabLayout.newTab().setText(it)) }
        showPanel(0)
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { showPanel(tab.position) }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun showPanel(index: Int) {
        binding.panelDashboard.rootDashboard.visibility = if (index == 0) View.VISIBLE else View.GONE
        binding.panelStudents.rootStudents.visibility = if (index == 1) View.VISIBLE else View.GONE
        binding.panelRevenue.rootRevenue.visibility = if (index == 2) View.VISIBLE else View.GONE
        binding.panelTrends.rootTrends.visibility = if (index == 3) View.VISIBLE else View.GONE
    }

    // ---------- HELPERS ----------

    private fun ageFromDob(dob: String): Int? {
        if (dob.isEmpty()) return null
        return try {
            val birth = LocalDate.parse(dob)
            val today = LocalDate.now()
            var age = today.year - birth.year
            if (today.monthValue < birth.monthValue || (today.monthValue == birth.monthValue && today.dayOfMonth < birth.dayOfMonth)) age--
            age
        } catch (e: Exception) { null }
    }

    private fun seniorityMonths(m: Member) = m.trainingYears * 12 + m.trainingMonths

    private fun ageBand(age: Int): String = when {
        age < 12 -> "Under 12"
        age <= 17 -> "12-17"
        age <= 25 -> "18-25"
        age <= 35 -> "26-35"
        age <= 45 -> "36-45"
        else -> "46+"
    }

    private fun seniorityBand(months: Int): String = when {
        months < 6 -> "< 6 months"
        months < 12 -> "6-12 months"
        months < 24 -> "1-2 years"
        months < 60 -> "2-5 years"
        else -> "5+ years"
    }

    private fun monthKey(date: String): String = if (date.length >= 7) date.substring(0, 7) else ""

    private fun weekKey(date: String): String = try {
        val d = LocalDate.parse(date)
        val week = d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val year = d.get(IsoFields.WEEK_BASED_YEAR)
        "$year-W" + week.toString().padStart(2, '0')
    } catch (e: Exception) { date }

    private fun displayDate(iso: String): String {
        val p = iso.split("-")
        return if (p.size == 3) "${p[2]}/${p[1]}/${p[0]}" else iso
    }

    // ---------- DASHBOARD ----------

    private fun setupDashboardPanel() {
        val d = binding.panelDashboard
        d.spinnerDashboardClass.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("All classes") + Constants.CLASSES.map { it.label })
        d.spinnerDashboardClass.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                renderBreakdownCharts(lastDashboardMembers)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val tileActions = listOf(
            d.tileTotalStudents to "students",
            d.tileRevenueMonth to "revenueMonth",
            d.tileRevenueWeek to "revenueWeek",
            d.tileRevenueTotal to "revenueTotal",
            d.tileSessionsLogged to "sessions",
        )
        tileActions.forEach { (tile, action) -> tile.setOnClickListener { showStatDetail(action) } }
    }

    private suspend fun renderDashboard() {
        val d = binding.panelDashboard
        val members = Store.getMembers(this)
        val revenue = Store.getRevenueEntries(this)
        lastDashboardMembers = members
        lastDashboardRevenue = revenue

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
        val thisMonth = monthKey(todayStr)
        val thisWeek = weekKey(todayStr)
        val monthRevenue = revenue.filter { monthKey(it.date) == thisMonth }.sumOf { it.amount }
        val weekRevenue = revenue.filter { weekKey(it.date) == thisWeek }.sumOf { it.amount }
        val totalRevenue = revenue.sumOf { it.amount }

        val monthsWithRevenue = revenue.map { monthKey(it.date) }.filter { it.isNotEmpty() }.toSet()
        val weeksWithRevenue = revenue.map { weekKey(it.date) }.filter { it.isNotEmpty() }.toSet()
        val avgMonthlyRevenue = if (monthsWithRevenue.isNotEmpty()) totalRevenue / monthsWithRevenue.size else 0.0
        val avgWeeklyRevenue = if (weeksWithRevenue.isNotEmpty()) totalRevenue / weeksWithRevenue.size else 0.0

        d.tvTotalStudents.text = members.size.toString()
        // Math.round, not toInt() (which truncates toward zero), so this
        // matches the web app's toFixed(0) rounding for the same totals.
        d.tvMonthRevenue.text = Math.round(monthRevenue).toString()
        d.tvWeekRevenue.text = Math.round(weekRevenue).toString()
        d.tvTotalRevenue.text = Math.round(totalRevenue).toString()
        d.tvSessionsLogged.text = revenue.size.toString()
        d.tvAvgMonthlyRevenue.text = Math.round(avgMonthlyRevenue).toString()
        d.tvAvgWeeklyRevenue.text = Math.round(avgWeeklyRevenue).toString()
        d.statDetail.visibility = View.GONE

        val monthMap = linkedMapOf<String, Float>()
        revenue.sortedBy { it.date }.forEach { e ->
            val k = monthKey(e.date)
            if (k.isNotEmpty()) monthMap[k] = (monthMap[k] ?: 0f) + e.amount.toFloat()
        }
        d.chartMonthlyRevenue.setData(monthMap.keys.toList(), monthMap.values.toList(), "#E30613")

        val weekMap = linkedMapOf<String, Float>()
        revenue.sortedBy { it.date }.forEach { e ->
            val k = weekKey(e.date)
            if (k.isNotEmpty()) weekMap[k] = (weekMap[k] ?: 0f) + e.amount.toFloat()
        }
        d.chartWeeklyRevenue.setData(weekMap.keys.toList(), weekMap.values.toList(), "#2E9E4B")

        renderBreakdownCharts(members)
    }

    // Age/Seniority/Payment/Grade charts, optionally sliced to one class via
    // the "Breakdown for" filter. Coaches don't have a class/payment-type/
    // seniority, so they're excluded from those two charts (but still
    // counted in Age/Grade).
    private fun renderBreakdownCharts(members: List<Member>) {
        val d = binding.panelDashboard
        val classIdx = d.spinnerDashboardClass.selectedItemPosition
        val inClass = if (classIdx > 0) members.filter { it.classCategory == Constants.CLASSES[classIdx - 1].key } else members
        val nonCoach = inClass.filter { it.role != "coach" }

        val ageLabels = listOf("Under 12", "12-17", "18-25", "26-35", "36-45", "46+")
        val ageCounts = ageLabels.associateWith { 0 }.toMutableMap()
        inClass.forEach { m -> ageFromDob(m.dob)?.let { ageCounts[ageBand(it)] = (ageCounts[ageBand(it)] ?: 0) + 1 } }
        d.chartAge.setData(ageLabels, ageLabels.map { (ageCounts[it] ?: 0).toFloat() })

        val senLabels = listOf("< 6 months", "6-12 months", "1-2 years", "2-5 years", "5+ years")
        val senCounts = senLabels.associateWith { 0 }.toMutableMap()
        nonCoach.forEach { val b = seniorityBand(seniorityMonths(it)); senCounts[b] = (senCounts[b] ?: 0) + 1 }
        d.chartSeniority.setData(senLabels, senLabels.map { (senCounts[it] ?: 0).toFloat() })

        val payLabels = Constants.PAYMENT_TYPES.map { it.label }
        val payCounts = Constants.PAYMENT_TYPES.associate { it.key to 0 }.toMutableMap()
        nonCoach.forEach { payCounts[it.paymentType] = (payCounts[it.paymentType] ?: 0) + 1 }
        d.chartPayment.setData(payLabels, Constants.PAYMENT_TYPES.map { (payCounts[it.key] ?: 0).toFloat() })

        val gradeLabels = Constants.GRADES.map { it.label }
        val gradeCounts = Constants.GRADES.associate { it.key to 0 }.toMutableMap()
        inClass.forEach { gradeCounts[it.grade] = (gradeCounts[it.grade] ?: 0) + 1 }
        val gradeColors = Constants.GRADES.map { ContextCompat.getColor(this, Constants.gradeColorRes(it.key)) }
        d.chartGrade.setData(gradeLabels, Constants.GRADES.map { (gradeCounts[it.key] ?: 0).toFloat() }, gradeColors, true)
    }

    private fun entryDetailLines(entries: List<RevenueEntry>): List<String> {
        if (entries.isEmpty()) return listOf("No entries.")
        val total = entries.sumOf { it.amount }
        val out = mutableListOf("${entries.size} session(s) · total ${"%.2f".format(total)}", "")
        entries.forEach { e -> out.add("${displayDate(e.date)} — ${Constants.scheduleLabel(e.classKey)}: ${"%.2f".format(e.amount)}") }
        return out
    }

    private fun showStatDetail(action: String) {
        val d = binding.panelDashboard
        val members = lastDashboardMembers
        val revenue = lastDashboardRevenue
        var title = ""
        val lines = mutableListOf<String>()

        when (action) {
            "students" -> {
                title = "Total Students"
                val byRole = members.groupingBy { Constants.roleLabel(it.role) }.eachCount()
                val byClass = members.filter { it.role != "coach" }.groupingBy { Constants.classLabel(it.classCategory) }.eachCount()
                lines.add("By status:")
                byRole.forEach { (k, v) -> lines.add("  $k: $v") }
                lines.add("")
                lines.add("By class (students & assistants):")
                byClass.forEach { (k, v) -> lines.add("  $k: $v") }
            }
            "revenueMonth", "revenueWeek" -> {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
                val key = if (action == "revenueMonth") monthKey(todayStr) else weekKey(todayStr)
                val entries = revenue.filter { (if (action == "revenueMonth") monthKey(it.date) else weekKey(it.date)) == key }.sortedByDescending { it.date }
                title = if (action == "revenueMonth") "Revenue This Month" else "Revenue This Week"
                lines.addAll(entryDetailLines(entries))
            }
            "revenueTotal" -> {
                title = "Total Revenue"
                val byMonth = linkedMapOf<String, Double>()
                revenue.sortedBy { it.date }.forEach { e -> val k = monthKey(e.date); if (k.isNotEmpty()) byMonth[k] = (byMonth[k] ?: 0.0) + e.amount }
                val total = revenue.sumOf { it.amount }
                lines.add("${revenue.size} session(s) · total ${"%.2f".format(total)}")
                lines.add("")
                byMonth.keys.sortedDescending().forEach { m -> lines.add("$m: ${"%.2f".format(byMonth[m])}") }
            }
            "sessions" -> {
                title = "Sessions Logged"
                lines.addAll(entryDetailLines(revenue.sortedByDescending { it.date }))
            }
        }

        d.statDetail.removeAllViews()
        val density = resources.displayMetrics.density
        val titleView = TextView(this).apply {
            text = title
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@RegistryActivity, R.color.ink))
        }
        d.statDetail.addView(titleView)
        lines.forEach { line ->
            val tv = TextView(this).apply {
                text = line
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@RegistryActivity, R.color.ink))
                setPadding(0, (4 * density).toInt(), 0, 0)
            }
            d.statDetail.addView(tv)
        }
        val closeBtn = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = "Close"
            setPadding(0, (12 * density).toInt(), 0, 0)
            setOnClickListener { d.statDetail.visibility = View.GONE }
        }
        d.statDetail.addView(closeBtn)
        d.statDetail.visibility = View.VISIBLE
    }

    // ---------- STUDENTS ----------

    private fun setupStudentsPanel() {
        val p = binding.panelStudents
        p.spinnerFilterRole.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("All statuses") + Constants.ROLES.map { it.label })
        p.spinnerFilterClass.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("All classes") + Constants.CLASSES.map { it.label })
        p.spinnerFilterPayment.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("All payment types") + Constants.PAYMENT_TYPES.map { it.label })
        p.spinnerFilterGrade.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("All grades") + Constants.GRADES.map { it.label })

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                lifecycleScope.launch { try { renderStudents() } catch (e: Exception) { onDataError(e) } }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        p.spinnerFilterRole.onItemSelectedListener = listener
        p.spinnerFilterClass.onItemSelectedListener = listener
        p.spinnerFilterPayment.onItemSelectedListener = listener
        p.spinnerFilterGrade.onItemSelectedListener = listener

        // Debounced so typing a name doesn't re-fetch and re-inflate the
        // whole student list on every single keystroke.
        p.editSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                val runnable = Runnable {
                    lifecycleScope.launch { try { renderStudents() } catch (e: Exception) { onDataError(e) } }
                }
                searchRunnable = runnable
                searchHandler.postDelayed(runnable, 200)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private suspend fun renderStudents() {
        val p = binding.panelStudents
        var members = Store.getMembers(this)

        val search = p.editSearch.text.toString().trim().lowercase()
        if (search.isNotEmpty()) {
            members = members.filter {
                "${it.firstName} ${it.lastName}".lowercase().contains(search) ||
                    "${it.phonePrefix}${it.phoneNumber}".contains(search)
            }
        }
        val roleIdx = p.spinnerFilterRole.selectedItemPosition
        if (roleIdx > 0) members = members.filter { it.role == Constants.ROLES[roleIdx - 1].key }
        val classIdx = p.spinnerFilterClass.selectedItemPosition
        if (classIdx > 0) members = members.filter { it.classCategory == Constants.CLASSES[classIdx - 1].key }
        val payIdx = p.spinnerFilterPayment.selectedItemPosition
        if (payIdx > 0) members = members.filter { it.paymentType == Constants.PAYMENT_TYPES[payIdx - 1].key }
        val gradeIdx = p.spinnerFilterGrade.selectedItemPosition
        if (gradeIdx > 0) members = members.filter { it.grade == Constants.GRADES[gradeIdx - 1].key }

        p.studentsList.removeAllViews()
        p.tvNoStudents.visibility = if (members.isEmpty()) View.VISIBLE else View.GONE

        val inflater = LayoutInflater.from(this)
        members.forEach { member ->
            val row = RowStudentBinding.inflate(inflater, p.studentsList, false)
            row.tvName.text = "${member.firstName} ${member.lastName}" +
                if (member.role != "student") "  ·  ${Constants.roleLabel(member.role)}" else ""
            val age = ageFromDob(member.dob)
            val isCoach = member.role == "coach"
            val classText = if (isCoach) "-" else Constants.classLabel(member.classCategory)
            val paymentText = if (isCoach) "-" else Constants.paymentLabel(member.paymentType)
            val trainingText = if (isCoach) "-" else "${member.trainingYears}y ${member.trainingMonths}m"
            row.tvMeta.text = "${member.phonePrefix} ${member.phoneNumber} · ${age ?: "-"}y · " +
                "$classText · $paymentText · $trainingText"

            row.spinnerGrade.adapter = GradeSpinnerAdapter()
            val gradeIdx2 = Constants.GRADES.indexOfFirst { it.key == member.grade }.coerceAtLeast(0)
            row.spinnerGrade.setSelection(gradeIdx2)
            applyGradeSpinnerColor(row.spinnerGrade, member.grade)
            row.spinnerGrade.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val newGrade = Constants.GRADES[position].key
                    if (newGrade != member.grade) {
                        member.grade = newGrade
                        applyGradeSpinnerColor(row.spinnerGrade, newGrade)
                        lifecycleScope.launch {
                            try {
                                Store.upsertMember(this@RegistryActivity, member)
                                renderDashboard()
                                renderStudents()
                            } catch (e: Exception) {
                                onDataError(e)
                            }
                        }
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            row.btnView.setOnClickListener { showStudentDetail(member) }
            row.btnDelete.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Delete profile")
                    .setMessage("Delete the profile for ${member.firstName} ${member.lastName}? This cannot be undone.")
                    .setPositiveButton("Delete") { _, _ ->
                        lifecycleScope.launch {
                            try {
                                Store.deleteMember(this@RegistryActivity, member.id)
                                renderStudents()
                                renderDashboard()
                            } catch (e: Exception) {
                                onDataError(e)
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            p.studentsList.addView(row.root)
        }
    }

    private fun showStudentDetail(m: Member) {
        val age = ageFromDob(m.dob)
        val lines = mutableListOf(
            "Status: ${Constants.roleLabel(m.role)}",
            "Phone: ${m.phonePrefix} ${m.phoneNumber}",
            "WhatsApp: ${m.whatsapp.ifEmpty { "-" }}",
            "Gender: ${if (m.gender == "female") "Female" else "Male"}",
            "Date of birth: ${if (m.dob.isNotEmpty()) displayDate(m.dob) else "-"} ${if (age != null) "($age y)" else ""}",
            "Emergency contact: ${m.emergencyName.ifEmpty { "-" }} (${m.emergencyPhone.ifEmpty { "-" }})",
        )
        if (m.role != "coach") {
            lines.add("Class: ${Constants.classLabel(m.classCategory)}")
            lines.add("Payment type: ${Constants.paymentLabel(m.paymentType)}")
            lines.add("Training experience: ${m.trainingYears}y ${m.trainingMonths}m")
        }
        lines.add("Grade: ${Constants.gradeLabel(m.grade)}")
        AlertDialog.Builder(this)
            .setTitle("${m.firstName} ${m.lastName}")
            .setMessage(lines.joinToString("\n"))
            .setPositiveButton("Close", null)
            .show()
    }

    // ---------- REVENUE ----------

    // Pre-selects the class/session spinner to match whatever is scheduled
    // on the chosen day (e.g. picking a Wednesday selects Girls Only, the
    // first Wednesday session) — the coach can still change it if there's
    // more than one session that day.
    private fun autoSelectClassForDate(dateStr: String) {
        val jsDay = try {
            val ld = LocalDate.parse(dateStr)
            if (ld.dayOfWeek == java.time.DayOfWeek.SUNDAY) 0 else ld.dayOfWeek.value
        } catch (e: Exception) { return }
        val idx = Constants.SCHEDULE.indexOfFirst { it.dayIndex == jsDay }
        if (idx >= 0) binding.panelRevenue.spinnerRevClass.setSelection(idx)
    }

    private fun setupRevenuePanel() {
        val p = binding.panelRevenue
        p.spinnerRevClass.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Constants.SCHEDULE.map { "${it.day} ${it.time} — ${it.label}" })

        val cal = Calendar.getInstance()
        revDateIso = "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        p.btnRevDate.text = displayDate(revDateIso)
        autoSelectClassForDate(revDateIso)
        p.btnRevDate.setOnClickListener {
            DatePickerDialog(this, { _, y, mo, day ->
                revDateIso = "%04d-%02d-%02d".format(y, mo + 1, day)
                p.btnRevDate.text = displayDate(revDateIso)
                autoSelectClassForDate(revDateIso)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        p.btnSaveRevenue.setOnClickListener {
            val amount = p.editRevAmount.text.toString().toDoubleOrNull()
            if (amount == null) {
                Toast.makeText(this, "Please enter a valid amount.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val entry = RevenueEntry(
                date = revDateIso,
                classKey = Constants.SCHEDULE[p.spinnerRevClass.selectedItemPosition].key,
                amount = amount,
                studentCount = p.editRevCount.text.toString().toIntOrNull() ?: 0,
                note = p.editRevNote.text.toString().trim(),
            )
            p.btnSaveRevenue.isEnabled = false
            lifecycleScope.launch {
                try {
                    Store.addRevenueEntry(this@RegistryActivity, entry)
                    p.editRevAmount.text.clear()
                    p.editRevCount.text.clear()
                    p.editRevNote.text.clear()
                    val todayCal = Calendar.getInstance()
                    revDateIso = "%04d-%02d-%02d".format(todayCal.get(Calendar.YEAR), todayCal.get(Calendar.MONTH) + 1, todayCal.get(Calendar.DAY_OF_MONTH))
                    p.btnRevDate.text = displayDate(revDateIso)
                    autoSelectClassForDate(revDateIso)
                    renderRevenueList()
                    renderDashboard()
                    renderTrends()
                } catch (e: Exception) {
                    onDataError(e)
                } finally {
                    p.btnSaveRevenue.isEnabled = true
                }
            }
        }
    }

    private suspend fun renderRevenueList() {
        val p = binding.panelRevenue
        val entries = Store.getRevenueEntries(this).sortedByDescending { it.date }
        p.revenueList.removeAllViews()
        p.tvNoEntries.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE

        val inflater = LayoutInflater.from(this)
        entries.forEach { e ->
            val row = RowRevenueBinding.inflate(inflater, p.revenueList, false)
            row.tvSession.text = "${displayDate(e.date)} — ${Constants.scheduleLabel(e.classKey)}"
            row.tvDetails.text = "${e.studentCount} students · ${"%.2f".format(e.amount)}" + if (e.note.isNotEmpty()) " · ${e.note}" else ""
            row.btnDelete.setOnClickListener {
                lifecycleScope.launch {
                    try {
                        Store.deleteRevenueEntry(this@RegistryActivity, e.id)
                        renderRevenueList()
                        renderDashboard()
                        renderTrends()
                    } catch (ex: Exception) {
                        onDataError(ex)
                    }
                }
            }
            p.revenueList.addView(row.root)
        }
    }

    // ---------- SCHEDULE & TRENDS ----------

    private fun renderSchedule() {
        val p = binding.panelTrends
        p.scheduleList.removeAllViews()
        val inflater = LayoutInflater.from(this)
        Constants.SCHEDULE.forEach { s ->
            val row = RowScheduleBinding.inflate(inflater, p.scheduleList, false)
            row.tvDay.text = s.day
            row.tvTime.text = s.time
            row.tvClass.text = s.label
            p.scheduleList.addView(row.root)
        }
    }

    private fun setupTrendsPanel() {
        val p = binding.panelTrends
        p.spinnerTrendClass.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Constants.SCHEDULE.map { "${it.day} ${it.time} — ${it.label}" })
        p.spinnerTrendClass.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                lifecycleScope.launch { try { renderTrends() } catch (e: Exception) { onDataError(e) } }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private suspend fun renderTrends() {
        val p = binding.panelTrends
        val classKey = Constants.SCHEDULE[max(p.spinnerTrendClass.selectedItemPosition, 0)].key
        val entries = Store.getRevenueEntries(this).filter { it.classKey == classKey }.sortedBy { it.date }

        val weekMap = linkedMapOf<String, Pair<Float, Float>>() // students, revenue
        entries.forEach { e ->
            val wk = weekKey(e.date)
            val cur = weekMap[wk] ?: (0f to 0f)
            weekMap[wk] = (cur.first + e.studentCount) to (cur.second + e.amount.toFloat())
        }
        val weeks = weekMap.keys.sorted()
        p.chartClassStudents.setData(weeks, weeks.map { weekMap[it]!!.first }, "#1565C0")
        p.chartClassRevenue.setData(weeks, weeks.map { weekMap[it]!!.second }, "#2E9E4B")
    }
}
