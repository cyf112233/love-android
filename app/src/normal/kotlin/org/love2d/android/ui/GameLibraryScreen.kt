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

package org.love2d.android.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.love2d.android.AboutActivity
import org.love2d.android.R
import org.love2d.android.games.EngineVersion
import org.love2d.android.games.Game
import org.love2d.android.games.GameLibraryViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.util.Date

/**
 * Android's document picker filters by MIME type, not by file extension, and
 * `.exe` is reported with a different MIME by different providers (or hidden
 * entirely when a specific MIME list is used). Ask for every file and validate
 * the extension in [GameImporter] instead; otherwise `.exe` files are simply
 * not selectable even though `.so` (octet-stream) is.
 */
private val IMPORT_MIME_TYPES = arrayOf("*/*")

/**
 * Main screen: imports games and lists everything that can be launched.
 *
 * All overlays live inside the [Scaffold] and outside the [LazyColumn] so they
 * survive item recycling.
 */
@Composable
fun GameLibraryScreen(
    viewModel: GameLibraryViewModel,
    onLaunchGame: (Game, EngineVersion) -> Unit,
    onLaunchNoGame: () -> Unit,
) {
    val state = viewModel.uiState
    val colors = MiuixTheme.colorScheme
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = MiuixScrollBehavior()

    var showMore by remember { mutableStateOf(false) }
    var renameGame by remember { mutableStateOf<Game?>(null) }
    var showGameFolder by remember { mutableStateOf(false) }
    var actionGame by remember { mutableStateOf<Game?>(null) }
    var infoGame by remember { mutableStateOf<Game?>(null) }
    var engineGame by remember { mutableStateOf<Game?>(null) }
    var pendingDelete by remember { mutableStateOf<Game?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importGame(uri)
        }
    }

    val noPickerMessage = stringResource(R.string.import_error_no_picker)
    val openImportPicker = {
        try {
            importLauncher.launch(IMPORT_MIME_TYPES)
        } catch (_: Exception) {
            viewModel.showError(noPickerMessage)
        }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.library_title),
                largeTitle = stringResource(R.string.library_title),
                subtitle = stringResource(R.string.library_subtitle),
                actions = {
                    IconButton(onClick = openImportPicker) {
                        Icon(
                            imageVector = MiuixIcons.Add,
                            contentDescription = stringResource(R.string.import_game),
                        )
                    }
                    IconButton(onClick = { showMore = true }) {
                        Icon(
                            imageVector = MiuixIcons.More,
                            contentDescription = stringResource(R.string.more),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .scrollEndHaptic()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding(),
            ),
            overscrollEffect = null,
        ) {
            item { Spacer(Modifier.size(12.dp)) }

            when {
                state.loading -> item {
                    FullScreenState {
                        InfiniteProgressIndicator(color = colors.primary)
                    }
                }

                state.games.isEmpty() -> item {
                    EmptyLibrary(onImport = openImportPicker)
                }

                else -> {
                    item(key = "import_card") {
                        ImportCard(onImport = openImportPicker)
                    }
                    item(key = "games_title") {
                        SmallTitle(stringResource(R.string.my_games, state.games.size))
                    }
                    items(state.games, key = { it.id }) { game ->
                        GameCard(
                            game = game,
                            onClick = { onLaunchGame(game, viewModel.engineFor(game)) },
                            onMore = { actionGame = game },
                        )
                    }
                }
            }

            item { Spacer(Modifier.size(12.dp)) }
        }

        // --- Overlays -------------------------------------------------------

        OverlayDialog(
            show = state.importing,
            title = stringResource(R.string.importing_title),
            summary = stringResource(R.string.importing_summary),
            onDismissRequest = null,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                InfiniteProgressIndicator(color = colors.primary)
            }
        }

        OverlayDialog(
            show = state.error != null,
            title = stringResource(R.string.error_title),
            summary = state.error,
            onDismissRequest = viewModel::consumeError,
        ) {
            TextButton(
                text = stringResource(R.string.ok),
                onClick = viewModel::consumeError,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }

        pendingDelete?.let { game ->
            OverlayDialog(
                show = true,
                title = stringResource(R.string.delete_confirm_title, game.title),
                summary = stringResource(R.string.delete_confirm_summary),
                onDismissRequest = { pendingDelete = null },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = { pendingDelete = null },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.delete),
                        onClick = {
                            viewModel.deleteGame(game)
                            pendingDelete = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }

        actionGame?.let { game ->
            val engine = viewModel.engineFor(game)
            OverlayBottomSheet(
                show = true,
                title = game.title,
                onDismissRequest = { actionGame = null },
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    ArrowPreference(
                        title = stringResource(R.string.engine_version),
                        summary = engine.displayName,
                        startAction = { Icon(MiuixIcons.Layers, contentDescription = null) },
                        onClick = {
                            actionGame = null
                            engineGame = game
                        },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.rename_game),
                        startAction = { Icon(MiuixIcons.Edit, contentDescription = null) },
                        onClick = {
                            actionGame = null
                            renameGame = game
                        },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.game_info),
                        startAction = { Icon(MiuixIcons.Info, contentDescription = null) },
                        onClick = {
                            actionGame = null
                            infoGame = game
                        },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.delete_game),
                        titleColor = BasicComponentDefaults.titleColor(color = colors.error),
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Delete,
                                contentDescription = null,
                                tint = colors.error,
                            )
                        },
                        onClick = {
                            actionGame = null
                            pendingDelete = game
                        },
                    )
                }
            }
        }

        renameGame?.let { game ->
            RenameGameDialog(
                game = game,
                onDismiss = { renameGame = null },
                onRename = { name ->
                    viewModel.renameGame(game, name)
                    renameGame = null
                },
            )
        }

        infoGame?.let { game ->
            GameInfoSheet(game = game, onDismiss = { infoGame = null })
        }

        engineGame?.let { game ->
            EnginePickerSheet(
                game = game,
                selected = viewModel.engineFor(game),
                installed = state.installedEngines,
                onSelect = { version ->
                    viewModel.setEngine(game, version)
                    engineGame = null
                },
                onDismiss = { engineGame = null },
            )
        }

        OverlayBottomSheet(
            show = showMore,
            title = stringResource(R.string.more),
            onDismissRequest = { showMore = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                ArrowPreference(
                    title = stringResource(R.string.refresh),
                    startAction = { Icon(MiuixIcons.Refresh, contentDescription = null) },
                    onClick = {
                        showMore = false
                        viewModel.refresh()
                    },
                )
                ArrowPreference(
                    title = stringResource(R.string.launch_nogame),
                    startAction = { Icon(MiuixIcons.Play, contentDescription = null) },
                    onClick = {
                        showMore = false
                        onLaunchNoGame()
                    },
                )
                ArrowPreference(
                    title = stringResource(R.string.game_folder),
                    startAction = { Icon(MiuixIcons.Folder, contentDescription = null) },
                    onClick = {
                        showMore = false
                        showGameFolder = true
                    },
                )
                ArrowPreference(
                    title = stringResource(R.string.about),
                    startAction = { Icon(MiuixIcons.Info, contentDescription = null) },
                    onClick = {
                        showMore = false
                        context.startActivity(Intent(context, AboutActivity::class.java))
                    },
                )
            }
        }

        if (showGameFolder) {
            GameFolderDialog(
                path = remember {
                    runCatching { viewModel.gamesDirectory().absolutePath }.getOrDefault("")
                },
                onDismiss = { showGameFolder = false },
            )
        }
    }
}

