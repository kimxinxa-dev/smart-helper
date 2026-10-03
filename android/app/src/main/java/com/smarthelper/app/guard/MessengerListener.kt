package com.smarthelper.app.guard

import android.app.Notification
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * 카카오톡 등 메신저의 새 메시지 알림을 휴대폰 안에서 검사한다 (알림 읽기 권한, 사용자가 설정에서 직접 켬).
 * 정해진 메신저 앱의 알림만 보고, 그 밖의 알림은 읽지 않는다. 내용은 밖으로 보내지 않는다.
 */
class MessengerListener : NotificationListenerService() {

    companion object {
        val APPS = mapOf(
            "com.kakao.talk" to "카카오톡",
            "jp.naver.line.android" to "라인",
            "org.telegram.messenger" to "텔레그램",
            "com.facebook.orca" to "페이스북 메신저",
            "com.whatsapp" to "왓츠앱",
        )
        /** 개발용: 디버그 앱에서는 `adb shell cmd notification post` 로 올린 시험 알림도 메신저로 본다 */
        private const val SHELL = "com.android.shell"
    }

    /** 같은 메시지 알림이 여러 번 갱신돼도 한 번만 검사한다 */
    private val seen = object : LinkedHashMap<String, Boolean>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 100
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val debug = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val app = APPS[sbn.packageName] ?: if (debug && sbn.packageName == SHELL) "시험용 메신저" else return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val (sender, text) = lastMessage(n.extras) ?: return
        if (text.isBlank() || seen.put("${sbn.packageName}|$sender|$text", true) != null) return
        // 메신저는 번호를 알 수 없어 이름으로 짐작한다. 사기꾼이 이름을 흉내 낼 수 있으니 그때는 확실히 위험할 때만 알린다.
        val namedLikeContact = GuardAlert.savedName(this, sender)
        GuardAlert.handle(this, sender, text, app, savedContact = false, alertOnlyHigh = namedLikeContact)
    }

    /** 알림에서 (보낸 사람, 메시지). 대화형 알림이면 마지막 메시지를 쓴다. */
    private fun lastMessage(ex: Bundle): Pair<String, String>? {
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        @Suppress("DEPRECATION")
        val msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES)
        val last = msgs?.lastOrNull() as? Bundle
        if (last != null) {
            val text = last.getCharSequence("text")?.toString().orEmpty()
            @Suppress("DEPRECATION")
            val who = last.getCharSequence("sender")?.toString()
                ?: (last.getParcelable("sender_person") as? android.app.Person)?.name?.toString()
                ?: title
            return who to text
        }
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: return null
        return title.ifBlank { "알 수 없음" } to text
    }
}
