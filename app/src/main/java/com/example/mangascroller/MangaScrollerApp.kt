package com.example.mangascroller

import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.Image
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun MangaScrollerApp(
    onPickFolder: () -> Unit
) {
    val folder = MainActivity.selectedFolder

    if (folder == null) {
        FolderPickerScreen(onPickFolder)
    } else {
        MangaReaderScreen(folder)
    }
}

@Composable
fun FolderPickerScreen(onPickFolder: () -> Unit) {
    Scaffold(
        content = {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = onPickFolder) {
                    Text("Select Manga Folder")
                }
            }
        }
    )
}

@Composable
fun MangaReaderScreen(folderUri: Uri) {

    val context = LocalContext.current
    val images = remember { mutableStateListOf<Uri>() }
    val bookmarkStore = remember { mutableStateMapOf<String, Pair<Int, Int>>() }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var autoScroll by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(3f) }

    LaunchedEffect(folderUri) {
        images.clear()

        val cacheDir = File(context.cacheDir, "manga_cache/${DocumentsContract.getTreeDocumentId(folderUri)}")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            folderUri,
            DocumentsContract.getTreeDocumentId(folderUri)
        )

        val cursor = context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null,
            null,
            null
        )

        cursor?.use {
            while (it.moveToNext()) {
                val docId = it.getString(0)
                val mime = it.getString(1) ?: ""
                val name = it.getString(2) ?: ""
                val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)

                try {
                    // If it's an image file, add directly
                    if (mime.startsWith("image/") || name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) || name.endsWith(".png", true) || name.endsWith(".webp", true)) {
                        images.add(fileUri)
                        continue
                    }

                    // If it's a CBZ/ZIP archive, extract image entries to cache and add those files
                    if (name.endsWith(".cbz", true) || name.endsWith(".zip", true)) {
                        val input = context.contentResolver.openInputStream(fileUri)
                        if (input != null) {
                            ZipInputStream(BufferedInputStream(input)).use { zip ->
                                var entry = zip.nextEntry
                                while (entry != null) {
                                    val entryName = entry.name
                                    if (!entry.isDirectory && (entryName.endsWith(".jpg", true) || entryName.endsWith(".jpeg", true) || entryName.endsWith(".png", true) || entryName.endsWith(".webp", true))) {
                                        val outFile = File(cacheDir, entryName)
                                        outFile.parentFile?.mkdirs()
                                        FileOutputStream(outFile).use { out ->
                                            zip.copyTo(out)
                                        }
                                        images.add(android.net.Uri.fromFile(outFile))
                                    }
                                    entry = zip.nextEntry
                                }
                            }
                        }
                        continue
                    }

                    // If it's a directory, attempt to enumerate its children
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val subChildren = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, docId)
                        val subCursor = context.contentResolver.query(subChildren, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                        subCursor?.use { sc ->
                            while (sc.moveToNext()) {
                                val subId = sc.getString(0)
                                val subName = sc.getString(2) ?: ""
                                val subUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, subId)
                                if (subName.endsWith(".jpg", true) || subName.endsWith(".png", true) || subName.endsWith(".jpeg", true) || subName.endsWith(".webp", true)) {
                                    images.add(subUri)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        images.sortBy { it.toString() }

        // restore last position for this chapter (in-memory)
        bookmarkStore[folderUri.toString()]?.let { (index, offset) ->
            scope.launch {
                try {
                    listState.scrollToItem(index, offset)
                } catch (_: Exception) {
                }
            }
        }
    }

    // Auto-scroll loop (pixel based) - use scrollToItem with offset increment
    LaunchedEffect(autoScroll, speed) {
        while (autoScroll && images.isNotEmpty()) {
            val index = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset + speed.toInt()
            try {
                listState.scrollToItem(index, offset)
            } catch (_: Exception) {
            }
            delay(16L)
        }
    }

    // Persist current visible position in-memory per chapter
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                bookmarkStore[folderUri.toString()] = index to offset
            }
    }

    Scaffold(
        bottomBar = {
            ControlBar(
                autoScroll = autoScroll,
                speed = speed,
                onToggle = { autoScroll = !autoScroll },
                onSpeedChange = { speed = it }
            )
        }
    ) { padding ->

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { autoScroll = !autoScroll }
                    )
                }
        ) {
            itemsIndexed(images) { _, uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun ControlBar(
    autoScroll: Boolean,
    speed: Float,
    onToggle: () -> Unit,
    onSpeedChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {

        Text(text = if (autoScroll) "Auto-Scroll: ON" else "Auto-Scroll: OFF")

        Slider(
            value = speed,
            onValueChange = onSpeedChange,
            valueRange = 1f..10f
        )

        Button(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (autoScroll) "Pause" else "Start")
        }
    }
}
