package nl.schellenberg.hk36ttc.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the "Real Life Performance" hub card (Fase 4a GPS/sensor logging, see
 * `ui/reallife/`) is shown. Off by default — this is a testing/data-gathering tool for Frank's
 * own use, not a feature most pilots need to see, so it stays hidden until switched on in
 * Instellingen. The recording feature itself, its routes, and its data are never removed by
 * turning this off; it only hides the entry point on the registration hub.
 */
class RealLifePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("real_life_preferences", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
    }
}
