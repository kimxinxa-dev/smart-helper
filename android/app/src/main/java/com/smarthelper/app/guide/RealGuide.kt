package com.smarthelper.app.guide

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.Settings
import com.smarthelper.app.assist.Command
import com.smarthelper.app.assist.CommandParser

/**
 * '말로 물어보기'의 "📱 내 휴대폰으로 해 보기".
 * 대신 해 주지 않고, 실제 앱 화면 위에 테두리·화살표로 사용자가 직접 하도록 안내한다.
 * kind: photo(사진 보내기) / text(문자 보내기) / font_up·font_down(글자 크기) / wifi(와이파이 연결)
 */
class RealGuide(private val act: Activity) {

    /** say: 들려줄 말. confirm: "네/아니요"로 확인받을지. perm: 연락처 권한이 필요함. run: "네"일 때 할 일 */
    class Plan(val say: String, val confirm: Boolean = false, val perm: Boolean = false, val run: (() -> String)? = null)

    private data class Person(val name: String, val number: String)

    fun plan(text: String, kind: String): Plan {
        val service = GuideService.instance
            ?: return Plan("화면에 표시하며 도와드리려면 '화면 안내' 기능을 한 번 켜야 해요. 설정을 열어 드릴까요?", confirm = true) {
                act.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                "'설치된 앱' 또는 '다운로드한 앱'에서 '스마트 헬퍼 화면 안내'를 눌러 켜 주세요. '기기를 제어하도록 허용할까요?'라는 안내가 나오면 '허용'을 누르셔도 괜찮아요. 켠 다음 다시 말씀해 주세요."
            }
        val note = "버튼은 직접 누르시면 돼요. 안내하는 동안 화면을 보지만 저장하지는 않아요."
        return when (kind) {
            "photo", "text" -> {
                val c = CommandParser.parse(text)
                val (who, number, kakao) = when (c) {
                    is Command.SendPhoto -> Triple(c.who, c.number, c.kakao)
                    is Command.Sms -> Triple(c.who, c.number, false)
                    is Command.Call -> Triple(c.who, c.number, false)
                    else -> Triple(null, null, false)
                }
                val p = person(who, number) ?: return notFound(who)
                val what = if (kind == "photo") "사진" else "문자"
                val k = if (kakao) "카카오톡은 아직 안내하지 못해서, 문자로 보내는 방법을 알려 드릴게요. " else ""
                val guide = if (kind == "photo") Guides.photo(p.name, p.number) else Guides.text(p.name, p.number)
                Plan("$k${p.name} 님께 ${what} 보내는 방법을 화면에 표시하며 한 단계씩 알려 드릴까요? $note", confirm = true) {
                    // 문자 앱이 마지막에 보던 대화를 다시 보여 주지 않도록 새로 연다
                    start(service, guide, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${p.number}")), "문자 창을 열었어요. 노란 테두리를 따라 눌러 보세요.")
                }
            }
            "font_up", "font_down" -> {
                val up = kind == "font_up"
                Plan("설정 앱에서 글자를 ${if (up) "키우는" else "줄이는"} 방법을 화면에 표시하며 알려 드릴까요? $note", confirm = true) {
                    start(service, Guides.font(up), Intent(Settings.ACTION_SETTINGS), "설정을 열었어요. 노란 테두리를 따라 눌러 보세요.")
                }
            }
            "install" -> {
                val app = appName(text)
                Plan("Play 스토어에서 ${app?.let { "'$it'을 " } ?: "앱을 "}찾아 설치하는 방법을 화면에 표시하며 알려 드릴까요? 공식 스토어에서만 설치해요. $note", confirm = true) {
                    val store = act.packageManager.getLaunchIntentForPackage("com.android.vending")
                        ?: return@Plan "이 휴대폰에서 Play 스토어를 찾지 못했어요."
                    start(service, Guides.install(app), store, "Play 스토어를 열었어요. 노란 테두리를 따라 눌러 보세요.")
                }
            }
            "uninstall" -> {
                val said = appName(text)
                    ?: return Plan("어떤 앱을 지울까요? '연습용 퍼즐 지워 줘'처럼 앱 이름을 함께 말씀해 주세요.")
                // 홈 화면에 보이는 앱 이름으로 실제 앱을 찾는다 (없으면 안내를 시작하지 않는다)
                val (app, pkg) = launcherApp(said)
                    ?: return Plan("휴대폰에서 '$said' 앱을 찾지 못했어요. 홈 화면에 보이는 이름 그대로 말씀해 주세요.")
                val installed = {
                    try { act.packageManager.getPackageInfo(pkg, 0); true } catch (e: PackageManager.NameNotFoundException) { false }
                }
                Plan("설정에서 '$app'을 지우는 방법을 화면에 표시하며 알려 드릴까요? 지운 앱의 자료는 사라질 수 있어요. $note", confirm = true) {
                    start(service, Guides.uninstall(app, installed), Intent(Settings.ACTION_SETTINGS), "설정을 열었어요. 노란 테두리를 따라 눌러 보세요.")
                }
            }
            "alarm" -> {
                val a = CommandParser.parse(text) as? Command.Alarm
                val (h, m) = when {
                    a == null -> -1 to 0
                    a.afterMinutes != null -> java.util.Calendar.getInstance().apply { add(java.util.Calendar.MINUTE, a.afterMinutes) }
                        .let { it.get(java.util.Calendar.HOUR_OF_DAY) to it.get(java.util.Calendar.MINUTE) }
                    else -> a.hour to a.minute
                }
                val clock = launch("com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.android.deskclock")
                    ?: return Plan("이 휴대폰에서 시계 앱을 찾지 못했어요.")
                val time = if (h >= 0) "${if (h < 12) "오전" else "오후"} ${if (h % 12 == 0) 12 else h % 12}시${if (m > 0) " ${m}분" else ""}에 " else ""
                // 완료 확인: 휴대폰의 '다음 알람'이 말한 시각이 되었는지(시각을 모르면 바뀌었는지)
                val am = act.getSystemService(android.app.AlarmManager::class.java)
                val before = am.nextAlarmClock?.triggerTime
                val alarmSet = {
                    val t = am.nextAlarmClock?.triggerTime
                    if (t == null) false
                    else if (h < 0) t != before
                    else java.util.Calendar.getInstance().apply { timeInMillis = t }.let {
                        it.get(java.util.Calendar.HOUR_OF_DAY) == h && it.get(java.util.Calendar.MINUTE) == m
                    } || t != before
                }
                Plan("시계 앱에서 ${time}알람을 맞추는 방법을 화면에 표시하며 알려 드릴까요? $note", confirm = true) {
                    start(service, Guides.alarm(h, m, alarmSet), clock, "시계를 열었어요. 노란 테두리를 따라 눌러 보세요.")
                }
            }
            "call" -> {
                val c = CommandParser.parse(text)
                val who = (c as? Command.Call)?.who ?: (c as? Command.Sms)?.who
                val p = person(who, (c as? Command.Call)?.number)
                    ?: return if (who == null) Plan("누구에게 전화할까요? '영희한테 전화 거는 법 알려 줘'처럼 이름을 함께 말씀해 주세요.") else notFound(who)
                val phone = launch("com.google.android.dialer", "com.samsung.android.dialer", "com.android.dialer")
                    ?: return Plan("이 휴대폰에서 전화 앱을 찾지 못했어요.")
                Plan("전화 앱에서 ${p.name} 님께 전화 거는 방법을 화면에 표시하며 알려 드릴까요? 통화 버튼은 직접 누르시면 돼요. 안내하는 동안 화면을 보지만 저장하지는 않아요.", confirm = true) {
                    start(service, Guides.call(p.name, p.number), phone, "전화 앱을 열었어요. 노란 테두리를 따라 눌러 보세요.")
                }
            }
            "wifi" -> Plan("설정 앱에서 와이파이에 연결하는 방법을 화면에 표시하며 알려 드릴까요? $note", confirm = true) {
                start(service, Guides.wifi(), Intent(Settings.ACTION_SETTINGS), "설정을 열었어요. 노란 테두리를 따라 눌러 보세요.")
            }
            else -> Plan("이 일은 아직 내 휴대폰에서 안내하지 못해요. 화면 속 휴대폰으로 연습해 보세요.")
        }
    }

    /** 안내를 켜고, 이전 화면이 남지 않게 앱을 새로 연다 */
    private fun start(service: GuideService, guide: Guide, intent: Intent, ok: String): String {
        if (!service.begin(guide)) return "화면 안내가 잠시 꺼졌어요. 설정 > 접근성에서 '스마트 헬퍼 화면 안내'를 껐다가 다시 켜 주세요."
        act.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        return ok
    }

    /** 여러 휴대폰 회사의 같은 앱(시계, 전화 등) 중 이 휴대폰에 있는 것을 연다 */
    private fun launch(vararg packages: String): Intent? =
        packages.firstNotNullOfOrNull { act.packageManager.getLaunchIntentForPackage(it) }

    /** 홈 화면 앱 중 이름이 같은(없으면 이름이 들어간) 앱 → (화면에 보이는 이름, 패키지 이름) */
    private fun launcherApp(name: String): Pair<String, String>? {
        val pm = act.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .filter { it.second != act.packageName }
        val n = name.replace(" ", "")
        return apps.firstOrNull { it.first.replace(" ", "") == n } ?: apps.firstOrNull { it.first.replace(" ", "").contains(n) }
    }

    /** "연습용 퍼즐 지워 줘", "카카오톡 앱을 설치하고 싶어요" → 앱 이름. "앱을 설치하고 싶어요"처럼 이름이 없으면 null */
    private fun appName(text: String): String? {
        val m = Regex("^(.*?)\\s*(?:앱|어플|어플리케이션)?\\s*(?:을|를)?\\s*(?:좀\\s*)?(?:설치|깔|다운|삭제|지우|지워|제거)").find(text.trim()) ?: return null
        val name = m.groupValues[1].replace(Regex("^(?:안 ?쓰는|우리|내|그|이)\\s*"), "").replace(Regex("\\s*(?:앱|어플|을|를)$"), "").trim()
        return name.ifEmpty { null }?.takeUnless { it in setOf("앱", "어플", "새", "새로운") }
    }

    private fun notFound(who: String?) = when {
        who == null -> Plan("누구에게 보낼까요? '영희한테 사진 보내 줘'처럼 이름을 함께 말씀해 주세요.")
        !granted() -> Plan("연락처에서 '$who' 님을 찾으려면 허락이 필요해요. 아래 버튼을 눌러 주세요.", perm = true)
        else -> Plan("연락처에서 '$who' 님을 찾지 못했어요. 이름을 다시 말씀해 주세요.")
    }

    /** 번호가 있으면 그대로, 이름이면 연락처에서 찾는다. "아들 민수"는 "아들 민수" → "민수" → "아들" 순서로 찾는다. */
    private fun person(who: String?, number: String?): Person? {
        if (number != null) return Person(number, number)
        if (who == null || !granted()) return null
        val tries = listOf(who) + who.split(' ').filter { it.length >= 2 }.sortedByDescending { it.length }
        for (q in tries.distinct()) {
            act.contentResolver.query(
                Phone.CONTENT_URI, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER),
                "${Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$q%"), "${Phone.DISPLAY_NAME} ASC"
            )?.use { if (it.moveToFirst()) return Person(it.getString(0), it.getString(1)) }
        }
        return null
    }

    private fun granted() = act.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
}
