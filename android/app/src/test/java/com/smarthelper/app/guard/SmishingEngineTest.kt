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

    private fun reasons(body: String) = SmishingEngine.check(body, savedContact = false).reasons
    private val MONEY = "돈을 보내라고 요구해요."

    // 정상 은행 알림에는 '돈을 보내라' 이유가 붙지 않는다
    @Test fun 입금_알림은_돈요구_아님() = assertEquals(false, MONEY in reasons("[국민은행] 입금 50,000원 잔액 1,250,000원 김영희"))
    @Test fun 계좌_확인은_돈요구_아님() = assertEquals(false, MONEY in reasons("[신한은행] 고객님 계좌 개설이 완료되었습니다. 계좌 확인은 앱에서 하세요."))
    @Test fun 송금해줘는_돈요구() = assertEquals(true, MONEY in reasons("엄마 이 계좌로 50만원만 송금해줘"))
    @Test fun 안전계좌는_돈요구() = assertEquals(true, MONEY in reasons("수사 협조를 위해 안전계좌로 즉시 옮기세요"))
    @Test fun 입금_바랍니다는_돈요구() = assertEquals(true, MONEY in reasons("배송비 2,500원 미납. 아래 계좌로 입금 바랍니다"))

    // 진짜 인증번호 문자·기관 예방 안내·평범한 가족 문자는 규칙에 걸리지 않는다
    private val CODE = "비밀번호나 인증번호를 알려 달라고 해요. 절대 알려 주면 안 돼요."
    private val AGENCY = "공공기관을 사칭하고 있어요. 진짜 기관은 문자로 돈을 요구하지 않아요."
    private val FAMILY = "가족을 사칭하는 전형적인 수법이에요. 꼭 전화로 직접 확인하세요."
    @Test fun 진짜_인증번호_문자() = assertEquals(Level.LOW, level("[카카오] 인증번호 [482910] 입력해 주세요. 타인에게 절대 알려주지 마세요"))
    @Test fun 인증번호_알려줘는_요구() = assertEquals(true, CODE in reasons("아빠 지금 오는 인증번호 좀 알려줘"))
    @Test fun 인증번호_회신은_요구() = assertEquals(true, CODE in reasons("인증번호 6자리 회신 바랍니다"))
    @Test fun 주민센터는_요구_아님() = assertEquals(false, CODE in reasons("[주민센터] 신청하신 등본 발급이 완료되었습니다"))
    @Test fun 기관_예방_안내() = assertEquals(false, AGENCY in reasons("[경찰청] 검찰 경찰 금감원은 절대로 전화로 돈을 요구하지 않습니다"))
    @Test fun 기관_사칭() = assertEquals(true, AGENCY in reasons("경찰청 사이버수사대입니다. 고객님 계좌가 범죄에 이용되었습니다"))
    @Test fun 가족_폰_이야기는_사칭_아님() = assertEquals(false, FAMILY in reasons("엄마 폰 액정 보호필름 내가 사 놨어"))
    @Test fun 가족_폰_고장은_사칭() = assertEquals(true, FAMILY in reasons("할머니 저 손자예요 폰이 고장 나서 이 번호로 연락드려요"))
    @Test fun 가족_기프트카드는_사칭() = assertEquals(true, FAMILY in reasons("엄마 기프트카드 몇 장만 사서 번호 보내줘"))

    // 브라우저 주소창은 http:// 없이 보여 준다
    @Test fun 주소창_IP주소() = assertEquals(1, RuleDetector.linkWarnings("10.0.0.1/pay").size)
    @Test fun 주소창_수상한_끝자리() = assertEquals(1, RuleDetector.linkWarnings("han-bit.xyz/a8Kd2").size)
    @Test fun 주소창_APK() = assertEquals(2, RuleDetector.linkWarnings("http://evil.top/app.apk").size)
    @Test fun 주소창_정상_사이트() = assertEquals(0, RuleDetector.linkWarnings("m.naver.com/news").size)

    @Test fun 긴_숫자_가리기() = assertEquals("계좌 ●●●●●● 로", SmishingEngine.mask("계좌 123456789012 로"))
}
