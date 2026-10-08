# 스마트 헬퍼 (Smart Helper) — 프로젝트 브리프

> AI 코딩 도구(Claude Code, Cursor 등)가 매번 먼저 읽도록 프로젝트 루트에 두는 파일입니다.

## 한 문장 요약
어르신이 말하거나 화면을 보여 주면 AI가 현재 화면과 상황을 이해해 **한 단계씩** 사용법을 안내하고, 사기와 위험 행동을 예방하며, 키오스크 연습을 지원하는 디지털 생활 도우미.

## 사용자
스마트폰이 서툰 고령 사용자. 모든 UI는 **큰 글씨, 쉬운 말(존댓말), 음성 안내**가 기본.

## 화면 구성
- 홈(웹 브라우저): **말로 물어보기 / 수상한 문자 확인 / 키오스크 연습**
- 홈(안드로이드 앱): **말로 물어보기 / 키오스크 연습 / 문자 지킴이** (문자 지킴이 안: 자동 감시 · 더 지키기[💬 카카오톡 지킴이, 🔗 위험 링크 차단] · 직접 검사 · 기록)
  - '말로 시키기'(AI가 대신 실행)는 빅스비와 겹쳐 제거. 이 앱은 **대신 해 주지 않고 스스로 하도록 가르치는 AI**
  - 앱에서는 '수상한 문자 확인'을 '문자 지킴이' 안의 **✍️ 직접 검사하기**로 합침(카톡 등 다른 경로로 받은 글, 권한 없을 때 대비)
- 하단 고정 버튼(모든 화면): **처음 화면 / 이전 단계 / 다시 설명 / 도움 종료**

## 기능 명세
### 1. 말로 물어보기
- 마이크로 음성 질문 (느린 말투, 사투리 등 일상 표현 인식이 목표)
- **사용자 동의 후** 현재 화면 분석 → 버튼·아이콘·문구 위치를 인식해 눌러야 할 곳을 **화살표/테두리**로 표시
- 화면이 바뀌면 다음 행동을 자동 안내, 작업 완료 시 화면 공유 종료
- 🎓 **연습(시뮬레이션)**: 화면 속 가상 휴대폰 — **삼성 갤럭시(One UI) 모양**(`index.html` 의 `D` 화면들, `o*` 도우미 함수와 `.o*` 스타일). 메뉴 이름·순서도 갤럭시 기준(디스플레이 → 글자 크기와 스타일, 애플리케이션 → 앱 → 삭제 → 확인 등). 실제 갤럭시를 보고 그린 것은 아니어서 시연 폰이 생기면 맞춰 볼 것. 사진 보내기 / 전화 걸기 / 메시지 보내기 / 알람 / 와이파이 / 글자 크기 / 음량 / 앱 설치·삭제 / 연락처 추가·삭제
- 📱 **내 휴대폰으로 해 보기(앱 전용, 실제 화면)**: 사진 보내기 / 메시지 보내기 / 글자 크기 / 와이파이 / 앱 설치(Play 스토어) / 앱 삭제(설정) — 실제 앱 위에 테두리·화살표
- 은행 송금·이체: 연습·안내 모두 하지 않음(가상 휴대폰의 은행 앱도 제거). 말하면 안전 안내만

### 2. 수상한 문자 확인
- 문자 붙여넣기 또는 캡처 사진 업로드 → 위험도(위험/주의/낮음) + 근거 + 대처법을 쉬운 말로
- 자동 감시 3단계(연락처 저장 번호 통과 → 링크 없으면 통과 → 로컬 패턴 검사)
  - 패턴: IP 주소 URL, `.apk` 링크, 의심 TLD(top/xyz/site/club/work/kim/space/online), 위험 키워드 2개 이상
- 한계: 링크 없는 사칭(자녀·검찰)은 자동 감시가 통과 → "직접 확인"에서 말투/요구 내용 규칙으로 보완

