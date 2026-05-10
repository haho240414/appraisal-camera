package com.codex.appraisalcamera.ui

import android.content.res.Configuration
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.codex.appraisalcamera.MainActivity

/**
 * Phase D2 — Toss/카카오뱅크 풍 메인 카메라 화면.
 *
 * 구조:
 *  - 전체 배경: 카메라 미리보기 (검은 캔버스 위 PreviewView)
 *  - 상단 바 (반투명 흰색): 모드 칩 + 물건지 + ⋯ 메뉴
 *  - 하단 카드 (Toss 풍 흰색 시트, corner 16dp, 약한 그림자):
 *    - 카테고리 SegmentedButtonRow (또는 현지답사 입력)
 *    - 기호 / 메모 입력
 *    - 큰 원형 촬영 FAB + 보조 "이미지 선택" 버튼
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(activity: MainActivity) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // 사용자가 설정에서 조정한 가이드 투명도/크기 — 카드를 카메라 화면 위에 어떻게 얹을지 결정.
    val cardAlpha = (activity.guideAlphaPercent / 100f).coerceIn(0.35f, 1f)
    val cardScale = (activity.guideScalePercent / 100f).coerceIn(0.6f, 1f)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // 카메라 미리보기 — 1회만 생성, 활성화는 Activity 가 책임.
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    activity.bindCameraPreview(this)
                }
            }
        )

        if (isLandscape) {
            LandscapeOverlay(activity, cardAlpha, cardScale)
        } else {
            PortraitOverlay(activity, cardAlpha, cardScale)
        }

        if (isLandscape && activity.floatingCaptureButton) {
            FloatingCaptureButton(activity)
        }

        // 시트/다이얼로그 디스패처
        AppSheets(activity = activity)
    }
}

/**
 * 세로 모드: 상단 바 + 좌/우 사이드 패널 + 하단 메모/촬영 영역.
 * 가운데 컬럼은 카메라 미리보기가 그대로 보임.
 *  - 좌측 패널: 카테고리 탭(세로) + 기호 선택
 *  - 우측 패널: 사진 수 + 촬영 FAB + 이미지 선택
 *  - 하단 영역: 메모(현지답사 모드 시 채무자/답사자)
 * 사이드 패널에 사용자 설정의 alpha/scale 적용.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortraitOverlay(activity: MainActivity, cardAlpha: Float, cardScale: Float) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        TopBar(activity = activity)

        // 카메라 중심부는 최대한 비워두고, 조작 가이드는 아래 검정 여백에 모은다.
        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
        ) {
            PortraitUnifiedGuide(activity, cardAlpha)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortraitUnifiedGuide(activity: MainActivity, cardAlpha: Float) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = cardAlpha)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (activity.isFieldSurveyMode()) {
                    FieldSurveyInputs(activity)
                } else {
                    HorizontalCategoryChips(activity)
                    SymbolPicker(activity)
                }
                MemoField(activity)
            }

            Column(
                modifier = Modifier.width(96.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val count = activity.photos.size
                Text(
                    if (count == 0) "0장" else "${count}장",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                CaptureFab(activity, size = 58.dp)
                OutlinedButton(
                    onClick = { activity.pickImage() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 5.dp, vertical = 3.dp)
                ) {
                    Text("이미지", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeftSidePanel(activity: MainActivity, cardAlpha: Float) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = cardAlpha)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!activity.isFieldSurveyMode()) {
                VerticalCategoryTabs(activity)
                SymbolPicker(activity)
            } else {
                Text(
                    "현지답사",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Text(
                    "사진 ${activity.photos.size}장",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun VerticalCategoryTabs(activity: MainActivity) {
    val items = listOf(
        MainActivity.CATEGORY_LAND to "토지",
        MainActivity.CATEGORY_BUILDING to "건물",
        MainActivity.CATEGORY_EXTRA to "제시외",
        MainActivity.CATEGORY_CUSTOM to "기타"
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (key, label) ->
            val selected = activity.currentCategory == key
            Surface(
                onClick = {
                    if (activity.currentCategory != key) {
                        activity.currentCategory = key
                        activity.currentSymbol = ""
                        activity.currentBuildingSub = ""
                        activity.saveControlState()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(36.dp),
                shape = MaterialTheme.shapes.small,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactVerticalCategoryTabs(activity: MainActivity) {
    val items = listOf(
        MainActivity.CATEGORY_LAND to "토지",
        MainActivity.CATEGORY_BUILDING to "건물",
        MainActivity.CATEGORY_EXTRA to "제시외",
        MainActivity.CATEGORY_CUSTOM to "기타"
    )
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items.forEach { (key, label) ->
            val selected = activity.currentCategory == key
            Surface(
                onClick = {
                    if (activity.currentCategory != key) {
                        activity.currentCategory = key
                        activity.currentSymbol = ""
                        activity.currentBuildingSub = ""
                        activity.saveControlState()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(30.dp),
                shape = MaterialTheme.shapes.small,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun HorizontalCategoryChips(activity: MainActivity) {
    val items = listOf(
        MainActivity.CATEGORY_LAND to "토지",
        MainActivity.CATEGORY_BUILDING to "건물",
        MainActivity.CATEGORY_EXTRA to "제시외",
        MainActivity.CATEGORY_CUSTOM to "기타"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { (key, label) ->
            val selected = activity.currentCategory == key
            Surface(
                onClick = {
                    if (activity.currentCategory != key) {
                        activity.currentCategory = key
                        activity.currentSymbol = ""
                        activity.currentBuildingSub = ""
                        activity.saveControlState()
                    }
                },
                modifier = Modifier.weight(1f).height(32.dp),
                shape = MaterialTheme.shapes.small,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RightSidePanel(activity: MainActivity, cardAlpha: Float) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = cardAlpha)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 사진 수
            val count = activity.photos.size
            Text(
                if (count == 0) "0장" else "${count}장",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // 큰 원형 촬영 FAB
            CaptureFab(activity, size = 64.dp)

            // 이미지 선택
            OutlinedButton(
                onClick = { activity.pickImage() },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text("이미지", fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun CaptureFab(activity: MainActivity, size: Dp) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(),
        label = "fabScale"
    )
    Surface(
        onClick = { activity.capturePhoto() },
        interactionSource = interactionSource,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 6.dp,
        modifier = Modifier.size(size).scale(scale)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(size * 0.42f)
            ) {}
        }
    }
}

@Composable
private fun BoxScope.FloatingCaptureButton(activity: MainActivity) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 8.dp,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 18.dp)
            .size(72.dp),
        onClick = { activity.capturePhoto() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(30.dp)
            ) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomBar(activity: MainActivity, cardAlpha: Float) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = cardAlpha)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (activity.isFieldSurveyMode()) {
                FieldSurveyInputs(activity)
            }
            MemoField(activity)
        }
    }
}

/**
 * 가로 모드: 상단 바 + 우측 사이드패널.
 * 카메라 화면이 가려지지 않도록 컨트롤 카드를 오른쪽 320dp 폭에 한정.
 * 좌우 패널이 가로모드 검정 여백의 위아래를 채우도록 배치.
 * scale 의 transformOrigin 은 우측 중앙.
 */
