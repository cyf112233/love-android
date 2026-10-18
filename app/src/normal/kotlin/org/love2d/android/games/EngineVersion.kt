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

/**
 * LÖVE runtimes available to the launcher.
 *
 * LÖVE 12 is built into this APK. 11.5 and 0.10.2 are shipped as small engine
 * pack apps because their SDL2/Lua stacks cannot share a process with SDL3.
 * [packageName] is null for the in-process engine.
 */
enum class EngineVersion(
    val storageKey: String,
    val displayName: String,
    val packageName: String? = null,
) {
    LOVE_12("12.0", "LÖVE 12.0", packageName = null),
    LOVE_11("11.5", "LÖVE 11.5", packageName = "org.love2d.android.engine11"),
    LOVE_010("0.10.2", "LÖVE 0.10.2", packageName = "org.love2d.android.engine010"),
    ;

    val isBuiltIn: Boolean get() = packageName == null

    companion object {
        fun fromKey(key: String?): EngineVersion =
            entries.firstOrNull { it.storageKey == key } ?: LOVE_12
    }
}
