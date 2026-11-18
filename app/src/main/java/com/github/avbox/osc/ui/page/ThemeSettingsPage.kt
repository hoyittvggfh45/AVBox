@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.github.avbox.osc.ui.page

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.avbox.osc.R
import com.github.avbox.osc.ui.components.AppTopBarScaffold
import com.github.avbox.osc.ui.components.CapsuleSegmentedButton
import com.github.avbox.osc.ui.components.PhoneMockupPreview
import com.github.avbox.osc.ui.components.RowLeadingIcon
import com.github.avbox.osc.ui.components.SegmentOption
import com.github.avbox.osc.ui.components.SettingsCard
import com.github.avbox.osc.ui.components.SettingsCardPosition
import com.github.avbox.osc.ui.components.SettingsGroup
import com.github.avbox.osc.ui.components.SettingsSwitch
import com.github.avbox.osc.ui.components.SettingsSwitchRow
import com.github.avbox.osc.ui.components.ThemeColorPickerSheet
import com.github.avbox.osc.ui.components.TopBarActionBox
import com.github.avbox.osc.ui.theme.AppThemeState
import com.github.avbox.osc.ui.theme.LiquidGlassState
import com.github.avbox.osc.ui.theme.MoyiContentTier
import com.github.avbox.osc.ui.theme.MoyiThemeConfig
import com.github.avbox.osc.ui.theme.MoyiThemeMode
import com.github.avbox.osc.ui.theme.PaletteStyles
import com.github.avbox.osc.ui.theme.PresetSeeds
import com.github.avbox.osc.ui.theme.ThemeMode
import com.github.avbox.osc.ui.theme.ThemeSource
import com.github.avbox.osc.ui.theme.filterChipColors
import com.materialkolor.PaletteStyle
import kotlin.math.roundToInt

private const val DisabledAlpha = 0.45f

private val PresetSeedCardSpacing = 12.dp

private val PresetSeedMinCardWidth = 80.dp

private const val PresetSeedNarrowColumns = 4

private const val PresetSeedWideColumns = 8