@Composable
private fun LandscapeOverlay(activity: MainActivity, cardAlpha: Float, cardScale: Float) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(176.dp)
                .fillMaxHeight()
                .padding(start = 6.dp, top = 4.dp, bottom = 4.dp)
        ) {
            LandscapeActionRail(activity = activity, cardAlpha = cardAlpha)
        }

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .width(188.dp)
                .fillMaxHeight()
                .padding(end = 6.dp, top = 4.dp, bottom = 4.dp)
        ) {
            NarrowControlsCard(activity = activity, cardAlpha = cardAlpha)
        }
    }
}

@Composable
private fun LandscapeActionRail(activity: MainActivity, cardAlpha: Float) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .shadow(2.dp, MaterialTheme.shapes.large),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f.coerceAtMost(cardAlpha + 0.08f)),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FilledTonalButton(
                onClick = { activity.showModeDialog() },
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(activity.modeLabel(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Surface(
                onClick = { activity.showAddressDialog() },
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
                    Text("물건지", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = activity.propertyAddress.ifBlank { "주소 입력" },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2
                    )
                }
            }

            RailActionButton("저장") { activity.showExportFormatDialog() }
            RailActionButton("공유") { activity.showEmailDialog() }
            RailActionButton("목록") { activity.showPhotoListDialog() }
            RailActionButton("설정") { activity.showGuideSettingsDialog() }
            RailActionButton("도움말") { activity.showHelpDialog() }
            RailActionButton("전체삭제", danger = true) { activity.confirmClear() }
        }
    }
}

