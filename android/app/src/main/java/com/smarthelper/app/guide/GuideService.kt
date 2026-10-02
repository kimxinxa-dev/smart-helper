package com.smarthelper.app.guide

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.util.Locale

/**
 * 화면 안내 서비스(접근성 서비스).
 * 안내 중일 때만 문자 앱 화면에서 버튼 위치를 찾아 테두리·화살표로 표시한다. 화면 내용은 저장하지 않는다.
 * 사용자가 설정 > 접근성에서 직접 켜야 한다.
 */
class GuideService : AccessibilityService(), TextToSpeech.OnInitListener {

    companion object {
        /** 켜져 있으면 연결된 서비스, 꺼져 있으면 null */
        var instance: GuideService? = null
            private set
        private const val TIMEOUT_MS = 3 * 60 * 1000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var overlay: GuideOverlay
    private var tts: TextToSpeech? = null
    private var steps: List<Step>? = null
    /** 받는 사람 이름과 번호(숫자만). 엉뚱한 사람에게 보내지 않도록 대화창에서 확인한다. */
    private var toName = ""
    private var toDigits = ""
    private var recipientChecked = false
    private var current = -1
    private var spoken = -1
    private var misses = 0

    private val scanTask = Runnable { scan() }
    private val tick = object : Runnable {
        override fun run() { if (steps != null) { scan(); handler.postDelayed(this, 1000) } }
    }
    private val timeout = Runnable { stop("안내 시간이 지나서 화면 보기를 멈췄어요. 필요하면 다시 불러 주세요.") }

    override fun onServiceConnected() {
        instance = this
        // 접근성 서비스 자신의 창 관리자여야 다른 앱 위에 그릴 수 있는 표식(token)이 붙는다
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        overlay = GuideOverlay(this, wm) { stop("화면 안내를 멈췄어요.") }
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) { tts?.language = Locale.KOREAN; tts?.setSpeechRate(0.85f) }
    }

    /**
     * 안내 시작. 문자 앱은 호출하는 쪽에서 연다.
     * 서비스 연결이 잠시 끊긴 상태면(다른 접근성 도구가 끼어든 경우 등) false.
     */
    fun begin(guide: List<Step>, name: String, number: String): Boolean {
        toName = name; toDigits = number.filter { it.isDigit() }; recipientChecked = false
        try {
            overlay.show()
        } catch (e: WindowManager.BadTokenException) {
            android.util.Log.w("SmartHelper", "화면 안내 연결이 끊긴 상태", e)
            if (instance === this) instance = null
            return false
        }
        steps = guide; current = -1; spoken = -1; misses = 0
        overlay.point(null, "문자 화면을 여는 중이에요...", 1, guide.size)
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(tick, 1500)
        handler.postDelayed(timeout, TIMEOUT_MS)
        return true
    }

    fun stop(message: String?) {
        steps = null
        handler.removeCallbacksAndMessages(null)
        overlay.hide()
        message?.let { say(it) }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val s = steps ?: return
        // 마지막 단계에서 보내기 버튼을 누르면 완료
        if (e.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && current == s.lastIndex && PhotoGuide.isSend(e.source)) {
            stop("🎉 사진을 보냈어요! 잘 하셨어요. 화면 보기도 끝냈어요.")
            return
        }
        handler.removeCallbacks(scanTask)
        handler.postDelayed(scanTask, 250)
    }

    /** 지금 화면에서 "찾을 수 있는 가장 뒤 단계"를 찾아 표시한다 */
    private fun scan() {
        val s = steps ?: return
        val root = rootInActiveWindow ?: return
        if (PhotoGuide.unsupported(root)) {
            stop("이 휴대폰은 사진 문자를 보낼 수 없게 설정되어 있어요. '확인'을 누르고, 통신사에 사진 문자(MMS)를 물어보세요.")
            return
        }
        for (i in s.indices.reversed()) {
            val node = s[i].find(root) ?: continue
            // 대화창이 처음 보이면, 받는 사람이 맞는지부터 확인한다 (사기 번호 대화창이 열려 있을 수도 있다)
            if (!recipientChecked) {
                if (!isRecipient(root)) {
                    stop("지금 열린 대화는 $toName 님이 아니에요. 잘못 보내지 않도록 안내를 멈췄어요. 다시 말씀해 주시면 처음부터 열어 드릴게요.")
                    return
                }
                recipientChecked = true
            }
            val r = Rect().also { node.getBoundsInScreen(it) }
            current = i; misses = 0
            // 첫 단계에서는 받는 사람을 다시 알려 준다 (위쪽 이름이 안내 띠에 가려질 수 있다)
            val msg = if (i == 0) "받는 사람: $toName 님\n${s[i].say}" else s[i].say
            overlay.point(r, msg, i + 1, s.size)
            if (spoken != i) { spoken = i; say(if (i == 0) "$toName 님께 보낼 거예요. ${s[i].say}" else s[i].say) }
            return
        }
        // 눌러야 할 곳을 못 찾으면(다른 화면으로 감) 몇 번 기다렸다가 말풍선으로만 알려 준다
        if (++misses == 3) overlay.point(null, "문자 보내는 화면으로 돌아가 주세요. 뒤로 가기를 누르면 돼요.", maxOf(current, 0) + 1, s.size)
    }

    /** 화면 어딘가(보통 맨 위 제목)에 받는 사람 이름이나 번호가 있는지 */
    private fun isRecipient(root: android.view.accessibility.AccessibilityNodeInfo) = Nodes.first(root) { n ->
        val t = n.text?.toString().orEmpty()
        (toName.isNotEmpty() && t.contains(toName)) ||
            (toDigits.length >= 9 && localDigits(t).contains(localDigits(toDigits)))
    } != null

    /** "+82 10-1234-5678" → "01012345678" 처럼 국내 번호 숫자만 남긴다 (끝자리만 비교하면 02-1234-5678 과 헷갈린다) */
    private fun localDigits(s: String): String {
        val d = s.filter { it.isDigit() }
        return if (d.startsWith("82")) "0" + d.drop(2) else d
    }

    private fun say(t: String) {
        tts?.speak(t.replace(Regex("[🎉]"), ""), TextToSpeech.QUEUE_FLUSH, null, "guide")
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (steps != null) stop(null)
        if (instance === this) instance = null
        tts?.shutdown()
        return super.onUnbind(intent)
    }
}
