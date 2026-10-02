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
 * 지금은 문자로 사진 보내기만 지원한다.
 */
class RealGuide(private val act: Activity) {

    /** say: 들려줄 말. confirm: "네/아니요"로 확인받을지. perm: 연락처 권한이 필요함. run: "네"일 때 할 일 */
    class Plan(val say: String, val confirm: Boolean = false, val perm: Boolean = false, val run: (() -> String)? = null)

    private data class Person(val name: String, val number: String)

    fun plan(text: String): Plan {
        val c = CommandParser.parse(text) as? Command.SendPhoto
            ?: return Plan("이 일은 아직 내 휴대폰에서 안내하지 못해요. 화면 속 휴대폰으로 연습해 보세요.")
        val p = person(c.who, c.number) ?: return notFound(c.who)
        val kakao = if (c.kakao) "카카오톡은 아직 안내하지 못해서, 문자로 보내는 방법을 알려 드릴게요. " else ""
        val guide = GuideService.instance
            ?: return Plan("${kakao}화면에 표시하며 도와드리려면 '화면 안내' 기능을 한 번 켜야 해요. 설정을 열어 드릴까요?", confirm = true) {
                act.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                "'설치된 앱' 또는 '다운로드한 앱'에서 '스마트 헬퍼 화면 안내'를 눌러 켜 주세요. '기기를 제어하도록 허용할까요?'라는 안내가 나오면 '허용'을 누르셔도 괜찮아요. 켠 다음 다시 말씀해 주세요."
            }
        return Plan("$kakao${p.name} 님께 문자로 사진 보내는 방법을, 화면에 표시하며 한 단계씩 알려 드릴까요? 버튼은 직접 누르시면 돼요. 안내하는 동안 문자 앱 화면을 보지만 저장하지는 않아요.", confirm = true) {
            if (!guide.begin(PhotoGuide.steps, p.name, p.number)) return@Plan "화면 안내가 잠시 꺼졌어요. 설정 > 접근성에서 '스마트 헬퍼 화면 안내'를 껐다가 다시 켜 주세요."
            // 문자 앱이 마지막에 보던 대화를 다시 보여 주지 않도록 새로 연다
            act.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${p.number}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            "문자 창을 열었어요. 노란 테두리를 따라 눌러 보세요."
        }
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
