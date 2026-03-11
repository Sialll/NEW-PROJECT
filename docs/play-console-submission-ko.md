# Play Console 제출 초안 (KO)

최종 업데이트: 2026-03-11

이 문서는 현재 `main` 브랜치의 MoneyMind 코드베이스 기준으로 작성한 Play Console 제출 초안입니다.

## 1. 스토어 등록 문구

### 앱 이름

`MoneyMind`

### 짧은 설명

`알림·명세서 가져오기로 빠르게 기록하는 개인용 가계부`

### 자세한 설명 최종본

`MoneyMind`는 개인 사용자를 위한 안드로이드 가계부 앱입니다. 결제 및 입출금 알림을 바탕으로 거래를 빠르게 기록하고, CSV/XLS/XLSX/PDF 명세서를 가져와 과거 내역도 한 번에 정리할 수 있습니다.

주요 기능:

- 금융 앱 알림 기반 자동 기록
- CSV, XLS/XLSX, PDF 명세서 가져오기
- 수입, 지출, 내부이체 자동 분류
- 카테고리 예산과 월말 정산 확인
- 은행용/분석용 CSV 내보내기

MoneyMind는 복잡한 공유 기능보다 개인 기록과 월별 소비 점검에 집중합니다. 사용자가 허용한 알림, 직접 선택한 파일, 수동 입력 데이터를 기반으로 가계부를 구성하며, 데이터는 기본적으로 기기 내부에 저장됩니다.

광고 SDK나 외부 동기화 서버를 기본 포함하지 않으며, 빠르게 기록하고 직접 점검하는 개인형 가계부 경험에 초점을 맞췄습니다.

### 자세한 설명 짧은 대안

`MoneyMind`는 알림과 명세서 가져오기로 거래를 빠르게 정리할 수 있는 개인용 가계부입니다. 금융 앱 알림 자동 기록, CSV/XLS/XLSX/PDF 가져오기, 자동 분류, 카테고리 예산, CSV 내보내기를 한곳에서 지원합니다. 데이터는 기본적으로 기기 내부에 저장되며, 개인 기록과 월별 소비 점검에 집중한 구조로 설계되었습니다.`

### 출시 노트 최종본

`첫 공개 버전입니다. 알림 기반 자동 기록, 명세서 가져오기, 카테고리 예산, 월말 정산, CSV 내보내기를 지원합니다.`

### 출시 노트 짧은 대안

`알림 자동 기록, 명세서 가져오기, 예산 관리 기능을 포함한 첫 공개 버전입니다.`

## 2. Play Console 입력 초안

### 앱 카테고리

권장값: `Finance`

### 지원 이메일

실제 운영 가능한 이메일을 넣어야 합니다.

예시 형식:

`support@example.com`

### 개인정보처리방침 URL

현재 저장소 문서:

`https://github.com/Sialll/NEW-PROJECT/blob/main/docs/privacy-policy.md`

실제 제출 시에는 GitHub Pages나 별도 정적 페이지처럼 더 안정적인 URL이 있으면 그쪽을 우선 권장합니다.

## 3. Data safety 초안

주의: 아래 답안은 현재 코드 기준 추론입니다.

- 근거
  - 기본 앱 구성에 광고 SDK, 분석 SDK, 외부 동기화 서버가 없습니다.
  - 거래/예산/룰 데이터는 기기 내부 DB에 저장됩니다.
  - Google Play 공식 문서상 `on-device only` 처리는 Data safety의 `collected` 로 보지 않습니다.

### 추천 답안

#### 앱에서 사용자 데이터를 수집하거나 공유하나요?

권장값: `아니요`

#### 이유

- 앱이 처리하는 거래 정보는 기본적으로 기기 내부에서만 사용됩니다.
- 사용자가 직접 실행한 CSV 내보내기 외에 외부 서버 전송이 없습니다.

#### 제출 전 다시 확인할 것

