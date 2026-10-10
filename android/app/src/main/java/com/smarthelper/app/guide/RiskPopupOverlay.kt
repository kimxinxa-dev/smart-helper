package com.smarthelper.app.guide

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.smarthelper.app.Look
import com.smarthelper.app.Look.Companion.INK
import com.smarthelper.app.Look.Companion.LINE
import com.smarthelper.app.Look.Companion.MUTED
import com.smarthelper.app.Look.Companion.RED
import com.smarthelper.app.Look.Companion.RED_DARK
import com.smarthelper.app.Look.Companion.SOFT

/**
 * 🚨 위험한 문자·메시지를 받는 순간 지금 보던 화면 위에 띄우는 팝업 (알림과 함께).
 * 어두운 배경 위 가운데 흰 카드: 빨간 느낌표 배지, 보낸 사람, 가장 중요한 이유 한 줄, [왜 위험한지 보기] · [알겠어요].
 */
class RiskPopupOverlay(
    private val ctx: Context,
    private val wm: WindowManager,
    private val onDetail: (Long) -> Unit,
    /** 사용자가 팝업을 닫은 뒤 (팝업이 화면을 덮는 동안 뒤의 앱 화면은 읽을 수 없어서, 닫히면 다시 살펴본다) */
    private val onClosed: () -> Unit = {},
) {
    private var view: View? = null
    val showing get() = view != null
    private val ui = Look(ctx)

    /** what: "문자" 또는 "카카오톡 메시지", reason: 가장 중요한 이유 한 줄 */
    fun show(id: Long, sender: String, what: String, reason: String, hasLink: Boolean) {
        hide()
        val back = FrameLayout(ctx).apply {
            setBackgroundColor(0xC70F172A.toInt())
            isClickable = true // 뒤의 화면이 눌리지 않게 막는다 (버튼으로만 닫는다)
        }
        val card = ui.card()
        card.addView(ui.badge("!", RED, 0xFFFFFFFF.toInt(), ring = 0xFFFEE2E2.toInt()))
        card.addView(ui.text("방금 온 ${what}가\n위험해요!", 28f, true, RED_DARK), ui.lp(top = 16))
        card.addView(ui.pill("$sender 님이 보냈어요", 18f, SOFT, MUTED), ui.lp(top = 14, w = FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })
        card.addView(ui.text(reason, 20f, true, INK), ui.lp(top = 16))
        card.addView(ui.text(if (hasLink) "링크를 누르지 마세요. 답장하지 마세요." else "답장하지 마세요. 돈·인증번호를 보내지 마세요.", 18f, false, MUTED), ui.lp(top = 8))
        card.addView(ui.primary("왜 위험한지 보기", RED, RED_DARK) { hide(); onDetail(id) }, ui.lp(top = 24))
        card.addView(ui.secondary("알겠어요", 0xFFFFFFFF.toInt(), INK, LINE) { hide(); onClosed() }, ui.lp(top = 10))
        back.addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            leftMargin = ui.dp(20); rightMargin = ui.dp(20)
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
}
