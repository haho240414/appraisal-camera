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
    data class ConfirmDelete(val photo: MainActivity.PhotoItem) : AppSheet()
}
