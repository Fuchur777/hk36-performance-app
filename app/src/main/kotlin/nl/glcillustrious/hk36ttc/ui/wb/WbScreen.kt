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
import nl.glcillustrious.hk36ttc.core.units.CgPositionUnit
import nl.glcillustrious.hk36ttc.core.units.MassUnit
import nl.glcillustrious.hk36ttc.core.wb.Seat
import nl.glcillustrious.hk36ttc.core.wb.WBResult
import nl.glcillustrious.hk36ttc.core.wb.WBViolation
import nl.glcillustrious.hk36ttc.core.wb.WBWarning
import nl.glcillustrious.hk36ttc.core.wb.WbConstantsData
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileRepository
import nl.glcillustrious.hk36ttc.ui.common.IntStepperField
import nl.glcillustrious.hk36ttc.ui.common.LocalAppUnits
import nl.glcillustrious.hk36ttc.ui.common.cgPositionSuffix
import nl.glcillustrious.hk36ttc.ui.common.displayCgPosition
import nl.glcillustrious.hk36ttc.ui.common.displayFuelDensity
import nl.glcillustrious.hk36ttc.ui.common.displayFuelVolume
import nl.glcillustrious.hk36ttc.ui.common.displayMass
import nl.glcillustrious.hk36ttc.ui.common.fuelVolumeSuffix
import nl.glcillustrious.hk36ttc.ui.common.massSuffix
import nl.glcillustrious.hk36ttc.ui.common.nativeFuelVolumeLitersInt
import nl.glcillustrious.hk36ttc.ui.common.nativeMassKgInt
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

        val units = LocalAppUnits.current
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
                value = displayMass(state.pilotKg, units.mass),
                onValueChange = { v -> viewModel.updatePilot(nativeMassKgInt(v, units.mass)) },
                min = 0,
                max = displayMass(150, units.mass),
                suffix = massSuffix(units.mass)
            )
            IntStepperField(
                label = stringResource(R.string.wb_copilot_label),
                value = displayMass(state.copilotKg, units.mass),
                onValueChange = { v -> viewModel.updateCopilot(nativeMassKgInt(v, units.mass)) },
                min = 0,
                max = displayMass(150, units.mass),
                suffix = massSuffix(units.mass)
            )
            IntStepperField(
                label = stringResource(R.string.wb_fuel_label),
                value = displayFuelVolume(state.fuelLiters, units.fuelVolume),
                onValueChange = { v -> viewModel.updateFuelLiters(nativeFuelVolumeLitersInt(v, units.fuelVolume)) },
                min = 0,
                max = displayFuelVolume(maxFuelLiters, units.fuelVolume),
                suffix = fuelVolumeSuffix(units.fuelVolume),
                helperText = stringResource(
                    R.string.wb_fuel_helper_format,
                    displayMass(fuelKgEquivalent, units.mass),
                    massSuffix(units.mass),
                    displayFuelDensity(wbConstants.fuelDensityKgPerL, units.mass, units.fuelVolume),
                    fuelVolumeSuffix(units.fuelVolume)
                )
            )
            IntStepperField(
                label = stringResource(R.string.wb_baggage_label),
                value = displayMass(state.baggageKg, units.mass),
                onValueChange = { v -> viewModel.updateBaggage(nativeMassKgInt(v, units.mass)) },
                min = 0,
                max = displayMass(20, units.mass),
                suffix = massSuffix(units.mass)
            )

            state.result?.let { result ->
                WbResultCard(result, units.mass, units.cgPosition)
            }

            val registration = state.profile?.registration
            val reportTitle = stringResource(
                R.string.report_title_format,
                stringResource(R.string.report_title_wb),
                registration ?: ""
            )
            val labels = wbReportLabels()
            val violationTexts = state.result?.violations?.map { violationText(it, units.mass, units.cgPosition) }.orEmpty()
            val warningTexts = state.result?.warnings?.map { warningText(it, units.mass) }.orEmpty()
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
                        pilotDisplay = displayMass(state.pilotKg, units.mass),
                        copilotDisplay = displayMass(state.copilotKg, units.mass),
                        fuelDisplay = displayFuelVolume(state.fuelLiters, units.fuelVolume),
                        baggageDisplay = displayMass(state.baggageKg, units.mass),
                        massSuffix = massSuffix(units.mass),
                        fuelSuffix = fuelVolumeSuffix(units.fuelVolume),
                        cgSuffix = cgPositionSuffix(units.cgPosition),
                        result = result,
                        totalMassDisplay = result?.let { displayMass(it.totalMassKg, units.mass) },
                        cgPositionDisplay = result?.let { displayCgPosition(it.cgMm, units.cgPosition) },
                        marginToMtowDisplay = result?.let { displayMass(it.marginToMtowKg, units.mass) },
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

