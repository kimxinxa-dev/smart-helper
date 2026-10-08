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
        /** 위험 링크 차단: 이 브라우저들의 주소창만 본다 */
        val BROWSERS = setOf("com.android.chrome", "com.sec.android.app.sbrowser", "com.naver.whale", "org.mozilla.firefox", "com.microsoft.emmx")
        private val URL_BAR_IDS = arrayOf("url_bar", "location_bar_edit_text", "url_field", "mozac_browser_toolbar_url_view", "addressbarEdit")
        /** 위험 링크 차단: 주소창 없이 앱 안에서 링크를 여는 앱 (누른 말풍선 + 앱 안 웹 화면 제목 줄을 본다) */
        val IN_APP = setOf("com.kakao.talk")
        /** 개발용: 디버그 앱에서는 연습용 퍼즐의 '가짜 카카오톡' 화면도 같은 방식으로 본다 */
        private const val FAKE_IN_APP = "com.smarthelper.practicepuzzle"
    }

    private var inApp = IN_APP
    /** 앱 안 웹 화면에서 마지막으로 검사한 주소들 */
    private var lastInApp = ""
    private var inAppPending = false
    private val inAppTask = Runnable { inAppPending = false; checkInAppBrowser() }
    /** 지금 띄운 링크 경고가 카카오톡 같은 앱 안에서 막은 것인지 (나가기 방법이 다르다) */
    private var blockedInApp = false

    private lateinit var block: LinkBlockOverlay
    private lateinit var installBlock: InstallBlockOverlay
    private lateinit var riskPopup: RiskPopupOverlay
    private var installPending = false
    private val installTask = Runnable { installPending = false; checkInstall() }
    /** 마지막으로 검사한 주소 (같은 주소를 계속 검사하지 않는다) */
    private var lastUrl = ""
    private var browserPending = false
    private val browserTask = Runnable { browserPending = false; checkBrowser() }

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
        block = LinkBlockOverlay(this, wm, onExit = ::leaveRiskySite, onStay = { host ->
            com.smarthelper.app.guard.LinkGuard.allow(host)
            say("알겠어요. 개인정보나 돈을 요구하면 바로 나가세요.")
        })
        installBlock = InstallBlockOverlay(this, wm, onQuit = ::quitInstall, onFamily = ::callFamily, onProceed = {
            com.smarthelper.app.guard.GuardStore.snoozeInstall(this, com.smarthelper.app.guard.InstallGate.snoozeUntil(System.currentTimeMillis()))
            say("알겠어요. 5분 동안은 다시 묻지 않을게요. 개인정보나 돈을 요구하는 앱이면 바로 그만두세요.")
        })
        riskPopup = RiskPopupOverlay(this, wm, onDetail = { id ->
            startActivity(android.content.Intent(this, com.smarthelper.app.guard.WarningActivity::class.java)
                .putExtra(com.smarthelper.app.guard.WarningActivity.EXTRA_ID, id)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        tts = TextToSpeech(this, this)
        // 개발용(디버그 앱에서만): PC 에서 신호를 보내면 지금 화면의 요소 목록을 기록한다.
        //   adb shell am broadcast -a com.smarthelper.app.DUMP_SCREEN
        // 화면이 계속 움직여 uiautomator 로 못 읽는 앱(시계 등)이나 새 휴대폰의 버튼 이름을 조사할 때 쓴다.
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            inApp = IN_APP + FAKE_IN_APP
            serviceInfo = serviceInfo.apply { packageNames = (packageNames.orEmpty().toList() + FAKE_IN_APP).toTypedArray() }
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
        handler.removeCallbacksAndMessages(null); browserPending = false; installPending = false
        handler.postDelayed(tick, 1500)
        handler.postDelayed(timeout, TIMEOUT_MS)
        return true
    }

    fun stop(message: String?) {
        guide = null
        handler.removeCallbacksAndMessages(null); browserPending = false; installPending = false
        overlay.hide()
        message?.let { say(it) }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        // 위험 링크 차단: 안내 중이 아니어도 브라우저 주소가 바뀌면 검사한다
        // 설치 차단: 위험 시간대일 때만 설치·설정 화면을 본다 (평소에는 아무것도 하지 않음)
        val pkg = e.packageName?.toString().orEmpty()
        if (!installPending && (pkg in com.smarthelper.app.guard.InstallGate.INSTALLERS || pkg.endsWith(".packageinstaller") || pkg in com.smarthelper.app.guard.InstallGate.SETTINGS) &&
            com.smarthelper.app.guard.InstallGate.inRiskWindow(System.currentTimeMillis(), com.smarthelper.app.guard.GuardStore.lastRisk(this)?.at)) {
            installPending = true
            handler.postDelayed(installTask, 400)
        }
        if (e.packageName?.toString() in BROWSERS && !browserPending) {
            // 페이지가 바뀌는 동안 신호가 아주 많이 오므로, 0.7초에 한 번만 본다
            browserPending = true
            handler.postDelayed(browserTask, 700)
        }
        if (pkg in inApp) {
            // 카카오톡: 링크를 누르는 순간(페이지가 열리기 전에) 누른 말풍선 글자를 보고,
            // 앱 안 웹 화면이 열리거나 바뀌면 제목 줄의 주소를 본다. 채팅 내용은 저장하지 않는다.
            if (e.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) checkInAppClick(e)
            else if (!inAppPending && (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.className?.contains("WebView") == true)) {
                inAppPending = true
                handler.postDelayed(inAppTask, 700)
            }
        }
        val g = guide ?: return
        // 마지막 단계에서 완료 버튼(보내기, 확대, 와이파이 이름 등)을 누르면 끝
        if (e.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && current == g.steps.lastIndex && (g.isDone(e.source) || clickedText(e) in g.doneTexts)) {
            stop(g.doneMsg)
            return
        }
        handler.removeCallbacks(scanTask)
        handler.postDelayed(scanTask, 250)
    }

    /** 브라우저 주소창의 주소가 위험하면 화면 전체를 가리는 경고를 띄운다. 주소는 저장하지 않는다. */
    private fun checkBrowser() {
        // 브라우저가 자기 안내 창(팝업)을 띄우면 그 창이 앞에 오므로, 화면의 모든 창에서 주소창을 찾는다
        val bar = urlBar() ?: return
        if (bar.isFocused) return // 주소를 쓰는 중
        val url = bar.text?.toString()?.trim().orEmpty()
        if (url.isEmpty() || url == lastUrl || block.showing) return
        val d = com.smarthelper.app.guard.LinkGuard.check(this, url)
        if (d == null) { lastUrl = url; return }
        if (guide != null) stop(null)
        try {
            block.show(d)
        } catch (e: Exception) {
            // 경고를 못 띄웠으면 이 주소를 '검사함'으로 표시하지 않고 다음 신호 때 다시 시도한다
            android.util.Log.w("SmartHelper", "위험 링크 경고를 띄우지 못함: ${d.host}", e)
            return
        }
        lastUrl = url
        blockedInApp = false
        android.util.Log.i("SmartHelper", "위험 링크 차단: ${d.host}")
        say("위험한 사이트예요. ${d.reason} 안전하게 나가기를 눌러 주세요.")
    }

    /** 카카오톡 채팅방에서 누른 말풍선·미리보기 카드에 위험한 링크가 있으면 페이지가 열리기 전에 막는다 */
    private fun checkInAppClick(e: AccessibilityEvent) {
        val texts = ArrayList<String>()
        texts += e.text.map { it.toString() }
        e.contentDescription?.let { texts += it.toString() }
        // 미리보기 카드처럼 글자가 안쪽에 나뉘어 있으면 누른 칸 안의 글자를 조금만 모은다
        e.source?.let { src ->
            var budget = 30
            Nodes.first(src) { n ->
                n.text?.let { texts += it.toString() }
                n.contentDescription?.let { texts += it.toString() }
                --budget <= 0
            }
        }
        blockInApp(texts)
    }

    /** 카카오톡 안 웹 화면: 웹 페이지 내용은 보지 않고, 웹 화면 바깥(제목 줄)의 글자만 본다 */
    private fun checkInAppBrowser() {
        if (block.showing) return
        for (w in windows) {
            val root = w.root ?: continue
            if (root.packageName?.toString() !in inApp) continue
            val texts = ArrayList<String>()
            var web = false
            val queue = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>().apply { add(root) }
            while (queue.isNotEmpty()) {
                val n = queue.removeFirst()
                if (n.className?.toString() == "android.webkit.WebView") {
                    web = true
                    // 웹 화면 자체의 이름(주소나 페이지 제목)만 보고, 안쪽 페이지 내용은 훑지 않는다
                    n.text?.let { texts += it.toString() }
                    n.contentDescription?.let { texts += it.toString() }
                    continue
                }
                n.text?.let { texts += it.toString() }
                for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
            }
            if (!web) { lastInApp = ""; continue }
            val key = texts.joinToString("|")
            if (key == lastInApp) return
            if (blockInApp(texts)) return
            lastInApp = key
        }
    }

    /** 글자들에서 링크를 찾아 위험하면 경고. 경고를 띄웠으면 true */
    private fun blockInApp(texts: List<String>): Boolean {
        if (block.showing) return false
        val d = com.smarthelper.app.guard.InAppLink.links(texts).firstNotNullOfOrNull { com.smarthelper.app.guard.LinkGuard.check(this, it) } ?: return false
        if (guide != null) stop(null)
        try {
            block.show(d)
        } catch (e: Exception) {
            android.util.Log.w("SmartHelper", "앱 안 위험 링크 경고를 띄우지 못함: ${d.host}", e)
            return false
        }
        blockedInApp = true
        android.util.Log.i("SmartHelper", "앱 안 위험 링크 차단: ${d.host}")
        say("위험한 링크예요. ${d.reason} 안전하게 나가기를 눌러 주세요.")
        return true
    }

    /** 앱 안 웹 화면이 떠 있는지 (나가기 때 뒤로 가기를 할지 정한다) */
    private fun inAppWebOpen() = windows.mapNotNull { it.root }.filter { it.packageName?.toString() in inApp }.any { r ->
        Nodes.first(r) { it.className?.toString() == "android.webkit.WebView" } != null
    }

    /** 설치 화면이나 "알 수 없는 앱 설치" 화면이면, 위험 시간대·유예를 확인해 전체 화면 경고를 띄운다 */
    private fun checkInstall() {
        if (installBlock.showing) return
        val roots = windows.mapNotNull { it.root }.filter {
            val p = it.packageName?.toString().orEmpty()
            p in com.smarthelper.app.guard.InstallGate.INSTALLERS || p.endsWith(".packageinstaller") || p in com.smarthelper.app.guard.InstallGate.SETTINGS
        }
        val now = System.currentTimeMillis()
        val risk = com.smarthelper.app.guard.GuardStore.lastRisk(this)
        for (r in roots) {
            val texts = ArrayList<String>()
            Nodes.first(r) { n -> n.text?.toString()?.let { texts += it }; false } // 화면 글자만 모은다 (저장하지 않음)
            val screen = com.smarthelper.app.guard.InstallGate.screen(r.packageName?.toString(), texts)
            if (!com.smarthelper.app.guard.InstallGate.shouldBlock(screen, now, risk?.at, com.smarthelper.app.guard.GuardStore.installSnoozedUntil(this))) continue
            if (guide != null) stop(null)
            val time = java.text.SimpleDateFormat("a h:mm", java.util.Locale.KOREAN).format(java.util.Date(risk!!.at))
            val what = if (risk.source == "문자") "문자" else "${risk.source} 메시지"
            val detail = "$time · ${risk.sender} 님이 보낸 $what"
            val family = com.smarthelper.app.guard.GuardStore.family(this)?.first
            try {
                installBlock.show(detail, family)
            } catch (e: Exception) {
                android.util.Log.w("SmartHelper", "설치 경고를 띄우지 못함", e)
                return
            }
            android.util.Log.i("SmartHelper", "설치 차단 경고: $screen")
            say("잠깐만요! 방금 받은 문자 때문에 설치하시는 건가요? $time 에 ${risk.sender} 님이 보낸 ${what}가 위험했어요. 안전하게 그만두기를 눌러 주세요.")
            return
        }
    }

    /**
     * 🚨 위험한 문자·메시지를 받는 순간 지금 화면 위에 팝업을 띄우고 읽어 준다 (알림은 따로 함께 간다).
     * 문자를 받으면 앱이 스스로 화면을 띄울 수 없어서(안드로이드 10부터), 접근성 서비스의 겹쳐 그리기 창을 쓴다.
     * 띄우지 못하면 false (알림만 남는다).
     */
    fun showRiskPopup(id: Long, sender: String, what: String, reason: String, hasLink: Boolean): Boolean {
        if (!::riskPopup.isInitialized) return false
        // 링크·설치 차단 화면이 떠 있으면 그 위에 겹치지 않는다 (그쪽이 더 급하다)
        if (block.showing || installBlock.showing) return false
        try {
            riskPopup.show(id, sender, what, reason, hasLink)
        } catch (e: Exception) {
            android.util.Log.w("SmartHelper", "위험 문자 팝업을 띄우지 못함", e)
            return false
        }
        android.util.Log.i("SmartHelper", "위험 문자 팝업: $what")
        say("방금 온 ${what}가 위험해요! ${if (hasLink) "링크를 누르지 마세요." else "답장하지 마세요."} $reason")
        return true
    }

    /** "안전하게 그만두기": 설치 화면을 닫고 홈 화면으로 */
    private fun quitInstall() {
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME); say("잘 하셨어요. 설치를 그만뒀어요.") }, 400)
    }

    /** "가족에게 전화하기": 전화 앱에 번호만 띄운다 (전화 권한 없이 ACTION_DIAL). 가족이 없으면 118 */
    private fun callFamily() {
        val num = com.smarthelper.app.guard.GuardStore.family(this)?.second ?: "118"
        installBlock.hide()
        startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$num"))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        say("번호를 띄워 두었어요. 통화 버튼을 눌러 물어보세요.")
    }

    /**
     * 브라우저 주소창. 웹 페이지 전체를 훑으면 브라우저가 느려지므로, 이름표(id)로 바로 찾는다.
     * 브라우저가 자기 팝업을 띄우면 그 창이 앞에 오므로 화면의 모든 브라우저 창에서 찾는다.
     */
    private fun urlBar(): android.view.accessibility.AccessibilityNodeInfo? =
        windows.mapNotNull { it.root }.filter { it.packageName?.toString() in BROWSERS }.firstNotNullOfOrNull { r ->
            val pkg = r.packageName.toString()
            URL_BAR_IDS.firstNotNullOfOrNull { id -> r.findAccessibilityNodeInfosByViewId("$pkg:id/$id").firstOrNull() }
        }

    /** "안전하게 나가기": 뒤로 가고, 그래도 위험한 주소에 머물러 있으면 홈 화면으로 */
    private fun leaveRiskySite() {
        if (blockedInApp) {
            // 카카오톡: 말풍선을 누르자마자 막았으면 웹 화면이 막 열리는 중일 수 있어 잠시 뒤에 확인하고, 열려 있으면 닫는다.
            // 열려 있지 않으면 채팅방에 그대로 둔다 (뒤로 가기를 하면 채팅방이 닫힌다)
            blockedInApp = false
            handler.postDelayed({
                if (inAppWebOpen()) performGlobalAction(GLOBAL_ACTION_BACK)
                lastInApp = ""
                say("안전하게 나왔어요. 잘 하셨어요.")
            }, 700)
            return
        }
        val risky = lastUrl
        lastUrl = ""
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({
            val url = urlBar()?.text?.toString().orEmpty()
            val h = com.smarthelper.app.guard.LinkGuard.host(url)
            if (h != null && h == com.smarthelper.app.guard.LinkGuard.host(risky)) performGlobalAction(GLOBAL_ACTION_HOME)
            say("안전하게 나왔어요. 잘 하셨어요.")
        }, 1200)
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
        if (::block.isInitialized) block.hide()
        if (::installBlock.isInitialized) installBlock.hide()
        if (::riskPopup.isInitialized) riskPopup.hide()
        if (instance === this) instance = null
        tts?.shutdown()
        return super.onUnbind(intent)
    }
}
