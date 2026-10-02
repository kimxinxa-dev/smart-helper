package com.smarthelper.app.guide

import android.view.accessibility.AccessibilityNodeInfo

/** 안내 한 단계: 들려줄 말 + 화면에서 눌러야 할 곳을 찾는 방법 */
class Step(val say: String, val find: (AccessibilityNodeInfo) -> AccessibilityNodeInfo?)

/**
 * 실제 휴대폰 화면 안내 하나.
 * 뒤 단계의 버튼은 앞 단계를 마쳐야 화면에 나타나므로, "찾을 수 있는 가장 뒤 단계"가 지금 단계다.
 */
class Guide(
    val steps: List<Step>,
    /** 단계를 못 찾을 때(다른 화면으로 갔을 때) 말풍선 */
    val lost: String,
    /** 마지막 단계에서 눌린 요소가 '완료'인지 */
    val isDone: (AccessibilityNodeInfo?) -> Boolean,
    val doneMsg: String,
    /** 이 휴대폰에서 할 수 없는 상황이면 그 이유 (예: 에뮬레이터의 사진 문자) */
    val unsupported: (AccessibilityNodeInfo) -> String? = { null },
    /** 받는 사람 (이름, 번호). 있으면 대화창이 맞는 사람인지 먼저 확인한다 */
    val recipient: Pair<String, String>? = null,
    /** 마지막 단계에서 첫 단계 화면으로 돌아오면 완료로 본다 (문자를 보내면 입력 칸이 다시 비는 경우) */
    val doneWhenBackToStart: Boolean = false,
)

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

    /** 설정 메뉴처럼 제목 글자가 정확히 같은 줄을 찾아, 눌리는 줄 전체를 돌려준다 */
    fun row(root: AccessibilityNodeInfo, vararg titles: String): AccessibilityNodeInfo? =
        first(root) { n -> n.text?.toString()?.trim() in titles }?.let(::clickable)

    /** 눌리는 조상까지 올라간다 (제목 글자 자체는 눌리지 않는 경우가 많다) */
    fun clickable(n: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var p: AccessibilityNodeInfo? = n
        while (p != null && !p.isClickable) p = p.parent
        return p ?: n
    }

    fun any(n: AccessibilityNodeInfo, pred: (AccessibilityNodeInfo) -> Boolean): Boolean {
        if (pred(n)) return true
        for (i in 0 until n.childCount) if (n.getChild(i)?.let { any(it, pred) } == true) return true
        return false
    }
}

/** 문자 앱 공통 (구글 메시지 기준, 삼성 메시지는 설명 글자로 함께 찾는다) */
private val SEND = { n: AccessibilityNodeInfo -> Nodes.desc(n, "보내기", "전송", "send") && !Nodes.desc(n, "음성", "voice") }
/** 누르는 순간 입력 칸이 비면서 버튼 설명이 '음성 메시지'로 바뀌므로, 마지막 단계에서는 이름표(Send)만 봐도 보내기로 친다 */
private fun isSend(n: AccessibilityNodeInfo?) = n != null && (SEND(n) || Nodes.id(n, "Send"))
private const val MSG_LOST = "문자 보내는 화면으로 돌아가 주세요. 뒤로 가기를 누르면 돼요."
private const val SETTINGS_LOST = "찾는 메뉴가 안 보이면 화면을 위로 살짝 밀어 보세요. 다른 화면으로 갔다면 뒤로 가기를 눌러요."

object Guides {
    /** 문자로 사진 보내기: 사진 버튼 → 사진 고르기 → 보내기 */
    fun photo(name: String, number: String) = Guide(
        steps = listOf(
            Step("사진 모양 버튼을 눌러 주세요. 노란 테두리가 있는 곳이에요.") { r ->
                Nodes.first(r) { Nodes.id(it, "Gallery") || Nodes.desc(it, "미디어 첨부", "갤러리", "사진 첨부", "attach media", "gallery") }
            },
            Step("보낼 사진을 눌러 주세요. 가장 최근 사진이 맨 앞에 있어요.") { r ->
                Nodes.first(r) { it.isClickable && Nodes.desc(it, "이미지 항목", "사진 항목", "image item", "photo") }
            },
            Step("사진이 들어갔어요. 이제 보내기 버튼을 눌러 주세요.") { r -> Nodes.first(r, SEND) },
        ),
        lost = MSG_LOST, isDone = ::isSend,
        doneMsg = "🎉 사진을 보냈어요! 잘 하셨어요. 화면 보기도 끝냈어요.",
        unsupported = { r ->
            if (Nodes.first(r) { Nodes.text(it, "첨부파일이 지원되지") } != null)
                "이 휴대폰은 사진 문자를 보낼 수 없게 설정되어 있어요. '확인'을 누르고, 통신사에 사진 문자(MMS)를 물어보세요." else null
        },
        recipient = name to number, doneWhenBackToStart = true,
    )

