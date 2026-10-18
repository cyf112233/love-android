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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.love2d.android.touch.TouchSettingsRepository
import org.love2d.android.ui.theme.LoveAndroidTheme
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Transparent Miuix menu shown on top of the paused game. */
class GameMenuActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LoveAndroidTheme {
                GameMenuScreen(onClose = { finish() })
            }
        }
    }
}

@Composable
private fun GameMenuScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { TouchSettingsRepository(context) }
    val colors = MiuixTheme.colorScheme

    var touchEnabled by remember { mutableStateOf(repository.load().enabled) }
    var showExitDialog by remember { mutableStateOf(false) }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)) // miuix-lint:allow — dim the paused game
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose,
                    ),
            )

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(padding)
                    .padding(12.dp)
                    .fillMaxWidth(),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.game_menu_title),
                            style = MiuixTheme.textStyles.title3,
                            color = colors.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = MiuixIcons.Close,
                                contentDescription = stringResource(R.string.close),
                            )
                        }
                    }

                    SwitchPreference(
                        title = stringResource(R.string.touch_controls_enable),
                        summary = stringResource(R.string.touch_controls_enable_summary),
                        checked = touchEnabled,
                        onCheckedChange = {
                            touchEnabled = it
                            repository.setEnabled(it)
                        },
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    ArrowPreference(
                        title = stringResource(R.string.touch_controls_edit),
                        summary = stringResource(R.string.touch_controls_edit_summary),
                        startAction = {
                            Icon(MiuixIcons.Layers, contentDescription = null)
                        },
                        onClick = {
                            repository.setEnabled(true)
                            repository.setEditing(true)
                            onClose()
                        },
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    ArrowPreference(
                        title = stringResource(R.string.exit_game),
                        titleColor = BasicComponentDefaults.titleColor(color = colors.error),
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Delete,
                                contentDescription = null,
                                tint = colors.error,
                            )
                        },
                        onClick = { showExitDialog = true },
                    )
                }
            }
        }

        OverlayDialog(
            show = showExitDialog,
            title = stringResource(R.string.exit_game_confirm_title),
            summary = stringResource(R.string.exit_game_confirm_summary),
            onDismissRequest = { showExitDialog = false },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showExitDialog = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.exit_game),
                    onClick = {
                        showExitDialog = false
                        GameSession.requestExit()
                        onClose()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
