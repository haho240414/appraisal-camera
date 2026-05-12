package com.codex.appraisalcamera

/**
 * 메인 화면에서 열려 있는 시트/다이얼로그 상태.
 *
 * MainActivity 의 [MainActivity.openSheet] 가 이 값을 들고 있고,
 * Compose CameraScreen 이 관찰해서 적절한 sheet/dialog 를 그린다.
 */
sealed class AppSheet {
    data object None : AppSheet()
    data object PhotoList : AppSheet()
    data object Settings : AppSheet()
    data object Help : AppSheet()
    data object Address : AppSheet()
    data object EmailRecipient : AppSheet()
    data object MailApp : AppSheet()
    data object Mode : AppSheet()
    data object ExportFormat : AppSheet()
    data class ShareFormat(val recipient: String) : AppSheet()
    data object ConfirmClear : AppSheet()
    data object ConfirmClearPhotosOnly : AppSheet()
    data class ConfirmDelete(val photo: MainActivity.PhotoItem) : AppSheet()

    /**
     * 익스포트(PPTX/PDF/JPG) 진행 표시.
     * total = 0 이면 indeterminate spinner, 양수면 LinearProgressIndicator(current/total).
     */
    data class ExportProgress(val format: String, val current: Int, val total: Int) : AppSheet()

    /** 저장된 작업 목록 (불러오기/덮어쓰기/삭제). */
    data object Sessions : AppSheet()

    /** "현재 상태 새로 저장" 입력 시트. */
    data object SaveSessionPrompt : AppSheet()

    /** 세션 불러오기 전 확인 (현재 사진이 있을 때 덮어쓸 거라는 안내). */
    data class ConfirmLoadSession(val session: MainActivity.SavedSession) : AppSheet()

    /** 세션 삭제 확인. */
    data class ConfirmDeleteSession(val session: MainActivity.SavedSession) : AppSheet()
}
