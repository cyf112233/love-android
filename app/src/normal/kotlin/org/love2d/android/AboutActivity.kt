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
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.love2d.android.ui.theme.LoveAndroidTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private const val MAIN_PROJECT_URL = "https://github.com/cyf112233/love-android"

class AboutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LoveAndroidTheme {
                AboutScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = MiuixScrollBehavior()
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    val openUrl: (String) -> Unit = { url ->
        runCatching { uriHandler.openUri(url) }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.about),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
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
            item { Spacer(Modifier.height(12.dp)) }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(R.drawable.love),
                        contentDescription = null,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(24.dp)),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.about_app_name),
                        style = MiuixTheme.textStyles.title2,
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.version_info, versionName),
                        style = MiuixTheme.textStyles.body2,
                        color = colors.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.about_description),
                        style = MiuixTheme.textStyles.body2,
                        color = colors.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            item { SmallTitle(stringResource(R.string.about_projects)) }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openUrl(MAIN_PROJECT_URL) }
                            .padding(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.about_main_project),
                                style = MiuixTheme.textStyles.main,
                                color = colors.onSurface,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Icon(
                                imageVector = MiuixIcons.Link,
                                contentDescription = null,
                                tint = colors.primary,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = MAIN_PROJECT_URL,
                            style = MiuixTheme.textStyles.footnote1,
                            color = colors.primary,
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                ) {
                    ArrowPreference(
                        title = "LÖVE",
                        summary = stringResource(R.string.about_love_summary),
                        startAction = {
                            Icon(MiuixIcons.Link, contentDescription = null, tint = colors.primary)
                        },
                        onClick = { openUrl("https://love2d.org") },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ArrowPreference(
                        title = "Miuix",
                        summary = stringResource(R.string.about_miuix_summary),
                        startAction = {
                            Icon(MiuixIcons.Link, contentDescription = null, tint = colors.primary)
                        },
                        onClick = { openUrl("https://github.com/compose-miuix-ui/miuix") },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ArrowPreference(
                        title = stringResource(R.string.about_engine_packs),
                        summary = stringResource(R.string.about_engine_packs_summary),
                        startAction = {
                            Icon(MiuixIcons.Link, contentDescription = null, tint = colors.primary)
                        },
                        onClick = { openUrl("https://github.com/love2d/love-android") },
                    )
                }
            }

            item { SmallTitle(stringResource(R.string.about_components)) }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                ) {
                    ComponentRow(
                        title = "Jetpack Compose",
                        summary = stringResource(R.string.about_compose_summary),
                        url = "https://developer.android.com/jetpack/compose",
                        openUrl = openUrl,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ComponentRow(
                        title = "Kotlin",
                        summary = stringResource(R.string.about_kotlin_summary),
                        url = "https://kotlinlang.org",
                        openUrl = openUrl,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ComponentRow(
                        title = "SDL3",
                        summary = stringResource(R.string.about_sdl_summary),
                        url = "https://github.com/libsdl-org/SDL",
                        openUrl = openUrl,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ComponentRow(
                        title = "LuaJIT",
                        summary = stringResource(R.string.about_luajit_summary),
                        url = "https://luajit.org",
                        openUrl = openUrl,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ComponentRow(
                        title = "OpenAL Soft",
                        summary = stringResource(R.string.about_openal_summary),
                        url = "https://github.com/kcat/openal-soft",
                        openUrl = openUrl,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ComponentRow(
                        title = "FreeType",
                        summary = stringResource(R.string.about_freetype_summary),
                        url = "https://freetype.org",
                        openUrl = openUrl,
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.copyright),
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ComponentRow(
    title: String,
    summary: String,
    url: String,
    openUrl: (String) -> Unit,
) {
    ArrowPreference(
        title = title,
        summary = summary,
        startAction = {
            Icon(
                imageVector = MiuixIcons.Link,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
            )
        },
        onClick = { openUrl(url) },
    )
}
