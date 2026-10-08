package com.smarthelper.app.guide

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * ⚠️ 주의 단계 메시지를 받으면 화면 아래쪽에 띄우는 작은 노란 카드 (🚨 위험 팝업보다 가볍게).
 * - 화면을 어둡게 덮지 않고, 카드 밖은 그대로 누를 수 있다.
 *   (화면 전체를 덮지 않으니 뒤의 브라우저 주소창도 계속 읽혀 위험 링크 차단이 그대로 동작한다)
 * - 위쪽에는 문자 앱의 수신 알림([링크 열기] 등)이 뜨므로 겹치지 않게 아래에 둔다.
 * - 잠시 뒤 저절로 사라진다.
 */
class CautionBannerOverlay(
    private val ctx: Context,
    private val wm: WindowManager,
    private val onDetail: (Long) -> Unit,
) {
    private var view: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoHide = Runnable { hide() }
    val showing get() = view != null

    /** what: "문자" 또는 "카카오톡 메시지", reason: 가장 중요한 이유 한 줄 */
    fun show(id: Long, sender: String, what: String, reason: String) {
        hide()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(14))
            background = GradientDrawable().apply {
                setColor(0xFFFEF3C7.toInt())
                setStroke(dp(3), 0xFFF59E0B.toInt())
                cornerRadius = dp(22).toFloat()
            }
            elevation = dp(8).toFloat()
        }
        card.addView(text("⚠️ 조심해야 할 ${what}가 왔어요", 22f, true))
        card.addView(text("$sender · $reason", 18f, false).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }, lp(top = 6))
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("🔍 자세히", 0xFFB45309.toInt(), 0xFFFFFFFF.toInt()) { hide(); onDetail(id) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })
        row.addView(button("닫기", 0xFFFFFFFF.toInt(), 0xFF78350F.toInt()) { hide() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row, lp(top = 12))
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // 카드 밖의 터치는 뒤의 앱으로 보낸다
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            // 아래쪽 탐색 막대(홈·뒤로)에 겹치지 않게 조금 띄운다
            y = dp(56)
            horizontalMargin = 0f
        }
        val wrap = LinearLayout(ctx).apply {
            setPadding(dp(12), 0, dp(12), 0)
            addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        wm.addView(wrap, p)
        view = wrap
        handler.postDelayed(autoHide, SHOW_MS)
    }

    fun hide() {
        handler.removeCallbacks(autoHide)
        view?.let { wm.removeView(it) }
        view = null
    }

    private fun button(s: String, bg: Int, fg: Int, onClick: () -> Unit) = Button(ctx).apply {
        text = s
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(fg)
        background = GradientDrawable().apply {
            setColor(bg)
            setStroke(dp(2), 0xFFD97706.toInt())
            cornerRadius = dp(16).toFloat()
        }
        minHeight = dp(52)
        setOnClickListener { onClick() }
    }

    private fun text(s: String, sp: Float, bold: Boolean) = TextView(ctx).apply {
        text = s
        setTextColor(0xFF78350F.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setLineSpacing(0f, 1.15f)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun lp(top: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    companion object {
        /** 저절로 사라질 때까지 (읽고 누를 시간을 넉넉히) */
        const val SHOW_MS = 10_000L
    }
}
