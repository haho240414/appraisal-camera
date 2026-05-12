# 자체감정 사진 안드로이드 앱

갤럭시에서 사용하는 Android 네이티브 앱 프로젝트입니다.

## 기능

- 토지, 건물, 제시외건물 선택 후 사진 촬영
- 앱 실행 시 바로 카메라 화면 표시
- 카메라 화면 위에서 토지, 건물, 제시외건물과 기호 선택
- 토지 기호: `1, 2, 3...`
- 건물 기호: `가, 나, 다...`, 수동으로 `가-1` 같은 세부 기호 선택 가능
- 제시외건물 기호: `ㄱ, ㄴ, ㄷ...`
- 출력자료는 `토지 → 건물 → 제시외건물` 순서로 자동 정렬
- 같은 분류 안에서는 기호 순서대로 정렬
- 출력자료 인쇄 버튼으로 Android 인쇄 화면 호출
- 출력자료는 제공된 감정평가서의 `사 진 용 지` 형식처럼 A4 기준 페이지당 사진 2장씩 배치
- 사진 오른쪽 아래에 촬영시간 표시
- PPTX 버튼으로 PowerPoint에서 열 수 있는 사진자료 저장
- 촬영 사진과 메모는 앱에 자동 저장

## 권한

- 카메라: 사진 촬영에 필요합니다.

## Android Studio에서 실행

1. Android Studio를 실행합니다.
2. `Open`을 누르고 이 폴더의 `android-app`을 선택합니다.
3. Gradle 동기화가 끝나면 갤럭시를 USB로 연결합니다.
4. 실행 대상에서 갤럭시를 선택하고 `Run`을 누릅니다.

처음 실행할 때 카메라 권한을 허용하면 됩니다.

## Android Studio 없이 APK 받기

GitHub에 이 프로젝트를 올리면 APK가 자동으로 만들어집니다.

### 가장 쉬운 방법: Releases에서 직접 받기

1. GitHub 저장소의 `Releases`를 엽니다.
2. 최신 `Appraisal Camera APK` 항목을 엽니다.
3. `appraisalcamera-camera-screen.apk`를 휴대폰에서 내려받습니다.
4. 내려받은 APK를 눌러 설치합니다.

### Actions Artifact로 받기

1. GitHub 저장소에 프로젝트를 업로드합니다.
2. GitHub 저장소의 `Actions` 탭을 엽니다.
3. `Build Android APK` 실행 결과를 엽니다.
4. `Artifacts`에서 `appraisal-camera-debug-apk`를 내려받습니다.
5. 압축을 풀고 `app-debug.apk`를 갤럭시에 설치합니다.

갤럭시에서 APK 설치 시 `출처를 알 수 없는 앱 설치` 허용이 필요할 수 있습니다. 설치 후에는 다시 꺼두는 편이 안전합니다.

## 기존 앱 위에 업데이트 설치되게 만들기

Android는 같은 앱이라도 APK 서명키가 달라지면 업데이트로 인정하지 않습니다. GitHub Actions에 릴리즈 서명키를 한 번 등록하면 이후 릴리즈 APK는 기존 앱을 지우지 않고 업데이트 설치할 수 있습니다.

주의: 이미 설치된 예전 디버그 APK와 새 릴리즈 서명 APK는 서명키가 다르므로, 서명키를 적용한 첫 APK는 한 번만 기존 앱을 삭제하고 설치해야 합니다. 그 다음 릴리즈부터는 그대로 업데이트됩니다.

1. PC에서 릴리즈 키를 만듭니다.

```powershell
keytool -genkeypair -v -keystore appraisal-camera-release.jks -alias appraisal-camera -keyalg RSA -keysize 2048 -validity 10000
```

2. 만든 키를 Base64 문자열로 바꿉니다.

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("appraisal-camera-release.jks")) | Set-Clipboard
```

3. GitHub 저장소에서 `Settings` → `Secrets and variables` → `Actions` → `New repository secret`로 아래 4개를 등록합니다.

- `ANDROID_KEYSTORE_BASE64`: 2번에서 복사된 Base64 문자열
- `ANDROID_KEYSTORE_PASSWORD`: 키 만들 때 입력한 keystore 비밀번호
- `ANDROID_KEY_ALIAS`: `appraisal-camera`
- `ANDROID_KEY_PASSWORD`: 키 만들 때 입력한 key 비밀번호

4. 등록 후 새 커밋을 푸시하거나 `Release Android APK` 워크플로를 수동 실행하면, 업데이트 가능한 서명 APK가 릴리즈됩니다.

서명키 파일과 비밀번호는 절대 공개 저장소에 올리면 안 됩니다. 키를 잃어버리면 같은 앱으로 업데이트를 이어갈 수 없으니 안전한 곳에 보관하세요.

## Play Store용 AAB 만들기

Google Play Store 신규 앱은 APK가 아니라 Android App Bundle(`.aab`)을 업로드합니다.

1. 위의 GitHub Secrets 4개를 등록합니다.
2. GitHub 저장소의 `Actions` 탭을 엽니다.
3. `Build Play Store AAB` 워크플로를 선택합니다.
4. `Run workflow`를 누릅니다.
5. 성공한 실행의 Artifacts에서 `app-release.aab`가 들어 있는 파일을 내려받습니다.
6. Google Play Console의 테스트 트랙 또는 프로덕션 트랙에 해당 AAB를 업로드합니다.

Play Store 제출 체크리스트와 스토어 문구 초안은 저장소 루트의 `play-store-submission.md`를 참고하세요.
