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
     * savedContact: 연락처에 저장된 상대인지.
     * alertOnlyHigh: 메신저처럼 이름만으로 저장 여부를 짐작한 경우, 확실히 위험할 때만 알린다.
     */
    fun handle(ctx: Context, sender: String, body: String, source: String, savedContact: Boolean, alertOnlyHigh: Boolean = false) {
        Guard.init(ctx)
        val v = SmishingEngine.check(body, savedContact)
        val id = GuardStore.add(ctx, sender, body, v, source)
        val alert = if (alertOnlyHigh) v.level == Level.HIGH else v.level == Level.MID || v.level == Level.HIGH
        if (alert) notify(ctx, id, sender, v, source)
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
