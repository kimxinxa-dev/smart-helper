package com.smarthelper.app.assist

/** 말에서 알아낸 할 일. 안드로이드와 무관한 순수 Kotlin 이라 PC 에서 테스트한다. */
sealed class Command {
    data class Call(val who: String?, val number: String?) : Command()
    data class Sms(val who: String?, val number: String?, val body: String?) : Command()
    data class Volume(val up: Boolean) : Command()
    /** hour < 0 이면 시간을 못 알아들음. afterMinutes 가 있으면 "30분 뒤" 같은 상대 시간 */
    data class Alarm(val hour: Int, val minute: Int, val tomorrow: Boolean, val afterMinutes: Int? = null) : Command()
    data class OpenApp(val name: String) : Command()
    data class Install(val name: String) : Command()
    data class Torch(val on: Boolean) : Command()
    data class AddContact(val name: String?, val number: String?) : Command()
    data class Emergency(val number: String) : Command()
    data object FontSize : Command()
    data object Wifi : Command()
    data object Bluetooth : Command()
    data object Camera : Command()
    data object Time : Command()
    data object Date : Command()
    data object Battery : Command()
    /** 송금·이체: 안전을 위해 대신하지 않는다 */
    data object Banking : Command()
    data object Help : Command()
    data class Unknown(val text: String) : Command()
}

/**
 * 규칙 기반 말 이해. 순서가 중요하다(문자 > 전화, 손전등 > 앱 열기 등).
 * 나중에 C안 언어모델이 이 자리를 대신하거나 못 알아들은 말만 맡을 수 있다.
 */
object CommandParser {
    private val PHONE = Regex("0\\d{1,2}[- ]?\\d{3,4}[- ]?\\d{4}")
    private val WHO = Regex("([가-힣A-Za-z0-9 ]+?)\\s*(?:한테로|에게로|한테|에게|께)")
    private val FILLER = Regex("^(?:우리|내|저희|제|그|좀)\\s+")
    private val TAIL = Regex("\\s*(?:님|씨|좀|을|를|이|가|은|는|으로|로|앱|어플|어플리케이션)$")

    fun parse(raw: String): Command {
        val t = raw.trim().replace(Regex("[.?!~,]"), " ").replace(Regex("\\s+"), " ").trim()
        fun has(re: String) = Regex(re, RegexOption.IGNORE_CASE).containsMatchIn(t)
        val number = PHONE.find(t)?.value?.replace(Regex("[- ]"), "")

        return when {
            has("(?<!\\d)119(?!\\d)|구급차|불이 ?났|불났|쓰러졌") -> Command.Emergency("119")
            has("(?<!\\d)112(?!\\d)|경찰.*(불러|신고)|도둑") -> Command.Emergency("112")
            has("송금|이체|계좌|돈 ?(좀 )?(보내|부쳐)") -> Command.Banking
            has("뭘? ?할 ?수 ?있|뭐 ?해 ?줄 ?수|도움말|어떻게 (써|사용)") -> Command.Help
            has("(연락처|전화번호|번호).*(저장|추가|등록)|(저장|추가|등록).*(연락처|번호)") ->
                Command.AddContact(who(t) ?: before(t, "(?:의\\s*)?(?:연락처|전화번호|번호)")?.takeIf { number == null || !it.contains(number) }, number)
            has("문자|메시지|메세지|카톡 ?보내") && has("보내|써|전해|해") ->
                Command.Sms(if (number != null) null else who(t) ?: before(t, "(?:에게|한테)?\\s*(?:문자|메시지|메세지)"), number, smsBody(t))
            has("전화") -> Command.Call(if (number != null) null else who(t) ?: before(t, "(?:에게|한테)?\\s*전화"), number)
            has("알람|깨워") -> alarm(t)
            has("손전등|플래시|후레시|후레쉬|랜턴") -> Command.Torch(!has("꺼|끄|끔"))
            has("소리|음량|볼륨|벨소리|안 ?들려|시끄") -> Command.Volume(up = !has("줄|작게|낮|내려|시끄|조용"))
            has("글자|글씨") -> Command.FontSize
            has("와이파이|wifi|인터넷 ?연결") -> Command.Wifi
            has("블루투스") -> Command.Bluetooth
            has("배터리|충전.*(얼마|몇)") -> Command.Battery
            has("며칠|날짜|무슨 ?요일|몇 ?월") -> Command.Date
            has("몇 ?시|시간.*(알려|몇)") -> Command.Time
            has("설치|깔아|깔아 ?줘|다운") -> before(t, "\\s*(?:좀\\s*)?(?:설치|깔아|다운)")?.let { Command.Install(it) } ?: Command.Unknown(raw)
            has("카메라|사진 ?(찍|촬영)") -> Command.Camera
            has("열어|켜|실행|틀어|들어가") -> before(t, "\\s*(?:좀\\s*)?(?:열어|켜|실행|틀어|들어가)")?.let { Command.OpenApp(it) } ?: Command.Unknown(raw)
            else -> Command.Unknown(raw)
        }
    }