@Composable
fun ThemeSettingsScreen(onNavigateBack: () -> Unit) {
    val config = AppThemeState.config
    val isCustom = config.source == ThemeSource.CUSTOM
    val isMoyi = config.source == ThemeSource.MOYI
    var seedPickerOpen by remember { mutableStateOf(false) }
    var schemeTick by remember { mutableStateOf(0) }
    var tierDialogMode by remember { mutableStateOf<MoyiThemeMode?>(null) }
    val glassConfig = LiquidGlassState.config
    var blurValue by remember(glassConfig.blurDp) { mutableStateOf(glassConfig.blurDp) }
    var distortionValue by remember(glassConfig.distortionDp) { mutableStateOf(glassConfig.distortionDp) }
    var translucencyValue by remember(glassConfig.translucency) { mutableStateOf(glassConfig.translucency) }

    val listState = rememberScrollState()

    AppTopBarScaffold(
        titleContent = {
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        navigationIcon = {
            TopBarActionBox(R.drawable.ic_arrow_left, stringResource(R.string.common_back), onClick = onNavigateBack)
        },
    ) { topPad, _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(listState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Spacer(Modifier.height(topPad - 20.dp))

            SettingsCard(SettingsCardPosition.SINGLE) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PhoneMockupPreview()
                }
            }

            SettingsGroup(title = stringResource(R.string.theme_color)) {
                ThemeCard(SettingsCardPosition.FIRST) {
                    CustomThemeSwitchRow(
                        checked = isCustom,
                        onCheckedChange = { checked ->
                            AppThemeState.source =
                                if (checked) ThemeSource.CUSTOM else ThemeSource.SYSTEM
                        },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.theme_pure_black),
                        checked = config.pureBlack,
                        onCheckedChange = { AppThemeState.pureBlack = it },
                        enabled = !isMoyi,
                    )
                }
                ThemeCard(SettingsCardPosition.MIDDLE, enabled = !isMoyi) {
                    ThemeModeRow(
                        currentMode = config.mode,
                        onModeSelected = { AppThemeState.mode = it },
                    )
                }
                ThemeCard(SettingsCardPosition.MIDDLE, enabled = isCustom) {
                    PresetSeedsRow(
                        currentSeed = config.seedArgb,
                        style = config.style,
                        enabled = isCustom,
                        onSeedSelected = { AppThemeState.seed = it },
                    )
                }
                ThemeCard(SettingsCardPosition.MIDDLE, enabled = isCustom) {
                    CustomSeedRow(
                        seedArgb = config.seedArgb,
                        enabled = isCustom,
                        onClick = { seedPickerOpen = true },
                    )
                }
                ThemeCard(SettingsCardPosition.LAST, enabled = isCustom) {
                    VariantSelectorRow(
                        currentStyle = config.style,
                        enabled = isCustom,
                        onStyleSelected = { AppThemeState.style = it },
                    )
                }
            }

            SettingsGroup(title = stringResource(R.string.theme_preset_group)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.theme_preset_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    key(schemeTick) {
                        MoyiPresetGrid(
                            selectedMode = if (isMoyi) {
                                MoyiThemeMode.fromStorageValue(config.preset)
                            } else {
                                null
                            },
                            onSelect = { mode ->
                                AppThemeState.preset = mode.storageValue
                                AppThemeState.source = ThemeSource.MOYI
                            },
                            onTierClick = { mode -> tierDialogMode = mode },
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    OutlinedButton(
                        onClick = {
                            AppThemeState.preset = MoyiThemeMode.XP_WINDOWS.storageValue
                            AppThemeState.source = ThemeSource.MOYI
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.theme_preset_reset))
                    }
                }
            }

            SettingsGroup(title = stringResource(R.string.theme_app_effects)) {
                SettingsCard(SettingsCardPosition.FIRST) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RowLeadingIcon(R.drawable.ic_theme_liquid_glass, enabled = true)
                            Text(
                                text = stringResource(R.string.theme_liquid_glass),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { LiquidGlassState.restoreDefaults() }) {
                                Text(stringResource(R.string.theme_reset))
                            }
                        }
                        Text(
                            text = stringResource(R.string.theme_liquid_glass_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.theme_nav_bar),
                        checked = glassConfig.navbarEnabled,
                        onCheckedChange = { LiquidGlassState.navbarEnabled = it },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.theme_app_controls),
                        checked = glassConfig.controlsEnabled,
                        onCheckedChange = { LiquidGlassState.controlsEnabled = it },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    GlassSliderRow(
                        title = stringResource(R.string.theme_blur),
                        value = blurValue,
                        valueRange = LiquidGlassState.BLUR_RANGE,
                        onValueChange = { blurValue = it },
                        onValueChangeFinished = { LiquidGlassState.blurDp = blurValue },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    GlassSliderRow(
                        title = stringResource(R.string.theme_distortion),
                        value = distortionValue,
                        valueRange = LiquidGlassState.DISTORTION_RANGE,
                        onValueChange = { distortionValue = it },
                        onValueChangeFinished = { LiquidGlassState.distortionDp = distortionValue },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    GlassSliderRow(
                        title = stringResource(R.string.theme_translucency),
                        value = translucencyValue,
                        valueRange = LiquidGlassState.TRANSLUCENCY_RANGE,
                        onValueChange = { translucencyValue = it },
                        onValueChangeFinished = { LiquidGlassState.translucency = translucencyValue },
                        valueText = "${(translucencyValue * 100f).roundToInt()}%",
                    )
                }
                SettingsCard(SettingsCardPosition.LAST) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.theme_dispersion),
                        checked = glassConfig.dispersion,
                        onCheckedChange = { LiquidGlassState.dispersion = it },
                    )
                }
            }

            Spacer(Modifier.height(36.dp))
        }
    }

    if (seedPickerOpen) {
        ThemeColorPickerSheet(
            title = stringResource(R.string.theme_custom_color),
            initialColor = config.seedArgb,
            onConfirm = { argb ->
                AppThemeState.seed = argb
            },
            onDismiss = { seedPickerOpen = false },
        )
    }

    val dialogMode = tierDialogMode
    if (dialogMode != null) {
        MoyiTierDialog(
            mode = dialogMode,
            currentTier = AppThemeState.displayScheme(dialogMode).contentTier,
            onDismiss = { tierDialogMode = null },
            onTierSelected = { tier ->
                AppThemeState.setDisplayScheme(
                    dialogMode,
                    AppThemeState.displayScheme(dialogMode).copy(contentTier = tier),
                )
                schemeTick++
                tierDialogMode = null
            },
        )
    }
}

