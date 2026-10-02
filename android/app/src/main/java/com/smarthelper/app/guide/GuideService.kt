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
 * 안내 중일 때만 문자·설정 앱 화면에서 버튼 위치를 찾아 테두리·화살표로 표시한다. 화면 내용은 저장하지 않는다.
 * 사용자가 설정 > 접근성에서 직접 켜야 한다.
 */
class GuideService : AccessibilityService(), TextToSpeech.OnInitListener {

    companion object {
        /** 켜져 있으면 연결된 서비스, 꺼져 있으면 null */
        var instance: GuideService? = null
            private set
        private const val TIMEOUT_MS = 3 * 60 * 1000L
        private const val DUMP_ACTION = "com.smarthelper.app.DUMP_SCREEN"
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var overlay: GuideOverlay
    private var tts: TextToSpeech? = null
    private var guide: Guide? = null
    /** 받는 사람 이름과 번호(숫자만). 엉뚱한 사람에게 보내지 않도록 대화창에서 확인한다. */
    private var toName = ""
    private var toDigits = ""
    private var recipientChecked = false
    private var current = -1
    private var spoken = ""
    private var misses = 0
    private var leaveTries = 0

    private val scanTask = Runnable { scan() }
    private val tick = object : Runnable {
        override fun run() { if (guide != null) { scan(); handler.postDelayed(this, 1000) } }
    }
    private val timeout = Runnable { stop("안내 시간이 지나서 화면 보기를 멈췄어요. 필요하면 다시 불러 주세요.") }

    override fun onServiceConnected() {
        instance = this
        // 접근성 서비스 자신의 창 관리자여야 다른 앱 위에 그릴 수 있는 표식(token)이 붙는다
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        overlay = GuideOverlay(this, wm) { stop("화면 안내를 멈췄어요.") }
        tts = TextToSpeech(this, this)
        // 개발용(디버그 앱에서만): PC 에서 신호를 보내면 지금 화면의 요소 목록을 기록한다.
        //   adb shell am broadcast -a com.smarthelper.app.DUMP_SCREEN
        // 화면이 계속 움직여 uiautomator 로 못 읽는 앱(시계 등)이나 새 휴대폰의 버튼 이름을 조사할 때 쓴다.
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            val filter = android.content.IntentFilter(DUMP_ACTION)
            if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(dumpReceiver, filter, RECEIVER_EXPORTED)
            else @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(dumpReceiver, filter)
        }
    }

    /**
     * 지금 사용자가 보는 앱 화면의 뿌리.
     * 앱이 띄운 말풍선·팝업 때문에 '앞 창'을 못 받을 때는, 화면에 떠 있는 앱 창 중 맨 위 것을 쓴다.
     */
    private fun screenRoot(): android.view.accessibility.AccessibilityNodeInfo? =
        rootInActiveWindow ?: windows
            .filter { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION }
            .maxByOrNull { it.layer }?.root

