package nl.schellenberg.hk36ttc.ui.airfield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import nl.schellenberg.hk36ttc.R
import nl.schellenberg.hk36ttc.data.catalog.AirportCatalogRepository

/**
 * Search the worldwide OurAirports catalogue and turn an entry into one of the pilot's own
 * airfields. Tapping a result creates the airfield and goes straight to the edit screen, where
 * the runways can be pulled in and everything checked.
 *
 * This search is the *only* way in to adding an airfield — there is deliberately no separate
 * "add manually" entry point on the list screen, so a pilot always checks the catalogue first
 * (and, with it, gets a chance at the real runway data). [onAddManually] is offered only once a
 * search has actually come up empty, right where the pilot has just learned the catalogue
 * doesn't have their field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirportCatalogScreen(
    catalog: AirportCatalogRepository,
    onBack: () -> Unit,
    onAirfieldCreated: (Long) -> Unit,
    onAddManually: () -> Unit
) {
    val viewModel: AirportCatalogViewModel = viewModel(factory = AirportCatalogViewModel.factory(catalog))
    val state by viewModel.state.collectAsState()
    val results by viewModel.results.collectAsState()
    val scope = rememberCoroutineScope()
    val networkError = stringResource(R.string.airport_catalog_refresh_failed)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.airport_catalog_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh(networkError) },
                        enabled = state.status != CatalogStatus.Loading
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.airport_catalog_refresh))
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                label = { Text(stringResource(R.string.airport_catalog_search_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )

            when (val status = state.status) {
                is CatalogStatus.Loading -> Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        stringResource(R.string.airport_catalog_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is CatalogStatus.Failed -> Text(
                    status.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )

                CatalogStatus.Ready -> {}
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.query.isBlank()) {
                    item {
                        Text(
                            stringResource(R.string.airport_catalog_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (results.isEmpty() && state.status == CatalogStatus.Ready) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.airport_catalog_no_results),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(onClick = onAddManually, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.airport_catalog_add_manually))
                            }
                        }
                    }
                }

                items(results, key = { it.id }) { entry ->
                    Card(
                        onClick = {
                            scope.launch { onAirfieldCreated(viewModel.addAirfield(entry)) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ListItem(
                            headlineContent = { Text(entry.name) },
                            supportingContent = {
                                val place = listOfNotNull(entry.municipality, entry.isoCountry).joinToString(", ")
                                val elevation = entry.elevationM
                                    ?.let { stringResource(R.string.airfield_list_elevation_format, it.roundToInt()) }
                                    ?: stringResource(R.string.airport_catalog_no_elevation)
                                Text(listOf(entry.displayCode, place, elevation).filter { it.isNotBlank() }.joinToString(" · "))
                            }
                        )
                    }
                }

                state.meta?.let { meta ->
                    item {
                        Text(
                            stringResource(R.string.airport_catalog_source_format, meta.airportCount, meta.runwayCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
