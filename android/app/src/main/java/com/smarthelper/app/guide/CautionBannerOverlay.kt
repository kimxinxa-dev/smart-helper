package com.smarthelper.app.guide

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import com.smarthelper.app.Look
import com.smarthelper.app.Look.Companion.AMBER
import com.smarthelper.app.Look.Companion.AMBER_DARK
import com.smarthelper.app.Look.Companion.INK
import com.smarthelper.app.Look.Companion.LINE
import com.smarthelper.app.Look.Companion.MUTED

/**
 * ⚠️ 주의 단계 메시지를 받으면 화면 아래쪽에 띄우는 작은 카드 (🚨 위험 팝업보다 가볍게).
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
    private val ui = Look(ctx)

    /** what: "문자" 또는 "카카오톡 메시지", reason: 가장 중요한 이유 한 줄 */
    fun show(id: Long, sender: String, what: String, reason: String) {
        hide()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(18), ui.dp(18), ui.dp(18), ui.dp(16))
            background = ui.round(26, 0xFFFFFBEB.toInt(), stroke = 0xFFFDE68A.toInt(), strokeDp = 2)
            elevation = ui.dp(12).toFloat()
        }
        // 위: 노란 느낌표 배지 + 제목·보낸 사람과 이유
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(ui.badge("!", AMBER, 0xFFFFFFFF.toInt(), sizeDp = 52), LinearLayout.LayoutParams(ui.dp(52), ui.dp(52)))
        val words = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        words.addView(ui.text("조심해야 할 ${what}가 왔어요", 21f, true, INK, center = false))
        words.addView(ui.text("$sender · $reason", 18f, false, MUTED, center = false).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }, ui.lp(top = 2))
        head.addView(words, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = ui.dp(14) })
        card.addView(head)
        // 아래: [자세히] · [닫기]
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(ui.primary("자세히", AMBER, AMBER_DARK, sp = 19f, minDp = 54) { hide(); onDetail(id) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = ui.dp(8) })
        row.addView(ui.secondary("닫기", 0xFFFFFFFF.toInt(), INK, LINE, sp = 19f, minDp = 54) { hide() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row, ui.lp(top = 14))
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // 카드 밖의 터치는 뒤의 앱으로 보낸다
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            // 아래쪽 탐색 막대(홈·뒤로)에 겹치지 않게 조금 띄운다
            y = ui.dp(56)
        }
        val wrap = LinearLayout(ctx).apply {
            // 그림자가 잘리지 않게 둘레에 여백
            setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
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

    companion object {
        /** 저절로 사라질 때까지 (읽고 누를 시간을 넉넉히) */
        const val SHOW_MS = 10_000L
    }
}