### 3. 키오스크 연습
- 실제 키오스크 모양: 대기 화면, 남은 시간(0초여도 처음으로 돌리지 않고 안내만), 큰 글씨, 아래 카드·QR·지폐·신분증 투입구와 영수증 출력구
- 음식점(탭·메뉴판·수량 팝업·장바구니·적립·결제), 병원(업무별 흐름: 진료 접수 / 수납·증명서는 금액 확인 후 결제), 기차(역·날짜·인원·시간표·좌석표)
- 수준: 따라 하기 / 연습하기 / 혼자 하기 / 자유롭게
- **어떤 선택을 해도 진행**, 잘못 눌러도 불이익 없이 이전 단계로 복귀

## 안전 설계 (반드시 지킬 것)
1. 송금·결제·비밀번호 입력은 AI가 대신 수행하지 않는다. 화면 공유를 중단하고 쉬운 음성으로만 안내.
2. 화면 공유·카메라는 **사용자 동의 후** 실행, 상태(🔴/⚪)를 항상 표시.
3. 금융·인증 화면과 개인정보는 저장하지 않거나 자동으로 가린다(긴 숫자 마스킹 등).
4. 출처 불분명한 앱 설치, 보안 해제는 차단한다.
5. 연습용 입력창에는 "실제 번호/비밀번호를 넣지 마세요" 안내.

## 목표
**AI 경진대회 출품용 실제 안드로이드 앱.** API 키 없음 → AI는 휴대폰 안에서 동작:
A안(직접 학습한 소형 분류 모델) + C안(휴대폰 안 소형 언어모델, Gemma 계열 약 1B 4비트·0.5~0.6GB 예정).
시연용 실제 휴대폰은 구하는 중(기종 미정, RAM 6GB 이상 권장).

## 현재 구현 상태
### 웹 (`index.html`) — 브라우저에서는 시뮬레이션, 앱 안에서는 화면(UI) 역할
- 단일 파일 (HTML+CSS+JS, 외부 의존성 없음). 앱이 이 파일을 빌드 때 assets 로 복사해 WebView 로 띄운다.
- `window.Android` 가 있으면(앱 안) 안드로이드 기능 사용: 음성 안내/인식, 뒤로 가기(`appBack`), 문자 지킴이, 실제 화면 안내(`Android.realGuide(말, 종류)` → `onReal`)
- 상태: `view`, `dev`(가상 폰 상태), `goal`(안내 목표), `sharing`(화면 공유), `k`(키오스크)
- 앱 전용: **🛡️ 문자 지킴이**(`guardRender`), 말로 물어보기의 실제 안내 선택(`G.*.real` 이 있는 시나리오)

