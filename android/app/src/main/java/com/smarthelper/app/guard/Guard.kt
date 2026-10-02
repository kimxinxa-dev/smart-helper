package com.smarthelper.app.guard

import android.content.Context
import android.util.Patterns

/** 문자 지킴이 판단 엔진 준비. 자동 검사(SmishingReceiver)와 직접 검사(앱 화면)가 같은 엔진을 쓴다. */
object Guard {
    fun init(ctx: Context) {
        RuleDetector.urlFinder = ::extractUrls
        ModelDetector.install(ctx)
    }

    /** 안드로이드 공식 링크 인식기로 URL 추출 */
    private fun extractUrls(text: String): List<String> {
        val urls = mutableListOf<String>()
        val matcher = Patterns.WEB_URL.matcher(text)
        while (matcher.find()) urls.add(matcher.group())
        return urls
    }
}
