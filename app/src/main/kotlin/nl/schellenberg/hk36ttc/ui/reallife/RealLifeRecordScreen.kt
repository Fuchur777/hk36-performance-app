package nl.schellenberg.hk36ttc.ui.reallife

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import nl.schellenberg.hk36ttc.R
import nl.schellenberg.hk36ttc.data.local.AircraftProfileRepository
import nl.schellenberg.hk36ttc.data.local.RealLifeConfiguration
import nl.schellenberg.hk36ttc.data.local.RealLifeMarkerType
import nl.schellenberg.hk36ttc.data.local.RealLifeStopReason
import nl.schellenberg.hk36ttc.ui.common.uniformSegmentedRowHeight

/** GPS update interval requested from FusedLocationProviderClient. Achieved frequency depends on
 * the device's GNSS chip and cannot be forced (see docs/00-plan.md §12, spec §3) — this is only
 * what's asked for. */
private const val REQUESTED_GPS_INTERVAL_MS = 1_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealLifeRecordScreen(
    repository: AircraftProfileRepository,
    profileId: Long,
    onBack: () -> Unit
) {
    val viewModel: RealLifeRecordViewModel = viewModel(factory = RealLifeRecordViewModel.factory(repository, profileId))
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Real Life Performance") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isRecording) {
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    stringResource(R.string.reallife_disclaimer),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            LocationPermissionGate {
                RecordingControls(state = state, viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingControls(state: RealLifeRecordState, viewModel: RealLifeRecordViewModel) {
    val context = LocalContext.current
    val view = LocalView.current

    val recorder = remember(viewModel) {
        RealLifeRecorder(
            context = context.applicationContext,
            requestedIntervalMs = REQUESTED_GPS_INTERVAL_MS,
            onLocationBatch = viewModel::onLocationBatch,
            onImuBatch = viewModel::onImuBatch,
            onBarometerBatch = viewModel::onBarometerBatch,
            onError = viewModel::recordingStartFailed
        )
    }

    // Safety net: whatever happens to composition (back press, process death signal, nav away),
    // never leave GPS/sensors registered.
    DisposableEffect(Unit) {
        onDispose { recorder.stop() }
    }

    // Foreground-only recording (see docs/00-plan.md §24-adjacent plan notes): if the app is
    // backgrounded mid-recording, stop immediately rather than silently continuing to (maybe)
    // collect throttled data the pilot doesn't know is degraded. Deliberately ProcessLifecycleOwner
    // (whole-app foreground/background), not LocalLifecycleOwner: under Navigation-Compose,
    // LocalLifecycleOwner resolves to the NavBackStackEntry's own lifecycle, which also fires
    // ON_PAUSE on plain back-navigation away from this screen -- that used to stop a still-wanted
    // recording and mislabel it BACKGROUNDED even though the app never left the foreground.
    DisposableEffect(state.isRecording) {
        val processLifecycleOwner = ProcessLifecycleOwner.get()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && state.isRecording) {
                recorder.stop()
                viewModel.stopRecording(RealLifeStopReason.BACKGROUNDED)
            }
        }
        processLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { processLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.isRecording) { view.keepScreenOn = state.isRecording }

    if (state.isRecording) {
        RecordingActiveCard(
            startedAtEpochMs = state.startedAtEpochMs ?: System.currentTimeMillis(),
            locationCount = state.locationCount,
            imuCount = state.imuCount,
            barometerCount = state.barometerCount,
            markedTypes = state.markedTypes,
            onMark = viewModel::markEvent,
            onStop = {
                recorder.stop()
                viewModel.stopRecording(RealLifeStopReason.MANUAL)
            }
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.reallife_configuration_label), style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().uniformSegmentedRowHeight()) {
                    SegmentedButton(
                        selected = state.configuration == RealLifeConfiguration.NORMAL,
                        onClick = { viewModel.updateConfiguration(RealLifeConfiguration.NORMAL) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.fillMaxHeight()
                    ) { Text(stringResource(R.string.reallife_configuration_normal)) }
                    SegmentedButton(
                        selected = state.configuration == RealLifeConfiguration.SLEEPVLUCHT,
                        onClick = { viewModel.updateConfiguration(RealLifeConfiguration.SLEEPVLUCHT) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.fillMaxHeight()
                    ) { Text(stringResource(R.string.hub_action_sleepvlucht)) }
                }
            }

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::updateNotes,
                label = { Text(stringResource(R.string.reallife_notes_label)) },
                modifier = Modifier.fillMaxWidth()
            )

            state.startError?.let { error ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        error,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Button(
                onClick = {
                    viewModel.startRecording(recorder.barometerAvailable(), REQUESTED_GPS_INTERVAL_MS) { logId ->
                        recorder.start(logId)
                    }
                },
                enabled = !state.starting,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(stringResource(R.string.reallife_start_button), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun RecordingActiveCard(
    startedAtEpochMs: Long,
    locationCount: Int,
    imuCount: Int,
    barometerCount: Int,
    markedTypes: Set<RealLifeMarkerType>,
    onMark: (RealLifeMarkerType) -> Unit,
    onStop: () -> Unit
) {
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(startedAtEpochMs) {
        while (true) {
            elapsedSeconds = (System.currentTimeMillis() - startedAtEpochMs) / 1000
            delay(1_000)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.reallife_recording_elapsed_format, elapsedSeconds / 60, elapsedSeconds % 60),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.reallife_recording_counts_format, locationCount, imuCount, barometerCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Ground-truth taps for a co-pilot/observer — see RealLifeRecordViewModel.markEvent's
        // KDoc. Large, one-shot buttons: this is meant to be usable mid-flight without looking,
        // so each one flips to a checkmark instead of just disabling silently once tapped.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.reallife_marker_heading), style = MaterialTheme.typography.labelLarge)
            MarkerButton(
                label = stringResource(R.string.reallife_marker_roll_start),
                marked = RealLifeMarkerType.ROLL_START in markedTypes,
                onClick = { onMark(RealLifeMarkerType.ROLL_START) }
            )
            MarkerButton(
                label = stringResource(R.string.reallife_marker_lift_off),
                marked = RealLifeMarkerType.LIFT_OFF in markedTypes,
                onClick = { onMark(RealLifeMarkerType.LIFT_OFF) }
            )
            MarkerButton(
                label = stringResource(R.string.reallife_marker_fifteen_m),
                marked = RealLifeMarkerType.FIFTEEN_M in markedTypes,
                onClick = { onMark(RealLifeMarkerType.FIFTEEN_M) }
            )
        }

        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(stringResource(R.string.reallife_stop_button), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun MarkerButton(label: String, marked: Boolean, onClick: () -> Unit) {
    if (marked) {
        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.Filled.Check, contentDescription = null)
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    } else {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}
