package com.smarthelper.app.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 공식 사이트 판단 + 진짜 택배사·은행 문자를 잘못 잡지 않는지 */
class OfficialSitesTest {
    @Test fun 공식_주소() {
        assertTrue(OfficialSites.isOfficial("https://www.cjlogistics.com"))
        assertTrue(OfficialSites.isOfficial("https://www.cjlogistics.com/ko/tool/parcel/tracking"))
        assertTrue(OfficialSites.isOfficial("m.coupang.com/vm/orders"))
        assertTrue(OfficialSites.isOfficial("https://www.hometax.go.kr"))
        assertTrue(OfficialSites.isOfficial("https://service.epost.go.kr/x"))
    }

    @Test fun 비슷하게_흉내_낸_주소는_공식_아님() {
        assertFalse(OfficialSites.isOfficial("http://cj-logistics.com/a"))
        assertFalse(OfficialSites.isOfficial("http://cjlogistics.com.evil.xyz/a"))
        assertFalse(OfficialSites.isOfficial("https://coupang-delivery.com/r8"))
        assertFalse(OfficialSites.isOfficial("http://mycjlogistics.com"))
        assertFalse(OfficialSites.isOfficial("http://cjlogistics.com@evil.top/x"))
        assertFalse(OfficialSites.isOfficial("http://gov.kr.evil.site"))
        assertFalse(OfficialSites.isOfficial("bit.ly/3kTz9"))
    }

    @Test fun 주소_이름_뽑기() = assertEquals("cjlogistics.com", OfficialSites.host("https://www.CJLogistics.com:443/ko?x=1"))

    private fun check(body: String) = SmishingEngine.check(body, savedContact = false)

    @Test fun 진짜_택배_배송완료_문자는_낮음() {
        val v = check("[CJ대한통운] 고객님의 상품이 오늘 배송 완료되었습니다. 배송 조회 https://www.cjlogistics.com")
        assertEquals(Level.LOW, v.level)
        assertFalse(v.reasons.any { it.startsWith("모르는 인터넷 주소") })
    }

    @Test fun 진짜_우체국_문자는_낮음() =
        assertEquals(Level.LOW, check("[우체국택배] 내일 배송 예정입니다. 조회 https://service.epost.go.kr/trace").level)

    @Test fun 흉내_낸_주소는_그대로_위험() =
        assertTrue(check("[CJ대한통운] 고객님 택배가 주소 불명으로 반송 예정입니다. 주소 확인 http://cj-logistics.xyz/a8").level >= Level.MID)

    @Test fun 공식_주소와_사기_주소가_섞이면_그대로_봄() {
        val v = check("[CJ대한통운] 배송지 확인 필요 https://www.cjlogistics.com 이 아니라 http://cj-check.top/a 에서 확인")
        assertTrue(v.level >= Level.MID)
        assertTrue(v.reasons.any { it.startsWith("모르는 인터넷 주소") })
    }

    @Test fun 공식_주소가_있어도_다른_사기_수법은_봄() =
        assertTrue(check("서울중앙지검 수사관입니다. 고객님 계좌가 범죄에 연루되었습니다 https://www.naver.com").level >= Level.MID)
}