/** Full-viewport centered state, used for loading and the empty library. */
@Composable
private fun LazyItemScope.FullScreenState(content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
private fun LazyItemScope.EmptyLibrary(onImport: () -> Unit) {
    val colors = MiuixTheme.colorScheme

    FullScreenState {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(colors.tertiaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MiuixIcons.File,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(42.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.no_games),
            style = MiuixTheme.textStyles.title3,
            color = colors.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.no_games_info),
            style = MiuixTheme.textStyles.footnote1,
            color = colors.onBackgroundVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onImport,
            minWidth = 160.dp,
            colors = ButtonDefaults.buttonColors(
                color = colors.primary,
                contentColor = colors.onPrimary,
            ),
        ) {
            Icon(
                imageVector = MiuixIcons.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.onPrimary,
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.import_game))
        }
    }
}

@Composable
private fun ImportCard(onImport: () -> Unit) {
    val colors = MiuixTheme.colorScheme

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        onClick = onImport,
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .squircleSurface(colors.tertiaryContainer, 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Import,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.import_game),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.import_hint),
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceVariantSummary,
                )
            }
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = null,
                tint = colors.onSurfaceVariantSummary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun GameCard(
    game: Game,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val context = LocalContext.current
    val summary = remember(game, context) { gameSummary(context, game) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        onClick = onClick,
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        BasicComponent(
            title = game.title,
            summary = summary,
            startAction = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .squircleSurface(colors.tertiaryContainer, 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Play,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
            endActions = {
                IconButton(onClick = onMore) {
                    Icon(
                        imageVector = MiuixIcons.More,
                        contentDescription = stringResource(R.string.more),
                    )
                }
            },
        )
    }
}

@Composable
private fun EnginePickerSheet(
    game: Game,
    selected: EngineVersion,
    installed: List<EngineVersion>,
    onSelect: (EngineVersion) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayBottomSheet(
        show = true,
        title = "${stringResource(R.string.engine_version)} · ${game.title}",
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            installed.forEach { version ->
                RadioButtonPreference(
                    title = version.displayName,
                    summary = when {
                        version == selected -> stringResource(R.string.engine_current)
                        version.isBuiltIn -> stringResource(R.string.engine_built_in)
                        else -> stringResource(R.string.engine_pack)
                    },
                    selected = version == selected,
                    onClick = { onSelect(version) },
                )
            }
        }
    }
}

