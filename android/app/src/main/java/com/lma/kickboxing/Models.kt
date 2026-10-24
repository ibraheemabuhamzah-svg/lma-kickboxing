package com.lma.kickboxing

import org.json.JSONObject
import java.util.UUID

data class Member(
    var id: String = UUID.randomUUID().toString(),
    var role: String = "student",
    var firstName: String = "",
    var lastName: String = "",
    var dob: String = "", // yyyy-MM-dd
    var gender: String = "",
    var phonePrefix: String = "+44",
    var phoneNumber: String = "",
    var whatsapp: String = "",
    var emergencyName: String = "",
    var emergencyPhone: String = "",
    // Named differently from RevenueEntry.classKey below on purpose: this
    // holds a class-CATEGORY key (matches Constants.CLASSES), while
    // RevenueEntry.classKey holds a SCHEDULE-SLOT id (matches
    // Constants.SCHEDULE) — disjoint key-spaces that used to share the
    // identical field name "classKey" on both data classes, with nothing
    // stopping a future accidental comparison/assignment between the two.
    var classCategory: String = "",
    var paymentType: String = "payg",
    var trainingYears: Int = 0,
    var trainingMonths: Int = 0,
    var selfieBase64: String? = null,
    var grade: String = "white",
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("role", role); put("firstName", firstName); put("lastName", lastName); put("dob", dob)
        put("gender", gender); put("phonePrefix", phonePrefix); put("phoneNumber", phoneNumber)
        put("whatsapp", whatsapp); put("emergencyName", emergencyName); put("emergencyPhone", emergencyPhone)
        // Serialized key stays "classKey" for backward compatibility with
        // already-stored data on installed devices; only the Kotlin property
        // name changed.
        put("classKey", classCategory); put("paymentType", paymentType); put("trainingYears", trainingYears)
        put("trainingMonths", trainingMonths); put("selfieBase64", selfieBase64 ?: JSONObject.NULL)
        put("grade", grade); put("createdAt", createdAt); put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): Member = Member(
            id = o.optString("id"),
            role = o.optString("role", "student"),
            firstName = o.optString("firstName"),
            lastName = o.optString("lastName"),
            dob = o.optString("dob"),
            gender = o.optString("gender"),
            phonePrefix = o.optString("phonePrefix"),
            phoneNumber = o.optString("phoneNumber"),
            whatsapp = o.optString("whatsapp"),
            emergencyName = o.optString("emergencyName"),
            emergencyPhone = o.optString("emergencyPhone"),
            classCategory = o.optString("classKey"),
            paymentType = o.optString("paymentType"),
            trainingYears = o.optInt("trainingYears"),
            trainingMonths = o.optInt("trainingMonths"),
            selfieBase64 = if (o.isNull("selfieBase64")) null else o.optString("selfieBase64"),
            grade = o.optString("grade", "white"),
            createdAt = o.optLong("createdAt"),
            updatedAt = o.optLong("updatedAt"),
        )
    }
}

data class RevenueEntry(
    var id: String = UUID.randomUUID().toString(),
    var date: String = "", // yyyy-MM-dd
    var classKey: String = "",
    var amount: Double = 0.0,
    var studentCount: Int = 0,
    var note: String = "",
    var createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("date", date); put("classKey", classKey); put("amount", amount)
        put("studentCount", studentCount); put("note", note); put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): RevenueEntry = RevenueEntry(
            id = o.optString("id"),
            date = o.optString("date"),
            classKey = o.optString("classKey"),
            amount = o.optDouble("amount", 0.0),
            studentCount = o.optInt("studentCount"),
            note = o.optString("note"),
            createdAt = o.optLong("createdAt"),
        )
    }
}
