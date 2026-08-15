package nl.glcillustrious.hk36ttc.ui.sailplane

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.glcillustrious.hk36ttc.R
import nl.glcillustrious.hk36ttc.core.perf.SailplaneTypesData
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SailplaneTypesScreen(
    repository: AircraftProfileRepository,
    sailplaneTypes: SailplaneTypesData,
    onBack: () -> Unit
) {
    val viewModel: SailplaneTypesViewModel = viewModel(
        factory = SailplaneTypesViewModel.factory(repository, sailplaneTypes)
    )
    val query by viewModel.searchQuery.collectAsState()
    val rows by viewModel.rows.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sailplane_types_title)) },
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
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::updateSearchQuery,
                label = { Text(stringResource(R.string.sailplane_types_search_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rows, key = { it.type.name }) { row ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        ListItem(
                            headlineContent = { Text(row.type.name) },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        R.string.sailplane_types_row_detail_format,
                                        row.type.emptyMassKg.toInt(),
                                        row.type.mtowKg.toInt(),
                                        row.type.ldRatio.toString()
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { viewModel.toggleFavorite(row) }) {
                                    Icon(
                                        if (row.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = stringResource(
                                            if (row.isFavorite) R.string.sailplane_types_favorite_remove else R.string.sailplane_types_favorite_add
                                        ),
                                        tint = if (row.isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                    }
                }
                item {
                    Text(
                        stringResource(R.string.sailplane_types_source_footer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}
