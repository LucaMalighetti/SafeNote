package com.example.safenote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.safenote.ui.theme.SafeNoteTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID

// --- MODELLO DATI ---
data class SharedFile(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    val ownerName: String,
    val fileName: String = "",
    val fileType: String = ""
) {
    val uri: Uri get() = Uri.parse(uriString)
}

data class ViewRequest(
    val id: String = UUID.randomUUID().toString(),
    val photoId: String,
    val requesterName: String,
    val ownerName: String,
    var status: RequestStatus = RequestStatus.PENDING
)

enum class RequestStatus { PENDING, APPROVED, REJECTED }

class MainActivity : ComponentActivity() {
    private val gson = Gson()
    private val filesFile by lazy { File(filesDir, "files.json") }
    private val photosFile by lazy { File(filesDir, "photos.json") }
    private val requestsFile by lazy { File(filesDir, "requests.json") }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val users = listOf("Luca", "Marco", "Giulia")
            var currentUser by remember { mutableStateOf<String?>(null) }
            var isDarkTheme by remember { mutableStateOf(false) }

            val sharedFiles = remember { mutableStateListOf<SharedFile>() }
            val requests = remember { mutableStateListOf<ViewRequest>() }

            var showIncomingRequestsDialog by remember { mutableStateOf(false) }
            var showSentRequestsDialog by remember { mutableStateOf(false) }

            val context = LocalContext.current

            // Caricamento dati salvati
            LaunchedEffect(Unit) {
                if (filesFile.exists()) {
                    val json = filesFile.readText()
                    val type = object : TypeToken<List<SharedFile>>() {}.type
                    val list: List<SharedFile> = gson.fromJson(json, type)
                    sharedFiles.addAll(list)
                } else if (photosFile.exists()) {
                    val json = photosFile.readText()
                    val type = object : TypeToken<List<SharedFile>>() {}.type
                    val list: List<SharedFile> = gson.fromJson(json, type)
                    sharedFiles.addAll(list)
                }
                if (requestsFile.exists()) {
                    val json = requestsFile.readText()
                    val type = object : TypeToken<List<ViewRequest>>() {}.type
                    val list: List<ViewRequest> = gson.fromJson(json, type)
                    requests.addAll(list)
                }
            }

            // Salvataggio dati quando cambiano
            fun saveData() {
                filesFile.writeText(gson.toJson(sharedFiles.toList()))
                requestsFile.writeText(gson.toJson(requests.toList()))
            }

