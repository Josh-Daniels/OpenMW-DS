@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS", "DEPRECATION")

package org.openmw.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.os.FileObserver
import android.os.storage.StorageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.openmw.Constants
import org.openmw.EngineActivity
import org.openmw.R
import org.openmw.ui.controls.UIStateManager
import org.openmw.ui.controls.UIStateManager.updateButtonState
import org.openmw.ui.controls.UIStateManager.userUI
import org.openmw.ui.view.addCustomLog
import org.openmw.utils.GameFilesPreferences.getExtensionAllowedToEdit
import java.io.File
import java.io.FileOutputStream
import java.util.Base64

enum class FileBrowserMode {
    FILE, FOLDER
}

fun findFilesWithExtensions(directory: DocumentFile?, extensions: Array<String>): List<DocumentFile> {
    val lowerCaseExtensions = extensions.map { it.lowercase() }
    return directory?.listFiles()?.filter { file ->
        val fileName = file.name ?: return@filter false
        val fileExtension = fileName.substringAfterLast(".", "").lowercase()
        lowerCaseExtensions.contains(fileExtension)
    } ?: emptyList()
}

@Composable
fun InitialDirectorySelection(
    onSelect: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val newFeatureEnabledChecked by GameFilesPreferences.loadNewFeatureEnabledState(context).collectAsState(initial = false)
    Dialog(onDismissRequest = { onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.wrapContentHeight()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Select Initial Directory", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(16.dp))

                // Button for context.filesDir
                Button(onClick = { onSelect(context.filesDir) }) {
                    Text(text = "Internal Storage")
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Button for External Storage
                Button(onClick = { onSelect(File(Constants.USER_FILE_STORAGE)) }) {
                    Text(text = "External Storage")
                }
                if (newFeatureEnabledChecked) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { onSelect(context.filesDir.parentFile) }) {
                        Text(text = "Root App Storage")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { onSelect(File(Constants.SECOND_USER_FILE_STORAGE)) }) {
                        Text(text = "ExternalFilesDir")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { onSelect(File(context.applicationInfo.nativeLibraryDir)) }) {
                        Text(text = "Native Library Dir")
                    }
                }
            }
        }
    }
}

@Composable
fun FileBrowser(directory: File, onFileClick: (File) -> Unit, onNavigate: (File) -> Unit) {
    val files = directory.listFiles()?.toList() ?: listOf()
    val sortedFiles = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    val imageExtensions = listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "apng")

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(sortedFiles) { file ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        if (file.isDirectory) onNavigate(file) else onFileClick(file)
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (file.isDirectory) {
                    Image(
                        painter = painterResource(id = R.drawable.folder_24dp_e8eaed_fill0_wght400_grad0_opsz24),
                        contentDescription = "Folder",
                        modifier = Modifier
                            .padding(4.dp)
                            .size(24.dp),
                        colorFilter = ColorFilter.tint(Color.White)
                    )
                } else {
                    if (file.extension.lowercase() in imageExtensions) {
                        AsyncImage(
                            model = file,
                            contentDescription = file.name,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                            //colorFilter = ColorFilter.tint(Color.White)
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.description_24dp_e8eaed_fill0_wght400_grad0_opsz24),
                            contentDescription = "File",
                            modifier = Modifier
                                .padding(4.dp)
                                .size(24.dp),
                            //colorFilter = ColorFilter.tint(Color.White)
                        )
                    }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(file.name, style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp), maxLines = 2, color = Color.White)
                    if (!file.isDirectory) {
                        Text(text = formatFileSize(file.length()), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), maxLines = 1, modifier = Modifier.alpha(0.8f), color = Color.White)
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

@SuppressLint("DefaultLocale")
fun formatFileSize(size: Long): String {
    val kb = 1024
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        size >= gb -> String.format("%.2f GB", size.toFloat() / gb)
        size >= mb -> String.format("%.2f MB", size.toFloat() / mb)
        size >= kb -> String.format("%.2f KB", size.toFloat() / kb)
        else -> String.format("%d B", size)
    }
}

fun copyAndRenameImage(context: Context, sourceUri: Uri, destinationDir: File, buttonId: Int): File? {
    val contentResolver = context.contentResolver
    val inputStream = contentResolver.openInputStream(sourceUri) ?: return null

    try {
        val isGif = contentResolver.getType(sourceUri) == "image/gif" ||
                sourceUri.toString().endsWith(".gif", ignoreCase = true)

        val destinationFile = if (isGif) {
            File(destinationDir, "$buttonId.gif")
        } else {
            File(destinationDir, "$buttonId.png")
        }

        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }

        if (isGif) {
            inputStream.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            FileOutputStream(destinationFile).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            bitmap.recycle()
        }

        return destinationFile
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    } finally {
        inputStream.close()
    }
}

