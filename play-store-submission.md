# 자체감정 카메라 Play Store 제출 준비서

기준 코드: `camera-screen-apk-66` 기능 상태 + Play Store 제출용 서명/AAB/보안 설정

## 현재 충족 상태

- 패키지명: `com.codex.appraisalcamera`
- 앱 이름: `자체감정 카메라`
- 최소 SDK: 29
- 타깃 SDK: 35
- 요청 권한: `CAMERA` 1개
- 인터넷 권한: 없음
- 앱 백업: 비활성화
- 일반 HTTP 통신: 비활성화
- 파일 공유: `cache/mail_exports/` 범위의 FileProvider만 사용
- 릴리즈 보호: R8 난독화/코드 축소/리소스 축소 활성화
- Play Store 제출 형식: signed release AAB 생성 워크플로 추가

## GitHub Secrets

Play Store에 올릴 AAB와 업데이트 가능한 APK를 만들려면 저장소의 `Settings` → `Secrets and variables` → `Actions`에 아래 4개를 등록해야 한다.

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

처음 등록 후 `Actions` → `Build Play Store AAB` → `Run workflow`를 실행하면 `app-release.aab` artifact가 생성된다.

## Play Console 등록 순서

1. Google Play Console 개발자 계정을 만든다.
2. 새 앱을 생성한다.
3. 앱 이름, 기본 언어, 앱/게임 여부, 무료/유료 여부를 입력한다.
4. Play App Signing을 사용한다.
5. `Build Play Store AAB` workflow에서 생성된 `app-release.aab`를 테스트 트랙 또는 프로덕션 트랙에 업로드한다.
6. 스토어 등록정보를 작성한다.
7. 개인정보처리방침 URL을 입력한다.
8. Data safety 양식을 작성한다.
9. 앱 콘텐츠, 타깃층, 광고 여부, 뉴스 앱 여부 등을 답변한다.
10. 내부 테스트 또는 비공개 테스트 후 프로덕션으로 제출한다.

## Data safety 작성 참고

현재 코드 기준:

- 앱 자체 서버로 수집/전송하는 데이터: 없음
- 제3자 SDK 분석/광고 전송: 없음
- 위치/연락처/마이크/전화 권한: 없음
- 사용자가 직접 공유 버튼으로 외부 앱에 파일을 보내는 기능: 있음
- 앱 내 처리 데이터: 사진, 물건지 주소, 메모, 채무자명, 현지답사자명, 촬영/등록 시간

Play Console Data safety에서는 실제 운영 방식에 맞춰 답해야 한다. 앱이 자체 서버로 데이터를 보내지 않는 현재 구조라면, 데이터는 기기 내 처리이며 사용자가 명시적으로 공유할 때만 외부 앱으로 전달된다고 설명하면 된다.

## 스토어 등록 문구 초안

짧은 설명:

> 물건지 답사 사진을 토지·건물·제시외·기타 항목별로 정리하고 PPTX/PDF/JPG 사진자료로 저장하는 카메라 앱

긴 설명:

> 자체감정 카메라는 물건지 답사 현장에서 사진을 촬영하면서 토지, 건물, 제시외건물, 기타 항목과 기호를 바로 지정할 수 있는 업무용 카메라 앱입니다. 촬영한 사진은 분류와 기호 순서에 맞춰 정렬되며, 물건지 주소와 촬영시간을 포함한 사진자료를 PPTX, PDF, JPG 형식으로 저장하거나 공유할 수 있습니다. 현지답사 모드에서는 채무자명과 현지답사자명을 함께 기록할 수 있습니다.

## 제출 전 확인

- 실제 갤럭시 기기에서 카메라 촬영, 이미지 선택, 저장, 공유, 작업 저장/불러오기 확인
- 개인정보처리방침 URL이 외부에서 열리는지 확인
- 스토어 그래픽: 512x512 앱 아이콘, 스크린샷 2장 이상, 대표 이미지 준비
- 첫 Play Store 릴리즈는 내부 테스트 트랙으로 먼저 제출

## 복제 방지 관련

앱을 완전히 복제 불가능하게 만드는 방법은 없습니다. 다만 현재 설정은 다음 방어를 적용합니다.

- 릴리즈 APK/AAB는 R8로 난독화되어 역공학이 어려워집니다.
- 공식 릴리즈는 서명키가 있어야 생성됩니다.
- 저장소에는 `All rights reserved` 라이선스를 명시했습니다.
- 실제 복제 위험을 줄이려면 저장소를 private으로 전환하는 것이 가장 효과적입니다.
