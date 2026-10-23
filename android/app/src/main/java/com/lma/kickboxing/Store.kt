package com.lma.kickboxing

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Shared cloud database (Supabase) so the web app and the Android app see
// the same data. The anon key is meant to be public (same trust model as
// the Registry's own client-side password gate) but it does mean anyone
// who extracts it can read/write the tables directly, bypassing the
// Registry password — see supabase/schema.sql for the policies in place.
object Store {
    private const val SUPABASE_URL = "https://oaxeeljelnpobzxzfsrh.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im9heGVlbGplbG5wb2J6eHpmc3JoIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTI3MDY2NzIsImV4cCI6MjEwODI4MjY3Mn0.SWq5rPQNuPmWWhDkHNKwNLCRgvZcYwP3cumNRVl_CLs"

    private suspend fun request(
        path: String,
        method: String = "GET",
        body: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val conn = URL("$SUPABASE_URL/rest/v1/$path").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            conn.setRequestProperty("Content-Type", "application/json")
            extraHeaders.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw Exception("Database error ($code): $text")
            }
            text
        } catch (e: Exception) {
            if (e.message?.startsWith("Database error") == true) throw e
            throw Exception("Network error — check your internet connection.")
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // ---------- MEMBERS ----------

    private fun memberToJson(m: Member): JSONObject = JSONObject().apply {
        put("id", m.id)
        put("role", m.role)
        put("first_name", m.firstName)
        put("last_name", m.lastName)
        put("dob", m.dob)
        put("gender", m.gender)
        put("phone_prefix", m.phonePrefix)
        put("phone_number", m.phoneNumber)
        put("whatsapp", m.whatsapp)
        put("emergency_name", m.emergencyName)
        put("emergency_phone", m.emergencyPhone)
        put("class_category", m.classCategory)
        put("payment_type", m.paymentType)
        put("training_years", m.trainingYears)
        put("training_months", m.trainingMonths)
        put("selfie_base64", m.selfieBase64 ?: JSONObject.NULL)
        put("grade", m.grade)
        put("created_at", m.createdAt)
        put("updated_at", m.updatedAt)
    }

    private fun jsonToMember(o: JSONObject): Member = Member(
        id = o.optString("id"),
        role = o.optString("role", "student"),
        firstName = o.optString("first_name"),
        lastName = o.optString("last_name"),
        dob = o.optString("dob"),
        gender = o.optString("gender"),
        phonePrefix = o.optString("phone_prefix"),
        phoneNumber = o.optString("phone_number"),
        whatsapp = o.optString("whatsapp"),
        emergencyName = o.optString("emergency_name"),
        emergencyPhone = o.optString("emergency_phone"),
        classCategory = o.optString("class_category"),
        paymentType = o.optString("payment_type"),
        trainingYears = o.optInt("training_years"),
        trainingMonths = o.optInt("training_months"),
        selfieBase64 = if (o.isNull("selfie_base64")) null else o.optString("selfie_base64"),
        grade = o.optString("grade", "white"),
        createdAt = o.optLong("created_at"),
        updatedAt = o.optLong("updated_at"),
    )

    suspend fun getMembers(ctx: Context): List<Member> {
        val text = request("members?select=*&order=created_at.asc")
        val arr = JSONArray(text)
        return (0 until arr.length()).map { jsonToMember(arr.getJSONObject(it)) }
    }

    // Strips spacing/dashes and a leading zero so "0501234567",
    // "050-123-4567" and "501234567" are recognized as the same number.
    fun normalizePhone(number: String): String =
        number.replace(Regex("[\\s-]"), "").trimStart('0')

    suspend fun getMemberByPhone(ctx: Context, prefix: String, number: String): Member? {
        val target = normalizePhone(number)
        return getMembers(ctx).find { it.phonePrefix == prefix && normalizePhone(it.phoneNumber) == target }
    }

    suspend fun getMemberById(ctx: Context, id: String): Member? {
        val text = request("members?id=eq.${enc(id)}&select=*")
        val arr = JSONArray(text)
        return if (arr.length() > 0) jsonToMember(arr.getJSONObject(0)) else null
    }

    suspend fun upsertMember(ctx: Context, member: Member): Member {
        member.updatedAt = System.currentTimeMillis()
        val body = JSONArray().put(memberToJson(member)).toString()
        val text = request(
            "members",
            method = "POST",
            body = body,
            extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=representation"),
        )
        val arr = JSONArray(text)
        return if (arr.length() > 0) jsonToMember(arr.getJSONObject(0)) else member
    }

    suspend fun deleteMember(ctx: Context, id: String) {
        request("members?id=eq.${enc(id)}", method = "DELETE")
    }

    // ---------- REVENUE ----------

    private fun entryToJson(e: RevenueEntry): JSONObject = JSONObject().apply {
        put("id", e.id)
        put("date", e.date)
        put("class_key", e.classKey)
        put("amount", e.amount)
        put("student_count", e.studentCount)
        put("note", e.note)
        put("created_at", e.createdAt)
    }

    private fun jsonToEntry(o: JSONObject): RevenueEntry = RevenueEntry(
        id = o.optString("id"),
        date = o.optString("date"),
        classKey = o.optString("class_key"),
        amount = o.optDouble("amount", 0.0),
        studentCount = o.optInt("student_count"),
        note = o.optString("note"),
        createdAt = o.optLong("created_at"),
    )

    suspend fun getRevenueEntries(ctx: Context): List<RevenueEntry> {
        val text = request("revenue_entries?select=*&order=date.asc")
        val arr = JSONArray(text)
        return (0 until arr.length()).map { jsonToEntry(arr.getJSONObject(it)) }
    }

    suspend fun addRevenueEntry(ctx: Context, entry: RevenueEntry) {
        val body = JSONArray().put(entryToJson(entry)).toString()
        request(
            "revenue_entries",
            method = "POST",
            body = body,
            extraHeaders = mapOf("Prefer" to "return=representation"),
        )
    }

    suspend fun deleteRevenueEntry(ctx: Context, id: String) {
        request("revenue_entries?id=eq.${enc(id)}", method = "DELETE")
    }

    // In-memory only (not SharedPreferences) so it resets whenever the app
    // process dies, mirroring the web app's sessionStorage-scoped gate
    // instead of staying unlocked forever on the device. A coach can also
    // lock it explicitly via the Registry's "Lock" button.
    private var registryUnlocked = false

    fun isRegistryUnlocked(ctx: Context): Boolean = registryUnlocked

    fun setRegistryUnlocked(ctx: Context, unlocked: Boolean) {
        registryUnlocked = unlocked
    }
}
