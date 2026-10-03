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

    /** source: 어디로 받았는지 ("문자", "카카오톡" 등) */
    fun add(ctx: Context, sender: String, body: String, v: Verdict, source: String = "문자"): Long {
        val id = System.currentTimeMillis()
        val risky = v.level == Level.MID || v.level == Level.HIGH
        val item = JSONObject()
            .put("id", id)
            .put("time", id)
            .put("sender", sender)
            .put("source", source)
            .put("text", SmishingEngine.mask(body).take(300))
            .put("level", v.level.name)
            .put("score", v.score)
            .put("links", v.urls.size)
            .put("reasons", JSONArray(v.reasons))
            // 위험한 메시지의 링크 주소만 남겨, 나중에 브라우저에서 열릴 때 막는다 (LinkGuard)
            .put("hosts", JSONArray(if (risky) v.urls.mapNotNull(LinkGuard::host).distinct() else emptyList()))
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

    /** 위험하다고 본 메시지에 있던 링크 주소(호스트) → 그 메시지 기록 id */
    fun riskyHosts(ctx: Context): Map<String, Long> {
        val arr = all(ctx)
        val out = HashMap<String, Long>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val hosts = o.optJSONArray("hosts") ?: continue
            for (k in 0 until hosts.length()) out.putIfAbsent(hosts.getString(k), o.getLong("id"))
        }
        return out
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY).apply()
        onChange?.invoke()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
