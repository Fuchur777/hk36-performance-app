package nl.glcillustrious.hk36ttc.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.glcillustrious.hk36ttc.BuildConfig
import nl.glcillustrious.hk36ttc.R

private data class DataSourceEntry(
    val name: String,
    val detailResId: Int,
    val licenseResId: Int? = null,
    val licenseLiteral: String? = null,
    val url: String? = null
)
private data class LibraryEntry(val name: String, val license: String)

private val DATA_SOURCES = listOf(
    DataSourceEntry(
        name = "HK36 TTC/TTS AFM (3.01.20-E Rev. 4)",
        detailResId = R.string.about_source_afm_detail,
        licenseResId = R.string.about_source_proprietary_license
    ),
    DataSourceEntry(
        name = "AFM Supplement No. 1 (3.01.15-E Rev. 1)",
        detailResId = R.string.about_source_sup1_detail,
        licenseResId = R.string.about_source_proprietary_license
    ),
    DataSourceEntry(
        name = "AFM Supplement No. 11 (3.01.15-E)",
        detailResId = R.string.about_source_sup11_detail,
        licenseResId = R.string.about_source_proprietary_license
    ),
    DataSourceEntry(
        name = "UK CAA AIC P 173/2024",
        detailResId = R.string.about_source_aic_detail,
        licenseResId = R.string.about_source_aic_license,
        url = "https://nats-uk.ead-it.com/cms-nats/export/sites/default/en/Publications/Aeronautical-Information-Circulars-AICs/pink-aics/EG_Circ_2024_P_173_en.pdf"
    ),
    DataSourceEntry(
        name = "XCSoar polar-database",
        detailResId = R.string.about_source_xcsoar_detail,
        licenseLiteral = "GPL-2.0-or-later",
        url = "https://github.com/XCSoar/XCSoar"
    )
)

private val LIBRARIES = listOf(
    LibraryEntry("Kotlin", "Apache License 2.0"),
    LibraryEntry("Jetpack Compose", "Apache License 2.0"),
    LibraryEntry("AndroidX Lifecycle / ViewModel", "Apache License 2.0"),
    LibraryEntry("AndroidX Navigation Compose", "Apache License 2.0"),
    LibraryEntry("AndroidX Room", "Apache License 2.0"),
    LibraryEntry("Kotlinx Coroutines", "Apache License 2.0"),
    LibraryEntry("Kotlinx Serialization", "Apache License 2.0")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("HK36TTC Calc", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.about_version_format, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.about_app_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(stringResource(R.string.about_data_sources_heading), style = MaterialTheme.typography.titleMedium)
            DATA_SOURCES.forEach { entry ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        entry.url?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                    }
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(entry.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(stringResource(entry.detailResId), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            entry.licenseLiteral ?: stringResource(entry.licenseResId!!),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(stringResource(R.string.about_libraries_heading), style = MaterialTheme.typography.titleMedium)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LIBRARIES.forEach { lib ->
                        Text(
                            "${lib.name} — ${lib.license}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
