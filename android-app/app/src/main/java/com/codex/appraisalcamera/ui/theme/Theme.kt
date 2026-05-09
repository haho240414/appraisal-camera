package com.codex.appraisalcamera.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * AppraisalCameraTheme — Toss/카카오뱅크 풍 Material 3 테마.
 *
 * 라이트/다크 모두 지원. 시스템 설정에 따라 자동 전환.
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

// 다크 팔레트 (Toss 풍): 진회색 surface + 같은 Toss Blue primary.
private val DarkSurface = Color(0xFF1F2329)
private val DarkSurfaceVariant = Color(0xFF2D333B)
private val DarkSurfaceContainer = Color(0xFF262A30)
private val DarkSurfaceContainerHigh = Color(0xFF2D333B)
private val DarkSurfaceContainerHighest = Color(0xFF333D4B)
private val DarkOutline = Color(0xFF333D4B)

private val DarkColors = darkColorScheme(
    primary = TossBlue500,
    onPrimary = White,
    primaryContainer = TossBlue700,
    onPrimaryContainer = TossBlue50,

    secondary = TossGreen500,
    onSecondary = White,
    secondaryContainer = TossGreen600,
    onSecondaryContainer = TossGreen100,

    tertiary = Gray400,
    onTertiary = Gray900,

    background = Gray900,
    onBackground = Gray100,

    surface = DarkSurface,
    onSurface = Gray100,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Gray400,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,

    outline = DarkOutline,
    outlineVariant = DarkSurfaceVariant,

    error = TossRed500,
    onError = White,
    errorContainer = TossRed600,
    onErrorContainer = TossRed100
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
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
