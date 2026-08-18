package nl.glcillustrious.hk36ttc.ui.wb

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import nl.glcillustrious.hk36ttc.R
import nl.glcillustrious.hk36ttc.core.wb.Seat
import nl.glcillustrious.hk36ttc.core.wb.WBResult
import nl.glcillustrious.hk36ttc.core.wb.WBViolation
import nl.glcillustrious.hk36ttc.core.wb.WBWarning
import nl.glcillustrious.hk36ttc.core.wb.WbConstantsData
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileRepository
import nl.glcillustrious.hk36ttc.ui.common.IntStepperField
import nl.glcillustrious.hk36ttc.ui.report.SharePdfButton
import nl.glcillustrious.hk36ttc.ui.report.WbReportLabels
import nl.glcillustrious.hk36ttc.ui.report.buildWbReport
import nl.glcillustrious.hk36ttc.ui.theme.status

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WbScreen(
    repository: AircraftProfileRepository,
    wbConstants: WbConstantsData,
    profileId: Long,
    onBack: () -> Unit
) {
    val viewModel: WbViewModel = viewModel(factory = WbViewModel.factory(repository, profileId, wbConstants))
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.profile?.registration?.let { stringResource(R.string.wb_title_format, it) }
                            ?: stringResource(R.string.wb_title_default)
                    )
                },
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
        if (state.profileNotFound) {
            Column(modifier = Modifier.padding(padding).padding(16.dp)) {
                Text(stringResource(R.string.wb_profile_not_found), color = MaterialTheme.colorScheme.error)
            }
            return@Scaffold
        }

        val maxFuelLiters = state.profile
            ?.let { wbConstants.tankCapacityLiters(it.fuelTankType).roundToInt() }
            ?: 79
        val fuelKgEquivalent = wbConstants.fuelKgFromLiters(state.fuelLiters.toDouble())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IntStepperField(
                label = stringResource(R.string.wb_pilot_label),
                value = state.pilotKg,
                onValueChange = viewModel::updatePilot,
                min = 0,
                max = 150,
                suffix = "kg"
            )
            IntStepperField(
                label = stringResource(R.string.wb_copilot_label),
                value = state.copilotKg,
                onValueChange = viewModel::updateCopilot,
                min = 0,
                max = 150,
                suffix = "kg"
            )
            IntStepperField(
                label = stringResource(R.string.wb_fuel_label),
                value = state.fuelLiters,
                onValueChange = viewModel::updateFuelLiters,
                min = 0,
                max = maxFuelLiters,
                suffix = "L",
                helperText = stringResource(
                    R.string.wb_fuel_helper_format,
                    fuelKgEquivalent.roundToInt(),
                    wbConstants.fuelDensityKgPerL.toString()
                )
            )
            IntStepperField(
                label = stringResource(R.string.wb_baggage_label),
                value = state.baggageKg,
                onValueChange = viewModel::updateBaggage,
                min = 0,
                max = 20,
                suffix = "kg"
            )

            state.result?.let { result ->
                WbResultCard(result)
            }

            val registration = state.profile?.registration
            val reportTitle = stringResource(
                R.string.report_title_format,
                stringResource(R.string.report_title_wb),
                registration ?: ""
            )
            val labels = wbReportLabels()
            val violationTexts = state.result?.violations?.map { violationText(it) }.orEmpty()
            val warningTexts = state.result?.warnings?.map { warningText(it) }.orEmpty()
            val result = state.result

            SharePdfButton(
                kind = "wb",
                registration = registration,
                enabled = result != null,
                buildDocument = { timestamp ->
                    buildWbReport(
                        title = reportTitle,
                        timestamp = timestamp,
                        labels = labels,
                        registration = registration,
                        pilotKg = state.pilotKg,
                        copilotKg = state.copilotKg,
                        fuelLiters = state.fuelLiters,
                        baggageKg = state.baggageKg,
                        result = result,
                        violationTexts = violationTexts,
                        warningTexts = warningTexts
                    )
                }
            )
        }
    }
}

