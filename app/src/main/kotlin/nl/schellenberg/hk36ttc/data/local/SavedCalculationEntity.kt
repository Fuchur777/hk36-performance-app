package nl.schellenberg.hk36ttc.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Which calculation screen a [SavedCalculationEntity] came from — matches
 * [nl.schellenberg.hk36ttc.ui.report.SharePdfButton]'s existing `kind` strings 1:1, but as a
 * proper enum since this one is persisted and needs to stay stable across app updates. */
enum class SavedCalculationType { WB, TAKEOFF, GLIDER_TOW, LANDING }

/** One "Save" tap from a calculation screen (Fase: save-instead-of-share, mirroring the Real
 * Life Performance list). [documentJson] is a [nl.schellenberg.hk36ttc.ui.report.ReportDocument]
 * serialized verbatim — the exact already-translated, already-computed report the pilot would
 * have shared at save time — so opening this list later and sharing an entry never re-runs the
 * calculation or depends on the app's current language/unit settings still matching what they
 * were when it was saved. [title]/[timestamp] are denormalized copies of the same fields inside
 * that document purely so the list row can display them without deserializing every entry just
 * to render itself. */
@Entity(tableName = "saved_calculations")
data class SavedCalculationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    /** [SavedCalculationType.name] — a plain `String` column rather than a Room enum
     * `@TypeConverter`, matching every other enum-backed column in this schema (e.g.
     * `RealLifeLogEntity.configuration`/`surfaceType`). */
    val type: String,
    val title: String,
    val timestamp: String,
    val createdAtEpochMs: Long,
    val documentJson: String
)
