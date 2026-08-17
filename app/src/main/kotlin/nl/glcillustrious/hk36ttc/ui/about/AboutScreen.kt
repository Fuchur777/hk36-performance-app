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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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

/** Not a resource: an address doesn't change by language, same rule as the AFM/AIC document codes. */
private const val CONTACT_EMAIL = "frank+hk36ttc@schellenberg.nl"

/**
 * One entry per source the app's numbers actually come from.
 *
 * Deliberately a single list: the four AFM/AIC documents used to appear twice on this screen —
 * once as "source documents" you can open and once as "data sources" with a licence — which is
 * the same document described two ways. Name, what it supplies, its licence and where to read
 * it now live together.
 */
private data class SourceEntry(
    val name: String,
    val detailResId: Int,
    val licenseResId: Int? = null,
    val licenseLiteral: String? = null,
    val url: String? = null
)

private data class LibraryEntry(val name: String, val license: String)

private val SOURCES = listOf(
    SourceEntry(
        name = "HK36 TTC/TTS AFM (3.01.20-E Rev. 4)",
        detailResId = R.string.about_source_afm_detail,
        licenseResId = R.string.about_source_proprietary_license,
        url = "https://partners.diamondaircraft.com/sfc/servlet.shepherd/document/download/069Tb000003HCsiIAG?operationContext=S1"
    ),
    SourceEntry(
        name = "AFM Supplement No. 1 (3.01.15-E Rev. 1)",
        detailResId = R.string.about_source_sup1_detail,
        licenseResId = R.string.about_source_proprietary_license,
        url = "https://partners.diamondaircraft.com/sfc/servlet.shepherd/document/download/069Tb000001CFUBIA4?operationContext=S1"
    ),
    SourceEntry(
        name = "AFM Supplement No. 11 (3.01.15-E)",
        detailResId = R.string.about_source_sup11_detail,
        licenseResId = R.string.about_source_proprietary_license,
        url = "https://partners.diamondaircraft.com/sfc/servlet.shepherd/document/download/069Tb000001CGurIAG?operationContext=S1"
    ),
    SourceEntry(
        name = "UK CAA AIC P 173/2024",
        detailResId = R.string.about_source_aic_detail,
        licenseResId = R.string.about_source_aic_license,
        url = "https://nats-uk.ead-it.com/cms-nats/export/sites/default/en/Publications/Aeronautical-Information-Circulars-AICs/pink-aics/EG_Circ_2024_P_173_en.pdf"
    ),
    SourceEntry(
        name = "OurAirports",
        detailResId = R.string.about_source_ourairports_detail,
        licenseResId = R.string.about_source_ourairports_license,
        url = "https://ourairports.com/data/"
    ),
    SourceEntry(
        name = "aviationweather.gov",
        detailResId = R.string.about_source_awc_detail,
        licenseResId = R.string.about_source_awc_license,
        url = "https://aviationweather.gov/"
    ),
    SourceEntry(
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

/**
 * One screen for everything *about* the app rather than a tool you use in flight: what it is,
 * who to mail, how it calculates, and which documents and datasets it is built on.
 *
 * Merged from three separate menu entries (About / Source documents / How this app calculates)
 * — they were all reference material a pilot reads once, and three menu items for that crowded
 * out the two that get used every flight.
 */
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
            // --- What this is ---------------------------------------------------------
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("HK36TTC Calc", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.about_version_format, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.about_build_info_format, BuildConfig.VERSION_CODE, BuildConfig.BUILD_DATE),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.about_app_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // --- Contact --------------------------------------------------------------
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$CONTACT_EMAIL")))
                }
            ) {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.MailOutline, contentDescription = null) },
                    headlineContent = { Text(CONTACT_EMAIL) },
                    supportingContent = { Text(stringResource(R.string.about_contact_body)) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }

            HorizontalDivider()

            // --- How this app calculates ----------------------------------------------
            Text(stringResource(R.string.explainer_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.explainer_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.explainer_labels_legend_heading), style = MaterialTheme.typography.labelLarge)
                    Text(
                        stringResource(R.string.explainer_labels_legend_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            ExplainerSection(R.string.explainer_wb_heading, R.string.explainer_wb_body)
            ExplainerSection(R.string.explainer_takeoff_landing_heading, R.string.explainer_takeoff_landing_body)
            ExplainerSection(R.string.explainer_sleepvlucht_heading, R.string.explainer_sleepvlucht_body)
            Text(
                stringResource(R.string.explainer_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // --- Where the numbers come from ------------------------------------------
            Text(stringResource(R.string.about_data_sources_heading), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            SOURCES.forEach { entry ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        entry.url?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                    }
                ) {
                    ListItem(
                        headlineContent = {
                            Text(entry.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(entry.detailResId), style = MaterialTheme.typography.bodySmall)
                                Text(
                                    entry.licenseLiteral ?: stringResource(entry.licenseResId!!),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        // Only the openable ones get the affordance; nothing here is a dead tap.
                        trailingContent = entry.url?.let {
                            {
                                Icon(
                                    Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = stringResource(R.string.documents_open_content_description)
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
            }

            Text(stringResource(R.string.about_libraries_heading), style = MaterialTheme.typography.titleMedium)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LIBRARIES.forEach { lib ->
                        Text("${lib.name} — ${lib.license}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplainerSection(headingResId: Int, bodyResId: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(headingResId), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(bodyResId), style = MaterialTheme.typography.bodyMedium)
    }
}
