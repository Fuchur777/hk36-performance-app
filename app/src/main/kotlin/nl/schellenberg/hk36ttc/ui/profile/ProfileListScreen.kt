package nl.schellenberg.hk36ttc.ui.profile

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import nl.schellenberg.hk36ttc.R
import nl.schellenberg.hk36ttc.data.local.AircraftProfileEntity
import nl.schellenberg.hk36ttc.data.local.AircraftProfileRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileListScreen(
    repository: AircraftProfileRepository,
    onAddProfile: () -> Unit,
    onOpenProfile: (AircraftProfileEntity) -> Unit,
    onEditProfile: (AircraftProfileEntity) -> Unit,
    onOpenSailplaneTypes: () -> Unit,
    onOpenAirfields: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val viewModel: ProfileListViewModel = viewModel(factory = ProfileListViewModel.factory(repository))
    val profiles by viewModel.profiles.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var profilePendingDelete by remember { mutableStateOf<AircraftProfileEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HK36TTC Calc") },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.profile_list_menu_content_description),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    // Ordered by how often a pilot actually needs them: airfields and sailplane
                    // types get touched per flight, settings rarely, About once. Source
                    // documents and the calculation explainer are sections inside About since
                    // they are all read-once reference material.
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.airfield_list_title)) },
                            onClick = {
                                menuExpanded = false
                                onOpenAirfields()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sailplane_types_title)) },
                            onClick = {
                                menuExpanded = false
                                onOpenSailplaneTypes()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_title)) },
                            onClick = {
                                menuExpanded = false
                                onOpenSettings()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.about_title)) },
                            onClick = {
                                menuExpanded = false
                                onOpenAbout()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddProfile,
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.profile_list_add_content_description))
            }
        }
    ) { padding ->
        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.profile_list_empty_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.profile_list_empty_body),
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
                items(profiles, key = { it.id }) { profile ->
                    Card(
                        onClick = { onOpenProfile(profile) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        ListItem(
                            headlineContent = { Text(profile.registration) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onEditProfile(profile) }) {
                                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.profile_list_edit_content_description))
                                    }
                                    IconButton(onClick = { profilePendingDelete = profile }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = stringResource(R.string.profile_list_delete_content_description),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                                }
                            },
                            colors = androidx.compose.material3.ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }
            }
        }
    }

    profilePendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { profilePendingDelete = null },
            title = { Text(stringResource(R.string.profile_list_delete_confirm_title)) },
            text = { Text(stringResource(R.string.profile_list_delete_confirm_body_format, profile.registration)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProfile(profile)
                    profilePendingDelete = null
                }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { profilePendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
