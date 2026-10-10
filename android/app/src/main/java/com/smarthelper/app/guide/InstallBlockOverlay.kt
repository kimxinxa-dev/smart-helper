package com.smarthelper.app.guide

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import com.smarthelper.app.Look
import com.smarthelper.app.Look.Companion.AMBER_DARK

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
    private val ui = Look(ctx)

    /** detail: "오후 2:03 · 010-1234-5678 님이 보낸 문자" 같은 위험 메시지 설명, family: 가족 이름(없으면 null) */
    fun show(detail: String, family: String?) {
        hide()
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(ui.dp(28), ui.dp(24), ui.dp(28), ui.dp(24))
            background = ui.backdrop(0xFAC2410C.toInt(), 0xFA431407.toInt())
            isClickable = true // 뒤의 설치 화면이 눌리지 않게 막는다
        }
        col.addView(ui.badge("✋", WHITE, AMBER_DARK, sizeDp = 96, ring = 0x33FFFFFF))
        col.addView(ui.text("잠깐만요!", 34f, true, WHITE), ui.lp(top = 18))
        col.addView(ui.text("방금 받은 문자 때문에\n설치하시는 건가요?", 25f, true, WHITE), ui.lp(top = 6))
        col.addView(ui.pill(detail, 18f, 0x26FFFFFF, WHITE), ui.lp(top = 16))
        col.addView(ui.text("사기 문자가 시키는 앱은 휴대폰을 몰래 조종하거나 돈을 빼 갈 수 있어요.", 19f, false, 0xE6FFFFFF.toInt()), ui.lp(top = 12))
        col.addView(ui.primary("🛡️ 안전하게 그만두기", WHITE, 0xFFF1F5F9.toInt(), fg = 0xFF9A3412.toInt(), sp = 24f, minDp = 72) { hide(); onQuit() }, ui.lp(top = 28))
        col.addView(ui.secondary(if (family != null) "📞 $family 님께 전화하기" else "📞 118 상담센터에 전화하기", 0x26FFFFFF, WHITE, 0x66FFFFFF, sp = 20f, minDp = 60) { onFamily() }, ui.lp(top = 12))
        col.addView(ui.quietLink("그래도 진행 (5분 동안 다시 묻지 않아요)", 0xCCFFFFFF.toInt()) { hide(); onProceed() }, ui.lp(top = 4))
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

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