@Composable
private fun GameInfoSheet(game: Game, onDismiss: () -> Unit) {
    val context = LocalContext.current

    OverlayBottomSheet(
        show = true,
        title = game.title,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            InfoRow(
                label = stringResource(R.string.game_info_type),
                value = stringResource(
                    if (game.isDirectory) R.string.game_type_folder else R.string.game_type_love
                ),
            )
            InfoRow(
                label = stringResource(R.string.game_info_size),
                value = if (game.isDirectory) {
                    "—"
                } else {
                    Formatter.formatShortFileSize(context, game.sizeBytes)
                },
            )
            InfoRow(
                label = stringResource(R.string.game_info_modified),
                value = DateFormat.getDateFormat(context).format(Date(game.lastModified)),
            )
            InfoRow(
                label = stringResource(R.string.game_info_path),
                value = game.file.absolutePath,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = MiuixTheme.colorScheme

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = colors.onSurfaceVariantSummary,
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body1,
            color = colors.onSurface,
        )
    }
}

@Composable
private fun RenameGameDialog(
    game: Game,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember(game.id) { mutableStateOf(game.title) }
    var showEmptyError by remember(game.id) { mutableStateOf(false) }
    val colors = MiuixTheme.colorScheme

    OverlayDialog(
        show = true,
        title = stringResource(R.string.rename_game),
        summary = stringResource(R.string.rename_game_summary),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            TextField(
                value = name,
                onValueChange = {
                    name = it.take(80)
                    showEmptyError = false
                },
                label = stringResource(R.string.rename_game_label),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (showEmptyError) {
                Text(
                    text = stringResource(R.string.rename_game_empty),
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.error,
                    modifier = Modifier.padding(start = 16.dp, top = 6.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.save),
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isEmpty()) {
                            showEmptyError = true
                        } else {
                            onRename(trimmed)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun GameFolderDialog(path: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val location = stringResource(R.string.game_folder_location, context.packageName)
    val accessibility = stringResource(
        if (Build.VERSION.SDK_INT >= 30) {
            R.string.game_folder_inaccessible
        } else {
            R.string.game_folder_accessible
        }
    )
    val message = listOf(location, accessibility, path).joinToString("\n\n")

    OverlayDialog(
        show = true,
        title = stringResource(R.string.game_folder),
        summary = message,
        onDismissRequest = onDismiss,
    ) {
        TextButton(
            text = stringResource(R.string.ok),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}

private fun gameSummary(context: Context, game: Game): String {
    val size = if (game.isDirectory) {
        context.getString(R.string.game_type_folder)
    } else {
        Formatter.formatShortFileSize(context, game.sizeBytes)
    }
    val date = DateFormat.getDateFormat(context).format(Date(game.lastModified))
    return "$size · $date"
}
