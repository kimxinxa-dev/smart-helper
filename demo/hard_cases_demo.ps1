# 스마트 헬퍼 데모: 판단하기 어려운 문자 수신 → 감지 → 팝업 → 위험 링크 차단
# 실행: 같은 폴더의 "어려운_문자_시연.bat" 을 두 번 누르세요.
# -Auto : 메뉴 없이 모든 시나리오의 판정만 자동으로 점검 (링크는 열지 않음)
param([switch]$Auto)

$adb = "C:\Android\Sdk\platform-tools\adb.exe"
# adb 가 보내는 글자(UTF-8)를 그대로 읽는다. .bat 으로 연 창은 한글을 CP949 로 읽어서, 판정 기록 속 한글이 깨져 읽기에 실패했다
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$app = "com.smarthelper.app"
$script:fail = 0

$LEVEL = @{ HIGH = "🚨 위험"; MID = "⚠️ 주의"; LOW = "✅ 낮음"; SKIP = "연락처 번호(검사 안 함)" }

function Pause-Step($msg) {
    if ($Auto) { return }
    Write-Host ""
    Read-Host "  ▶ $msg [Enter]" | Out-Null
}

function New-Number { "010" + (Get-Random -Minimum 20000000 -Maximum 99999999) }

# 스마트 헬퍼가 남긴 가장 최근 판정 (디버그 앱에서만 읽을 수 있음)
function Get-Latest {
    $raw = & $adb shell run-as $app cat shared_prefs/guard.xml 2>$null
    if (-not $raw) { return $null }
    try {
        $x = [xml]($raw -join "`n")
        $j = ($x.map.string | Where-Object { $_.name -eq 'history' }).'#text'
        if (-not $j) { return $null }
        return ($j | ConvertFrom-Json)[0]
    } catch { return $null }
}

# 문자를 보내고 판정이 나올 때까지 기다렸다가 기대 결과와 함께 보여 준다
function Send-And-Show($from, $text, $expect) {
    $before = Get-Latest
    $beforeId = if ($before) { $before.id } else { 0 }
    & $adb emu sms send $from $text | Out-Null
    Write-Host ""
    Write-Host "  📩 $from 님이 보낸 문자" -ForegroundColor Cyan
    Write-Host "     $text"
    $v = $null
    for ($i = 0; $i -lt 15; $i++) {
        Start-Sleep 1
        $v = Get-Latest
        if ($v -and $v.id -ne $beforeId) { break }
        $v = $null
    }
    if (-not $v) { Write-Host "  (판정 결과를 읽지 못했어요. 휴대폰 화면을 확인하세요)" -ForegroundColor DarkYellow; return }
    $ok = ($v.level -eq $expect)
    if (-not $ok) { $script:fail++ }
    $mark = if ($ok) { "✔ 기대대로" } else { "✘ 기대와 다름 (기대: $($LEVEL[$expect]))" }
    $color = switch ($v.level) { "HIGH" { "Red" } "MID" { "Yellow" } default { "Green" } }
    Write-Host "  → 스마트 헬퍼 판정: $($LEVEL[$v.level])  $mark" -ForegroundColor $color
    foreach ($r in $v.reasons) { Write-Host "       · $r" }
    if ($v.level -eq "HIGH") { Write-Host "  👉 휴대폰에 문자 수신 알림이 먼저 오고, 약 2초 뒤 🚨 팝업과 경고 알림이 떠요. [알겠어요] 로 닫으세요." -ForegroundColor Magenta
        Write-Host "     (에뮬레이터의 문자 앱은 수신 알림이 6~8초 늦게 와요. 실제 휴대폰은 더 빨라요)" -ForegroundColor DarkGray }
}

# 위험 문자 속 링크를 실수로 누른 상황
function Open-Link($url) {
    if ($Auto) { return }
    Write-Host ""
    Write-Host "  🔗 이제 문자 속 링크를 실수로 누른 상황을 보여 줘요." -ForegroundColor Yellow
    Write-Host "     · 직접 하려면: 알림창을 내려 메시지 알림의 [링크 열기] 를 누르세요." -ForegroundColor Yellow
    $a = Read-Host "     · 자동으로 크롬에서 열려면 Y, 건너뛰려면 Enter"
    if ($a -match '^[Yy]') {
        & $adb shell am start -a android.intent.action.VIEW -d $url -p com.android.chrome | Out-Null
        Write-Host "  크롬으로 열었어요. 주소창에 주소가 뜨면 화면 전체가 빨간 경고로 가려져요." -ForegroundColor Cyan
        Write-Host "  (에뮬레이터 크롬은 느려서 10초쯤 걸릴 수 있어요) → [안전하게 나가기] 를 누르세요." -ForegroundColor Cyan
    }
}

