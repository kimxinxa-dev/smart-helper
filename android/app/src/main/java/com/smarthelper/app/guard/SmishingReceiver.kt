package com.smarthelper.app.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * 문자가 오면 휴대폰 안에서만 검사하고, 위험하면 경고한다. 문자는 밖으로 보내지 않는다.
 *  1단계: 연락처에 저장된 번호면 즉시 통과 (자원 소모 0)
 *  2단계: 규칙 검사 (말투·돈 요구·링크 모양)
 *  3단계: AI 분류 모델 검사 — 링크가 없어도 항상 본다 (링크 없는 지인 사칭 대비)
 *  4단계: 내용 밖의 단서 — 처음 온 번호인지, 조금 전 의심 문자를 보낸 번호인지 (SenderSignals, GuardAlert 에서)
 * 카카오톡 등 메신저는 MessengerListener 가 같은 방식으로 검사한다.
 */
class SmishingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        // 경고를 문자 앱 알림 뒤로 미루므로(GuardAlert.afterSmsNotice) 그동안 앱이 꺼지지 않게 붙잡아 둔다
        val pending = goAsync()
        // 긴 문자는 여러 조각으로 오므로 보낸 사람별로 합쳐서 검사한다
        Telephony.Sms.Intents.getMessagesFromIntent(intent)
            .groupBy { it.originatingAddress ?: "알 수 없음" }
            .forEach { (sender, parts) ->
                val body = parts.joinToString("") { it.messageBody ?: "" }
                GuardAlert.handle(context, sender, body, "문자", GuardAlert.savedNumber(context, sender))
            }
        // 방송 처리 제한은 10초지만 느린 휴대폰에서는 앱이 켜지는 시간까지 포함돼 9초만 붙잡아도 '응답 없음'으로 강제 종료됐다.
        // 붙잡아 둘 필요가 있는 건 알림 읽기가 꺼져 있을 때의 경고 대기(GuardAlert.NO_LISTENER_DELAY_MS, 3초)뿐이다.
        // 알림 읽기·접근성 서비스가 켜져 있으면 그 서비스들이 앱을 살려 두므로 더 오래 기다려도 경고가 뜬다.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ pending.finish() }, GuardAlert.NO_LISTENER_DELAY_MS + 1000)
    }
}
