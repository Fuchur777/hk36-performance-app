package nl.glcillustrious.hk36ttc.ui.common

import nl.glcillustrious.hk36ttc.core.metar.MetarConfigData
import nl.glcillustrious.hk36ttc.data.local.AirfieldEntity
import nl.glcillustrious.hk36ttc.data.metar.MetarRepository

/**
 * Tracks which airfields have already had a quiet background METAR refresh attempted this
 * session, so a stale/no-signal field isn't re-hammered on every recalculation — the collector
 * that drives this fires on every edit, not just on opening the screen.
 *
 * Extracted because before this class existed, [nl.glcillustrious.hk36ttc.ui.perf.TakeoffViewModel]
 * was the only one of the three calculation screens that had this at all: [LandingViewModel]
 * and [SleepvluchtViewModel] never auto-refreshed a stored METAR, silently, for as long as
 * those screens existed. One shared class makes that gap structurally impossible to reopen —
 * a fourth screen gets the same behaviour by construction, not by remembering to copy the block.
 *
 * Deliberately holds no [kotlinx.coroutines.CoroutineScope] and does not launch anything itself:
 * it only answers "is this due", so it stays plain, synchronous, and trivially testable — the
 * caller's `viewModelScope.launch { … }` is left to the ViewModel, which already owns it.
 */
class MetarAutoRefresher {
    private val attemptedAirfieldIds = mutableSetOf<Long>()

    /**
     * True at most once per [AirfieldEntity.id] per instance of this class (i.e. per screen
     * visit) — and only when [MetarRepository.shouldAutoRefresh] agrees the stored report is
     * missing or old enough to be worth replacing. A caller that gets `true` back is expected to
     * actually attempt the refresh; a `false` result never becomes `true` again for that same
     * airfield without a fresh [MetarAutoRefresher] (i.e. a fresh ViewModel instance).
     */
    fun shouldAutoRefresh(airfield: AirfieldEntity?, metarConfig: MetarConfigData): Boolean {
        if (airfield == null) return false
        if (!MetarRepository.shouldAutoRefresh(airfield, metarConfig, System.currentTimeMillis())) return false
        return attemptedAirfieldIds.add(airfield.id)
    }
}
