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

/** Per-game display names, keyed by the absolute game path. */
class GamePreferences(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences("game_names", Context.MODE_PRIVATE)

    fun nameFor(gameId: String): String? =
        preferences.getString(key(gameId), null)?.takeIf { it.isNotBlank() }

    fun setName(gameId: String, name: String) {
        preferences.edit().putString(key(gameId), name).apply()
    }

    fun clear(gameId: String) {
        preferences.edit().remove(key(gameId)).apply()
    }

    private fun key(gameId: String): String = "name:$gameId"
}
