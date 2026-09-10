package com.example.llmtest.llm

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a `.litertlm` model file straight to app-private storage.
 *
 * The destination must live under app-private storage (`context.getExternalFilesDir(null)` or
 * `context.filesDir`), never a shared path like `Environment.getExternalStorageDirectory()` and
 * never a content URI - LiteRT-LM memory-maps the model file directly rather than reading it
 * through a stream.
 */
object ModelDownloader {

    private const val MAX_REDIRECTS = 5
    private const val BUFFER_SIZE = 1 shl 20 // 1 MiB
    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 30_000

    /**
     * Downloads [url] into [destination]. If a file of exactly [expectedSizeBytes] is already at
     * [destination], the download is skipped entirely.
     *
     * Streams directly from the network to disk in fixed-size chunks; the full payload is never
     * held in memory. [onProgress] may be invoked from a background thread.
     */
    @Throws(IOException::class)
    fun download(
        url: String,
        destination: File,
        expectedSizeBytes: Long,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ) {
        if (destination.exists() && destination.length() == expectedSizeBytes) {
            onProgress(expectedSizeBytes, expectedSizeBytes)
            return
        }

        val tmpFile = File(destination.parentFile, "${destination.name}.download")
        val connection = openConnectionFollowingRedirects(url)
        try {
            val totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: expectedSizeBytes

            val usableSpace = destination.parentFile?.usableSpace ?: Long.MAX_VALUE
            if (usableSpace < totalBytes) {
                throw IOException(
                    "Not enough free storage: need ~${totalBytes / 1_000_000} MB, " +
                        "have ~${usableSpace / 1_000_000} MB free"
                )
            }

            connection.inputStream.use { input ->
                tmpFile.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesDownloaded = 0L
                    onProgress(0L, totalBytes)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesDownloaded += read
                        onProgress(bytesDownloaded, totalBytes)
                    }
                }
            }
        } catch (e: Exception) {
            tmpFile.delete()
            throw e
        } finally {
            connection.disconnect()
        }

        if (!tmpFile.renameTo(destination)) {
            throw IOException("Failed to move downloaded model into place at ${destination.absolutePath}")
        }
    }

    /**
     * `HttpURLConnection` does not follow redirects across hosts (e.g. huggingface.co -> its CDN),
     * which is exactly what downloading a model file requires, so redirects are followed manually.
     */
    private fun openConnectionFollowingRedirects(startUrl: String): HttpURLConnection {
        var currentUrl = startUrl
        repeat(MAX_REDIRECTS) {
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
            }
            when (connection.responseCode) {
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_SEE_OTHER,
                307,
                308 -> {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    currentUrl = location ?: throw IOException("Redirect with no Location header")
                }
                HttpURLConnection.HTTP_OK -> return connection
                else -> {
                    val code = connection.responseCode
                    connection.disconnect()
                    throw IOException("Unexpected HTTP $code while downloading $currentUrl")
                }
            }
        }
        throw IOException("Too many redirects while downloading model")
    }
}
