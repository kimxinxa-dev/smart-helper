package com.smarthelper.app.guard

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.smarthelper.app.padForSystemBars
import java.util.Locale

/** 위험 문자 경고 화면. 큰 글씨 + 쉬운 말 + 음성 안내. */
class WarningActivity : Activity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var speech = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val item = GuardStore.get(this, intent.getLongExtra(EXTRA_ID, -1))
        if (item == null) { finish(); return }

        val level = Level.valueOf(item.getString("level"))
        val hasLink = item.optInt("links", 1) > 0
        val (color, title, lead) = when (level) {
            Level.HIGH -> Triple(0xFFB91C1C.toInt(), "🚨 매우 위험한 문자예요",
                if (hasLink) "답장하거나 링크를 누르지 마세요. 사기일 가능성이 높아요."
                else "답장하지 말고, 돈이나 개인정보를 보내지 마세요. 사기일 가능성이 높아요.")
            Level.MID -> Triple(0xFFB45309.toInt(), "⚠️ 조심해야 할 문자예요",
                if (hasLink) "링크를 누르기 전에 가족에게 먼저 물어보세요."
                else "시키는 대로 하기 전에 가족에게 먼저 물어보세요.")
            Level.LOW -> Triple(0xFF15803D.toInt(), "✅ 위험이 낮아 보여요", "그래도 모르는 링크는 누르지 마세요.")
            Level.SKIP -> Triple(0xFF15803D.toInt(), "✅ 저장된 번호의 문자예요", "연락처에 저장된 번호라서 검사하지 않았어요.")
        }
        val reasons = item.getJSONArray("reasons").let { a -> List(a.length()) { a.getString(it) } }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        col.addView(text(title, 30f, true, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(20), dp(16), dp(20))
            background = box(color, 0)
        })
        col.addView(text(lead, 24f, true, color).apply { setPadding(0, dp(16), 0, dp(8)) })
        col.addView(text("보낸 사람: ${item.getString("sender")}", 20f, false, GRAY))
        col.addView(text(item.getString("text"), 19f, false, Color.BLACK).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = box(0xFFF3F4F6.toInt(), 0xFFD1D5DB.toInt())
        }, margin(top = 8))
        if (level == Level.HIGH || level == Level.MID) {
            col.addView(text("왜 위험한가요?", 22f, true, Color.BLACK), margin(top = 20))
            reasons.forEach { col.addView(text("• $it", 20f, false, Color.BLACK), margin(top = 6)) }
            col.addView(text("이렇게 하세요", 22f, true, Color.BLACK), margin(top = 20))
            listOf(
                if (hasLink) "링크는 누르지 마세요." else "가족이나 기관이라고 해도, 알고 있는 번호로 직접 전화해서 확인하세요.",
                "답장하지 말고 문자를 지우세요.",
                "이미 돈을 보냈다면 바로 112에 전화하세요.",
                "불안하면 118(인터넷 상담)이나 1332(금융감독원)에 물어보세요.",
            ).forEach { col.addView(text("• $it", 20f, false, Color.BLACK), margin(top = 6)) }
        }
        col.addView(button("🔊 다시 듣기", 0xFFE5E7EB.toInt(), Color.BLACK) { say() }, margin(top = 24))
        col.addView(button("알겠어요", 0xFF1D4ED8.toInt(), Color.WHITE) { finish() }, margin(top = 10))

        val scroll = ScrollView(this).apply { setBackgroundColor(Color.WHITE); addView(col) }
        scroll.padForSystemBars()
        setContentView(scroll)

        speech = "$title. $lead " + (reasons.firstOrNull() ?: "")
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts?.language = Locale.KOREAN
        tts?.setSpeechRate(0.85f)
        say()
    }

    private fun say() {
        tts?.speak(speech.replace(Regex("[🚨⚠️✅]"), ""), TextToSpeech.QUEUE_FLUSH, null, "warn")
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun margin(top: Int) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(top) }

    private fun box(fill: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(16).toFloat()
        if (stroke != 0) setStroke(dp(2), stroke)
    }

    private fun text(s: String, sp: Float, bold: Boolean, c: Int) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(c)
        setLineSpacing(0f, 1.25f)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(s: String, bg: Int, fg: Int, onClick: () -> Unit) = Button(this).apply {
        text = s
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(fg)
        background = box(bg, 0)
        minHeight = dp(64)
        setOnClickListener { onClick() }
    }

    companion object {
        const val EXTRA_ID = "id"
        private const val GRAY = 0xFF4B5563.toInt()
    }
}
