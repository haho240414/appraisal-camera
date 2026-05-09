package com.codex.appraisalcamera.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * AppraisalCameraTheme — Toss/카카오뱅크 풍 light Material 3 테마.
 *
 * Phase D1 단계에서는 light scheme 만 정의. Dark scheme 은 D4 polish 단계.
 *
 * Toss 디자인 특징:
 *  - 그림자 약함 (elevation 1~2dp 위주, 큰 카드만 4~6dp)
 *  - 모서리 둥글기 작은 컴포넌트 8dp, 카드/시트 16dp
 *  - 채도 낮은 회색 + 강조용 단일 블루
 */
private val LightColors = lightColorScheme(
    primary = TossBlue500,
    onPrimary = White,
    primaryContainer = TossBlue50,
    onPrimaryContainer = TossBlue700,

    secondary = TossGreen500,
    onSecondary = White,
    secondaryContainer = TossGreen100,
    onSecondaryContainer = TossGreen600,

    tertiary = Gray700,
    onTertiary = White,

    background = White,
    onBackground = Gray900,

    surface = White,
    onSurface = Gray900,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray700,
    surfaceContainer = Gray50,
    surfaceContainerHigh = Gray100,
    surfaceContainerHighest = Gray200,

    outline = Gray200,
    outlineVariant = Gray100,

    error = TossRed500,
    onError = White,
    errorContainer = TossRed100,
    onErrorContainer = TossRed600
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun AppraisalCameraTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
