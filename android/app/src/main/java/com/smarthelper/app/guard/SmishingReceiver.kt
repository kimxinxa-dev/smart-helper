package com.smarthelper.app.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * 문자가 오면 휴대폰 안에서만 검사하고, 위험하면 경고한다. 문자는 밖으로 보내지 않는다.
 *  1단계: 연락처에 저장된 번호면 즉시 통과 (자원 소모 0)
 *  2단계: 링크가 없으면 무거운 검사(AI 모델)는 건너뛴다. 가벼운 말투 규칙은 항상 본다 — 링크 없는 사칭 대비
 *  3단계: 로컬 정밀 검사 (SmishingEngine: 규칙 + 직접 학습한 AI 분류 모델)
 * 카카오톡 등 메신저는 MessengerListener 가 같은 방식으로 검사한다.
 */
class SmishingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        // 긴 문자는 여러 조각으로 오므로 보낸 사람별로 합쳐서 검사한다
        Telephony.Sms.Intents.getMessagesFromIntent(intent)
            .groupBy { it.originatingAddress ?: "알 수 없음" }
            .forEach { (sender, parts) ->
                val body = parts.joinToString("") { it.messageBody ?: "" }
                GuardAlert.handle(context, sender, body, "문자", GuardAlert.savedNumber(context, sender))
            }
    }
}
