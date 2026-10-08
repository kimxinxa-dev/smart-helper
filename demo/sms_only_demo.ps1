# 스마트 헬퍼 데모: 가상 휴대폰으로 문자만 보내 준다
# 팝업 확인, 링크 누르기, 차단 화면은 발표자가 휴대폰에서 직접 시연한다.
# 실행: 같은 폴더의 "문자만_보내기.bat" 을 두 번 누르세요.
# -Auto : 메뉴 없이 모든 문자를 차례로 보냄 (점검용)
param([switch]$Auto)

$adb = "C:\Android\Sdk\platform-tools\adb.exe"
$app = "com.smarthelper.app"

function New-Number { "010" + (Get-Random -Minimum 20000000 -Maximum 99999999) }

function Send-Sms($from, $text) {
    & $adb emu sms send $from $text | Out-Null
    Write-Host "  📩 보냄 ($from)" -ForegroundColor Cyan
    Write-Host "     $text"
}

# 시나리오마다 문자 목록. 한 시나리오 안의 문자는 같은 번호로 보낸다 (번호가 $null 이면 시나리오마다 새 번호)
$SCENARIOS = [ordered]@{
    "1" = @{ Title = "지인 사칭 대화 (가장 어려움) — 같은 번호로 3통"; From = $null; Messages = @(
        "할머니 저 손자 준서예요 새로 개통한 번호라 저장 부탁드려요 통화는 나중에 할게요",
        "할머니 혹시 지금 편의점 가실 수 있어요? 제가 급해서 그런데 부탁 하나만 드릴게요",
        "할머니 편의점에서 구글 기프트카드 사서 여기에 번호만 넣어 주시면 돼요 http://gift-check.com/a7") }
    "2" = @{ Title = "진짜 쇼핑몰 같은 배송 문자"; From = $null; Messages = @(
        "[Web발신] 고객님, 주문하신 상품의 배송지 정보가 누락되어 출고가 보류되었습니다. 배송지 입력 https://coupang-delivery.com/r8") }
    "3" = @{ Title = "모바일 청첩장"; From = $null; Messages = @(
        "모바일 청첩장 보내드려요. 꼭 와 주세요 https://our-wedding.com/kim") }
    "4" = @{ Title = "링크 없는 검찰 수사관 사칭"; From = $null; Messages = @(
        "서울중앙지검 김민수 수사관입니다. 고객님 명의 계좌가 사건에 연루되어 확인이 필요합니다. 이 번호로 회신 바랍니다") }
    "5" = @{ Title = "정상 문자 수신 — 7통 (경고 없음)"; From = "fixed"; Messages = @(
        @("15881234", "[한마음병원] 내일 오전 10시 내과 진료 예약이 확인되었습니다. 변경은 병원으로 연락 바랍니다."),
        @("15881688", "[KB국민카드] 김순자님 32,500원 승인 이마트 일시불"),
        @("15882100", "[농협] 입금 300,000원 잔액 1,240,500원 김순자"),
        @("15993333", "[카카오] 인증번호 [482910]를 입력해 주세요. 타인에게 절대 알려주지 마세요."),
        @("0233322114", "[마포구청] 내일 한파주의보 발효. 외출을 자제하고 따뜻한 옷차림 하세요."),
        @("0233225577", "[햇살아파트] 관리사무소입니다. 내일 오전 9시부터 12시까지 단수 예정이니 물을 받아 두세요."),
        @("15880011", "[CJ대한통운] 고객님의 상품이 오늘 배송 완료되었습니다. 배송 조회 https://www.cjlogistics.com")) }
}

function Run-Scenario($key) {
    $s = $SCENARIOS[$key]
    Write-Host ""
    Write-Host "■ $key. $($s.Title)" -ForegroundColor White
    $from = New-Number
    $i = 0
    foreach ($m in $s.Messages) {
        $i++
        if ($s.From -eq "fixed") { $num = $m[0]; $text = $m[1] } else { $num = $from; $text = $m }
        if (-not $Auto) {
            Read-Host "  ▶ $i/$($s.Messages.Count)번째 문자 보내기 [Enter]" | Out-Null
        }
        Send-Sms $num $text
        if ($Auto) { Start-Sleep 4 }
    }
    Write-Host "  ✔ $key 번 문자를 모두 보냈어요. 이제 휴대폰에서 직접 시연하세요." -ForegroundColor Green
}

# ── 준비
Write-Host ""
Write-Host "=== 스마트 헬퍼 데모: 가상 휴대폰으로 문자만 보내기 ===" -ForegroundColor Yellow
if (-not (Test-Path $adb)) { Write-Host "adb 를 찾을 수 없어요: $adb" -ForegroundColor Red; exit 1 }
if (-not (& $adb devices | Select-String "emulator-\d+\s+device")) { Write-Host "가상 휴대폰(에뮬레이터)이 켜져 있지 않아요. 먼저 켜 주세요." -ForegroundColor Red; exit 1 }
if (-not (& $adb shell pm list packages $app)) { Write-Host "가상 휴대폰에 스마트 헬퍼가 설치되어 있지 않아요." -ForegroundColor Red; exit 1 }
# 팝업·링크 차단(접근성)과 수신 알림 뒤 경고 순서(알림 읽기)가 동작하도록 켜 둔다 (앱을 다시 설치하면 꺼진다)
& $adb shell settings put secure enabled_accessibility_services "$app/$app.guide.GuideService"
& $adb shell cmd notification allow_listener "$app/$app.guard.MessengerListener"

if ($Auto) {
    foreach ($k in $SCENARIOS.Keys) { Run-Scenario $k }
    exit 0
}

while ($true) {
    Write-Host ""
    Write-Host "  어떤 문자를 보낼까요?" -ForegroundColor Yellow
    foreach ($k in $SCENARIOS.Keys) { Write-Host "   $k. $($SCENARIOS[$k].Title)" }
    Write-Host "   Q. 끝내기"
    $c = (Read-Host "  번호를 입력하세요").Trim()
    if ($c -match '^[Qq]$') { Write-Host "데모를 마쳤어요."; exit 0 }
    if ($SCENARIOS.Contains($c)) { Run-Scenario $c } else { Write-Host "  1~5, Q 중에서 골라 주세요." -ForegroundColor DarkYellow }
}
