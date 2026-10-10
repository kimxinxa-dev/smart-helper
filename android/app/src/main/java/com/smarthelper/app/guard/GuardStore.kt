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
        val hosts = if (risky) v.urls.filterNot(OfficialSites::isOfficial).mapNotNull(LinkGuard::host).distinct() else emptyList()
        // 전에 "그래도 볼게요"로 허용한 주소라도, 그 주소가 든 위험 메시지가 또 오면 다시 막는다
        LinkGuard.revoke(hosts)
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
            .put("hosts", JSONArray(hosts))
        val old = all(ctx)
        val arr = JSONArray().put(item)
        for (i in 0 until minOf(old.length(), MAX - 1)) arr.put(old.get(i))
        val ed = prefs(ctx).edit().putString(KEY, arr.toString())
        // 위험 시간대 시작: 주의 이상 메시지를 받은 시각과 보낸 사람 (설치 차단 경고에 보여 준다)
        if (risky) ed.putLong(RISK_AT, id).putString(RISK_FROM, sender).putString(RISK_SRC, source)
        ed.apply()
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

    /** 마지막 위험 메시지 (시각, 보낸 사람, 출처). 없으면 null */
    data class LastRisk(val at: Long, val sender: String, val source: String)

    fun lastRisk(ctx: Context): LastRisk? {
        val p = prefs(ctx)
        val at = p.getLong(RISK_AT, 0L).takeIf { it > 0 } ?: return null
        return LastRisk(at, p.getString(RISK_FROM, "").orEmpty(), p.getString(RISK_SRC, "문자").orEmpty())
    }

    /** "그래도 진행"을 누른 뒤 이 시각까지 설치 경고를 다시 띄우지 않는다 */
    fun installSnoozedUntil(ctx: Context) = prefs(ctx).getLong(SNOOZE, 0L)
    fun snoozeInstall(ctx: Context, until: Long) = prefs(ctx).edit().putLong(SNOOZE, until).apply()

    /** 가족 연락처 1명 (이름, 번호) */
    fun family(ctx: Context): Pair<String, String>? {
        val p = prefs(ctx)
        val num = p.getString(FAMILY_NUM, null) ?: return null
        return p.getString(FAMILY_NAME, "가족").orEmpty() to num
    }
    fun setFamily(ctx: Context, name: String, number: String) {
        prefs(ctx).edit().putString(FAMILY_NAME, name).putString(FAMILY_NUM, number).apply()
        onChange?.invoke()
    }
    fun clearFamily(ctx: Context) {
        prefs(ctx).edit().remove(FAMILY_NAME).remove(FAMILY_NUM).apply()
        onChange?.invoke()
    }

    private const val RISK_AT = "riskAt"
    private const val RISK_FROM = "riskFrom"
    private const val RISK_SRC = "riskSource"
    private const val SNOOZE = "installSnoozedUntil"
    private const val FAMILY_NAME = "familyName"
    private const val FAMILY_NUM = "familyNumber"

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY).apply()
        SenderBook.clear(ctx)
        onChange?.invoke()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
