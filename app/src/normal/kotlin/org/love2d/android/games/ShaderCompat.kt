package org.love2d.android.games

import android.content.Context
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * General GLSL -> GLSL ES compatibility cache for the old GLES engines.
 *
 * The original game archive is never modified. A cached runtime copy is made
 * only when a shader needs normalization. Rules are syntax based, not game
 * specific: illegal uniform initializers are removed, and shaders using array
 * constructors are switched to GLSL ES 3 with `#pragma language glsl3`.
 */
object ShaderCompat {
    private val SHADER_EXTENSIONS = listOf(".glsl", ".frag", ".vert", ".shader")
    private val NEW_SHADER = Regex("""(newShader\s*\(?\s*\[\[)([\s\S]*?)(\]\])""")

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
                    val text = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val fixed = if (shaderFile) sanitizeShader(text) else sanitizeLua(text)
                    if (fixed != text) { needsRewrite = true; break }
                }
            }
        } catch (_: Throwable) { return game.file }

        if (!needsRewrite) return game.file

        val temporary = File(cacheDir, "$key.tmp")
        try {
            ZipFile(game.file).use { zip ->
                ZipOutputStream(BufferedOutputStream(FileOutputStream(temporary))).use { output ->
                    for (entry in zip.entries()) {
                        output.putNextEntry(ZipEntry(entry.name).apply {
                            time = entry.time
                            comment = entry.comment
                        })
                        if (!entry.isDirectory) {
                            val shaderFile = isShader(entry.name)
                            val luaFile = entry.name.endsWith(".lua", ignoreCase = true)
                            if (shaderFile || luaFile) {
                                val text = zip.getInputStream(entry)
                                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                                output.write(
                                    (if (shaderFile) sanitizeShader(text) else sanitizeLua(text))
                                        .toByteArray(Charsets.UTF_8)
                                )
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

    fun sanitizeShader(source: String): String {
        var result = sanitizeUniforms(source)
        if ("[](" in result && "#pragma language glsl3" !in result) {
            result = "#pragma language glsl3\n" + result
        }
        return result
    }

    fun sanitizeLua(source: String): String = NEW_SHADER.replace(source) { match ->
        match.groupValues[1] + sanitizeShader(match.groupValues[2]) + match.groupValues[3]
    }

    private fun sanitizeUniforms(source: String): String {
        val output = StringBuilder(source.length)
        var cursor = 0
        while (cursor < source.length) {
            val start = source.indexOf("uniform", cursor)
            if (start < 0) { output.append(source, cursor, source.length); break }
            if (!wordBoundary(source, start, 7)) {
                output.append(source, cursor, start + 7); cursor = start + 7; continue
            }
            val end = source.indexOf(';', start + 7)
            if (end < 0) { output.append(source, cursor, source.length); break }
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
        return "uniform " + parts.map { it.substringBefore('=').trimEnd() }.joinToString(", ")
    }

    private fun splitTopLevel(value: String, separator: Char): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var start = 0
        for (i in value.indices) {
            when (value[i]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> if (depth > 0) depth--
                separator -> if (depth == 0) { result.add(value.substring(start, i)); start = i + 1 }
            }
        }
        result.add(value.substring(start))
        return result
    }

    private fun wordBoundary(source: String, start: Int, length: Int): Boolean {
        val before = if (start > 0) source[start - 1] else ' '
        val after = if (start + length < source.length) source[start + length] else ' '
        return !before.isLetterOrDigit() && before != '_' &&
            !after.isLetterOrDigit() && after != '_'
    }

    private fun isShader(name: String) = SHADER_EXTENSIONS.any { name.endsWith(it, true) }

    private fun cleanup(directory: File, keep: String) {
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        directory.listFiles()?.forEach { if (it.name != keep && it.lastModified() < cutoff) it.delete() }
    }
}
