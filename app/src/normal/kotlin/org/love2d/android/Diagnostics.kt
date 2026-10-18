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

package org.love2d.android

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes import progress and uncaught exceptions to
 * `Android/data/<package>/files/diagnostics.log`, which is reachable without
 * logcat permissions on devices where logcat is locked down.
 */
object Diagnostics {
    private const val FILE_NAME = "diagnostics.log"

    @Volatile
    private var logFile: File? = null

    private val lock = Any()

    fun install(context: Context) {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val target = File(dir, FILE_NAME)
        logFile = target

        append(target, "===== session ${timestamp()} =====")

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            append(
                target,
                "FATAL on ${thread.name}\n${Log.getStackTraceString(error)}",
            )
            previous?.uncaughtException(thread, error)
        }
    }

    fun log(message: String) {
        val target = logFile ?: return
        append(target, message)
    }

    private fun append(target: File, message: String) {
        synchronized(lock) {
            runCatching {
                target.appendText("${timestamp()} $message\n")
            }
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
}
