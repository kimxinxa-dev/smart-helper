package com.smarthelper.app.guard

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

/**
 * 연락처에 없는 상대를 몇 번 봤는지, 마지막으로 주의 이상 메시지를 보낸 때를 휴대폰 안에만 기억한다.
 * 번호·이름은 그대로 두지 않고 알아볼 수 없게(SHA-256) 바꿔 저장한다.
 */
object SenderBook {
    private const val PREF = "senders"
    private const val MAX = 300

    private fun key(source: String, sender: String): String {
        val norm = SenderSignals.normalize(sender, source == "문자")
        val d = MessageDigest.getInstance("SHA-256").digest("$source|$norm".toByteArray())
        return d.take(12).joinToString("") { "%02x".format(it) }
    }

    fun get(ctx: Context, source: String, sender: String): SenderSignals.Seen? {
        val s = prefs(ctx).getString(key(source, sender), null) ?: return null
        val o = JSONObject(s)
        return SenderSignals.Seen(o.optInt("n"), o.optLong("warn"))
    }

    fun record(ctx: Context, source: String, sender: String, now: Long, warned: Boolean) {
        val p = prefs(ctx)
        val k = key(source, sender)
        val old = p.getString(k, null)?.let(::JSONObject)
        val o = JSONObject()
            .put("n", (old?.optInt("n") ?: 0) + 1)
            .put("last", now)
            .put("warn", if (warned) now else old?.optLong("warn") ?: 0L)
        val ed = p.edit().putString(k, o.toString())
        // 너무 많아지면 가장 오래 소식 없던 상대부터 지운다
        if (old == null && p.all.size >= MAX) {
            p.all.minByOrNull { (_, v) -> (v as? String)?.let { JSONObject(it).optLong("last") } ?: 0L }?.let { ed.remove(it.key) }
        }
        ed.apply()
    }

    fun clear(ctx: Context) = prefs(ctx).edit().clear().apply()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
