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
import java.io.File
import java.io.IOException

/**
 * Reads the games folder that LÖVE uses on Android
 * (`Android/data/<package>/files/games`).
 */
class GameRepository(private val context: Context) {

    /** The games folder, created on demand. */
    @Throws(IOException::class)
    fun gamesDir(): File {
        val dir = context.getExternalFilesDir("games")
            ?: File(context.filesDir, "games")

        if (!dir.isDirectory && !dir.mkdirs()) {
            throw IOException("Unable to create ${dir.absolutePath}")
        }

        return dir
    }

    /**
     * Deletes temporary files left behind by imports that were killed before
     * they could clean up (process death, force stop, ...). Safe to call on
     * startup, before any import can be running.
     */
    fun cleanupOrphanedImportTemps() {
        val dir = runCatching { gamesDir() }.getOrNull() ?: return
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith(IMPORT_TEMP_PREFIX)) {
                file.delete()
            }
        }
    }

    /**
     * Lists launchable games. A game is either
     *  - a `.love` archive, or
     *  - an unpacked directory containing `main.lua` (legacy layout).
     */
    @Throws(IOException::class)
    fun scan(): List<Game> {
        val files = gamesDir().listFiles() ?: return emptyList()
        val now = System.currentTimeMillis()

        return files.mapNotNull { file ->
            if (file.isFile && file.name.startsWith(IMPORT_TEMP_PREFIX) &&
                now - file.lastModified() > STALE_TEMP_AGE_MS
            ) {
                // Leftovers from an import that was killed mid-way.
                file.delete()
                return@mapNotNull null
            }

            when {
                file.isDirectory ->
                    if (File(file, "main.lua").isFile) file.toGame(isDirectory = true) else null

                file.isFile && file.name.endsWith(LOVE_EXTENSION, ignoreCase = true) ->
                    file.toGame(isDirectory = false)

                else -> null
            }
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    fun delete(game: Game): Boolean = game.file.deleteRecursively()

    private fun File.toGame(isDirectory: Boolean): Game {
        val displayName = if (isDirectory) {
            name
        } else {
            name.substringBeforeLast('.', name)
        }

        return Game(
            file = this,
            title = displayName.ifBlank { name },
            sizeBytes = if (isDirectory) 0L else length(),
            lastModified = lastModified(),
            isDirectory = isDirectory,
        )
    }

    companion object {
        const val LOVE_EXTENSION = ".love"
        private const val IMPORT_TEMP_PREFIX = ".import-"
        private const val STALE_TEMP_AGE_MS = 24 * 60 * 60 * 1000L
    }
}
