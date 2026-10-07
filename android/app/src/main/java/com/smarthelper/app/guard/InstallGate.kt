package com.smarthelper.app.guard

/**
 * "위험한 행동 순간 막기": 위험 메시지를 받은 직후 앱 설치를 시도하면 막을지 판단한다.
 * 안드로이드와 무관한 순수 Kotlin 이라 PC 에서 테스트한다.
 */
object InstallGate {
    /** 위험 시간대: 주의(MID) 이상 메시지를 받은 뒤 이 시간 동안 */
    const val RISK_WINDOW_MS = 30 * 60 * 1000L
    /** "그래도 진행"을 누른 뒤 같은 경고를 다시 띄우지 않는 시간 */
    const val SNOOZE_MS = 5 * 60 * 1000L

    /** 앱 설치 화면을 띄우는 시스템 앱 (삼성 등 다른 기기는 화면 글자로도 판단한다) */
    val INSTALLERS = setOf("com.google.android.packageinstaller", "com.android.packageinstaller", "com.samsung.android.packageinstaller")
    val SETTINGS = setOf("com.android.settings")

    private val INSTALL_WORDS = listOf("이 앱을 설치하시겠습니까", "이 앱을 설치할까요", "설치하시겠습니까", "Do you want to install")
    private val INSTALL_BUTTONS = setOf("설치", "Install")
    private val UNKNOWN_SOURCE_WORDS = listOf("알 수 없는 앱 설치", "이 출처 허용", "출처를 알 수 없는 앱", "알 수 없는 앱을 설치할 수 없도록", "Install unknown apps", "Allow from this source")

    enum class Screen { INSTALL, UNKNOWN_SOURCE }

    /**
     * 지금 화면이 설치 화면인지. 패키지명만 믿지 않고 화면 글자도 함께 본다.
     *  - 설치 화면: 설치 앱 + ("이 앱을 설치하시겠습니까" 같은 질문 또는 "설치" 버튼)
     *  - 알 수 없는 출처 허용 화면: 설정 앱 또는 설치 앱 + "알 수 없는 앱 설치"/"이 출처 허용"
     */
    fun screen(pkg: String?, texts: List<String>): Screen? {
        val p = pkg.orEmpty()
        val t = texts.map { it.trim() }.filter { it.isNotEmpty() }
        val installerish = p in INSTALLERS || p.endsWith(".packageinstaller")
        if ((installerish || p in SETTINGS) && t.any { s -> UNKNOWN_SOURCE_WORDS.any { s.contains(it, ignoreCase = true) } }) return Screen.UNKNOWN_SOURCE
        if (installerish && (t.any { s -> INSTALL_WORDS.any { s.contains(it, ignoreCase = true) } } || t.any { it in INSTALL_BUTTONS })) return Screen.INSTALL
        return null
    }

    /** 위험 시간대인지: 마지막 위험 메시지를 받은 지 30분이 안 됐을 때 */
    fun inRiskWindow(now: Long, lastRiskAt: Long?): Boolean =
        lastRiskAt != null && now >= lastRiskAt && now - lastRiskAt < RISK_WINDOW_MS

    /** 경고를 띄울지: 설치 화면 + 위험 시간대 + "그래도 진행" 유예가 끝남 */
    fun shouldBlock(screen: Screen?, now: Long, lastRiskAt: Long?, snoozedUntil: Long): Boolean =
        screen != null && inRiskWindow(now, lastRiskAt) && now >= snoozedUntil

    /** "그래도 진행"을 누른 시각 → 이때까지 다시 경고하지 않음 */
    fun snoozeUntil(now: Long) = now + SNOOZE_MS
}
