package com.smarthelper.app.guard

import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 문자 → 특징 변환. 학습(PC)과 판단(휴대폰)이 같은 코드를 쓴다.
 * 링크는 주소 끝자리만 남기고, 숫자는 0으로 바꾼 뒤 글자 1~3개 조각과 단어를 해시한다.
 */
object TextFeatures {
    const val DIM = 1 shl 15
    private val URL = Regex("https?://\\S+|(?:[a-z0-9-]+\\.)+[a-z]{2,}(?:/\\S*)?", RegexOption.IGNORE_CASE)
    private val IP_HOST = Regex("^[0-9.:]+$")

    fun normalize(text: String): String =
        URL.replace(text.lowercase()) { " ${urlToken(it.value)} " }
            .replace(Regex("\\d"), "0")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** 링크 하나를 "ⓤ끝자리" 토큰으로. IP 주소면 ⓤip, apk 파일이면 ⓐ 를 덧붙인다. */
    private fun urlToken(u: String): String {
        val host = u.substringAfter("://").substringBefore('/').substringBefore('?')
        val tld = if (IP_HOST.matches(host)) "ip" else host.substringAfterLast('.', "none")
        return "ⓤ$tld" + if (u.contains(".apk", ignoreCase = true)) " ⓐ" else ""
    }

    /** 특징 번호 목록과 각 값(길이로 나눠 문자 길이 영향을 줄임) */
    fun extract(text: String): Pair<IntArray, Float> {
        val t = " ${normalize(text)} "
        val set = HashSet<Int>()
        for (n in 1..3) for (i in 0..t.length - n) set += hash("c" + t.substring(i, i + n))
        for (w in t.split(' ')) if (w.isNotEmpty()) set += hash("w$w")
        return set.toIntArray() to (1f / sqrt(set.size.coerceAtLeast(1).toFloat()))
    }

    private fun hash(s: String) = (s.hashCode() and 0x7fffffff) % DIM
}

/** 로지스틱 회귀 분류기: 사기 문자일 확률(0~1)을 낸다 */
class TextModel(val bias: Float, val w: FloatArray) {

    fun predict(text: String): Float {
        val (idx, v) = TextFeatures.extract(text)
        var z = bias
        for (i in idx) z += w[i] * v
        return sigmoid(z)
    }

    /** 0이 아닌 가중치만 "번호 값" 줄로 저장 (첫 줄은 bias) */
    fun serialize(): String = buildString {
        append(bias).append('\n')
        for (i in w.indices) if (w[i] != 0f) append(i).append(' ').append("%.5f".format(java.util.Locale.ROOT, w[i])).append('\n')
    }

    companion object {
        private fun sigmoid(z: Float) = (1.0 / (1.0 + exp(-z.toDouble()))).toFloat()

        fun parse(text: String): TextModel {
            val lines = text.lineSequence().filter { it.isNotBlank() }.iterator()
            val bias = lines.next().toFloat()
            val w = FloatArray(TextFeatures.DIM)
            for (l in lines) {
                val sp = l.indexOf(' ')
                w[l.substring(0, sp).toInt()] = l.substring(sp + 1).toFloat()
            }
            return TextModel(bias, w)
        }

        /** 확률적 경사하강법(SGD)으로 학습. label 1 = 사기 문자 */
        fun train(data: List<Pair<String, Int>>, epochs: Int = 20, lr: Float = 0.5f, l2: Float = 1e-6f, seed: Int = 7): TextModel {
            val feats = data.map { TextFeatures.extract(it.first) }
            val w = FloatArray(TextFeatures.DIM)
            var bias = 0f
            val rnd = Random(seed)
            val order = data.indices.toMutableList()
            repeat(epochs) { e ->
                order.shuffle(rnd)
                val rate = lr / (1 + e * 0.2f)
                for (k in order) {
                    val (idx, v) = feats[k]
                    var z = bias
                    for (i in idx) z += w[i] * v
                    val g = sigmoid(z) - data[k].second
                    bias -= rate * g
                    for (i in idx) w[i] -= rate * (g * v + l2 * w[i])
                }
            }
            return TextModel(bias, w)
        }
    }
}
