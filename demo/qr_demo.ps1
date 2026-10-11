# 스마트 헬퍼 데모: QR 코드 검사(큐싱 방어)
#   1 ✅ 진짜 택배 조회 QR → 2 ⚠️ 가게 메뉴판 단축 주소 QR → 3 📶 카페 와이파이 QR → 4 🚨 주차장 정산 QR 위 가짜 스티커(.apk)
#   4번에서 '그래도 열기'를 누르면 크롬이 열리는 순간 위험 사이트 차단 화면이 한 번 더 막는다 (두 겹 방어)
# 에뮬레이터 카메라에는 진짜 QR 을 비출 수 없어서, 디버그 앱에 "QR 을 찍은 것처럼" 내용을 넣는다 (찍은 뒤는 실제와 같다).
# 실제 휴대폰에서는 QR 을 종이에 뽑아 [📷 QR 코드 찍기] 로 직접 찍으면 된다.
# 실행: 같은 폴더의 "QR_코드_시연.bat" 을 두 번 누르세요.
# -Auto : 기다리지 않고 네 가지 판정만 점검
param([switch]$Auto)

$adb = "C:\Android\Sdk\platform-tools\adb.exe"
# adb 가 보내는 글자(UTF-8)를 그대로 읽는다. .bat 으로 연 창은 한글을 CP949 로 읽어서, 기록 속 한글이 깨져 읽기에 실패했다
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$app = "com.smarthelper.app"
$script:fail = 0

$LEVEL = @{ HIGH = "🚨 위험"; MID = "⚠️ 확인 필요"; LOW = "✅ 안전해 보임" }
$COLOR = @{ HIGH = "Red"; MID = "Yellow"; LOW = "Green" }

# 찍을 QR 들: 분류, 담긴 내용, 기대 판정(LOW/MID/HIGH), 종류(URL/WIFI), 발표용 설명
$QRS = @(
    @{ Cat = "✅ 진짜 택배 조회 QR"; Text = "https://www.cjlogistics.com/ko/tool/parcel/tracking"; Expect = "LOW"; Kind = "URL"
       Note = "공식 택배사 주소라 위험 신호가 없어요. [🌐 열기] 가 크게 나와요." }
    @{ Cat = "⚠️ 가게 메뉴판 QR (단축 주소)"; Text = "https://bit.ly/3kTz9"; Expect = "MID"; Kind = "URL"
       Note = "단축 주소는 실제로 어디로 가는지 숨겨요. 정상 가게도 많이 써서 '위험'이 아니라 '확인 필요'로 알려요." }
    @{ Cat = "📶 카페 와이파이 QR"; Text = "WIFI:T:WPA;S:Cafe_Guest;P:secret1234;;"; Expect = "LOW"; Kind = "WIFI"
       Note = "인터넷 주소가 아니라 열지 않아요. 와이파이 이름만 보여 주고 비밀번호는 화면에 보여 주지 않아요." }
    @{ Cat = "🚨 주차장 정산 QR 위에 덧붙인 가짜 스티커"; Text = "http://parking-pay.xyz/pay.apk"; Expect = "HIGH"; Kind = "URL"
       Note = "앱 설치 파일(.apk) + 수상한 주소 끝자리(.xyz). '열지 않기'를 크게, '그래도 열기'는 작게 + 확인 창." }
)

function Pause-Step($msg) {
    if ($Auto) { Start-Sleep 1; return }
    Write-Host ""
    Read-Host "  ▶ $msg — 이 PC 창에서 [Enter] 를 누르세요" | Out-Null
}

# 앱이 남긴 마지막 QR 판정 (로그 한 줄: "QR 검사: HIGH URL parking-pay.xyz")
function Wait-QrLog($sec) {
    for ($i = 0; $i -lt $sec * 2; $i++) {
        Start-Sleep -Milliseconds 500
        $line = (& $adb logcat -d -s SmartHelper:I) | Select-String 'QR 검사: (\w+) (\w+)' | Select-Object -Last 1
        if ($line) { return $line.Matches[0] }
    }
    return $null
}

function Installed($pkg) { [bool](& $adb shell pm list packages $pkg | Select-String "package:$pkg$") }