### 안드로이드 (`android/`, Kotlin, 패키지 `com.smarthelper.app`)
- `MainActivity` — WebView + `Bridge`(JS ↔ 안드로이드: speak/listen/realGuide/realAnswer/guardStatus/checkText 등)
- `guard/` 문자 지킴이 (수신 문자 자동 스미싱 감지, 문자는 휴대폰 밖으로 안 나감)
  - `SmishingReceiver` 단계: 저장된 연락처 통과 → 규칙 검사 → AI 모델 (링크 유무와 관계없이 항상) → 내용 밖 단서
  - `SenderSignals` 내용 밖 단서(순수 Kotlin, `SenderSignalsTest`): 처음 온 번호+가족 말투 → 주의(문자만), 해외·070 +1(문자만), 24시간 안 의심 상대가 돈·상품권·인증·설치·링크 요구 → +6(위험), 그 밖의 다음 메시지도 주의 유지(문자·메신저). `SmishingEngine.withExtra` 로 합침. `SenderBook` 이 상대별 횟수·마지막 의심 시각을 SHA-256 키로 저장(기록 지우기 때 함께 지움). `GuardAlert.handle` 에서 연결
  - `SmishingEngine` 검사기 꽂기 구조: `RuleDetector`(규칙) + `ModelDetector`(A안 모델). `heavy=true` 검사기는 규칙 뒤에, 저장 안 된 번호면 항상 실행 (`LinklessModelTest`)
  - `TextModel` 글자 n-gram 로지스틱 회귀(64KB, `assets/smishing_model.txt`), `WarningActivity` 큰 글씨 경고, `GuardStore` 기록(긴 숫자 가림)
  - `Guard.init()` 엔진 준비(링크 인식기 + 모델) — 자동 검사와 직접 검사(`Bridge.checkText`, 기록 안 남김)가 같은 엔진 사용
  - `GuardAlert` 문자·메신저 공통 검사→기록→알림. 경고 순서: 문자 앱 수신 알림 → 2초 뒤 경고 알림 + 팝업(`GuardAlert.afterSmsNotice`. `MessengerListener` 가 기본 문자 앱의 category=msg 알림을 보면 `onSmsAppNotice`, 알림 읽기가 꺼져 있으면 3초·켜져 있는데 못 보면 10초 뒤. 에뮬레이터 구글 메시지는 수신 알림이 6~8초 늦고 그 전에 category=service 알림을 띄움). 🚨 HIGH 면 알림과 함께 `GuideService.showRiskPopup` → `RiskPopupOverlay`(접근성 겹쳐 그리기 창, 화면 가운데 카드: 왜 위험한지 보기 → WarningActivity / 알겠어요, 음성 안내). ⚠️ MID 면 `GuideService.showCautionPopup` → `CautionBannerOverlay`(화면 아래쪽 작은 노란 카드, FLAG_NOT_TOUCH_MODAL 로 카드 밖 터치는 뒤 앱으로, 10초 뒤 자동으로 사라짐, 자세히 → WarningActivity / 닫기, 한 문장 음성). 접근성 서비스가 꺼져 있거나 링크·설치 차단 화면(주의 카드는 위험 팝업도)이 떠 있으면 알림만. `SmishingReceiver` 는 goAsync 를 4초만 붙잡는다(9초는 느린 기기에서 방송 10초 제한을 넘겨 ANR로 앱이 죽었음). 안드로이드 10+ 는 문자 수신 때 앱이 직접 화면을 못 띄워서 이 방식을 씀. `MessengerListener`(알림 읽기 권한) 카카오톡·라인·텔레그램·페메·왓츠앱 새 메시지 검사. 이름이 연락처와 같으면 HIGH 일 때만 알림(사칭 대비). 디버그 앱은 `adb shell cmd notification post` 시험 알림도 메신저로 봄
  - `OfficialSites` 공식 사이트 목록(택배·은행·카드·`.go.kr` 등, 주소 끝 정확히 일치, `OfficialSitesTest`): 문자 속 링크가 모두 공식이면 링크 규칙(+3)·위험 단어 flag 를 주지 않고, `GuardStore` 'hosts' 에도 넣지 않으며, `LinkGuard` 는 공식 주소를 막지 않음. `index.html` 의 `OFFICIAL`/`isOfficial`/`allOfficial` 과 같은 목록으로 맞출 것
  - `LinkGuard` 위험 링크: 위험 판정 메시지의 링크 주소(`GuardStore` 'hosts') + 주소 모양(IP·.apk·.xyz 등). `GuideService` 가 브라우저 주소창만(id 로 바로 찾음, 0.7초 간격) 보고 `LinkBlockOverlay` 전체 화면 경고(안전하게 나가기 / 그래도 볼래요 → "정말 들어가시겠어요?" 확인 창에서 한 번 더 물음). 위험 팝업(`RiskPopupOverlay`)이 화면을 덮는 동안은 안드로이드가 가려진 브라우저 창을 알려 주지 않으므로, 팝업을 닫을 때(`onClosed`) 주소창을 다시 검사
  - 카카오톡(`GuideService.IN_APP`): 채팅방 말풍선·미리보기 카드를 누르는 순간(TYPE_VIEW_CLICKED) 글자에서 링크를 찾아 막고, 앱 안 WebView 가 있으면 WebView 바깥(제목 줄) 글자만 봄(`InAppLink`, 순수 Kotlin, `InAppLinkTest`). 나가기는 웹 화면이 열려 있을 때만 뒤로 가기. 디버그 앱은 testapp(패키지 `com.smarthelper.practicepuzzle`)의 가짜 카카오톡도 봄: `adb shell am start -n com.smarthelper.practicepuzzle/com.smarthelper.testapp.FakeChatActivity --es msg "'택배 확인 http://cj-logis.xyz/a'"` → 말풍선(540,330) 누르기. 실제 카카오톡 제목 줄에 주소가 보이는지는 실기기 확인 필요
  - `InstallGate` 설치 차단(순수 Kotlin, 테스트 19개): 주의 이상 메시지 후 30분 위험 시간대, 설치 앱+설치 글자 / 설정+'알 수 없는 앱 설치' 판단, '그래도 진행' 5분 유예. `GuideService.checkInstall` → `InstallBlockOverlay`(그만두기 · 가족 전화 ACTION_DIAL, 없으면 118 · 그래도 진행). 가족 연락처는 `GuardStore`(연락처 선택 창으로 고름, 권한 불필요)
  - 설치 화면 시험: testapp APK를 `/sdcard/Download/puzzle.apk`로 push → 미디어 스캔 → `am start -a android.intent.action.VIEW -d content://media/external/file/<id> -t application/vnd.android.package-archive --grant-read-uri-permission`. 연락처 선택 창은 캡처가 막혀 DUMP_SCREEN 으로 위치를 찾음
