package nl.glcillustrious.hk36ttc.ui.documents

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.glcillustrious.hk36ttc.R

private data class SourceDocument(val label: String, val descriptionResId: Int, val url: String)

private val SOURCE_DOCUMENTS = listOf(
    SourceDocument(
        label = "AFM",
        descriptionResId = R.string.documents_afm_description,
        url = "https://partners.diamondaircraft.com/sfc/servlet.shepherd/document/download/069Tb000003HCsiIAG?operationContext=S1"
    ),
    SourceDocument(
        label = "AFM Sup 1",
        descriptionResId = R.string.documents_afm_sup1_description,
        url = "https://partners.diamondaircraft.com/sfc/servlet.shepherd/document/download/069Tb000001CFUBIA4?operationContext=S1"
    ),
    SourceDocument(
        label = "AFM Sup 11",
        descriptionResId = R.string.documents_afm_sup11_description,
        url = "https://partners.diamondaircraft.com/sfc/servlet.shepherd/document/download/069Tb000001CGurIAG?operationContext=S1"
    ),
    SourceDocument(
        label = "AIC P173",
        descriptionResId = R.string.documents_aic_p173_description,
        url = "https://nats-uk.ead-it.com/cms-nats/export/sites/default/en/Publications/Aeronautical-Information-Circulars-AICs/pink-aics/EG_Circ_2024_P_173_en.pdf"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.documents_title)) },
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
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding)
        ) {
            items(SOURCE_DOCUMENTS) { doc ->
                Card(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(doc.url))) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    ListItem(
                        headlineContent = { Text(doc.label) },
                        supportingContent = { Text(stringResource(doc.descriptionResId)) },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.documents_open_content_description))
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
            }
        }
    }
}
