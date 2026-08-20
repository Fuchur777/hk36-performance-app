package nl.glcillustrious.hk36ttc.ui.common

import androidx.compose.runtime.compositionLocalOf
import nl.glcillustrious.hk36ttc.core.units.AppUnits

/**
 * The pilot's current display-unit choices, read by every numeric input field and result across
 * the app — see [AppUnits]'s KDoc for why this is a `CompositionLocal` rather than an explicit
 * parameter threaded through every screen: virtually every screen consumes it, and a change in
 * Settings must reach every one of them immediately without an Activity recreate.
 *
 * Provided once, near the navigation root in `MainActivity`, from
 * [nl.glcillustrious.hk36ttc.data.local.UnitPreferences.units]. The default here (plain
 * [AppUnits]) only matters for a `@Preview` or a composable that somehow renders outside that
 * provider — every real screen always has a live value supplied.
 */
val LocalAppUnits = compositionLocalOf { AppUnits() }
