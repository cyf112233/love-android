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

import java.io.File
import java.io.RandomAccessFile

/**
 * Minimal, lenient zip reader used for validation.
 *
 * It deliberately does not use [java.util.zip.ZipFile] because some game
 * packages are polyglot files and/or contain filenames in a legacy encoding:
 *
 *  - LÖVE fusing concatenates a `.love` archive to an executable. The zip
 *    payload then starts at a non-zero offset (a PE header in front).
 *  - Windows-packaged games may store filenames as GBK. `java.util.zip`
 *    rejects such central directory entries, even though LÖVE/PhysFS can
 *    still read them.
 *
 * We only need to know two things: where the archive starts and whether it
 * has `main.lua` at its root. Entry names are compared as raw bytes, so the
 * filename encoding does not matter.
 */
object ZipInspector {

    data class Inspection(
        /** Offset of the zip payload inside the file. */
        val dataStart: Long,
        /** True when the archive root contains a `main.lua` entry. */
        val hasMainLua: Boolean,
    )

    /**
     * Returns null when [file] is not a readable zip archive (or a zip with a
     * prefix, such as a fused executable).
     */
    fun inspect(file: File): Inspection? {
        val length = file.length()
        if (length < EOCD_SIZE) return null

        RandomAccessFile(file, "r").use { raf ->
            val tailLength = minOf(length, EOCD_SIZE.toLong() + MAX_ZIP_COMMENT).toInt()
            val tail = ByteArray(tailLength)
            raf.seek(length - tailLength)
            raf.readFully(tail)

            for (offset in tailLength - EOCD_SIZE downTo 0) {
                if (tail.readUInt32(offset) != EOCD_SIGNATURE) continue

                val commentLength = tail.readUInt16(offset + 20)
                if (offset + EOCD_SIZE + commentLength > tailLength) continue

                val centralDirectorySize = tail.readUInt32(offset + 12)
                val centralDirectoryOffset = tail.readUInt32(offset + 16)
                if (centralDirectorySize == UINT32_MAX ||
                    centralDirectoryOffset == UINT32_MAX ||
                    centralDirectorySize > MAX_CENTRAL_DIRECTORY_SIZE
                ) {
                    // Zip64 or implausibly large central directory.
                    continue
                }

                val eocdPosition = length - tailLength + offset
                val dataStart = eocdPosition - centralDirectorySize - centralDirectoryOffset
                if (dataStart < 0 || dataStart >= eocdPosition) continue

                // The payload must begin with a local file header (or be an
                // empty archive). This rejects accidental EOCD signatures.
                if (!hasSignatureAt(raf, dataStart, LOCAL_FILE_HEADER_SIGNATURE) &&
                    !hasSignatureAt(raf, dataStart, EOCD_SIGNATURE)
                ) {
                    continue
                }

                val centralDirectoryStart = dataStart + centralDirectoryOffset
                if (centralDirectoryStart + centralDirectorySize > length) continue

                val hasMainLua = scanCentralDirectory(
                    raf = raf,
                    start = centralDirectoryStart,
                    size = centralDirectorySize.toInt(),
                ) ?: continue

                return Inspection(dataStart, hasMainLua)
            }
        }

        return null
    }

    /** Returns null when the central directory is malformed. */
    private fun scanCentralDirectory(
        raf: RandomAccessFile,
        start: Long,
        size: Int,
    ): Boolean? {
        if (size < CENTRAL_DIRECTORY_HEADER_SIZE) return false

        val directory = ByteArray(size)
        raf.seek(start)
        raf.readFully(directory)

        var position = 0
        while (position + CENTRAL_DIRECTORY_HEADER_SIZE <= directory.size) {
            if (directory.readUInt32(position) != CENTRAL_DIRECTORY_SIGNATURE) return null

            val nameLength = directory.readUInt16(position + 28)
            val extraLength = directory.readUInt16(position + 30)
            val commentLength = directory.readUInt16(position + 32)
            val entryEnd = position + CENTRAL_DIRECTORY_HEADER_SIZE +
                nameLength + extraLength + commentLength
            if (entryEnd > directory.size) return null

            if (nameLength == MAIN_LUA_NAME.size) {
                var matches = true
                for (index in MAIN_LUA_NAME.indices) {
                    if (directory[position + CENTRAL_DIRECTORY_HEADER_SIZE + index] != MAIN_LUA_NAME[index]) {
                        matches = false
                        break
                    }
                }
                if (matches) return true
            }

            position = entryEnd
        }

        return false
    }

    private fun hasSignatureAt(raf: RandomAccessFile, offset: Long, signature: Long): Boolean {
        if (offset < 0 || offset + 4 > raf.length()) return false
        raf.seek(offset)
        val bytes = ByteArray(4)
        raf.readFully(bytes)
        return bytes.readUInt32(0) == signature
    }

    private fun ByteArray.readUInt16(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.readUInt32(offset: Int): Long =
        (this[offset].toLong() and 0xFF) or
            ((this[offset + 1].toLong() and 0xFF) shl 8) or
            ((this[offset + 2].toLong() and 0xFF) shl 16) or
            ((this[offset + 3].toLong() and 0xFF) shl 24)

    private val MAIN_LUA_NAME = "main.lua".toByteArray(Charsets.US_ASCII)

    private const val EOCD_SIZE = 22
    private const val MAX_ZIP_COMMENT = 0xFFFF
    private const val CENTRAL_DIRECTORY_HEADER_SIZE = 46

    private const val EOCD_SIGNATURE = 0x06054B50L
    private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014B50L
    private const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034B50L
    private const val UINT32_MAX = 0xFFFFFFFFL

    /** Sanity limit: 64 MiB of central directory (≈ hundreds of thousands of entries). */
    private const val MAX_CENTRAL_DIRECTORY_SIZE = 64L * 1024 * 1024
}
