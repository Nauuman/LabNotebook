package com.nauuman.measure.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A saved photo of measurements plus a text summary. */
data class HistoryItem(
    val id: Long,
    val timestamp: Long,
    val imageFile: String,
    val summary: List<String>,
)

/**
 * Stores captured measurement photos in app storage with a small JSON index.
 * [items] is observable from Compose and must only be mutated on the main thread.
 */
class HistoryStore(private val context: Context) {
    private val dir = File(context.filesDir, "captures").apply { mkdirs() }
    private val index = File(dir, "history.json")

    val items = mutableStateListOf<HistoryItem>().apply { addAll(load()) }

    fun fileFor(item: HistoryItem) = File(dir, item.imageFile)

    /** Writes the JPEG; safe to call off the main thread. Returns the new item. */
    fun writeCapture(bitmap: Bitmap, summary: List<String>, alsoToGallery: Boolean): HistoryItem {
        val now = System.currentTimeMillis()
        val name = "measure_$now.jpg"
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        if (alsoToGallery) saveToGallery(bitmap, name)
        return HistoryItem(now, now, name, summary)
    }

    /** Main thread only. */
    fun add(item: HistoryItem) {
        items.add(0, item)
        persist()
    }

    /** Main thread only. */
    fun delete(item: HistoryItem) {
        items.remove(item)
        fileFor(item).delete()
        persist()
    }

    private fun saveToGallery(bitmap: Bitmap, name: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return // needs storage permission; use Share instead
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Measure")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    }

    private fun load(): List<HistoryItem> = try {
        if (!index.exists()) emptyList() else {
            val arr = JSONArray(index.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val lines = o.getJSONArray("summary")
                HistoryItem(
                    id = o.getLong("id"),
                    timestamp = o.getLong("timestamp"),
                    imageFile = o.getString("image"),
                    summary = (0 until lines.length()).map { lines.getString(it) },
                )
            }.filter { File(dir, it.imageFile).exists() }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun persist() {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("timestamp", item.timestamp)
                    .put("image", item.imageFile)
                    .put("summary", JSONArray(item.summary)),
            )
        }
        index.writeText(arr.toString())
    }
}
