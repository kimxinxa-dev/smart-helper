package com.smarthelper.app.guard

import org.junit.Assert.assertEquals
import org.junit.Test

/** index.html 의 샘플 문자 5종(SHOTS)이 기대 결과(위험 4 / 낮음 1)와 같은지 확인 */
class SmishingEngineTest {
    private fun level(body: String) = SmishingEngine.check(body, savedContact = false).level

    @Test fun 택배_반송() = assertEquals(Level.HIGH, level("[Web발신]\n[한빛택배] 고객님의 택배가 주소 불명으로 반송 예정입니다. 오늘까지 주소를 확인해 주세요.\nhttp://han-bit.xyz/a8Kd2"))

    @Test fun 자녀_사칭() = assertEquals(Level.HIGH, level("엄마 나 폰이 고장나서 친구 폰으로 문자해. 지금 급해서 그런데 이 계좌로 50만원만 송금해줘. 3333-01-1234567"))

    @Test fun 검찰_사칭() = assertEquals(Level.HIGH, level("[Web발신]\n서울중앙지방검찰청입니다. 귀하의 계좌가 범죄에 연루되었습니다. 수사 협조를 위해 안전계좌로 즉시 이체하세요."))

    @Test fun 지원금_사칭() = assertEquals(Level.HIGH, level("[Web발신]\n긴급 재난지원금 대상자로 선정되셨습니다. 신청 마감 오늘까지.\nhttps://gov-support.top/apply\n본인 확인용 주민등록번호와 인증번호를 입력하세요."))

    @Test fun 정상_문자() = assertEquals(Level.LOW, level("[Web발신]\n[한마음병원] 내일 오전 10시 내과 진료 예약이 확인되었습니다. 변경은 병원으로 전화 주세요."))

    @Test fun 상품권_사칭_보내줘() = assertEquals(Level.HIGH, level("아빠 나 휴대폰 액정 깨져서 수리 맡겼어. 급해서 그런데 상품권 좀 사서 번호 보내줘"))

    @Test fun 저장된_연락처는_검사하지_않음() =
        assertEquals(Level.SKIP, SmishingEngine.check("http://evil.xyz 택배 반송", savedContact = true).level)

    // 다른 규칙에는 안 걸리고 자동 감시(수상한 주소 끝자리)에만 걸리는 경우
    @Test fun 링크_모양만으로도_주의() = assertEquals(Level.MID, level("사진 여기 있어요 kakao-photo.site/x"))

    // 브라우저 주소창은 http:// 없이 보여 준다
    @Test fun 주소창_IP주소() = assertEquals(1, RuleDetector.linkWarnings("10.0.0.1/pay").size)
    @Test fun 주소창_수상한_끝자리() = assertEquals(1, RuleDetector.linkWarnings("han-bit.xyz/a8Kd2").size)
    @Test fun 주소창_APK() = assertEquals(2, RuleDetector.linkWarnings("http://evil.top/app.apk").size)
    @Test fun 주소창_정상_사이트() = assertEquals(0, RuleDetector.linkWarnings("m.naver.com/news").size)

    @Test fun 긴_숫자_가리기() = assertEquals("계좌 ●●●●●● 로", SmishingEngine.mask("계좌 123456789012 로"))
}