    private val dumpReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context, i: android.content.Intent) {
            windows.forEach { w -> android.util.Log.d("SmartDump", "창: 종류=${w.type} 층=${w.layer} 제목=${w.title} 앱=${w.root?.packageName}") }
            val root = screenRoot() ?: return android.util.Log.d("SmartDump", "화면 없음").let { }
            android.util.Log.d("SmartDump", "=== ${root.packageName}")
            fun walk(n: android.view.accessibility.AccessibilityNodeInfo, depth: Int) {
                val t = n.text?.toString().orEmpty(); val d = n.contentDescription?.toString().orEmpty(); val id = n.viewIdResourceName.orEmpty()
                if (t.isNotEmpty() || d.isNotEmpty() || (id.isNotEmpty() && n.isClickable) || n.isEditable || n.isCheckable) {
                    val r = Rect().also { n.getBoundsInScreen(it) }
                    android.util.Log.d("SmartDump", "${" ".repeat(depth)}$id | t=$t | d=$d | c=${n.isClickable} e=${n.isEditable} chk=${if (n.isCheckable) n.isChecked else "-"} | $r")
                }
                for (k in 0 until n.childCount) n.getChild(k)?.let { walk(it, depth + 1) }
            }
            walk(root, 0)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) { tts?.language = Locale.KOREAN; tts?.setSpeechRate(0.85f) }
    }

    /**
     * 안내 시작. 안내할 앱(문자·설정)은 호출하는 쪽에서 연다.
     * 서비스 연결이 잠시 끊긴 상태면(다른 접근성 도구가 끼어든 경우 등) false.
     */
    fun begin(g: Guide): Boolean {
        toName = g.recipient?.first.orEmpty(); toDigits = g.recipient?.second.orEmpty().filter { it.isDigit() }
        recipientChecked = g.recipient == null
        try {
            overlay.show()
        } catch (e: WindowManager.BadTokenException) {
            // 잠깐 끊긴 것일 수 있으니 서비스를 잊지는 않는다 (다시 연결되면 onServiceConnected 가 새로 불린다)
            android.util.Log.w("SmartHelper", "화면 안내 연결이 끊긴 상태", e)
            return false
        }
        guide = g; current = -1; spoken = ""; misses = 0; leaveTries = 0
        overlay.point(null, "화면을 여는 중이에요...", 1, g.steps.size)
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(tick, 1500)
        handler.postDelayed(timeout, TIMEOUT_MS)
        return true
    }

    fun stop(message: String?) {
        guide = null
        handler.removeCallbacksAndMessages(null)
        overlay.hide()
        message?.let { say(it) }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val g = guide ?: return
        // 마지막 단계에서 완료 버튼(보내기, 확대, 와이파이 이름 등)을 누르면 끝
        if (e.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && current == g.steps.lastIndex && (g.isDone(e.source) || clickedText(e) in g.doneTexts)) {
            stop(g.doneMsg)
            return
        }
        handler.removeCallbacks(scanTask)
        handler.postDelayed(scanTask, 250)
    }

    /** 누름 신호에 실려 온 버튼 글자 (창이 닫혀 버튼을 다시 읽을 수 없을 때 쓴다) */
    private fun clickedText(e: AccessibilityEvent): String =
        (e.text.joinToString(" ").ifBlank { e.contentDescription?.toString().orEmpty() }).trim()

    /** 지금 화면에서 "찾을 수 있는 가장 뒤 단계"를 찾아 표시한다 */
    private fun scan() {
        val g = guide ?: return
        val s = g.steps
        if (g.finished?.invoke() == true) { stop(g.doneMsg); return }
        val root = screenRoot() ?: return
        g.unsupported(root)?.let { stop(it); return }
        // 마지막 단계의 창이 닫혔으면(시간 선택 창 등) 정말 끝났는지 직접 확인한다
        val leave = g.leftLast
        if (leave != null && current == s.lastIndex && s.last().locate(root) == null) {
            if (leave(root)) { stop(g.doneMsg); return }
            // 창이 닫히는 중이면 목록이 아직 안 바뀌었을 수 있어 몇 번 더 본다
            if (++leaveTries < 4) return
            leaveTries = 0; current = -1; say(g.leftLastFail)
        }
        for (i in s.indices.reversed()) {
            val (node, text) = s[i].locate(root) ?: continue
            // 받는 사람이 보이는 단계가 처음 나오면, 맞는 사람인지부터 확인한다 (사기 번호 대화창이 열려 있을 수도 있다)
            if (!recipientChecked && i >= g.checkRecipientFrom) {
                if (!isRecipient(root)) {
                    stop(g.wrongRecipient ?: "지금 열린 대화는 $toName 님이 아니에요. 잘못 보내지 않도록 안내를 멈췄어요. 다시 말씀해 주시면 처음부터 열어 드릴게요.")
                    return
                }
                recipientChecked = true
            }
            // 보내기 단계였는데 첫 단계(빈 입력 칸)로 돌아왔다면 보낸 것이다
            if (g.doneWhenBackToStart && current == s.lastIndex && i == 0) { stop(g.doneMsg); return }
            val r = Rect().also { node.getBoundsInScreen(it) }
            current = i; misses = 0
            // 받는 사람을 확인한 단계에서는 이름을 다시 알려 준다 (위쪽 이름이 안내 띠에 가려질 수 있다)
            val who = i == g.checkRecipientFrom && g.recipient != null
            overlay.point(r, if (who) "받는 사람: $toName 님\n$text" else text, i + 1, s.size)
            if (spoken != text) { spoken = text; say(if (who) "$toName 님이 맞아요. $text" else text) }
            return
        }
        // 눌러야 할 곳을 못 찾으면(다른 화면으로 감) 몇 번 기다렸다가 말풍선으로만 알려 준다
        if (++misses == 5) { overlay.point(null, g.lost, maxOf(current, 0) + 1, s.size); say(g.lost) }
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
        try { unregisterReceiver(dumpReceiver) } catch (e: IllegalArgumentException) { /* 등록 안 됨(배포용 앱) */ }
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (guide != null) stop(null)
        if (instance === this) instance = null
        tts?.shutdown()
        return super.onUnbind(intent)
    }
}
