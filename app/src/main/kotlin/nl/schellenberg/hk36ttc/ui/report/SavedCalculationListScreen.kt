package nl.schellenberg.hk36ttc.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.schellenberg.hk36ttc.R
import nl.schellenberg.hk36ttc.data.local.AircraftProfileRepository
import nl.schellenberg.hk36ttc.data.local.SavedCalculationEntity
import nl.schellenberg.hk36ttc.data.local.SavedCalculationType
import nl.schellenberg.hk36ttc.ui.common.FileSharing

/**
 * Lists every calculation the pilot has saved for one registration and one calculation kind
 * (W&B/Take-off/Glider tow/Landing) — the "save instead of share" list, modeled directly on
 * `ui/reallife/RealLifeLogListScreen.kt`. Sharing a saved entry as a PDF lives here now, per row
 * — each row's [documentJson][SavedCalculationEntity.documentJson] is the exact already-computed,
 * already-translated [ReportDocument] the pilot would have shared at save time, so re-sharing it
 * later needs no recalculation and is correct regardless of the app's *current* language/unit
 * settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedCalculationListScreen(
    repository: AircraftProfileRepository,
    profileId: Long,
    type: SavedCalculationType,
    onBack: () -> Unit
) {
    val viewModel: SavedCalculationListViewModel =
        viewModel(factory = SavedCalculationListViewModel.factory(repository, profileId, type))
    val calculations by viewModel.calculations.collectAsState()
    val registration by viewModel.registration.collectAsState()
    var pendingDelete by remember { mutableStateOf<SavedCalculationEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.report_saved_calculations_format, savedCalculationKindLabel(type))) },
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
        if (calculations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.report_saved_list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(calculations, key = { it.id }) { calculation ->
                    SavedCalculationRow(
                        calculation = calculation,
                        registration = registration,
                        kindFileToken = savedCalculationFileToken(type),
                        onDelete = { pendingDelete = calculation }
                    )
                }
            }
        }
    }

    pendingDelete?.let { calculation ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.report_saved_delete_confirm_title)) },
            text = { Text(stringResource(R.string.report_saved_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCalculation(calculation)
                    pendingDelete = null
                }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun SavedCalculationRow(
    calculation: SavedCalculationEntity,
    registration: String?,
    kindFileToken: String,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.report_share_chooser_title)
    val shareDescription = stringResource(R.string.report_share_pdf)
    val deleteDescription = stringResource(R.string.common_delete)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        ListItem(
            headlineContent = { Text(calculation.title) },
            supportingContent = { Text(calculation.timestamp) },
            trailingContent = {
                Row {
                    IconButton(onClick = {
                        val document = reportDocumentJson.decodeFromString<ReportDocument>(calculation.documentJson)
                        val safeRegistration = (registration ?: "HK36TTC").replace(Regex("[^A-Za-z0-9-]"), "")
                        FileSharing.writeAndShare(
                            context = context,
                            fileName = "HK36TTC_${safeRegistration}_${kindFileToken}_${FileSharing.fileTimestamp()}.pdf",
                            mimeType = FileSharing.MIME_PDF,
                            chooserTitle = chooserTitle
                        ) { out -> PdfRenderer.render(document, out) }
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = shareDescription)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = deleteDescription, tint = MaterialTheme.colorScheme.error)
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        )
    }
}

@Composable
private fun savedCalculationKindLabel(type: SavedCalculationType): String = when (type) {
    SavedCalculationType.WB -> stringResource(R.string.report_title_wb)
    SavedCalculationType.TAKEOFF -> stringResource(R.string.report_title_takeoff)
    SavedCalculationType.GLIDER_TOW -> stringResource(R.string.report_title_sleepvlucht)
    SavedCalculationType.LANDING -> stringResource(R.string.report_title_landing)
}

/** Short, filename-safe token — matches [SharePdfButton]'s own `kind` strings 1:1 (not
 * translated on purpose, same reasoning as there: a filename should stay recognisable regardless
 * of the app's language). */
private fun savedCalculationFileToken(type: SavedCalculationType): String = when (type) {
    SavedCalculationType.WB -> "wb"
    SavedCalculationType.TAKEOFF -> "takeoff"
    SavedCalculationType.GLIDER_TOW -> "sleepvlucht"
    SavedCalculationType.LANDING -> "landing"
}
