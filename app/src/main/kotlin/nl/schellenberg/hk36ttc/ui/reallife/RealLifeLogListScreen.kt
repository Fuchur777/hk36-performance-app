package nl.schellenberg.hk36ttc.ui.reallife

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Circle
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import nl.schellenberg.hk36ttc.R
import nl.schellenberg.hk36ttc.data.local.AircraftProfileRepository
import nl.schellenberg.hk36ttc.data.local.RealLifeConfiguration
import nl.schellenberg.hk36ttc.data.local.RealLifeLogEntity
import nl.schellenberg.hk36ttc.ui.theme.status

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealLifeLogListScreen(
    repository: AircraftProfileRepository,
    profileId: Long,
    onBack: () -> Unit,
    onAddRecording: () -> Unit,
    onOpenLog: (Long) -> Unit
) {
    val viewModel: RealLifeLogListViewModel = viewModel(factory = RealLifeLogListViewModel.factory(repository, profileId))
    val logs by viewModel.logs.collectAsState()
    var logPendingDelete by remember { mutableStateOf<RealLifeLogEntity?>(null) }
    val timestampFormatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Real Life Performance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = onAddRecording) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.reallife_add_content_description))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.reallife_list_empty),
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
                items(logs, key = { it.id }) { log ->
                    LogRow(
                        repository = repository,
                        log = log,
                        timestampFormatter = timestampFormatter,
                        onClick = { onOpenLog(log.id) },
                        onDelete = { logPendingDelete = log }
                    )
                }
            }
        }
    }

    logPendingDelete?.let { log ->
        AlertDialog(
            onDismissRequest = { logPendingDelete = null },
            title = { Text(stringResource(R.string.reallife_delete_confirm_title)) },
            text = { Text(stringResource(R.string.reallife_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteLog(log)
                    logPendingDelete = null
                }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { logPendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun LogRow(
    repository: AircraftProfileRepository,
    log: RealLifeLogEntity,
    timestampFormatter: DateTimeFormatter,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val configurationLabel = when (RealLifeConfiguration.valueOf(log.configuration)) {
        RealLifeConfiguration.NORMAL -> stringResource(R.string.reallife_configuration_normal)
        RealLifeConfiguration.SLEEPVLUCHT -> stringResource(R.string.hub_action_sleepvlucht)
    }
    val startedText = remember(log.startedAtEpochMs) {
        timestampFormatter.format(Instant.ofEpochMilli(log.startedAtEpochMs).atZone(ZoneId.systemDefault()))
    }
    val durationSeconds = log.stoppedAtEpochMs?.let { (it - log.startedAtEpochMs) / 1000 }
    var locationCount by remember(log.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(log.id) { locationCount = repository.countLocationSamples(log.id) }

    // Cheap, DB-only status: whether the Fase 4c conditions snapshot has been filled in (enables
    // the handboek-vs-gemeten comparison on the detail screen) -- not whether detection actually
    // succeeded, which would mean re-running TakeoffDetector over every log's full sample set
    // just to render a list row.
    val conditionsComplete = log.surfaceType != null && log.slopePct != null && log.oatC != null && log.pressureAltM != null

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        ListItem(
            headlineContent = { Text("$startedText — $configurationLabel") },
            leadingContent = {
                Icon(
                    imageVector = if (conditionsComplete) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = stringResource(
                        if (conditionsComplete) R.string.reallife_list_status_complete else R.string.reallife_list_status_log_only
                    ),
                    tint = if (conditionsComplete) MaterialTheme.status.success else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            supportingContent = {
                Column {
                    Text(
                        if (durationSeconds != null) {
                            stringResource(R.string.reallife_list_row_duration_format, durationSeconds / 60, durationSeconds % 60)
                        } else {
                            stringResource(R.string.reallife_list_row_incomplete)
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    locationCount?.let {
                        Text(
                            stringResource(R.string.reallife_list_row_samples_format, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            trailingContent = {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.common_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        )
    }
}
