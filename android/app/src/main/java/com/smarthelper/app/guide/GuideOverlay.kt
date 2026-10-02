package com.smarthelper.app.guide

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 다른 앱 화면 위에 노란 테두리·화살표·안내 말풍선을 그린다 (터치는 그대로 아래 앱으로 통과).
 * 맨 위에는 "🔴 화면을 보고 있어요 · 그만" 띠를 항상 띄운다 (CLAUDE.md 안전 설계 2).
 */
class GuideOverlay(private val ctx: Context, private val wm: WindowManager, private val onStop: () -> Unit) {
    private var canvasView: HighlightView? = null
    private var banner: View? = null

    fun show() {
        if (canvasView != null) return
        canvasView = HighlightView(ctx).also { wm.addView(it, params(touchable = false)) }
        banner = makeBanner().also {
            wm.addView(it, params(touchable = true).apply {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.TOP
                y = statusBarHeight()
            })
        }
    }

    /** target 이 null 이면 테두리 없이 말풍선만 */
    fun point(target: Rect?, message: String, step: Int, total: Int) {
        canvasView?.set(target, "$step/$total  $message")
    }

    fun hide() {
        canvasView?.let { it.stop(); wm.removeView(it) }
        banner?.let { wm.removeView(it) }
        canvasView = null
        banner = null
    }

    private fun params(touchable: Boolean) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply {
        if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }

    private fun makeBanner() = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(6), dp(8), dp(6))
        setBackgroundColor(0xEE7F1D1D.toInt())
        addView(TextView(ctx).apply {
            text = "🔴 스마트 헬퍼가 화면을 보고 있어요"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(Button(ctx).apply {
            text = "그만"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.BLACK)
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(12).toFloat() }
            setOnClickListener { onStop() }
        }, LinearLayout.LayoutParams(dp(80), dp(44)))
    }

    private fun statusBarHeight(): Int {
        val id = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) ctx.resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    /** 테두리(깜빡임) + 화살표 + 말풍선 */
    private class HighlightView(ctx: Context) : View(ctx) {
        private var target: Rect? = null
        private var message = ""
        private var pulse = 0f
        private val dens = ctx.resources.displayMetrics.density
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFF59E0B.toInt() }
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFF59E0B.toInt() }
        private val arrow = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 44 * dens; textAlign = Paint.Align.CENTER }
        private val bubbleBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF0111827.toInt() }
        private val bubbleText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 26 * dens; typeface = Typeface.DEFAULT_BOLD }
        private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
            start()
        }

        fun set(t: Rect?, msg: String) { target = t; message = msg; invalidate() }
        fun stop() = anim.cancel()

        override fun onDraw(c: Canvas) {
            val loc = IntArray(2).also { getLocationOnScreen(it) }
            val t = target?.let { RectF(it).apply { offset(-loc[0].toFloat(), -loc[1].toFloat()); inset(-6 * dens, -6 * dens) } }
            if (t != null) {
                glow.strokeWidth = (6 + 14 * pulse) * dens
                glow.alpha = (110 * (1 - pulse)).toInt()
                c.drawRoundRect(t, 16 * dens, 16 * dens, glow)
                ring.strokeWidth = 5 * dens
                c.drawRoundRect(t, 16 * dens, 16 * dens, ring)
                val bounce = 10 * dens * pulse
                if (t.top > 90 * dens) c.drawText("👇", t.centerX(), t.top - 12 * dens + bounce, arrow)
                else c.drawText("👆", t.centerX(), t.bottom + 50 * dens - bounce, arrow)
            }
            if (message.isEmpty()) return
            // 말풍선은 테두리와 겹치지 않게 반대쪽에 둔다
            val pad = 16 * dens
            val w = (width - 2 * pad).toInt()
            val layout = StaticLayout.Builder.obtain(message, 0, message.length, bubbleText, w - (2 * pad).toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.2f).build()
            val h = layout.height + 2 * pad
            val top = if (t == null || t.centerY() > height / 2f) 130 * dens else height - h - 120 * dens
            c.drawRoundRect(RectF(pad, top, width - pad, top + h), 20 * dens, 20 * dens, bubbleBg)
            c.save(); c.translate(2 * pad, top + pad); layout.draw(c); c.restore()
        }
    }
}
