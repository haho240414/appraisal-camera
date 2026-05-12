# Store Target Separation

이 저장소는 Android와 iOS 배포 대상을 분리해서 관리합니다.

## Android / Google Play

- 코드 위치: `android-app/`
- 제출 산출물: `.aab`
- 릴리즈/테스트 APK: GitHub Releases
- 제출 안내: `play-store-submission.md`
- 빌드 워크플로: `.github/workflows/play-store-bundle.yml`

## iOS / Apple App Store

- 코드 위치: `ios-app/AppraisalCameraIOS/`
- 제출 산출물: Xcode Archive → App Store Connect
- 제출 안내: `app-store-submission.md`
- 기준 기능: Android `camera-screen-apk-66`

## 공통

- 개인정보처리방침: `privacy-policy.html`
- 보안 안내: `SECURITY.md`
- 라이선스/복제 제한: `LICENSE`

앞으로 기능을 수정할 때는 Android와 iOS 중 어느 플랫폼에 반영할지 먼저 구분하고, 두 플랫폼 모두 필요한 기능은 각각의 폴더에 별도로 반영합니다.
