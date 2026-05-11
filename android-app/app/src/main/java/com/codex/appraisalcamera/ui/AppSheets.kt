package com.codex.appraisalcamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.codex.appraisalcamera.AppSheet
import com.codex.appraisalcamera.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 메인 화면에 떠 있는 시트/다이얼로그 디스패처.
 * activity.openSheet 를 관찰해서 적절한 sheet 또는 dialog 를 그린다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheets(activity: MainActivity) {
    when (val sheet = activity.openSheet) {
        AppSheet.None -> Unit
        AppSheet.PhotoList -> PhotoListSheet(activity)
        AppSheet.Settings -> SettingsSheet(activity)
        AppSheet.Help -> HelpSheet(activity)
        AppSheet.Address -> AddressSheet(activity)
        AppSheet.EmailRecipient -> EmailRecipientSheet(activity)
        AppSheet.MailApp -> MailAppDialog(activity)
        AppSheet.Mode -> ModeDialog(activity)
        AppSheet.ExportFormat -> ExportFormatDialog(activity)
        is AppSheet.ShareFormat -> ShareFormatDialog(activity, sheet.recipient)
        AppSheet.ConfirmClear -> ConfirmClearDialog(activity)
        is AppSheet.ConfirmDelete -> ConfirmDeleteDialog(activity, sheet.photo)
        is AppSheet.ExportProgress -> ExportProgressSheet(sheet.format, sheet.current, sheet.total)
        AppSheet.Sessions -> SessionsSheet(activity)
        AppSheet.SaveSessionPrompt -> SaveSessionPromptSheet(activity)
        is AppSheet.ConfirmLoadSession -> ConfirmLoadSessionDialog(activity, sheet.session)
        is AppSheet.ConfirmDeleteSession -> ConfirmDeleteSessionDialog(activity, sheet.session)
    }
}

