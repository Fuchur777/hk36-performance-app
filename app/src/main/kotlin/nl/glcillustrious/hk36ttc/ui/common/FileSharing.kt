package nl.glcillustrious.hk36ttc.ui.common

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Writes a generated file into the app's cache and hands it to the Android share sheet.
 *
 * Shared by both export features — a calculation PDF and the user-data JSON backup — because
 * the mechanics are identical and the `FileProvider` may only serve one whitelisted directory
 * (see `res/xml/file_paths.xml`).
 *
 * The share sheet rather than a "save as" picker is a deliberate choice: it covers *both*
 * outcomes a pilot wants — "Opslaan in Bestanden" is one of its entries, alongside mailing the
 * calculation to an instructor.
 */
object FileSharing {

    private const val SUBDIR = "exports"

    /** Matches the `android:authorities` in AndroidManifest.xml. */
    private fun authority(context: Context) = "${context.packageName}.fileprovider"

    /**
     * Timestamp suffix for generated filenames, e.g. `2026-08-17_1432`. Deliberately sortable
     * and free of characters that trip up file managers or mail clients.
     */
    fun fileTimestamp(now: LocalDateTime = LocalDateTime.now()): String =
        now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"))

    /**
     * How long a shared file is left alone before it is eligible for cleanup. The receiving app
     * reads the `content://` URI on its own schedule — Gmail, for one, opens it when the message
     * is actually sent, which can be minutes after the share sheet closed. An earlier version
     * deleted *every* previous export on each new one, which could pull the file out from under
     * a mail still sitting in the outbox.
     */
    private const val KEEP_ALIVE_MS = 24 * 60 * 60 * 1000L

    /**
     * Writes [fileName] via [write] and returns it.
     *
     * Exports older than [KEEP_ALIVE_MS] are cleared out first so the cache doesn't grow by one
     * file per share forever, but recent ones are left in place — see above for why. Android
     * clears the whole cache directory under storage pressure anyway, so nothing here leaks
     * permanently.
     */
    fun writeToCache(context: Context, fileName: String, write: (OutputStream) -> Unit): File {
        val dir = File(context.cacheDir, SUBDIR).apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - KEEP_ALIVE_MS
        dir.listFiles()?.forEach { existing ->
            if (existing.name != fileName && existing.lastModified() < cutoff) existing.delete()
        }
        val file = File(dir, fileName)
        file.outputStream().use(write)
        return file
    }

    /**
     * Opens the share sheet for a file previously produced by [writeToCache].
     *
     * `FLAG_GRANT_READ_URI_PERMISSION` is what actually lets the chosen app open it — the
     * provider itself is not exported, so without the grant every receiver would see a
     * permission denial.
     */
    fun share(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    /** Convenience for the common case: generate, then immediately offer to share. */
    fun writeAndShare(
        context: Context,
        fileName: String,
        mimeType: String,
        chooserTitle: String,
        write: (OutputStream) -> Unit
    ) {
        share(context, writeToCache(context, fileName, write), mimeType, chooserTitle)
    }

    const val MIME_PDF = "application/pdf"
    const val MIME_JSON = "application/json"
}
