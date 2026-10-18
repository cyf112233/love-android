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

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import org.love2d.android.games.EngineVersion
import org.love2d.android.games.Game
import org.love2d.android.games.GameLibraryViewModel
import org.love2d.android.ui.GameLibraryScreen
import org.love2d.android.ui.theme.LoveAndroidTheme

/**
 * Launcher screen: a Miuix game library that imports `.love`, `.zip` and
 * `.exe` games and stores every one of them as a `.love` archive.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: GameLibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.install(this)
        Diagnostics.log("MainActivity.onCreate")
        enableEdgeToEdge()

        setContent {
            LoveAndroidTheme {
                GameLibraryScreen(
                    viewModel = viewModel,
                    onLaunchGame = ::launchGame,
                    onLaunchNoGame = {
                        startActivity(Intent(this, GameActivity::class.java))
                    },
                )
            }
        }
    }

    private fun launchGame(game: Game, engine: EngineVersion) {
        if (engine.isBuiltIn) {
            // GameActivity accepts both file:// URIs and content:// URIs. The
            // games folder is app-private, so a plain file URI is enough here.
            startActivity(
                Intent(this, GameActivity::class.java).setData(Uri.fromFile(game.file))
            )
            return
        }

        val targetPackage = engine.packageName ?: return
        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.enginefiles",
            game.file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/x-love-game")
            setClassName(targetPackage, "org.love2d.android.GameActivity")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("game", uri)
        }

        runCatching { startActivity(intent) }
            .onFailure {
                Diagnostics.log("engine launch failed: ${it.stackTraceToString()}")
                viewModel.showError(getString(R.string.engine_launch_failed, engine.displayName))
            }
    }
}
