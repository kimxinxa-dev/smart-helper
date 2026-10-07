package com.smarthelper.app.guard

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * 연락처에 없는 번호의 문자는 링크가 없어도 AI 모델로 검사한다.
 * (예전에는 링크가 있거나 규칙에 걸린 문자만 모델로 봐서, 규칙을 피해 간 링크 없는 지인 사칭을 놓쳤다)
 */
class LinklessModelTest {
    private val model = TextModel.parse(File("src/main/assets/${ModelDetector.ASSET}").readText())

    /** 모델을 부른 횟수를 세는 검사기 */
    private class CountingHeavy : Detector {
        var calls = 0
        override val heavy = true
        override fun inspect(body: String): Finding { calls++; return Finding(0, emptyList()) }
    }

    @Before fun setUp() { SmishingEngine.detectors.retainAll { it === RuleDetector } }
    @After fun tearDown() { SmishingEngine.detectors.retainAll { it === RuleDetector } }

    // 규칙에는 걸리지 않는(점수 0) 링크 없는 지인 사칭 문자들
    private val acquaintance = listOf(
        "엄마 나 민수야 번호 바꿨어 이 번호로 저장해 둬",
        "엄마 나야 이 번호 내 새 번호니까 저장해 줘 바쁘면 문자로만 해",
        "아빠 나 딸이야 번호 바뀌었어 지금 회의 중이라 전화는 못 받아",
        "아버지 저예요 번호 바꿨어요 지금 통화 어려우니 문자로 해 주세요",
    )

    @Test fun 예시는_규칙만으로는_잡히지_않음() = acquaintance.forEach {
        assertEquals("규칙 점수: $it", 0, RuleDetector.inspect(it).score)
        assertTrue("링크 없음: $it", RuleDetector.urls(it).isEmpty())
    }

    @Test fun 링크도_규칙도_없어도_모델을_부름() {
        val spy = CountingHeavy()
        SmishingEngine.detectors += spy
        SmishingEngine.check(acquaintance[0], savedContact = false)
        assertEquals(1, spy.calls)
    }

    @Test fun 저장된_번호는_모델도_부르지_않음() {
        val spy = CountingHeavy()
        SmishingEngine.detectors += spy
        assertEquals(Level.SKIP, SmishingEngine.check(acquaintance[0], savedContact = true).level)
        assertEquals(0, spy.calls)
    }

    @Test fun 실제_모델로_링크없는_지인사칭을_주의이상으로() {
        SmishingEngine.detectors += ModelDetector(model)
        acquaintance.forEach {
            val v = SmishingEngine.check(it, savedContact = false)
            assertTrue("${v.level} (사기 가능성 ${(model.predict(it) * 100).toInt()}%): $it", v.level == Level.MID || v.level == Level.HIGH)
            assertTrue("AI 이유가 있어야 함: ${v.reasons}", v.reasons.any { r -> r.startsWith("AI가") })
        }
    }

    @Test fun 실제_모델로도_정상_가족_문자는_낮음() {
        SmishingEngine.detectors += ModelDetector(model)
        assertEquals(Level.LOW, SmishingEngine.check("엄마 오늘 저녁 늦을 것 같아요 먼저 드세요", savedContact = false).level)
    }

    @Test fun 실제_모델로도_정상_문자는_낮음() {
        SmishingEngine.detectors += ModelDetector(model)
        assertEquals(Level.LOW, SmishingEngine.check("[한마음병원] 내일 오전 10시 내과 진료 예약이 확인되었습니다.", savedContact = false).level)
    }
}
