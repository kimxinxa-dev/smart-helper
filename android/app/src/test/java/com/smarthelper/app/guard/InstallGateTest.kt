package com.smarthelper.app.guard

import com.smarthelper.app.guard.InstallGate.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallGateTest {
    private val G = "com.google.android.packageinstaller"
    private val S = "com.android.settings"
    private val MIN = 60_000L

    // 설치 화면인지
    @Test fun 설치앱_질문글자() = assertEquals(Screen.INSTALL, InstallGate.screen(G, listOf("연습용 퍼즐", "이 앱을 설치하시겠습니까?", "취소", "설치")))
    @Test fun 설치앱_설치버튼만() = assertEquals(Screen.INSTALL, InstallGate.screen(G, listOf("연습용 퍼즐", "설치")))
    @Test fun 설치앱이어도_글자없으면_아님() = assertEquals(null, InstallGate.screen(G, listOf("연습용 퍼즐", "이 앱을 삭제할까요?", "취소", "확인")))
    @Test fun 다른앱의_설치글자는_아님() = assertEquals(null, InstallGate.screen("com.android.vending", listOf("건강 도우미", "설치")))
    @Test fun 삼성_설치앱도_인식() = assertEquals(Screen.INSTALL, InstallGate.screen("com.samsung.android.packageinstaller", listOf("이 앱을 설치할까요?")))
    @Test fun 처음보는_설치앱이름도_인식() = assertEquals(Screen.INSTALL, InstallGate.screen("com.vendor.packageinstaller", listOf("설치")))
    @Test fun 영어_설치화면() = assertEquals(Screen.INSTALL, InstallGate.screen(G, listOf("Do you want to install this app?")))

    // 알 수 없는 출처 허용 화면
    @Test fun 설정_알수없는앱설치() = assertEquals(Screen.UNKNOWN_SOURCE, InstallGate.screen(S, listOf("알 수 없는 앱 설치", "Chrome", "이 출처 허용")))
    @Test fun 설치앱이_띄운_출처허용창() = assertEquals(Screen.UNKNOWN_SOURCE, InstallGate.screen(G, listOf("보안을 위해 이 출처의 알 수 없는 앱을 설치할 수 없도록 설정되어 있습니다.", "설정")))
    @Test fun 설정_다른화면은_아님() = assertEquals(null, InstallGate.screen(S, listOf("디스플레이", "글자 크기와 스타일")))

    // 위험 시간대 (30분)
    @Test fun 위험메시지_없으면_아님() = assertFalse(InstallGate.inRiskWindow(100 * MIN, null))
    @Test fun 받은지_29분() = assertTrue(InstallGate.inRiskWindow(29 * MIN, 0))
    @Test fun 받은지_31분() = assertFalse(InstallGate.inRiskWindow(31 * MIN, 0))
    @Test fun 정확히_30분은_끝남() = assertFalse(InstallGate.inRiskWindow(30 * MIN, 0))

    // 경고를 띄울지 + 5분 유예
    @Test fun 위험시간대_설치화면이면_막음() = assertTrue(InstallGate.shouldBlock(Screen.INSTALL, 10 * MIN, 0, 0))
    @Test fun 평소에는_막지않음() = assertFalse(InstallGate.shouldBlock(Screen.INSTALL, 10 * MIN, null, 0))
    @Test fun 설치화면_아니면_막지않음() = assertFalse(InstallGate.shouldBlock(null, 10 * MIN, 0, 0))
    @Test fun 그래도진행_4분뒤는_막지않음() {
        val until = InstallGate.snoozeUntil(10 * MIN)
        assertFalse(InstallGate.shouldBlock(Screen.INSTALL, 14 * MIN, 0, until))
    }
    @Test fun 그래도진행_5분뒤는_다시막음() {
        val until = InstallGate.snoozeUntil(10 * MIN)
        assertTrue(InstallGate.shouldBlock(Screen.INSTALL, 15 * MIN, 0, until))
    }
}
