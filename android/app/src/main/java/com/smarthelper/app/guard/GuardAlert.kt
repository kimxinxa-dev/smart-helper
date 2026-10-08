package com.smarthelper.app.guard

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract

/** 문자·메신저 공통: 검사 → 기록 → 위험하면 알림. 내용은 휴대폰 밖으로 보내지 않는다. */
object GuardAlert {
    const val CHANNEL = "smishing"

    /**
     * 경고 순서: 문자 앱의 수신 알림 → (AFTER_SMS_NOTICE_MS 뒤) 경고 알림 + 🚨 팝업.
     * 문자 앱이 알림을 띄우는 속도는 휴대폰마다 달라서(에뮬레이터의 구글 메시지는 6~7초), 알림 읽기 권한으로
     * 문자 앱 알림이 실제로 뜬 것을 보고 띄운다. 권한이 없거나 알림을 못 보면 FALLBACK_MS 뒤에 띄운다.
     */
    const val AFTER_SMS_NOTICE_MS = 2000L
    /** 알림 읽기 권한이 켜져 있으면 문자 앱 알림을 이만큼까지 기다린다 */
    const val FALLBACK_MS = 10000L
    /** 알림 읽기 권한이 꺼져 있어 문자 앱 알림을 볼 수 없으면 이만큼만 기다린다 */
    const val NO_LISTENER_DELAY_MS = 3000L
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** 문자 앱 알림을 기다리는 경고들 */
    private class Pending(val run: () -> Unit) {
        var done = false
        val fallback = Runnable { fire(this) }
    }
    private val waiting = mutableListOf<Pending>()
    /** 문자 앱이 마지막으로 알림을 띄운 시각 (경고보다 알림이 먼저 뜬 경우 대비) */
    private var lastSmsNotice = 0L

    private fun fire(p: Pending) {
        if (p.done) return
        p.done = true
        main.removeCallbacks(p.fallback)
        waiting.remove(p)
        p.run()
    }

    /** 문자 경고를 문자 앱 알림 뒤로 미룬다 */
    private fun afterSmsNotice(run: () -> Unit) {
        val p = Pending(run)
        val since = System.currentTimeMillis() - lastSmsNotice
        if (since in 0..3000) { main.postDelayed({ fire(p) }, (AFTER_SMS_NOTICE_MS - since).coerceAtLeast(300)); return }
        waiting += p
        main.postDelayed(p.fallback, if (MessengerListener.connected) FALLBACK_MS else NO_LISTENER_DELAY_MS)
    }

    /** MessengerListener 가 기본 문자 앱의 새 메시지 알림을 보면 부른다 */
    fun onSmsAppNotice() {
        lastSmsNotice = System.currentTimeMillis()
        waiting.toList().forEach { p ->
            main.removeCallbacks(p.fallback)
            main.postDelayed({ fire(p) }, AFTER_SMS_NOTICE_MS)
        }
    }

    /**
     * savedContact: 연락처에 저장된 상대인지.
     * alertOnlyHigh: 메신저처럼 이름만으로 저장 여부를 짐작한 경우, 확실히 위험할 때만 알린다.
     */
    fun handle(ctx: Context, sender: String, body: String, source: String, savedContact: Boolean, alertOnlyHigh: Boolean = false) {
        Guard.init(ctx)
        var v = SmishingEngine.check(body, savedContact)
        if (!savedContact) {
            // 내용 밖의 단서: 처음 온 번호인지, 해외 번호인지, 조금 전 의심 문자를 보낸 상대인지
            val now = System.currentTimeMillis()
            val seen = SenderBook.get(ctx, source, sender)
            v = SmishingEngine.withExtra(v, SenderSignals.inspect(sender, source == "문자", body, seen, now, v.urls.isNotEmpty()))
            SenderBook.record(ctx, source, sender, now, warned = v.level >= Level.MID)
        }
        val id = GuardStore.add(ctx, sender, body, v, source)
        val alert = if (alertOnlyHigh) v.level == Level.HIGH else v.level == Level.MID || v.level == Level.HIGH
        if (!alert && v.level != Level.HIGH) return
        val warn: () -> Unit = {
            if (alert) notify(ctx, id, sender, v, source)
            // 🚨 위험이면 알림과 함께 지금 화면 위에 팝업도 띄운다 (위험 링크 차단을 켜 둔 경우. 꺼져 있으면 알림만)
            // ⚠️ 주의면 그보다 가볍게, 화면 아래쪽에 잠시 사라지는 작은 카드
            val what = if (source == "문자") "문자" else "$source 메시지"
            val reason = v.reasons.firstOrNull().orEmpty()
            val service = com.smarthelper.app.guide.GuideService.instance
            if (v.level == Level.HIGH) service?.showRiskPopup(id, sender, what, reason, v.urls.isNotEmpty())
            else if (alert && v.level == Level.MID) service?.showCautionPopup(id, sender, what, reason, v.urls.isNotEmpty())
        }
        // 문자는 문자 앱의 수신 알림이 먼저 보이게 기다린다. 메신저는 이미 메신저 알림을 보고 검사한 것이라 잠깐만 기다린다
        if (source == "문자") afterSmsNotice(warn) else main.postDelayed(warn, AFTER_SMS_NOTICE_MS)
    }

    /** 연락처에 이 번호가 있는지 (권한이 없으면 없는 것으로 보고 검사한다) */
    fun savedNumber(ctx: Context, number: String): Boolean {
        if (!canReadContacts(ctx)) return false
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return try {
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use { it.moveToFirst() } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /** 연락처에 이 이름이 그대로 있는지 (메신저는 번호를 알 수 없어 이름으로 본다) */
    fun savedName(ctx: Context, name: String): Boolean {
        if (!canReadContacts(ctx) || name.isBlank()) return false
        return try {
            ctx.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts._ID),
                "${ContactsContract.Contacts.DISPLAY_NAME} = ?", arrayOf(name.trim()), null
            )?.use { it.moveToFirst() } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun canReadContacts(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** 위험 알림. 누르면 큰 글씨 경고 화면(WarningActivity)이 열리고 음성으로 읽어 준다. */
    fun notify(ctx: Context, id: Long, sender: String, v: Verdict, source: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "위험한 메시지 경고", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "사기로 의심되는 문자·메시지가 오면 알려 드려요" }
            )
        }
        val open = PendingIntent.getActivity(
            ctx, id.toInt(),
            Intent(ctx, WarningActivity::class.java).putExtra(WarningActivity.EXTRA_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val what = if (source == "문자") "문자" else "$source 메시지"
        val title = when {
            v.level != Level.HIGH -> "⚠️ 조심해야 할 ${what}예요"
            v.urls.isNotEmpty() -> "🚨 위험한 ${what}예요! 링크를 누르지 마세요"
            else -> "🚨 사기로 의심되는 ${what}예요! 답장하지 마세요"
        }
        val text = "$sender 님이 보낸 ${what}예요. 눌러서 이유를 확인하세요."
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CHANNEL) else Notification.Builder(ctx)
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
}
