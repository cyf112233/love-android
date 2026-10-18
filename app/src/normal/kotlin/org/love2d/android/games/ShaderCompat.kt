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
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * General shader compatibility cache for the old GLES engines.
 *
 * LÖVE 11.5/0.10.2 reject GLSL declarations such as
 * `uniform float inversion = 0;` because a uniform cannot have an initializer
 * in GLSL ES. LÖVE 12 compiles those shaders whereas the old GLES validator
 * does not. This creates a sanitized runtime copy of the game archive with
 * uniform initializers removed. The original `.love` is never touched.
 */
object ShaderCompat {

    private val SHADER_EXTENSIONS = listOf(".glsl", ".frag", ".vert", ".shader")

    fun prepare(context: Context, game: Game): File {
        val cacheDir = File(context.cacheDir, "shader_compat").apply { mkdirs() }
        val key = "v2-${Integer.toHexString(game.id.hashCode())}-${game.lastModified}-${game.sizeBytes}"
        val target = File(cacheDir, "$key.love")
        if (target.isFile && target.length() > 0L) return target

        var needsRewrite = false
        try {
            ZipFile(game.file).use { zip ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory) continue
                    val shaderFile = isShader(entry.name)
                    val luaFile = entry.name.endsWith(".lua", ignoreCase = true)
                    if (!shaderFile && !luaFile) continue
                    val source = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val fixed = if (shaderFile) sanitizeShader(source) else sanitizeLua(source)
                    if (fixed != source) {
                        needsRewrite = true
                        break
                    }
                }
            }
        } catch (_: Throwable) {
            return game.file
        }

        if (!needsRewrite) return game.file

        val temporary = File(cacheDir, "$key.tmp")
        try {
            ZipFile(game.file).use { zip ->
                ZipOutputStream(BufferedOutputStream(FileOutputStream(temporary))).use { output ->
                    for (entry in zip.entries()) {
                        val outEntry = ZipEntry(entry.name).apply {
                            time = entry.time
                            comment = entry.comment
                        }
                        output.putNextEntry(outEntry)
                        if (!entry.isDirectory) {
                            if (isShader(entry.name) || entry.name.endsWith(".lua", ignoreCase = true)) {
                                val source = zip.getInputStream(entry)
                                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                                val fixed = if (isShader(entry.name)) sanitizeShader(source) else sanitizeLua(source)
                                output.write(fixed.toByteArray(Charsets.UTF_8))
                            } else {
                                zip.getInputStream(entry).use { it.copyTo(output) }
                            }
                        }
                        output.closeEntry()
                    }
                }
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            cleanup(cacheDir, target.name)
            return target
        } catch (_: Throwable) {
            temporary.delete()
            return game.file
        }
    }

    /** Normalizes a standalone shader file. */
    fun sanitizeShader(source: String): String {
        var result = sanitize(source)
        if ("[](" in result && "#pragma language glsl3" !in result) {
            result = "#pragma language glsl3\n" + result
        }
        return result
    }

    /** Normalizes inline `love.graphics.newShader [[ ... ]]` blocks in Lua files. */
    fun sanitizeLua(source: String): String =
        NEW_SHADER.replace(source) { match ->
            val prefix = match.groupValues[1]
            val body = match.groupValues[2]
            val suffix = match.groupValues[3]
            prefix + sanitizeShader(body) + suffix
        }

    private val NEW_SHADER = Regex("""(newShader\s*\(?\s*\[\[)([\s\S]*?)(\]\])""")

    /** Removes initializers from `uniform` declarations, e.g. `uniform float x = 1;`. */
    fun sanitize(source: String): String {
        val output = StringBuilder(source.length)
        var cursor = 0
        while (cursor < source.length) {
            val start = source.indexOf("uniform", cursor)
            if (start < 0) {
                output.append(source, cursor, source.length)
                break
            }
            if (!isWordBoundary(source, start, 7)) {
                output.append(source, cursor, start + 7)
                cursor = start + 7
                continue
            }
            val end = source.indexOf(';', start + 7)
            if (end < 0) {
                output.append(source, cursor, source.length)
                break
            }
            output.append(source, cursor, start)
            val declaration = source.substring(start, end)
            output.append(if ('=' in declaration) stripInitializers(declaration) else declaration)
            output.append(';')
            cursor = end + 1
        }
        return output.toString()
    }

    private fun stripInitializers(declaration: String): String {
        val body = declaration.removePrefix("uniform").trim()
        val parts = splitTopLevel(body, ',')
        val cleaned = parts.map { part -> part.substringBefore('=').trimEnd() }
        return "uniform " + cleaned.joinToString(", ")
    }

    private fun splitTopLevel(value: String, separator: Char): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var start = 0
        for (index in value.indices) {
            when (value[index]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> if (depth > 0) depth--
                separator -> if (depth == 0) {
                    result.add(value.substring(start, index))
                    start = index + 1
                }
            }
        }
        result.add(value.substring(start))
        return result
    }

    private fun isWordBoundary(source: String, start: Int, length: Int): Boolean {
        val before = if (start > 0) source[start - 1] else ' '
        val after = if (start + length < source.length) source[start + length] else ' '
        return !before.isLetterOrDigit() && before != '_' &&
            !after.isLetterOrDigit() && after != '_'
    }

    private fun isShader(name: String): Boolean =
        SHADER_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }

    private fun cleanup(directory: File, keep: String) {
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        directory.listFiles()?.forEach { file ->
            if (file.name != keep && file.lastModified() < cutoff) file.delete()
        }
    }
}