@Composable
fun CustomIconPickerButton(
    context: Context,
    buttonId: Int,
    buttonUri: MutableState<Uri?>,
    containerWidth: Float,
    containerHeight: Float
) {
    var showFileBrowser by remember { mutableStateOf(false) }
    val buttonStates by UIStateManager.buttonStates.collectAsState()
    val buttonState = buttonStates[buttonId]

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val buttonAction = if (buttonState?.uri == null) {
            {
                showFileBrowser = true
            }
        } else {
            {
                buttonState.uri.let { uri ->
                    val file = File(uri.path ?: "")
                    if (file.exists()) {
                        file.delete()
                        addCustomLog(
                            "FileBrowser.kt line 265 = $file.absolutePath",
                            textSize = 10,
                            textColor = Color.Cyan
                        )
                        Log.d("ImageDelete", "Image deleted from: ${file.absolutePath}")
                    }
                }
                buttonUri.value = null
                val updatedState = buttonState.copy(uri = null)
                updateButtonState(buttonId, updatedState)
                UIStateManager.saveButtonState(containerWidth, containerHeight)
            }
        }

        val buttonText = if (buttonState?.uri == null) "Choose Icon (png, jpg, gif, apng)" else "Remove Icon"
        val buttonTextColor = if (buttonState?.uri == null) Color.White else Color.Red

        Button(
            onClick = {
                buttonAction()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(202, 165, 96),
                contentColor = Color.White,
                disabledContainerColor = Color.Gray,
                disabledContentColor = Color.White.copy(alpha = 0.5f)
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp,
                pressedElevation = 8.dp,
                disabledElevation = 0.dp
            )
        ) {
            Text(buttonText, color = buttonTextColor)
            addCustomLog(
                "FileBrowser.kt line 280 = $buttonText",
                textSize = 10,
                textColor = Color.Cyan
            )
        }

        buttonState?.uri?.let { uri ->
            val painter: Painter = if (uri.toString().endsWith(".gif", ignoreCase = true)) {
                rememberAsyncImagePainter(
                    ImageRequest.Builder(context)
                        .data(uri)
                        .decoderFactory(GifDecoder.Factory())
                        .build()
                )
            } else {
                rememberAsyncImagePainter(uri)
            }

            Image(
                painter = painter,
                contentDescription = "Selected Image",
                modifier = Modifier
                    .size(100.dp)
                    .padding(8.dp)
            )
        }
    }
    BackHandler(showFileBrowser) {
        showFileBrowser = false
    }
    if (showFileBrowser) {
        FileBrowserPopup(
            onDismiss = { showFileBrowser = false },
            onFileSelected = { file ->
                val destinationDir = File(userUI)
                if (!destinationDir.exists()) {
                    destinationDir.mkdirs()
                }
                val copiedFile = copyAndRenameImage(context, Uri.fromFile(file), destinationDir, buttonId)
                addCustomLog(
                    "FileBrowser.kt line 307 $copiedFile",
                    textSize = 10,
                    textColor = Color.Cyan
                )
                if (copiedFile != null) {
                    addCustomLog(
                        "FileBrowser.kt line 309 = Image copied to: ${copiedFile.absolutePath}",
                        textSize = 10,
                        textColor = Color.Cyan
                    )
                    Log.d("ImageCopy", "Image copied to: ${copiedFile.absolutePath}")
                    UIStateManager.saveImageUri(buttonId, copiedFile.absolutePath.toUri())
                    buttonUri.value = copiedFile.absolutePath.toUri()
                    val updatedState = UIStateManager.buttonStates.value[buttonId]?.copy(uri = buttonUri.value)
                    updatedState?.let {
                        updateButtonState(buttonId, it)
                        addCustomLog(
                            "FileBrowser.kt line 316 $updatedState",
                            textSize = 10,
                            textColor = Color.Cyan
                        )
                    }
                } else {
                    Log.e("ImageCopy", "Failed to copy image")
                }
            },
            mode = FileBrowserMode.FILE
        )
    }
}