@Composable
private fun RailActionButton(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().height(28.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 6.dp)
        ) {
            Text(
                text,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/**
 * 상단 바: 모드 칩 + 물건지 라벨 + ⋯ overflow 메뉴.
 * 반투명 흰색 surface, 그림자 없음 (카메라 미리보기 위에서 가벼운 느낌).
 */
@Composable
private fun TopBar(activity: MainActivity) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(2.dp, MaterialTheme.shapes.large),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 모드 칩
            FilledTonalButton(
                onClick = { activity.showModeDialog() },
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text(
                    text = activity.modeLabel(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.width(10.dp))

            // 물건지 (탭하면 다이얼로그)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { activity.showAddressDialog() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    "물건지",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = activity.propertyAddress.ifBlank { "주소 입력" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (activity.propertyAddress.isBlank())
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }

            // 저장 / 공유 / overflow
            CompactActionButton("저장") { activity.showExportFormatDialog() }
            Spacer(Modifier.width(4.dp))
            CompactActionButton("공유") { activity.showEmailDialog() }
            Spacer(Modifier.width(4.dp))
            OverflowMenu(activity = activity)
        }
    }
}

@Composable
private fun CompactActionButton(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.height(32.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OverflowMenu(activity: MainActivity) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { expanded = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "⋯",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(text = { Text("목록") }, onClick = {
                expanded = false
                activity.showPhotoListDialog()
            })
            DropdownMenuItem(text = { Text("설정") }, onClick = {
                expanded = false
                activity.showGuideSettingsDialog()
            })
            DropdownMenuItem(text = { Text("도움말") }, onClick = {
                expanded = false
                activity.showHelpDialog()
            })
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(
                        "전체 삭제",
                        color = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    expanded = false
                    activity.confirmClear()
                }
            )
        }
    }
}