- `assist/CommandParser` 규칙 기반 말 이해(순수 Kotlin) — 실제 안내에서 받는 사람 이름 등을 뽑는 데 사용 (대신 실행하던 `Assistant` 는 제거)
- `guide/` 화면 안내 (접근성 서비스, 사용자가 설정에서 직접 켬)
  - `GuideService` 문자·설정 앱 위에 `GuideOverlay`(노란 테두리·👇·큰 말풍선·🔴 띠+그만) 표시. "찾을 수 있는 가장 뒤 단계"가 지금 단계
  - `Guide`(단계·완료 판단·길 잃음 안내·받는 사람·직접 완료 확인) / `Guides.photo·text·font·wifi·install·uninstall` — 구글 기본 앱 기준, 삼성 메뉴 이름도 함께 찾음(실기기 확인 필요)
  - `RealGuide` 말로 물어보기에서 고른 안내를 시작. 사진·문자는 **받는 사람 확인 후에만 안내**(번호 전체 비교)
- `testapp/` **연습용 퍼즐**: 지워도 되는 빈 앱. 앱 삭제 안내를 안전하게 시험·시연할 때 설치해 둔다 (`gradle :testapp:assembleDebug`)
- 테스트(PC): `CommandParserTest` 32, `SmishingEngineTest` 27, `ModelDetectorTest` 6, `InstallGateTest` 19, `LinklessModelTest` 6, `SenderSignalsTest` 19, `InAppLinkTest` 9, `OfficialSitesTest` 8

## 개발 환경·명령 (Windows, 사용자 이름이 한글이라 경로를 영어로 분리함)
- SDK `C:\Android\Sdk`, Gradle 홈 `C:\Android\gradle`(GRADLE_USER_HOME), Gradle 배포본 `C:\Android\gradle-dist\gradle-9.8.0`
- JDK: `C:\Program Files\Android\Android Studio\jbr` (JAVA_HOME 으로 지정), AGP 9.4.1(내장 Kotlin), compileSdk 37 / targetSdk 36 / minSdk 26
- 빌드·테스트: `android` 폴더에서 `gradle :app:testDebugUnitTest assembleDebug` (또는 `gradlew.bat`)
- A안 재학습: `gradle :app:testDebugUnitTest --tests "*ModelTrainer*" -Ptrain=1` → `data/sms_real.tsv`(라벨<TAB>문자, 사기=1) 있으면 함께 학습
- 설치: `C:\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk`
- 가짜 문자: `adb emu sms send 01048217733 "문자 내용"`
- 주의
  - `uiautomator dump` 를 실행하면 접근성 서비스가 잠시 끊긴다 → 화면 안내 시험 중에는 스크린샷+좌표로만 조작
  - 앱 재설치 직후 접근성 서비스는 몇 초 뒤 다시 연결된다
  - 앱을 다시 설치하면 접근성 서비스 설정이 꺼진다 → `adb shell settings put secure enabled_accessibility_services com.smarthelper.app/com.smarthelper.app.guide.GuideService`
  - 에뮬레이터는 사진 문자(MMS) 불가("첨부파일이 지원되지 않습니다") → 사진 보내기 3단계는 실제 폰에서 확인
  - 시험용 연락처 번호는 끝자리가 겹치지 않게(문자 앱이 끝자리만 비교해 다른 대화를 연 적 있음)

