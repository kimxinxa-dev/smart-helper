package com.smarthelper.testapp

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 개발용 '가짜 카카오톡' 채팅방. 진짜 카카오톡은 계정이 있어야 해서, 스마트 헬퍼(디버그 앱)의
 * 카카오톡 링크 차단을 에뮬레이터에서 시험할 때 쓴다. 앱 목록에는 나오지 않는다.
 *   adb shell am start -n com.smarthelper.practicepuzzle/com.smarthelper.testapp.FakeChatActivity --es msg "'택배 주소 확인 http://cj-logis.xyz/a8K'"
 * 말풍선을 누르면 카카오톡처럼 앱 안 웹 화면(FakeBrowserActivity)이 열린다.
 */
class FakeChatActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val msg = intent.getStringExtra("msg") ?: "고객님 택배 주소 확인 부탁드려요 http://cj-logis.xyz/a8K"
        val url = Regex("\\S+\\.\\S+").find(msg)?.value ?: "http://example.com"
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFFB2C7DA.toInt()); setPadding(32, 48, 32, 32) }
        col.addView(text("가짜 카카오톡 · 모르는 사람", 20f).apply { setPadding(0, 0, 0, 32) })
        col.addView(text(msg, 22f).apply {
            setBackgroundColor(Color.WHITE); setPadding(32, 24, 32, 24)
            setOnClickListener { startActivity(Intent(this@FakeChatActivity, FakeBrowserActivity::class.java).putExtra("url", url)) }
        })
        setContentView(col)
    }

    private fun text(s: String, sp: Float) = TextView(this).apply { text = s; setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); setTextColor(Color.BLACK) }
}

/** 개발용 '가짜 카카오톡' 앱 안 웹 화면: 위쪽 제목 줄(페이지 제목 + 주소) + 웹 화면. 인터넷 없이 글만 띄운다 */
class FakeBrowserActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url") ?: "http://example.com"
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 16); setBackgroundColor(Color.WHITE) }
        bar.addView(TextView(this).apply { text = "택배 주소 확인"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); setTextColor(Color.BLACK) })
        bar.addView(TextView(this).apply { text = url.removePrefix("http://").removePrefix("https://").substringBefore('/'); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(Color.GRAY) })
        col.addView(bar)
        col.addView(WebView(this).apply {
            loadDataWithBaseURL(url, "<h2>주소를 다시 입력해 주세요</h2><p>이름, 전화번호, 카드번호</p>", "text/html", "utf-8", null)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(col)
    }
}
