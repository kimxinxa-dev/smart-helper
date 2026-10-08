package com.smarthelper.app.guard

/**
 * 택배사·은행·카드사·관공서 등 공식 사이트 (순수 Kotlin, OfficialSitesTest).
 * 진짜 "[CJ대한통운] 배송 완료 … https://www.cjlogistics.com" 문자를 '모르는 링크'로 보고 경고하거나,
 * 그 주소를 위험 링크 차단 목록에 넣지 않기 위해 쓴다.
 * 주소 끝이 정확히 같아야 한다 (cjlogistics.com.evil.xyz, cj-logistics.com 은 공식이 아님).
 */
object OfficialSites {
    private val DOMAINS = setOf(
        // 택배·우편·쇼핑
        "cjlogistics.com", "epost.go.kr", "hanjin.com", "lotteglogis.com", "ilogen.com", "kdexp.com",
        "coupang.com", "gmarket.co.kr", "11st.co.kr", "ssg.com", "auction.co.kr",
        // 포털·메신저
        "naver.com", "naver.me", "kakao.com", "kakaocorp.com", "daum.net", "google.com", "youtube.com",
        // 은행·카드·간편결제
        "kbstar.com", "shinhan.com", "wooribank.com", "hanabank.com", "nonghyup.com", "ibk.co.kr",
        "kakaobank.com", "toss.im", "kbcard.com", "shinhancard.com", "samsungcard.com", "hyundaicard.com",
        "lottecard.co.kr", "wooricard.com", "hanacard.co.kr", "bccard.com", "nhcard.co.kr",
        // 공공기관 (.go.kr 은 정부기관만 쓸 수 있는 주소)
        "go.kr", "nhis.or.kr", "nps.or.kr", "kisa.or.kr", "fss.or.kr", "korail.com",
        // 통신사
        "tworld.co.kr", "kt.com", "lguplus.com",
    )

    /** "https://www.cjlogistics.com/ko/x" → "cjlogistics.com" 꼴의 주소 이름 (www. 제거, 소문자) */
    fun host(url: String): String {
        val s = url.trim().substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfter('@').substringBefore(':').lowercase()
        return s.removePrefix("www.").trimEnd('.')
    }

    fun isOfficial(url: String): Boolean {
        val h = host(url)
        if (h.isEmpty()) return false
        return DOMAINS.any { d -> h == d || h.endsWith(".$d") }
    }
}
