package com.jiangdg.demo

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaRepository {
    private const val PREFS = "alyx_media_library"
    private const val KEY_RECORDS = "records"
    private const val STORAGE_PREFS = "media_storage"
    private const val KEY_MEDIA_FOLDER = "media_folder_uri"
    private val lock = Any()

    fun fileNameDate(timestamp: Long): String =
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(timestamp))

    fun displayDate(timestamp: Long): String =
        SimpleDateFormat("dd/MM/yy\nHH:mm", Locale.FRANCE).format(Date(timestamp))

    fun add(context: Context, record: MediaRecord) = synchronized(lock) {
        val records = read(context).toMutableList()
        val existing = records.indexOfFirst { it.uri == record.uri }
        if (existing >= 0) {
            records[existing] = record.copy(comment = records[existing].comment)
        } else {
            records.add(record)
        }
        write(context, records)
    }

    fun updateComment(context: Context, uri: String, comment: String) = synchronized(lock) {
        val records = read(context).map {
            if (it.uri == uri) it.copy(comment = comment.trim()) else it
        }
        write(context, records)
    }

    fun find(context: Context, uri: String): MediaRecord? = synchronized(lock) {
        read(context).firstOrNull { it.uri == uri }
    }

    fun list(context: Context): List<MediaRecord> = synchronized(lock) {
        read(context).sortedByDescending(MediaRecord::capturedAt)
    }

    /** Imports current Endoscope captures and older Alyx captures into the app gallery. */
    fun importSelectedFolder(context: Context) = synchronized(lock) {
        val folderUri = context.getSharedPreferences(STORAGE_PREFS, 0)
            .getString(KEY_MEDIA_FOLDER, null)
            ?.let(Uri::parse)
            ?: return@synchronized
        val tree = DocumentFile.fromTreeUri(context, folderUri) ?: return@synchronized
        val records = read(context).associateBy { it.uri }.toMutableMap()
        tree.listFiles().forEach { file ->
            val name = file.name ?: return@forEach
            val type = file.type ?: mimeTypeFromName(name) ?: return@forEach
            if (!file.isFile || CAPTURE_PREFIXES.none { name.startsWith(it) } ||
                (!type.startsWith("image/") && !type.startsWith("video/"))) {
                return@forEach
            }
            val uri = file.uri.toString()
            if (records.containsKey(uri)) return@forEach
            val timestamp = timestampFromName(name)?.takeIf { it > 0L }
                ?: file.lastModified().takeIf { it > 0L }
                ?: System.currentTimeMillis()
            records[uri] = MediaRecord(uri, type, timestamp, name)
        }
        write(context, records.values.toList())
    }

    private fun timestampFromName(name: String): Long? {
        Regex("^(?:Endoscope|Alyx)_(\\d{8}_\\d{6}_\\d{3})").find(name)?.groupValues?.getOrNull(1)?.let {
            return runCatching {
                SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).apply { isLenient = false }.parse(it)?.time
            }.getOrNull()
        }
        Regex("^(?:Endoscope|Alyx)_(\\d{13})").find(name)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let {
            return it
        }
        return null
    }

    private fun mimeTypeFromName(name: String): String? = when {
        name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> "image/jpeg"
        name.endsWith(".mp4", true) -> "video/mp4"
        else -> null
    }

    private fun read(context: Context): List<MediaRecord> {
        val raw = context.getSharedPreferences(PREFS, 0).getString(KEY_RECORDS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        MediaRecord(
                            uri = item.getString("uri"),
                            mimeType = item.optString("mimeType", "image/jpeg"),
                            capturedAt = item.optLong("capturedAt", 0L),
                            displayName = item.optString("displayName", "Média Endoscope"),
                            comment = item.optString("comment", ""),
                            hasEmbeddedStamp = item.optBoolean("hasEmbeddedStamp", false),
                            embeddedComment = item.optString("embeddedComment", "")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun write(context: Context, records: List<MediaRecord>) {
        val array = JSONArray()
        records.sortedByDescending(MediaRecord::capturedAt).forEach { record ->
            array.put(
                JSONObject().apply {
                    put("uri", record.uri)
                    put("mimeType", record.mimeType)
                    put("capturedAt", record.capturedAt)
                    put("displayName", record.displayName)
                    put("comment", record.comment)
                    put("hasEmbeddedStamp", record.hasEmbeddedStamp)
                    put("embeddedComment", record.embeddedComment)
                }
            )
        }
        context.getSharedPreferences(PREFS, 0).edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    private val CAPTURE_PREFIXES = listOf("Endoscope_", "Alyx_")
}
