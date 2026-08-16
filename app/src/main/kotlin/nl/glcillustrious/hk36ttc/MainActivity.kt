package nl.glcillustrious.hk36ttc

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileRepository
import nl.glcillustrious.hk36ttc.data.local.AppCalculationData
import nl.glcillustrious.hk36ttc.data.local.CalculationDataResult
import nl.glcillustrious.hk36ttc.data.local.CalculationDataStore
import nl.glcillustrious.hk36ttc.data.local.LanguagePreference
import nl.glcillustrious.hk36ttc.ui.about.AboutScreen
import nl.glcillustrious.hk36ttc.ui.documents.DocumentsScreen
import nl.glcillustrious.hk36ttc.ui.explainer.ExplainerScreen
import nl.glcillustrious.hk36ttc.ui.hub.RegistrationHubScreen
import nl.glcillustrious.hk36ttc.ui.perf.LandingScreen
import nl.glcillustrious.hk36ttc.ui.perf.SleepvluchtScreen
import nl.glcillustrious.hk36ttc.ui.perf.TakeoffScreen
import nl.glcillustrious.hk36ttc.ui.profile.ProfileEditScreen
import nl.glcillustrious.hk36ttc.ui.profile.ProfileListScreen
import nl.glcillustrious.hk36ttc.ui.sailplane.SailplaneTypesScreen
import nl.glcillustrious.hk36ttc.ui.settings.SettingsScreen
import nl.glcillustrious.hk36ttc.ui.theme.Hk36ttcTheme
import nl.glcillustrious.hk36ttc.ui.wb.WbScreen
import java.util.Locale

private object Routes {
    const val PROFILES = "profiles"
    const val PROFILE_EDIT = "profile_edit/{profileId}"
    const val HUB = "hub/{profileId}"
    const val WB = "wb/{profileId}"
    const val TAKEOFF = "takeoff/{profileId}"
    const val SLEEPVLUCHT = "sleepvlucht/{profileId}"
    const val LANDING = "landing/{profileId}"
    const val DOCUMENTS = "documents"
    const val SAILPLANE_TYPES = "sailplane_types"
    const val EXPLAINER = "explainer"
    const val ABOUT = "about"
    const val SETTINGS = "settings"

    fun profileEdit(id: Long) = "profile_edit/$id"
    fun hub(id: Long) = "hub/$id"
    fun wb(id: Long) = "wb/$id"
    fun takeoff(id: Long) = "takeoff/$id"
    fun sleepvlucht(id: Long) = "sleepvlucht/$id"
    fun landing(id: Long) = "landing/$id"
}

class MainActivity : ComponentActivity() {

    /**
     * Applies the user's language override (if any) before any resource is resolved — Android
     * calls this before [onCreate], so a forced NL/EN choice takes effect on cold start too,
     * not just after a manual [recreate]. Falls through to the device's own locale untouched
     * when no override is set, per rekenlogica.md's "auto-detect, user can always override"
     * requirement (00-plan.md).
     */
    override fun attachBaseContext(newBase: Context) {
        val languageTag = LanguagePreference(newBase).get()
        val context = if (languageTag != null) {
            val locale = Locale.forLanguageTag(languageTag)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            newBase.createConfigurationContext(config)
        } else {
            newBase
        }
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as Hk36Application
        val repository = app.repository
        val dataStore = app.calculationDataStore

        setContent {
            Hk36ttcTheme {
                CalculationDataGate(dataStore) { appData ->
                    val navController = rememberNavController()
                    Hk36NavHost(navController, repository, appData, onLanguageChanged = { recreate() })
                }
            }
        }
    }
}

/**
 * Loads [AppCalculationData] from the on-device JSON files before showing any calculation
 * screen. A parse/read failure is shown as a blocking error with a reset action rather than
 * falling back to a hardcoded default — this is safety-relevant W&B/performance data, so
 * silently substituting a possibly-wrong value is worse than making the user fix or reset
 * the file(s) first.
 */