    /** 문자 보내기: 입력 칸에 쓰기 → 보내기 */
    fun text(name: String, number: String) = Guide(
        steps = listOf(
            Step("아래 '문자 메시지' 칸을 눌러 보낼 말을 써 주세요. 글자판이 나오면 천천히 눌러요.") { r ->
                Nodes.first(r) { Nodes.id(it, "compose_message_text", "message_edit", "editor") || (it.isEditable && Nodes.text(it, "문자 메시지", "메시지 입력")) }
            },
            Step("다 쓰셨으면 오른쪽 보내기 버튼을 눌러 주세요.") { r -> Nodes.first(r, SEND) },
        ),
        lost = MSG_LOST, isDone = ::isSend,
        doneMsg = "🎉 문자를 보냈어요! 잘 하셨어요. 화면 보기도 끝냈어요.",
        recipient = name to number, doneWhenBackToStart = true,
    )

    /** 글자 크기: 설정 → 디스플레이 → 디스플레이 크기 및 텍스트 → 글꼴 크기 확대/축소 */
    fun font(up: Boolean): Guide {
        val btn = if (up) "확대" else "축소"
        // 아래 '디스플레이 크기'에도 같은 확대/축소 버튼이 있으므로 '글꼴(글자) 크기' 줄에 있는 것만 고른다
        val inFontRow = { n: AccessibilityNodeInfo -> n.parent?.let { p -> Nodes.any(p) { Nodes.desc(it, "글꼴 크기", "글자 크기") } } == true }
        return Guide(
            steps = listOf(
                Step("'디스플레이'를 눌러 주세요. 글자 크기가 여기 있어요.") { r -> Nodes.row(r, "디스플레이 및 터치", "디스플레이") },
                Step("'디스플레이 크기 및 텍스트'를 눌러 주세요.") { r -> Nodes.row(r, "디스플레이 크기 및 텍스트", "글자 크기와 스타일", "글꼴 크기 및 스타일") },
                Step(if (up) "'글꼴 크기' 오른쪽의 '＋' 버튼을 눌러 주세요. 누를 때마다 글자가 커져요." else "'글꼴 크기' 왼쪽의 '－' 버튼을 눌러 주세요. 누를 때마다 글자가 작아져요.") { r ->
                    Nodes.first(r) { Nodes.desc(it, btn) && it.isClickable && inFontRow(it) }
                },
            ),
            lost = SETTINGS_LOST,
            isDone = { n -> n != null && Nodes.desc(n, btn) && inFontRow(n) },
            doneMsg = if (up) "🎉 글자가 커졌어요! 더 키우고 싶으면 한 번 더 누르세요. 화면 보기는 끝낼게요." else "🎉 글자가 작아졌어요! 화면 보기는 끝낼게요.",
        )
    }

    /** 와이파이: 설정 → 네트워크 및 인터넷 → 인터넷 → (꺼져 있으면 켜기) → 와이파이 이름 */
    fun wifi() = Guide(
        steps = listOf(
            Step("'네트워크 및 인터넷'을 눌러 주세요. 와이파이가 여기 있어요.") { r -> Nodes.row(r, "네트워크 및 인터넷", "연결") },
            Step("'인터넷'을 눌러 주세요.") { r -> Nodes.row(r, "인터넷", "Wi-Fi") },
            Step("와이파이가 꺼져 있어요. 'Wi-Fi' 옆 스위치를 눌러 켜 주세요.") { r ->
                Nodes.first(r) { n -> n.isCheckable && !n.isChecked && (Nodes.desc(n, "Wi-Fi") || Nodes.text(n, "Wi-Fi")) }
            },
            Step("연결할 와이파이 이름을 눌러 주세요. 우리 집 와이파이 이름은 보통 공유기에 적혀 있어요.") { r ->
                Nodes.first(r) { it.isClickable && Nodes.desc(it, "Wi-Fi 신호", "신호 강도") && !Nodes.desc(it, "연결됨") }
                    ?: Nodes.first(r) { it.isClickable && Nodes.desc(it, "Wi-Fi 신호", "신호 강도") }
            },
        ),
        lost = SETTINGS_LOST,
        isDone = { n -> n != null && Nodes.desc(n, "Wi-Fi 신호", "신호 강도") },
        doneMsg = "와이파이를 골랐어요. 비밀번호를 묻는 창이 나오면 공유기에 적힌 비밀번호를 쓰고 '연결'을 누르세요. 화면 보기는 끝낼게요.",
    )
}
