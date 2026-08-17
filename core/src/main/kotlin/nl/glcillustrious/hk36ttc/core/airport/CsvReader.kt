package nl.glcillustrious.hk36ttc.core.airport

/**
 * A minimal RFC 4180 CSV reader, written specifically for the OurAirports datasets
 * (`airports.csv` / `runways.csv`, see docs/00-plan.md §13).
 *
 * `split(",")` genuinely does not work on this data: airport names routinely contain commas
 * ("Ceuta, Helipuerto de") and quotes, and every text column is quoted while every numeric one
 * is not. So this handles the three things that actually occur — quoted fields, commas inside
 * quotes, and a doubled `""` as a literal quote — plus CRLF, since the files are served with
 * Unix endings today but nothing guarantees that.
 *
 * Deliberately *not* a general-purpose CSV library: no streaming API beyond a line sequence, no
 * configurable delimiter, no type coercion. Column meaning is resolved by the caller through
 * [CsvHeader].
 */
object CsvReader {

    /**
     * Splits one CSV record into its raw field values. Surrounding quotes are removed and `""`
     * collapses to `"`; everything else is returned verbatim, including leading/trailing spaces
     * inside quotes.
     */
    fun splitLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val char = line[index]
            when {
                inQuotes && char == '"' && index + 1 < line.length && line[index + 1] == '"' -> {
                    // Doubled quote inside a quoted field is one literal quote.
                    current.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.setLength(0)
                }
                else -> current.append(char)
            }
            index++
        }
        fields.add(current.toString())
        return fields
    }
}

/**
 * Column-name to index mapping taken from a CSV's first row.
 *
 * Reading columns by name rather than position is a hard requirement here, not a nicety: the
 * real `airports.csv` does not match the published OurAirports data dictionary — `icao_code`
 * appears *before* `gps_code` in the file, and after it in the docs. A positional reader would
 * silently write GPS codes into the ICAO column. The source can also add columns at any time,
 * since it is regenerated daily.
 */
class CsvHeader private constructor(private val indexByName: Map<String, Int>) {

    /** Index of [name], or null when the column is absent from this file. */
    fun indexOf(name: String): Int? = indexByName[name]

    /**
     * The value of column [name] in [fields], or null when the column is absent, the row is
     * short, or the value is empty. Empty and missing are deliberately conflated: in this
     * dataset an empty field means "not recorded", which is exactly what a caller wants a null
     * for.
     */
    fun value(fields: List<String>, name: String): String? {
        val index = indexOf(name) ?: return null
        val raw = fields.getOrNull(index)?.trim() ?: return null
        return raw.ifEmpty { null }
    }

    companion object {
        fun parse(headerLine: String): CsvHeader {
            val names = CsvReader.splitLine(headerLine)
            // Last occurrence wins on a duplicated column name — arbitrary, but a duplicate
            // would be a source defect either way and this keeps the map total.
            return CsvHeader(names.withIndex().associate { (index, name) -> name.trim() to index })
        }
    }
}
