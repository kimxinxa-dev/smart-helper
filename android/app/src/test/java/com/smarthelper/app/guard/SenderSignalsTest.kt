package com.smarthelper.app.guard

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** 문자 내용 밖의 단서: 처음 온 번호, 해외·070 번호, 대화 흐름 */
class SenderSignalsTest {
    private val now = 1_800_000_000_000L
    private val hour = 60 * 60 * 1000L
    private val model = TextModel.parse(File("src/main/assets/${ModelDetector.ASSET}").readText())

    @Before fun setUp() {
        SmishingEngine.detectors.retainAll { it === RuleDetector }
        SmishingEngine.detectors += ModelDetector(model)
    }
    @After fun tearDown() { SmishingEngine.detectors.retainAll { it === RuleDetector } }

    /** 앱(GuardAlert)과 같은 순서: 내용 검사 → 내용 밖 단서 더하기 */
    private fun judge(body: String, sender: String = "01055556666", seen: SenderSignals.Seen? = null, sms: Boolean = true): Verdict {
        val v = SmishingEngine.check(body, savedContact = false)
        return SmishingEngine.withExtra(v, SenderSignals.inspect(sender, sms, body, seen, now, v.urls.isNotEmpty()))
    }

    private val knownQuiet = SenderSignals.Seen(count = 5, lastWarnAt = 0L)

    // ── 처음 온 번호 + 가족 말투
    // 고정 시험(개발용)에서 내용만으로는 놓쳤던 두 문자 (AI 50% 안팎)
    @Test fun 처음_번호_가족말투_손자() =
        assertTrue(judge("할머니 저 손자 준서예요 새로 개통한 번호라 저장 부탁드려요 통화는 나중에 할게요").level >= Level.MID)

    @Test fun 처음_번호_가족말투_사위() =
        assertTrue(judge("아버님 저 큰사위입니다 번호 변경됐습니다 확인차 문자 남겨 주십시오").level >= Level.MID)

    @Test fun 처음_번호_가족말투_이유() =
        assertTrue(judge("엄마 나야 답장 좀").reasons.any { it.startsWith("처음 문자를 보낸 번호인데 가족처럼") })

    @Test fun 전에_보던_번호면_처음_단서_없음() =
        assertFalse(judge("엄마 나야 답장 좀", seen = knownQuiet).reasons.any { it.startsWith("처음 문자를") })

    @Test fun 처음_번호라도_가족말투가_아니면_그대로() =
        assertEquals(Level.LOW, judge("[연세내과] 내일 오전 9시 30분 예약입니다", sender = "0215881234").level)

    @Test fun 가족처럼_보이지만_아닌_말() {
        val f = { b: String -> SenderSignals.inspect("01011112222", true, b, null, now, false).flag }
        assertFalse(f("엄마손칼국수 오늘 휴무입니다"))
        assertFalse(f("나 딸기 샀어 가져갈게"))
        assertFalse(f("형광등 교체 작업 안내"))
        assertTrue(f("저 막내딸이에요 번호 바뀌었어요"))
        assertTrue(f("[Web발신] 엄마 나 폰 바꿨어"))
    }

    @Test fun 메신저는_처음_단서를_보지_않음() =
        assertFalse(SenderSignals.inspect("김민수", false, "할머니 저 손자예요", null, now, false).flag)

    // ── 해외·070 번호 (혼자서는 경고하지 않음)
    @Test fun 해외_번호는_점수만() {
        val f = SenderSignals.inspect("+12025550123", true, "안녕하세요 확인 부탁드립니다", knownQuiet, now, false)
        assertEquals(1, f.score); assertFalse(f.flag)
    }
    @Test fun 국제발신_표시() = assertEquals(1, SenderSignals.inspect("0100000", true, "[국제발신] 안녕하세요", knownQuiet, now, false).score)
    @Test fun 한국_번호는_해외_아님() = assertEquals(0, SenderSignals.inspect("+821012345678", true, "안녕하세요", knownQuiet, now, false).score)
    @Test fun 인터넷전화_070() = assertEquals(1, SenderSignals.inspect("070-1234-5678", true, "안녕하세요", knownQuiet, now, false).score)
    @Test fun 번호_모양_맞추기() = assertEquals("01012345678", SenderSignals.normalize("+82 10-1234-5678", true))

    // ── 대화 흐름: 의심 문자 → 몇 시간 뒤 요구
    private val warned = SenderSignals.Seen(count = 1, lastWarnAt = now - 2 * hour)

    @Test fun 의심_문자_뒤_상품권_부탁은_위험() =
        assertEquals(Level.HIGH, judge("할머니 혹시 편의점 가실 수 있어요? 부탁 하나만 할게요 상품권이요", seen = warned).level)

    @Test fun 의심_문자_뒤_인증번호_부탁은_위험() =
        assertEquals(Level.HIGH, judge("지금 할머니 폰으로 숫자 온 거 있죠 그 인증번호만 보내 주세요", seen = warned).level)

    @Test fun 의심_문자_뒤_평범한_말도_주의_유지() =
        assertEquals(Level.MID, judge("네 감사해요 이따 다시 연락드릴게요", seen = warned).level)

    @Test fun 하루가_지나면_이어서_보지_않음() =
        assertEquals(Level.LOW, judge("네 감사해요 이따 다시 연락드릴게요", seen = SenderSignals.Seen(1, now - 25 * hour)).level)

    @Test fun 메신저도_대화_흐름은_봄() =
        assertEquals(Level.HIGH, judge("상품권 번호 사진 찍어서 보내 주세요", sender = "준서", seen = warned, sms = false).level)

    // ── 합치기 규칙
    @Test fun 저장된_연락처는_그대로_통과() {
        val v = SmishingEngine.check("엄마 나야", savedContact = true)
        assertEquals(Level.SKIP, SmishingEngine.withExtra(v, Finding(6, listOf("x"), true)).level)
    }
    @Test fun 단서가_없으면_결과_그대로() {
        val v = SmishingEngine.check("내일 시청 앞에서 봐요", savedContact = false)
        assertEquals(v, SmishingEngine.withExtra(v, Finding(0, emptyList())))
    }
}