/** Converts each violation's kg/mm figures to [massUnit]/[cgUnit] before formatting — both
 * sides of a CG-vs-limit comparison always go through the same conversion call, so a forward or
 * aft envelope breach reads consistently regardless of which unit the pilot has chosen. */
@Composable
private fun violationText(violation: WBViolation, massUnit: MassUnit, cgUnit: CgPositionUnit): String = when (violation) {
    is WBViolation.MtowExceeded -> {
        val total = displayMass(violation.totalMassKg, massUnit)
        val mtow = displayMass(violation.mtowKg, massUnit)
        stringResource(R.string.wb_violation_mtow_exceeded_format, total, mtow, total - mtow, massSuffix(massUnit))
    }
    is WBViolation.SeatLimitExceeded -> stringResource(
        if (violation.seat == Seat.PILOT) R.string.wb_violation_seat_limit_pilot_format
        else R.string.wb_violation_seat_limit_copilot_format,
        displayMass(violation.massKg, massUnit), displayMass(violation.maxKg, massUnit), massSuffix(massUnit)
    )
    is WBViolation.BaggageLimitExceeded -> stringResource(
        R.string.wb_violation_baggage_limit_format,
        displayMass(violation.massKg, massUnit), displayMass(violation.maxKg, massUnit), massSuffix(massUnit)
    )
    is WBViolation.CgOutOfEnvelopeForward -> stringResource(
        R.string.wb_violation_cg_forward_format,
        displayCgPosition(violation.cgMm, cgUnit), displayCgPosition(violation.forwardLimitMm, cgUnit), cgPositionSuffix(cgUnit)
    )
    is WBViolation.CgOutOfEnvelopeAft -> stringResource(
        R.string.wb_violation_cg_aft_format,
        displayCgPosition(violation.cgMm, cgUnit), displayCgPosition(violation.aftLimitMm, cgUnit), cgPositionSuffix(cgUnit)
    )
}

@Composable
private fun warningText(warning: WBWarning, massUnit: MassUnit): String = when (warning) {
    is WBWarning.TrimWeightRequired -> {
        val trimMassKg = warning.trimMassKg
        if (trimMassKg != null) {
            stringResource(
                R.string.wb_warning_trim_with_mass_format,
                displayMass(warning.usefulLoadOnSeatsKg, massUnit),
                displayMass(warning.minRequiredKg, massUnit),
                displayMass(trimMassKg, massUnit),
                massSuffix(massUnit)
            )
        } else {
            stringResource(
                R.string.wb_warning_trim_without_mass_format,
                displayMass(warning.usefulLoadOnSeatsKg, massUnit),
                displayMass(warning.minRequiredKg, massUnit),
                massSuffix(massUnit)
            )
        }
    }
}

@Composable
private fun WbResultCard(result: WBResult, massUnit: MassUnit, cgUnit: CgPositionUnit) {
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

            Text(
                stringResource(
                    R.string.wb_result_total_mass_format,
                    displayMass(result.totalMassKg, massUnit),
                    displayMass(result.marginToMtowKg, massUnit),
                    massSuffix(massUnit)
                )
            )
            Text(
                stringResource(
                    R.string.wb_result_cg_format,
                    displayCgPosition(result.cgMm, cgUnit),
                    displayCgPosition(result.marginForwardMm, cgUnit),
                    displayCgPosition(result.marginAftMm, cgUnit),
                    cgPositionSuffix(cgUnit)
                )
            )

            result.violations.forEach { violation ->
                Text("• ${violationText(violation, massUnit, cgUnit)}", fontWeight = FontWeight.Bold)
            }
            result.warnings.forEach { warning ->
                Text("• ${warningText(warning, massUnit)}", fontWeight = FontWeight.Bold)
            }
        }
    }
}
