package com.smarthelper.app

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.text.LineBreakConfig
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 경고·안내 화면들(위험 팝업, 주의 카드, 링크·설치 차단, 경고 화면)이 함께 쓰는 모양.
 * 웹 화면(index.html)과 같은 색·둥근 모서리·그림자로 맞춘다. 글자는 크게(기본 18sp 이상).
 */
class Look(private val ctx: Context) {
    fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    fun text(s: String, sp: Float, bold: Boolean, color: Int, center: Boolean = true) = TextView(ctx).apply {
        text = s
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (center) gravity = Gravity.CENTER
        setLineSpacing(0f, 1.22f)
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        wordWrap(this)
    }

    /** 한국어를 낱말(띄어쓰기) 단위로 줄바꿈한다 ("마세 / 요" 대신 "마세요"). 안드로이드 13 미만은 기본 동작 */
    fun wordWrap(t: TextView) {
        if (Build.VERSION.SDK_INT >= 33) t.lineBreakWordStyle = LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE
    }

    /** 둥근 사각형 바탕. colors 가 둘이면 위→아래 그라데이션 */
    fun round(radiusDp: Int, vararg colors: Int, stroke: Int = 0, strokeDp: Int = 1) = GradientDrawable().apply {
        if (colors.size > 1) { this.colors = colors; orientation = GradientDrawable.Orientation.TOP_BOTTOM } else setColor(colors[0])
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke != 0) setStroke(dp(strokeDp), stroke)
    }

    /** 화면 전체 바탕 그라데이션 (위 → 아래) */
    fun backdrop(top: Int, bottom: Int) = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom))

    /** 동그란 아이콘 배지: 색 원 안에 큰 글자("!" 등), 바깥에 옅은 테 */
    fun badge(glyph: String, bg: Int, fg: Int, sizeDp: Int = 84, ring: Int = 0x00000000) = TextView(ctx).apply {
        text = glyph
        setTextColor(fg)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeDp * 0.46f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        gravity = Gravity.CENTER
        includeFontPadding = false
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(bg)
            if (ring != 0) setStroke(dp(8), ring)
        }
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply { gravity = Gravity.CENTER_HORIZONTAL }
    }

    /** 알약 모양 정보칸 (보낸 사람, 주소 등) */
    fun pill(s: String, sp: Float, bg: Int, fg: Int) = text(s, sp, false, fg).apply {
        setPadding(dp(16), dp(10), dp(16), dp(10))
        background = round(16, bg)
    }

    /** 흰 카드 (팝업 본문) */
    fun card() = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(24), dp(28), dp(24), dp(20))
        background = round(30, Color.WHITE)
        elevation = dp(16).toFloat()
    }

    /** 가장 권하는 버튼: 그라데이션 + 그림자 */
    fun primary(s: String, top: Int, bottom: Int, fg: Int = Color.WHITE, sp: Float = 22f, minDp: Int = 64, onClick: () -> Unit) = Button(ctx).apply {
        style(this, s, sp, fg, minDp)
        background = round(20, top, bottom)
        elevation = dp(4).toFloat()
        setOnClickListener { onClick() }
    }

    /** 보조 버튼: 옅은 바탕 + 테두리 */
    fun secondary(s: String, bg: Int, fg: Int, stroke: Int, sp: Float = 20f, minDp: Int = 58, onClick: () -> Unit) = Button(ctx).apply {
        style(this, s, sp, fg, minDp)
        background = round(20, bg, stroke = stroke, strokeDp = 2)
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    /** 일부러 작게 둔 "그래도 …" 글자 버튼 (밑줄) */
    fun quietLink(s: String, color: Int, onClick: () -> Unit) = TextView(ctx).apply {
        text = s
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        gravity = Gravity.CENTER
        paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
        setPadding(dp(8), dp(18), dp(8), dp(8))
        wordWrap(this)
        setOnClickListener { onClick() }
    }

    private fun style(b: Button, s: String, sp: Float, fg: Int, minDp: Int) = b.apply {
        text = s
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(fg)
        minHeight = dp(minDp)
        setPadding(dp(16), dp(10), dp(16), dp(10))
        wordWrap(this)
    }

    fun lp(top: Int, w: Int = LinearLayout.LayoutParams.MATCH_PARENT) =
        LinearLayout.LayoutParams(w, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }

    fun gap(v: View, top: Int) = v.apply { (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(top) }

    companion object {
        // index.html 과 같은 색
        const val INK = 0xFF0F172A.toInt()
        const val MUTED = 0xFF475569.toInt()
        const val LINE = 0xFFE2E8F0.toInt()
        const val SOFT = 0xFFF1F5F9.toInt()
        const val RED = 0xFFDC2626.toInt()
        const val RED_DARK = 0xFFB91C1C.toInt()
        const val AMBER = 0xFFF59E0B.toInt()
        const val AMBER_DARK = 0xFFB45309.toInt()
        const val BLUE = 0xFF2563EB.toInt()
        const val BLUE_DARK = 0xFF1D4ED8.toInt()
        const val GREEN = 0xFF16A34A.toInt()
        const val GREEN_DARK = 0xFF15803D.toInt()
    }
}
