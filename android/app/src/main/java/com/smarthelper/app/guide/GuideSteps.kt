package com.smarthelper.app.guide

import android.view.accessibility.AccessibilityNodeInfo

/**
 * 안내 한 단계: 들려줄 말 + 화면에서 눌러야 할 곳을 찾는 방법.
 * dynamic 이 있으면 화면 상태를 보고 (눌러야 할 곳, 들려줄 말)을 그때그때 정한다 (예: 알람 시계판의 시→분→오전/오후).
 */
class Step(
    val say: String,
    val dynamic: ((AccessibilityNodeInfo) -> Pair<AccessibilityNodeInfo, String>?)? = null,
    val find: (AccessibilityNodeInfo) -> AccessibilityNodeInfo? = { null },
) {
    fun locate(root: AccessibilityNodeInfo): Pair<AccessibilityNodeInfo, String>? =
        dynamic?.invoke(root) ?: find(root)?.let { it to say }
}

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
    /** 화면과 상관없이 일이 끝났는지 직접 확인하는 방법 (예: 지운 앱이 정말 사라졌는지) */
    val finished: (() -> Boolean)? = null,
    /** 받는 사람 확인을 시작할 단계 (문자는 첫 화면부터, 전화는 연락처 화면부터) */
    val checkRecipientFrom: Int = 0,
    /** 받는 사람이 다를 때 할 말 */
    val wrongRecipient: String? = null,
    /**
     * 마지막 단계에서 이 글자의 버튼이 눌리면 완료. 누르는 순간 창이 닫히면(시간 선택 창, 확인 창 등)
     * 눌린 요소를 다시 읽을 수 없어서, 누름 신호에 실려 오는 글자로 판단한다.
     */
    val doneTexts: Set<String> = emptySet(),
    /**
     * 마지막 단계 화면이 사라졌을 때(창이 닫힘) 정말 끝났는지 확인하는 방법.
     * true 면 완료, false 면 취소된 것으로 보고 앞 단계부터 다시 안내한다.
     */
    val leftLast: ((AccessibilityNodeInfo) -> Boolean)? = null,
    val leftLastFail: String = "",
)