function Scenario-Family {
    Write-Host ""
    Write-Host "■ 1. 지인 사칭 대화 (가장 어려움): 링크도 사기 단어도 없는 첫 문자 → 몇 통 뒤 요구" -ForegroundColor White
    Write-Host "   내용만 보면 AI 도 50% 로 애매한 문자예요. '처음 온 번호'와 '대화 흐름' 으로 잡아요."
    $n = New-Number
    Pause-Step "첫 문자 보내기"
    Send-And-Show $n "할머니 저 손자 준서예요 새로 개통한 번호라 저장 부탁드려요 통화는 나중에 할게요" "MID"
    Pause-Step "같은 번호에서 두 번째 문자 보내기"
    Send-And-Show $n "할머니 혹시 지금 편의점 가실 수 있어요? 제가 급해서 그런데 부탁 하나만 드릴게요" "MID"
    Pause-Step "같은 번호에서 세 번째 문자 (기프트카드 + 평범해 보이는 .com 링크) 보내기"
    Send-And-Show $n "할머니 편의점에서 구글 기프트카드 사서 여기에 번호만 넣어 주시면 돼요 http://gift-check.com/a7" "HIGH"
    Write-Host "  💡 gift-check.com 은 주소 모양으로는 평범해요. 앞의 대화를 기억해서 막아요." -ForegroundColor DarkCyan
    Open-Link "http://gift-check.com/a7"
}

function Scenario-Shop {
    Write-Host ""
    Write-Host "■ 2. 진짜 쇼핑몰 같은 배송 문자: 공손한 말투 + .com 주소" -ForegroundColor White
    Pause-Step "문자 보내기"
    Send-And-Show (New-Number) "[Web발신] 고객님, 주문하신 상품의 배송지 정보가 누락되어 출고가 보류되었습니다. 배송지 입력 https://coupang-delivery.com/r8" "HIGH"
    Open-Link "https://coupang-delivery.com/r8"
}

function Scenario-Wedding {
    Write-Host ""
    Write-Host "■ 3. 지인이 보낸 것 같은 모바일 청첩장" -ForegroundColor White
    Pause-Step "문자 보내기"
    Send-And-Show (New-Number) "모바일 청첩장 보내드려요. 꼭 와 주세요 https://our-wedding.com/kim" "HIGH"
    Open-Link "https://our-wedding.com/kim"
}

function Scenario-Prosecutor {
    Write-Host ""
    Write-Host "■ 4. 링크 없는 검찰 수사관 사칭 (링크 검사만 하는 앱은 놓쳐요)" -ForegroundColor White
    Pause-Step "문자 보내기"
    Send-And-Show (New-Number) "서울중앙지검 김민수 수사관입니다. 고객님 명의 계좌가 사건에 연루되어 확인이 필요합니다. 이 번호로 회신 바랍니다" "HIGH"
}

function Scenario-Normal {
    Write-Host ""
    Write-Host "■ 5. 정상 문자 수신: 평소 받는 문자는 경고 없이 그대로 와요" -ForegroundColor White
    Write-Host "   병원·카드·은행·인증번호·구청·아파트·택배 문자가 차례로 와요. 문자 앱 알림만 뜨고, 스마트 헬퍼 경고와 팝업은 뜨지 않아요."
    $normal = @(
        @("15881234", "[한마음병원] 내일 오전 10시 내과 진료 예약이 확인되었습니다. 변경은 병원으로 연락 바랍니다."),
        @("15881688", "[KB국민카드] 김순자님 32,500원 승인 이마트 일시불"),
        @("15882100", "[농협] 입금 300,000원 잔액 1,240,500원 김순자"),
        @("15993333", "[카카오] 인증번호 [482910]를 입력해 주세요. 타인에게 절대 알려주지 마세요."),
        @("0233322114", "[마포구청] 내일 한파주의보 발효. 외출을 자제하고 따뜻한 옷차림 하세요."),
        @("0233225577", "[햇살아파트] 관리사무소입니다. 내일 오전 9시부터 12시까지 단수 예정이니 물을 받아 두세요."),
        @("15880011", "[CJ대한통운] 고객님의 상품이 오늘 배송 완료되었습니다. 배송 조회 https://www.cjlogistics.com")
    )
    foreach ($m in $normal) {
        Pause-Step "정상 문자 보내기"
        Send-And-Show $m[0] $m[1] "LOW"
    }
    Write-Host "  💡 마지막 CJ 문자는 링크가 있어도 공식 사이트 주소라서 경고하지 않아요. (2번의 coupang-delivery.com 같은 흉내 주소와 비교)" -ForegroundColor DarkCyan
    if (-not $Auto) {
        $a = Read-Host "  진짜 CJ대한통운 사이트를 크롬에서 열어 보려면 Y, 건너뛰려면 Enter"
        if ($a -match '^[Yy]') {
            & $adb shell am start -a android.intent.action.VIEW -d "https://www.cjlogistics.com" -p com.android.chrome | Out-Null
            Write-Host "  경고 없이 그대로 열려요." -ForegroundColor Green
        }
    }
}

