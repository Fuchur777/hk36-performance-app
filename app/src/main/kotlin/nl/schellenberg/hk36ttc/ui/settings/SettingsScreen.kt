package nl.schellenberg.hk36ttc.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.schellenberg.hk36ttc.R
import nl.schellenberg.hk36ttc.data.export.ImportParseResult
import nl.schellenberg.hk36ttc.data.export.UserDataExport
import nl.schellenberg.hk36ttc.data.export.UserDataRepository
import nl.schellenberg.hk36ttc.data.local.LanguagePreference
import nl.schellenberg.hk36ttc.data.local.UnitPreferences
import nl.schellenberg.hk36ttc.ui.common.FileSharing

private data class LanguageOption(val tag: String?, val labelResId: Int?, val literalLabel: String?)

private val LANGUAGE_OPTIONS = listOf(
    LanguageOption(null, R.string.settings_language_system, null),
    LanguageOption("nl", null, "Nederlands"),
    LanguageOption("en", null, "English")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userDataRepository: UserDataRepository,
    unitPreferences: UnitPreferences,
    onBack: () -> Unit,
    onLanguageChanged: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val prefs = remember { LanguagePreference(context) }
    var selectedTag by remember { mutableStateOf(prefs.get()) }
    val scope = rememberCoroutineScope()

    // Held until the pilot confirms: parsing never writes, so this is a preview of what *would*
    // replace their data, with the counts the dialog shows.
    var pendingImport by remember { mutableStateOf<UserDataExport?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val unreadableText = stringResource(R.string.settings_data_import_failed_unreadable)
    val exportFailedText = stringResource(R.string.settings_data_export_failed)
    val importDoneText = stringResource(R.string.settings_data_import_done)
    val exportChooserTitle = stringResource(R.string.settings_data_export_chooser)

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }
            }.getOrNull()
            when (val parsed = json?.let { userDataRepository.parse(it) }) {
                is ImportParseResult.Ok -> pendingImport = parsed.data
                is ImportParseResult.TooNew -> message = resources.getString(
                    R.string.settings_data_import_failed_too_new_format,
                    parsed.fileVersion,
                    parsed.supportedVersion
                )
                else -> message = unreadableText
            }
        }
    }

    pendingImport?.let { data ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.settings_data_import_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_data_import_confirm_body_format,
                        data.profileCount,
                        data.airfieldCount
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    scope.launch {
                        val languageChanges = userDataRepository.languageWouldChange(data)
                        userDataRepository.replaceAll(data)
                        selectedTag = prefs.get()
                        message = importDoneText
                        // Same treatment the language picker itself uses — the whole UI has to
                        // be rebuilt for a locale change to take effect.
                        if (languageChanges) onLanguageChanged()
                    }
                }) {
                    Text(
                        stringResource(R.string.settings_data_import_confirm_button),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text(stringResource(R.string.settings_data_heading)) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { message = null }) { Text(stringResource(R.string.common_ok)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                // Scrollable since the data section was added — two sections no longer fit on a
                // small screen.
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(stringResource(R.string.settings_language_heading), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_language_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LANGUAGE_OPTIONS.forEach { option ->
                Card(
                    onClick = {
                        selectedTag = option.tag
                        prefs.set(option.tag)
                        onLanguageChanged()
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    ListItem(
                        headlineContent = { Text(option.literalLabel ?: stringResource(option.labelResId!!)) },
                        leadingContent = {
                            RadioButton(selected = selectedTag == option.tag, onClick = null)
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            UnitSettingsSection(unitPreferences)

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text(stringResource(R.string.settings_data_heading), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_data_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                onClick = {
                    scope.launch {
                        val ok = runCatching {
                            val json = withContext(Dispatchers.IO) {
                                userDataRepository.serialize(userDataRepository.buildExport())
                            }
                            FileSharing.writeAndShare(
                                context = context,
                                fileName = "HK36TTC_gegevens_${FileSharing.fileTimestamp()}.json",
                                mimeType = FileSharing.MIME_JSON,
                                chooserTitle = exportChooserTitle
                            ) { out -> out.write(json.toByteArray()) }
                        }.isSuccess
                        if (!ok) message = exportFailedText
                    }
                },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_data_export)) },
                    supportingContent = { Text(stringResource(R.string.settings_data_export_detail)) },
                    leadingContent = { Icon(Icons.Filled.Upload, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }

            Card(
                // Any MIME type: file managers and mail clients label a .json attachment
                // inconsistently (application/json, text/plain, octet-stream), and filtering on
                // one of them would hide the pilot's own backup from the picker.
                onClick = { importPicker.launch(arrayOf("*/*")) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_data_import)) },
                    supportingContent = { Text(stringResource(R.string.settings_data_import_detail)) },
                    leadingContent = { Icon(Icons.Filled.Download, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        }
    }
}
