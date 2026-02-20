# Google Play 내부 테스트 배포 (MoneyMind)

## 업로드 파일 (v1.0.3, 알림 수집 ON)

- `C:\Users\CAREM_0410\OneDrive\Desktop\MoneyMind-Play내부테스트-v1.0.3-알림ON.aab`
- `C:\Users\CAREM_0410\OneDrive\Desktop\MoneyMind-Play-mapping-v1.0.3.txt` (deobfuscation)
- `C:\Users\CAREM_0410\OneDrive\Desktop\MoneyMind-Play-native-debug-symbols-v1.0.3-play.zip` (native symbols)
- 패키지: `com.moneymind.personal`
- 버전: `versionCode 4`, `versionName 1.0.3`
- 알림 수집: `NOTIFICATION_CAPTURE_ENABLED=true` (full/play)

## Play Console 순서

1. Play Console에서 앱 선택
2. `테스트 > 내부 테스트` 트랙 진입
3. `새 버전 만들기` 클릭
4. `MoneyMind-Play내부테스트-v1.0.3-알림ON.aab` 업로드
5. 릴리스 이름 입력 (예: `internal-1.0.3`)
6. 릴리스 노트 저장
7. 검토 후 내부 테스트 배포
8. 릴리스 상세 경고가 뜨면 아래 파일도 업로드
   - Deobfuscation file: `MoneyMind-Play-mapping-v1.0.3.txt`
   - Native debug symbols: `MoneyMind-Play-native-debug-symbols-v1.0.3-play.zip`

## 테스트 확인 순서

1. 내부 테스트 앱 설치 후 앱 실행
2. 메인 화면에서 `알림 접근 권한 열기` 클릭
3. 시스템 설정에서 `MoneyMind` 허용
4. 카드/은행 알림 발생 시 거래 자동 반영 확인

## 참고

- 내부 테스트 링크 반영까지 수분~수십분 걸릴 수 있음
- 다음 버전 배포 시 `versionCode`는 반드시 증가해야 함
