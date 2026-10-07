package com.smarthelper.app.guard

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 앱에 들어가는 학습된 모델(assets)이 기준을 넘는지 확인 */
class ModelDetectorTest {
    private val model = TextModel.parse(File("src/main/assets/${ModelDetector.ASSET}").readText())

    // index.html 의 샘플 5종 — 학습 틀과 문장이 다르다
    private val smishing = listOf(
        "[Web발신]\n[한빛택배] 고객님의 택배가 주소 불명으로 반송 예정입니다. 오늘까지 주소를 확인해 주세요.\nhttp://han-bit.xyz/a8Kd2",
        "엄마 나 폰이 고장나서 친구 폰으로 문자해. 지금 급해서 그런데 이 계좌로 50만원만 송금해줘. 3333-01-1234567",
        "[Web발신]\n서울중앙지방검찰청입니다. 귀하의 계좌가 범죄에 연루되었습니다. 수사 협조를 위해 안전계좌로 즉시 이체하세요.",
        "[Web발신]\n긴급 재난지원금 대상자로 선정되셨습니다. 신청 마감 오늘까지.\nhttps://gov-support.top/apply\n본인 확인용 주민등록번호와 인증번호를 입력하세요.",
    )
    private val normal = "[Web발신]\n[한마음병원] 내일 오전 10시 내과 진료 예약이 확인되었습니다. 변경은 병원으로 전화 주세요."

    @Test fun 샘플_사기문자는_사기로() = smishing.forEach {
        assertTrue("${model.predict(it)} : $it", model.predict(it) >= 0.6f)
    }

    @Test fun 샘플_정상문자는_정상으로() = assertTrue(model.predict(normal) < 0.5f)

    @Test fun 학습에_안쓴_틀로_정확도_90퍼센트_이상() {
        val test = SmsDataset.generate().filter { it.template % 5 == 0 }.map { it.text to it.label }
        val m = Metrics.of(model, test)
        assertTrue("정확도 ${m.accuracy}", m.accuracy >= 0.9)
    }

    @Test fun 고정_시험_문제_성적() {
        File("build/holdout_report.txt").writeText(ModelTrainer.holdoutReport(model))
        assertTrue("고정 시험 문제가 있어야 함", ModelTrainer.holdout.size >= 100 && ModelTrainer.finalTest.size >= 50)
    }

    @Test fun 고정_시험_문제는_학습_데이터에_없음() {
        val train = SmsDataset.generate().map { it.text.replace("\n", " ") }.toSet()
        (ModelTrainer.holdout + ModelTrainer.finalTest).forEach { assertTrue("학습 데이터와 겹침: ${it.first}", it.first !in train) }
    }

    @Test fun 저장하고_다시_읽어도_같은_결과() {
        val again = TextModel.parse(model.serialize())
        assertTrue(Math.abs(again.predict(normal) - model.predict(normal)) < 1e-3f)
    }
}