/**
 * 하단 컨트롤 카드.
 * 자체감정: 카테고리 segmented + 기호 + 메모 + 촬영 / 이미지 선택
 * 현지답사: 채무자/답사자 입력 + 메모 + 촬영
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlsCard(activity: MainActivity, cardAlpha: Float = 0.97f) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(4.dp, MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = cardAlpha)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (activity.isFieldSurveyMode()) {
                FieldSurveyInputs(activity)
            } else {
                CategoryTabs(activity)
                SymbolPicker(activity)
            }
            MemoField(activity)
            CaptureRow(activity)
            PhotoCountText(activity)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NarrowControlsCard(activity: MainActivity, cardAlpha: Float = 0.97f) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .shadow(2.dp, MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = cardAlpha)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (activity.isFieldSurveyMode()) {
                FieldSurveyInputs(activity)
            } else {
                CompactVerticalCategoryTabs(activity)
                CompactSymbolPicker(activity)
            }
            CompactMemoField(activity)
            if (!activity.floatingCaptureButton) {
                CaptureFab(activity, size = 52.dp)
            }
            OutlinedButton(
                onClick = { activity.pickImage() },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text("이미지", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            PhotoCountText(activity)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryTabs(activity: MainActivity) {
    val labels = listOf(
        MainActivity.CATEGORY_LAND to "토지",
        MainActivity.CATEGORY_BUILDING to "건물",
        MainActivity.CATEGORY_EXTRA to "제시외",
        MainActivity.CATEGORY_CUSTOM to "기타"
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, (key, label) ->
            SegmentedButton(
                selected = activity.currentCategory == key,
                onClick = {
                    if (activity.currentCategory != key) {
                        activity.currentCategory = key
                        activity.currentSymbol = ""
                        activity.currentBuildingSub = ""
                        activity.saveControlState()
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    inactiveContainerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SymbolPicker(activity: MainActivity) {
    if (activity.currentCategory == MainActivity.CATEGORY_CUSTOM) {
        CustomPresetPicker(activity, compact = false)
        return
    }

    val symbols = activity.symbolsForCategory(activity.currentCategory).toList()
    val displayLabel = if (activity.currentSymbol.isEmpty()) {
        // 자동 다음 기호 미리 보기
        val next = activity.nextSymbol(activity.currentCategory)
        "${activity.categoryLabel(activity.currentCategory)} 기호 ${next.base} (자동)"
    } else {
        "${activity.categoryLabel(activity.currentCategory)} 기호 ${activity.currentSymbol}"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        var symbolMenuExpanded by remember { mutableStateOf(false) }
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { symbolMenuExpanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            ) {
                Text(displayLabel, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            DropdownMenu(
                expanded = symbolMenuExpanded,
                onDismissRequest = { symbolMenuExpanded = false }
            ) {
                symbols.forEach { sym ->
                    DropdownMenuItem(
                        text = { Text("${activity.categoryLabel(activity.currentCategory)} 기호 $sym") },
                        onClick = {
                            activity.currentSymbol = sym
                            activity.saveControlState()
                            symbolMenuExpanded = false
                        }
                    )
                }
            }
        }

        // 건물 카테고리만 sub-spinner
        if (activity.currentCategory == MainActivity.CATEGORY_BUILDING) {
            var subMenuExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { subMenuExpanded = true },
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text(
                        if (activity.currentBuildingSub.isEmpty()) "없음" else activity.currentBuildingSub,
                        fontSize = 14.sp
                    )
                }
                DropdownMenu(
                    expanded = subMenuExpanded,
                    onDismissRequest = { subMenuExpanded = false }
                ) {
                    MainActivity.BUILDING_SUB_SYMBOLS.forEach { sub ->
                        DropdownMenuItem(
                            text = { Text(sub) },
                            onClick = {
                                activity.currentBuildingSub = if (sub == "없음") "" else sub
                                activity.saveControlState()
                                subMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSymbolPicker(activity: MainActivity) {
    if (activity.currentCategory == MainActivity.CATEGORY_CUSTOM) {
        CustomPresetPicker(activity, compact = true)
        return
    }

    val symbols = activity.symbolsForCategory(activity.currentCategory).toList()
    val displayLabel = if (activity.currentSymbol.isEmpty()) {
        val next = activity.nextSymbol(activity.currentCategory)
        "${activity.categoryLabel(activity.currentCategory)} ${next.base}"
    } else {
        "${activity.categoryLabel(activity.currentCategory)} ${activity.currentSymbol}"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        var symbolMenuExpanded by remember { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { symbolMenuExpanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(displayLabel, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            DropdownMenu(
                expanded = symbolMenuExpanded,
                onDismissRequest = { symbolMenuExpanded = false }
            ) {
                symbols.forEach { sym ->
                    DropdownMenuItem(
                        text = { Text("${activity.categoryLabel(activity.currentCategory)} 기호 $sym") },
                        onClick = {
                            activity.currentSymbol = sym
                            activity.saveControlState()
                            symbolMenuExpanded = false
                        }
                    )
                }
            }
        }

        if (activity.currentCategory == MainActivity.CATEGORY_BUILDING) {
            var subMenuExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { subMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        if (activity.currentBuildingSub.isEmpty()) "하위기호 없음" else activity.currentBuildingSub,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
                DropdownMenu(
                    expanded = subMenuExpanded,
                    onDismissRequest = { subMenuExpanded = false }
                ) {
                    MainActivity.BUILDING_SUB_SYMBOLS.forEach { sub ->
                        DropdownMenuItem(
                            text = { Text(sub) },
                            onClick = {
                                activity.currentBuildingSub = if (sub == "없음") "" else sub
                                activity.saveControlState()
                                subMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldSurveyInputs(activity: MainActivity) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = activity.debtorName,
            onValueChange = {
                activity.debtorName = it
                activity.saveFieldSurveyInfo()
            },
            label = { Text("채무자 명") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = activity.fieldSurveyor,
            onValueChange = {
                activity.fieldSurveyor = it
                activity.saveFieldSurveyInfo()
            },
            label = { Text("현지답사자") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MemoField(activity: MainActivity) {
    OutlinedTextField(
        value = activity.currentMemo,
        onValueChange = {
            activity.currentMemo = it
            activity.saveControlState()
        },
        label = { Text("사진 설명 (선택)") },
        placeholder = { Text("전경, 진입로, 외벽, 내부 등") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CompactMemoField(activity: MainActivity) {
    OutlinedTextField(
        value = activity.currentMemo,
        onValueChange = {
            activity.currentMemo = it
            activity.saveControlState()
        },
        label = { Text("설명") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun CaptureRow(activity: MainActivity) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(),
        label = "fabScale"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 큰 원형 촬영 FAB — press 시 살짝 줄어드는 spring 애니메이션.
        Surface(
            onClick = { activity.capturePhoto() },
            interactionSource = interactionSource,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(64.dp)
                .scale(scale)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                ) {}
            }
        }

        OutlinedButton(
            onClick = { activity.pickImage() },
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("이미지 선택", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PhotoCountText(activity: MainActivity) {
    val count = activity.photos.size
    Text(
        text = if (count == 0) "촬영하거나 이미지를 선택해 주세요" else "등록된 사진 ${count}장",
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 기타 카테고리: 인근도로 / 물건전경 / 기계기구 / 개별입력 4 옵션.
 * - 프리셋 버튼 클릭 → currentSymbol = 프리셋 라벨
 * - 개별입력 → 텍스트 필드 노출, 사용자가 입력하는 값이 currentSymbol
 * - currentSymbol 이 프리셋 중 하나면 그 프리셋이 선택된 상태로 표시
 * - 그 외(빈 문자열 포함) → "개별입력" 모드로 간주
 *
 * @param compact true 면 좁은 사이드 패널용으로 폰트/패딩 축소
 */
