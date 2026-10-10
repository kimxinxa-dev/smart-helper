package com.smarthelper.app.guard

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import com.smarthelper.app.Look
import com.smarthelper.app.padForSystemBars
import java.util.Locale

/** 위험 문자 경고 화면. 큰 글씨 + 쉬운 말 + 음성 안내. */
class WarningActivity : Activity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var speech = ""
    private val ui = Look(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val item = GuardStore.get(this, intent.getLongExtra(EXTRA_ID, -1))
        if (item == null) { finish(); return }

        val level = Level.valueOf(item.getString("level"))
        val hasLink = item.optInt("links", 1) > 0
        val source = item.optString("source", "문자")
        val what = if (source == "문자") "문자" else "메시지"
        // 위험도 → 머리 카드 색(위·아래), 배지 글자, 제목, 첫 줄
        data class Head(val top: Int, val bottom: Int, val glyph: String, val title: String, val lead: String)
        val head = when (level) {
            Level.HIGH -> Head(Look.RED, 0xFF991B1B.toInt(), "!", "매우 위험한 ${what}예요",
                if (hasLink) "답장하거나 링크를 누르지 마세요. 사기일 가능성이 높아요."
                else "답장하지 말고, 돈이나 개인정보를 보내지 마세요. 사기일 가능성이 높아요.")
            Level.MID -> Head(Look.AMBER, Look.AMBER_DARK, "!", "조심해야 할 ${what}예요",
                if (hasLink) "링크를 누르기 전에 가족에게 먼저 물어보세요."
                else "시키는 대로 하기 전에 가족에게 먼저 물어보세요.")
            Level.LOW -> Head(Look.GREEN, Look.GREEN_DARK, "✓", "위험이 낮아 보여요", "그래도 모르는 링크는 누르지 마세요.")
            Level.SKIP -> Head(Look.GREEN, Look.GREEN_DARK, "✓", "저장된 번호의 ${what}예요", "연락처에 저장된 번호라서 검사하지 않았어요.")
        }
        val title = head.title
        val lead = head.lead
        val reasons = item.getJSONArray("reasons").let { a -> List(a.length()) { a.getString(it) } }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(24))
        }
        // 머리 카드: 위험도 색 그라데이션 + 흰 배지 + 제목 + 첫 줄
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(ui.dp(20), ui.dp(26), ui.dp(20), ui.dp(24))
            background = ui.round(28, head.top, head.bottom)
            elevation = ui.dp(6).toFloat()
            addView(ui.badge(head.glyph, Color.WHITE, head.bottom, sizeDp = 76, ring = 0x33FFFFFF))
            addView(ui.text(title, 28f, true, Color.WHITE), ui.lp(top = 14))
            addView(ui.text(lead, 20f, false, 0xF0FFFFFF.toInt()), ui.lp(top = 8))
        })
        // 받은 내용 카드: 보낸 사람 + 가린 문자
        col.addView(section().apply {
            addView(ui.text("보낸 사람", 18f, false, Look.MUTED, center = false))
            addView(ui.text(item.getString("sender") + if (source != "문자") " ($source)" else "", 22f, true, Look.INK, center = false), ui.lp(top = 2))
            addView(ui.text(item.getString("text"), 19f, false, Look.INK, center = false).apply {
                setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14))
                background = ui.round(18, Look.SOFT)
            }, ui.lp(top = 12))
        }, ui.lp(top = 14))
        if (level == Level.HIGH || level == Level.MID) {
            col.addView(section().apply {
                addView(ui.text("왜 위험한가요?", 22f, true, Look.INK, center = false))
                reasons.forEach { addView(ui.text("•  $it", 19f, false, Look.INK, center = false), ui.lp(top = 8)) }
            }, ui.lp(top = 12))
            col.addView(section().apply {
                addView(ui.text("이렇게 하세요", 22f, true, Look.INK, center = false))
                listOf(
                    if (hasLink) "링크는 누르지 마세요." else "가족이나 기관이라고 해도, 알고 있는 번호로 직접 전화해서 확인하세요.",
                    "답장하지 말고 문자를 지우세요.",
                    "이미 돈을 보냈다면 바로 112에 전화하세요.",
                    "불안하면 118(인터넷 상담)이나 1332(금융감독원)에 물어보세요.",
                ).forEachIndexed { i, s -> addView(ui.text("${i + 1}.  $s", 19f, false, Look.INK, center = false), ui.lp(top = 8)) }
            }, ui.lp(top = 12))
        }
        col.addView(ui.secondary("🔊 다시 듣기", Color.WHITE, Look.INK, Look.LINE, sp = 21f, minDp = 62) { say() }, ui.lp(top = 20))
        col.addView(ui.primary("알겠어요", Look.BLUE, Look.BLUE_DARK) { finish() }, ui.lp(top = 10))

        val scroll = ScrollView(this).apply { setBackgroundColor(0xFFF3F5F9.toInt()); addView(col) }
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

    /** 흰 카드 한 칸 (내용 묶음) */
    private fun section() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ui.dp(20), ui.dp(18), ui.dp(20), ui.dp(20))
        background = ui.round(24, Color.WHITE, stroke = Look.LINE)
        elevation = ui.dp(1).toFloat()
    }

    companion object {
        const val EXTRA_ID = "id"
    }
}