@Composable
private fun ThemeCard(
    position: SettingsCardPosition,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    SettingsCard(
        position = position,
        modifier = Modifier.then(if (enabled) Modifier else Modifier.alpha(DisabledAlpha)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun CustomThemeSwitchRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLeadingIcon(R.drawable.ic_theme_custom, enabled = true)
        Text(
            text = stringResource(R.string.theme_custom_theme),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        SettingsSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun GlassSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueText: String = value.roundToInt().toString(),
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = 0,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledThumbColor = MaterialTheme.colorScheme.outline,
                disabledActiveTrackColor = MaterialTheme.colorScheme.outline,
                disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

@Composable
private fun ThemeModeRow(currentMode: Int, onModeSelected: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.theme_mode),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        CapsuleSegmentedButton(
            options = listOf(
                SegmentOption(
                    label = stringResource(R.string.theme_mode_auto),
                    value = ThemeMode.FOLLOW_SYSTEM,
                    iconPainter = painterResource(R.drawable.ic_brightness_auto),
                ),
                SegmentOption(
                    label = stringResource(R.string.theme_mode_light),
                    value = ThemeMode.LIGHT,
                    iconPainter = painterResource(R.drawable.ic_light_mode),
                ),
                SegmentOption(
                    label = stringResource(R.string.theme_mode_dark),
                    value = ThemeMode.DARK,
                    iconPainter = painterResource(R.drawable.ic_dark_mode),
                ),
            ),
            selectedValue = currentMode,
            onOptionSelected = onModeSelected,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CustomSeedRow(seedArgb: Int, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.theme_custom_color),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        val seedShape = MaterialTheme.shapes.small
        Surface(
            modifier = Modifier
                .size(40.dp)
                .clip(seedShape)
                .clickable(enabled = enabled, onClick = onClick),
            shape = seedShape,
            color = Color(seedArgb),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {}
    }
}

@Composable
private fun VariantSelectorRow(
    currentStyle: PaletteStyle,
    enabled: Boolean,
    onStyleSelected: (PaletteStyle) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.theme_palette_style),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PaletteStyles.forEach { (style, labelRes) ->
                FilterChip(
                    selected = currentStyle == style,
                    onClick = { onStyleSelected(style) },
                    enabled = enabled,
                    label = { Text(stringResource(labelRes)) },
                    colors = MaterialTheme.colorScheme.filterChipColors(),
                )
            }
        }
    }
}

@Composable
private fun PresetSeedsRow(
    currentSeed: Int,
    style: PaletteStyle,
    enabled: Boolean,
    onSeedSelected: (Int) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val wideColumnsMinWidth = PresetSeedMinCardWidth * PresetSeedWideColumns +
            PresetSeedCardSpacing * (PresetSeedWideColumns - 1)
        val columns =
            if (maxWidth >= wideColumnsMinWidth) PresetSeedWideColumns
            else PresetSeedNarrowColumns
        Column {
            PresetSeeds.chunked(columns).forEachIndexed { rowIndex, rowItems ->
                if (rowIndex > 0) Spacer(Modifier.height(PresetSeedCardSpacing))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PresetSeedCardSpacing),
                ) {
                    rowItems.forEach { (nameRes, argb) ->
                        key(argb, style) {
                            PresetSeedCard(
                                nameRes = nameRes,
                                seedArgb = argb,
                                selected = currentSeed == argb,
                                style = style,
                                enabled = enabled,
                                onClick = { onSeedSelected(argb) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    repeat(columns - rowItems.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetSeedCard(
    nameRes: Int,
    seedArgb: Int,
    selected: Boolean,
    style: PaletteStyle,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewScheme by produceState(
        initialValue = MaterialTheme.colorScheme,
        key1 = seedArgb,
        key2 = style,
    ) {
        value = AppThemeState.previewScheme(seedArgb, style)
    }
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val cardShape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .clip(cardShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = cardShape,
        color = previewScheme.surfaceContainer,
        border = BorderStroke(2.dp, borderColor),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val unit = maxWidth
            val barHeight = unit * 0.14f
            val gap = unit * 0.05f
            val blockCorner = unit * 0.04f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(unit * 0.08f),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth(0.6f)
                        .height(barHeight)
                        .background(previewScheme.primary, RoundedCornerShape(barHeight / 2)),
                )
                if (selected) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(unit * 0.25f),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(unit * 0.04f),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(barHeight)
                                .background(previewScheme.secondary, RoundedCornerShape(blockCorner)),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(barHeight)
                                .background(previewScheme.tertiary, RoundedCornerShape(blockCorner)),
                        )
                    }
                    Spacer(Modifier.height(gap))
                    Text(
                        text = stringResource(nameRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = previewScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MoyiPresetGrid(
    selectedMode: MoyiThemeMode?,
    onSelect: (MoyiThemeMode) -> Unit,
    onTierClick: (MoyiThemeMode) -> Unit,
) {
    val configs = remember { MoyiThemeMode.entries.map { MoyiThemeConfig.forTheme(it) } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        configs.chunked(2).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rowItems.forEach { preset ->
                    MoyiPresetCard(
                        preset = preset,
                        selected = selectedMode == preset.mode,
                        tier = AppThemeState.displayScheme(preset.mode).contentTier,
                        onSelect = { onSelect(preset.mode) },
                        onTierClick = { onTierClick(preset.mode) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowItems.size < 2) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MoyiPresetCard(
    preset: MoyiThemeConfig,
    selected: Boolean,
    tier: MoyiContentTier,
    onSelect: () -> Unit,
    onTierClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(14.dp)
    val borderColor = if (selected) preset.primaryColor else preset.dividerColor
    val borderWidth = if (selected) 2.dp else 1.dp
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .clickable(onClick = onSelect),
            shape = cardShape,
            color = preset.backgroundColor,
            border = BorderStroke(borderWidth, borderColor),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        preset.backgroundBrush()?.let { brush -> Modifier.background(brush) }
                            ?: Modifier,
                    )
                    .padding(12.dp),
            ) {
                Column {
                    Text(
                        text = stringResource(preset.mode.nameRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.W700,
                        color = preset.textPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(preset.navStyle.labelRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = preset.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .then(
                                    preset.selectedBrush()?.let { brush ->
                                        Modifier.background(brush)
                                    } ?: Modifier.background(preset.selectedBg),
                                ),
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(preset.surfaceColor)
                                .border(1.dp, preset.dividerColor, RoundedCornerShape(4.dp)),
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(preset.primaryColor),
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(preset.secondaryColor),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(preset.unselectedBg)
                            .clickable(onClick = onTierClick)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(
                                    R.string.theme_tier_row,
                                    stringResource(tier.labelRes),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = preset.textSecondary,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = preset.textSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
        if (selected) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp),
                )
            }
        }
    }
}

@Composable
private fun MoyiTierDialog(
    mode: MoyiThemeMode,
    currentTier: MoyiContentTier,
    onDismiss: () -> Unit,
    onTierSelected: (MoyiContentTier) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    R.string.theme_tier_title,
                    stringResource(mode.nameRes),
                ),
            )
        },
        text = {
            Column {
                MoyiContentTier.entries.forEach { tier ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onTierSelected(tier) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = tier == currentTier,
                            onClick = { onTierSelected(tier) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(tier.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
