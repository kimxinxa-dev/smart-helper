package com.smarthelper.app.guard

import android.content.Context

/** A안: 직접 학습한 분류 모델 검사기. 휴대폰 안에서 1ms 안에 판단하고, 문자를 밖으로 보내지 않는다. */
class ModelDetector(private val model: TextModel) : Detector {

    override fun inspect(body: String): Finding {
        val p = model.predict(body)
        val pct = (p * 100).toInt()
        return when {
            p >= 0.85f -> Finding(4, listOf("AI가 사기 문자와 아주 비슷하다고 판단했어요. (사기 가능성 $pct%)"), flag = true)
            p >= 0.6f -> Finding(2, listOf("AI가 사기 문자와 비슷한 점을 찾았어요. (사기 가능성 $pct%)"), flag = true)
            else -> Finding(0, emptyList())
        }
    }

    companion object {
        const val ASSET = "smishing_model.txt"
        @Volatile private var installed = false

        /** 앱 안의 모델 파일을 읽어 판단 엔진에 한 번만 꽂는다 */
        fun install(ctx: Context) {
            if (installed) return
            synchronized(this) {
                if (installed) return
                val model = ctx.assets.open(ASSET).bufferedReader().use { TextModel.parse(it.readText()) }
                SmishingEngine.detectors += ModelDetector(model)
                installed = true
            }
        }
    }
}
