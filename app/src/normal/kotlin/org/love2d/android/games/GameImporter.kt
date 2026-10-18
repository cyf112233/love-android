/*
 * Copyright (c) 2006-2024 LOVE Development Team
 *
 * This software is provided 'as-is', without any express or implied
 * warranty.  In no event will the authors be held liable for any damages
 * arising from the use of this software.
 *
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 *
 * 1. The origin of this software must not be misrepresented; you must not
 *    claim that you wrote the original software. If you use this software
 *    in a product, an acknowledgment in the product documentation would be
 *    appreciated but is not required.
 * 2. Altered source versions must be plainly marked as such, and must not be
 *    misrepresented as being the original software.
 * 3. This notice may not be removed or altered from any source distribution.
 */

package org.love2d.android.games

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.StringRes
import org.love2d.android.Diagnostics
import org.love2d.android.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Locale

/**
 * Turns a user-picked `.love`, `.zip` or `.exe` into a `.love` archive inside
 * the games folder.
 *
 * `.love` files are copied as-is, `.zip` files are repackaged under a `.love`
 * name and `.exe` files are treated as fused LÖVE executables. In practice the
 * three cases overlap a lot: a Windows build can be a polyglot file that is
 * both a PE executable and a zip archive, and it may be named `.zip`. The
 * archive payload is detected from the zip end-of-central-directory record
 * and extracted starting at its real offset, so any executable prefix (and
 * legacy filename encodings) no longer matter.
 */
class GameImporter(
    private val context: Context,
    private val repository: GameRepository,
) {

    sealed interface ImportResult {
        data class Success(val game: Game) : ImportResult

        data class Failure(
            @StringRes val reason: Int,
            val detail: String? = null,
        ) : ImportResult
    }

    fun import(uri: Uri): ImportResult {
        val displayName = runCatching { queryDisplayName(uri) }
            .getOrNull()
            ?: uri.lastPathSegment?.substringAfterLast('/')

        val extension = displayName
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.ROOT)
            .orEmpty()

        if (extension !in SUPPORTED_EXTENSIONS) {
            Diagnostics.log("import: unsupported ext='$extension' name=$displayName")
            return ImportResult.Failure(R.string.import_error_unsupported)
        }

        Diagnostics.log("import: start uri=$uri name=$displayName ext=$extension")

        val dir = try {
            repository.gamesDir()
        } catch (e: IOException) {
            Diagnostics.log("import: gamesDir failed: $e")
            return ImportResult.Failure(R.string.import_error_io, e.localizedMessage)
        }

        val tmpCopy = try {
            File.createTempFile(".import-", ".tmp", dir)
        } catch (e: IOException) {
            return ImportResult.Failure(R.string.import_error_io, e.localizedMessage)
        }

        var target: File? = null
        var completed = false
        try {
            copyUriToFile(uri, tmpCopy)
            Diagnostics.log("import: copied ${tmpCopy.length()} bytes")

            val inspection = ZipInspector.inspect(tmpCopy)
            Diagnostics.log("import: inspection=$inspection")
            if (inspection == null) {
                return ImportResult.Failure(
                    if (extension == "exe") {
                        R.string.import_error_exe_no_game
                    } else {
                        R.string.import_error_no_main_lua
                    }
                )
            }
            if (!inspection.hasMainLua) {
                return ImportResult.Failure(R.string.import_error_no_main_lua)
            }

            val finalFile = uniqueTarget(dir, baseName(displayName, uri))
            target = finalFile
            Diagnostics.log("import: target=$finalFile")

            if (inspection.dataStart > 0) {
                // The archive has an executable (or other) prefix. Extract
                // only the zip payload so the resulting .love is a normal zip.
                if (!hasRoomFor(dir, tmpCopy.length())) {
                    return ImportResult.Failure(R.string.import_error_no_space)
                }
                copyRange(tmpCopy, finalFile, inspection.dataStart)
            } else if (!tmpCopy.renameTo(finalFile)) {
                if (!hasRoomFor(dir, tmpCopy.length())) {
                    return ImportResult.Failure(R.string.import_error_no_space)
                }
                tmpCopy.inputStream().use { input ->
                    FileOutputStream(finalFile).use { output -> input.copyTo(output) }
                }
            }
            completed = true
            Diagnostics.log("import: success size=${finalFile.length()}")

            return ImportResult.Success(
                Game(
                    file = finalFile,
                    title = finalFile.name.substringBeforeLast('.', finalFile.name),
                    sizeBytes = finalFile.length(),
                    lastModified = finalFile.lastModified(),
                    isDirectory = false,
                )
            )
        } catch (e: SecurityException) {
            Diagnostics.log("import: SecurityException ${Log.getStackTraceString(e)}")
            return ImportResult.Failure(R.string.import_error_read, e.localizedMessage)
        } catch (e: IOException) {
            Diagnostics.log("import: IOException ${Log.getStackTraceString(e)}")
            return ImportResult.Failure(R.string.import_error_io, e.localizedMessage)
        } catch (e: Throwable) {
            Diagnostics.log("import: unexpected ${Log.getStackTraceString(e)}")
            return ImportResult.Failure(R.string.import_error_io, e.localizedMessage)
        } finally {
            tmpCopy.delete()
            Diagnostics.log("import: finally completed=$completed")
            // Never leave a half-written game behind.
            if (!completed) target?.delete()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    return cursor.getString(index)
                }
            }
        }

        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun copyUriToFile(uri: Uri, target: File) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open $uri")

        input.use { source ->
            FileOutputStream(target).use { output -> source.copyTo(output) }
        }
    }

    private fun baseName(displayName: String?, uri: Uri): String {
        val raw = displayName?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "game"

        return raw
            .substringBeforeLast('.', raw)
            .replace(ILLEGAL_FILE_NAME_CHARS, "_")
            .trim()
            .trimStart('.')
            .take(MAX_BASE_NAME_LENGTH)
            .ifBlank { "game" }
    }

    private fun uniqueTarget(dir: File, baseName: String): File {
        var candidate = File(dir, "$baseName${GameRepository.LOVE_EXTENSION}")
        var index = 1

        while (candidate.exists()) {
            candidate = File(dir, "$baseName ($index)${GameRepository.LOVE_EXTENSION}")
            index++
        }

        return candidate
    }

    private fun hasRoomFor(dir: File, bytes: Long): Boolean {
        val available = dir.usableSpace
        // usableSpace can be 0/unknown on some providers; don't block in that case.
        return available <= 0 || available >= bytes + FREE_SPACE_MARGIN
    }

    private fun copyRange(source: File, target: File, start: Long) {
        RandomAccessFile(source, "r").use { raf ->
            raf.seek(start)
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = raf.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    private companion object {
        val SUPPORTED_EXTENSIONS = setOf("love", "zip", "exe")
        val ILLEGAL_FILE_NAME_CHARS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

        const val MAX_BASE_NAME_LENGTH = 80
        const val FREE_SPACE_MARGIN = 8L * 1024 * 1024
    }
}
