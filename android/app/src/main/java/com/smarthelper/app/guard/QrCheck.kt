package com.smarthelper.app.guard

/**
 * 큐싱(QR 코드 + 피싱) 검사 (순수 Kotlin, QrCheckTest).
 * QR 코드를 찍으면 바로 열지 않고, 담긴 내용이 어디로 가는지 먼저 살펴본다. 판정은 휴대폰 안에서만 한다.
 *
 * 문자 검사와 같은 공식 사이트 목록(OfficialSites)과 주소 모양 규칙(RuleDetector.linkWarnings)을 쓰고,
 * QR 에 흔한 신호(단축 주소, 흉내 내는 글자)를 더한다. 문자와 달리 내용이 주소 하나뿐이라 말투·AI 모델 검사는 하지 않는다.
 *
 * 점수: .apk +4, IP 주소 +3, 단축 주소 +2, 수상한 주소 끝자리(.xyz 등) +2, 흉내 내는 글자·@ 속임수 +2, 공식 목록에 없음 +1
 *       5점 이상 위험, 3점 이상 주의 (공식 사이트는 0점)
 */
object QrCheck {
    enum class Kind { URL, WIFI, TEXT }

    data class Result(
        val kind: Kind,
        val level: Level,
        val score: Int,
        /** 열 수 있는 주소 (URL 일 때만) */
        val url: String?,
        /** 연결되는 곳 (예: "han-bit.xyz") */
        val host: String?,
        val reasons: List<String>,
        /** 화면에 보여 줄 내용 (와이파이 비밀번호, 긴 숫자는 가림) */
        val shown: String,
    )

    /** 흔한 단축 주소 서비스. 실제 도착 주소를 숨긴다 (naver.me 는 공식 목록에 있어 점수를 주지 않는다) */
    private val SHORTENERS = setOf(
        "bit.ly", "abit.ly", "me2.do", "han.gl", "vo.la", "url.kr", "buly.kr", "c11.kr", "lrl.kr", "zrr.kr", "muz.so",
        "tinyurl.com", "t.co", "goo.gl", "is.gd", "v.gd", "ow.ly", "rb.gy", "cutt.ly", "shorturl.at", "t.ly",
        "tiny.cc", "rebrand.ly", "s.id", "qrco.de", "qr.net", "bl.ink", "short.io", "buff.ly", "lnkd.in",
    )
    private val WEB = Regex("^(https?://)?(www\\.)?[^\\s/:?#@]+\\.[^\\s/?#]{2,}(/\\S*)?$", RegexOption.IGNORE_CASE)
    private val SCHEME = Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE)

    fun inspect(raw: String): Result {
        val text = raw.trim()
        if (text.startsWith("WIFI:", ignoreCase = true)) return wifi(text)
        val url = asUrl(text) ?: return Result(Kind.TEXT, Level.LOW, 0, null, null,
            listOf("인터넷 주소가 아니라 글자만 담긴 QR 코드예요."), SmishingEngine.mask(text).take(300))
        val host = OfficialSites.host(url)
        if (OfficialSites.isOfficial(url)) {
            return Result(Kind.URL, Level.LOW, 0, url, host, listOf("공식 사이트 주소예요."), url)
        }
        val reasons = mutableListOf<String>()
        var score = 0
        fun add(points: Int, why: String) { score += points; reasons += why }
        // 문자 검사와 같은 주소 모양 규칙 (IP 주소 · .apk · 수상한 끝자리)
        for (w in RuleDetector.linkWarnings(url)) when {
            w.contains("APK") -> add(4, w)
            w.contains("IP") -> add(3, w)
            else -> add(2, w)
        }
        if (host in SHORTENERS) add(2, "단축 주소예요. 실제로 어디로 가는지 숨기고 있어요.")
        if (lookalike(url, host)) add(2, "다른 사이트를 흉내 낼 수 있는 글자가 섞인 주소예요.")
        add(1, "공식 사이트 목록에 없는 주소예요.")
        val level = when {
            score >= 5 -> Level.HIGH
            score >= 3 -> Level.MID
            else -> Level.LOW
        }
        return Result(Kind.URL, level, score, url, host, reasons, url)
    }

    /** 내용 전체가 웹 주소 하나일 때만 주소로 본다. "www.x.com" 처럼 http 가 없으면 붙인다 */
    private fun asUrl(text: String): String? {
        if (text.any { it.isWhitespace() }) return null
        if (text.startsWith("http://", true) || text.startsWith("https://", true)) return text
        if (SCHEME.containsMatchIn(text)) return null // tel:, mailto:, sms: 등은 주소로 열지 않는다
        return if (WEB.matches(text)) "http://$text" else null
    }

    /** 퓨니코드(xn--)·영문 아닌 글자로 된 주소, 또는 "진짜주소@가짜주소" 처럼 @ 앞을 보여 주는 속임수 */
    private fun lookalike(url: String, host: String): Boolean {
        val authority = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        return host.contains("xn--") || host.any { it.code > 127 } || authority.contains('@')
    }

    /** "WIFI:T:WPA;S:우리집;P:비밀번호;;" → 이름만 보여 주고 비밀번호는 보여 주지 않는다 */
    private fun wifi(text: String): Result {
        val name = Regex("(?:^|;|:)S:((?:\\\\.|[^;])*)", RegexOption.IGNORE_CASE).find(text.substringAfter(':'))
            ?.groupValues?.get(1)?.replace("\\", "").orEmpty()
        return Result(Kind.WIFI, Level.LOW, 0, null, null,
            listOf("와이파이 연결용 QR 코드예요. 인터넷 주소가 아니라 열지 않아요."),
            if (name.isNotEmpty()) "와이파이 이름: $name" else "와이파이 연결 정보")
    }
}
