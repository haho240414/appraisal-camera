package com.codex.appraisalcamera.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.codex.appraisalcamera.R

/**
 * Material 3 Typography 토큰 — Toss/카카오뱅크 풍 위계 + Noto Sans KR.
 *
 * Google Fonts Downloadable Fonts 로 Noto Sans KR 을 가져온다.
 * - Google Play Services 가 있는 디바이스에서는 자동 다운로드 + 캐싱.
 * - 없거나 오프라인이면 시스템 sans-serif 로 fallback.
 *
 * Pretendard 는 Google Fonts 등록이 안 되어 있어 번들 필요(D5).
 */
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val notoSansKr = GoogleFont("Noto Sans KR")

private val NotoSansKrFamily = FontFamily(
    Font(googleFont = notoSansKr, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = notoSansKr, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = notoSansKr, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = notoSansKr, fontProvider = provider, weight = FontWeight.Bold)
)

val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = NotoSansKrFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontFamily = NotoSansKrFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontFamily = NotoSansKrFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    bodyLarge = TextStyle(
        fontFamily = NotoSansKrFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = NotoSansKrFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = NotoSansKrFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    )
)
