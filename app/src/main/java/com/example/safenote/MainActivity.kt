package com.example.safenote

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.rememberAsyncImagePainter
import com.example.safenote.ui.theme.SafeNoteTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.FileOutputStream

val CLASSES_CONFIG = listOf(
    SchoolClass("3AI", listOf("Informatica", "Sistemi", "TPS", "Matematica", "Inglese", "Lab Informatica", "GEC", "Telecomunicazioni", "Religione", "Ed. Fisica")),
    SchoolClass("2Z", listOf("Fisica", "Chimica", "Biologia", "Geografia", "Italiano", "Matematica", "Storia", "Inglese", "Scienze", "Ed. Fisica")),
    SchoolClass("3AC", listOf("Economia", "Diritto", "Francese", "Storia", "Matematica", "Inglese", "Informatica", "Religione", "Ed. Fisica"))
)

val PREDEFINED_TAGS = listOf("Generale") // Usato solo come fallback

fun getTagBrush(tag: String): Brush {
    val colors = when (tag.lowercase()) {
        "italiano", "fisica" -> listOf(Color(0xFFFF5252), Color(0xFFB71C1C))
        "sistemi", "economia", "gec" -> listOf(Color(0xFFFF9800), Color(0xFFE65100))
        "storia", "geografia", "scienze" -> listOf(Color(0xFFFFF176), Color(0xFFFBC02D))
        "matematica", "biologia" -> listOf(Color(0xFF69F0AE), Color(0xFF1B5E20))
        "informatica", "diritto", "telecomunicazioni" -> listOf(Color(0xFF448AFF), Color(0xFF0D47A1))
        "inglese", "francese" -> listOf(Color(0xFFFF4081), Color(0xFF880E4F))
        "chimica" -> listOf(Color(0xFF00BCD4), Color(0xFF006064))
        "tps", "religione" -> listOf(Color(0xFFB388FF), Color(0xFF4A00E0))
        "lab informatica", "ed. fisica" -> listOf(Color(0xFF00C6FF), Color(0xFF0072FF))
        else -> listOf(Color.Gray, Color.DarkGray)
    }
    return Brush.linearGradient(colors = colors)
}

fun getTagTextColor(tag: String): Color {
    return when (tag.lowercase()) {
        "storia", "telecomunicazioni", "lab teleco" -> Color.Black
        else -> Color.White
    }
}

