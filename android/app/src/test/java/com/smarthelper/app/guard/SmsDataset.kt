package com.smarthelper.app.guard

import kotlin.random.Random

/**
 * 합성 학습 데이터 생성기.
 * 실제 스미싱 유형을 본뜬 틀(사기 45종)과, 헷갈리기 쉬운 정상 문자 틀(42종)에 내용을 바꿔 끼워 만든다.
 * 실제 문자 데이터를 모으면 data/sms_real.tsv 에 "라벨<TAB>문자" 로 넣어 함께 학습한다.
 */
object SmsDataset {
    data class Sample(val text: String, val label: Int, val template: Int)

    val SMISHING = listOf(
        "[{courier}] 고객님 택배가 주소지 불일치로 반송 예정입니다. 주소 확인 {bad}",
        "[{courier}] 미수령 택배 보관 중. 오늘까지 주소 수정 바랍니다 {bad}",
        "[국제발신] 해외 직구 물품 관세 미납으로 통관 보류. 납부 확인 {bad}",
        "고객님 택배 운송장 번호({num}) 주소 불명. {bad} 확인 후 재배송 신청",
        "[{courier}] 배송비 {won} 미결제로 배송이 지연되고 있습니다 {bad}",
        "{rel} 나 폰 액정 깨져서 수리 맡겼어. 이 번호로 문자 줘",
        "{rel} 나 {name}인데 폰 고장나서 친구폰으로 연락해. 급하게 상품권 좀 사줄 수 있어?",
        "{rel} 바빠? 나 지금 급해서 그런데 {won}만 이 계좌로 보내줘 {bank} {acct}",
        "{rel} 나 휴대폰 보험 처리하려는데 인증번호 좀 알려줘",
        "{rel} 이 링크로 앱 하나만 설치해 줘 내가 처리할게 {bad}",
        "서울중앙지검 수사관입니다. 고객님 명의 계좌가 범죄에 연루되어 연락드립니다. 회신 바랍니다.",
        "[금융감독원] 귀하 명의도용 피해 신고 접수. 안전계좌로 자산 이전 필요. 문의 {tel}",
        "[경찰청] 교통법규 위반 과태료 고지서 발송. 확인 {bad}",
        "[국민건강보험] 건강검진 결과 통보서 확인하기 {bad}",
        "[정부24] 긴급 재난지원금 {won} 지급 대상자입니다. 신청 마감 {date} {bad}",
        "[국세청] 미수령 환급금 {won} 조회 및 신청 {bad}",
        "[{bank}] 정부지원 저금리 대환대출 승인 안내. 최대 {won} 한도 {tel}",
        "고객님은 저금리 대출 우선 대상입니다. 기존 대출 상환 후 진행 {bad}",
        "[해외결제] {card} {won} 승인 완료. 본인 아닐 경우 고객센터 {tel} 연락",
        "해외 IP에서 카카오 계정 로그인 시도가 감지되었습니다. 보호조치 {bad}",
        "[네이버] 회원님 계정이 정지 예정입니다. 본인 확인 {bad}",
        "모바일청첩장이 도착했습니다. {name}♥{name2} 결혼합니다 {bad}",
        "[부고] 故 {name}님 별세. 장례식장 안내 {bad}",
        "축하합니다! {shop} 경품 이벤트 당첨. 수령 정보 입력 {bad}",
        "무료 쿠폰 {won} 지급! 앱 설치 후 사용 {bad}",
        "(광고) 수익률 300% 보장 주식 리딩방 무료 입장 {bad}",
        "코인 고수익 보장 VIP 리딩 무료체험 카톡 문의 {bad}",
        "[{courier}] 택배 도착 예정. 수령 시 본인 확인 앱 설치 필요 {bad}",
        "{date} 쓰레기 무단투기 과태료 부과. 사진 확인 {bad}",
        "[우체국] 등기우편 2회 미수령. 반송 예정 안내 {bad}",
        "{rel} 나 문화상품권 10만원짜리 5장만 사서 핀번호 사진 찍어 보내줘 급해",
        "고객님의 {card} 결제 {won} 이상 거래 감지. 즉시 확인 {bad}",
        "[검찰청] 사건번호 {num} 출석 요구서 확인 {bad}",
        "보험금 환급 대상자입니다. 주민번호 입력 후 신청 {bad}",
        // 링크 없는 기관 사칭 보강
        "[{bank}] 고객님 계좌가 대포통장으로 신고되어 지급정지 예정입니다. 해제 문의 {tel}",
        "경찰청 사이버수사대입니다. 고객님 명의로 개설된 계좌가 사기에 이용되었습니다. 즉시 연락 바랍니다 {tel}",
        "[금융감독원] 고객님 예금 보호를 위해 자산을 안전계좌로 옮겨야 합니다. 담당자 {tel}",
        "검찰청 {name} 수사관입니다. 금융범죄 사건 관련 조사가 필요합니다. 오늘 중 연락 바랍니다.",
        "[법원] 귀하 앞으로 압류 명령이 접수되었습니다. 벌금 {won} 즉시 납부 바랍니다. 문의 {tel}",
        // 링크 없는 지인 사칭 보강 (엄마·아빠 말고 어머니·할머니·고모 등 여러 호칭)
        "{elder} 저 {me}예요 번호가 바뀌어서 새 번호로 연락드려요 이 번호로 저장해 주세요",
        "{elder} 저예요 {me} {name}이요 임시 번호라서 지금은 통화가 안 돼요 문자로만 답장 주세요",
        "{elder} 바쁘세요? 저 {me}인데 부탁드릴 게 하나 있어요 이 번호로 문자 주세요",
        "{elder} 저 {name}이에요 번호 새로 만들었어요 저장해 두시고 카톡 친구 추가해 주세요",
        "{elder} {me} {name}입니다 번호 바꿨어요 확인하시면 이 번호로 답장 부탁드려요",
        "{rel} 나 {name}인데 번호 바뀌었어 이 번호 저장하고 문자로 답 줘 지금 통화 못 해",
    )

