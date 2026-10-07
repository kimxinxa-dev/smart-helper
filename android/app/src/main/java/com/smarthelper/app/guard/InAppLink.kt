package com.smarthelper.app.guard

/**
 * 카카오톡처럼 주소창이 없는 앱에서 링크 찾기 (순수 Kotlin, InAppLinkTest).
 * 누른 말풍선·미리보기 카드, 앱 안 웹 화면의 제목 줄 글자에서 주소처럼 보이는 것을 뽑는다.
 */
object InAppLink {
    private val URLISH = Regex(
        "(?:https?://)?(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d+)?(?:/[^\\s]*)?" +
            "|(?:https?://)?(?:[a-z0-9-]+\\.)+[a-z]{2,}(?::\\d+)?(?:/[^\\s]*)?",
        RegexOption.IGNORE_CASE,
    )

    fun links(texts: List<String>): List<String> =
        texts.flatMap { t -> URLISH.findAll(t).map { it.value.trimEnd('.', ',', ')', ']', '>', '"', '\'') }.toList() }
            .filter { it.isNotEmpty() }
            .distinct()
}
