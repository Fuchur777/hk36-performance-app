package nl.schellenberg.hk36ttc.data.local

import android.content.Context

/** "nl" / "en" to force a language regardless of device locale, or null to follow the
 * device's own language setting — see rekenlogica.md-adjacent 00-plan.md for why this exists
 * as a user-facing override rather than pure auto-detection. */
class LanguagePreference(context: Context) {
    private val prefs = context.getSharedPreferences("language_preference", Context.MODE_PRIVATE)

    fun get(): String? = prefs.getString(KEY_LANGUAGE, null)

    fun set(languageTag: String?) {
        prefs.edit().apply {
            if (languageTag == null) remove(KEY_LANGUAGE) else putString(KEY_LANGUAGE, languageTag)
        }.apply()
    }

    private companion object {
        const val KEY_LANGUAGE = "language_tag"
    }
}
