package com.mirkwood.rutinify.domain.importer

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * `rutinify://import?v=1&d=<base64url(deflate(JSON))>` (R-19.4, D-75): the plan travels inside the link or its QR, with
 * no server (R-19.10).
 */
object PlanLink {

    const val PREFIX = "rutinify://import?"

    /** Byte capacity of a version 40 QR code with error correction M (R-19.5). */
    const val MAX_QR_BYTES = 2_331

    /** A decoded plan larger than the accepted input is rejected without inflating further (R-3.20). */
    private const val MAX_JSON_BYTES = 100 * 1024

    sealed interface Encoded {
        data class Ok(val link: String) : Encoded

        /** R-19.5: too big for one QR code; share it as text instead. */
        data class TooLarge(val bytes: Int) : Encoded
    }

    fun encode(json: String): Encoded {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        val compressed = try {
            deflater.setInput(json.toByteArray(Charsets.UTF_8))
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER)
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
            out.toByteArray()
        } finally {
            deflater.end()
        }
        val link = PREFIX + "v=1&d=" + Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
        val bytes = link.toByteArray(Charsets.UTF_8).size
        return if (bytes > MAX_QR_BYTES) Encoded.TooLarge(bytes) else Encoded.Ok(link)
    }

    /** True for anything that claims to be a Rutinify import link, valid or not. */
    fun isLink(text: String): Boolean = text.trim().startsWith(PREFIX)

    /** The JSON inside [link], or null if it is not a Rutinify link or is damaged (R-19.8). */
    fun decode(link: String): String? {
        val text = link.trim()
        if (!text.startsWith(PREFIX)) return null
        val params = text.removePrefix(PREFIX).split('&').mapNotNull { part ->
            part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }.toMap()
        if (params["v"] != "1") return null
        val data = params["d"] ?: return null
        val compressed = try {
            Base64.getUrlDecoder().decode(data)
        } catch (e: IllegalArgumentException) {
            return null
        }
        val inflater = Inflater(true)
        return try {
            inflater.setInput(compressed)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER)
            while (!inflater.finished()) {
                val read = inflater.inflate(buffer)
                if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) return null
                out.write(buffer, 0, read)
                if (out.size() > MAX_JSON_BYTES) return null
            }
            out.toString(Charsets.UTF_8.name()).takeIf { it.isNotBlank() }
        } catch (e: DataFormatException) {
            null
        } finally {
            inflater.end()
        }
    }

    private const val BUFFER = 4096
}
