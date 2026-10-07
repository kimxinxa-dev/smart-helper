package com.smarthelper.app.guide

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 위험 메시지를 받은 직후 앱을 설치하려 하면 화면 전체를 가리는 경고 (LinkBlockOverlay 와 같은 방식).
 * 크게 "안전하게 그만두기", 가운데 "가족에게 전화하기", 작게 "그래도 진행".
 */
class InstallBlockOverlay(
    private val ctx: Context,
    private val wm: WindowManager,
    private val onQuit: () -> Unit,
    private val onFamily: () -> Unit,
    private val onProceed: () -> Unit,
) {
    private var view: View? = null
    val showing get() = view != null

    /** detail: "오후 2:03 · 010-1234-5678 님이 보낸 문자" 같은 위험 메시지 설명, family: 가족 이름(없으면 null) */
    fun show(detail: String, family: String?) {
        hide()
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(0xF2B45309.toInt())
            isClickable = true // 뒤의 설치 화면이 눌리지 않게 막는다
        }
        col.addView(text("✋", 64f, false))
        col.addView(text("잠깐만요!", 34f, true), lp(top = 8))
        col.addView(text("방금 받은 문자 때문에\n설치하시는 건가요?", 26f, true), lp(top = 8))
        col.addView(text(detail, 19f, false).apply {
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply { setColor(0x55000000); cornerRadius = dp(12).toFloat() }
        }, lp(top = 16))
        col.addView(text("사기 문자가 시키는 앱은 휴대폰을 몰래 조종하거나 돈을 빼 갈 수 있어요.", 18f, false), lp(top = 12))
        col.addView(button("🛡️ 안전하게 그만두기", 24f, Color.WHITE, 0xFFB45309.toInt(), 72) { hide(); onQuit() }, lp(top = 28))
        col.addView(button(if (family != null) "📞 $family 님께 전화하기" else "📞 118 상담센터에 전화하기", 20f, 0x33FFFFFF, Color.WHITE, 60) { onFamily() }, lp(top = 12))
        col.addView(TextView(ctx).apply {
            text = "그래도 진행 (5분 동안 다시 묻지 않아요)"
            setTextColor(0xCCFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            setPadding(dp(8), dp(20), dp(8), dp(8))
            setOnClickListener { hide(); onProceed() }
        }, lp(top = 4))
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS }
        wm.addView(col, p)
        view = col
    }

    fun hide() {
        view?.let { wm.removeView(it) }
        view = null
    }

    private fun button(s: String, sp: Float, bg: Int, fg: Int, minH: Int, onClick: () -> Unit) = Button(ctx).apply {
        text = s
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(fg)
        background = GradientDrawable().apply { setColor(bg); cornerRadius = dp(20).toFloat() }
        minHeight = dp(minH)
        setOnClickListener { onClick() }
    }

    private fun text(s: String, sp: Float, bold: Boolean) = TextView(ctx).apply {
        text = s
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        gravity = Gravity.CENTER
        setLineSpacing(0f, 1.2f)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun lp(top: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()
}
