# 스마트 헬퍼 데모: 위험도별로 문자가 한 통씩 차례로 도착하는 시연 (✅ 진짜 택배 → ⚠️ 해외 결제 → 🚨 가짜 택배)
# 실행: 같은 폴더의 "위험도별_문자_시연.bat" 을 두 번 누르세요.
#  - 메뉴 없이 저절로 다음 문자로 넘어가요. Enter: 바로 다음 / P: 잠깐 멈춤 / Q: 그만
#  - 🚨 위험 문자는 휴대폰에 팝업이 뜨면, 발표자가 [알겠어요] 를 누를 때까지 기다렸다가 다음으로 넘어가요.
#  - ⚠️ 주의 문자는 휴대폰 아래쪽에 노란 카드가 떴다가 저절로 사라지면 넘어가요.
# -Auto : 기다리지 않고 모든 문자의 판정만 점검 (팝업은 기다리지 않음)
param([switch]$Auto)

$adb = "C:\Android\Sdk\platform-tools\adb.exe"
# adb 가 보내는 글자(UTF-8)를 그대로 읽는다. .bat 으로 연 창은 한글을 CP949 로 읽어서, 판정 기록 속 한글이 깨져 읽기에 실패했다
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$app = "com.smarthelper.app"

$LEVEL = @{ HIGH = "🚨 위험"; MID = "⚠️ 주의"; LOW = "✅ 낮음"; SKIP = "연락처 번호(검사 안 함)" }
$COLOR = @{ HIGH = "Red"; MID = "Yellow"; LOW = "Green"; SKIP = "Gray" }

function New-Number { "010" + (Get-Random -Minimum 20000000 -Maximum 99999999) }

$script:results = New-Object System.Collections.ArrayList
$script:quit = $false

# 장(위험도)마다 한 통: 문자 분류, 보낸 사람, 내용, 기대 판정.
# 진짜 택배 문자(정상)와 가짜 택배 문자(위험)를 나란히 보여 주어 차이가 드러나게 한다.
# 보낸 사람이 "new"·"intl" 이면 보낼 때마다 처음 보는 휴대폰·해외 번호
# (같은 번호로 다시 보내면 "조금 전 의심 문자를 보낸 상대"로 기억돼 판정이 달라지므로 매번 새로 만든다)
$CHAPTERS = @(
    @{ Title = "✅ 1장. 정상 문자 — 평소 받는 문자는 경고 없이 그대로 와요"; Color = "Green"; Items = @(
        @{ Cat = "택배 (진짜 공식 주소)"; From = "15880011"; Expect = "LOW"; Text = "[CJ대한통운] 고객님의 상품이 오늘 배송 완료되었습니다. 배송 조회 https://www.cjlogistics.com"
           Note = "링크가 있어도 진짜 택배사 공식 주소라서 경고하지 않아요." }
    ) }
    @{ Title = "⚠️ 2장. 주의 문자 — 사기라고 단정할 수 없지만 조심해야 해요"; Color = "Yellow"; Items = @(
        @{ Cat = "해외 결제 승인 (국제발신)"; From = "intl"; Expect = "MID"; Text = "[국제발신] 고객님 해외 직구 결제 498,000원 승인. 본인 아니시면 고객센터로 문의 바랍니다"
           Note = "놀라게 해서 전화하게 만드는 수법일 수 있어요. 문자 말고 카드 뒷면 번호로 직접 확인하세요." }
    ) }
    @{ Title = "🚨 3장. 위험 문자 — 받는 순간 큰 팝업과 음성으로 알려요"; Color = "Red"; Items = @(
        @{ Cat = "택배 사칭"; From = "new"; Expect = "HIGH"; Text = "[Web발신] [한빛택배] 고객님의 택배가 주소 불명으로 반송 예정입니다. 주소 확인 http://han-bit.xyz/a8Kd2"
           Note = "1장의 진짜 택배 문자와 비교해 보세요. 모르는 택배사 이름과 수상한 주소(.xyz)예요." }
    ) }
)

# ── 휴대폰 상태 읽기
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

# 스마트 헬퍼가 휴대폰 화면 위에 띄운 창(위험 팝업·링크 차단 등)이 있는지
function Test-Overlay {
    [bool]((& $adb shell dumpsys window windows) -match "package=$app appop=CREATE_ACCESSIBILITY_OVERLAY")
}

# ── 진행 조절: Enter 바로 다음 / P 멈춤 / Q 그만
function Read-Key {
    try {
        if ([Console]::KeyAvailable) { return [Console]::ReadKey($true).Key }
    } catch {}
    return $null
}

