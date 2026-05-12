# iOS / App Store Version

이 폴더는 기존 Android 앱과 별도로 관리되는 iPhone용 앱입니다.

- Android / Play Store: `android-app/`
- iOS / Apple App Store: `ios-app/AppraisalCameraIOS/`

기능 기준은 Android `camera-screen-apk-66`입니다.

## 현재 구현 범위

- 카메라 촬영
- 사진 보관
- 토지 / 건물 / 제시외 / 기타 분류
- 자동 기호 증가
- 물건지 주소 입력
- 사진 목록 확인
- 개별 사진 삭제
- 현재 사진만 전체 삭제
- 작업 전체 초기화
- 사진자료 PDF 생성 및 공유
- 사진자료 JPG 생성 및 공유
- 앱 내부 저장

## iOS 2차 구현 후보

- PPTX 직접 생성
- 현지답사 모드 세부 UI 고도화
- 저장된 작업 목록 관리
- App Store용 1024px 아이콘/스크린샷 최종 디자인
- Apple Developer 자동 서명/업로드 워크플로

## Xcode에서 열기

1. Mac에서 Xcode를 설치합니다.
2. `ios-app/AppraisalCameraIOS/AppraisalCameraIOS.xcodeproj`를 엽니다.
3. Signing & Capabilities에서 Apple Developer Team을 선택합니다.
4. 실제 iPhone에서 카메라 권한을 허용하고 테스트합니다.
5. Product → Archive로 App Store Connect에 업로드합니다.

## 보안/개인정보

- 앱은 카메라와 사진 선택 권한만 사용합니다.
- 자체 서버로 사진이나 주소를 전송하지 않습니다.
- 공유는 사용자가 iOS 공유 시트에서 직접 선택한 앱으로만 진행됩니다.
