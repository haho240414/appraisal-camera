# Apple App Store 제출 준비서

이 문서는 `ios-app/AppraisalCameraIOS`를 App Store에 올리기 위한 체크리스트입니다.

## Android / iOS 구분

- Play Store용 Android 앱: `android-app/`
- App Store용 iOS 앱: `ios-app/AppraisalCameraIOS/`
- 개인정보처리방침 공통 초안: `privacy-policy.html`

## 현재 iOS 구현 상태

릴리즈 66 기준으로 다음 기능을 iOS SwiftUI 앱에 반영했습니다.

- 카메라 촬영
- 이미지 선택
- 토지 / 건물 / 제시외 / 기타 분류
- 자동 기호 증가
- 물건지 주소 입력
- 현지답사 모드 입력란
- 사진 목록
- 개별 삭제
- 현재 사진만 전체 삭제
- 작업 전체 삭제
- PDF 사진자료 생성 및 공유
- JPG 사진자료 생성 및 공유
- 앱 내부 사진 저장

## 아직 별도 마무리가 필요한 항목

- Mac/Xcode에서 실제 빌드 검증
- Apple Developer Team 선택
- App Store용 1024x1024 앱 아이콘 제작 및 등록
- App Store 스크린샷 제작
- PPTX 직접 생성 기능
- TestFlight 실제 기기 테스트

## App Store 제출 순서

1. Apple Developer Program 가입
2. Mac에서 Xcode 설치
3. `ios-app/AppraisalCameraIOS/AppraisalCameraIOS.xcodeproj` 열기
4. Signing & Capabilities에서 Team 선택
5. Bundle ID 확인: `com.haho240414.appraisalcamera.ios`
6. 실제 iPhone에서 카메라/사진 선택/PDF/JPG 공유 테스트
7. Product → Archive
8. Organizer에서 App Store Connect로 업로드
9. App Store Connect에서 새 앱 레코드 생성
10. TestFlight 내부 테스트
11. 앱 정보, 개인정보, 스크린샷, 심사 정보 입력
12. 심사 제출

## 개인정보 답변 기준

현재 iOS 앱은 자체 서버로 데이터를 전송하지 않습니다.

- 카메라 권한: 사진자료 촬영
- 사진 보관함 권한: 기존 사진 선택
- 처리 데이터: 사진, 물건지 주소, 메모, 채무자명, 답사자명, 촬영 시각
- 데이터 저장 위치: 사용자 기기 내부
- 외부 공유: 사용자가 iOS 공유 시트에서 직접 선택한 앱으로만 공유
- 추적/광고/분석 SDK: 없음

## 상품화 문구 초안

앱 이름 후보:

- 답사 사진자료 카메라
- 자체감정 사진자료
- 현장 사진자료 카메라

짧은 설명:

> 물건지 답사 사진을 분류·기호별로 정리하고 PDF/JPG 사진자료로 저장하는 업무용 카메라 앱

긴 설명:

> 답사 사진자료 카메라는 물건지 현장에서 사진을 촬영하면서 토지, 건물, 제시외, 기타 항목과 기호를 바로 지정할 수 있는 업무용 카메라 앱입니다. 촬영한 사진은 분류와 기호 순서에 맞춰 정렬되며, 물건지 주소와 촬영 시간을 포함한 사진자료를 PDF 또는 JPG 형식으로 저장하고 공유할 수 있습니다.
