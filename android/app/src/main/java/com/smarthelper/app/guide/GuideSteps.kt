package com.smarthelper.app.guide

import android.view.accessibility.AccessibilityNodeInfo

/** 안내 한 단계: 들려줄 말 + 화면에서 눌러야 할 곳을 찾는 방법 */
class Step(val say: String, val find: (AccessibilityNodeInfo) -> AccessibilityNodeInfo?)

/** 화면 요소를 이름표(viewId)·설명(contentDescription)·글자(text)로 찾는 도우미 */
object Nodes {
    fun first(root: AccessibilityNodeInfo, pred: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n.isVisibleToUser && pred(n)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    fun id(n: AccessibilityNodeInfo, vararg keys: String) = n.viewIdResourceName.orEmpty().let { v -> keys.any { v.contains(it, true) } }
    fun desc(n: AccessibilityNodeInfo, vararg keys: String) = n.contentDescription?.toString().orEmpty().let { v -> keys.any { v.contains(it, true) } }
    fun text(n: AccessibilityNodeInfo, vararg keys: String) = n.text?.toString().orEmpty().let { v -> keys.any { v.contains(it, true) } }
}

/**
 * 문자로 사진 보내기 안내 (구글 메시지 기준, 삼성 메시지는 설명 글자로 함께 찾는다).
 * 뒤 단계의 버튼은 앞 단계를 마쳐야 화면에 나타나므로, "찾을 수 있는 가장 뒤 단계"가 지금 단계다.
 */
object PhotoGuide {
    val steps = listOf(
        Step("사진 모양 버튼을 눌러 주세요. 노란 테두리가 있는 곳이에요.") { r ->
            Nodes.first(r) {
                Nodes.id(it, "Gallery") || Nodes.desc(it, "미디어 첨부", "갤러리", "사진 첨부", "attach media", "gallery")
            }
        },
        Step("보낼 사진을 눌러 주세요. 가장 최근 사진이 맨 앞에 있어요.") { r ->
            Nodes.first(r) { it.isClickable && Nodes.desc(it, "이미지 항목", "사진 항목", "image item", "photo") }
        },
        Step("사진이 들어갔어요. 이제 보내기 버튼을 눌러 주세요.") { r ->
            Nodes.first(r) { Nodes.desc(it, "보내기", "전송", "send") && !Nodes.desc(it, "음성", "voice") }
        },
    )

    /** 보내기 버튼을 눌렀는지 (클릭된 것은 보통 설명이 붙은 칸의 부모 버튼) */
    fun isSend(n: AccessibilityNodeInfo?) = n != null &&
        (Nodes.id(n, "Send") || Nodes.desc(n, "보내기", "전송", "send")) && !Nodes.desc(n, "음성", "voice")

    /** 이 휴대폰에서 사진 문자를 못 보내는 경우 (예: 에뮬레이터) */
    fun unsupported(root: AccessibilityNodeInfo) = Nodes.first(root) { Nodes.text(it, "첨부파일이 지원되지") } != null
}