@Composable
private fun CustomPresetPicker(activity: MainActivity, compact: Boolean) {
    val presets = MainActivity.CUSTOM_PRESETS
    val isFreeform = activity.currentSymbol !in presets
    val labelFontSize = if (compact) 12.sp else 14.sp
    val rowHeight = if (compact) 32.dp else 38.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        presets.forEach { preset ->
            val selected = activity.currentSymbol == preset
            CustomPresetButton(
                label = preset,
                selected = selected,
                fontSize = labelFontSize,
                height = rowHeight
            ) {
                activity.currentSymbol = preset
                activity.saveControlState()
            }
        }
        CustomPresetButton(
            label = "개별입력",
            selected = isFreeform,
            fontSize = labelFontSize,
            height = rowHeight
        ) {
            // 프리셋이 선택돼 있던 상태에서 개별입력으로 전환할 때 텍스트 필드를 비움.
            if (activity.currentSymbol in presets) {
                activity.currentSymbol = ""
                activity.saveControlState()
            }
        }
        if (isFreeform) {
            OutlinedTextField(
                value = activity.currentSymbol,
                onValueChange = {
                    activity.currentSymbol = it.trim()
                    activity.saveControlState()
                },
                label = { Text("기타사항 입력") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CustomPresetButton(
    label: String,
    selected: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    height: Dp,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(height),
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