# QR 하나를 "찍는다": 앱을 앞으로 가져오고, QR 내용을 넣고, 판정을 확인한다
function Scan-One($no, $q) {
    Write-Host ""
    Write-Host ("■ {0}. {1}" -f $no, $q.Cat) -ForegroundColor White
    Write-Host "     QR 내용: $($q.Text)" -ForegroundColor DarkGray
    Pause-Step "QR 코드 찍기"
    & $adb shell am start -n "$app/.MainActivity" 2>$null | Out-Null
    Start-Sleep -Milliseconds 800
    & $adb logcat -c
    Write-Host "  📷 찍었어요." -ForegroundColor Cyan
    # 앱이 막 켜지는 중이면 받지 못할 수 있어 판정이 안 나오면 다시 찍는다 (최대 3번)
    $m = $null
    for ($try = 0; $try -lt 3 -and -not $m; $try++) {
        # 와이파이 QR 처럼 ; 가 든 내용은 휴대폰 셸이 나누지 않도록 따옴표로 감싸 한 줄로 보낸다
        & $adb shell "am broadcast -a com.smarthelper.app.FAKE_QR --es text '$($q.Text)'" | Out-Null
        $m = Wait-QrLog 5
    }
    if (-not $m) { Write-Host "  (판정을 읽지 못했어요. 휴대폰 화면을 확인하세요)" -ForegroundColor DarkYellow; $script:fail++; return }
    $lv = $m.Groups[1].Value; $kind = $m.Groups[2].Value
    $ok = ($lv -eq $q.Expect -and $kind -eq $q.Kind)
    if (-not $ok) { $script:fail++ }
    $mark = if ($ok) { "✔ 기대대로" } else { "✘ 기대와 다름 (기대: $($LEVEL[$q.Expect]) $($q.Kind))" }
    $what = if ($kind -eq "WIFI") { "📶 와이파이 QR" } elseif ($kind -eq "TEXT") { "📄 글자 QR" } else { $LEVEL[$lv] }
    Write-Host "  → 스마트 헬퍼 판정: $what  $mark" -ForegroundColor $(if ($kind -eq "URL") { $COLOR[$lv] } else { "Green" })
    Write-Host "  💡 $($q.Note)" -ForegroundColor DarkCyan
}

# ── 준비
Write-Host ""
Write-Host "=== 스마트 헬퍼 데모: QR 코드 검사 (큐싱 방어) ===" -ForegroundColor Yellow
if (-not (Test-Path $adb)) { Write-Host "adb 를 찾을 수 없어요: $adb" -ForegroundColor Red; exit 1 }
if (-not (& $adb devices | Select-String "emulator-\d+\s+device")) { Write-Host "가상 휴대폰(에뮬레이터)이 켜져 있지 않아요. 먼저 켜 주세요." -ForegroundColor Red; exit 1 }
if (-not (Installed $app)) { Write-Host "가상 휴대폰에 스마트 헬퍼가 설치되어 있지 않아요." -ForegroundColor Red; exit 1 }
# 위험 사이트 차단(두 겹 방어)은 접근성 서비스가 켜져 있어야 한다. 앱을 다시 설치하거나 강제 종료하면 꺼진다
& $adb shell settings put secure enabled_accessibility_services "$app/$app.guide.GuideService"
& $adb shell settings put secure accessibility_enabled 1
& $adb shell am start -W -n "$app/.MainActivity" 2>$null | Out-Null
Start-Sleep 3   # 화면(웹)까지 다 그려질 때까지
Write-Host "  휴대폰에 스마트 헬퍼를 열었어요. 찍은 QR 의 결과는 '문자 지킴이' 화면에 저절로 나와요." -ForegroundColor DarkGray
Write-Host "  (에뮬레이터 카메라에는 진짜 QR 을 비출 수 없어서, QR 을 찍은 것처럼 내용을 넣어요. 찍은 뒤는 실제와 같아요)" -ForegroundColor DarkGray

$n = 0
foreach ($q in $QRS) { $n++; Scan-One $n $q }

# ── 두 겹 방어 (선택)
if (-not $Auto) {
    Write-Host ""
    Write-Host "■ 5. (선택) 두 겹 방어: 경고를 무시하고 '그래도 열기'를 누르면?" -ForegroundColor White
    Write-Host "  👉 휴대폰에서 아래로 내려 [그래도 열기] → '한 번 더 확인할게요' 창에서 [그래도 열기] 를 누르세요." -ForegroundColor Magenta
    Write-Host "     크롬이 열리는 순간 화면 전체가 빨간 '위험한 사이트예요'로 가려져요 → [안전하게 나가기]" -ForegroundColor Magenta
    & $adb logcat -c
    Write-Host "  (기다리는 중... 건너뛰려면 이 창에서 Enter)" -ForegroundColor DarkGray
    $blocked = $false
    for ($i = 0; $i -lt 240 -and -not $blocked; $i++) {
        Start-Sleep -Milliseconds 500
        $blocked = [bool]((& $adb logcat -d -s SmartHelper:I) -match '위험 링크 차단')
        try { if ([Console]::KeyAvailable -and [Console]::ReadKey($true).Key -eq 'Enter') { break } } catch {}
    }
    if ($blocked) { Write-Host "  → 🛡️ 크롬에서 위험 사이트를 한 번 더 막았어요  ✔" -ForegroundColor Green }
}

Write-Host ""
if ($script:fail -eq 0) { Write-Host "시연 끝: 모두 기대대로예요." -ForegroundColor Green } else { Write-Host "시연 끝: 기대와 다른 단계 $($script:fail)개" -ForegroundColor Red }
if ($Auto) { exit $script:fail }
