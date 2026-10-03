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
import com.smarthelper.app.guard.LinkGuard

/** 위험한 주소가 브라우저에서 열리면 화면 전체를 가리는 경고. "안전하게 나가기"를 크게, "그래도 볼래요"는 작게. */
class LinkBlockOverlay(
    private val ctx: Context,
    private val wm: WindowManager,
    private val onExit: () -> Unit,
    private val onStay: (String) -> Unit,
) {
    private var view: View? = null
    val showing get() = view != null

    fun show(d: LinkGuard.Danger) {
        hide()
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(0xF27F1D1D.toInt())
            isClickable = true // 뒤의 웹 페이지가 눌리지 않게 막는다
        }
        col.addView(text("🚨", 64f, false))
        col.addView(text("위험한 사이트예요", 32f, true), lp(top = 8))
        col.addView(text(d.host, 20f, false).apply {
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply { setColor(0x55000000); cornerRadius = dp(12).toFloat() }
        }, lp(top = 12))
        col.addView(text(d.reason, 22f, true), lp(top = 20))
        col.addView(text("카드번호·비밀번호·인증번호를 넣지 마세요.\n앱을 설치하라고 하면 절대 설치하지 마세요.", 19f, false), lp(top = 12))
        col.addView(Button(ctx).apply {
            text = "🛡️ 안전하게 나가기"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF7F1D1D.toInt())
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(20).toFloat() }
            minHeight = dp(72)
            setOnClickListener { hide(); onExit() }
        }, lp(top = 32))
        col.addView(TextView(ctx).apply {
            text = "그래도 볼래요 (위험할 수 있어요)"
            setTextColor(0xCCFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            setPadding(dp(8), dp(20), dp(8), dp(8))
            setOnClickListener { hide(); onStay(d.host) }
        }, lp(top = 8))
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