    val NORMAL = listOf(
        "[{courier}] 고객님의 상품이 {date} 배송 완료되었습니다. 문 앞에 두었습니다.",
        "[{courier}] {date} {time}~ 배송 예정입니다. 송장번호 {num}",
        "[{hosp}] {date} {time} 진료 예약되었습니다. 변경은 병원으로 연락 바랍니다.",
        "[{card}] {name}님 {won} 승인 {shop} 일시불 {date}",
        "[{bank}] 입금 {won} 잔액 {won2} {name}",
        "[네이버] 인증번호 [{num}]를 입력해 주세요.",
        "[카카오] 인증번호 {num}. 타인에게 절대 알려주지 마세요.",
        "{rel} 오늘 저녁에 늦을 것 같아요. 먼저 드세요",
        "{rel} 이번 주말에 {place}에서 만나요. 점심 같이 먹어요",
        "{rel} 사진 잘 받았어요. {name}이가 많이 컸네요",
        "{rel} 폰 충전기 어디 있어요?",
        "내일 {time}에 {place}에서 보자",
        "[{gu}구청] 폭염 경보 발령. 야외활동 자제하고 물을 자주 드세요.",
        "[행정안전부] 오늘 {time} 호우경보. 하천 접근 자제 바랍니다.",
        "(광고)[{shop}] 가을 세일 최대 50% 할인! 무료수신거부 080-{n4}-{n4}",
        "(광고)[{shop}] 회원님께 {won} 쿠폰이 발급되었어요. {good}",
        "[{shop}] 주문하신 상품이 결제 완료되었습니다. 주문번호 {num}",
        "[국민건강보험공단] {name}님 올해 건강검진 대상자입니다. 가까운 검진기관에 예약하세요.",
        "[{hosp}] 처방전이 발급되었습니다. 약국에서 수령하세요.",
        "[{bank}] {date} 자동이체 {won} 출금 예정입니다.",
        "[SKT] {name}님 이번 달 데이터 사용량이 80%를 넘었습니다.",
        "{name}아 생일 축하해! 맛있는 거 먹어",
        "택배 기사입니다. 오늘 {time} 방문 예정이에요. 부재 시 경비실에 맡길게요",
        "회의 자료 메일로 보냈습니다. 확인 부탁드립니다.",
        "[{apt}아파트] 관리사무소입니다. {date} 엘리베이터 점검이 있습니다.",
        "[{gu}구 보건소] 독감 예방접종 안내. 만 65세 이상 무료 접종 {date}부터",
        "[{card}] {date} 결제 예정 금액 {won}입니다. {good}",
        "[{hosp}] 검사 결과가 나왔습니다. 내원하여 확인 바랍니다.",
        "{rel} 저 {name}예요. 이번 주 일요일에 갈게요",
        "[{shop}] 고객님 적립금 {won}이 소멸 예정입니다. 앱에서 확인하세요.",
        "[우체국택배] {date} 배송 예정. 문의 1588-1300",
        "택배 왔어요 경비실에 맡겨뒀어요",
        "[{bank}] 고객님 정기예금이 {date} 만기됩니다. 가까운 영업점 방문 바랍니다.",
        // 기관이 보내는 진짜 안내 (기관 사칭과 헷갈리지 않게)
        "[경찰청] 보이스피싱 주의! 검찰·경찰·금감원은 절대 돈을 요구하지 않습니다.",
        "[{gu}구청] 주민세 납부 기간입니다. 가까운 은행이나 위택스에서 납부하세요.",
        "[{bank}] 고객님 계좌 개설이 완료되었습니다. 문의는 대표번호로 연락 바랍니다.",
        // 같은 호칭의 평범한 가족 문자 (호칭만 보고 사기라고 하지 않게)
        "{elder} 저 {me}예요 이번 주말에 찾아뵐게요",
        "{elder} 날씨가 추워졌어요 식사 잘 챙겨 드세요",
        "{elder} 생신 축하드려요 저녁에 전화드릴게요",
        "{elder} 저 {name}이에요 집에 잘 도착했어요",
        "{elder} 병원 잘 다녀오셨어요? 결과 나오면 알려 주세요",
        "{elder} 보내 주신 김치 잘 받았어요 감사해요 {me} 올림",
    )

