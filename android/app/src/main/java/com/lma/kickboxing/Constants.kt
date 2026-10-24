package com.lma.kickboxing

data class Option(val key: String, val label: String)

object Constants {
    // SHA-256 of the registry password, so it isn't sitting in plaintext in
    // the compiled APK (still recoverable by a determined attacker who hashes
    // guesses offline, but closes the trivial "grep the decompiled strings" path).
    const val REGISTRY_PASSWORD_HASH = "cd67a26f1ae4b7e78a1f1a2c92c770fc11818052c3438ceb2ef761c0b919b9d2"

    fun sha256Hex(text: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    val GRADES = listOf(
        Option("white", "White Belt"),
        Option("red", "Red Belt"),
        Option("yellow", "Yellow Belt"),
        Option("orange", "Orange Belt"),
        Option("green", "Green Belt"),
        Option("blue", "Blue Belt"),
        Option("purple", "Purple Belt"),
        Option("brown", "Brown Belt"),
        Option("black", "Black Belt"),
    )

    val CLASSES = listOf(
        Option("girls", "Girls Only"),
        Option("kids", "Kids"),
        Option("teens_adults", "Teens & Adults"),
    )

    val PAYMENT_TYPES = listOf(
        Option("payg", "Pay as you go"),
        Option("monthly", "Monthly membership"),
        Option("transfer", "Transfer"),
        Option("free", "Free"),
    )

    val ROLES = listOf(
        Option("student", "Student"),
        Option("coach", "Coach"),
        Option("assistant", "Assistant (Volunteer)"),
    )

    data class ScheduleSlot(val key: String, val day: String, val time: String, val classKey: String, val label: String)

    val SCHEDULE = listOf(
        ScheduleSlot("mon-18-teens", "Monday", "18:00 - 19:00", "teens_adults", "Teens & Adults"),
        ScheduleSlot("wed-18-girls", "Wednesday", "18:00 - 19:00", "girls", "Girls Only"),
        ScheduleSlot("wed-1915-kids", "Wednesday", "19:15 - 20:15", "kids", "Kids"),
        ScheduleSlot("thu-18-teens", "Thursday", "18:00 - 19:00", "teens_adults", "Teens & Adults"),
        ScheduleSlot("fri-18-sparring", "Friday", "18:00 - 19:00", "teens_adults", "Teens & Adults Sparring"),
        ScheduleSlot("sat-09-girls", "Saturday", "09:00 - 10:00", "girls", "Girls Only"),
        ScheduleSlot("sat-1215-kids", "Saturday", "12:15 - 13:15", "kids", "Kids"),
        ScheduleSlot("sat-1315-teens", "Saturday", "13:15 - 14:15", "teens_adults", "Teens & Adults"),
    )

    val DIAL_CODES = listOf(
        "+44" to "United Kingdom",
        "+971" to "UAE", "+966" to "Saudi Arabia", "+974" to "Qatar", "+973" to "Bahrain",
        "+965" to "Kuwait", "+968" to "Oman", "+20" to "Egypt", "+962" to "Jordan",
        "+961" to "Lebanon", "+963" to "Syria", "+964" to "Iraq", "+970" to "Palestine",
        "+90" to "Turkey", "+98" to "Iran", "+92" to "Pakistan", "+91" to "India",
        "+880" to "Bangladesh", "+94" to "Sri Lanka", "+63" to "Philippines", "+60" to "Malaysia",
        "+65" to "Singapore", "+62" to "Indonesia", "+66" to "Thailand", "+84" to "Vietnam",
        "+86" to "China", "+852" to "Hong Kong", "+81" to "Japan", "+82" to "South Korea",
        "+1" to "USA / Canada", "+33" to "France", "+49" to "Germany",
        "+39" to "Italy", "+34" to "Spain", "+351" to "Portugal", "+31" to "Netherlands",
        "+32" to "Belgium", "+41" to "Switzerland", "+43" to "Austria", "+46" to "Sweden",
        "+47" to "Norway", "+45" to "Denmark", "+358" to "Finland", "+48" to "Poland",
        "+30" to "Greece", "+7" to "Russia", "+380" to "Ukraine", "+27" to "South Africa",
        "+234" to "Nigeria", "+254" to "Kenya", "+212" to "Morocco", "+216" to "Tunisia",
        "+213" to "Algeria", "+61" to "Australia", "+64" to "New Zealand", "+55" to "Brazil",
        "+52" to "Mexico", "+54" to "Argentina",
    )

    fun gradeLabel(key: String?) = GRADES.find { it.key == key }?.label ?: "White Belt"
    fun classLabel(key: String?) = CLASSES.find { it.key == key }?.label ?: (key ?: "-")
    fun paymentLabel(key: String?) = PAYMENT_TYPES.find { it.key == key }?.label ?: (key ?: "-")
    fun roleLabel(key: String?) = ROLES.find { it.key == key }?.label ?: "Student"
    fun scheduleLabel(key: String?) = SCHEDULE.find { it.key == key }?.let { "${it.day} ${it.time} — ${it.label}" } ?: (key ?: "-")

    fun gradeColorRes(key: String?): Int = when (key) {
        "red" -> R.color.grade_red
        "yellow" -> R.color.grade_yellow
        "orange" -> R.color.grade_orange
        "green" -> R.color.grade_green
        "blue" -> R.color.grade_blue
        "purple" -> R.color.grade_purple
        "brown" -> R.color.grade_brown
        "black" -> R.color.grade_black
        else -> R.color.grade_white
    }
}
