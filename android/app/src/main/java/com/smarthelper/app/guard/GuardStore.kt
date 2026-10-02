package com.smarthelper.app.guard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 검사 기록. 원문 대신 긴 숫자를 가린 내용만 휴대폰 안에 저장한다. */
object GuardStore {
    private const val PREF = "guard"
    private const val KEY = "history"
    private const val MAX = 50

    /** 앱 화면이 열려 있으면 새 기록을 바로 보여 주기 위한 알림 */
    var onChange: (() -> Unit)? = null

    fun add(ctx: Context, sender: String, body: String, v: Verdict): Long {
        val id = System.currentTimeMillis()
        val item = JSONObject()
            .put("id", id)
            .put("time", id)
            .put("sender", sender)
            .put("text", SmishingEngine.mask(body).take(300))
            .put("level", v.level.name)
            .put("score", v.score)
            .put("links", v.urls.size)
            .put("reasons", JSONArray(v.reasons))
        val old = all(ctx)
        val arr = JSONArray().put(item)
        for (i in 0 until minOf(old.length(), MAX - 1)) arr.put(old.get(i))
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
        onChange?.invoke()
        return id
    }

    fun all(ctx: Context) = JSONArray(prefs(ctx).getString(KEY, "[]"))

    fun get(ctx: Context, id: Long): JSONObject? {
        val arr = all(ctx)
        for (i in 0 until arr.length()) if (arr.getJSONObject(i).getLong("id") == id) return arr.getJSONObject(i)
        return null
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY).apply()
        onChange?.invoke()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
