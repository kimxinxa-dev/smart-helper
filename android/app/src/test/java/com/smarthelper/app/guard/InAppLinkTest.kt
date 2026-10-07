package com.smarthelper.app.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 카카오톡 말풍선·웹 화면 제목 줄에서 링크 찾기 + 찾은 링크의 주소 모양 판단 */
class InAppLinkTest {
    private fun links(vararg t: String) = InAppLink.links(t.toList())

    @Test fun 말풍선_속_링크() = assertEquals(listOf("http://cj-logis.xyz/a8K"), links("고객님 택배 주소 확인 http://cj-logis.xyz/a8K 부탁드려요"))
    @Test fun 한글이_바로_붙은_주소() = assertEquals(listOf("cj-logis.xyz"), links("cj-logis.xyz에서 확인하세요"))
    @Test fun 짧은_주소() = assertEquals(listOf("bit.ly/3kTz9"), links("확인 bit.ly/3kTz9"))
    @Test fun IP_주소() = assertEquals(listOf("183.92.11.40/post"), links("183.92.11.40/post 접속"))
    @Test fun 문장부호_떼기() = assertEquals(listOf("https://m.naver.com/news"), links("(https://m.naver.com/news)."))
    @Test fun 미리보기_카드_도메인() = assertEquals(listOf("kakao-safe.site"), links("계정 보호 조치 안내", "kakao-safe.site"))
    @Test fun 링크_없는_글() = assertTrue(links("오늘 저녁 7시에 만나요", "엄마 사진 보내 줘").isEmpty())
    @Test fun 여러_링크() = assertEquals(2, links("m.naver.com 말고 evil.top/x 로 와").size)

    // 찾은 링크는 브라우저와 같은 기준(LinkGuard → RuleDetector.linkWarnings)으로 판단한다
    @Test fun 찾은_링크_판단() {
        val found = links("앱 받기 http://kakao-help.kim/app.apk", "공지 m.naver.com/notice")
        assertEquals(2, found.size)
        assertTrue(RuleDetector.linkWarnings(found[0]).isNotEmpty())
        assertTrue(RuleDetector.linkWarnings(found[1]).isEmpty())
    }
}
