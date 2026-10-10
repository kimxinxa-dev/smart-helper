# 스마트 헬퍼 데모: 사기 문자가 시키는 "수상한 앱 설치"를 막는 순간까지
#   ① 🚨 "보안 앱을 설치하세요 (.apk 링크)" 문자 도착 → 위험 팝업
#   ② (선택) 링크를 열면 → 빨간 위험 사이트 차단 화면
#   ③ 시키는 대로 앱 파일을 내려받아 설치하려 하면 → ✋ 설치 화면을 가리고 "방금 받은 문자 때문에 설치하시는 건가요?"
# 내려받는 앱은 아무 기능 없는 '연습용 퍼즐'(testapp)이라 설치해도 안전하다. 사기 주소는 가짜라 실제로 내려받을 수 없어서,
# PC 에서 휴대폰의 '다운로드' 폴더에 넣어 내려받은 것처럼 만든다.
# 실행: 같은 폴더의 "앱_설치_차단_시연.bat" 을 두 번 누르세요.
# -Auto : 기다리지 않고 차례로 진행하며 판정·차단만 점검 (팝업은 화면을 눌러 닫는다)
param([switch]$Auto)

$adb = "C:\Android\Sdk\platform-tools\adb.exe"
$app = "com.smarthelper.app"
$puzzle = "com.smarthelper.practicepuzzle"
$apk = Join-Path $PSScriptRoot "..\android\testapp\build\outputs\apk\debug\testapp-debug.apk"
$fileName = "SecurityUpdate.apk"   # 사기꾼이 보낸 '보안 업데이트' 파일인 척
$scamUrl = "http://safe-guard.site/v3.apk"
$script:fail = 0
$stayed = $false   # ② 에서 위험 사이트를 그래도 보기로 했는지

function New-Number { "010" + (Get-Random -Minimum 20000000 -Maximum 99999999) }

function Pause-Step($msg) {
    if ($Auto) { Start-Sleep 2; return }
    Write-Host ""
    Read-Host "  ▶ $msg — 이 PC 창에서 [Enter] 를 누르세요" | Out-Null
}

# 지금 휴대폰 맨 앞 화면이 이 앱인지 (예: 크롬)
function Test-Top($pkg) { [bool]((& $adb shell dumpsys activity activities | Select-String 'topResumedActivity' | Select-Object -First 1) -match [regex]::Escape($pkg)) }

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

# '그래도 진행'을 누른 뒤 5분 동안은 설치 차단이 다시 묻지 않는다 → 그 시각 (없으면 0)
function Get-SnoozeUntil {
    $raw = & $adb shell run-as $app cat shared_prefs/guard.xml 2>$null
    if (-not $raw) { return 0 }
    try {
        $x = [xml]($raw -join "`n")
        $v = ($x.map.long | Where-Object { $_.name -eq 'installSnoozedUntil' }).value
        if ($v) { return [int64]$v } else { return 0 }
    } catch { return 0 }
}

# 스마트 헬퍼가 화면 위에 띄운 창(위험 팝업·차단 화면 등)이 있는지
function Test-Overlay {
    [bool]((& $adb shell dumpsys window windows) -match "package=$app appop=CREATE_ACCESSIBILITY_OVERLAY")
}

