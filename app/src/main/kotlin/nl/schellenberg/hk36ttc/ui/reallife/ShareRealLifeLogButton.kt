package nl.schellenberg.hk36ttc.ui.reallife

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.schellenberg.hk36ttc.BuildConfig
import nl.schellenberg.hk36ttc.R
import nl.schellenberg.hk36ttc.data.export.RealLifeLogExport
import nl.schellenberg.hk36ttc.data.export.realLifeLogJson
import nl.schellenberg.hk36ttc.data.export.toDto
import nl.schellenberg.hk36ttc.data.local.AircraftProfileRepository
import nl.schellenberg.hk36ttc.data.local.RealLifeLogEntity
import nl.schellenberg.hk36ttc.ui.common.FileSharing

/**
 * Modeled on `ui/report/SharePdfButton.kt`, but async — building the export needs three suspend
 * DB reads (potentially tens of thousands of rows total) rather than `SharePdfButton`'s
 * synchronous, effectively-instant PDF build, so this shows a disabled/spinner state while the
 * button is not idle.
 */
@Composable
fun ShareRealLifeLogButton(
    repository: AircraftProfileRepository,
    log: RealLifeLogEntity,
    registration: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isBuilding by remember { mutableStateOf(false) }
    val chooserTitle = stringResource(R.string.reallife_share_chooser_title)

    OutlinedButton(
        onClick = {
            isBuilding = true
            scope.launch {
                val json = withContext(Dispatchers.IO) {
                    val export = RealLifeLogExport(
                        exportedAtEpochMs = System.currentTimeMillis(),
                        appVersionName = BuildConfig.VERSION_NAME,
                        registration = registration,
                        log = log.toDto(),
                        locationSamples = repository.getLocationSamples(log.id).map { it.toDto() },
                        imuSamples = repository.getImuSamples(log.id).map { it.toDto() },
                        barometerSamples = repository.getBarometerSamples(log.id).map { it.toDto() },
                        markers = repository.getRealLifeMarkers(log.id).map { it.toDto() }
                    )
                    realLifeLogJson.encodeToString(export)
                }
                val safeRegistration = (registration ?: "HK36TTC").replace(Regex("[^A-Za-z0-9-]"), "")
                FileSharing.writeAndShare(
                    context = context,
                    fileName = "HK36TTC_${safeRegistration}_reallife_${log.id}_${FileSharing.fileTimestamp()}.json",
                    mimeType = FileSharing.MIME_JSON,
                    chooserTitle = chooserTitle
                ) { out -> out.write(json.toByteArray()) }
                isBuilding = false
            }
        },
        enabled = !isBuilding,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isBuilding) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Filled.Share, contentDescription = null)
            Text(stringResource(R.string.reallife_share_button))
        }
    }
}