@Composable
private fun CalculationDataGate(
    dataStore: CalculationDataStore,
    content: @Composable (AppCalculationData) -> Unit
) {
    var result by remember { mutableStateOf<CalculationDataResult?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        result = withContext(Dispatchers.IO) { dataStore.loadAll() }
    }

    when (val current = result) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
        }

        is CalculationDataResult.Success -> content(current.data)

        is CalculationDataResult.Failure -> Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.startup_load_failed_title), style = MaterialTheme.typography.titleMedium)
                Text(current.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.startup_load_failed_folder_format, dataStore.dataDirPath()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = {
                    result = null
                    scope.launch {
                        result = withContext(Dispatchers.IO) { dataStore.resetAllToDefaults() }
                    }
                }) {
                    Text(stringResource(R.string.startup_reset_to_defaults))
                }
            }
        }
    }
}

@Composable
private fun Hk36NavHost(
    navController: NavHostController,
    repository: AircraftProfileRepository,
    appData: AppCalculationData,
    onLanguageChanged: () -> Unit
) {
    NavHost(navController = navController, startDestination = Routes.PROFILES) {
        composable(Routes.PROFILES) {
            ProfileListScreen(
                repository = repository,
                onAddProfile = { navController.navigate(Routes.profileEdit(0)) },
                onOpenProfile = { profile -> navController.navigate(Routes.hub(profile.id)) },
                onEditProfile = { profile -> navController.navigate(Routes.profileEdit(profile.id)) },
                onOpenDocuments = { navController.navigate(Routes.DOCUMENTS) },
                onOpenSailplaneTypes = { navController.navigate(Routes.SAILPLANE_TYPES) },
                onOpenExplainer = { navController.navigate(Routes.EXPLAINER) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() }, onLanguageChanged = onLanguageChanged)
        }
        composable(Routes.DOCUMENTS) {
            DocumentsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.EXPLAINER) {
            ExplainerScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SAILPLANE_TYPES) {
            SailplaneTypesScreen(
                repository = repository,
                sailplaneTypes = appData.sailplaneTypes,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.HUB) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId")?.toLongOrNull() ?: 0L
            RegistrationHubScreen(
                repository = repository,
                profileId = profileId,
                onBack = { navController.popBackStack() },
                onEditProfile = { navController.navigate(Routes.profileEdit(profileId)) },
                onOpenWb = { navController.navigate(Routes.wb(profileId)) },
                onOpenTakeoff = { navController.navigate(Routes.takeoff(profileId)) },
                onOpenSleepvlucht = { navController.navigate(Routes.sleepvlucht(profileId)) },
                onOpenLanding = { navController.navigate(Routes.landing(profileId)) }
            )
        }
        composable(Routes.PROFILE_EDIT) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId")?.toLongOrNull() ?: 0L
            ProfileEditScreen(
                repository = repository,
                wbConstants = appData.wbConstants,
                profileId = profileId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
        composable(Routes.WB) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId")?.toLongOrNull() ?: 0L
            WbScreen(
                repository = repository,
                wbConstants = appData.wbConstants,
                profileId = profileId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.TAKEOFF) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId")?.toLongOrNull() ?: 0L
            TakeoffScreen(
                repository = repository,
                performanceNormal = appData.performanceNormal,
                performanceCorrections = appData.performanceCorrections,
                profileId = profileId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SLEEPVLUCHT) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId")?.toLongOrNull() ?: 0L
            SleepvluchtScreen(
                repository = repository,
                performanceTow = appData.performanceTow,
                performanceNormal = appData.performanceNormal,
                performanceCorrections = appData.performanceCorrections,
                sailplaneTypes = appData.sailplaneTypes,
                profileId = profileId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.LANDING) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId")?.toLongOrNull() ?: 0L
            LandingScreen(
                repository = repository,
                performanceNormal = appData.performanceNormal,
                performanceCorrections = appData.performanceCorrections,
                profileId = profileId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
