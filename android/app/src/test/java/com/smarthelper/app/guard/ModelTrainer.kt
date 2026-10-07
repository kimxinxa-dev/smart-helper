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
        // 실제 문자 중 고정 시험 문제와 같은 문자는 빼서 시험이 새지 않게 한다
        val hold = (holdout + finalTest).map { it.first }.toSet()
        val real = readTsv("sms_real.tsv").filter { it.first !in hold }

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
        |
            |${holdoutReport(model)}
        """.trimMargin()
        File("build/model_report.txt").writeText(report)
        println(report)
    }

    companion object {
        const val DATA_DIR = "../../data"

        /** "라벨<TAB>문자" 파일 읽기. 없으면 빈 목록 */
        fun readTsv(name: String): List<Pair<String, Int>> =
            File(DATA_DIR, name).takeIf { it.exists() }?.readLines(Charsets.UTF_8)
                ?.mapNotNull { l -> l.split('\t', limit = 2).takeIf { it.size == 2 }?.let { it[1] to it[0].trim().toInt() } }
                .orEmpty()

        /** 고정 시험 문제(개발용). 학습 틀과 따로 쓴 문자. 틀린 문자를 보고 규칙·틀을 고치는 데 쓴다 */
        val holdout by lazy { readTsv("sms_holdout.tsv") }

        /** 최종 시험 문제. 개선 작업 중에는 보지 않고 마지막에 점수만 확인한다. 둘 다 학습에 절대 넣지 않는다 */
        val finalTest by lazy { readTsv("sms_final_test.tsv") }

        fun holdoutReport(model: TextModel) = report("개발용 시험", holdout, model) + "\n\n" + report("최종 시험", finalTest, model)

        private fun report(title: String, set: List<Pair<String, Int>>, model: TextModel): String {
            val ai = Metrics.of(set) { model.predict(it) >= 0.6f }
            val app = engineMetrics(model, set)
            fun line(m: Metrics) = "정확도 ${"%.3f".format(m.accuracy)}  정밀도 ${"%.3f".format(m.precision)}  재현율 ${"%.3f".format(m.recall)}"
            fun tag(y: Int) = if (y == 1) "사기" else "정상"
            return """
                |$title ${set.size}개 (사기 ${set.count { it.second == 1 }} / 정상 ${set.count { it.second == 0 }})
                |AI 모델만 (60% 이상 경고)  ${line(ai)}
                |앱 전체 (규칙+AI, 주의 이상 경고)  ${line(app)}
                |앱 전체가 틀린 문자:
                |${app.wrong.joinToString("\n") { "  [${tag(it.second)}] AI ${(model.predict(it.first) * 100).toInt()}% ${it.first}" }}
            """.trimMargin()
        }

        /** 앱과 같은 판단(SmishingEngine: 규칙 + AI)으로 채점. 주의(MID) 이상이면 경고로 본다 */
        fun engineMetrics(model: TextModel, set: List<Pair<String, Int>>): Metrics {
            val saved = SmishingEngine.detectors.toList()
            SmishingEngine.detectors.retainAll { it === RuleDetector }
            SmishingEngine.detectors += ModelDetector(model)
            try {
                return Metrics.of(set) { SmishingEngine.check(it, savedContact = false).level >= Level.MID }
            } finally {
                SmishingEngine.detectors.clear(); SmishingEngine.detectors += saved
            }
        }
    }
}

data class Metrics(val accuracy: Double, val precision: Double, val recall: Double, val wrong: List<Pair<String, Int>>) {
    companion object {
        fun of(model: TextModel, set: List<Pair<String, Int>>, threshold: Float = 0.5f) = of(set) { model.predict(it) >= threshold }

        /** hit(문자) = 사기라고 판단했는가 */
        fun of(set: List<Pair<String, Int>>, isScam: (String) -> Boolean): Metrics {
            var tp = 0; var fp = 0; var fn = 0; var tn = 0
            val wrong = mutableListOf<Pair<String, Int>>()
            for ((text, y) in set) {
                val hit = isScam(text)
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
