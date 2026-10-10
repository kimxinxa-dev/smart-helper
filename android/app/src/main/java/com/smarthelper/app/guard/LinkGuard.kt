package com.smarthelper.app.guard

import android.content.Context
import android.net.Uri

/**
 * 위험 링크 판단. 브라우저 주소창에 보이는 주소가
 *  ① 사기로 의심된 문자·메시지에 있던 주소이거나 ② 주소 모양 자체가 수상하면(IP 주소, .apk, .xyz 등) 막는다.
 * 주소는 저장하지 않고 그 자리에서만 본다.
 */
object LinkGuard {
    /**
     * 사용자가 "그래도 볼게요"를 고른 주소 → 허용이 끝나는 시각.
     * 계속 허용하면 같은 사기꾼이 나중에 같은 주소를 또 보내도 막지 못하므로 잠깐만(ALLOW_MS) 허용하고,
     * 같은 주소가 든 위험 메시지가 새로 오면 바로 취소한다 (revoke).
     */
    private val allowed = HashMap<String, Long>()
    const val ALLOW_MS = 5 * 60 * 1000L

    class Danger(val host: String, val reason: String, val messageId: Long?)

    /** "http://han-bit.xyz/a8Kd2", "han-bit.xyz/a8Kd2" → "han-bit.xyz" */
    fun host(url: String): String? {
        val u = url.trim().let { if (it.contains("://")) it else "http://$it" }
        return try {
            Uri.parse(u).host?.lowercase()?.removePrefix("www.")?.takeIf { it.contains('.') }
        } catch (e: Exception) {
            null
        }
    }

    fun check(ctx: Context, url: String): Danger? {
        val h = host(url) ?: return null
        allowed[h]?.let { until -> if (System.currentTimeMillis() < until) return null else allowed.remove(h) }
        // 공식 사이트(진짜 택배사·은행 등)는 의심 문자에 들어 있었더라도 막지 않는다
        if (OfficialSites.isOfficial(url)) return null
        GuardStore.riskyHosts(ctx)[h]?.let { return Danger(h, "사기로 의심된 문자·메시지나 QR 코드에 있던 주소예요.", it) }
        RuleDetector.linkWarnings(url).firstOrNull()?.let { return Danger(h, it, null) }
        return null
    }

    fun allow(host: String) {
        allowed[host] = System.currentTimeMillis() + ALLOW_MS
    }

    /** 같은 주소가 든 위험 메시지가 새로 오면 "그래도 볼게요" 허용을 취소한다 */
    fun revoke(hosts: Collection<String>) {
        hosts.forEach { allowed.remove(it) }
    }
}
