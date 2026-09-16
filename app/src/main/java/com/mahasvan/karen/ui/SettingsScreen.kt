package com.mahasvan.karen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mahasvan.karen.R
import com.mahasvan.karen.settings.AppSettings
import com.mahasvan.karen.settings.ColorTheme
import com.mahasvan.karen.ui.theme.AppTheme
import com.mahasvan.karen.ui.theme.AssistantTheme
import androidx.compose.material3.MaterialTheme

/**
 * Settings page. Appearance is wired first; model and runtime selection land
 * here next, under their own sections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onColorThemeChange: (ColorTheme) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = AssistantTheme.palette
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = palette.sheetSurface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding)) {
            SectionHeader(stringResource(R.string.settings_appearance))
            SwitchRow(
                title = stringResource(R.string.settings_material_you),
                summary = stringResource(R.string.settings_material_you_summary),
                checked = settings.colorTheme == ColorTheme.MaterialYou,
                onCheckedChange = { useMaterialYou ->
                    onColorThemeChange(
                        if (useMaterialYou) ColorTheme.MaterialYou else ColorTheme.Google,
                    )
                },
                trailingPreview = { BrandColorDots() },
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

/**
 * A settings row whose whole surface toggles the switch, so the tap target isn't
 * just the control itself.
 */
@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    trailingPreview: @Composable (() -> Unit)? = null,
) {
    val palette = AssistantTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.textPrimary,
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
            )
            if (trailingPreview != null) {
                Spacer(Modifier.size(10.dp))
                trailingPreview()
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Live preview of the four accents the current choice produces. */
@Composable
private fun BrandColorDots() {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AssistantTheme.brandColors.forEach { color ->
            Spacer(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    AppTheme {
        SettingsScreen(
            settings = AppSettings(colorTheme = ColorTheme.MaterialYou),
            onColorThemeChange = {},
            onBack = {},
        )
    }
}
