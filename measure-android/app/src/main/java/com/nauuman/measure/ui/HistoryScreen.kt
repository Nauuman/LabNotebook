package com.nauuman.measure.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.nauuman.measure.data.HistoryItem
import com.nauuman.measure.data.HistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(store: HistoryStore, onClose: () -> Unit) {
    var open by remember { mutableStateOf<HistoryItem?>(null) }

    BackHandler { if (open != null) open = null else onClose() }

    Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Header(title = "Saved measurements", onBack = onClose)
            if (store.items.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No saved measurements yet.\nTap the camera button while measuring to save a photo.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 15.sp,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(store.items, key = { it.id }) { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { open = item }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Thumbnail(store.fileFor(item), 400, Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(formatDate(item.timestamp), color = Color.White, fontWeight = FontWeight.SemiBold)
                                item.summary.take(3).forEach {
                                    Text(it, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                                }
                                if (item.summary.size > 3) {
                                    Text("+${item.summary.size - 3} more", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        open?.let { item ->
            Viewer(
                store = store,
                item = item,
                onClose = { open = null },
                onDeleted = { open = null },
            )
        }
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Viewer(store: HistoryStore, item: HistoryItem, onClose: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Header(title = formatDate(item.timestamp), onBack = onClose)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Thumbnail(store.fileFor(item), 2000, Modifier.fillMaxSize(), ContentScale.Fit)
        }
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            item.summary.forEach { Text(it, color = Color.White, fontSize = 15.sp) }
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextButton(onClick = { share(context, store.fileFor(item), item.summary) }) {
                Icon(Icons.Filled.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Share")
            }
            TextButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = Color(0xFFFF453A))
                Spacer(Modifier.width(8.dp))
                Text("Delete", color = Color(0xFFFF453A))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this measurement?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    store.delete(item)
                    onDeleted()
                }) { Text("Delete", color = Color(0xFFFF453A)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Thumbnail(file: File, maxSize: Int, modifier: Modifier, scale: ContentScale = ContentScale.Crop) {
    val bitmap by produceState<Bitmap?>(null, file, maxSize) {
        value = withContext(Dispatchers.IO) { decodeSampled(file, maxSize) }
    }
    val b = bitmap
    if (b == null) {
        Box(modifier.background(Color(0xFF1C1C1E)))
    } else {
        Image(b.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = scale)
    }
}

private fun decodeSampled(file: File, maxSize: Int): Bitmap? {
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSize || bounds.outHeight / (sample * 2) >= maxSize) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}

private fun formatDate(ts: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ts))

fun share(context: Context, file: File, summary: List<String>) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, summary.joinToString("\n"))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share measurement"))
}