/** All translated strings the W&B report needs, resolved here where `stringResource` works so
 * [buildWbReport] itself stays a pure, testable function. */
@Composable
private fun wbReportLabels() = WbReportLabels(
    registration = stringResource(R.string.report_registration_label),
    sectionInput = stringResource(R.string.report_section_input),
    sectionResult = stringResource(R.string.report_section_result),
    sectionNotes = stringResource(R.string.report_section_notes),
    pilot = stringResource(R.string.wb_pilot_label),
    copilot = stringResource(R.string.wb_copilot_label),
    fuel = stringResource(R.string.wb_fuel_label),
    baggage = stringResource(R.string.wb_baggage_label),
    totalMass = stringResource(R.string.report_wb_total_mass),
    cg = stringResource(R.string.report_wb_cg),
    marginToMtow = stringResource(R.string.report_wb_margin_mtow),
    withinEnvelope = stringResource(R.string.wb_result_ok_heading),
    warningHeading = stringResource(R.string.wb_result_warning_heading),
    footer = stringResource(R.string.report_footer)
)

@Composable
private fun violationText(violation: WBViolation): String = when (violation) {
    is WBViolation.MtowExceeded -> stringResource(
        R.string.wb_violation_mtow_exceeded_format,
        violation.totalMassKg, violation.mtowKg, violation.totalMassKg - violation.mtowKg
    )
    is WBViolation.SeatLimitExceeded -> stringResource(
        if (violation.seat == Seat.PILOT) R.string.wb_violation_seat_limit_pilot_format
        else R.string.wb_violation_seat_limit_copilot_format,
        violation.massKg, violation.maxKg
    )
    is WBViolation.BaggageLimitExceeded -> stringResource(
        R.string.wb_violation_baggage_limit_format, violation.massKg, violation.maxKg
    )
    is WBViolation.CgOutOfEnvelopeForward -> stringResource(
        R.string.wb_violation_cg_forward_format, violation.cgMm, violation.forwardLimitMm
    )
    is WBViolation.CgOutOfEnvelopeAft -> stringResource(
        R.string.wb_violation_cg_aft_format, violation.cgMm, violation.aftLimitMm
    )
}

@Composable
private fun warningText(warning: WBWarning): String = when (warning) {
    is WBWarning.TrimWeightRequired -> {
        val trimMassKg = warning.trimMassKg
        if (trimMassKg != null) {
            stringResource(
                R.string.wb_warning_trim_with_mass_format,
                warning.usefulLoadOnSeatsKg, warning.minRequiredKg, trimMassKg
            )
        } else {
            stringResource(
                R.string.wb_warning_trim_without_mass_format,
                warning.usefulLoadOnSeatsKg, warning.minRequiredKg
            )
        }
    }
}

@Composable
private fun WbResultCard(result: WBResult) {
    val statusColors = MaterialTheme.status
    val hasIssues = result.violations.isNotEmpty() || result.warnings.isNotEmpty()
    val (containerColor, contentColor, icon) = if (hasIssues) {
        Triple(statusColors.warning, statusColors.onWarning, Icons.Filled.Warning)
    } else {
        Triple(statusColors.success, statusColors.onSuccess, Icons.Filled.CheckCircle)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                Text(
                    text = if (hasIssues) stringResource(R.string.wb_result_warning_heading) else stringResource(R.string.wb_result_ok_heading),
                    modifier = Modifier.padding(start = 8.dp),
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider(color = contentColor.copy(alpha = 0.3f))

            Text(stringResource(R.string.wb_result_total_mass_format, result.totalMassKg, result.marginToMtowKg))
            Text(stringResource(R.string.wb_result_cg_format, result.cgMm, result.marginForwardMm, result.marginAftMm))

            result.violations.forEach { violation ->
                Text("• ${violationText(violation)}", fontWeight = FontWeight.Bold)
            }
            result.warnings.forEach { warning ->
                Text("• ${warningText(warning)}", fontWeight = FontWeight.Bold)
            }
        }
    }
}
