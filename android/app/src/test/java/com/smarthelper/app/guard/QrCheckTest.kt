package com.smarthelper.app.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 큐싱(QR 코드) 검사: 공식 사이트는 안전, 단축·IP·수상한 끝자리는 주의, .apk 등이 겹치면 위험 */
class QrCheckTest {
    private fun level(raw: String) = QrCheck.inspect(raw).level

    @Test fun 공식_사이트는_안전() {
        val r = QrCheck.inspect("https://www.cjlogistics.com/ko/tool/parcel/tracking")
        assertEquals(Level.LOW, r.level)
        assertEquals(0, r.score)
        assertEquals("cjlogistics.com", r.host)
        assertEquals(Level.LOW, level("https://www.gov.kr"))
        assertEquals(Level.LOW, level("https://naver.me/xYz12")) // 네이버 단축 주소는 공식 목록에 있다
    }

    @Test fun 위험_신호_없는_일반_사이트는_안전_1점() {
        val r = QrCheck.inspect("https://www.cafe-menu.com/table/7")
        assertEquals(Level.LOW, r.level)
        assertEquals(1, r.score)
        assertEquals("cafe-menu.com", r.host)
    }

    @Test fun 단축_주소는_주의() {
        assertEquals(Level.MID, level("https://bit.ly/3kTz9"))
        assertEquals(Level.MID, level("me2.do/abc")) // http 가 없어도 주소로 본다
    }

    @Test fun IP_주소와_수상한_끝자리는_주의() {
        assertEquals(Level.MID, level("http://211.45.12.9/login"))
        assertEquals(Level.MID, level("http://parking-pay.xyz/p"))
    }

    @Test fun apk_는_위험() {
        assertEquals(Level.HIGH, level("http://safe-guard.site/v3.apk"))
        assertEquals(Level.HIGH, level("https://download-app.com/update.apk"))
    }

    @Test fun 흉내_내는_주소는_위험_신호() {
        // "진짜주소@가짜주소" — 브라우저는 @ 뒤로 간다
        val r = QrCheck.inspect("http://cjlogistics.com@evil.top/x")
        assertEquals(Level.HIGH, r.level)
        assertTrue(r.reasons.any { it.contains("흉내") })
        assertEquals(Level.MID, level("http://xn--80ak6aa92e.com/login"))
    }

    @Test fun 와이파이_QR은_열지_않고_비밀번호를_보여_주지_않음() {
        val r = QrCheck.inspect("WIFI:T:WPA;S:우리카페;P:secret1234;;")
        assertEquals(QrCheck.Kind.WIFI, r.kind)
        assertNull(r.url)
        assertEquals("와이파이 이름: 우리카페", r.shown)
        assertFalse(r.shown.contains("secret"))
    }

    @Test fun 주소가_아닌_글자는_열지_않음() {
        val r = QrCheck.inspect("테이블 7번 주문 번호 123456789")
        assertEquals(QrCheck.Kind.TEXT, r.kind)
        assertNull(r.url)
        assertFalse(r.shown.contains("123456789")) // 긴 숫자는 가린다
        assertNull(QrCheck.inspect("tel:01012345678").url)
        assertNull(QrCheck.inspect("http://a.com 그리고 http://b.com").url) // 주소 하나만 담긴 경우만 연다
    }
}
