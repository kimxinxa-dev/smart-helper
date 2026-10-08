# 스마트 헬퍼 시연용 스크립트: 가장 어려운 스미싱(링크 없는 지인 사칭)부터 위험 링크 차단까지
# 실행: 같은 폴더의 "시연_시작.bat" 을 두 번 누르거나, PowerShell 에서 .\smishing_demo.ps1
# -Auto : 단계마다 기다리지 않고 바로 진행 (점검용)
param([switch]$Auto)

$adb = "C:\Android\Sdk\platform-tools\adb.exe"
$app = "com.smarthelper.app"

function Wait-Next($msg) {
    if ($Auto) { Start-Sleep 6; return }
    Write-Host ""
    Read-Host "  ▶ $msg [Enter 를 누르세요]" | Out-Null
}

function Send-Sms($from, $text) {
    & $adb emu sms send $from $text | Out-Null
    Write-Host "  📩 $from → $text" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "=== 스마트 헬퍼 시연: 지인 사칭 스미싱 → 위험 링크 차단 ===" -ForegroundColor Yellow

# 0. 준비: adb 와 가상 휴대폰 확인
if (-not (Test-Path $adb)) { Write-Host "adb 를 찾을 수 없어요: $adb" -ForegroundColor Red; exit 1 }
$devices = & $adb devices | Select-String "emulator-\d+\s+device"
if (-not $devices) { Write-Host "가상 휴대폰(에뮬레이터)이 켜져 있지 않아요. Android Studio 에서 먼저 켜 주세요." -ForegroundColor Red; exit 1 }
$installed = & $adb shell pm list packages $app
if (-not $installed) { Write-Host "가상 휴대폰에 스마트 헬퍼가 설치되어 있지 않아요." -ForegroundColor Red; exit 1 }

# 시연마다 새 번호를 써서 '처음 연락 온 번호'가 되게 한다
$from = "010" + (Get-Random -Minimum 20000000 -Maximum 99999999)
Write-Host "  이번 시연에서 사기꾼 번호: $from"

# 위험 링크 차단·화면 안내(접근성 서비스)가 꺼져 있으면 다시 켠다 (앱을 다시 설치하면 꺼진다)
& $adb shell settings put secure enabled_accessibility_services "$app/$app.guide.GuideService"
# 문자 앱의 수신 알림이 뜬 것을 보고 그 뒤에 경고·팝업을 띄우려면 알림 읽기(카카오톡 지킴이)도 켜져 있어야 한다
& $adb shell cmd notification allow_listener "$app/$app.guard.MessengerListener"
& $adb shell am start -n "$app/.MainActivity" | Out-Null
Start-Sleep 2
& $adb shell input keyevent HOME

Wait-Next "1단계: 처음 보는 번호에서 '링크 없는 지인 사칭' 문자를 보냅니다"
Send-Sms $from "할머니 저 손자 준서예요 새로 개통한 번호라 저장 부탁드려요 통화는 나중에 할게요"
Write-Host "  👉 기대 결과: ⚠️ 조심해야 할 문자예요 (처음 문자를 보낸 번호인데 가족처럼 말해요)" -ForegroundColor Green

Wait-Next "2단계: 같은 번호에서 급한 부탁 문자를 보냅니다"
Send-Sms $from "할머니 혹시 지금 편의점 가실 수 있어요? 제가 급해서 그런데 부탁 하나만 드릴게요"
Write-Host "  👉 기대 결과: ⚠️ 주의 유지 (조금 전 의심스러운 문자를 보낸 상대예요)" -ForegroundColor Green

Wait-Next "3단계: 같은 번호에서 기프트카드 + 평범해 보이는 .com 링크를 보냅니다"
Send-Sms $from "할머니 편의점에서 구글 기프트카드 사서 여기에 번호만 넣어 주시면 돼요 http://gift-check.com/a7"
Write-Host "  👉 기대 결과: 🚨 위험한 문자예요! 링크를 누르지 마세요" -ForegroundColor Green

Write-Host ""
Write-Host "  4단계: 가상 휴대폰 화면을 위에서 아래로 내려 알림창을 열고," -ForegroundColor Yellow
Write-Host "         메시지 알림의 [링크 열기] 를 눌러 보세요. (실수로 링크를 누른 상황)" -ForegroundColor Yellow
Write-Host "  👉 기대 결과: 크롬 화면 전체가 빨간 경고로 가려짐 → [안전하게 나가기] 를 누르면 홈 화면으로" -ForegroundColor Green

if (-not $Auto) {
    Write-Host ""
    $open = Read-Host "  알림에서 누르기 어려우면 Y 를 입력하세요. 링크를 크롬으로 대신 열어 드려요 (Y/Enter)"
    if ($open -match '^[Yy]') {
        & $adb shell am start -a android.intent.action.VIEW -d "http://gift-check.com/a7" -p com.android.chrome | Out-Null
        Write-Host "  크롬으로 열었어요. 주소가 뜨면 몇 초 안에 경고가 나타나요." -ForegroundColor Cyan
    }
}

Write-Host ""
Write-Host "=== 시연 끝. 다시 하려면 이 스크립트를 다시 실행하세요 (매번 새 번호를 써요) ===" -ForegroundColor Yellow