# ── 준비
Write-Host ""
Write-Host "=== 스마트 헬퍼 데모: 판단하기 어려운 문자 → 감지 → 팝업 → 위험 링크 차단 ===" -ForegroundColor Yellow
if (-not (Test-Path $adb)) { Write-Host "adb 를 찾을 수 없어요: $adb" -ForegroundColor Red; exit 1 }
if (-not (& $adb devices | Select-String "emulator-\d+\s+device")) { Write-Host "가상 휴대폰(에뮬레이터)이 켜져 있지 않아요. 먼저 켜 주세요." -ForegroundColor Red; exit 1 }
if (-not (& $adb shell pm list packages $app)) { Write-Host "가상 휴대폰에 스마트 헬퍼가 설치되어 있지 않아요." -ForegroundColor Red; exit 1 }
# 팝업·링크 차단은 접근성 서비스가 켜져 있어야 동작한다 (앱을 다시 설치하면 꺼진다)
& $adb shell settings put secure enabled_accessibility_services "$app/$app.guide.GuideService"
# 문자 앱의 수신 알림이 뜬 것을 보고 그 뒤에 경고·팝업을 띄우려면 알림 읽기(카카오톡 지킴이)도 켜져 있어야 한다
& $adb shell cmd notification allow_listener "$app/$app.guard.MessengerListener"
& $adb shell am start -n "$app/.MainActivity" 2>$null | Out-Null
Start-Sleep 2
& $adb shell input keyevent HOME

if ($Auto) {
    Scenario-Family; Scenario-Shop; Scenario-Wedding; Scenario-Prosecutor; Scenario-Normal
    Write-Host ""
    if ($script:fail -eq 0) { Write-Host "점검 끝: 모두 기대대로예요." -ForegroundColor Green } else { Write-Host "점검 끝: 기대와 다른 판정 $($script:fail)개" -ForegroundColor Red }
    exit $script:fail
}

while ($true) {
    Write-Host ""
    Write-Host "  어떤 시연을 할까요?" -ForegroundColor Yellow
    Write-Host "   1. 지인 사칭 대화 (가장 어려움) → 팝업 → 링크 차단"
    Write-Host "   2. 진짜 쇼핑몰 같은 배송 문자 → 팝업 → 링크 차단"
    Write-Host "   3. 모바일 청첩장 → 팝업 → 링크 차단"
    Write-Host "   4. 링크 없는 검찰 사칭 → 팝업"
    Write-Host "   5. 정상 문자 수신 (병원·카드·은행·인증번호·구청·아파트·택배) → 경고 없음"
    Write-Host "   A. 1~5 차례로 모두"
    Write-Host "   Q. 끝내기"
    $c = Read-Host "  번호를 입력하세요"
    switch -Regex ($c) {
        '^1$' { Scenario-Family }
        '^2$' { Scenario-Shop }
        '^3$' { Scenario-Wedding }
        '^4$' { Scenario-Prosecutor }
        '^5$' { Scenario-Normal }
        '^[Aa]$' { Scenario-Family; Scenario-Shop; Scenario-Wedding; Scenario-Prosecutor; Scenario-Normal }
        '^[Qq]$' { Write-Host "시연을 마쳤어요."; exit 0 }
        default { Write-Host "  1~5, A, Q 중에서 골라 주세요." -ForegroundColor DarkYellow }
    }
}