            SafeNoteTheme(darkTheme = isDarkTheme) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (currentUser == null) {
                        UserSelectionScreen(users) { currentUser = it }
                    } else {
                        val user = currentUser!!
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    navigationIcon = {
                                        Box(
                                            modifier = Modifier
                                                .padding(start = 12.dp)
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(user.take(1), fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    title = {
                                        Text(user, style = MaterialTheme.typography.titleMedium)
                                    },
                                    actions = {
                                        var menuExpanded by remember { mutableStateOf(false) }
                                        IconButton(onClick = { menuExpanded = true }) {
                                            BadgedBox(badge = {
                                                val pendingCount = requests.count { it.ownerName == user && it.status == RequestStatus.PENDING }
                                                if (pendingCount > 0) Badge { Text(pendingCount.toString()) }
                                            }) {
                                                Icon(Icons.Default.MoreVert, "Menu")
                                            }
                                        }
                                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                            DropdownMenuItem(
                                                text = { Text("Account") },
                                                leadingIcon = { Icon(Icons.Default.AccountCircle, null) },
                                                onClick = { currentUser = null; menuExpanded = false }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Richieste in arrivo") },
                                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallReceived, null) },
                                                onClick = { menuExpanded = false; showIncomingRequestsDialog = true }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Richieste mandate") },
                                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallMade, null) },
                                                onClick = { menuExpanded = false; showSentRequestsDialog = true }
                                            )
                                            HorizontalDivider()
                                            DropdownMenuItem(
                                                text = { Text(if (isDarkTheme) "Modalità chiara" else "Modalità scura") },
                                                leadingIcon = { Icon(if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode, null) },
                                                onClick = { menuExpanded = false; isDarkTheme = !isDarkTheme }
                                            )
                                        }
                                    }
                                )
                            },
                            floatingActionButton = {
                                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                                    uri?.let {
                                        try {
                                            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        } catch (_: Exception) {
                                        }
                                        val (fileName, fileType) = getFileNameAndType(context, it)
                                        sharedFiles.add(0, SharedFile(uriString = it.toString(), ownerName = user, fileName = fileName, fileType = fileType))
                                        saveData()
                                    }
                                }
                                FloatingActionButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                                    Icon(Icons.Default.Add, contentDescription = "Condividi File")
                                }
                            },
                            floatingActionButtonPosition = FabPosition.End
                        ) { p ->
                            Column(Modifier.padding(p)) {
                                FileGallery(
                                    files = sharedFiles,
                                    currentUser = user,
                                    requests = requests,
                                    onSendRequest = { file ->
                                        requests.add(ViewRequest(photoId = file.id, requesterName = user, ownerName = file.ownerName))
                                        saveData()
                                    }
                                )
                            }
                        }

                        if (showIncomingRequestsDialog) {
                            IncomingRequestsDialog(
                                requests = requests.filter { it.ownerName == user },
                                files = sharedFiles,
                                onDismiss = { showIncomingRequestsDialog = false },
                                onAccept = { req ->
                                    val index = requests.indexOfFirst { it.id == req.id }
                                    if (index != -1) {
                                        requests[index] = req.copy(status = RequestStatus.APPROVED)
                                        saveData()
                                    }
                                },
                                onReject = { req ->
                                    val index = requests.indexOfFirst { it.id == req.id }
                                    if (index != -1) {
                                        requests[index] = req.copy(status = RequestStatus.REJECTED)
                                        saveData()
                                    }
                                }
                            )
                        }

                        if (showSentRequestsDialog) {
                            SentRequestsDialog(
                                requests = requests.filter { it.requesterName == user },
                                files = sharedFiles,
                                onDismiss = { showSentRequestsDialog = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getFileNameAndType(context: Context, uri: Uri): Pair<String, String> {
    var fileName = ""
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex) ?: ""
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    if (fileName.isEmpty()) {
        fileName = uri.lastPathSegment ?: "file"
    }
    val fileType = fileName.substringAfterLast('.', "").lowercase()
    return Pair(fileName, fileType)
}

fun isImageFile(fileName: String, fileType: String): Boolean {
    val ext = fileType.ifEmpty { fileName.substringAfterLast('.', "").lowercase() }
    return ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
}

fun getMimeType(fileName: String, fileType: String): String {
    val ext = fileType.ifEmpty { fileName.substringAfterLast('.', "").lowercase() }
    return when (ext) {
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        "md" -> "text/markdown"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "doc" -> "application/msword"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "xls" -> "application/vnd.ms-excel"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "*/*"
    }
}

fun openFile(context: Context, file: SharedFile) {
    val mimeType = getMimeType(file.fileName, file.fileType)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(file.uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Apri file"))
    } catch (_: Exception) {
        Toast.makeText(context, "Nessuna applicazione disponibile per aprire il file", Toast.LENGTH_SHORT).show()
    }
}

fun shareFile(context: Context, file: SharedFile) {
    val mimeType = getMimeType(file.fileName, file.fileType)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, file.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Condividi file"))
    } catch (_: Exception) {
        Toast.makeText(context, "Impossibile condividere il file", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun UserSelectionScreen(users: List<String>, onUserSelected: (String) -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Benvenuto su SafeNote", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(32.dp))
            Text("Seleziona il tuo profilo:")
            Spacer(Modifier.height(16.dp))
            users.forEach { user ->
                Button(
                    onClick = { onUserSelected(user) },
                    modifier = Modifier.fillMaxWidth(0.6f).padding(vertical = 4.dp)
                ) {
                    Text(user)
                }
            }
        }
    }
}

@Composable
fun FileGallery(
    files: List<SharedFile>,
    currentUser: String,
    requests: List<ViewRequest>,
    onSendRequest: (SharedFile) -> Unit
) {
    if (files.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nessun file condiviso.") }
    } else {
        LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(8.dp)) {
            items(files) { file ->
                val hasAccess = file.ownerName == currentUser ||
                        requests.any { it.photoId == file.id && it.requesterName == currentUser && it.status == RequestStatus.APPROVED }
                val isPending = requests.any { it.photoId == file.id && it.requesterName == currentUser && it.status == RequestStatus.PENDING }

                FileItem(file, hasAccess, isPending, onSendRequest)
            }
        }
    }
}

@Composable
fun FileItem(file: SharedFile, hasAccess: Boolean, isPending: Boolean, onSendRequest: (SharedFile) -> Unit) {
    val context = LocalContext.current
    val isImg = isImageFile(file.fileName, file.fileType)

    Card(Modifier.padding(4.dp).fillMaxWidth().height(220.dp)) {
        Box(Modifier.fillMaxSize()) {
            if (hasAccess) {
                if (isImg) {
                    Box(Modifier.fillMaxSize()) {
                        Image(
                            painter = rememberAsyncImagePainter(file.uri),
                            contentDescription = file.fileName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            IconButton(onClick = { openFile(context, file) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, "Apri", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { shareFile(context, file) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Share, "Condividi", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        val ext = file.fileType.ifEmpty { file.fileName.substringAfterLast('.', "").lowercase() }
                        val (icon, tint) = when (ext) {
                            "pdf" -> Pair(Icons.Default.PictureAsPdf, Color(0xFFD32F2F))
                            "xlsx", "xls" -> Pair(Icons.Default.TableChart, Color(0xFF2E7D32))
                            "docx", "doc" -> Pair(Icons.Default.Description, Color(0xFF1976D2))
                            "md", "txt" -> Pair(Icons.AutoMirrored.Filled.Article, Color(0xFF00838F))
                            else -> Pair(Icons.AutoMirrored.Filled.InsertDriveFile, MaterialTheme.colorScheme.primary)
                        }

                        Spacer(Modifier.height(4.dp))
                        Icon(icon, contentDescription = file.fileName, tint = tint, modifier = Modifier.size(44.dp))

                        Text(
                            text = file.fileName.ifEmpty { "File" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )

                        Surface(
                            color = tint.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = ext.uppercase().ifEmpty { "FILE" },
                                color = tint,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp)
                        ) {
                            IconButton(onClick = { openFile(context, file) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, "Apri", modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { shareFile(context, file) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Share, "Condividi", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                        Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(4.dp))
                        if (file.fileName.isNotEmpty()) {
                            Text(
                                file.fileName,
                                color = Color.White,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        if (isPending) {
                            Text("Richiesta inviata...", color = Color.LightGray, fontSize = 12.sp)
                        } else {
                            Button(onClick = { onSendRequest(file) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                                Text("Richiedi Accesso", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
            Surface(
                color = Color.Black.copy(0.6f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
            ) {
                Text("Da: ${file.ownerName}", color = Color.White, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun IncomingRequestsDialog(
    requests: List<ViewRequest>,
    files: List<SharedFile>,
    onDismiss: () -> Unit,
    onAccept: (ViewRequest) -> Unit,
    onReject: (ViewRequest) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Richieste in Arrivo") },
        text = {
            if (requests.isEmpty()) {
                Text("Non hai nuove richieste.")
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(requests) { req ->
                        val targetFile = files.find { it.id == req.photoId }
                        val fileLabel = targetFile?.fileName?.ifEmpty { null } ?: "un tuo file"
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(req.requesterName, fontWeight = FontWeight.Bold)
                                Text("Vuole accedere a: $fileLabel", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (req.status == RequestStatus.PENDING) {
                                IconButton(onClick = { onAccept(req) }) { Icon(Icons.Default.Check, null, tint = Color.Green) }
                                IconButton(onClick = { onReject(req) }) { Icon(Icons.Default.Close, null, tint = Color.Red) }
                            } else {
                                Text(req.status.name, fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } }
    )
}

@Composable
fun SentRequestsDialog(requests: List<ViewRequest>, files: List<SharedFile>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Richieste Mandate") },
        text = {
            if (requests.isEmpty()) {
                Text("Non hai mandato richieste.")
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(requests) { req ->
                        val targetFile = files.find { it.id == req.photoId }
                        val fileLabel = targetFile?.fileName?.ifEmpty { null } ?: "ID File: ${req.photoId.take(8)}..."
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("A: ${req.ownerName}", fontWeight = FontWeight.Bold)
                                Text(fileLabel, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            StatusBadge(req.status)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } }
    )
}

@Composable
fun StatusBadge(status: RequestStatus) {
    val color = when(status) {
        RequestStatus.PENDING -> Color.Gray
        RequestStatus.APPROVED -> Color.Green
        RequestStatus.REJECTED -> Color.Red
    }
    Surface(color = color.copy(0.2f), shape = RoundedCornerShape(4.dp)) {
        Text(status.name, color = color, fontSize = 10.sp, modifier = Modifier.padding(4.dp))
    }
}
