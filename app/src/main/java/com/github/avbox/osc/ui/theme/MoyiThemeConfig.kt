package com.github.avbox.osc.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import com.github.avbox.osc.R

val LocalMoyiThemeConfig = compositionLocalOf<MoyiThemeConfig?> { null }

enum class MoyiThemeMode(val storageValue: String, val nameRes: Int) {
    PURPLE_NEON("purpleNeon", R.string.theme_preset_purple_neon),
    LIGHT_CLEAN("lightClean", R.string.theme_preset_light_clean),
    GOLD_VIP("goldVip", R.string.theme_preset_gold_vip),
    CYBER_BLUE("cyberBlue", R.string.theme_preset_cyber_blue),
    KID_FAMILY("kidFamily", R.string.theme_preset_kid_family),
    MINIMAL_CARD("minimalCard", R.string.theme_preset_minimal_card),
    RETRO_FILM("retroFilm", R.string.theme_preset_retro_film),
    TV_BIG_SCREEN("tvBigScreen", R.string.theme_preset_tv_big_screen),
    LEFT_CATEGORIES("leftCategories", R.string.theme_preset_left_categories),
    CYAN_CYBER_NEON("cyanCyberNeon", R.string.theme_preset_cyan_cyber_neon),
    RED_BLACK_NEON("redBlackNeon", R.string.theme_preset_red_black_neon),
    PINK_PURPLE_NEON("pinkPurpleNeon", R.string.theme_preset_pink_purple_neon),
    WIN7("win7", R.string.theme_preset_win7),
    XP_WINDOWS("xpWindows", R.string.theme_preset_xp_windows);

    companion object {
        fun fromStorageValue(value: String?): MoyiThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: XP_WINDOWS
    }
}

enum class MoyiCategoryNavStyle(val labelRes: Int) {
    DARK_SIDEBAR(R.string.theme_sidebar_dark),
    COLOR_BLOCK_SIDEBAR(R.string.theme_sidebar_color_block),
    MINI_ICON_SIDEBAR(R.string.theme_sidebar_mini_icon),
    FOLD_FILTER_SIDEBAR(R.string.theme_sidebar_fold_filter),
    LIGHT_SIDEBAR(R.string.theme_sidebar_light),
    GOLD_SIDEBAR(R.string.theme_sidebar_gold),
    CYBER_SIDEBAR(R.string.theme_sidebar_cyber),
    TV_WIDE_SIDEBAR(R.string.theme_sidebar_tv_wide),
    LEFT_CATEGORY_SIDEBAR(R.string.theme_sidebar_left_category),
}

enum class MoyiNavStyle(val storageValue: String) {
    DRAWER("drawer"),
    BOTTOM_TABS("bottomTabs");

    companion object {
        fun fromStorageValue(value: String?): MoyiNavStyle =
            entries.firstOrNull { it.storageValue == value } ?: DRAWER
    }
}

enum class MoyiContentTier(val storageValue: String, val labelRes: Int) {
    ALL("all", R.string.theme_tier_all),
    MANHUA("manhua", R.string.theme_tier_manhua),
    LIVE_ACTION("liveAction", R.string.theme_tier_live_action),
    ADULT("adult", R.string.theme_tier_adult);

    companion object {
        fun fromStorageValue(value: String?): MoyiContentTier =
            entries.firstOrNull { it.storageValue == value } ?: ALL
    }
}

data class MoyiDisplayScheme(
    val navStyle: MoyiNavStyle = MoyiNavStyle.BOTTOM_TABS,
    val contentTier: MoyiContentTier = MoyiContentTier.ALL,
) {
    val storageValue: String get() = "${navStyle.storageValue}:${contentTier.storageValue}"

    companion object {
        fun fromStorageValue(value: String?): MoyiDisplayScheme {
            if (value.isNullOrEmpty()) return MoyiDisplayScheme()
            val parts = value.split(":")
            return MoyiDisplayScheme(
                navStyle = MoyiNavStyle.DRAWER,
                contentTier = if (parts.size > 1) {
                    MoyiContentTier.fromStorageValue(parts[1])
                } else {
                    MoyiContentTier.ALL
                },
            )
        }
    }
}