아래 중 하나라도 추가되면 위 답안은 바뀔 수 있습니다.

- Firebase Analytics
- Crashlytics
- 원격 백업/동기화
- 서버 API 연동
- 광고 SDK

## 4. 계정/데이터 삭제 관련 메모

현재 앱은 서버 계정 생성 흐름이 없습니다.

따라서 Play Console에서 계정 생성 여부를 묻는 항목이 나오면 현재 앱 구조상 `계정 없음` 방향으로 검토하면 됩니다.

데이터 삭제는 앱 내부 기능으로 가능합니다.

- 기간 기록 초기화
- 전체 기록 초기화
- 공장초기화

## 5. 리뷰어 메모 최종본

아래 문구를 Play Console `App access` 또는 리뷰 메모에 그대로 넣는 것을 권장합니다.

```text
MoneyMind is a personal finance ledger app for individual users.

Core behavior:
- The app records supported financial app notifications locally after the user enables Notification Access in Android settings.
- The app can also import user-selected CSV, XLS/XLSX, and PDF statements.
- Imported and captured transaction data is stored on-device only in the default build.
- The public release does not request SMS or Call Log permissions.
- No advertising SDK or external sync server is included in the default build.

How to test:
1. Launch the app.
2. Open the "옵션" tab and go to the "파일" section.
3. Tap "알림 접근 권한 열기" and enable MoneyMind in Android Notification Access settings.
4. Return to the app.
5. Use the import action to select a statement file, or use the test notification buttons to verify local transaction capture and classification behavior.
```

### 리뷰어 메모 짧은 대안

```text
This app is a personal finance ledger.
It stores supported financial notifications and imported statement files locally on-device.
The public release does not request SMS or Call Log permissions.
To test, open Options > File, enable Notification Access for MoneyMind, then import a statement file or trigger a test notification.
```

## 6. 제출 직전 체크리스트

- [ ] 릴리즈 키스토어 환경변수 설정
  - `MM_RELEASE_STORE_FILE`
  - `MM_RELEASE_STORE_PASSWORD`
  - `MM_RELEASE_KEY_PASSWORD`
  - `MM_RELEASE_KEY_ALIAS` (선택)
- [ ] `bundleFullPlay` 다시 실행
- [ ] 실제 업로드할 `.aab` 확인
- [ ] 아이콘, 스크린샷, 상세 설명, 지원 이메일 입력
- [ ] 개인정보처리방침 URL 입력
- [ ] Data safety 답안 재검토
- [ ] 콘텐츠 등급 설문 완료
- [ ] 개인 개발자 계정이면 closed testing 요건 충족 여부 확인

## 7. 로컬 빌드 메모

현재 확인된 AAB 경로:

`/var/folders/5c/t9g487mx3ws3q3tzg_yxghp80000gn/T/moneymind-build/app/outputs/bundle/fullPlay/app-full-play.aab`

실제 업로드용 번들 생성 명령:

```bash
export MM_RELEASE_STORE_FILE="/absolute/path/to/keystore.jks"
export MM_RELEASE_STORE_PASSWORD="..."
export MM_RELEASE_KEY_PASSWORD="..."
export MM_RELEASE_KEY_ALIAS="moneymind"
bash ./gradlew --no-daemon bundleFullPlay -Pkotlin.incremental=false
```

## 8. 공식 참고 문서

- Target API requirements:
  - https://support.google.com/googleplay/android-developer/answer/11926878
- Create and set up your app:
  - https://support.google.com/googleplay/android-developer/answer/9859152
- User Data / Privacy Policy:
  - https://support.google.com/googleplay/android-developer/answer/10144311
- Data safety section:
  - https://support.google.com/googleplay/android-developer/answer/10787469
- Content rating:
  - https://support.google.com/googleplay/android-developer/answer/9859655
- Testing requirements for new personal accounts:
  - https://support.google.com/googleplay/android-developer/answer/14151465