function Wait-Next($sec, $msg = "다음 문자") {
    if ($Auto -or $script:quit) { return }
    for ($s = $sec; $s -gt 0; $s--) {
        Write-Host ("`r  ⏩ {0}초 뒤 {1}   (Enter: 바로 · P: 멈춤 · Q: 그만)   " -f $s, $msg) -NoNewline -ForegroundColor DarkGray
        for ($t = 0; $t -lt 10; $t++) {
            Start-Sleep -Milliseconds 100
            switch (Read-Key) {
                'Enter' { Write-Host ""; return }
                'Q' { Write-Host ""; $script:quit = $true; return }
                'P' {
                    Write-Host ""
                    Read-Host "  ⏸ 멈췄어요. 이어서 하려면 Enter" | Out-Null
                    return
                }
            }
        }
    }
    Write-Host ""
}

# ⚠️ 주의 문자: 휴대폰 아래쪽에 노란 카드가 떴다가 10초 뒤 저절로 사라지면 넘어간다
function Wait-Card {
    if ($Auto -or $script:quit) { return }
    Write-Host "  ⏳ 휴대폰에 문자 수신 알림이 오고, 곧 아래쪽에 ⚠️ 노란 카드가 떠요..." -ForegroundColor Magenta
    $shown = $false
    for ($i = 0; $i -lt 25; $i++) {
        if (Test-Overlay) { $shown = $true; break }
        Start-Sleep -Milliseconds 800
        if ((Read-Key) -eq 'Enter') { return }
    }
    if (-not $shown) {
        Write-Host "  (카드를 찾지 못했어요. 스마트 헬퍼의 '위험 링크 차단'이 켜져 있는지 확인하세요)" -ForegroundColor DarkYellow
        return
    }
    Write-Host "  👉 위험 팝업보다 가볍게, 화면을 가리지 않고 잠깐 떴다가 저절로 사라져요. (Enter: 기다리지 않기)" -ForegroundColor Magenta
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Milliseconds 800
        if (-not (Test-Overlay)) { Write-Host "  ✔ 카드가 사라졌어요." -ForegroundColor DarkGray; return }
        $k = Read-Key
        if ($k -eq 'Enter') { return }
        if ($k -eq 'Q') { $script:quit = $true; return }
    }
}

# 🚨 위험 문자: 팝업이 뜨기를 기다렸다가, 발표자가 [알겠어요] 로 닫으면 넘어간다
function Wait-Popup {
    if ($Auto -or $script:quit) { return }
    Write-Host "  ⏳ 휴대폰에 문자 수신 알림이 오고, 곧 🚨 팝업이 떠요..." -ForegroundColor Magenta
    $shown = $false
    for ($i = 0; $i -lt 25; $i++) {
        if (Test-Overlay) { $shown = $true; break }
        Start-Sleep -Milliseconds 800
        if ((Read-Key) -eq 'Enter') { return }
    }
    if (-not $shown) {
        Write-Host "  (팝업을 찾지 못했어요. 스마트 헬퍼의 '위험 링크 차단'이 켜져 있는지 확인하세요)" -ForegroundColor DarkYellow
        return
    }
    Write-Host "  👉 휴대폰에서 팝업을 보여 준 뒤 [알겠어요] 를 누르면 다음 문자로 넘어가요. (Enter: 기다리지 않기)" -ForegroundColor Magenta
    for ($i = 0; $i -lt 150; $i++) {
        Start-Sleep -Milliseconds 800
        if (-not (Test-Overlay)) { Write-Host "  ✔ 팝업을 닫았어요." -ForegroundColor DarkGray; return }
        $k = Read-Key
        if ($k -eq 'Enter') { return }
        if ($k -eq 'Q') { $script:quit = $true; return }
    }
}

# ── 문자 한 통: 보내고, 판정을 기다려 보여 주고, 기대와 비교
function Send-One($chapter, $no, $total, $m) {
    $from = switch ($m.From) {
        "new" { New-Number }
        "intl" { "+86139" + (Get-Random -Minimum 10000000 -Maximum 99999999) }
        default { $m.From }
    }
    $before = Get-Latest
    $beforeId = if ($before) { $before.id } else { 0 }
    Write-Host ""
    Write-Host "  ▶ 문자 분류: $($m.Cat)" -ForegroundColor White
    & $adb emu sms send $from $m.Text | Out-Null
    Write-Host "  📩 $from 님이 보낸 문자" -ForegroundColor Cyan
    Write-Host "     $($m.Text)"
    $v = $null
    for ($i = 0; $i -lt 40; $i++) { # 막 켠 휴대폰은 첫 판정이 늦을 수 있어 최대 28초
        Start-Sleep -Milliseconds 700
        $v = Get-Latest
        if ($v -and $v.id -ne $beforeId) { break }
        $v = $null
    }
    if (-not $v) {
        Write-Host "  (판정 결과를 읽지 못했어요. 휴대폰 화면을 확인하세요)" -ForegroundColor DarkYellow
        [void]$script:results.Add([pscustomobject]@{ 분류 = $m.Cat; 기대 = $LEVEL[$m.Expect]; 판정 = "?"; 결과 = "?" })
        return
    }
    $ok = ($v.level -eq $m.Expect)
    $mark = if ($ok) { "✔ 기대대로" } else { "✘ 기대와 다름 (기대: $($LEVEL[$m.Expect]))" }
    Write-Host "  → 스마트 헬퍼 판정: $($LEVEL[$v.level])  $mark" -ForegroundColor $COLOR[$v.level]
    foreach ($r in $v.reasons) { Write-Host "       · $r" }
    if ($m.Note) { Write-Host "  💡 $($m.Note)" -ForegroundColor DarkCyan }
    [void]$script:results.Add([pscustomobject]@{ 분류 = $m.Cat; 기대 = $LEVEL[$m.Expect]; 판정 = $LEVEL[$v.level]; 결과 = $(if ($ok) { "✔" } else { "✘" }) })
    if ($v.level -eq "HIGH") { Wait-Popup; Wait-Next 3 }
    elseif ($v.level -eq "MID") { Wait-Card; Wait-Next 3 }
    else { Wait-Next 5 }
}