enum class MoyiSiteListStyle {
    NORMAL,
    CUBE,
}

fun moyiSiteListStyleForTheme(mode: MoyiThemeMode): MoyiSiteListStyle = when (mode) {
    MoyiThemeMode.PURPLE_NEON -> MoyiSiteListStyle.CUBE
    MoyiThemeMode.LIGHT_CLEAN -> MoyiSiteListStyle.NORMAL
    MoyiThemeMode.GOLD_VIP -> MoyiSiteListStyle.NORMAL
    MoyiThemeMode.CYBER_BLUE -> MoyiSiteListStyle.NORMAL
    MoyiThemeMode.KID_FAMILY -> MoyiSiteListStyle.NORMAL
    MoyiThemeMode.MINIMAL_CARD -> MoyiSiteListStyle.NORMAL
    MoyiThemeMode.RETRO_FILM -> MoyiSiteListStyle.NORMAL
    MoyiThemeMode.TV_BIG_SCREEN -> MoyiSiteListStyle.NORMAL
    MoyiThemeMode.LEFT_CATEGORIES -> MoyiSiteListStyle.NORMAL
    MoyiThemeMode.CYAN_CYBER_NEON -> MoyiSiteListStyle.NORMAL
    MoyiThemeMode.RED_BLACK_NEON -> MoyiSiteListStyle.NORMAL
    MoyiThemeMode.PINK_PURPLE_NEON -> MoyiSiteListStyle.NORMAL
    MoyiThemeMode.WIN7 -> MoyiSiteListStyle.NORMAL
    MoyiThemeMode.XP_WINDOWS -> MoyiSiteListStyle.NORMAL
}

fun moyiNavStyleForTheme(mode: MoyiThemeMode): MoyiNavStyle = when (mode) {
    MoyiThemeMode.PURPLE_NEON -> MoyiNavStyle.BOTTOM_TABS
    MoyiThemeMode.LIGHT_CLEAN -> MoyiNavStyle.BOTTOM_TABS
    MoyiThemeMode.GOLD_VIP -> MoyiNavStyle.BOTTOM_TABS
    MoyiThemeMode.CYBER_BLUE -> MoyiNavStyle.BOTTOM_TABS
    MoyiThemeMode.KID_FAMILY -> MoyiNavStyle.BOTTOM_TABS
    MoyiThemeMode.MINIMAL_CARD -> MoyiNavStyle.BOTTOM_TABS
    MoyiThemeMode.RETRO_FILM -> MoyiNavStyle.BOTTOM_TABS
    MoyiThemeMode.TV_BIG_SCREEN -> MoyiNavStyle.BOTTOM_TABS
    MoyiThemeMode.LEFT_CATEGORIES -> MoyiNavStyle.BOTTOM_TABS
    MoyiThemeMode.CYAN_CYBER_NEON -> MoyiNavStyle.BOTTOM_TABS
    MoyiThemeMode.RED_BLACK_NEON -> MoyiNavStyle.BOTTOM_TABS
    MoyiThemeMode.PINK_PURPLE_NEON -> MoyiNavStyle.BOTTOM_TABS
    MoyiThemeMode.WIN7 -> MoyiNavStyle.BOTTOM_TABS
    MoyiThemeMode.XP_WINDOWS -> MoyiNavStyle.BOTTOM_TABS
}

enum class MoyiGradientDirection {
    HORIZONTAL,
    DIAGONAL,
    VERTICAL,
}