// =================================================================
// Bottom Sheets
// =================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoListSheet(activity: MainActivity) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sorted = activity.sortedPhotos()
    ModalBottomSheet(
        onDismissRequest = { activity.closeSheet() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            // Toss 풍 큰 헤더
            Text(
                text = "사진 자료",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = sorted.size.toString(),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "장 등록됨",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 600.dp)
            ) {
                items(sorted, key = { it.id }) { photo ->
                    Box(modifier = Modifier.animateItem()) {
                        PhotoCard(activity, photo)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoCard(activity: MainActivity, photo: MainActivity.PhotoItem) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 썸네일 — Coil 비동기 로딩, file:// / content:// 모두 지원.
            // 로딩 중에는 회색 박스 + 기호 텍스트가 placeholder 로 보였다가
            // 비트맵이 디코드되면 자연스럽게 페이드인.
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = photo.symbol.take(3),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photo.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = activity.photoTitle(photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activity.photoTitle(photo),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = activity.photoMetaText(photo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = { activity.confirmDeletePhoto(photo) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("삭제", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(activity: MainActivity) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var alpha by remember { mutableStateOf(activity.guideAlphaPercent.toFloat()) }
    var scale by remember { mutableStateOf(activity.guideScalePercent.toFloat()) }

    ModalBottomSheet(
        onDismissRequest = { activity.closeSheet() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .padding(bottom = 12.dp)
        ) {
            Text(
                "설정",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(20.dp))

            // 메일주소
            SettingRow(
                title = "기본 메일주소",
                value = activity.emailRecipient.ifEmpty { "미설정" },
                onClick = { activity.showEmailAddressSheet() }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // 공유 방식
            SettingRow(
                title = "기본 공유 방식",
                value = activity.mailAppLabel(activity.mailAppPref),
                onClick = { activity.showMailAppSheet() }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // 가이드 투명도
            Text(
                "배경 불투명도 ${alpha.toInt()}%",
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = alpha,
                onValueChange = { alpha = it },
                onValueChangeFinished = { activity.applyGuideAlpha(alpha.toInt()) },
                valueRange = 35f..100f
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "가이드 크기 ${scale.toInt()}%",
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = scale,
                onValueChange = { scale = it },
                onValueChangeFinished = { activity.applyGuideScale(scale.toInt()) },
                valueRange = 60f..100f
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingRow(
                title = "플로팅 촬영 버튼",
                value = if (activity.floatingCaptureButton) "켜짐" else "꺼짐",
                onClick = { activity.toggleFloatingCaptureButton() }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // 한 페이지 사진 수
            Text(
                "한 페이지 사진 수",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "사진자료 한 페이지에 들어갈 사진 수입니다. 부족한 페이지는 자동으로 빈 슬롯으로 둡니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            PhotosPerPagePicker(activity)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // 사진 자료 순서
            Text(
                "사진 자료 순서",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "사진자료(PPTX/PDF/JPG) 안에서 카테고리가 나오는 순서를 조정합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            CategoryOrderEditor(activity)

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        activity.resetGuideDefaults()
                        alpha = activity.guideAlphaPercent.toFloat()
                        scale = activity.guideScalePercent.toFloat()
                        activity.resetCategoryOrder()
                        activity.applyPhotosPerPage(MainActivity.DEFAULT_PHOTOS_PER_PAGE)
                    },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("기본값")
                }
                Button(
                    onClick = { activity.closeSheet() },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("닫기")
                }
            }
        }
    }
}

@Composable
private fun PhotosPerPagePicker(activity: MainActivity) {
    val options = MainActivity.ALLOWED_PHOTOS_PER_PAGE
    val selected = activity.photosPerPage
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { count ->
            val isSelected = selected == count
            Surface(
                onClick = { activity.applyPhotosPerPage(count) },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                shape = MaterialTheme.shapes.small,
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "${count}장",
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryOrderEditor(activity: MainActivity) {
    val order = activity.categoryOrder
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        order.forEachIndexed { index, category ->
            Card(
                shape = MaterialTheme.shapes.small,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${index + 1}.",
                        modifier = Modifier.width(24.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        activity.categoryLabel(category),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = { activity.moveCategoryUp(category) },
                        enabled = index > 0
                    ) { Text("↑", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                    TextButton(
                        onClick = { activity.moveCategoryDown(category) },
                        enabled = index < order.size - 1
                    ) { Text("↓", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            "›",
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpSheet(activity: MainActivity) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val items = listOf(
        "자체감정 / 현지답사" to "작업 모드를 전환합니다. 주소 미입력 시 자동 제목/파일명에 반영됩니다.",
        "물건지" to "사진자료 상단 주소. 입력한 주소별로 앱 내부 사진 폴더가 나뉩니다.",
        "채무자 / 답사자" to "현지답사 모드에서만 표시. 사진 설명에 함께 저장됩니다.",
        "저장" to "PPTX / PDF / JPG 중 선택해 외부 저장소에 저장.",
        "공유" to "Gmail 또는 다른 앱으로 사진자료 공유.",
        "목록" to "등록된 사진을 분류 순서로 확인 / 개별 삭제.",
        "전체삭제" to "사진 + 앱 내부 파일 모두 제거. 되돌릴 수 없습니다.",
        "설정" to "메일주소 / 공유앱 / 가이드 투명도·크기.",
        "토지 / 건물 / 제시외 / 기타" to "토지=숫자, 건물=가나다, 제시외=ㄱㄴㄷ, 기타=직접입력.",
        "기호 선택" to "선택 분류의 다음 미사용 기호가 자동 입력됩니다.",
        "사진 설명" to "비워두면 분류+기호 자동 사용.",
        "촬영 / 이미지 선택" to "촬영하거나 갤러리에서 사진 추가."
    )

    ModalBottomSheet(
        onDismissRequest = { activity.closeSheet() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Text(
                "도움말",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "현장에서 자주 쓰는 기능들의 역할입니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEach { (title, desc) ->
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressSheet(activity: MainActivity) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf(activity.propertyAddress) }
    ModalBottomSheet(
        onDismissRequest = { activity.closeSheet() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .imePadding()
        ) {
            Text(
                "물건지 주소",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("주소") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { activity.closeSheet() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("취소")
                }
                Button(
                    onClick = {
                        activity.applyAddress(text)
                        activity.closeSheet()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("저장")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailRecipientSheet(activity: MainActivity) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf(activity.emailRecipient) }
    ModalBottomSheet(
        onDismissRequest = { activity.closeSheet() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .imePadding()
        ) {
            Text(
                "기본 메일주소",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "공유 시 자동으로 채워질 수신 메일주소.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("이메일") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { activity.closeSheet() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("취소")
                }
                Button(
                    onClick = {
                        if (activity.applyEmailRecipient(text)) {
                            activity.closeSheet()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("저장")
                }
            }
        }
    }
}

// =================================================================
// AlertDialogs (작은 선택 다이얼로그)
// =================================================================

@Composable
private fun ModeDialog(activity: MainActivity) {
    val labels = listOf("자체감정" to MainActivity.MODE_SELF_APPRAISAL, "현지답사" to MainActivity.MODE_FIELD_SURVEY)
    AlertDialog(
        onDismissRequest = { activity.closeSheet() },
        title = { Text("작업 모드", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                labels.forEach { (label, value) ->
                    RadioRow(
                        selected = activity.appMode == value,
                        text = label,
                        onClick = {
                            activity.applyMode(value)
                            activity.closeSheet()
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { activity.closeSheet() }) { Text("닫기") }
        }
    )
}

@Composable
private fun MailAppDialog(activity: MainActivity) {
    val labels = listOf("Gmail" to MainActivity.MAIL_APP_GMAIL, "Other" to MainActivity.MAIL_APP_OTHER)
    AlertDialog(
        onDismissRequest = { activity.closeSheet() },
        title = { Text("기본 공유 방식", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                labels.forEach { (label, value) ->
                    RadioRow(
                        selected = activity.mailAppPref == value,
                        text = label,
                        onClick = {
                            activity.applyMailApp(value)
                            activity.closeSheet()
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { activity.closeSheet() }) { Text("닫기") }
        }
    )
}

@Composable
private fun RadioRow(selected: Boolean, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ExportFormatDialog(activity: MainActivity) {
    FormatChoiceDialog(
        title = "저장 형식 선택",
        onDismiss = { activity.closeSheet() },
        onChoose = { format ->
            activity.closeSheet()
            activity.startExport(format)
        }
    )
}

@Composable
private fun ShareFormatDialog(activity: MainActivity, recipient: String) {
    FormatChoiceDialog(
        title = "공유 형식 선택",
        onDismiss = { activity.closeSheet() },
        onChoose = { format ->
            activity.closeSheet()
            activity.startShare(recipient, format)
        }
    )
}

@Composable
private fun FormatChoiceDialog(
    title: String,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit
) {
    val formats = listOf(
        "PPTX" to MainActivity.FORMAT_PPTX,
        "PDF" to MainActivity.FORMAT_PDF,
        "JPG" to MainActivity.FORMAT_JPG
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                formats.forEach { (label, value) ->
                    Button(
                        onClick = { onChoose(value) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(label, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

@Composable
private fun ConfirmClearDialog(activity: MainActivity) {
    AlertDialog(
        onDismissRequest = { activity.closeSheet() },
        title = { Text("전체 삭제", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "등록된 사진 ${activity.photos.size}장과 앱 내부 파일을 모두 삭제합니다. 되돌릴 수 없습니다.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    activity.applyClearAll()
                    activity.closeSheet()
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) { Text("삭제", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = { activity.closeSheet() }) { Text("취소") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportProgressSheet(format: String, current: Int, total: Int) {
    // 사용자가 작업 중에 swipe 로 닫지 못하게 막는 modal sheet.
    // (취소 버튼은 D5 에서 추가 — Future cancel 이 필요)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val label = when (format) {
        MainActivity.FORMAT_PDF -> "PDF"
        MainActivity.FORMAT_JPG -> "JPG"
        else -> "PPTX"
    }
    ModalBottomSheet(
        onDismissRequest = { /* 취소 불가 */ },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 12.dp)
        ) {
            Text(
                "$label 사진자료 만드는 중",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (total > 0) "$current / $total 페이지" else "준비 중…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { (current.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(activity: MainActivity, item: MainActivity.PhotoItem) {
    AlertDialog(
        onDismissRequest = { activity.closeSheet() },
        title = { Text(activity.photoTitle(item), fontWeight = FontWeight.Bold) },
        text = { Text("이 사진을 삭제할까요?") },
        confirmButton = {
            TextButton(
                onClick = {
                    activity.deletePhoto(item)
                    activity.openSheet = AppSheet.PhotoList // 목록으로 돌아가기
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) { Text("삭제", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(
                onClick = { activity.openSheet = AppSheet.PhotoList }
            ) { Text("취소") }
        }
    )
}

// =================================================================
// Sessions (저장된 작업)
// =================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionsSheet(activity: MainActivity) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val list = activity.sessions
    ModalBottomSheet(
        onDismissRequest = { activity.closeSheet() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Text(
                "저장된 작업",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = list.size.toString(),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "건 저장됨",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Spacer(Modifier.height(12.dp))

            // 현재 상태 저장 버튼
            Button(
                onClick = { activity.showSaveSessionPrompt() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("현재 상태 새로 저장 (사진 ${activity.photos.size}장)", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))

            if (list.isEmpty()) {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "아직 저장된 작업이 없어요",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "위 버튼을 눌러 현재 사진과 설정을 작업으로 저장하면 나중에 다시 불러와서 이어서 편집할 수 있습니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 540.dp)
                ) {
                    items(list, key = { it.id }) { session ->
                        Box(modifier = Modifier.animateItem()) {
                            SessionCard(activity, session)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(activity: MainActivity, session: MainActivity.SavedSession) {
    val df = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA) }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                session.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${df.format(Date(session.savedAt))} · 사진 ${session.photos.size}장 · ${if (session.appMode == MainActivity.MODE_FIELD_SURVEY) "현지답사" else "자체감정"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (session.propertyAddress.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    session.propertyAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = { activity.confirmLoadSession(session) },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("불러오기", fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = { activity.overwriteSession(session) },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("덮어쓰기", fontSize = 13.sp)
                }
                TextButton(
                    onClick = { activity.confirmDeleteSession(session) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("삭제", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveSessionPromptSheet(activity: MainActivity) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val defaultName = activity.propertyAddress.ifBlank { activity.modeDefaultTitle() }
    var text by remember { mutableStateOf(defaultName) }
    ModalBottomSheet(
        onDismissRequest = { activity.openSheet = AppSheet.Sessions },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .imePadding()
        ) {
            Text(
                "작업 이름",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "나중에 작업 목록에서 이 이름으로 보입니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("이름") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { activity.openSheet = AppSheet.Sessions },
                    modifier = Modifier.weight(1f)
                ) { Text("취소") }
                Button(
                    onClick = {
                        activity.saveCurrentAsSession(text)
                        activity.openSheet = AppSheet.Sessions
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("저장") }
            }
        }
    }
}

@Composable
private fun ConfirmLoadSessionDialog(activity: MainActivity, session: MainActivity.SavedSession) {
    AlertDialog(
        onDismissRequest = { activity.openSheet = AppSheet.Sessions },
        title = { Text("불러오기", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "현재 등록된 사진 ${activity.photos.size}장이 \"${session.name}\" (사진 ${session.photos.size}장)으로 교체됩니다. 계속할까요?",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    activity.loadSession(session)
                    activity.closeSheet()
                }
            ) { Text("불러오기", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(
                onClick = { activity.openSheet = AppSheet.Sessions }
            ) { Text("취소") }
        }
    )
}

@Composable
private fun ConfirmDeleteSessionDialog(activity: MainActivity, session: MainActivity.SavedSession) {
    AlertDialog(
        onDismissRequest = { activity.openSheet = AppSheet.Sessions },
        title = { Text("작업 삭제", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "\"${session.name}\" 작업을 목록에서 삭제합니다. 사진 파일 자체는 그대로 두고 작업 메타만 지웁니다.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    activity.deleteSession(session)
                    activity.openSheet = AppSheet.Sessions
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) { Text("삭제", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(
                onClick = { activity.openSheet = AppSheet.Sessions }
            ) { Text("취소") }
        }
    )
}