## 권장 폴더 구조 (분리 시)
```
smart-helper/
├─ index.html
├─ css/style.css
├─ js/
│  ├─ app.js        # 홈, 하단 고정 버튼, 공통(say, consent)
│  ├─ ask.js        # 말로 물어보기 (가상 폰, guide, secure)
│  ├─ scam.js       # 수상한 문자 (SHOTS, RULES, smish, analyze)
│  ├─ kiosk.js      # 키오스크 (K, M, playRender)
│  └─ data/         # 시나리오(GOALS), 키오스크 단계 데이터
└─ CLAUDE.md
```

## 코딩 규칙
- 한국어 UI, 쉬운 말. 기본 글씨 크게(최소 18px), 버튼은 크게.
- 외부 라이브러리·CDN 없이 동작 유지(필요 시 이유 설명 후 추가).
- API 키는 **절대 클라이언트 코드에 넣지 않는다**. 외부 API는 서버리스 함수 경유.
- 동작을 바꾸지 않는 리팩터링과 기능 추가를 한 번에 섞지 않는다.
- 클릭 가능한 요소는 `<button data-id="...">` (화면 인식·안내 로직이 `data-id`를 사용).

## 다음 작업 후보
- 앱 설치 안내는 로그인 화면 감지까지만 확인함 → 구글 계정 로그인된 기기에서 검색·설치 단계 확인 필요
- 실제 폰(갤럭시 예상) 확보 후: 실제 화면 안내 6종을 삼성 문자·설정 앱에서 확인하고 버튼 찾는 규칙 보완, 사진 보내기 마지막 단계(에뮬레이터는 MMS 불가), 와이파이 비밀번호 창, 마이크 음성 인식
- 실제 화면 안내 늘리기: 알람(시계 앱), 전화 걸기(전화 앱), 음량
- C안: 휴대폰 안 언어모델(Gemma 약 1B) — 위험 이유 쉬운 말 설명 + 규칙이 못 알아들은 말 해석
- 실제 문자 데이터 수집 → A안 재학습 (지금 성능은 합성 데이터 기준: 최종 시험 92개에서 앱 전체 정확도 96.7%, 정밀도 97.6%, 재현율 95.3%)
  - 고정 시험 문제: `data/sms_holdout.tsv`(개발용, 보고 고쳐도 됨) / `data/sms_final_test.tsv`(최종, 틀린 문자를 보고 고치지 말 것 — 새로 고칠 일이 생기면 최종 시험 문제도 새로 써야 공정). 둘 다 학습 금지. 채점표는 `app/build/holdout_report.txt`(ModelDetectorTest 가 만듦)
  - 두 낱말 묶음 특징은 시험해 봤지만 개발용 점수가 오히려 낮아져 넣지 않음
- 대회 발표 자료(구조도, 동작 흐름, 성능 표)
## 테스트 체크리스트 (웹 시뮬레이션)
- [ ] 말로 물어보기 6개 시나리오가 끝까지 안내되고 완료 시 화면 공유가 꺼진다
- [ ] 송금 화면에서 화면 인식이 중단되고 가림 처리된다
- [ ] 샘플 문자 5종 결과가 기대와 같다(위험 4 / 낮음 1)
- [ ] 키오스크 3종이 끝까지 진행되고 "이전 단계"가 건너뛴 단계를 거른다
- [ ] 하단 고정 버튼 4개가 모든 화면에서 동작한다
- [ ] 마이크 권한 거부 시 입력칸·예시 버튼으로 대체된다
