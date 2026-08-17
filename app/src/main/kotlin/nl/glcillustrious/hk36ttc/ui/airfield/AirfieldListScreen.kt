package nl.glcillustrious.hk36ttc.ui.airfield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.glcillustrious.hk36ttc.R
import nl.glcillustrious.hk36ttc.data.catalog.AirportCatalogRepository
import nl.glcillustrious.hk36ttc.data.local.AirfieldEntity
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirfieldListScreen(
    repository: AircraftProfileRepository,
    catalog: AirportCatalogRepository,
    onBack: () -> Unit,
    onEditAirfield: (AirfieldEntity) -> Unit,
    onOpenCatalog: () -> Unit
) {
    val viewModel: AirfieldListViewModel = viewModel(factory = AirfieldListViewModel.factory(repository, catalog))
    val rows by viewModel.rows.collectAsState()
    val refreshResult by viewModel.refreshResult.collectAsState()
    var airfieldPendingDelete by remember { mutableStateOf<AirfieldEntity?>(null) }

    refreshResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.clearRefreshResult() },
            title = { Text(stringResource(R.string.airfield_list_update_from_source)) },
            text = {
                Text(stringResource(R.string.airfield_list_update_result_format, result.updated, result.checked))
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearRefreshResult() }) {
                    Text(stringResource(R.string.common_ok))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.airfield_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenCatalog) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.airport_catalog_open))
                    }
                    IconButton(onClick = { viewModel.updateFromCatalog() }, enabled = rows.isNotEmpty()) {
                        Icon(Icons.Filled.Sync, contentDescription = stringResource(R.string.airfield_list_update_from_source))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
    ) { padding ->
        if (rows.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.airfield_list_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.airfield_list_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                item {
                    Text(
                        stringResource(R.string.airfield_list_favorite_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(rows, key = { it.airfield.id }) { row ->
                    val airfield = row.airfield
                    Card(
                        onClick = { onEditAirfield(airfield) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        ListItem(
                            headlineContent = { Text(airfield.name) },
                            supportingContent = {
                                Text(
                                    stringResource(R.string.airfield_list_elevation_format, airfield.elevationM.toInt()),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { viewModel.toggleFavorite(row) }) {
                                        Icon(
                                            if (row.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                            contentDescription = stringResource(
                                                if (row.isFavorite) R.string.airfield_list_favorite_remove else R.string.airfield_list_favorite_add
                                            ),
                                            tint = if (row.isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onEditAirfield(airfield) }) {
                                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.airfield_list_edit_content_description))
                                    }
                                    IconButton(onClick = { airfieldPendingDelete = airfield }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = stringResource(R.string.airfield_list_delete_content_description),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                    }
                }
            }
        }
    }

    airfieldPendingDelete?.let { airfield ->
        AlertDialog(
            onDismissRequest = { airfieldPendingDelete = null },
            title = { Text(stringResource(R.string.airfield_list_delete_confirm_title)) },
            text = { Text(stringResource(R.string.airfield_list_delete_confirm_body_format, airfield.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAirfield(airfield)
                    airfieldPendingDelete = null
                }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { airfieldPendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