function Run-Chapter($c) {
    if ($script:quit) { return }
    $items = @($c.Items)
    Write-Host ""
    Write-Host "════════════════════════════════════════════════════════" -ForegroundColor $c.Color
    Write-Host "  $($c.Title)" -ForegroundColor $c.Color
    Write-Host "════════════════════════════════════════════════════════" -ForegroundColor $c.Color
    Wait-Next 3 "첫 문자"
    $n = 0
    foreach ($m in $items) {
        if ($script:quit) { return }
        $n++
        Send-One $c $n $items.Count $m
    }
}

function Show-Summary {
    if ($script:results.Count -eq 0) { return }
    Write-Host ""
    Write-Host "  ── 결과 요약 ──" -ForegroundColor Yellow
    $script:results | Format-Table -AutoSize | Out-String | Write-Host
    $bad = @($script:results | Where-Object { $_.결과 -ne "✔" }).Count
    if ($bad -eq 0) { Write-Host "  모두 기대대로예요. ($($script:results.Count)통)" -ForegroundColor Green }
    else { Write-Host "  기대와 다른 판정 $bad 통 / 전체 $($script:results.Count)통" -ForegroundColor Red }
}

function Reset-Run {
    $script:results.Clear()
    $script:quit = $false
}

# ── 준비
Write-Host ""
Write-Host "=== 스마트 헬퍼 데모: 위험도·문자 분류별 문자 수신 ===" -ForegroundColor Yellow
if (-not (Test-Path $adb)) { Write-Host "adb 를 찾을 수 없어요: $adb" -ForegroundColor Red; exit 1 }
if (-not (& $adb devices | Select-String "emulator-\d+\s+device")) { Write-Host "가상 휴대폰(에뮬레이터)이 켜져 있지 않아요. 먼저 켜 주세요." -ForegroundColor Red; exit 1 }
if (-not (& $adb shell pm list packages $app)) { Write-Host "가상 휴대폰에 스마트 헬퍼가 설치되어 있지 않아요." -ForegroundColor Red; exit 1 }
# 팝업·링크 차단(접근성)과 수신 알림 뒤 경고 순서(알림 읽기)가 동작하도록 켜 둔다 (앱을 다시 설치하면 꺼진다)
& $adb shell settings put secure enabled_accessibility_services "$app/$app.guide.GuideService"
& $adb shell cmd notification allow_listener "$app/$app.guard.MessengerListener"
& $adb shell am start -n "$app/.MainActivity" 2>$null | Out-Null
Start-Sleep 2
& $adb shell input keyevent HOME

if ($Auto) {
    foreach ($c in $CHAPTERS) { Run-Chapter $c }
    Show-Summary
    exit @($script:results | Where-Object { $_.결과 -ne "✔" }).Count
}

while ($true) {
    Write-Host ""
    Write-Host "  어떤 시연을 할까요?" -ForegroundColor Yellow
    Write-Host "   A. 처음부터 끝까지 (✅ 진짜 택배 → ⚠️ 해외 결제 → 🚨 가짜 택배, 한 통씩)  ← 발표용"
    Write-Host "   1. ✅ 정상 문자만"
    Write-Host "   2. ⚠️ 주의 문자만"
    Write-Host "   3. 🚨 위험 문자만"
    Write-Host "   Q. 끝내기"
    $r = Read-Host "  골라 주세요"
    if ($null -eq $r) { exit 0 }
    switch -Regex ($r.Trim()) {
        '^[Aa]$' { Reset-Run; foreach ($c in $CHAPTERS) { Run-Chapter $c }; Show-Summary }
        '^1$' { Reset-Run; Run-Chapter $CHAPTERS[0]; Show-Summary }
        '^2$' { Reset-Run; Run-Chapter $CHAPTERS[1]; Show-Summary }
        '^3$' { Reset-Run; Run-Chapter $CHAPTERS[2]; Show-Summary }
        '^[Qq]$' { Write-Host "시연을 마쳤어요."; exit 0 }
        default { Write-Host "  A, 1, 2, 3, Q 중에서 골라 주세요." -ForegroundColor DarkYellow }
    }
}
