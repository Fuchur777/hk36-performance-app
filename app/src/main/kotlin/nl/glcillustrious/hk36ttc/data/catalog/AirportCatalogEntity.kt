package nl.glcillustrious.hk36ttc.data.catalog

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * One airport from the OurAirports catalogue — reference data, never user data.
 *
 * This lives in [AirportCatalogDatabase], a *separate* database file from the pilot's own
 * `airfields`/`runway_strips`. That separation is the structural guarantee behind the one hard
 * rule of this feature: an import or refresh physically cannot reach the pilot's runways,
 * because they are in another database.
 *
 * [ident] is OurAirports' own primary key and the join key to [RunwayCatalogEntity]; it is not
 * always an ICAO code (small fields often have only a local code). [icaoCode] is the official
 * one where it exists, and is what an existing hand-entered airfield is matched on.
 *
 * [elevationM] is nullable for the same reason as in the parser: unknown is not sea level.
 * [latitudeDeg]/[longitudeDeg] are stored for the planned nearest-airfield lookup.
 */
@Entity(
    tableName = "airport_catalog",
    indices = [
        Index("icaoCode"),
        Index("ident"),
        Index("name")
    ]
)
data class AirportCatalogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ident: String,
    val type: String,
    val name: String,
    val elevationM: Double?,
    val isoCountry: String?,
    val municipality: String?,
    val icaoCode: String?,
    val gpsCode: String?,
    val localCode: String?,
    val latitudeDeg: Double?,
    val longitudeDeg: Double?
) {
    /** What the pilot should see as "the code" for this field: official ICAO where there is
     * one, otherwise whatever identifier the source does have. */
    val displayCode: String
        get() = icaoCode ?: gpsCode ?: localCode ?: ident
}

@Dao
interface AirportCatalogDao {

    /**
     * Ranked search over 70,000+ rows, so it runs in SQL with a LIMIT rather than by filtering
     * a list in memory the way the sailplane-type screen does.
     *
     * The `CASE` orders exact code matches first, then code prefixes, then name/municipality
     * hits — docs/00-plan.md §13 asked specifically for ICAO-code search to lead.
     */
    @Query(
        """
        SELECT * FROM airport_catalog
        WHERE icaoCode = :exact OR ident = :exact
           OR icaoCode LIKE :prefix OR ident LIKE :prefix OR gpsCode LIKE :prefix OR localCode LIKE :prefix
           OR name LIKE :contains OR municipality LIKE :contains
        ORDER BY CASE
            WHEN icaoCode = :exact OR ident = :exact THEN 0
            WHEN icaoCode LIKE :prefix OR ident LIKE :prefix THEN 1
            WHEN name LIKE :prefix THEN 2
            ELSE 3
        END, name ASC
        LIMIT :limit
        """
    )
    suspend fun search(exact: String, prefix: String, contains: String, limit: Int): List<AirportCatalogEntity>

    /**
     * Finds an airport by whichever code the pilot has stored for it.
     *
     * Matching on `icaoCode` alone silently failed for most gliding sites: they have no official
     * ICAO code at all, and their identifier lives in `gps_code` or `local_code` instead. Terlet
     * is the case that exposed it — `ident` and `gpsCode` are both `EHTL` while `icaoCode` is
     * empty, so its six runways were invisible while Gilze-Rijen's (which does have an ICAO
     * code) imported fine.
     *
     * The ordering keeps a genuine ICAO match ahead of an `ident` match, and both ahead of a
     * GPS/local code, so a short local code that happens to collide with another field's real
     * ICAO code can never win.
     */
    @Query(
        """
        SELECT * FROM airport_catalog
        WHERE icaoCode = :code OR ident = :code OR gpsCode = :code OR localCode = :code
        ORDER BY CASE
            WHEN icaoCode = :code THEN 0
            WHEN ident = :code THEN 1
            ELSE 2
        END
        LIMIT 1
        """
    )
    suspend fun findByCode(code: String): AirportCatalogEntity?

    @Query("SELECT COUNT(*) FROM airport_catalog")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(airports: List<AirportCatalogEntity>)

    @Query("DELETE FROM airport_catalog")
    suspend fun clear()
}