class MainActivity : ComponentActivity() {
    private val apiService = RetrofitClient.instance

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("safenote_session", android.content.Context.MODE_PRIVATE) }
            
            var currentUser by remember { mutableStateOf<String?>(prefs.getString("user", null)) }
            var currentClass by remember { mutableStateOf<String?>(prefs.getString("class", null)) }
            var isDarkTheme by rememberSaveable { mutableStateOf(false) }
            
            val userProfiles = remember { mutableStateMapOf<String, String>() }
            val sharedPhotos = remember { mutableStateListOf<SharedPhoto>() }
            val requests = remember { mutableStateListOf<ViewRequest>() }
            
            // Zero Tolerance always active
            val isSecureModeEnabled = true
            var isThreatDetected by remember { mutableStateOf(false) }
            
            var searchQuery by rememberSaveable { mutableStateOf("") }
            var selectedTagFilter by rememberSaveable { mutableStateOf<String?>(null) }
            var showTagDialog by rememberSaveable { mutableStateOf(false) }
            var pendingPhotoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
            
            var showIncomingRequestsDialog by rememberSaveable { mutableStateOf(false) }
            var showSentRequestsDialog by rememberSaveable { mutableStateOf(false) }
            var fullScreenPhoto by remember { mutableStateOf<SharedPhoto?>(null) }

            var hasPermission by remember { mutableStateOf(false) }
            val coroutineScope = rememberCoroutineScope()
            val gson = remember { Gson() }
            val photosFile = remember { File(context.filesDir, "photos.json") }
            val requestsFile = remember { File(context.filesDir, "requests.json") }
            val profilesFile = remember { File(context.filesDir, "profiles.json") }

            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }

            fun loadData() {
                try {
                    if (profilesFile.exists()) {
                        val type = object : TypeToken<Map<String, String>>() {}.type
                        val map: Map<String, String> = gson.fromJson(profilesFile.readText(), type)
                        userProfiles.clear()
                        userProfiles.putAll(map)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            LaunchedEffect(Unit) {
                // Reset dati locali come richiesto (una sola volta)
                if (!prefs.getBoolean("data_reset_v1", false)) {
                    photosFile.delete()
                    requestsFile.delete()
                    profilesFile.delete()
                    prefs.edit().clear().putBoolean("data_reset_v1", true).apply()
                    currentUser = null
                    currentClass = null
                }

                loadData()
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    hasPermission = true
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }

            // Caricamento Dati dal Server
            fun refreshData() {
                currentClass?.let { className ->
                    lifecycleScope.launch {
                        try {
                            val response = apiService.getPhotos(className)
                            if (response.isSuccessful) {
                                sharedPhotos.clear()
                                response.body()?.let { sharedPhotos.addAll(it) }
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }
                currentUser?.let { username ->
                    lifecycleScope.launch {
                        try {
                            val response = apiService.getRequests(username)
                            if (response.isSuccessful) {
                                requests.clear()
                                response.body()?.let { requests.addAll(it) }
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }
            }

            LaunchedEffect(currentClass, currentUser) {
                refreshData()
            }

            fun saveData() {
                photosFile.writeText(gson.toJson(sharedPhotos.toList()))
                requestsFile.writeText(gson.toJson(requests.toList()))
                profilesFile.writeText(gson.toJson(userProfiles.toMap()))
            }

            SafeNoteTheme(darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Crossfade(targetState = currentUser to currentClass, label = "login_transition") { (user, clazz) ->
                        if (user == null || clazz == null) {
                            LoginScreen(CLASSES_CONFIG.map { it.name }) { u, c -> 
                                prefs.edit().putString("user", u).putString("class", c).apply()
                                currentUser = u
                                currentClass = c
                            }
                        } else {
                            MainContent(
                                user = user,
                                className = clazz,
                                isDarkTheme = isDarkTheme,
                                onThemeToggle = { isDarkTheme = !isDarkTheme },
                                sharedPhotos = sharedPhotos,
                                requests = requests,
                                userProfiles = userProfiles,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                selectedTagFilter = selectedTagFilter,
                                onTagFilterChange = { selectedTagFilter = it },
                                onSignOut = { 
                                    prefs.edit().clear().apply()
                                    currentUser = null
                                    currentClass = null 
                                },
                                onSetProfilePic = { uri ->
                                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    userProfiles[user] = uri.toString()
                                    saveData()
                                },
                                onShowIncomingRequests = { showIncomingRequestsDialog = true },
                                onShowSentRequests = { showSentRequestsDialog = true },
                                onRefresh = { refreshData() },
                                onAddPhotos = { uris ->
                                    uris.forEach { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                    pendingPhotoUris = uris
                                    showTagDialog = true
                                },
                                onSendRequest = { photo ->
                                    coroutineScope.launch {
                                        try {
                                            val request = ViewRequest(photoId = photo.id, requesterName = user, ownerName = photo.ownerName)
                                            val response = apiService.sendRequest(request)
                                            if (response.isSuccessful) {
                                                requests.add(response.body()!!)
                                            }
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                },
                                onDeletePhoto = { photo ->
                                    coroutineScope.launch {
                                        try {
                                            val response = apiService.deletePhoto(photo.id)
                                            if (response.isSuccessful) {
                                                sharedPhotos.remove(photo)
                                                requests.removeAll { it.photoId == photo.id }
                                            }
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                },
                                onPhotoClick = { fullScreenPhoto = it }
                            )
                        }
                    }

                    // Dialogs & Overlays
                    if (fullScreenPhoto != null) {
                        FullScreenImageOverlay(photo = fullScreenPhoto!!, onDismiss = { fullScreenPhoto = null })
                    }

                    if (showTagDialog && pendingPhotoUris.isNotEmpty()) {
                        AddTagsDialog(
                            photoCount = pendingPhotoUris.size,
                            availableTags = CLASSES_CONFIG.find { it.name == currentClass }?.tags ?: emptyList(),
                            onDismiss = { showTagDialog = false; pendingPhotoUris = emptyList() },
                            onConfirm = { tags, title, description, coverUri ->
                                coroutineScope.launch {
                                    try {
                                        val parts = pendingPhotoUris.mapNotNull { uri ->
                                            context.contentResolver.openInputStream(uri)?.use { input ->
                                                val file = File(context.cacheDir, "upload_${UUID.randomUUID()}.jpg")
                                                FileOutputStream(file).use { output -> input.copyTo(output) }
                                                val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                                                MultipartBody.Part.createFormData("photos", file.name, requestFile)
                                            }
                                        }

                                        val ownerBody = currentUser!!.toRequestBody("text/plain".toMediaTypeOrNull())
                                        val classBody = currentClass!!.toRequestBody("text/plain".toMediaTypeOrNull())
                                        val titleBody = title.toRequestBody("text/plain".toMediaTypeOrNull())
                                        val descBody = description.toRequestBody("text/plain".toMediaTypeOrNull())
                                        val tagsBody = Gson().toJson(tags).toRequestBody("text/plain".toMediaTypeOrNull())

                                        val response = apiService.uploadPhoto(ownerBody, classBody, titleBody, descBody, tagsBody, parts)
                                        if (response.isSuccessful) {
                                            response.body()?.let { sharedPhotos.add(0, it) }
                                            showTagDialog = false
                                            pendingPhotoUris = emptyList()
                                        }
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            }
                        )
                    }

                    if (hasPermission) {
                        ZeroToleranceMonitor { isThreat, _ -> isThreatDetected = isThreat }
                    }

                    if (isThreatDetected) SecurityLockOverlay()

                    if (showIncomingRequestsDialog) {
                        IncomingRequestsDialog(
                            requests = requests.filter { it.ownerName == currentUser },
                            userProfiles = userProfiles,
                            onDismiss = { showIncomingRequestsDialog = false },
                            onAccept = { req -> 
                                coroutineScope.launch {
                                    try {
                                        val response = apiService.updateRequestStatus(req.id, mapOf("status" to "APPROVED"))
                                        if (response.isSuccessful) {
                                            val index = requests.indexOfFirst { it.id == req.id }
                                            if (index != -1) requests[index] = response.body()!!
                                        }
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            },
                            onReject = { req -> 
                                coroutineScope.launch {
                                    try {
                                        val response = apiService.updateRequestStatus(req.id, mapOf("status" to "REJECTED"))
                                        if (response.isSuccessful) {
                                            val index = requests.indexOfFirst { it.id == req.id }
                                            if (index != -1) requests[index] = response.body()!!
                                        }
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            }
                        )
                    }

                    if (showSentRequestsDialog) {
                        SentRequestsDialog(
                            requests = requests.filter { it.requesterName == currentUser },
                            userProfiles = userProfiles,
                            onDismiss = { showSentRequestsDialog = false }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    user: String,
    className: String,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    sharedPhotos: List<SharedPhoto>,
    requests: List<ViewRequest>,
    userProfiles: Map<String, String>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedTagFilter: String?,
    onTagFilterChange: (String?) -> Unit,
    onSignOut: () -> Unit,
    onSetProfilePic: (Uri) -> Unit,
    onShowIncomingRequests: () -> Unit,
    onShowSentRequests: () -> Unit,
    onRefresh: () -> Unit,
    onAddPhotos: (List<Uri>) -> Unit,
    onSendRequest: (SharedPhoto) -> Unit,
    onDeletePhoto: (SharedPhoto) -> Unit,
    onPhotoClick: (SharedPhoto) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val profilePicLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onSetProfilePic(it) }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) onAddPhotos(uris)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Column {
                        Text("SafeNote", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                        Text("Ciao, $user ($className)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    val profilePicUri = userProfiles[user]
                    Box(
                        modifier = Modifier
                            .padding(start = 16.dp, end = 8.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable { profilePicLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (profilePicUri != null) {
                            Image(
                                painter = rememberAsyncImagePainter(profilePicUri),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.Person, null, modifier = Modifier.size(24.dp))
                        }
                    }
                },
                actions = {
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = onShowIncomingRequests) {
                        BadgedBox(badge = {
                            val pendingCount = requests.count { it.ownerName == user && it.status == RequestStatus.PENDING }
                            if (pendingCount > 0) Badge { Text(pendingCount.toString()) }
                        }) {
                            Icon(Icons.Outlined.Notifications, "Richieste")
                        }
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, "Menu")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Miei invii") },
                            leadingIcon = { Icon(Icons.Outlined.History, null) },
                            onClick = { menuExpanded = false; onShowSentRequests() }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(if (isDarkTheme) "Tema Chiaro" else "Tema Scuro") },
                            leadingIcon = { Icon(if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode, null) },
                            onClick = { menuExpanded = false; onThemeToggle() }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Esci", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuExpanded = false; onSignOut() }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { photoPicker.launch("image/*") },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Pubblica") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    isRefreshing = true
                    onRefresh()
                    isRefreshing = false
                }
            },
            modifier = Modifier.padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search Bar
                SearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Tags Row
                val classTags = CLASSES_CONFIG.find { it.name == className }?.tags ?: emptyList()
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    item {
                        GradientTagChip(
                            tag = "Tutti",
                            isSelected = selectedTagFilter == null,
                            onClick = { onTagFilterChange(null) }
                        )
                    }
                    items(classTags) { tag ->
                        GradientTagChip(
                            tag = tag,
                            isSelected = selectedTagFilter == tag,
                            onClick = { onTagFilterChange(if (selectedTagFilter == tag) null else tag) }
                        )
                    }
                }

                // Gallery
                val filteredPhotos = sharedPhotos.filter { p ->
                    val matchesClass = p.className == className
                    val matchesQuery = searchQuery.isEmpty() || 
                                       p.title.contains(searchQuery, ignoreCase = true) || 
                                       p.ownerName.contains(searchQuery, ignoreCase = true) ||
                                       p.tags?.any { it.contains(searchQuery, ignoreCase = true) } == true
                    
                    val matchesTag = selectedTagFilter == null || 
                                     p.tags?.any { it.equals(selectedTagFilter, ignoreCase = true) } == true

                    matchesClass && matchesQuery && matchesTag
                }
                
                PhotoGallery(
                    photos = filteredPhotos,
                    currentUser = user,
                    userProfiles = userProfiles,
                    requests = requests,
                    onSendRequest = onSendRequest,
                    onDeletePhoto = onDeletePhoto,
                    onPhotoClick = onPhotoClick
                )
            }
        }
    }
}

@Composable
fun GradientTagChip(tag: String, isSelected: Boolean, onClick: () -> Unit) {
    val brush = if (tag == "Tutti") {
        Brush.linearGradient(listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondaryContainer))
    } else {
        getTagBrush(tag)
    }
    
    val textColor = if (tag == "Tutti") {
        MaterialTheme.colorScheme.onSecondary
    } else {
        getTagTextColor(tag)
    }

    Box(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clip(CircleShape)
            .background(brush)
            .then(
                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) 
                else Modifier
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isSelected) {
                Icon(
                    Icons.Default.Check, 
                    null, 
                    Modifier.size(16.dp).padding(end = 4.dp), 
                    tint = textColor
                )
            }
            Text(
                text = tag,
                color = textColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp)),
        placeholder = { Text("Cerca appunti o publisher...") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = if (query.isNotEmpty()) {
            { IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, null) } }
        } else null,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(classes: List<String>, onLoginSuccess: (String, String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var verificationCode by rememberSaveable { mutableStateOf("") }
    var selectedClass by rememberSaveable { mutableStateOf(classes.firstOrNull() ?: "") }
    
    var isRegisterMode by rememberSaveable { mutableStateOf(false) }
    var isCodeSent by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val apiService = RetrofitClient.instance

    Box(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.background))),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(32.dp).shadow(16.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("SafeNote", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    Text("Sveglia del server in corso...", style = MaterialTheme.typography.bodyMedium)
                    Text("Potrebbe richiedere fino a 1 minuto", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                } else if (!isCodeSent) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; errorMessage = null },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                    )

                    if (isRegisterMode) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Nome utente") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Person, null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                    }

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                    
                    if (isRegisterMode) {
                        Column(Modifier.fillMaxWidth()) {
                            Text("Seleziona la tua classe:", style = MaterialTheme.typography.labelMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                classes.forEach { clazz ->
                                    FilterChip(
                                        selected = selectedClass == clazz,
                                        onClick = { selectedClass = clazz },
                                        label = { Text(clazz) }
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                errorMessage = null
                                try {
                                    if (isRegisterMode) {
                                        val response = apiService.requestCode(mapOf(
                                            "email" to email,
                                            "action" to "register",
                                            "username" to username,
                                            "password" to password,
                                            "className" to selectedClass
                                        ))
                                        if (response.isSuccessful) isCodeSent = true
                                        else {
                                            val errorBody = response.errorBody()?.string()
                                            errorMessage = "Errore server: ${errorBody ?: "Invio fallito"}"
                                        }
                                    } else {
                                        val response = apiService.loginDirect(mapOf(
                                            "email" to email,
                                            "password" to password
                                        ))
                                        if (response.isSuccessful) {
                                            val body = response.body()
                                            val u = body?.get("username") as? String ?: ""
                                            val c = body?.get("className") as? String ?: ""
                                            onLoginSuccess(u, c)
                                        } else {
                                            errorMessage = "Email o password errati"
                                        }
                                    }
                                } catch (e: Exception) { 
                                    errorMessage = "Errore: ${e.localizedMessage ?: "Connessione fallita"}"
                                    e.printStackTrace()
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = email.isNotBlank() && password.isNotBlank()
                    ) {
                        Text(if (isRegisterMode) "Invia Codice Registrazione" else "Accedi")
                    }

                    TextButton(onClick = { 
                        if (!isRegisterMode) {
                            coroutineScope.launch {
                                isLoading = true
                                errorMessage = null
                                try {
                                    val response = apiService.requestCode(mapOf("email" to email, "action" to "reset_password"))
                                    if (response.isSuccessful) {
                                        isCodeSent = true
                                        isRegisterMode = false 
                                    } else errorMessage = "Email non trovata"
                                } catch (e: Exception) { 
                                    errorMessage = "Errore: ${e.localizedMessage ?: "Connessione fallita"}"
                                    e.printStackTrace() 
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    }) {
                        Text("Password dimenticata?")
                    }
                } else {
                    Text("Inserisci il codice inviato via email", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = verificationCode,
                        onValueChange = { verificationCode = it },
                        label = { Text("Codice a 6 cifre") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                errorMessage = null
                                try {
                                    val response = apiService.verifyCode(mapOf("email" to email, "code" to verificationCode))
                                        if (response.isSuccessful) {
                                            val body = response.body()
                                            val u = body?.get("username") as? String ?: ""
                                            val c = body?.get("className") as? String ?: ""
                                            if (u.isNotEmpty() && c.isNotEmpty()) {
                                                onLoginSuccess(u, c)
                                            } else {
                                                errorMessage = "Errore nel profilo utente"
                                            }
                                        } else errorMessage = "Codice errato o scaduto"
                                } catch (e: Exception) { 
                                    errorMessage = "Errore verifica: ${e.localizedMessage}"
                                    e.printStackTrace()
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Verifica e Accedi")
                    }
                }

                if (errorMessage != null) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }

                if (!isLoading) {
                    TextButton(onClick = { isRegisterMode = !isRegisterMode; isCodeSent = false }) {
                        Text(if (isRegisterMode) "Hai già un account? Accedi" else "Non hai un account? Registrati")
                    }
                }
            }
        }
    }
}

@Composable
fun PhotoGallery(
    photos: List<SharedPhoto>,
    currentUser: String,
    userProfiles: Map<String, String>,
    requests: List<ViewRequest>,
    onSendRequest: (SharedPhoto) -> Unit,
    onDeletePhoto: (SharedPhoto) -> Unit,
    onPhotoClick: (SharedPhoto) -> Unit
) {
    if (photos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Collections, null, Modifier.size(64.dp), Color.Gray.copy(0.4f))
                Text("Nessun contenuto trovato.", color = Color.Gray)
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(photos, key = { it.id }) { photo ->
                val hasAccess = photo.ownerName == currentUser || 
                               requests.any { it.photoId == photo.id && it.requesterName == currentUser && it.status == RequestStatus.APPROVED }
                val isPending = requests.any { it.photoId == photo.id && it.requesterName == currentUser && it.status == RequestStatus.PENDING }
                val hasApprovedRequests = requests.any { it.photoId == photo.id && it.status == RequestStatus.APPROVED }

                PhotoItem(
                    photo = photo, 
                    ownerProfilePic = userProfiles[photo.ownerName], 
                    hasAccess = hasAccess, 
                    isPending = isPending, 
                    isOwner = photo.ownerName == currentUser,
                    hasApprovedRequests = hasApprovedRequests,
                    onSendRequest = onSendRequest, 
                    onDeletePhoto = onDeletePhoto,
                    onPhotoClick = onPhotoClick
                )
            }
        }
    }
}

@Composable
fun PhotoItem(
    photo: SharedPhoto, 
    ownerProfilePic: String?, 
    hasAccess: Boolean, 
    isPending: Boolean, 
    isOwner: Boolean,
    hasApprovedRequests: Boolean,
    onSendRequest: (SharedPhoto) -> Unit, 
    onDeletePhoto: (SharedPhoto) -> Unit,
    onPhotoClick: (SharedPhoto) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clickable(enabled = hasAccess) { onPhotoClick(photo) }
    ) {
        Box(Modifier.fillMaxSize()) {
            // Background / Cover
            if (photo.coverUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(photo.coverUri),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                val tag = photo.tags?.firstOrNull() ?: "Appunti"
                Box(
                    modifier = Modifier.fillMaxSize().background(getTagBrush(tag)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = photo.title.ifEmpty { tag }.uppercase(),
                        color = getTagTextColor(tag),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            
            // Security Overlay
            if (!hasAccess) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                    if (isPending) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Default.Lock, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(40.dp))
                    }
                }
            }

            // Top Bar Overlay (Tag & Delete)
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                val tag = photo.tags?.firstOrNull() ?: "Note"
                Surface(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .background(getTagBrush(tag))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            tag, 
                            color = getTagTextColor(tag), 
                            fontSize = 10.sp, 
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                
                if (isOwner) {
                    IconButton(
                        onClick = { onDeletePhoto(photo) },
                        enabled = !hasApprovedRequests,
                        modifier = Modifier.size(28.dp).background(
                            if (hasApprovedRequests) Color.Gray.copy(0.3f) else Color.Black.copy(0.3f), 
                            CircleShape
                        )
                    ) {
                        Icon(
                            if (hasApprovedRequests) Icons.Default.Block else Icons.Default.Delete, 
                            null, 
                            tint = if (hasApprovedRequests) Color.White.copy(0.5f) else Color.White, 
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Bottom Info Overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(0.9f))
                        )
                    )
                    .padding(12.dp)
            ) {
                Text(
                    photo.title.ifEmpty { "Senza Titolo" },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    if (ownerProfilePic != null) {
                        Image(
                            painter = rememberAsyncImagePainter(ownerProfilePic),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).clip(CircleShape)
                        )
                    } else {
                        Icon(Icons.Default.Person, null, tint = Color.LightGray, modifier = Modifier.size(12.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(photo.ownerName, color = Color.LightGray, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.PhotoLibrary, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(12.dp))
                    Text(" ${photo.photoCount}", color = Color.White.copy(0.7f), fontSize = 11.sp)
                }

                if (!hasAccess && !isPending) {
                    Button(
                        onClick = { onSendRequest(photo) },
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(32.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Richiedi Accesso", fontSize = 11.sp)
                    }
                } else if (isPending) {
                    Text(
                        "In attesa di approvazione...", 
                        color = Color.Yellow, 
                        fontSize = 10.sp, 
                        modifier = Modifier.padding(top = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun FullScreenImageOverlay(photo: SharedPhoto, onDismiss: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { photo.uris.size })
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 16.dp
        ) { page ->
            Image(
                painter = rememberAsyncImagePainter(photo.uris[page]),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clickable { onDismiss() },
                contentScale = ContentScale.Fit
            )
        }
        
        // UI Overlays
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp).statusBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(photo.title, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("${pagerState.currentPage + 1} di ${photo.uris.size}", color = Color.LightGray, fontSize = 12.sp)
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(Color.White.copy(0.2f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTagsDialog(
    photoCount: Int, 
    availableTags: List<String>,
    onDismiss: () -> Unit, 
    onConfirm: (List<String>, String, String, Uri?) -> Unit
) {
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var coverUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        coverUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuova Pubblicazione") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Titolo") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Descrizione") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
                item {
                    Text("Copertina", style = MaterialTheme.typography.labelLarge)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { coverPicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (coverUri != null) {
                            Image(
                                painter = rememberAsyncImagePainter(coverUri),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.primary)
                                Text("Scegli un'immagine", fontSize = 12.sp)
                            }
                        }
                    }
                }
                item {
                    Text("Materia", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableTags.forEach { tag ->
                            Box(modifier = Modifier.weight(1f, fill = false)) {
                                GradientTagChip(
                                    tag = tag,
                                    isSelected = selectedTag == tag,
                                    onClick = { selectedTag = tag }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(listOfNotNull(selectedTag), title, description, coverUri) }, 
                enabled = selectedTag != null && title.isNotBlank()
            ) { Text("Pubblica $photoCount foto") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}

@Composable
fun IncomingRequestsDialog(requests: List<ViewRequest>, userProfiles: Map<String, String>, onDismiss: () -> Unit, onAccept: (ViewRequest) -> Unit, onReject: (ViewRequest) -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Gestione Accessi")
                TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                        Text("Pendenti", modifier = Modifier.padding(8.dp))
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Text("Cronologia", modifier = Modifier.padding(8.dp))
                    }
                }
            }
        },
        text = {
            val filteredList = if (selectedTab == 0) {
                requests.filter { it.status == RequestStatus.PENDING }
            } else {
                requests.filter { it.status != RequestStatus.PENDING }
            }

            if (filteredList.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text(if (selectedTab == 0) "Nessuna richiesta in sospeso." else "Cronologia vuota.", color = Color.Gray)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(filteredList) { req ->
                        ListItem(
                            headlineContent = { Text(req.requesterName, fontWeight = FontWeight.Bold) },
                            supportingContent = { 
                                Text(if (req.status == RequestStatus.PENDING) "Richiede accesso" else "Richiesta ${if (req.status == RequestStatus.APPROVED) "accettata" else "rifiutata"}") 
                            },
                            leadingContent = {
                                val profilePic = userProfiles[req.requesterName]
                                if (profilePic != null) {
                                    Image(
                                        painter = rememberAsyncImagePainter(profilePic),
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(40.dp))
                                }
                            },
                            trailingContent = {
                                if (req.status == RequestStatus.PENDING) {
                                    Row {
                                        IconButton(onClick = { onAccept(req) }) { Icon(Icons.Default.CheckCircle, null, tint = Color.Green) }
                                        IconButton(onClick = { onReject(req) }) { Icon(Icons.Default.Cancel, null, tint = Color.Red) }
                                    }
                                } else {
                                    StatusBadge(req.status)
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Chiudi") } }
    )
}

@Composable
fun SentRequestsDialog(requests: List<ViewRequest>, userProfiles: Map<String, String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Richieste inviate") },
        text = {
            if (requests.isEmpty()) {
                Text("Non hai ancora inviato richieste.")
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(requests) { req ->
                        ListItem(
                            headlineContent = { Text("A: ${req.ownerName}") },
                            supportingContent = { Text("Stato: ${req.status}") },
                            leadingContent = { Icon(Icons.Outlined.FileUpload, null) },
                            trailingContent = { StatusBadge(req.status) }
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Chiudi") } }
    )
}

@Composable
fun StatusBadge(status: RequestStatus) {
    val color = when(status) {
        RequestStatus.PENDING -> MaterialTheme.colorScheme.outline
        RequestStatus.APPROVED -> Color(0xFF4CAF50)
        RequestStatus.REJECTED -> MaterialTheme.colorScheme.error
    }
    Surface(color = color.copy(0.1f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, color.copy(0.5f))) {
        Text(status.name, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
fun ZeroToleranceMonitor(onStatus: (Boolean, String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val faceDetector = remember { FaceDetection.getClient(FaceDetectorOptions.Builder().build()) }
    val labeler = remember { ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.55f).build()) }

    Box(Modifier.size(1.dp).alpha(0f)) {
        AndroidView(factory = { ctx ->
            val previewView = PreviewView(ctx)
            ProcessCameraProvider.getInstance(ctx).addListener({
                val cameraProvider = try { ProcessCameraProvider.getInstance(ctx).get() } catch (e: Exception) { null } ?: return@addListener
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor, object : ImageAnalysis.Analyzer {
                    @androidx.camera.core.ExperimentalGetImage
                    override fun analyze(proxy: ImageProxy) {
                        analyzeFrame(proxy, faceDetector, labeler, onStatus)
                    }
                })
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        })
    }
}

@androidx.camera.core.ExperimentalGetImage
private fun analyzeFrame(proxy: ImageProxy, faceDetector: com.google.mlkit.vision.face.FaceDetector, labeler: com.google.mlkit.vision.label.ImageLabeler, onStatus: (Boolean, String) -> Unit) {
    val image = proxy.image?.let { InputImage.fromMediaImage(it, proxy.imageInfo.rotationDegrees) }
    if (image != null) {
        // Analisi manuale della luminosità per rilevare l'oscuramento (dito sulla camera)
        val buffer = proxy.planes[0].buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        
        var totalBrightness = 0L
        val step = 10 // Campionamento per performance
        var samples = 0
        for (i in data.indices step step) {
            totalBrightness += (data[i].toInt() and 0xFF)
            samples++
        }
        val avgBrightness = if (samples > 0) totalBrightness / samples else 0
        
        // Se la camera è coperta (avgBrightness molto basso), blocchiamo immediatamente
        val isObscured = avgBrightness < 35 

        labeler.process(image).addOnSuccessListener { labels ->
            val detectedThreat = labels.any { label ->
                val text = label.text.lowercase()
                val conf = label.confidence
                
                val isPhone = (text.contains("phone") || text.contains("cellular") || text.contains("mobile")) && conf > 0.45f
                val isGadget = (text.contains("gadget") || text.contains("electronic")) && conf > 0.60f
                val isCamera = (text.contains("camera") || text.contains("lens")) && conf > 0.50f
                
                isPhone || isGadget || isCamera
            }
            
            // L'app si blocca se viene rilevato un telefono O se la camera è coperta
            onStatus(detectedThreat || isObscured, "")
        }.addOnCompleteListener { proxy.close() }
    } else proxy.close()
}

@Composable
fun SecurityLockOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Schermata completamente nera come richiesto
    }
}