data class MoyiThemeConfig(
    val mode: MoyiThemeMode,
    val primaryColor: Color,
    val secondaryColor: Color,
    val backgroundColor: Color,
    val surfaceColor: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val dividerColor: Color,
    val selectedBg: Color,
    val selectedText: Color,
    val unselectedBg: Color,
    val unselectedText: Color,
    val backgroundGradient: List<Color> = emptyList(),
    val backgroundGradientDirection: MoyiGradientDirection = MoyiGradientDirection.HORIZONTAL,
    val selectedGradient: List<Color> = emptyList(),
    val navStyle: MoyiCategoryNavStyle,
    val accentColor: Color,
    val cardBorderColor: Color,
    val glowColor: Color,
    val glowBlur: Float,
    val titleShadows: List<Shadow> = emptyList(),
    val subtitleShadows: List<Shadow> = emptyList(),
) {
    val isDark: Boolean get() = when (mode) {
        MoyiThemeMode.LIGHT_CLEAN,
        MoyiThemeMode.KID_FAMILY,
        MoyiThemeMode.MINIMAL_CARD,
        MoyiThemeMode.XP_WINDOWS -> false
        else -> true
    }

    fun backgroundBrush(): Brush? {
        if (backgroundGradient.isEmpty()) return null
        return when (backgroundGradientDirection) {
            MoyiGradientDirection.HORIZONTAL -> Brush.horizontalGradient(backgroundGradient)
            MoyiGradientDirection.VERTICAL -> Brush.verticalGradient(backgroundGradient)
            MoyiGradientDirection.DIAGONAL -> Brush.linearGradient(backgroundGradient)
        }
    }

    fun selectedBrush(): Brush? {
        if (selectedGradient.isEmpty()) return null
        return Brush.horizontalGradient(selectedGradient)
    }

    fun toColorScheme(): ColorScheme {
        val dark = isDark
        val base = if (dark) darkColorScheme() else lightColorScheme()
        val edge = if (dark) Color.White else Color.Black
        return base.copy(
            primary = primaryColor,
            onPrimary = selectedText,
            primaryContainer = lerp(primaryColor, backgroundColor, if (dark) 0.72f else 0.82f),
            onPrimaryContainer = lerp(primaryColor, edge, if (dark) 0.82f else 0.35f),
            secondary = secondaryColor,
            onSecondary = selectedText,
            secondaryContainer = lerp(secondaryColor, backgroundColor, if (dark) 0.72f else 0.82f),
            onSecondaryContainer = lerp(secondaryColor, edge, if (dark) 0.82f else 0.35f),
            tertiary = secondaryColor,
            onTertiary = selectedText,
            background = backgroundColor,
            onBackground = textPrimary,
            surface = surfaceColor,
            onSurface = textPrimary,
            onSurfaceVariant = textSecondary,
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = backgroundColor,
            surfaceContainerLow = lerp(backgroundColor, surfaceColor, 0.5f),
            surfaceContainer = surfaceColor,
            surfaceContainerHigh = lerp(surfaceColor, textPrimary, 0.06f),
            surfaceContainerHighest = lerp(surfaceColor, textPrimary, 0.12f),
            outline = dividerColor,
            outlineVariant = dividerColor,
        )
    }

    companion object {
        fun forTheme(mode: MoyiThemeMode): MoyiThemeConfig = when (mode) {
            MoyiThemeMode.PURPLE_NEON -> MoyiThemeConfig(
                mode = mode,
                primaryColor = Color(0xFFE040FB),
                secondaryColor = Color(0xFF00BCD4),
                backgroundColor = Color(0xFF14102A),
                surfaceColor = Color(0xFF1F1A3C),
                textPrimary = Color.White,
                textSecondary = Color(0xFFB3E5FC),
                dividerColor = Color(0x1FFFFFFF),
                selectedBg = Color(0xFFE040FB),
                selectedText = Color.White,
                unselectedBg = Color(0x1FFFFFFF),
                unselectedText = Color(0xB3FFFFFF),
                backgroundGradient = listOf(Color(0xFF6A0572), Color(0xFF1A2980), Color(0xFF000000)),
                backgroundGradientDirection = MoyiGradientDirection.DIAGONAL,
                selectedGradient = listOf(Color(0xFFE040FB), Color(0xFF00BCD4)),
                navStyle = MoyiCategoryNavStyle.DARK_SIDEBAR,
                accentColor = Color(0xFFFF2EC6),
                cardBorderColor = Color(0xFF00E5FF),
                glowColor = Color(0xFF00BCD4),
                glowBlur = 18f,
                titleShadows = listOf(
                    Shadow(color = Color(0xFFE040FB), blurRadius = 12f),
                    Shadow(color = Color(0xFF00BCD4), blurRadius = 12f),
                ),
                subtitleShadows = listOf(
                    Shadow(color = Color(0xFF00BCD4), blurRadius = 8f),
                ),
            )
            MoyiThemeMode.LIGHT_CLEAN -> MoyiThemeConfig(
                mode = mode,
                primaryColor = Color(0xFF2196F3),
                secondaryColor = Color(0xFF64B5F6),
                backgroundColor = Color(0xFFF7F9FC),
                surfaceColor = Color.White,
                textPrimary = Color(0xFF1A1A1A),
                textSecondary = Color(0xFF757575),
                dividerColor = Color(0xFFEEEEEE),
                selectedBg = Color(0xFF2196F3),
                selectedText = Color.White,
                unselectedBg = Color(0xFFF1F5F9),
                unselectedText = Color(0xFF616161),
                navStyle = MoyiCategoryNavStyle.LIGHT_SIDEBAR,
                accentColor = Color(0xFF2196F3),
                cardBorderColor = Color(0xFFEEEEEE),
                glowColor = Color(0xFF2196F3),
                glowBlur = 0f,
            )
            MoyiThemeMode.GOLD_VIP -> MoyiThemeConfig(
                mode = mode,
                primaryColor = Color(0xFFFFC107),
                secondaryColor = Color(0xFF8D6E00),
                backgroundColor = Color(0xFF121212),
                surfaceColor = Color(0xFF1E1E1E),
                textPrimary = Color(0xFFFFF8E1),
                textSecondary = Color(0xFFBFA570),
                dividerColor = Color(0x1FFFFFFF),
                selectedBg = Color(0xFFFFC107),
                selectedText = Color.Black,
                unselectedBg = Color(0xFF2C2C2C),
                unselectedText = Color(0xFFBFA570),
                backgroundGradient = listOf(Color(0xFF121212), Color(0xFF2C2416)),
                selectedGradient = listOf(Color(0xFFFFC107), Color(0xFF8D6E00)),
                navStyle = MoyiCategoryNavStyle.GOLD_SIDEBAR,
                accentColor = Color(0xFFFFC107),
                cardBorderColor = Color(0x1FFFFFFF),
                glowColor = Color(0xFFFFC107),
                glowBlur = 0f,
            )
            MoyiThemeMode.CYBER_BLUE -> MoyiThemeConfig(
                mode = mode,
                primaryColor = Color(0xFF00FFD5),
                secondaryColor = Color(0xFF00B4D8),
                backgroundColor = Color(0xFF051428),
                surfaceColor = Color(0xFF0B2545),
                textPrimary = Color.White,
                textSecondary = Color(0xB3FFFFFF),
                dividerColor = Color(0x3300FFD5),
                selectedBg = Color(0xFF00FFD5),
                selectedText = Color.Black,
                unselectedBg = Color(0xFF0B2545),
                unselectedText = Color(0xB3FFFFFF),
                backgroundGradient = listOf(Color(0xFF051428), Color(0xFF003566)),
                selectedGradient = listOf(Color(0xFF00FFD5), Color(0xFF00B4D8)),
                navStyle = MoyiCategoryNavStyle.CYBER_SIDEBAR,
                accentColor = Color(0xFF00FFD5),
                cardBorderColor = Color(0x3300FFD5),
                glowColor = Color(0xFF00FFD5),
                glowBlur = 0f,
            )
            MoyiThemeMode.KID_FAMILY -> MoyiThemeConfig(
                mode = mode,
                primaryColor = Color(0xFFFF7096),
                secondaryColor = Color(0xFFFFD166),
                backgroundColor = Color(0xFFFFF4F8),
                surfaceColor = Color.White,
                textPrimary = Color(0xFF333333),
                textSecondary = Color(0xFF777777),
                dividerColor = Color(0xFFFFE4F0),
                selectedBg = Color(0xFFFF7096),
                selectedText = Color.White,
                unselectedBg = Color(0xFFFFE4F0),
                unselectedText = Color(0xFF777777),
                backgroundGradient = listOf(Color(0xFFFFF4F8), Color(0xFFE4F7FF)),
                selectedGradient = listOf(Color(0xFFFF7096), Color(0xFFFFD166)),
                navStyle = MoyiCategoryNavStyle.COLOR_BLOCK_SIDEBAR,
                accentColor = Color(0xFFFF7096),
                cardBorderColor = Color(0xFFFFE4F0),
                glowColor = Color(0xFFFF7096),
                glowBlur = 0f,
            )
            MoyiThemeMode.MINIMAL_CARD -> MoyiThemeConfig(
                mode = mode,
                primaryColor = Color.Black,
                secondaryColor = Color(0xFFB00020),
                backgroundColor = Color(0xFFF5F5F5),
                surfaceColor = Color.White,
                textPrimary = Color(0xDD000000),
                textSecondary = Color(0x8A000000),
                dividerColor = Color(0xFFEEEEEE),
                selectedBg = Color.Black,
                selectedText = Color.White,
                unselectedBg = Color(0xFFFAFAFA),
                unselectedText = Color(0x8A000000),
                navStyle = MoyiCategoryNavStyle.MINI_ICON_SIDEBAR,
                accentColor = Color.Black,
                cardBorderColor = Color(0xFFEEEEEE),
                glowColor = Color.Black,
                glowBlur = 0f,
            )
            MoyiThemeMode.RETRO_FILM -> MoyiThemeConfig(
                mode = mode,
                primaryColor = Color(0xFFC8894B),
                secondaryColor = Color(0xFF6F4E37),
                backgroundColor = Color(0xFF2B1F14),
                surfaceColor = Color(0xFF3A2A1C),
                textPrimary = Color(0xFFF5E6D3),
                textSecondary = Color(0xFFC9B896),
                dividerColor = Color(0xFF6F4E37),
                selectedBg = Color(0xFFC8894B),
                selectedText = Color.Black,
                unselectedBg = Color(0xFF3A2A1C),
                unselectedText = Color(0xFFC9B896),
                backgroundGradient = listOf(Color(0xFF2B1F14), Color(0xFF4A3520)),
                selectedGradient = listOf(Color(0xFFC8894B), Color(0xFF6F4E37)),
                navStyle = MoyiCategoryNavStyle.FOLD_FILTER_SIDEBAR,
                accentColor = Color(0xFFC8894B),
                cardBorderColor = Color(0xFF6F4E37),
                glowColor = Color(0xFFC8894B),
                glowBlur = 0f,
            )
            MoyiThemeMode.TV_BIG_SCREEN -> MoyiThemeConfig(
                mode = mode,
                primaryColor = Color(0xFF40C9FF),
                secondaryColor = Color(0xFF7B66FF),
                backgroundColor = Color(0xFF0A0E27),
                surfaceColor = Color(0xFF141A3A),
                textPrimary = Color.White,
                textSecondary = Color(0xB3FFFFFF),
                dividerColor = Color(0x1FFFFFFF),
                selectedBg = Color(0xFF40C9FF),
                selectedText = Color.Black,
                unselectedBg = Color(0xFF141A3A),
                unselectedText = Color(0xB3FFFFFF),
                backgroundGradient = listOf(Color(0xFF0A0E27), Color(0xFF141A3A)),
                selectedGradient = listOf(Color(0xFF40C9FF), Color(0xFF7B66FF)),
                navStyle = MoyiCategoryNavStyle.TV_WIDE_SIDEBAR,
                accentColor = Color(0xFF40C9FF),
                cardBorderColor = Color(0x1FFFFFFF),
                glowColor = Color(0xFF40C9FF),
                glowBlur = 0f,
            )
            MoyiThemeMode.LEFT_CATEGORIES -> MoyiThemeConfig(
                mode = mode,
                primaryColor = Color(0xFF22D3EE),
                secondaryColor = Color(0xFFE847A2),
                backgroundColor = Color(0xFF120E2B),
                surfaceColor = Color(0xFF1E1838),
                textPrimary = Color.White,
                textSecondary = Color(0xB3FFFFFF),
                dividerColor = Color(0x1FFFFFFF),
                selectedBg = Color(0xFF22D3EE),
                selectedText = Color.Black,
                unselectedBg = Color(0x1FFFFFFF),
                unselectedText = Color(0xB3FFFFFF),
                backgroundGradient = listOf(Color(0xFF2A1B5E), Color(0xFF0E3A5C)),
                backgroundGradientDirection = MoyiGradientDirection.DIAGONAL,
                selectedGradient = listOf(Color(0xFF22D3EE), Color(0xFFE847A2)),
                navStyle = MoyiCategoryNavStyle.LEFT_CATEGORY_SIDEBAR,
                accentColor = Color(0xFF22D3EE),
                cardBorderColor = Color(0x1FFFFFFF),
                glowColor = Color(0xFF22D3EE),
                glowBlur = 0f,
            )
            MoyiThemeMode.CYAN_CYBER_NEON -> MoyiThemeConfig(
                mode = mode,
                primaryColor = Color(0xFF1DE9B6),
                secondaryColor = Color(0xFF00E676),
                backgroundColor = Color(0xFF001A1A),
                surfaceColor = Color(0xFF0B2B25),
                textPrimary = Color.White,
                textSecondary = Color(0xFFB9F6CA),
                dividerColor = Color(0x1FFFFFFF),
                selectedBg = Color(0xFF1DE9B6),
                selectedText = Color.Black,
                unselectedBg = Color(0x1FFFFFFF),
                unselectedText = Color(0xFFB9F6CA),
                backgroundGradient = listOf(Color(0xFF004D40), Color(0xFF001A1A), Color(0xFF000000)),
                backgroundGradientDirection = MoyiGradientDirection.DIAGONAL,
                selectedGradient = listOf(Color(0xFF1DE9B6), Color(0xFF00E676)),
                navStyle = MoyiCategoryNavStyle.CYBER_SIDEBAR,
                accentColor = Color(0xFF76FF03),
                cardBorderColor = Color(0xFF1DE9B6),
                glowColor = Color(0xFF1DE9B6),
                glowBlur = 22f,
                titleShadows = listOf(
                    Shadow(color = Color(0xFF1DE9B6), blurRadius = 14f),
                    Shadow(color = Color(0xFF00E676), blurRadius = 10f),
                ),
                subtitleShadows = listOf(
                    Shadow(color = Color(0xFF1DE9B6), blurRadius = 8f),
                ),
            )
            MoyiThemeMode.RED_BLACK_NEON -> MoyiThemeConfig(
                mode = mode,
                primaryColor = Color(0xFFFF1744),
                secondaryColor = Color(0xFFFF5252),
                backgroundColor = Color(0xFF1A0000),
                surfaceColor = Color(0xFF2B0B0B),
                textPrimary = Color.White,
                textSecondary = Color(0xFFFFCDD2),
                dividerColor = Color(0x1FFFFFFF),
                selectedBg = Color(0xFFFF1744),
                selectedText = Color.White,
                unselectedBg = Color(0x1FFFFFFF),
                unselectedText = Color(0xFFFFCDD2),
                backgroundGradient = listOf(Color(0xFF4A0000), Color(0xFF1A0000), Color(0xFF000000)),
                backgroundGradientDirection = MoyiGradientDirection.DIAGONAL,
                selectedGradient = listOf(Color(0xFFFF1744), Color(0xFFFF5252)),
                navStyle = MoyiCategoryNavStyle.DARK_SIDEBAR,
                accentColor = Color(0xFFFF8A80),
                cardBorderColor = Color(0xFFFF1744),
                glowColor = Color(0xFFFF1744),
                glowBlur = 20f,
                titleShadows = listOf(
                    Shadow(color = Color(0xFFFF1744), blurRadius = 14f),
                    Shadow(color = Color(0xFFFF5252), blurRadius = 10f),
                ),
                subtitleShadows = listOf(
                    Shadow(color = Color(0xFFFF1744), blurRadius = 8f),
                ),
            )
            MoyiThemeMode.PINK_PURPLE_NEON -> MoyiThemeConfig(
                mode = mode,
                primaryColor = Color(0xFFFF4081),
                secondaryColor = Color(0xFFE040FB),
                backgroundColor = Color(0xFF1A0033),
                surfaceColor = Color(0xFF2A0A4A),
                textPrimary = Color.White,
                textSecondary = Color(0xFFF8BBD0),
                dividerColor = Color(0x1FFFFFFF),
                selectedBg = Color(0xFFFF4081),
                selectedText = Color.White,
                unselectedBg = Color(0x1FFFFFFF),
                unselectedText = Color(0xFFF8BBD0),
                backgroundGradient = listOf(Color(0xFFF06292), Color(0xFF4A148C), Color(0xFF1A0033)),
                backgroundGradientDirection = MoyiGradientDirection.DIAGONAL,
                selectedGradient = listOf(Color(0xFFFF4081), Color(0xFFE040FB)),
                navStyle = MoyiCategoryNavStyle.COLOR_BLOCK_SIDEBAR,
                accentColor = Color(0xFFD500F9),
                cardBorderColor = Color(0xFFFF4081),
                glowColor = Color(0xFFE040FB),
                glowBlur = 16f,
                titleShadows = listOf(
                    Shadow(color = Color(0xFFFF4081), blurRadius = 12f),
                    Shadow(color = Color(0xFFE040FB), blurRadius = 12f),
                ),
                subtitleShadows = listOf(
                    Shadow(color = Color(0xFFFF4081), blurRadius = 8f),
                ),
            )
            MoyiThemeMode.WIN7 -> MoyiThemeConfig(
                mode = mode,
                primaryColor = Color(0xFF0078D4),
                secondaryColor = Color(0xFF1E88E5),
                backgroundColor = Color(0xFF002147),
                surfaceColor = Color(0xFF202020),
                textPrimary = Color.White,
                textSecondary = Color(0xB3FFFFFF),
                dividerColor = Color(0x1FFFFFFF),
                selectedBg = Color(0xFF0078D4),
                selectedText = Color.White,
                unselectedBg = Color(0x1FFFFFFF),
                unselectedText = Color(0xB3FFFFFF),
                backgroundGradient = listOf(Color(0xFF0F4C81), Color(0xFF1E88E5), Color(0xFF002147)),
                backgroundGradientDirection = MoyiGradientDirection.VERTICAL,
                selectedGradient = listOf(Color(0xFF0078D4), Color(0xFF1E88E5)),
                navStyle = MoyiCategoryNavStyle.DARK_SIDEBAR,
                accentColor = Color(0xFF0078D4),
                cardBorderColor = Color(0x1FFFFFFF),
                glowColor = Color(0xFF0078D4),
                glowBlur = 0f,
            )
            MoyiThemeMode.XP_WINDOWS -> MoyiThemeConfig(
                mode = mode,
                primaryColor = Color(0xFF245EDC),
                secondaryColor = Color(0xFF3F8CF3),
                backgroundColor = Color(0xFFECE9D8),
                surfaceColor = Color.White,
                textPrimary = Color(0xFF1A1A1A),
                textSecondary = Color(0xFF4A4A4A),
                dividerColor = Color(0xFFD4D0C8),
                selectedBg = Color(0xFF245EDC),
                selectedText = Color.White,
                unselectedBg = Color(0xFFF1F1EA),
                unselectedText = Color(0xFF4A4A4A),
                backgroundGradient = listOf(Color(0xFF5A7EDC), Color(0xFF245EDC)),
                backgroundGradientDirection = MoyiGradientDirection.VERTICAL,
                selectedGradient = listOf(Color(0xFF3F8CF3), Color(0xFF245EDC)),
                navStyle = MoyiCategoryNavStyle.LIGHT_SIDEBAR,
                accentColor = Color(0xFF3D9E3D),
                cardBorderColor = Color(0xFFD4D0C8),
                glowColor = Color(0xFF245EDC),
                glowBlur = 0f,
            )
        }
    }
}