    /** "영희한테", "우리 아들 민수에게" → "영희", "아들 민수" */
    private fun who(t: String) = WHO.find(t)?.groupValues?.get(1)?.let(::clean)

    /** 표시 말 앞부분을 이름으로: "카카오톡 열어 줘" → "카카오톡" */
    private fun before(t: String, marker: String): String? =
        Regex("^(.*?)$marker").find(t)?.groupValues?.get(1)?.let(::clean)

    private fun clean(s: String): String? {
        var r = s.trim()
        while (true) { val n = FILLER.replace(r, "").replace(TAIL, "").trim(); if (n == r) break; r = n }
        return r.ifEmpty { null }
    }

    /** "민수한테 이따 간다고 문자 보내 줘" → "이따 간다", "문자 보내 줘 지금 출발해" → "지금 출발해" */
    private fun smsBody(t: String): String? {
        Regex("(?:한테|에게|께)\\s+(.+?)\\s*(?:라고|이라고|고)?\\s*(?:문자|메시지|메세지)").find(t)
            ?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return Regex("(?:문자|메시지|메세지)\\s*(?:좀\\s*)?(?:보내|써)\\s*(?:줘|주세요|줄래|봐)?\\s+(.+)$").find(t)
            ?.groupValues?.get(1)?.replace(Regex("\\s*(?:라고|이라고)$"), "")?.trim()?.ifEmpty { null }
    }

    private val KNUM = linkedMapOf("열한" to 11, "열두" to 12, "다섯" to 5, "여섯" to 6, "일곱" to 7, "여덟" to 8, "아홉" to 9, "한" to 1, "두" to 2, "세" to 3, "네" to 4, "열" to 10)
    private val TIME = Regex("(오전|오후|아침|저녁|밤|새벽|낮)?\\s*(\\d{1,2}|${KNUM.keys.joinToString("|")})\\s*시\\s*(반|(\\d{1,2})\\s*분)?")
    private val AFTER = Regex("(\\d+)\\s*(분|시간)\\s*(?:뒤|후|있다가)")

    private fun alarm(t: String): Command.Alarm {
        AFTER.find(t)?.let { m ->
            val n = m.groupValues[1].toInt()
            return Command.Alarm(-1, 0, false, if (m.groupValues[2] == "시간") n * 60 else n)
        }
        val m = TIME.find(t) ?: return Command.Alarm(-1, 0, t.contains("내일"))
        var h = m.groupValues[2].toIntOrNull() ?: KNUM.getValue(m.groupValues[2])
        val part = m.groupValues[1]
        if (part in setOf("오후", "저녁", "밤") && h < 12) h += 12
        if (part == "낮" && h < 6) h += 12
        if (part in setOf("오전", "아침", "새벽") && h == 12) h = 0
        val min = when {
            m.groupValues[3] == "반" -> 30
            m.groupValues[4].isNotEmpty() -> m.groupValues[4].toInt()
            else -> 0
        }
        return Command.Alarm(h % 24, min.coerceIn(0, 59), t.contains("내일"))
    }
}
