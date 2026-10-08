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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 🚨 위험한 문자·메시지를 받는 순간 지금 보던 화면 위에 띄우는 팝업 (알림과 함께).
 * 어두운 배경 위 가운데 카드: 보낸 사람, 가장 중요한 이유 한 줄, [왜 위험한지 보기] · [알겠어요].
 */
class RiskPopupOverlay(
    private val ctx: Context,
    private val wm: WindowManager,
    private val onDetail: (Long) -> Unit,
) {
    private var view: View? = null
    val showing get() = view != null

    /** what: "문자" 또는 "카카오톡 메시지", reason: 가장 중요한 이유 한 줄 */
    fun show(id: Long, sender: String, what: String, reason: String, hasLink: Boolean) {
        hide()
        val back = FrameLayout(ctx).apply {
            setBackgroundColor(0xB3000000.toInt())
            isClickable = true // 뒤의 화면이 눌리지 않게 막는다 (버튼으로만 닫는다)
        }
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(20))
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(28).toFloat() }
        }
        card.addView(text("🚨", 56f, false, Color.BLACK))
        card.addView(text("방금 온 ${what}가\n위험해요!", 30f, true, 0xFFB91C1C.toInt()), lp(top = 4))
        card.addView(text("$sender 님이 보냈어요", 19f, false, 0xFF374151.toInt()).apply {
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply { setColor(0xFFF3F4F6.toInt()); cornerRadius = dp(12).toFloat() }
        }, lp(top = 14))
        card.addView(text(reason, 21f, true, 0xFF111827.toInt()), lp(top = 16))
        card.addView(text(if (hasLink) "링크를 누르지 마세요. 답장하지 마세요." else "답장하지 마세요. 돈·인증번호를 보내지 마세요.", 19f, false, 0xFF374151.toInt()), lp(top = 10))
        card.addView(button("🔍 왜 위험한지 보기", 22f, Color.WHITE, 0xFFB91C1C.toInt()) { hide(); onDetail(id) }, lp(top = 24))
        card.addView(button("알겠어요", 20f, 0xFF111827.toInt(), 0xFFE5E7EB.toInt()) { hide() }, lp(top = 10))
        back.addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            leftMargin = dp(20); rightMargin = dp(20)
        })
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS }
        wm.addView(back, p)
        view = back
    }

    fun hide() {
        view?.let { wm.removeView(it) }
        view = null
    }

    private fun button(s: String, sp: Float, fg: Int, bg: Int, onClick: () -> Unit) = Button(ctx).apply {
        text = s
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(fg)
        background = GradientDrawable().apply { setColor(bg); cornerRadius = dp(18).toFloat() }
        minHeight = dp(64)
        setOnClickListener { onClick() }
    }

    private fun text(s: String, sp: Float, bold: Boolean, color: Int) = TextView(ctx).apply {
        text = s
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        gravity = Gravity.CENTER
        setLineSpacing(0f, 1.2f)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun lp(top: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()
}