    private val SLOTS = mapOf(
        "courier" to listOf("CJ대한통운", "한진택배", "롯데택배", "우체국택배", "로젠택배"),
        "rel" to listOf("엄마", "아빠", "엄마!", "아빠~"),
        "elder" to listOf("어머니", "아버지", "할머니", "할아버지", "고모", "이모", "삼촌", "외삼촌", "큰아버지", "작은엄마"),
        "me" to listOf("큰아들", "막내딸", "손녀", "손자", "조카", "며느리", "사위", "둘째"),
        "name" to listOf("민수", "지영", "현우", "수진", "도윤", "서연", "준호", "하은"),
        "name2" to listOf("민지", "태훈", "유나", "성민"),
        "bank" to listOf("국민은행", "신한은행", "우리은행", "농협", "하나은행"),
        "card" to listOf("KB국민카드", "신한카드", "삼성카드", "현대카드", "롯데카드"),
        "shop" to listOf("스타벅스", "GS25", "이마트", "쿠팡", "올리브영", "다이소"),
        "hosp" to listOf("한마음병원", "서울내과", "튼튼정형외과", "밝은안과"),
        "place" to listOf("강남역", "시청 앞", "집 앞", "역 2번 출구"),
        "gu" to listOf("강남", "마포", "수성", "해운대", "유성"),
        "apt" to listOf("햇살", "푸른마을", "행복", "한빛"),
        "date" to listOf("10/5", "10월 7일", "내일", "모레", "11월 2일"),
        "time" to listOf("오전 10시", "오후 2시 30분", "오후 6시", "오전 9시"),
    )

    private val BAD_HOSTS = listOf("cj-logis", "post-kr", "hanjin-dlv", "nts-go", "gov24-kr", "kakao-safe", "police-kr", "fss-help", "coupang-event", "toss-sec", "naver-auth")

    private fun bad(r: Random): String {
        val h = BAD_HOSTS.random(r)
        val p = code(r)
        return when (r.nextInt(9)) {
            0 -> "http://$h.xyz/$p"
            1 -> "https://$h.top/$p"
            2 -> "bit.ly/$p"
            3 -> "http://${r.nextInt(11, 223)}.${r.nextInt(256)}.${r.nextInt(256)}.${r.nextInt(256)}/$p"
            4 -> "$h.site/$p"
            5 -> "http://$h-kr.com/$p"
            6 -> "me2.do/$p"
            7 -> "https://$h${r.nextInt(10, 99)}.online/$p"
            else -> "http://$h.kim/$p.apk"
        }
    }

    private fun good(r: Random) = listOf(
        "https://www.cjlogistics.com", "https://m.coupang.com", "https://www.nhis.or.kr",
        "https://www.gov.kr", "https://card.kbcard.com", "https://www.shinhancard.com",
    ).random(r)

    private fun code(r: Random) = (1..5).map { "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"[r.nextInt(55)] }.joinToString("")

    private fun won(r: Random) = listOf("2,500원", "3,000원", "50만원", "120만원", "1,980,000원", "49,800원", "300만원").random(r)

    private fun fill(t: String, r: Random): String = Regex("\\{(\\w+)\\}").replace(t) { m ->
        when (val k = m.groupValues[1]) {
            "bad" -> bad(r)
            "good" -> good(r)
            "won", "won2" -> won(r)
            "num" -> r.nextInt(100000, 999999).toString()
            "n4" -> r.nextInt(1000, 9999).toString()
            "acct" -> "${r.nextInt(100, 999)}-${r.nextInt(10, 99)}-${r.nextInt(1000000, 9999999)}"
            "tel" -> "02-${r.nextInt(1000, 9999)}-${r.nextInt(1000, 9999)}"
            else -> SLOTS.getValue(k).random(r)
        }
    }

    /** 앞머리(Web발신 등)는 두 종류 모두에 무작위로 붙여서 단서가 되지 않게 한다 */
    private fun decorate(t: String, r: Random) = when (r.nextInt(4)) {
        0 -> "[Web발신]\n$t"
        1 -> "$t 감사합니다."
        else -> t
    }

    /** 틀마다 perTemplate 개씩 만든다. template 번호: 사기 0부터, 정상 100부터 */
    fun generate(perTemplate: Int = 40, seed: Int = 42): List<Sample> {
        val r = Random(seed)
        val out = mutableListOf<Sample>()
        SMISHING.forEachIndexed { i, t -> repeat(perTemplate) { out += Sample(decorate(fill(t, r), r), 1, i) } }
        NORMAL.forEachIndexed { i, t -> repeat(perTemplate) { out += Sample(decorate(fill(t, r), r), 0, 100 + i) } }
        return out.distinctBy { it.text }
    }
}
