package com.lma.kickboxing

import android.content.Context
import org.json.JSONArray

object Store {
    private const val PREFS = "lma_prefs"
    private const val KEY_MEMBERS = "lma_members"
    private const val KEY_REVENUE = "lma_revenue"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getMembers(ctx: Context): MutableList<Member> {
        val raw = prefs(ctx).getString(KEY_MEMBERS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<Member>()
        for (i in 0 until arr.length()) list.add(Member.fromJson(arr.getJSONObject(i)))
        return list
    }

    private fun saveMembers(ctx: Context, members: List<Member>) {
        val arr = JSONArray()
        members.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString(KEY_MEMBERS, arr.toString()).apply()
    }

    fun getMemberByPhone(ctx: Context, prefix: String, number: String): Member? =
        getMembers(ctx).find { it.phonePrefix == prefix && it.phoneNumber == number }

    fun getMemberById(ctx: Context, id: String): Member? =
        getMembers(ctx).find { it.id == id }

    fun upsertMember(ctx: Context, member: Member) {
        val members = getMembers(ctx)
        val idx = members.indexOfFirst { it.id == member.id }
        member.updatedAt = System.currentTimeMillis()
        if (idx >= 0) members[idx] = member else members.add(member)
        saveMembers(ctx, members)
    }

    fun getRevenueEntries(ctx: Context): MutableList<RevenueEntry> {
        val raw = prefs(ctx).getString(KEY_REVENUE, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<RevenueEntry>()
        for (i in 0 until arr.length()) list.add(RevenueEntry.fromJson(arr.getJSONObject(i)))
        return list
    }

    private fun saveRevenueEntries(ctx: Context, entries: List<RevenueEntry>) {
        val arr = JSONArray()
        entries.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString(KEY_REVENUE, arr.toString()).apply()
    }

    fun addRevenueEntry(ctx: Context, entry: RevenueEntry) {
        val entries = getRevenueEntries(ctx)
        entries.add(entry)
        saveRevenueEntries(ctx, entries)
    }

    fun deleteRevenueEntry(ctx: Context, id: String) {
        saveRevenueEntries(ctx, getRevenueEntries(ctx).filter { it.id != id })
    }

    const val GATE_KEY = "registry_unlocked"

    fun isRegistryUnlocked(ctx: Context): Boolean =
        prefs(ctx).getBoolean(GATE_KEY, false)

    fun setRegistryUnlocked(ctx: Context, unlocked: Boolean) {
        prefs(ctx).edit().putBoolean(GATE_KEY, unlocked).apply()
    }
}
