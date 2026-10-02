package com.smarthelper.app.assist

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandParserTest {
    private fun p(s: String) = CommandParser.parse(s)

    @Test fun 전화_이름() = assertEquals(Command.Call("영희", null), p("영희한테 전화해 줘"))
    @Test fun 전화_꾸밈말() = assertEquals(Command.Call("아들 민수", null), p("우리 아들 민수에게 전화 좀 걸어 줘"))
    @Test fun 전화_조사없이() = assertEquals(Command.Call("김영희", null), p("김영희 전화 걸어줘"))
    @Test fun 전화_번호() = assertEquals(Command.Call(null, "01012345678"), p("010-1234-5678로 전화해 줘"))
    @Test fun 문자_내용() = assertEquals(Command.Sms("민수", null, "이따 간다"), p("민수한테 이따 간다고 문자 보내 줘"))
    @Test fun 문자_내용_뒤에() = assertEquals(Command.Sms("영희", null, "지금 출발해"), p("영희한테 문자 보내 줘 지금 출발해"))
    @Test fun 문자_내용없음() = assertEquals(Command.Sms("영희", null, null), p("영희에게 문자 보내줘"))
    @Test fun 음량_키우기() = assertEquals(Command.Volume(true), p("소리 좀 키워 줘"))
    @Test fun 음량_안들려() = assertEquals(Command.Volume(true), p("소리가 잘 안 들려"))
    @Test fun 음량_줄이기() = assertEquals(Command.Volume(false), p("볼륨 줄여줘"))
    @Test fun 알람_아침() = assertEquals(Command.Alarm(7, 0, true), p("내일 아침 7시에 깨워 줘"))
    @Test fun 알람_오후_반() = assertEquals(Command.Alarm(15, 30, false), p("오후 3시 반에 알람 맞춰 줘"))
    @Test fun 알람_한글숫자() = assertEquals(Command.Alarm(19, 0, false), p("저녁 일곱 시에 알람"))
    @Test fun 알람_상대시간() = assertEquals(Command.Alarm(-1, 0, false, 30), p("30분 뒤에 깨워 줘"))
    @Test fun 앱_열기() = assertEquals(Command.OpenApp("카카오톡"), p("카카오톡 열어 줘"))
    @Test fun 앱_틀기() = assertEquals(Command.OpenApp("유튜브"), p("유튜브 좀 틀어줘"))
    @Test fun 앱_설치() = assertEquals(Command.Install("건강"), p("건강 앱 설치해 줘"))
    @Test fun 손전등_켜기() = assertEquals(Command.Torch(true), p("손전등 켜 줘"))
    @Test fun 손전등_끄기() = assertEquals(Command.Torch(false), p("플래시 꺼"))
    @Test fun 시간() = assertEquals(Command.Time, p("지금 몇 시야?"))
    @Test fun 날짜() = assertEquals(Command.Date, p("오늘 며칠이야"))
    @Test fun 배터리() = assertEquals(Command.Battery, p("배터리 얼마나 남았어?"))
    @Test fun 글자() = assertEquals(Command.FontSize, p("글씨를 크게 해 줘"))
    @Test fun 연락처_추가() = assertEquals(Command.AddContact("박철수", "01055556666"), p("박철수 번호 010-5555-6666 저장해 줘"))
    @Test fun 긴급() = assertEquals(Command.Emergency("119"), p("구급차 불러 줘"))
    @Test fun 번호속_112는_긴급아님() = assertEquals(Command.Call(null, "01011234567"), p("01011234567로 전화해"))
    @Test fun 송금은_하지_않음() = assertEquals(Command.Banking, p("아들한테 10만원 송금해 줘"))
    @Test fun 모르는말() = assertEquals(Command.Unknown("오늘 기분이 좋네"), p("오늘 기분이 좋네"))
}
