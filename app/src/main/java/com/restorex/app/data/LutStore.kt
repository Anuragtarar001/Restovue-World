package com.restorex.app.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.restorex.app.domain.model.ImportedLut
import com.restorex.app.domain.model.LutSelection
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LutStore(private val resolver: ContentResolver, filesDir: File) {
    private val directory = File(filesDir, "luts")
    private val cache = ConcurrentHashMap<String, Pair<Long, CubeLut>>()

    suspend fun import(uri: Uri, displayName: String?): ImportedLut = withContext(Dispatchers.IO) {
        val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Unable to read LUT file")
        CubeLutParser.parse(text)
        directory.mkdirs()
        val resolvedName = displayName ?: resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        val baseName = resolvedName.orEmpty().substringBeforeLast('.').ifBlank { "Imported LUT" }
            .replace(Regex("[^A-Za-z0-9 _.-]"), "_").take(60)
        val id = UUID.randomUUID().toString()
        val file = File(directory, "${id}__${baseName}.cube")
        file.writeText(text)
        ImportedLut(id, baseName, file.absolutePath)
    }

    suspend fun list(): List<ImportedLut> = withContext(Dispatchers.IO) {
        if (!directory.isDirectory) return@withContext emptyList()
        directory.listFiles { file -> file.isFile && file.extension.equals("cube", true) }
            .orEmpty().sortedBy { it.name }.mapNotNull { file ->
                runCatching {
                    CubeLutParser.parse(file.readText())
                    val stored = file.nameWithoutExtension
                    ImportedLut(stored.substringBefore("__"), stored.substringAfter("__", "Imported LUT"), file.absolutePath)
                }.getOrNull()
            }
    }

    suspend fun delete(lut: ImportedLut): Boolean = withContext(Dispatchers.IO) {
        val file = File(lut.path).canonicalFile
        val parent = directory.canonicalFile
        if (file.parentFile != parent || file.extension.lowercase() != "cube") return@withContext false
        cache.remove(file.absolutePath)
        !file.exists() || file.delete()
    }

    fun load(selection: LutSelection?): CubeLut? {
        val path = selection?.path ?: return null
        val file = File(path)
        if (!file.isFile) return null
        val cached = cache[path]
        if (cached != null && cached.first == file.lastModified()) return cached.second
        return runCatching { CubeLutParser.parse(file.readText()) }.getOrNull()?.also {
            cache[path] = file.lastModified() to it
        }
    }
}
