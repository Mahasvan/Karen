package com.mahasvan.karen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahasvan.karen.R
import com.mahasvan.karen.settings.AppSettings
import com.mahasvan.karen.settings.BackendChoice
import com.mahasvan.karen.settings.ColorTheme
import com.mahasvan.karen.settings.ConnectionState
import com.mahasvan.karen.settings.RemoteConfig
import com.mahasvan.karen.ui.theme.AppTheme
import com.mahasvan.karen.ui.theme.AssistantTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.tooling.preview.PreviewLightDark

/**
 * Settings page. Appearance and assistant backend are wired; on-device model and
 * runtime selection land here next, under their own sections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onColorThemeChange: (ColorTheme) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    connection: ConnectionState = ConnectionState.Idle,
    onBackendChange: (BackendChoice) -> Unit = {},
    onTestConnection: (baseUrl: String, apiKey: String) -> Unit = { _, _ -> },
    onModelChange: (String) -> Unit = {},
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
        Column(
            modifier = Modifier
                .padding(contentPadding)
                // Before verticalScroll, so the keyboard shrinks the scroll viewport
                // rather than padding the content inside it. Compose then scrolls the
                // focused field into what's left.
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
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

            SectionHeader(stringResource(R.string.settings_backend))
            BackendSegmentedButton(
                selected = settings.backend,
                onSelect = onBackendChange,
            )
            if (settings.backend == BackendChoice.Remote) {
                RemoteBackendSection(
                    config = settings.remoteBackendConfig,
                    connection = connection,
                    onTestConnection = onTestConnection,
                    onModelChange = onModelChange,
                )
            }
        }
    }
}

/**
 * Endpoint form. URL and key are edited as local drafts and only persisted by
 * [onTestConnection] once the endpoint answers, so a half-typed URL never reaches
 * the live backend. The model list is whatever that probe returned; the stored
 * model stays selectable so the row still reads correctly before a probe runs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteBackendSection(
    config: RemoteConfig,
    connection: ConnectionState,
    onTestConnection: (baseUrl: String, apiKey: String) -> Unit,
    onModelChange: (String) -> Unit,
) {
    val palette = AssistantTheme.palette
    var baseUrl by rememberSaveable(config.baseUrl) { mutableStateOf(config.baseUrl) }
    var apiKey by rememberSaveable(config.apiKey) { mutableStateOf(config.apiKey) }
    var modelsExpanded by rememberSaveable { mutableStateOf(false) }

    val models = (connection as? ConnectionState.Connected)?.models.orEmpty()

    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_remote_note),
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
        )
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text(stringResource(R.string.settings_remote_base_url)) },
            supportingText = { Text(stringResource(R.string.settings_remote_base_url_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text(stringResource(R.string.settings_remote_api_key)) },
            supportingText = { Text(stringResource(R.string.settings_remote_api_key_hint)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { onTestConnection(baseUrl, apiKey) },
                enabled = connection != ConnectionState.Testing && baseUrl.isNotBlank(),
            ) {
                Text(stringResource(R.string.settings_remote_test))
            }
            ConnectionStatus(connection)
        }

        ExposedDropdownMenuBox(
            expanded = modelsExpanded,
            onExpandedChange = { modelsExpanded = it },
        ) {
            OutlinedTextField(
                value = config.model.ifBlank { stringResource(R.string.settings_remote_model_none) },
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_remote_model)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelsExpanded) },
                supportingText = {
                    if (models.isEmpty()) Text(stringResource(R.string.settings_remote_model_hint))
                },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = modelsExpanded,
                onDismissRequest = { modelsExpanded = false },
            ) {
                models.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model) },
                        onClick = {
                            onModelChange(model)
                            modelsExpanded = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun ConnectionStatus(connection: ConnectionState) {
    val palette = AssistantTheme.palette
    when (connection) {
        ConnectionState.Idle -> Unit

        ConnectionState.Testing -> CircularProgressIndicator(modifier = Modifier.size(20.dp))

        is ConnectionState.Connected -> Text(
            text = pluralStringResource(
                R.plurals.settings_remote_connected,
                connection.models.size,
                connection.models.size,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
        )

        is ConnectionState.Failed -> Text(
            text = connection.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * Two-option segmented control for picking the assistant backend. The summaries
 * from the old radio rows don't fit a segmented button, so they live below it as
 * a single line of secondary text tied to whichever side is selected.
 */
@Composable
private fun BackendSegmentedButton(
    selected: BackendChoice,
    onSelect: (BackendChoice) -> Unit,
) {
    val palette = AssistantTheme.palette
    val options = BackendChoice.entries
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, choice ->
                SegmentedButton(
                    selected = choice == selected,
                    onClick = { onSelect(choice) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(
                        text = stringResource(
                            when (choice) {
                                BackendChoice.OnDevice -> R.string.settings_backend_on_device
                                BackendChoice.Remote -> R.string.settings_backend_remote
                            },
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.size(6.dp))
        Text(
            text = stringResource(
                when (selected) {
                    BackendChoice.OnDevice -> R.string.settings_backend_on_device_summary
                    BackendChoice.Remote -> R.string.settings_backend_remote_summary
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
        )
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

@PreviewLightDark()
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