@SuppressLint("NewApi", "FlowOperatorInvokedInComposition", "LocalContextGetResourceValueCall")
@Composable
fun FileBrowserPopup(
    initialDirectory: File = File(Environment.getExternalStorageDirectory().toString()),
    onDismiss: () -> Unit,
    onFileSelected: ((File) -> Unit)? = null,
    onFolderSelected: ((File) -> Unit)? = null,
    mode: FileBrowserMode = FileBrowserMode.FILE // Default to file mode
) {
    var currentDirectory by remember { mutableStateOf(initialDirectory) }
    val navigationHistory = remember { mutableStateListOf<File>() }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var showTextEditor by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Retrieve the allowed extensions as a list
    val allowedExtensionsFlow = getExtensionAllowedToEdit(context).map { extensionsString ->
        extensionsString.split(",")
    }
    val allowedExtensions by allowedExtensionsFlow.collectAsState(initial = emptyList())
    addCustomLog("FileBrowser.kt line 346 $selectedFile", textSize = 10, textColor = Color.Cyan)

    val customColor = Color(0xFF1f1e23)
    Popup(onDismissRequest = { onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .wrapContentHeight()
                .padding(8.dp),
            color = customColor
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringRes(R.string.file_browser), style = MaterialTheme.typography.bodySmall, color = Color.White)
                    IconButton(onClick = { onDismiss() }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Close", tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Current Directory Path
                Text(text = "Current Directory: ${currentDirectory.path}", style = MaterialTheme.typography.bodySmall, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Back Button
                    if (navigationHistory.isNotEmpty()) {
                        Button(onClick = {
                            val previousDirectory = navigationHistory.removeLastOrNull()
                            if (previousDirectory != null) {
                                currentDirectory = previousDirectory
                            }
                        }) {
                            Text(stringResource(R.string.back))
                        }
                    }

                    if (mode == FileBrowserMode.FOLDER) {
                        // Select Folder Button
                        Button(
                            onClick = {
                                onFolderSelected?.invoke(currentDirectory)
                                onDismiss()
                            },
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Text(stringResource(R.string.use_this_folder))
                        }
                    }

                    IconButton(
                        onClick = {
                            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                            val storageVolumes = storageManager.storageVolumes
                            val sdCardVolume = storageVolumes.find { it.isRemovable }

                            if (sdCardVolume != null) {
                                val sdCardDirectory = File(sdCardVolume.directory?.path ?: "")
                                if (sdCardDirectory.exists()) {
                                    // Update currentDirectory here
                                    currentDirectory = sdCardDirectory
                                } else {
                                    Toast.makeText(context,
                                        context.getString(R.string.sd_card_directory_not_found), Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context,
                                    context.getString(R.string.sd_card_not_found), Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.sd_card_24dp_e8eaed_fill0_wght400_grad0_opsz24), // Replace with your actual drawable resource name
                            contentDescription = "View SDCard",
                            modifier = Modifier.size(24.dp) // Adjust the size as needed
                        )
                    }

                }
                Spacer(modifier = Modifier.height(8.dp))
                FileBrowser(
                    directory = currentDirectory,
                    onFileClick = { file ->
                        if (file.isDirectory) {
                            navigationHistory.add(currentDirectory)
                            currentDirectory = file
                        } else if (mode == FileBrowserMode.FILE) {
                            if (file.extension in allowedExtensions || file.name == "defaults.bin") {
                                selectedFile = file
                                showTextEditor = true
                            } else if (onFileSelected != null) {
                                onFileSelected(file)
                                onDismiss()
                            } else {
                                Toast.makeText(context, "Please select a valid file", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onNavigate = { newDirectory ->
                        navigationHistory.add(currentDirectory)
                        currentDirectory = newDirectory
                    }
                )
                if (showTextEditor && selectedFile != null) {
                    TextEditor(
                        file = selectedFile!!,
                        onDismiss = { showTextEditor = false }
                    )
                }
            }
        }
    }
}

@Composable
fun TextEditor(file: File, onDismiss: () -> Unit) {
    // Check if the file is base64 encoded
    val isBase64File = file.name == "defaults.bin"
    var textContent by remember { mutableStateOf("") }
    var searchText by remember { mutableStateOf("") }

    // Decode base64 content if it's a base64 file, otherwise read text normally
    LaunchedEffect(file) {
        textContent = if (isBase64File) {
            val base64Content = file.readText()
            String(Base64.getDecoder().decode(base64Content))
        } else {
            file.readText()
        }
    }

    val lines = remember(textContent) { textContent.split("\n").toMutableStateList() }
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()

    Dialog(onDismissRequest = { onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.medium
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(text = "Editing: ${file.name}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Row with Search Input and Buttons
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            placeholder = { Text("Search...") },
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontSize = MaterialTheme.typography.bodySmall.fontSize
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Text Content Area with Scroll
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(horizontalScrollState)
                            .verticalScroll(verticalScrollState)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 8.dp)
                            ) {
                                // Line Numbers
                                Column(modifier = Modifier.padding(end = 8.dp)) {
                                    lines.forEachIndexed { index, _ ->
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = LocalContentColor.current,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight()
                                ) {
                                    BasicTextField(
                                        value = textContent,
                                        onValueChange = { newText ->
                                            textContent = newText
                                            lines.clear()
                                            lines.addAll(newText.split("\n"))
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                        textStyle = TextStyle(
                                            color = LocalContentColor.current,
                                            fontSize = 14.sp
                                        ),
                                        visualTransformation = VisualTransformation.None,
                                        decorationBox = { innerTextField ->
                                            Box(
                                                modifier = Modifier
                                                    .background(Color.Transparent)
                                                    .fillMaxSize()
                                            ) {
                                                if (searchText.isNotEmpty()) {
                                                    val annotatedText = buildAnnotatedString {
                                                        append(textContent)
                                                        var startIndex =
                                                            textContent.indexOf(searchText, ignoreCase = true)
                                                        while (startIndex != -1) {
                                                            val endIndex =
                                                                startIndex + searchText.length
                                                            addStyle(
                                                                style = TextStyle(
                                                                    color = Color.Red,
                                                                    background = Color.Yellow
                                                                ).toSpanStyle(),
                                                                start = startIndex,
                                                                end = endIndex
                                                            )
                                                            startIndex = textContent.indexOf(
                                                                searchText,
                                                                startIndex + 1,
                                                                ignoreCase = true
                                                            )
                                                        }
                                                    }
                                                    BasicText(
                                                        text = annotatedText,
                                                        modifier = Modifier.fillMaxSize(),
                                                        style = TextStyle(
                                                            color = LocalContentColor.current,
                                                            fontSize = 14.sp
                                                        )
                                                    )
                                                } else {
                                                    innerTextField()
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Buttons at the Bottom
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                // Encode the text content back to base64 if it's a base64 file, otherwise write text normally
                                val contentToWrite = if (isBase64File) {
                                    Base64.getEncoder().encodeToString(textContent.toByteArray())
                                } else {
                                    textContent
                                }
                                file.writeText(contentToWrite)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent
                            ),
                            modifier = Modifier.background(
                                brush = Brush.horizontalGradient(
                                    listOf(Color(0xFF42A5F5), Color(0xFF478DE0), Color(0xFF3F76D2), Color(0xFF3B5FBA))
                                )
                            )
                        ) {
                            Text("Save", color = Color.White)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { onDismiss() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent
                            ),
                            modifier = Modifier.background(
                                brush = Brush.horizontalGradient(
                                    listOf(Color(0xFF42A5F5), Color(0xFF478DE0), Color(0xFF3F76D2), Color(0xFF3B5FBA))
                                )
                            )
                        ) {
                            Text("Cancel", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(InternalCoroutinesApi::class)
class ConfigFileObserver(
    private val path: String
) : FileObserver(path, MODIFY) {

    init {
        // Initialize the resolution values from the file
        initializeResolutionValues()
    }

    override fun onEvent(event: Int, path: String?) {
        if (event == MODIFY) {
            CoroutineScope(Dispatchers.IO).launch {
                updateResolutionValues()
            }
        }
    }

    private fun initializeResolutionValues() {
        val file = File(Constants.SETTINGS_FILE)
        var width = 0
        var height = 0

        file.forEachLine { line ->
            when {
                line.startsWith("resolution x = ") -> {
                    width = line.removePrefix("resolution x = ").trim().toIntOrNull() ?: 0
                }
                line.startsWith("resolution y = ") -> {
                    height = line.removePrefix("resolution y = ").trim().toIntOrNull() ?: 0
                }
            }
        }

        // Update the companion object variables
        EngineActivity.resolutionX = width
        EngineActivity.resolutionY = height

        // Log the initial values
        addCustomLog(
            "Initial Resolution: Resolution X = $width, Resolution Y = $height",
            textSize = 16,
            textColor = Color.Blue
        )
    }

    private fun updateResolutionValues() {
        val file = File(Constants.SETTINGS_FILE)
        var width = 0
        var height = 0

        file.forEachLine { line ->
            when {
                line.startsWith("resolution x = ") -> {
                    width = line.removePrefix("resolution x = ").trim().toIntOrNull() ?: 0
                }
                line.startsWith("resolution y = ") -> {
                    height = line.removePrefix("resolution y = ").trim().toIntOrNull() ?: 0
                }
            }
        }

        // Update the companion object variables
        EngineActivity.resolutionX = width
        EngineActivity.resolutionY = height

        // Log the updated values
        val currentResolutionX = EngineActivity.resolutionX
        val currentResolutionY = EngineActivity.resolutionY

        addCustomLog(
            "Change detected: Resolution X = $currentResolutionX, Resolution Y = $currentResolutionY",
            textSize = 16,
            textColor = Color.Green
        )
    }
}
