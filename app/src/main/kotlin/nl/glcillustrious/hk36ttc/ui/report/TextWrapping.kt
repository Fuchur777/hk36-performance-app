package nl.glcillustrious.hk36ttc.ui.report

/**
 * Line-breaking for [PdfRenderer], kept apart from it so the algorithm can be tested.
 *
 * The measuring is injected rather than done here: the real caller measures with
 * `android.graphics.Paint.breakText`, which only exists on a device, and it was exactly that
 * un-testable coupling that let the original "no wrapping at all" bug ship unnoticed — the raw
 * METAR in every report ran straight off the page edge, silently clipped.
 */
object TextWrapping {

    /**
     * Splits [text] into lines that each fit the available width.
     *
     * [charsThatFit] answers "how many leading characters of this string fit?" — the exact
     * contract of `Paint.breakText`. Breaks at spaces where possible; a single token longer
     * than the line (a long METAR group, a URL) is broken mid-word rather than allowed to
     * overflow, since overflowing is invisible and therefore worse.
     */
    fun wrap(text: String, charsThatFit: (String) -> Int): List<String> {
        if (text.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        var remaining = text
        while (remaining.isNotEmpty()) {
            // Guarantees progress: a zero from an impossibly narrow column would otherwise loop
            // forever.
            var count = charsThatFit(remaining).coerceAtLeast(1)
            if (count < remaining.length) {
                val lastSpace = remaining.lastIndexOf(' ', count - 1)
                if (lastSpace > 0) count = lastSpace + 1
            }
            lines += remaining.substring(0, count).trimEnd()
            remaining = remaining.substring(count).trimStart()
        }
        return lines
    }
}
