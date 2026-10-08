# 스마트 헬퍼 데모: 가상 휴대폰으로 문자만 보내 준다 (메뉴에서 고를 때마다 한 통씩)
# 팝업 확인, 링크 누르기, 차단 화면은 발표자가 휴대폰에서 직접 시연한다.
# 실행: 같은 폴더의 "문자만_보내기.bat" 을 두 번 누르세요.
# -Auto : 메뉴 없이 모든 문자를 차례로 한 통씩 보냄 (점검용)
param([switch]$Auto)

$adb = "C:\Android\Sdk\platform-tools\adb.exe"
$app = "com.smarthelper.app"

function New-Number { "010" + (Get-Random -Minimum 20000000 -Maximum 99999999) }

# 1번 지인 사칭은 대화 흐름(처음 온 번호 → 같은 번호의 부탁)을 보여 주려고 1-1~1-3 을 같은 번호로 보낸다
$script:familyNumber = New-Number

# 메뉴 번호 → 보낸 사람, 내용, 메뉴 설명. 보낸 사람이 "family" 면 지인 사칭 번호, "new" 면 보낼 때마다 새 번호
$MESSAGES = [ordered]@{
    "1-1" = @{ From = "family"; Label = "지인 사칭 ① 처음 연락 (링크·사기 단어 없음)";
               Text = "할머니 저 손자 준서예요 새로 개통한 번호라 저장 부탁드려요 통화는 나중에 할게요" }
    "1-2" = @{ From = "family"; Label = "지인 사칭 ② 급한 부탁";
               Text = "할머니 혹시 지금 편의점 가실 수 있어요? 제가 급해서 그런데 부탁 하나만 드릴게요" }
    "1-3" = @{ From = "family"; Label = "지인 사칭 ③ 기프트카드 + 평범한 .com 링크";
               Text = "할머니 편의점에서 구글 기프트카드 사서 여기에 번호만 넣어 주시면 돼요 http://gift-check.com/a7" }
    "2"   = @{ From = "new"; Label = "진짜 쇼핑몰 같은 배송 문자";
               Text = "[Web발신] 고객님, 주문하신 상품의 배송지 정보가 누락되어 출고가 보류되었습니다. 배송지 입력 https://coupang-delivery.com/r8" }
    "3"   = @{ From = "new"; Label = "모바일 청첩장";
               Text = "모바일 청첩장 보내드려요. 꼭 와 주세요 https://our-wedding.com/kim" }
    "4"   = @{ From = "new"; Label = "링크 없는 검찰 수사관 사칭";
               Text = "서울중앙지검 김민수 수사관입니다. 고객님 명의 계좌가 사건에 연루되어 확인이 필요합니다. 이 번호로 회신 바랍니다" }
    "5-1" = @{ From = "15881234";   Label = "정상: 병원 예약";      Text = "[한마음병원] 내일 오전 10시 내과 진료 예약이 확인되었습니다. 변경은 병원으로 연락 바랍니다." }
    "5-2" = @{ From = "15881688";   Label = "정상: 카드 승인";      Text = "[KB국민카드] 김순자님 32,500원 승인 이마트 일시불" }
    "5-3" = @{ From = "15882100";   Label = "정상: 은행 입금";      Text = "[농협] 입금 300,000원 잔액 1,240,500원 김순자" }
    "5-4" = @{ From = "15993333";   Label = "정상: 인증번호";       Text = "[카카오] 인증번호 [482910]를 입력해 주세요. 타인에게 절대 알려주지 마세요." }
    "5-5" = @{ From = "0233322114"; Label = "정상: 구청 안내";      Text = "[마포구청] 내일 한파주의보 발효. 외출을 자제하고 따뜻한 옷차림 하세요." }
    "5-6" = @{ From = "0233225577"; Label = "정상: 아파트 안내";    Text = "[햇살아파트] 관리사무소입니다. 내일 오전 9시부터 12시까지 단수 예정이니 물을 받아 두세요." }
    "5-7" = @{ From = "15880011";   Label = "정상: 진짜 CJ대한통운 (공식 주소 링크)"; Text = "[CJ대한통운] 고객님의 상품이 오늘 배송 완료되었습니다. 배송 조회 https://www.cjlogistics.com" }
}

