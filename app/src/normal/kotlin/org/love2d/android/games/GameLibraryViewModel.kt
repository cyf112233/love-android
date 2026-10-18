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

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.love2d.android.Diagnostics
import org.love2d.android.R
import java.io.File

data class GameLibraryUiState(
    val loading: Boolean = true,
    val games: List<Game> = emptyList(),
    val engines: Map<String, EngineVersion> = emptyMap(),
    val installedEngines: List<EngineVersion> = listOf(EngineVersion.LOVE_12),
    val importing: Boolean = false,
    /** One-shot snackbar text. */
    val message: String? = null,
    /** One-shot error dialog text. */
    val error: String? = null,
)

class GameLibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GameRepository(application)
    private val importer = GameImporter(application, repository)
    private val enginePreferences = EnginePreferences(application)
    private val gamePreferences = GamePreferences(application)

    var uiState by mutableStateOf(GameLibraryUiState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // Clean up imports that were killed by process death.
                    repository.cleanupOrphanedImportTemps()
                    repository.scan()
                }
            }
            val games = withCustomNames(result.getOrDefault(uiState.games))
            val app = getApplication<Application>()
            uiState = uiState.copy(
                loading = false,
                games = games,
                engines = engineMapFor(games),
                installedEngines = EnginePacks.installed(app),
                error = result.exceptionOrNull()?.let {
                    app.getString(
                        R.string.error_scan_failed,
                        it.localizedMessage.orEmpty(),
                    )
                },
            )
        }
    }

    fun importGame(uri: Uri) {
        if (uiState.importing) return

        viewModelScope.launch {
            try {
                Diagnostics.log("importGame: begin uri=$uri")
                uiState = uiState.copy(importing = true, error = null)

                val result = withContext(Dispatchers.IO) { importer.import(uri) }
                Diagnostics.log("importGame: result=$result")

                when (result) {
                    is GameImporter.ImportResult.Success -> {
                        val games = scanGames()
                        uiState = uiState.copy(
                            importing = false,
                            games = games,
                            engines = engineMapFor(games),
                            message = getApplication<Application>().getString(
                                R.string.import_success,
                                result.game.title,
                            ),
                        )
                    }

                    is GameImporter.ImportResult.Failure -> {
                        val app = getApplication<Application>()
                        val reason = app.getString(result.reason)
                        uiState = uiState.copy(
                            importing = false,
                            error = result.detail
                                ?.takeIf { it.isNotBlank() }
                                ?.let { "$reason\n$it" }
                                ?: reason,
                        )
                    }
                }
                Diagnostics.log("importGame: state updated")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                Diagnostics.log("importGame: UNCAUGHT ${Log.getStackTraceString(e)}")
                uiState = uiState.copy(
                    importing = false,
                    error = e.localizedMessage ?: e.toString(),
                )
            }
        }
    }

    fun deleteGame(game: Game) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) { repository.delete(game) }
            if (deleted) {
                gamePreferences.clear(game.id)
                val games = scanGames()
                uiState = uiState.copy(
                    games = games,
                    engines = engineMapFor(games),
                    message = getApplication<Application>().getString(
                        R.string.delete_success,
                        game.title,
                    ),
                )
            } else {
                uiState = uiState.copy(
                    error = getApplication<Application>().getString(R.string.delete_failed),
                )
            }
        }
    }

    fun renameGame(game: Game, newName: String) {
        val name = newName.trim().take(MAX_GAME_NAME_LENGTH)
        if (name.isEmpty()) return

        gamePreferences.setName(game.id, name)
        val games = withCustomNames(
            uiState.games.map { if (it.id == game.id) it.copy(title = name) else it }
        )
        uiState = uiState.copy(
            games = games,
            message = getApplication<Application>().getString(R.string.rename_success, name),
        )
    }

    fun gamesDirectory(): File = repository.gamesDir()

    fun engineFor(game: Game): EngineVersion {
        val engine = uiState.engines[game.id] ?: EngineVersion.LOVE_12
        return if (engine in uiState.installedEngines) engine else EngineVersion.LOVE_12
    }

    fun setEngine(game: Game, version: EngineVersion) {
        if (!EnginePacks.isInstalled(getApplication(), version)) {
            uiState = uiState.copy(
                error = getApplication<Application>().getString(
                    R.string.engine_unavailable,
                    version.displayName,
                ),
            )
            return
        }

        enginePreferences.setEngine(game.id, version)
        uiState = uiState.copy(engines = uiState.engines + (game.id to version))
    }

    private fun engineMapFor(games: List<Game>): Map<String, EngineVersion> =
        games.associate { it.id to enginePreferences.engineFor(it.id) }

    fun consumeMessage() {
        if (uiState.message != null) uiState = uiState.copy(message = null)
    }

    fun consumeError() {
        if (uiState.error != null) uiState = uiState.copy(error = null)
    }

    fun showError(message: String) {
        uiState = uiState.copy(error = message)
    }

    private suspend fun scanGames(): List<Game> = withContext(Dispatchers.IO) {
        withCustomNames(runCatching { repository.scan() }.getOrDefault(uiState.games))
    }

    private fun withCustomNames(games: List<Game>): List<Game> =
        games.map { it.copy(title = gamePreferences.nameFor(it.id) ?: it.title) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

    private companion object {
        const val MAX_GAME_NAME_LENGTH = 80
    }
}