function Wait-Overlay($appear, $maxSec) {
    for ($i = 0; $i -lt ($maxSec * 2); $i++) {
        if ((Test-Overlay) -eq $appear) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Installed($pkg) { [bool](& $adb shell pm list packages $pkg | Select-String "package:$pkg$") }

# ── 준비
Write-Host ""
Write-Host "=== 스마트 헬퍼 데모: 사기 문자가 시키는 수상한 앱 설치 막기 ===" -ForegroundColor Yellow
if (-not (Test-Path $adb)) { Write-Host "adb 를 찾을 수 없어요: $adb" -ForegroundColor Red; exit 1 }
if (-not (& $adb devices | Select-String "emulator-\d+\s+device")) { Write-Host "가상 휴대폰(에뮬레이터)이 켜져 있지 않아요. 먼저 켜 주세요." -ForegroundColor Red; exit 1 }
if (-not (Installed $app)) { Write-Host "가상 휴대폰에 스마트 헬퍼가 설치되어 있지 않아요." -ForegroundColor Red; exit 1 }
if (-not (Test-Path $apk)) {
    Write-Host "연습용 퍼즐 앱 파일이 없어요: $apk" -ForegroundColor Red
    Write-Host "android 폴더에서 먼저 만들어 주세요:  .\gradlew.bat :testapp:assembleDebug" -ForegroundColor Yellow
    exit 1
}
# 팝업·링크 차단·설치 차단은 접근성 서비스가, 문자 앱 알림 뒤 경고 순서는 알림 읽기가 켜져 있어야 한다 (앱을 다시 설치하면 꺼진다)
& $adb shell settings put secure enabled_accessibility_services "$app/$app.guide.GuideService"
& $adb shell cmd notification allow_listener "$app/$app.guard.MessengerListener"
& $adb shell am start -n "$app/.MainActivity" 2>$null | Out-Null
Start-Sleep 3
& $adb shell input keyevent HOME

# 연습용 퍼즐이 이미 깔려 있으면 설치 화면이 "업데이트하시겠습니까?"로 바뀌므로 잠시 지운다 (끝나면 다시 설치)
$hadPuzzle = Installed $puzzle
if ($hadPuzzle) {
    & $adb uninstall $puzzle | Out-Null
    Write-Host "  (연습용 퍼즐 앱을 잠시 지웠어요. 시연이 끝나면 다시 설치해 둘게요)" -ForegroundColor DarkGray
}
& $adb shell rm -f "/sdcard/Download/$fileName" 2>$null

$wait = [int](((Get-SnoozeUntil) - [DateTimeOffset]::Now.ToUnixTimeMilliseconds()) / 1000)
if ($wait -gt 0) {
    Write-Host "  ⚠️ 조금 전에 '그래도 진행'을 눌러서 $wait 초 동안은 설치 차단이 다시 묻지 않아요. 그 뒤에 시연해 주세요." -ForegroundColor DarkYellow
}

# ── ① 사기 문자
Write-Host ""
Write-Host "■ 1. 사기 문자 도착: '휴대폰에 악성 앱이 있으니 보안 앱을 설치하라'" -ForegroundColor White
Pause-Step "사기 문자 보내기"
$from = New-Number
$before = Get-Latest
$beforeId = if ($before) { $before.id } else { 0 }
& $adb emu sms send $from "[Web발신] 고객님 휴대폰에서 악성 앱이 발견되었습니다. 보안 앱을 바로 설치하세요 $scamUrl" | Out-Null
Write-Host "  📩 $from 님이 보낸 문자" -ForegroundColor Cyan
Write-Host "     [Web발신] 고객님 휴대폰에서 악성 앱이 발견되었습니다. 보안 앱을 바로 설치하세요 $scamUrl"
$v = $null
# 휴대폰을 막 켠 직후에는 첫 판정이 늦을 수 있어 최대 28초까지 기다린다 (보통은 몇 초 안에 끝남)
for ($i = 0; $i -lt 40; $i++) { Start-Sleep -Milliseconds 700; $v = Get-Latest; if ($v -and $v.id -ne $beforeId) { break }; $v = $null }
if ($v) {
    $ok = $v.level -eq "HIGH"; if (-not $ok) { $script:fail++ }
    Write-Host ("  → 스마트 헬퍼 판정: {0}  {1}" -f $(if ($ok) { "🚨 위험" } else { $v.level }), $(if ($ok) { "✔ 기대대로" } else { "✘ 기대: 🚨 위험" })) -ForegroundColor $(if ($ok) { "Red" } else { "Yellow" })
    foreach ($r in $v.reasons) { Write-Host "       · $r" }
} else { Write-Host "  (판정 결과를 읽지 못했어요. 휴대폰 화면을 확인하세요)" -ForegroundColor DarkYellow; $script:fail++ }
Write-Host "  💡 이 순간부터 30분 동안이 '위험 시간대'예요. 이 동안 앱을 설치하려 하면 한 번 더 물어봐요." -ForegroundColor DarkCyan

Write-Host "  ⏳ 휴대폰에 문자 수신 알림이 오고, 곧 🚨 팝업이 떠요..." -ForegroundColor Magenta
if (Wait-Overlay $true 25) {
    if ($Auto) { & $adb shell input tap 540 2010 }
    else { Write-Host "  👉 휴대폰에서 팝업을 보여 준 뒤 [알겠어요] 를 눌러 주세요." -ForegroundColor Magenta }
    if (Wait-Overlay $false 120) { Write-Host "  ✔ 팝업을 닫았어요." -ForegroundColor DarkGray }
} else { Write-Host "  (팝업을 찾지 못했어요. 스마트 헬퍼의 '위험 링크 차단'이 켜져 있는지 확인하세요)" -ForegroundColor DarkYellow }

# ── ② (선택) 링크 열기
if (-not $Auto) {
    Write-Host ""
    Write-Host "■ 2. (선택) 문자 속 링크를 눌러 내려받으려 한 상황" -ForegroundColor White
    $a = Read-Host "  ▶ 크롬에서 링크를 열어 위험 사이트 차단을 보여 주려면 Y, 건너뛰려면 Enter"
    if ($a -match '^[Yy]') {
        & $adb shell am start -a android.intent.action.VIEW -d $scamUrl -p com.android.chrome | Out-Null
        Write-Host "  크롬으로 열었어요. 화면 전체가 빨간 경고로 가려져요 (에뮬레이터는 10초쯤 걸릴 수 있어요)." -ForegroundColor Cyan
        if (Wait-Overlay $true 25) {
            Write-Host "  👉 휴대폰에서 고르세요:" -ForegroundColor Magenta
            Write-Host "     · [안전하게 나가기] → 잘 나온 것을 보여 주고, 다음 단계로" -ForegroundColor Magenta
            Write-Host "     · [그래도 볼래요] → '정말 들어가시겠어요?' → [네, 그래도 볼게요] → 사기 사이트가 앱을 내려받게 한 것처럼 바로 설치 화면으로" -ForegroundColor Magenta
            Wait-Overlay $false 180 | Out-Null
            Start-Sleep 2
            # 그래도 보기로 했으면 크롬이 앞에 남아 있다 (나가기를 고르면 뒤로 가거나 홈으로 간다)
            $stayed = Test-Top "com.android.chrome"
            if ($stayed) { Write-Host "  → 사이트를 그대로 열었어요. 사기 사이트는 이때 '보안 앱'을 내려받게 해요." -ForegroundColor Yellow }
            else { Write-Host "  ✔ 안전하게 나왔어요. 잘 하셨어요." -ForegroundColor Green }
        }
    }
}

# ── ③ 내려받은 앱 설치
Write-Host ""
Write-Host "■ 3. 그래도 사기꾼 말대로 앱 파일을 내려받아 설치하려 한 상황" -ForegroundColor White
Write-Host "   (사기 주소는 가짜라 실제로 내려받을 수 없어서, 아무 기능 없는 '연습용 퍼즐' 앱을 '$fileName' 이라는 이름으로 다운로드 폴더에 넣어요)" -ForegroundColor DarkGray
if ($stayed) {
    # 사이트를 그대로 본 경우: 사이트가 앱 파일을 내려받게 한 것처럼 Enter 없이 바로 이어 간다
    Write-Host "  ⏬ 사기 사이트가 '$fileName' 을 내려받게 했어요. 받은 파일을 열면 설치 화면이 나와요..." -ForegroundColor Cyan
    Start-Sleep 2
} else {
    Pause-Step "앱 파일을 내려받고 설치 화면 열기"
    & $adb shell input keyevent HOME
}
# 지난 시연에서 지운 파일의 기록이 미디어 목록에 남아 있을 수 있다. 그 기록을 열면 파일이 없어 엉뚱한 앱(크롬)이 열리므로,
# 넣기 전의 가장 큰 번호를 기억해 두고 그보다 새로 생긴 기록만 쓴다.
function Get-FileIds { & $adb shell content query --uri content://media/external/file --projection _id:_data | Where-Object { $_ -match [regex]::Escape($fileName) } | ForEach-Object { [int64][regex]::Match($_, '_id=(\d+)').Groups[1].Value } }
foreach ($old in @(Get-FileIds)) { & $adb shell content delete --uri "content://media/external/file/$old" 2>&1 | Out-Null }
$oldMax = (@(Get-FileIds) + 0 | Measure-Object -Maximum).Maximum
& $adb push $apk "/sdcard/Download/$fileName" 2>&1 | Out-Null
& $adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/Download/$fileName" | Out-Null
$id = $null
for ($i = 0; $i -lt 15 -and -not $id; $i++) {
    Start-Sleep -Milliseconds 700
    $id = @(Get-FileIds) | Where-Object { $_ -gt $oldMax } | Sort-Object | Select-Object -Last 1
}
if (-not $id) { Write-Host "  (내려받은 파일을 찾지 못했어요)" -ForegroundColor Red; $script:fail++ }
else {
    Write-Host "  📥 다운로드 폴더에 '$fileName' 이 생겼어요. 이 파일을 열어 설치 화면으로 가요." -ForegroundColor Cyan
    & $adb logcat -c
    & $adb shell am start -a android.intent.action.VIEW -d "content://media/external/file/$id" -t application/vnd.android.package-archive --grant-read-uri-permission | Out-Null
    $blocked = $false
    for ($i = 0; $i -lt 30 -and -not $blocked; $i++) {
        Start-Sleep -Milliseconds 700
        $blocked = [bool]((& $adb logcat -d -s SmartHelper:*) -match "설치 차단 경고")
    }
    if ($blocked) {
        Write-Host "  → ✋ 설치 화면을 가렸어요  ✔ 기대대로" -ForegroundColor Red
        Write-Host "       '잠깐만요! 방금 받은 문자 때문에 설치하시는 건가요?' + 받은 시각·보낸 사람 ($from)"
        Write-Host "  👉 버튼: [🛡️ 안전하게 그만두기] 설치 화면을 닫고 홈으로 / [📞 가족에게 전화하기] 번호만 띄움(가족이 없으면 118) / [그래도 진행] 5분 동안 다시 묻지 않음" -ForegroundColor Magenta
        Write-Host "  💡 평소에는 아무것도 하지 않아요. 의심 문자를 받은 뒤 30분 동안만 설치 화면을 봐요." -ForegroundColor DarkCyan
        if ($Auto) { Start-Sleep 2; & $adb shell input tap 540 1680 }
        if (Wait-Overlay $false 180) {
            Start-Sleep 2
            if (Test-Top "packageinstaller") {
                # '그래도 진행'을 고르면 원래 설치 창이 다시 보인다. [설치]를 누르면 안드로이드 기본 검사(구글 Play 프로텍트)가
                # 낯선 앱 파일을 한 번 더 막을 수 있다 → 두 겹 방어로 보여 준다
                Write-Host "  → '그래도 진행'을 골랐어요. 설치 창에서 [설치] 를 눌러 보세요." -ForegroundColor Yellow
                $outcome = ""
                for ($i = 0; $i -lt 90 -and -not $outcome; $i++) {
                    Start-Sleep 1
                    if (Installed $puzzle) { $outcome = "installed" }
                    elseif ((& $adb logcat -d) -match "INSTALL_FAILED_VERIFICATION_FAILURE") { $outcome = "protect" }
                    elseif (-not (Test-Top "packageinstaller")) { $outcome = "closed" }
                }
                switch ($outcome) {
                    "protect" {
                        Write-Host "  🛡️ 구글 Play 프로텍트가 한 번 더 막았어요 (설치 실패)." -ForegroundColor Green
                        Write-Host "  💡 두 겹 방어: 스마트 헬퍼가 먼저 멈춰 세우고, 그래도 진행하면 안드로이드 기본 검사가 낯선 앱을 한 번 더 막아요." -ForegroundColor DarkCyan
                    }
                    "installed" { Write-Host "  ('그래도 진행' 뒤 설치까지 했어요. 연습용 퍼즐이라 안전해요)" -ForegroundColor DarkGray }
                    default { Write-Host "  ✔ 설치하지 않았어요." -ForegroundColor DarkGray }
                }
            }
            elseif (Installed $puzzle) { Write-Host "  ('그래도 진행' 뒤 설치까지 했어요. 연습용 퍼즐이라 안전해요)" -ForegroundColor DarkGray }
            else { Write-Host "  ✔ 설치하지 않았어요." -ForegroundColor DarkGray }
        }
    } else {
        Write-Host "  → 설치 차단 경고가 뜨지 않았어요  ✘" -ForegroundColor Yellow
        Write-Host "     · 스마트 헬퍼의 '위험 링크·설치 차단'이 켜져 있는지, 5분 안에 '그래도 진행'을 누르지 않았는지 확인하세요." -ForegroundColor DarkYellow
        $script:fail++
    }
}

# ── 정리: 내려받은 파일 지우기, 원래 깔려 있던 연습용 퍼즐은 다시 설치 (앱 지우기 안내 연습용)
& $adb shell rm -f "/sdcard/Download/$fileName" 2>$null
if ($hadPuzzle -and -not (Installed $puzzle)) {
    & $adb install -r $apk 2>&1 | Out-Null
    Write-Host "  (연습용 퍼즐 앱을 다시 설치해 뒀어요)" -ForegroundColor DarkGray
}

Write-Host ""
if ($script:fail -eq 0) { Write-Host "시연 끝: 모두 기대대로예요." -ForegroundColor Green } else { Write-Host "시연 끝: 기대와 다른 단계 $($script:fail)개" -ForegroundColor Red }
if ($Auto) { exit $script:fail }
