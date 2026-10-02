package com.smarthelper.app.guard

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * A안 모델 학습. 평소 테스트에서는 건너뛰고, 아래 명령으로만 실행한다.
 *   gradle :app:testDebugUnitTest --tests "*ModelTrainer*" -Ptrain=1
 * 결과: app/src/main/assets/smishing_model.txt, data/sms_synthetic.tsv, app/build/model_report.txt
 */
class ModelTrainer {

    @Test
    fun train() {
        assumeTrue(System.getProperty("train") == "1")
        val data = SmsDataset.generate()
        // 공정한 평가: 학습에 쓰지 않은 "틀"(5개 중 1개)로 만든 문자로만 시험한다
        val (test, train) = data.partition { it.template % 5 == 0 }
        val real = File(DATA_DIR, "sms_real.tsv").takeIf { it.exists() }?.readLines(Charsets.UTF_8)
            ?.mapNotNull { l -> l.split('\t', limit = 2).takeIf { it.size == 2 }?.let { it[1] to it[0].trim().toInt() } }
            .orEmpty()

        val model = TextModel.train(train.map { it.text to it.label } + real)
        val m = Metrics.of(model, test.map { it.text to it.label })

        File("src/main/assets").mkdirs()
        File("src/main/assets/${ModelDetector.ASSET}").writeText(model.serialize())
        File(DATA_DIR).mkdirs()
        File(DATA_DIR, "sms_synthetic.tsv").writeText(data.joinToString("\n") { "${it.label}\t${it.text.replace("\n", " ")}" })
        val report = """
            |학습 합성 ${train.size}개 + 실제 ${real.size}개 / 시험 ${test.size}개 (학습에 안 쓴 틀)
            |정확도 ${"%.3f".format(m.accuracy)}  정밀도 ${"%.3f".format(m.precision)}  재현율 ${"%.3f".format(m.recall)}
            |오답 예시:
            |${m.wrong.take(10).joinToString("\n") { "  [${it.second}] ${"%.2f".format(model.predict(it.first))} ${it.first.replace("\n", " ")}" }}
        """.trimMargin()
        File("build/model_report.txt").writeText(report)
        println(report)
    }

    companion object {
        const val DATA_DIR = "../../data"
    }
}

data class Metrics(val accuracy: Double, val precision: Double, val recall: Double, val wrong: List<Pair<String, Int>>) {
    companion object {
        fun of(model: TextModel, set: List<Pair<String, Int>>, threshold: Float = 0.5f): Metrics {
            var tp = 0; var fp = 0; var fn = 0; var tn = 0
            val wrong = mutableListOf<Pair<String, Int>>()
            for ((text, y) in set) {
                val hit = model.predict(text) >= threshold
                when {
                    hit && y == 1 -> tp++
                    hit && y == 0 -> { fp++; wrong += text to y }
                    !hit && y == 1 -> { fn++; wrong += text to y }
                    else -> tn++
                }
            }
            return Metrics(
                (tp + tn).toDouble() / set.size,
                if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp),
                if (tp + fn == 0) 0.0 else tp.toDouble() / (tp + fn),
                wrong,
            )
        }
    }
}
