package com.aod.pomodromo.data.media

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import timber.log.Timber

/**
 * The Media Gate (DEVELOPMENT-PLAN.md §4.4): validates user-picked files before use.
 *
 * - MIME comes from [ContentResolver.getType], never the file extension.
 * - Images additionally pass a magic-byte sniff (JPEG/PNG/WebP).
 * - Size caps prevent memory-exhaustion bombs; decode dimensions are bounded
 *   separately at the Coil call site.
 */
object PickedMediaPolicy {

    const val MAX_IMAGE_BYTES = 25L * 1024 * 1024
    const val MAX_AUDIO_BYTES = 100L * 1024 * 1024

    private val IMAGE_MIMES = setOf("image/jpeg", "image/png", "image/webp")
    private val AUDIO_MIMES = setOf(
        "audio/mpeg", "audio/ogg", "audio/wav", "audio/x-wav",
        "audio/mp4", "audio/x-m4a", "audio/flac", "audio/aac",
    )

    sealed class Result {
        data object Ok : Result()
        data class Rejected(val reason: String) : Result()
    }

    fun validateImage(resolver: ContentResolver, uri: Uri): Result {
        val mime = resolver.getType(uri) ?: return Result.Rejected("unknown type")
        if (mime !in IMAGE_MIMES) return Result.Rejected("mime $mime not allowed")
        val size = querySize(resolver, uri)
        if (size != null && size > MAX_IMAGE_BYTES) return Result.Rejected("image too large")
        if (!sniffImageMagic(resolver, uri, mime)) return Result.Rejected("magic bytes mismatch")
        return Result.Ok
    }

    fun validateAudio(resolver: ContentResolver, uri: Uri): Result {
        val mime = resolver.getType(uri) ?: return Result.Rejected("unknown type")
        if (mime !in AUDIO_MIMES) return Result.Rejected("mime $mime not allowed")
        val size = querySize(resolver, uri)
        if (size != null && size > MAX_AUDIO_BYTES) return Result.Rejected("audio too large")
        return Result.Ok
    }

    private fun querySize(resolver: ContentResolver, uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    }.onFailure { Timber.w(it, "size query failed") }.getOrNull()

    private fun sniffImageMagic(resolver: ContentResolver, uri: Uri, mime: String): Boolean =
        runCatching {
            resolver.openInputStream(uri)?.use { input ->
                val head = ByteArray(12)
                val read = input.read(head)
                if (read < 4) return@use false
                when (mime) {
                    "image/jpeg" -> head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte()
                    "image/png" -> head[0] == 0x89.toByte() && head[1] == 0x50.toByte() &&
                        head[2] == 0x4E.toByte() && head[3] == 0x47.toByte()
                    "image/webp" -> read >= 12 &&
                        head[0] == 'R'.code.toByte() && head[1] == 'I'.code.toByte() &&
                        head[2] == 'F'.code.toByte() && head[3] == 'F'.code.toByte() &&
                        head[8] == 'W'.code.toByte() && head[9] == 'E'.code.toByte() &&
                        head[10] == 'B'.code.toByte() && head[11] == 'P'.code.toByte()
                    else -> false
                }
            } ?: false
        }.getOrElse {
            Timber.w(it, "magic sniff failed")
            false
        }
}