/** 모든 빈칸을 없앤다. 시계 앱은 "오후" 뒤에 아주 얇은 빈칸(U+200A)을 쓴다 */
private fun noSpace(s: String) = s.replace(Regex("[\\s\\u00A0\\u2000-\\u200B\\u202F\\u205F\\u3000]"), "")

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

    /** n 에서 levels 단계까지 조상으로 올라가며, 그 안에서 pred 에 맞는 요소를 찾는다 */
    fun near(n: AccessibilityNodeInfo, levels: Int, pred: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        var p = n
        repeat(levels) {
            p = p.parent ?: return null
            first(p, pred)?.let { return it }
        }
        return null
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
private val SEND_TEXTS = setOf("보내기", "SMS 보내기", "MMS 보내기", "전송")
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
        lost = MSG_LOST, isDone = ::isSend, doneTexts = SEND_TEXTS,
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
        lost = MSG_LOST, isDone = ::isSend, doneTexts = SEND_TEXTS,
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

    /**
     * 앱 설치: Play 스토어 → 검색 → (이름 쓰기) → 앱 고르기 → 설치.
     * 공식 스토어에서만 설치하도록 안내한다 (CLAUDE.md 안전 설계 4). 로그인 안 된 휴대폰이면 멈추고 알려 준다.
     */
    fun install(app: String?) = Guide(
        steps = listOf(
            Step("아래쪽 '검색'을 눌러 주세요.") { r ->
                Nodes.first(r) { (Nodes.desc(it, "검색") || it.text?.toString() == "검색") && !it.isEditable }?.let(Nodes::clickable)
            },
            Step("위쪽 검색 칸을 눌러 주세요.") { r ->
                Nodes.first(r) { Nodes.text(it, "앱 및 게임 검색", "Google Play 검색") || Nodes.desc(it, "앱 및 게임 검색", "Google Play 검색") }?.let(Nodes::clickable)
            },
            Step(if (app != null) "'$app'을 쓰고, 글자판의 돋보기(검색) 버튼을 눌러 주세요." else "설치할 앱 이름을 쓰고, 글자판의 돋보기(검색) 버튼을 눌러 주세요.") { r ->
                Nodes.first(r) { it.isEditable && it.isFocused }
            },
            Step(if (app != null) "찾은 목록에서 '$app'을 눌러 주세요. 이름과 만든 회사를 꼭 확인해요." else "찾은 목록에서 설치할 앱을 눌러 주세요. 이름과 만든 회사를 꼭 확인해요.") { r ->
                if (app == null) null else Nodes.first(r) { n -> n.isClickable && (Nodes.desc(n, app) || Nodes.any(n) { Nodes.text(it, app) }) && !n.isEditable }
            },
            Step("별점과 다운로드 수가 많은 공식 앱인지 확인하고, 초록색 '설치' 버튼을 눌러 주세요.") { r ->
                val isInstall = { n: AccessibilityNodeInfo -> n.text?.toString()?.trim() == "설치" || n.contentDescription?.toString()?.trim() == "설치" }
                // 검색 결과에는 앱마다 설치 버튼이 있으므로, 찾는 앱 이름 가까이에 있는 것만 고른다
                val btn = if (app == null) Nodes.first(r, isInstall)
                else Nodes.first(r) { Nodes.text(it, app) || Nodes.desc(it, app) }?.let { Nodes.near(it, 5, isInstall) }
                btn?.let(Nodes::clickable)
            },
        ),
        lost = "Play 스토어 화면으로 돌아가 주세요. 뒤로 가기를 누르면 돼요.",
        isDone = { n -> n != null && (Nodes.text(n, "설치") || Nodes.desc(n, "설치") || Nodes.any(n) { it.text?.toString()?.trim() == "설치" }) },
        doneMsg = "🎉 설치를 시작했어요! 다 되면 '열기' 버튼이 생겨요. 홈 화면에서도 새 앱을 찾을 수 있어요.",
        unsupported = { r ->
            if (Nodes.first(r) { Nodes.text(it, "로그인하고 최신 Android 앱") } != null)
                "Play 스토어를 쓰려면 먼저 구글 계정으로 로그인해야 해요. 가족에게 도움을 받아 로그인한 뒤 다시 해 보세요." else null
        },
    )

    /** 앱 지우기: 설정 → 앱 → (모두 보기) → 지울 앱 → 제거 → 확인. installed: 그 앱이 아직 깔려 있는지 */
    fun uninstall(app: String, installed: () -> Boolean) = Guide(
        finished = { !installed() },
        steps = listOf(
            Step("'앱'을 눌러 주세요.") { r -> Nodes.row(r, "앱", "애플리케이션") },
            Step("'앱 모두 보기'를 눌러 주세요.") { r ->
                Nodes.first(r) { n -> n.text?.toString()?.let { it.startsWith("앱 ") && it.endsWith("모두 보기") || it == "모든 앱 보기" } == true }?.let(Nodes::clickable)
            },
            Step("목록에서 '$app'을 찾아 눌러 주세요. 안 보이면 화면을 위로 밀어 보세요.") { r -> Nodes.row(r, app) },
            Step("'제거' 버튼을 눌러 주세요. 지운 앱은 다시 설치해야 쓸 수 있어요.") { r ->
                // 앱 정보 화면(제목에 앱 이름이 보일 때)의 제거 버튼만
                if (Nodes.first(r) { it.text?.toString()?.trim() == app } == null) null
                else Nodes.first(r) { n -> n.text?.toString()?.trim() in setOf("제거", "삭제") }?.let(Nodes::clickable)
            },
            Step("정말 지울지 물어봐요. '$app'이 맞으면 '확인'을 눌러 주세요.") { r ->
                if (Nodes.first(r) { Nodes.text(it, "제거하시겠습니까", "삭제하시겠습니까", "삭제할까요", "제거할까요") } == null) null
                else Nodes.first(r) { n -> n.text?.toString()?.trim() in setOf("확인", "제거", "삭제") && n.isClickable }
            },
        ),
        lost = "찾는 메뉴가 안 보이면 화면을 위로 살짝 밀어 보세요. 다른 화면으로 갔다면 뒤로 가기를 눌러요.",
        isDone = { n -> n != null && n.text?.toString()?.trim() in setOf("확인", "제거", "삭제") }, doneTexts = setOf("확인", "제거", "삭제"),
        doneMsg = "🎉 '$app'을 지웠어요. 다시 쓰고 싶으면 Play 스토어에서 설치하면 돼요. 화면 보기는 끝낼게요.",
    )

    /**
     * 알람: 시계 앱 → 알람 탭 → ＋ → 시계판에서 시 → 분 → 오전/오후 → 확인.
     * hour(0~23)를 알면 시계판 상태를 보고 다음에 누를 숫자를 그때그때 짚어 준다. 모르면(-1) 직접 고르게 한다.
     */
    fun alarm(hour: Int, minute: Int, alarmSet: () -> Boolean): Guide {
        val known = hour >= 0
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        val am = hour < 12
        val label = "${if (am) "오전" else "오후"} ${h12}시" + if (minute > 0) " ${minute}분" else ""
        val idOrDesc = { n: AccessibilityNodeInfo, id: String -> Nodes.id(n, id) }
        /** 시간 고르기 창이면 (시 숫자, 분 숫자) */
        val picker = { r: AccessibilityNodeInfo ->
            Nodes.first(r) { idOrDesc(it, "material_hour_tv") }?.let { h -> h to Nodes.first(r) { idOrDesc(it, "material_minute_tv") } }
        }
        /** 아직 맞지 않은 것 하나를 골라 (누를 곳, 말) */
        val next = { r: AccessibilityNodeInfo ->
            val (hTv, mTv) = picker(r) ?: (null to null)
            when {
                hTv == null || !known -> null
                hTv.text?.toString()?.toIntOrNull() != h12 ->
                    Nodes.first(r) { it.contentDescription?.toString() == "${h12}시 정각" }?.let { it to "시계판에서 '$h12'를 눌러 주세요." }
                        ?: (hTv to "위쪽 왼쪽의 '시' 숫자를 눌러 주세요.")
                mTv != null && mTv.text?.toString()?.toIntOrNull() != minute ->
                    Nodes.first(r) { it.contentDescription?.toString() == "${minute}분" }?.let { it to "이번엔 분이에요. 시계판에서 '${"%02d".format(minute)}'을 눌러 주세요." }
                        ?: (mTv to "위쪽 오른쪽의 '분' 숫자를 눌러 주세요.")
                else -> Nodes.first(r) { idOrDesc(it, if (am) "period_am_button" else "period_pm_button") }
                    ?.takeIf { !it.isChecked }?.let { it to "'${if (am) "오전" else "오후"}'을 눌러 주세요." }
            }
        }
        val okBtn = { n: AccessibilityNodeInfo -> idOrDesc(n, "timepicker_ok_button") || (n.isClickable && n.text?.toString()?.trim() in setOf("확인", "저장")) }
        return Guide(
            steps = listOf(
                Step("아래쪽 '알람'을 눌러 주세요.") { r -> Nodes.first(r) { (idOrDesc(it, "tab_menu_alarm") || it.contentDescription?.toString() == "알람") && it.isClickable } },
                Step("가운데 아래 '＋' 버튼을 눌러 주세요. 새 알람을 만들어요.") { r -> Nodes.first(r) { Nodes.desc(it, "알람 추가") && it.isClickable } },
                Step("시계판에서 시간을 맞춰 주세요.", dynamic = { r -> next(r) }),
                Step(if (known) "'$label'${if (minute > 0) "으로" else "로"} 맞춰졌으면 '확인'을 눌러 주세요." else "원하는 시간을 고르고 '확인'을 눌러 주세요.") { r ->
                    if (picker(r) == null || next(r) != null) null else Nodes.first(r, okBtn)
                },
            ),
            lost = "시계 앱으로 돌아가 주세요. 다른 화면으로 갔다면 뒤로 가기를 눌러요.",
            isDone = { n -> n != null && okBtn(n) }, doneTexts = setOf("확인", "저장"),
            // 새로 만든 알람은 펼쳐진 채('라벨 추가' 칸과 함께) 보인다. 그 카드의 시각이 맞으면 완료.
            // 휴대폰의 다음 알람 정보(alarmSet)도 함께 본다.
            leftLast = { r ->
                alarmSet() || (known && Nodes.first(r) { Nodes.id(it, "edit_label") }?.let { label ->
                    Nodes.near(label, 3) { n -> Nodes.id(n, "digital_clock") && n.text?.toString()?.let(::noSpace) == noSpace("${if (am) "오전" else "오후"}$h12:${"%02d".format(minute)}") }
                } != null)
            },
            leftLastFail = "알람이 저장되지 않았어요. '취소'를 누르셨다면 다시 '＋'부터 해 볼게요.",
            doneMsg = if (known) "🎉 $label 알람을 맞췄어요! 화면 보기는 끝낼게요." else "🎉 알람을 맞췄어요! 화면 보기는 끝낼게요.",
        )
    }

    /** 전화: 전화 앱 → 연락처 탭 → 이름 → 통화. 받는 사람은 연락처 화면(통화 단계)에서 확인한다 */
    fun call(name: String, number: String): Guide {
        val callBtn = { n: AccessibilityNodeInfo ->
            Nodes.id(n, "verb_call") || (n.isClickable && (n.text?.toString()?.trim() == "통화" || n.contentDescription?.toString()?.trim() == "통화"))
        }
        return Guide(
            steps = listOf(
                Step("아래쪽 '연락처'를 눌러 주세요.") { r ->
                    Nodes.first(r) { (Nodes.id(it, "tab_contacts") || it.contentDescription?.toString() == "연락처" || it.text?.toString() == "연락처") && it.isClickable }
                },
                Step("목록에서 '$name' 님을 찾아 눌러 주세요. 안 보이면 화면을 위로 밀어 보세요.") { r -> Nodes.row(r, name) },
                Step("전화기 모양의 '통화' 버튼을 눌러 주세요. 끊을 때는 빨간 버튼을 눌러요.") { r -> Nodes.first(r, callBtn) },
            ),
            lost = "전화 앱으로 돌아가 주세요. 다른 화면으로 갔다면 뒤로 가기를 눌러요.",
            isDone = { n -> n != null && callBtn(n) }, doneTexts = setOf("통화"),
            doneMsg = "🎉 $name 님께 전화를 걸고 있어요! 끊을 때는 빨간 버튼을 누르세요. 화면 보기는 끝낼게요.",
            recipient = name to number, checkRecipientFrom = 2,
            wrongRecipient = "지금 열린 연락처는 $name 님이 아니에요. 엉뚱한 분께 걸지 않도록 안내를 멈췄어요. 다시 말씀해 주세요.",
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
