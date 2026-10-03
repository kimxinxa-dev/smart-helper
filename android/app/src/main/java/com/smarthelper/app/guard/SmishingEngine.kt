package com.smarthelper.app.guard

/** 위험도. SKIP = 저장된 연락처라 검사하지 않음 */
enum class Level { SKIP, LOW, MID, HIGH }

/** 검사기 하나가 찾은 결과 */
data class Finding(val score: Int, val reasons: List<String>, val flag: Boolean = false)

data class Verdict(val level: Level, val score: Int, val reasons: List<String>, val urls: List<String>)

/**
 * 문자 하나를 살펴보는 검사기.
 * 지금은 RuleDetector 하나지만, 학습한 분류 모델(A안)·휴대폰 안 언어모델(C안)도 같은 자리에 꽂는다.
 */
interface Detector {
    /** true 면 AI 모델처럼 무거운 검사기. 링크가 있거나 규칙에 걸린 문자에만 돌린다(배터리 절약). */
    val heavy: Boolean get() = false
    fun inspect(body: String): Finding
}

object SmishingEngine {
    val detectors: MutableList<Detector> = mutableListOf(RuleDetector)

    fun check(body: String, savedContact: Boolean): Verdict {
        val urls = RuleDetector.urls(body)
        // 1단계: 저장된 번호는 검사하지 않는다
        if (savedContact) return Verdict(Level.SKIP, 0, listOf("연락처에 저장된 번호라서 검사하지 않았어요."), urls)
        // 2·3단계: 가벼운 규칙은 항상, 무거운 AI는 의심 후보에만
        val light = detectors.filter { !it.heavy }.map { it.inspect(body) }
        val candidate = urls.isNotEmpty() || light.any { it.score > 0 || it.flag }
        val found = light + if (candidate) detectors.filter { it.heavy }.map { it.inspect(body) } else emptyList()
        val score = found.sumOf { it.score }
        val flag = found.any { it.flag }
        val level = when {
            score >= 6 -> Level.HIGH
            score >= 3 || flag -> Level.MID
            else -> Level.LOW
        }
        return Verdict(level, score, found.flatMap { it.reasons }.distinct(), urls)
    }

    /** 6자리 이상 숫자(계좌·주민번호 등)를 가린다 */
    fun mask(text: String) = text.replace(Regex("\\d{6,}"), "●●●●●●")
}

/** index.html 의 RULES(직접 확인) + smish(자동 감시 3단계)를 옮긴 규칙 검사기 */
object RuleDetector : Detector {
    private val URL = Regex("https?://\\S+|(?:[a-z0-9-]+\\.)+[a-z]{2,}(?:/\\S*)?", RegexOption.IGNORE_CASE)

    private val RULES = listOf(
        Triple(Regex("https?://|bit\\.ly|me2\\.do|\\.xyz|\\.top", RegexOption.IGNORE_CASE), 3, "모르는 인터넷 주소(링크)가 들어 있어요. 누르면 가짜 사이트로 갈 수 있어요."),
        Triple(Regex("택배|배송|반송|주소 ?불명"), 2, "택배 문자를 흉내 낸 사기가 아주 많아요."),
        Triple(Regex("검찰|경찰|금융감독원|수사|체포|안전계좌"), 4, "공공기관을 사칭하고 있어요. 진짜 기관은 문자로 돈을 요구하지 않아요."),
        Triple(Regex("(엄마|아빠|아들|딸).*(폰|휴대폰|고장|액정|급해|상품권)"), 4, "자녀를 사칭하는 전형적인 수법이에요. 꼭 전화로 직접 확인하세요."),
        Triple(Regex("계좌|송금|입금|이체|보내 ?[주줘]"), 2, "돈을 보내라고 요구해요."),
        Triple(Regex("인증번호|비밀번호|OTP|주민|카드번호|CVC", RegexOption.IGNORE_CASE), 3, "비밀번호나 인증번호를 알려 달라고 해요. 절대 알려 주면 안 돼요."),
        Triple(Regex("당첨|무료|지원금|환급|대출|저금리|고수익|투자"), 2, "공짜 돈이나 큰 이익을 약속해요. 사기의 흔한 미끼예요."),
        Triple(Regex("설치|apk|다운로드", RegexOption.IGNORE_CASE), 3, "앱을 설치하라고 해요. 몰래 휴대폰을 조종하는 앱일 수 있어요."),
        Triple(Regex("지금 ?바로|오늘까지|긴급|즉시|마감"), 1, "서두르게 만들어요. 급하게 하라는 말은 사기의 신호예요."),
    )

    private val KEYWORDS = listOf("배송", "택배", "주소지", "미수령", "관세", "검찰", "금감원", "형사", "구속", "명의도용", "지원금", "저금리", "대환대출", "모바일청첩장")
    private val IP_URL = Regex("https?://\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}", RegexOption.IGNORE_CASE)
    private val APK = Regex("\\.apk($|\\?)", RegexOption.IGNORE_CASE)
    private val BAD_TLD = Regex("\\.(top|xyz|site|club|work|kim|space|online)(?![a-z0-9-])", RegexOption.IGNORE_CASE)

    /** 링크 추출기. 앱에서는 안드로이드 Patterns.WEB_URL 로 바꿔 끼우고, PC 테스트에서는 정규식을 쓴다. */
    var urlFinder: (String) -> List<String> = { body -> URL.findAll(body).map { it.value }.toList() }

    fun urls(body: String) = urlFinder(body)

    /** 링크 주소 모양만 보고 찾은 위험 이유. 브라우저 주소창처럼 http:// 가 없는 주소도 받는다 */
    fun linkWarnings(url: String): List<String> {
        val u = if (url.contains("://")) url else "http://$url"
        return listOfNotNull(
            "IP 주소로 바로 접속하는 링크예요.".takeIf { IP_URL.containsMatchIn(u) },
            "앱(APK) 파일을 바로 내려받는 링크예요.".takeIf { APK.containsMatchIn(u) },
            "수상한 주소 끝자리(.top, .xyz 등)예요.".takeIf { BAD_TLD.containsMatchIn(u) },
        )
    }

    override fun inspect(body: String): Finding {
        val reasons = mutableListOf<String>()
        var score = 0
        for ((re, s, why) in RULES) if (re.containsMatchIn(body)) { score += s; reasons += why }

        // 자동 감시 3단계 중 3단계: 링크가 있을 때만 링크 모양과 위험 단어를 본다
        val urls = urls(body)
        var flag = false
        if (urls.isNotEmpty()) {
            for (u in urls) linkWarnings(u).let { if (it.isNotEmpty()) { flag = true; reasons += it } }
            val hits = KEYWORDS.filter { body.contains(it) }
            if (hits.size >= 2) { flag = true; reasons += "위험 단어가 ${hits.size}개 있어요: ${hits.joinToString(", ")}" }
        }
        return Finding(score, reasons, flag)
    }
}
