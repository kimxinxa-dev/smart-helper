package com.smarthelper.app.guard

/**
 * 문자 내용 밖의 단서: 누가, 처음인지, 조금 전에 무슨 문자를 보냈는지 (순수 Kotlin, SenderSignalsTest).
 * 지인 사칭은 첫 문자("할머니 저 손자예요 번호 바꿨어요")에서는 돈 얘기를 하지 않고, 몇 통 뒤에 요구한다.
 */
object SenderSignals {
    /** 주의 이상 메시지 뒤 이 시간 안에 같은 상대가 보낸 메시지는 이어서 본다 */
    const val FOLLOW_MS = 24 * 60 * 60 * 1000L

    /** 이 상대에 대해 앱이 기억하는 것. 처음이면 null */
    data class Seen(val count: Int, val lastWarnAt: Long)

    // 가족·친척을 부르며 시작하거나("할머니 저…") 자기를 가족이라고 소개("저 손자 준서예요")
    private val FAMILY = Regex(
        // 뒤에 띄어쓰기·문장부호가 와야 한다 ("엄마손칼국수", "나 딸기 샀어" 는 아님)
        "^\\s*(\\[[^\\]]*\\]\\s*)?(엄마|아빠|어머니|어머님|아버지|아버님|할머니|할아버지|고모|이모|삼촌|외삼촌|큰아버지|작은아버지|큰엄마|작은엄마|여보|언니|오빠|누나|형)(?=[\\s,.!?~]|$)" +
            "|(저|나)\\s?(는\\s?)?(큰|작은|막내|둘째|외)?\\s?(아들|딸|손자|손녀|조카|며느리|사위)(?=[\\s,.!?~이인예야요입]|$)"
    )
    // 돈·상품권·인증·설치처럼 사기꾼이 결국 요구하는 것
    private val ASK = Regex("상품권|기프트|핀번호|인증|비밀번호|계좌|송금|이체|입금|만원|돈|결제|카드|신분증|설치|앱|링크|https?://", RegexOption.IGNORE_CASE)

    /** 보낸 사람 표시를 같은 모양으로 (+82 10… → 010…, 기호 제거). 메신저 이름은 앞뒤 공백만 뺀다 */
    fun normalize(sender: String, isSms: Boolean): String {
        if (!isSms) return sender.trim()
        val d = sender.filter { it.isDigit() || it == '+' }
        return when {
            d.startsWith("+82") -> "0" + d.removePrefix("+82").removePrefix("0")
            d.isEmpty() -> sender.trim()
            else -> d
        }
    }

    /**
     * isSms: 문자인지(메신저는 번호를 모르고, 앱이 생기기 전 대화도 모르므로 '처음'·'해외' 단서는 문자만 본다).
     * 연락처에 저장된 상대는 이 검사까지 오지 않는다.
     */
    fun inspect(sender: String, isSms: Boolean, body: String, seen: Seen?, now: Long, hasLink: Boolean): Finding {
        var score = 0
        var flag = false
        val reasons = mutableListOf<String>()

        if (isSms) {
            val raw = sender.filter { it.isDigit() || it == '+' }
            if ((raw.startsWith("+") && !raw.startsWith("+82")) || body.contains("국제발신") || body.contains("해외발신")) {
                score += 1; reasons += "해외에서 보낸 문자예요. 가족·기관이 해외 번호로 보내는 일은 드물어요."
            } else if (normalize(sender, true).startsWith("070")) {
                score += 1; reasons += "070 인터넷 전화번호예요. 누구나 쉽게 만들 수 있는 번호예요."
            }
            if (seen == null && FAMILY.containsMatchIn(body)) {
                score += 2; flag = true
                reasons += "처음 문자를 보낸 번호인데 가족처럼 말해요. 원래 알던 번호로 꼭 전화해서 확인하세요."
            }
        }

        val following = seen != null && seen.lastWarnAt > 0 && now - seen.lastWarnAt in 0..FOLLOW_MS
        if (following) {
            flag = true
            if (hasLink || ASK.containsMatchIn(body)) {
                score += 6
                reasons += "조금 전 의심스러운 문자를 보낸 상대가 이번에는 돈·인증번호·설치 같은 것을 부탁해요. 사기의 전형적인 순서예요."
            } else {
                reasons += "조금 전 의심스러운 문자를 보낸 상대예요. 계속 조심하세요."
            }
        }
        return Finding(score, reasons, flag)
    }
}
