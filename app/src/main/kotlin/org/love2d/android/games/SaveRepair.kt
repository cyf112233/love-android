package org.love2d.android.games

import android.content.Context
import java.io.File

/**
 * Renames zero-byte JSON files under LÖVE's save directory.
 *
 * A failed write can leave an empty settings/save JSON behind. Games then fail
 * in json.decode() with "unexpected character '' at line 1 col 1". Renaming
 * the empty file lets the game create a fresh one instead of crashing.
 */
object SaveRepair {
    fun repair(context: Context): Int {
        val saveDir = File(context.getExternalFilesDir(null), "save")
        return repairDirectory(saveDir)
    }

    private fun repairDirectory(directory: File): Int {
        if (!directory.isDirectory) return 0
        var repaired = 0
        val children = directory.listFiles() ?: return 0
        for (file in children) {
            if (file.isDirectory) {
                repaired += repairDirectory(file)
                continue
            }
            if (file.length() != 0L) continue
            if (!file.name.endsWith(".json", ignoreCase = true)) continue
            val backup = File(file.parentFile, "${file.name}.empty-${System.currentTimeMillis()}.bak")
            if (file.renameTo(backup)) repaired++
        }
        return repaired
    }
}