function Send-One($key) {
    $m = $MESSAGES[$key]
    $from = switch ($m.From) { "family" { $script:familyNumber } "new" { New-Number } default { $m.From } }
    & $adb emu sms send $from $m.Text | Out-Null
    Write-Host ""
    Write-Host "  📩 [$key] 한 통 보냈어요 ($from)" -ForegroundColor Cyan
    Write-Host "     $($m.Text)"
    if ($key -eq "1-3") {
        # 다음에 1번을 처음부터 다시 하면 '처음 온 번호'가 되도록 번호를 바꿔 둔다
        $script:familyNumber = New-Number
        Write-Host "  (다음 지인 사칭은 새 번호 $($script:familyNumber) 로 보내요)" -ForegroundColor DarkGray
    }
}

# ── 준비
Write-Host ""
Write-Host "=== 스마트 헬퍼 데모: 가상 휴대폰으로 문자 한 통씩 보내기 ===" -ForegroundColor Yellow
if (-not (Test-Path $adb)) { Write-Host "adb 를 찾을 수 없어요: $adb" -ForegroundColor Red; exit 1 }
if (-not (& $adb devices | Select-String "emulator-\d+\s+device")) { Write-Host "가상 휴대폰(에뮬레이터)이 켜져 있지 않아요. 먼저 켜 주세요." -ForegroundColor Red; exit 1 }
if (-not (& $adb shell pm list packages $app)) { Write-Host "가상 휴대폰에 스마트 헬퍼가 설치되어 있지 않아요." -ForegroundColor Red; exit 1 }
# 팝업·링크 차단(접근성)과 수신 알림 뒤 경고 순서(알림 읽기)가 동작하도록 켜 둔다 (앱을 다시 설치하면 꺼진다)
& $adb shell settings put secure enabled_accessibility_services "$app/$app.guide.GuideService"
& $adb shell cmd notification allow_listener "$app/$app.guard.MessengerListener"

if ($Auto) {
    foreach ($k in $MESSAGES.Keys) { Send-One $k; Start-Sleep 4 }
    exit 0
}

while ($true) {
    Write-Host ""
    Write-Host "  어떤 문자를 한 통 보낼까요?" -ForegroundColor Yellow
    Write-Host "   [1. 지인 사칭 대화 (가장 어려움) — 1-1 → 1-2 → 1-3 순서로 보내세요. 같은 번호 $($script:familyNumber)]" -ForegroundColor White
    foreach ($k in $MESSAGES.Keys) {
        if ($k -eq "2") { Write-Host "   [사기 문자 한 통짜리]" -ForegroundColor White }
        if ($k -eq "5-1") { Write-Host "   [5. 정상 문자 — 경고 없이 그대로 와요]" -ForegroundColor White }
        Write-Host ("   {0,-4} {1}" -f $k, $MESSAGES[$k].Label)
    }
    Write-Host "   N    지인 사칭 번호 새로 바꾸기 (1번을 처음부터 다시 할 때)"
    Write-Host "   Q    끝내기"
    $r = Read-Host "  번호를 입력하세요 (예: 1-1, 2, 5-3)"
    if ($null -eq $r) { exit 0 } # 입력을 받을 수 없으면(창이 닫힘 등) 끝낸다
    $c = $r.Trim()
    if ($c -match '^[Qq]$') { Write-Host "데모를 마쳤어요."; exit 0 }
    if ($c -match '^[Nn]$') { $script:familyNumber = New-Number; Write-Host "  지인 사칭 번호를 $($script:familyNumber) 로 바꿨어요." -ForegroundColor Green; continue }
    if ($MESSAGES.Contains($c)) { Send-One $c } else { Write-Host "  메뉴에 있는 번호를 입력해 주세요. (예: 1-1, 2, 5-3)" -ForegroundColor DarkYellow }
}
