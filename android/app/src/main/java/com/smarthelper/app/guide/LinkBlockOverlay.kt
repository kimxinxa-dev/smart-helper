package com.smarthelper.app.guide

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.smarthelper.app.Look
import com.smarthelper.app.Look.Companion.AMBER
import com.smarthelper.app.Look.Companion.INK
import com.smarthelper.app.Look.Companion.LINE
import com.smarthelper.app.Look.Companion.MUTED
import com.smarthelper.app.Look.Companion.RED
import com.smarthelper.app.Look.Companion.RED_DARK
import com.smarthelper.app.guard.LinkGuard

/**
 * 위험한 주소가 브라우저에서 열리면 화면 전체를 가리는 경고. "안전하게 나가기"를 크게, "그래도 볼래요"는 작게.
 * "그래도 볼래요"를 누르면 바로 열어 주지 않고 확인 창으로 한 번 더 묻는다 (실수로 누르는 일을 막는다).
 */
class LinkBlockOverlay(
    private val ctx: Context,
    private val wm: WindowManager,
    private val onExit: () -> Unit,
    private val onStay: (String) -> Unit,
    /** 확인 창을 띄울 때 (음성으로 한 번 더 알려 준다) */
    private val onConfirmAsk: () -> Unit = {},
) {
    private var view: View? = null
    val showing get() = view != null
    private val ui = Look(ctx)

    fun show(d: LinkGuard.Danger) {
        hide()
        val root = FrameLayout(ctx)
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(ui.dp(28), ui.dp(24), ui.dp(28), ui.dp(24))
            background = ui.backdrop(0xFAB91C1C.toInt(), 0xFA450A0A.toInt())
            isClickable = true // 뒤의 웹 페이지가 눌리지 않게 막는다
        }
        root.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        col.addView(ui.badge("!", WHITE, RED_DARK, sizeDp = 96, ring = 0x33FFFFFF))
        col.addView(ui.text("위험한 사이트예요", 32f, true, WHITE), ui.lp(top = 20))
        col.addView(ui.pill(d.host, 20f, 0x26FFFFFF, WHITE), ui.lp(top = 14, w = LinearLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })
        col.addView(ui.text(d.reason, 21f, true, WHITE), ui.lp(top = 22))
        col.addView(ui.text("카드번호·비밀번호·인증번호를 넣지 마세요.\n앱을 설치하라고 하면 절대 설치하지 마세요.", 19f, false, 0xE6FFFFFF.toInt()), ui.lp(top = 10))
        col.addView(ui.primary("🛡️ 안전하게 나가기", WHITE, 0xFFF1F5F9.toInt(), fg = RED_DARK, sp = 24f, minDp = 72) { hide(); onExit() }, ui.lp(top = 34))
        col.addView(ui.quietLink("그래도 볼래요 (위험할 수 있어요)", 0xCCFFFFFF.toInt()) {
            root.addView(confirm(d), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            onConfirmAsk()
        }, ui.lp(top = 6))
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS }
        wm.addView(root, p)
        view = root
    }

    fun hide() {
        view?.let { wm.removeView(it) }
        view = null
    }

    /** "그래도 볼래요" 확인 창: 어두운 배경 위 흰 카드. 안전한 쪽(나가기)을 크게, 들어가기는 작게 */
    private fun confirm(d: LinkGuard.Danger): View {
        val back = FrameLayout(ctx).apply {
            setBackgroundColor(0xCC0F172A.toInt())
            isClickable = true
        }
        val card = ui.card()
        card.addView(ui.badge("?", AMBER, WHITE, sizeDp = 76, ring = 0xFFFEF3C7.toInt()))
        card.addView(ui.text("정말 들어가시겠어요?", 27f, true, RED_DARK), ui.lp(top = 14))
        card.addView(ui.text("한 번 더 위험할 수 있어요.\n사기 사이트라면 카드번호·비밀번호를 빼앗기거나 나쁜 앱이 설치될 수 있어요.", 19f, false, INK), ui.lp(top = 12))
        card.addView(ui.text("모르겠으면 가족에게 먼저 물어보세요.", 18f, true, MUTED), ui.lp(top = 10))
        card.addView(ui.primary("🛡️ 아니요, 나갈게요", RED, RED_DARK, sp = 23f, minDp = 68) { hide(); onExit() }, ui.lp(top = 22))
        card.addView(ui.secondary("네, 그래도 볼게요", WHITE, MUTED, LINE, sp = 18f, minDp = 54) { hide(); onStay(d.host) }, ui.lp(top = 10))
        back.addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            leftMargin = ui.dp(20); rightMargin = ui.dp(20)
        })
        return back
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
