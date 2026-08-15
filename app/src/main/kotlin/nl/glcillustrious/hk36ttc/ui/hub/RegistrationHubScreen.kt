package nl.glcillustrious.hk36ttc.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Scale
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.glcillustrious.hk36ttc.R
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileRepository

/** Landing spot after picking a registration — one place to launch any calculation for it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationHubScreen(
    repository: AircraftProfileRepository,
    profileId: Long,
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenWb: () -> Unit,
    onOpenTakeoff: () -> Unit,
    onOpenSleepvlucht: () -> Unit,
    onOpenLanding: () -> Unit
) {
    var registration by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(profileId) {
        registration = repository.getById(profileId)?.registration
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(registration ?: stringResource(R.string.hub_title_fallback)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = onEditProfile) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.hub_edit_content_description))
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HubActionCard("Weight & Balance", Icons.Filled.Scale, onOpenWb)
            HubActionCard("Take-off", Icons.Filled.FlightTakeoff, onOpenTakeoff)
            HubActionCard(stringResource(R.string.hub_action_sleepvlucht), Icons.Filled.Link, onOpenSleepvlucht)
            HubActionCard("Landing", Icons.Filled.FlightLand, onOpenLanding)
        }
    }
}

@Composable
private fun HubActionCard(title: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
    }
}
