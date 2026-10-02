package com.smarthelper.app.assist

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.MediaStore
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 알아들은 할 일(Command)을 실제 휴대폰 기능으로 바꾼다.
 * 남에게 영향을 주는 일(전화·문자·알람·설치)은 confirm=true 로 먼저 확인받는다.
 * 송금·결제는 하지 않는다 (CLAUDE.md 안전 설계 1).
 */
class Assistant(private val act: Activity) {

    /** say: 먼저 들려줄 말. run: 실행하고 결과 말을 돌려준다(null 이면 say 만). */
    class Plan(val say: String, val confirm: Boolean = false, val run: (() -> String?)? = null)

    private data class Person(val name: String, val number: String)

    fun plan(text: String): Plan = when (val c = CommandParser.parse(text)) {
        is Command.Call -> call(c)
        is Command.Sms -> sms(c)
        is Command.Volume -> Plan("") { volume(c.up) }
        is Command.Alarm -> alarm(c)
        is Command.OpenApp -> openApp(c.name)
        is Command.Install -> install(c.name)
        is Command.Torch -> Plan("") { torch(c.on) }
        is Command.AddContact -> Plan("") {
            start(Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)
                .apply { c.name?.let { putExtra(ContactsContract.Intents.Insert.NAME, it) } }
                .apply { c.number?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) } })
            "연락처 저장 화면을 열었어요. 이름과 번호를 확인하고 '저장'을 눌러 주세요."
        }
        is Command.Emergency -> Plan("") {
            start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${c.number}")))
            "${c.number} 번호를 눌러 두었어요. 초록색 통화 버튼을 누르세요."
        }
        Command.FontSize -> Plan("") {
            start(Intent(Settings.ACTION_DISPLAY_SETTINGS))
            "화면 설정을 열었어요. '글자 크기'를 찾아 눌러 주세요."
        }
        Command.Wifi -> Plan("") {
            start(Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS))
            "와이파이 화면을 열었어요. 연결할 이름을 눌러 주세요."
        }
        Command.Bluetooth -> Plan("") {
            start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            "블루투스 화면을 열었어요."
        }
        Command.Camera -> Plan("") {
            start(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            "카메라를 열었어요. 가운데 동그란 버튼을 누르면 사진이 찍혀요."
        }
        Command.Time -> Plan("지금은 ${hm(now(Calendar.HOUR_OF_DAY), now(Calendar.MINUTE))}이에요.")
        Command.Date -> Plan("오늘은 ${SimpleDateFormat("M월 d일 EEEE", Locale.KOREAN).format(Date())}이에요.")
        Command.Battery -> {
            val pct = act.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            Plan("배터리가 $pct% 남았어요." + if (pct in 0..20) " 충전기를 꽂아 주세요." else "")
        }
        Command.Banking -> Plan("송금은 안전을 위해 제가 대신하지 않아요. 은행 앱에서 천천히 직접 하시고, 누가 시킨 송금이라면 먼저 가족에게 물어보세요.")
        Command.Help -> Plan("이런 걸 할 수 있어요. 전화 걸기, 문자 쓰기, 알람 맞추기, 소리 키우기, 손전등 켜기, 앱 열기, 지금 시간과 배터리 알려 드리기예요. '영희한테 전화해 줘'처럼 말씀해 보세요.")
        is Command.Unknown -> Plan("죄송해요, 잘 이해하지 못했어요. '영희한테 전화해 줘'나 '소리 키워 줘'처럼 말씀해 주세요.")
    }

    // ---------- 전화·문자 ----------

    private fun call(c: Command.Call): Plan {
        val p = person(c.who, c.number) ?: return notFound(c.who)
        return Plan("${p.name} 님께 전화할까요?", confirm = true) {
            if (granted(Manifest.permission.CALL_PHONE)) {
                start(Intent(Intent.ACTION_CALL, Uri.parse("tel:${p.number}")))
                "${p.name} 님께 전화를 걸고 있어요."
            } else {
                start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${p.number}")))
                "번호를 눌러 두었어요. 초록색 통화 버튼을 누르세요."
            }
        }
    }

    private fun sms(c: Command.Sms): Plan {
        val p = person(c.who, c.number) ?: return notFound(c.who)
        val what = c.body?.let { "'$it'라고 " } ?: ""
        return Plan("${p.name} 님께 ${what}문자를 쓸까요? 보내기 버튼은 직접 눌러 주세요.", confirm = true) {
            start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${p.number}")).apply { c.body?.let { putExtra("sms_body", it) } })
            "문자 화면을 열었어요. 내용을 확인하고 보내기 버튼을 눌러 주세요."
        }
    }

    private fun notFound(who: String?) = when {
        who == null -> Plan("누구에게 할까요? '영희한테 전화해 줘'처럼 이름을 함께 말씀해 주세요.")
        !granted(Manifest.permission.READ_CONTACTS) -> Plan("연락처에서 이름을 찾으려면 허락이 필요해요. 아래 '허락하기' 버튼을 눌러 주세요.")
        else -> Plan("연락처에서 '$who' 님을 찾지 못했어요. 이름을 다시 말씀해 주세요.")
    }

    /** 번호가 있으면 그대로, 이름이면 연락처에서 찾는다. "아들 민수"는 "아들 민수" → "민수" → "아들" 순서로 찾는다. */
    private fun person(who: String?, number: String?): Person? {
        if (number != null) return Person(pretty(number), number)
        if (who == null || !granted(Manifest.permission.READ_CONTACTS)) return null
        val tries = listOf(who) + who.split(' ').filter { it.length >= 2 }.sortedByDescending { it.length }
        for (q in tries.distinct()) {
            act.contentResolver.query(
                Phone.CONTENT_URI, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER),
                "${Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$q%"), "${Phone.DISPLAY_NAME} ASC"
            )?.use { if (it.moveToFirst()) return Person(it.getString(0), it.getString(1)) }
        }
        return null
    }

    // ---------- 알람·음량·손전등 ----------

    private fun alarm(c: Command.Alarm): Plan {
        val (h, m) = if (c.afterMinutes != null) {
            val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, c.afterMinutes) }
            cal.get(Calendar.HOUR_OF_DAY) to cal.get(Calendar.MINUTE)
        } else c.hour to c.minute
        if (h < 0) return Plan("몇 시에 깨워 드릴까요? '아침 7시에 깨워 줘'처럼 말씀해 주세요.")
        val day = if (c.tomorrow) "내일 " else ""
        return Plan("$day${hm(h, m)}에 알람을 맞출까요?", confirm = true) {
            start(Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, h)
                .putExtra(AlarmClock.EXTRA_MINUTES, m)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "스마트 헬퍼 알람")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
            "$day${hm(h, m)}에 알람을 맞췄어요."
        }
    }

    private fun volume(up: Boolean): String {
        val am = act.getSystemService(AudioManager::class.java)
        val dir = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        repeat(2) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, if (it == 0) AudioManager.FLAG_SHOW_UI else 0) }
        try { am.adjustStreamVolume(AudioManager.STREAM_RING, dir, 0) } catch (e: SecurityException) { /* 방해 금지 모드 */ }
        val level = am.getStreamVolume(AudioManager.STREAM_MUSIC) * 10 / am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return (if (up) "소리를 키웠어요." else "소리를 줄였어요.") + " 지금 음량은 10 중에 ${level}이에요."
    }

    private fun torch(on: Boolean): String {
        val cm = act.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            ?: return "이 휴대폰에는 손전등이 없어요."
        cm.setTorchMode(id, on)
        return if (on) "손전등을 켰어요. 끌 때는 '손전등 꺼 줘'라고 말씀해 주세요." else "손전등을 껐어요."
    }

    // ---------- 앱 ----------

    private val ALIAS = mapOf(
        "카톡" to "카카오톡", "카카오" to "카카오톡", "유튜브" to "youtube", "크롬" to "chrome",
        "플레이스토어" to "play스토어", "스토어" to "play스토어", "지메일" to "gmail", "구글" to "google",
        "문자" to "메시지", "사진" to "사진", "갤러리" to "갤러리", "지도" to "지도",
    )

    private fun norm(s: String) = s.lowercase().replace(" ", "")

    private fun openApp(name: String): Plan {
        val q = norm(ALIAS[norm(name)] ?: name)
        val pm = act.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val hit = apps.firstOrNull { norm(it.loadLabel(pm).toString()) == q }
            ?: apps.firstOrNull { norm(it.loadLabel(pm).toString()).let { l -> l.contains(q) || q.contains(l) } }
            ?: return Plan("'$name' 앱을 찾지 못했어요. 공식 스토어에서 찾아 드릴까요?", confirm = true) { storeSearch(name) }
        val label = hit.loadLabel(pm)
        return Plan("") {
            start(pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return@Plan "'$label' 앱을 열 수 없어요.")
            "'$label' 앱을 열었어요."
        }
    }

    /** 앱 설치는 공식 스토어(Play 스토어)에서만 (CLAUDE.md 안전 설계 4) */
    private fun install(name: String) =
        Plan("Play 스토어에서 '$name' 앱을 찾아 드릴까요? 공식 스토어에서만 설치해요.", confirm = true) { storeSearch(name) }

    private fun storeSearch(name: String): String {
        try {
            start(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(name)}&c=apps")))
        } catch (e: ActivityNotFoundException) {
            start(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=${Uri.encode(name)}&c=apps")))
        }
        return "스토어에서 '$name'을 찾았어요. 별점과 다운로드 수가 많은 공식 앱인지 확인하고 '설치'를 눌러 주세요."
    }

    // ---------- 공통 ----------

    private fun start(i: Intent) = act.startActivity(i)

    private fun granted(p: String) = act.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun now(field: Int) = Calendar.getInstance().get(field)

    private fun hm(h: Int, m: Int) =
        (if (h < 12) "오전 " else "오후 ") + "${if (h % 12 == 0) 12 else h % 12}시" + if (m > 0) " ${m}분" else ""

    private fun pretty(n: String) =
        if (n.length == 11) "${n.substring(0, 3)}-${n.substring(3, 7)}-${n.substring(7)}" else n
}
