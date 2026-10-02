package com.myfamily.meow.ui.theme

import androidx.compose.ui.graphics.Color
import com.myfamily.meow.classification.Category

// Values from the team's Figma file (SKKU 해커톤_고양이). Light, pastel pink tone.
val Background = Color(0xFFFEFAF9)
val HomeBackground = Color(0xFFFEF9F9) // matches the baked-in background of home_cat_wallet.png
val Surface = Color(0xFFFFFFFF)
val SurfaceHigh = Color(0xFFF4EEEE)
val Outline = Color(0xFFEEE4E4)
val Divider = Color(0xFFE6E6E6)

val Pink = Color(0xFFE6738B) // primary actions (소비 스와이프 시작하기)
val PinkLight = Color(0xFFFFDEE8) // tutorial/next buttons
val PinkSoft = Color(0xFFFDECEF) // summary card
val PinkMuted = Color(0xFFD28D96) // "안녕하세요!"
val PinkCount = Color(0xFFE56783)
val PinkIconBg = Color(0xFFFCE1E7)
val DotActive = Color(0xFFFFBCD0)
val DotInactive = Color(0xFFD9D9D9)

val MintSoft = Color(0xFFEEF9F2) // 분석 리포트 button
val MintChip = Color(0xFFC8F0E2) // "AI 정리" chip
val MintText = Color(0xFF197556)
val MintIconBg = Color(0xFFDDF5EB)
val ReportGreen = Color(0xFF2E7D68)
val SpeedBorder = Color(0xFF56CCA1)
val SpeedBg = Color(0xFFEAF7F0)

// Swipe directions (also the tutorial's colored words).
val SwipeInclude = Color(0xFF34A853) // → 반영
val SwipeExclude = Color(0xFFFF383C) // ← 제외
val SwipeSettle = Color(0xFF0088FF) // ↑ 정산

val TextPrimary = Color(0xFF000000)
val TextSecondary = Color(0xFF5E5858)
val TextMuted = Color(0xFF828282)
val ChipGray = Color(0xFF8C8B8B)

val Amber = Color(0xFFC77A12)
val AmberDark = Color(0xFFFFF1DA)
val Sky = Color(0xFF2F7FD8) // income amounts
val SkyDark = Color(0xFFE2EFFC)

// Older names still used by the settings/debug screens.
val Mint = Pink
val MintDark = PinkLight
val OnMint = Color.White
val Coral = SwipeExclude
val CoralDark = Color(0xFFFFE3E4)

/** Category chip / folder pill color, matched to the team's Figma 카테고리 design. */
val Category.color: Color
    get() = when (this) {
        Category.SHOPPING -> Color(0xFFCC9340)
        Category.FOOD -> Color(0xFFC75B74)
        Category.TRANSPORT -> Color(0xFF53A860)
        Category.ETC -> Color(0xFF8E8E8E)
        Category.CAFE -> Color(0xFF8C6E52)
        Category.CULTURE -> Color(0xFF8257C9)
        Category.SUBSCRIPTION -> Color(0xFF4D7FD0)
        Category.MEDICAL -> Color(0xFFCC5A3F)
        Category.LIVING -> Color(0xFF5FA9C4)
        Category.EDUCATION -> Color(0xFF3FA99E)
        Category.TRAVEL -> Color(0xFF5596D9)
    }


