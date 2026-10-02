package com.smarthelper.app.guard

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Patterns

/**
 * 문자가 오면 휴대폰 안에서만 검사하고, 위험하면 경고한다. 문자는 밖으로 보내지 않는다.
 *  1단계: 연락처에 저장된 번호면 즉시 통과 (자원 소모 0)
 *  2단계: 링크가 없으면 무거운 검사(AI 모델)는 건너뛴다. 가벼운 말투 규칙은 항상 본다 — 링크 없는 사칭 대비
 *  3단계: 로컬 정밀 검사 (SmishingEngine: 규칙 + 직접 학습한 AI 분류 모델)
 */
class SmishingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        RuleDetector.urlFinder = ::extractUrls
        ModelDetector.install(context)
        // 긴 문자는 여러 조각으로 오므로 보낸 사람별로 합쳐서 검사한다
        Telephony.Sms.Intents.getMessagesFromIntent(intent)
            .groupBy { it.originatingAddress ?: "알 수 없음" }
            .forEach { (sender, parts) -> handle(context, sender, parts.joinToString("") { it.messageBody ?: "" }) }
    }

    private fun handle(context: Context, sender: String, body: String) {
        val verdict = SmishingEngine.check(body, isContactSaved(context, sender))
        val id = GuardStore.add(context, sender, body, verdict)
        if (verdict.level == Level.MID || verdict.level == Level.HIGH) triggerSmishingAlert(context, id, sender, verdict)
    }

    /** 안드로이드 공식 링크 인식기로 URL 추출 */
    private fun extractUrls(text: String): List<String> {
        val urls = mutableListOf<String>()
        val matcher = Patterns.WEB_URL.matcher(text)
        while (matcher.find()) urls.add(matcher.group())
        return urls
    }

    /** 연락처에 저장된 번호인지 확인. 권한이 없으면 저장 안 된 번호로 보고 검사한다. */
    private fun isContactSaved(context: Context, phoneNumber: String): Boolean {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return false
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        return try {
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
                ?.use { it.moveToFirst() } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /** 위험 알림. 누르면 큰 글씨 경고 화면(WarningActivity)이 열리고 음성으로 읽어 준다. */
    private fun triggerSmishingAlert(context: Context, id: Long, sender: String, v: Verdict) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "위험한 문자 경고", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "사기로 의심되는 문자가 오면 알려 드려요" }
            )
        }
        val open = PendingIntent.getActivity(
            context, id.toInt(),
            Intent(context, WarningActivity::class.java).putExtra(WarningActivity.EXTRA_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = when {
            v.level != Level.HIGH -> "⚠️ 조심해야 할 문자예요"
            v.urls.isNotEmpty() -> "🚨 위험한 문자예요! 링크를 누르지 마세요"
            else -> "🚨 사기로 의심되는 문자예요! 답장하지 마세요"
        }
        val text = "$sender 님이 보낸 문자예요. 눌러서 이유를 확인하세요."
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL) else Notification.Builder(context)
        val n = b.setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text + "\n" + v.reasons.firstOrNull().orEmpty()))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .build()
        try {
            nm.notify(id.toInt(), n)
        } catch (e: SecurityException) {
            // 알림 권한이 없으면 기록만 남는다
        }
    }

    companion object {
        const val CHANNEL = "smishing"
    }
}
