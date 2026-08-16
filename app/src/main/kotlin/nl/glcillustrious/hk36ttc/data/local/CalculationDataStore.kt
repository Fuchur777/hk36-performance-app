package nl.glcillustrious.hk36ttc.data.local

import android.content.Context
import java.io.File
import java.io.IOException
import nl.glcillustrious.hk36ttc.core.metar.MetarConfigData
import nl.glcillustrious.hk36ttc.core.metar.parseMetarConfigData
import nl.glcillustrious.hk36ttc.core.perf.PerformanceCorrectionsData
import nl.glcillustrious.hk36ttc.core.perf.PerformanceNormalData
import nl.glcillustrious.hk36ttc.core.perf.PerformanceTowData
import nl.glcillustrious.hk36ttc.core.perf.SailplaneTypesData
import nl.glcillustrious.hk36ttc.core.perf.parsePerformanceCorrectionsData
import nl.glcillustrious.hk36ttc.core.perf.parsePerformanceNormalData
import nl.glcillustrious.hk36ttc.core.perf.parsePerformanceTowData
import nl.glcillustrious.hk36ttc.core.perf.parseSailplaneTypesData
import nl.glcillustrious.hk36ttc.core.wb.WbConstantsData
import nl.glcillustrious.hk36ttc.core.wb.parseWbConstants

/** All on-device calculation JSON files, loaded together so the app has one loading/error
 * gate instead of one per file. */
data class AppCalculationData(
    val wbConstants: WbConstantsData,
    val performanceNormal: PerformanceNormalData,
    val performanceTow: PerformanceTowData,
    val performanceCorrections: PerformanceCorrectionsData,
    val sailplaneTypes: SailplaneTypesData,
    val metarConfig: MetarConfigData
)

/**
 * Result of loading calculation data from the on-device files — deliberately not a plain
 * nullable/throw, because a parse failure here is a safety-relevant condition (W&B/
 * performance limits) that the UI must surface and let the user recover from, never silently
 * paper over with a hardcoded fallback.
 */
sealed interface CalculationDataResult {
    data class Success(val data: AppCalculationData) : CalculationDataResult
    data class Failure(val message: String) : CalculationDataResult
}

/**
 * Owns the on-device copy of the calculation JSON files. The app never bakes these values
 * into Kotlin: on first launch it copies the bundled assets (the "assets/data" folder, itself
 * a copy of "docs/data") into app-external storage, then reads exclusively from those copies
 * from then on. That lets the club hand-edit any of the files with a JSON editor (a new AFM
 * revision, a corrected trim table, a corrected performance grid, and so on) and just restart
 * the app; no rebuild needed.
 */
class CalculationDataStore(private val context: Context) {

    /** Absolute path shown to the user so they know where to point a file manager / JSON editor. */
    fun dataDirPath(): String = dataDir().absolutePath

    fun loadAll(): CalculationDataResult = try {
        CalculationDataResult.Success(
            AppCalculationData(
                wbConstants = loadJson(FILENAME_WB_CONSTANTS, ASSET_WB_CONSTANTS, ::parseWbConstants),
                performanceNormal = loadJson(FILENAME_PERF_NORMAL, ASSET_PERF_NORMAL, ::parsePerformanceNormalData),
                performanceTow = loadJson(FILENAME_PERF_TOW, ASSET_PERF_TOW, ::parsePerformanceTowData),
                performanceCorrections = loadJson(FILENAME_PERF_CORRECTIONS, ASSET_PERF_CORRECTIONS, ::parsePerformanceCorrectionsData),
                sailplaneTypes = loadJson(FILENAME_SAILPLANE_TYPES, ASSET_SAILPLANE_TYPES, ::parseSailplaneTypesData),
                metarConfig = loadJson(FILENAME_METAR_CONFIG, ASSET_METAR_CONFIG, ::parseMetarConfigData)
            )
        )
    } catch (e: Exception) {
        CalculationDataResult.Failure(readableError(e))
    }

    /** Overwrites all on-device files with their bundled defaults, for when one has been corrupted. */
    fun resetAllToDefaults(): CalculationDataResult = try {
        copyAsset(ASSET_WB_CONSTANTS, File(dataDir(), FILENAME_WB_CONSTANTS))
        copyAsset(ASSET_PERF_NORMAL, File(dataDir(), FILENAME_PERF_NORMAL))
        copyAsset(ASSET_PERF_TOW, File(dataDir(), FILENAME_PERF_TOW))
        copyAsset(ASSET_PERF_CORRECTIONS, File(dataDir(), FILENAME_PERF_CORRECTIONS))
        copyAsset(ASSET_SAILPLANE_TYPES, File(dataDir(), FILENAME_SAILPLANE_TYPES))
        copyAsset(ASSET_METAR_CONFIG, File(dataDir(), FILENAME_METAR_CONFIG))
        loadAll()
    } catch (e: Exception) {
        CalculationDataResult.Failure(readableError(e))
    }

    private fun <T> loadJson(fileName: String, assetPath: String, parse: (String) -> T): T {
        val file = File(dataDir(), fileName)
        seedFromAssetIfMissing(file, assetPath)
        return parse(file.readText())
    }

    private fun dataDir(): File {
        val externalDir = context.getExternalFilesDir(null)
            ?: throw IOException("Externe opslag is niet beschikbaar op dit toestel.")
        return File(externalDir, "data").apply { mkdirs() }
    }

    private fun seedFromAssetIfMissing(target: File, assetPath: String) {
        if (!target.exists()) copyAsset(assetPath, target)
    }

    private fun copyAsset(assetPath: String, target: File) {
        context.assets.open(assetPath).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun readableError(e: Exception): String =
        "Kan rekengegevens niet laden uit ${dataDir().absolutePath}: " +
            (e.message ?: e::class.simpleName ?: "onbekende fout")

    private companion object {
        const val ASSET_WB_CONSTANTS = "data/weight_balance_constants.json"
        const val FILENAME_WB_CONSTANTS = "weight_balance_constants.json"
        const val ASSET_PERF_NORMAL = "data/performance_normal.json"
        const val FILENAME_PERF_NORMAL = "performance_normal.json"
        const val ASSET_PERF_TOW = "data/performance_tow.json"
        const val FILENAME_PERF_TOW = "performance_tow.json"
        const val ASSET_PERF_CORRECTIONS = "data/performance_corrections.json"
        const val FILENAME_PERF_CORRECTIONS = "performance_corrections.json"
        const val ASSET_SAILPLANE_TYPES = "data/sailplane_types.json"
        const val FILENAME_SAILPLANE_TYPES = "sailplane_types.json"
        const val ASSET_METAR_CONFIG = "data/metar_config.json"
        const val FILENAME_METAR_CONFIG = "metar_config.json"
    }
}
