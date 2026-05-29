package com.example

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.NewsDraft
import com.example.ui.LiveNews
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.NewsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen()
            }
        }
    }
}

@SuppressLint("KeySourceViolation")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: NewsViewModel = viewModel()) {
    val context = LocalContext.current
    val drafts by viewModel.allDrafts.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    
    // Clipboard helper
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    // Handles back presses to navigate WebView history if possible
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var webCanGoBack by remember { mutableStateOf(false) }

    if (viewModel.currentTab == 2 && webCanGoBack) {
        BackHandler {
            webViewRef?.goBack()
        }
    }

    // Toast messages synchronization
    LaunchedEffect(viewModel.toastMessage) {
        viewModel.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.toastMessage = null // Reset message
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Newspaper,
                            contentDescription = "Logo UPM Millenium",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "UPM MILLENIUM",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://upm-millenium.com/"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Tidak ada aplikasi browser untuk membuka link ini.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("action_open_web")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Buka Website Utama"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                windowInsets = WindowInsets.navigationBars,
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = viewModel.currentTab == 0,
                    onClick = { viewModel.currentTab = 0 },
                    icon = {
                        Icon(
                            imageVector = if (viewModel.currentTab == 0) Icons.Filled.Dashboard else Icons.Outlined.Dashboard,
                            contentDescription = "Dabor"
                        )
                    },
                    label = { Text("Dabor") },
                    modifier = Modifier.testTag("nav_dashboard")
                )
                NavigationBarItem(
                    selected = viewModel.currentTab == 1,
                    onClick = { viewModel.currentTab = 1 },
                    icon = {
                        Icon(
                            imageVector = if (viewModel.currentTab == 1) Icons.Filled.EditNote else Icons.Outlined.EditNote,
                            contentDescription = "Tulis Berita"
                        )
                    },
                    label = { Text("Tulis Berita") },
                    modifier = Modifier.testTag("nav_editor")
                )
                NavigationBarItem(
                    selected = viewModel.currentTab == 2,
                    onClick = { viewModel.currentTab = 2 },
                    icon = {
                        Icon(
                            imageVector = if (viewModel.currentTab == 2) Icons.Filled.AdminPanelSettings else Icons.Outlined.AdminPanelSettings,
                            contentDescription = "Portal Admin"
                        )
                    },
                    label = { Text("Admin Web") },
                    modifier = Modifier.testTag("nav_admin")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (viewModel.currentTab) {
                0 -> {
                    DashboardScreen(
                        viewModel = viewModel,
                        drafts = drafts,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        onLoadDraft = { viewModel.loadDraftToEditor(it) },
                        onDeleteDraft = { viewModel.deleteDraft(it) },
                        onCopyDraft = { draft ->
                            val textToCopy = "JUDUL: ${draft.title}\n\nKATEGORI: ${draft.category}\n\nKONTEN:\n${draft.content}"
                            clipboardManager.setPrimaryClip(ClipData.newPlainText("Draf Berita", textToCopy))
                            Toast.makeText(context, "Draf disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                1 -> {
                    EditorScreen(
                        viewModel = viewModel,
                        clipboardManager = clipboardManager
                    )
                }
                2 -> {
                    AdminPortalScreen(
                        viewModel = viewModel,
                        clipboardManager = clipboardManager,
                        onWebViewCreated = {
                            webViewRef = it
                        },
                        onNavigationStateChanged = { canGoBack ->
                            webCanGoBack = canGoBack
                        }
                    )
                }
            }
        }
    }
}

/**
 * --------------------------------------------------------------------------
 * 1. DASHBOARD SCREEN (DABOR REDAKSI)
 * --------------------------------------------------------------------------
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DashboardScreen(
    viewModel: NewsViewModel,
    drafts: List<NewsDraft>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onLoadDraft: (NewsDraft) -> Unit,
    onDeleteDraft: (NewsDraft) -> Unit,
    onCopyDraft: (NewsDraft) -> Unit
) {
    val context = LocalContext.current
    var isBriefingExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcoming Headline Banner
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Portal Redaksi UPM",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondary)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Reporter",
                                color = MaterialTheme.colorScheme.onSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "Selamat bekerja, jurnalis mahasiswa! Rancang draf liputan Anda dengan asisten AI, lalu unggah draf tersebut langsung ke Joomla Administrator tanpa repot login browser.",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // Horizontal slider for Live Published News Feed (Simulated scrape from upm-millenium.com)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kabar Terkini (upm-millenium.com)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                if (viewModel.liveNewsLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { viewModel.fetchLiveNews() }
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (viewModel.liveNewsLoading && viewModel.liveNewsList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(viewModel.liveNewsList) { news ->
                        Card(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(news.url))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Tidak ada aplikasi browser untuk membuka link ini.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier
                                .width(260.dp)
                                .height(130.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = news.category,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = news.date,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = news.title,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "Tap untuk baca →",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Journalistic Guidelines Quick Read Accordion
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isBriefingExpanded = !isBriefingExpanded }
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Gavel,
                                contentDescription = "Panduan",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Kode Etik Editorial Pers Mahasiswa",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = if (isBriefingExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (isBriefingExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "1. Penyajian Akurat: Konfirmasikan fakta berita di lapangan min. 2 narasumber yang kredibel.\n" +
                                   "2. Berimbang & Adil: Hindari memojokkan satu pihak secara subjektif, utamakan klarifikasi (cover both sides).\n" +
                                   "3. Independen: Berani menyuarakan kebenaran civitas akademik tanpa tekanan intervensi birokrasi kampus.\n" +
                                   "4. Kaidah KBBI: Penulisan draf wajib beralih ke bahasa formal-intelektual (Manfaatkan Tombol Koreksi AI di Tab samping!).",
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Local Draft List Header
        item {
            Text(
                text = "Daftar Draf Liputan Lokal",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Search text field
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_drafts_input"),
                placeholder = { Text("Cari draf tulisan Anda...", fontSize = 13.sp) },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Cari") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Bersihkan")
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                singleLine = true
            )
        }

        // List elements
        val filteredDrafts = drafts.filter {
            it.title.contains(searchQuery, ignoreCase = true) || 
            it.category.contains(searchQuery, ignoreCase = true) ||
            it.content.contains(searchQuery, ignoreCase = true)
        }

        if (filteredDrafts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.DriveFileRenameOutline,
                            contentDescription = "Belum Ada Draf",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "Belum ada draf berita tersimpan." else "Tidak ada draf yang cocok.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        if (searchQuery.isEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.currentTab = 1 },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Mulai Menulis", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        } else {
            items(filteredDrafts, key = { it.id }) { draft ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("draft_item_${draft.id}"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when (draft.category) {
                                            "Opini" -> MaterialTheme.colorScheme.secondaryContainer
                                            "Sastra" -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                val labelColor = when (draft.category) {
                                    "Opini" -> MaterialTheme.colorScheme.onSecondaryContainer
                                    "Sastra" -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Text(
                                    text = draft.category,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = labelColor
                                )
                            }

                            Row {
                                IconButton(
                                    onClick = { onCopyDraft(draft) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Salin Draf",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteDraft(draft) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Hapus Draf",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = draft.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clickable { onLoadDraft(draft) }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (draft.content.isBlank()) "Tanpa isi draf..." else draft.content,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Diperbarui: " + java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(draft.lastUpdated),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            Button(
                                onClick = { onLoadDraft(draft) },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Edit & Poles AI", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * --------------------------------------------------------------------------
 * 2. EDITOR SCREEN WITH GEMINI AI ASSISTANT (TULIS BERITA)
 * --------------------------------------------------------------------------
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: NewsViewModel,
    clipboardManager: ClipboardManager
) {
    val context = LocalContext.current
    val categories = listOf("Berita Kampus", "Opini", "Sastra", "Rilis Pers", "Liputan Khusus")
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Workspace state alert
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (viewModel.editorId != null) "📝 Mode Edit Draf (#${viewModel.editorId})" else "📝 Draf Liputan Baru",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                if (viewModel.editorId != null || viewModel.editorTitle.isNotEmpty() || viewModel.editorContent.isNotEmpty()) {
                    Text(
                        text = "Reset Editor",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .clickable { viewModel.createNewDraft() }
                            .padding(4.dp)
                    )
                }
            }
        }

        // Category dropdown and Title Action
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { categoryDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Kategori: ${viewModel.editorCategory}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }
                    DropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false }
                    ) {
                        categories.forEach { categoryName ->
                            DropdownMenuItem(
                                text = { Text(categoryName) },
                                onClick = {
                                    viewModel.editorCategory = categoryName
                                    categoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Button(
                    onClick = { viewModel.saveDraft() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Simpan", fontSize = 12.sp)
                }
            }
        }

        // Title Input Field
        item {
            OutlinedTextField(
                value = viewModel.editorTitle,
                onValueChange = { viewModel.editorTitle = it },
                label = { Text("Kepala Berita / Judul Utama") },
                placeholder = { Text("Masukkan judul liputan berita mahasiswa...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_title_input"),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                singleLine = true
            )
        }

        // Sub Judul Input Field
        item {
            OutlinedTextField(
                value = viewModel.editorSubTitle,
                onValueChange = { viewModel.editorSubTitle = it },
                label = { Text("Sub Judul / Keterangan Tambahan") },
                placeholder = { Text("Masukkan sub judul artikel jika ada...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_subtitle_input"),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                singleLine = true
            )
        }

        // Penulis Input Field
        item {
            OutlinedTextField(
                value = viewModel.editorAuthor,
                onValueChange = { viewModel.editorAuthor = it },
                label = { Text("Penulis / Reporter Liputan") },
                placeholder = { Text("Masukkan nama reporter atau jurnalis...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_author_input"),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                singleLine = true
            )
        }

        // Content Input Field
        item {
            OutlinedTextField(
                value = viewModel.editorContent,
                onValueChange = { viewModel.editorContent = it },
                label = { Text("Konten Liputan / Catatan Berita") },
                placeholder = { Text("Tulis draf Anda di sini atau masukkan catatan poin kasar untuk ditata oleh Asisten AI...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 340.dp)
                    .testTag("editor_content_input"),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                maxLines = 15
            )
        }

        // Editorial AI Assistant Panel Header
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Asisten AI",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Asisten AI Redaksi (Gemini ✨)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Gunakan asisten kecerdasan buatan bimbingan redaksi untuk menstrukturkan fakta berita (5W+1H), memoles ejaan ke bahasa formal (KBBI), atau meracik judul clicky.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Buttons of AI operations
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { viewModel.askGeminiAssistant(1) },
                            label = { Text("Metode 5W+1H", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.FormatAlignLeft,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            enabled = !viewModel.aiLoading
                        )

                        AssistChip(
                            onClick = { viewModel.askGeminiAssistant(2) },
                            label = { Text("Koreksi KBBI/Typo", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Spellcheck,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            enabled = !viewModel.aiLoading
                        )

                        AssistChip(
                            onClick = { viewModel.askGeminiAssistant(3) },
                            label = { Text("Ide 3 Judul Tajam", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            enabled = !viewModel.aiLoading
                        )
                    }
                }
            }
        }

        // AI Response Output Layout
        item {
            AnimatedVisibility(
                visible = viewModel.aiLoading || viewModel.aiResult != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Hasil Konsultasi AI Redaksi",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (viewModel.aiResult != null && !viewModel.aiResult!!.startsWith("[ERROR]")) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setPrimaryClip(ClipData.newPlainText("AI Poles", viewModel.aiResult))
                                            Toast.makeText(context, "Berhasil disalin!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Salin",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.applyAiResultToEditor() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoveToInbox,
                                            contentDescription = "Terapkan ke Editor",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (viewModel.aiLoading) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Komunikasi bimbingan AI sedang berlangsung...", fontSize = 12.sp)
                            }
                        } else {
                            viewModel.aiResult?.let { text ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = text,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                
                                if (!text.startsWith("[ERROR]")) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Tip: Gunakan tombol panah masuk di kanan atas untuk menyematkan hasil polesan ini langsung menggantikan konten editor Anda.",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sticky Footer Copy Board Toolbox
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Toolbox Copy-Publish",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (viewModel.editorTitle.isBlank()) {
                                    Toast.makeText(context, "Judul kosong!", Toast.LENGTH_SHORT).show()
                                } else {
                                    clipboardManager.setPrimaryClip(ClipData.newPlainText("Judul", viewModel.editorTitle))
                                    Toast.makeText(context, "Judul disalin!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Text("Salin Judul", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (viewModel.editorContent.isBlank()) {
                                    Toast.makeText(context, "Isi konten kosong!", Toast.LENGTH_SHORT).show()
                                } else {
                                    clipboardManager.setPrimaryClip(ClipData.newPlainText("Isi Konten", viewModel.editorContent))
                                    Toast.makeText(context, "Konten disalin!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Text("Salin Konten", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                viewModel.saveDraft()
                                // redirect to Tab 2 (WebView Portal)
                                viewModel.currentTab = 2
                            },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Text("Simpan & Go Admin →", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * --------------------------------------------------------------------------
 * 3. WEBPORTAL ADMINISTRATOR WRAPPED WEBVIEW (https://upm-millenium.com/administrator)
 * --------------------------------------------------------------------------
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AdminPortalScreen(
    viewModel: NewsViewModel,
    clipboardManager: ClipboardManager,
    onWebViewCreated: (WebView) -> Unit,
    onNavigationStateChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var progressVal by remember { mutableIntStateOf(0) }
    var pageTitle by remember { mutableStateOf("Memuat...") }
    var activeDraftExpanded by remember { mutableStateOf(true) }
    var webViewRefLocal by remember { mutableStateOf<WebView?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Floating draft helper ribbon at the very top of WebView so reporters can copy their content in one click!
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Attachment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Klip Draf Aktif: " + if (viewModel.editorTitle.isNotBlank()) {
                                if (viewModel.editorTitle.length > 28) viewModel.editorTitle.take(25) + "..." else viewModel.editorTitle
                            } else "Belum Ada Draf Dipilih",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Row {
                        if (viewModel.editorTitle.isNotBlank() || viewModel.editorContent.isNotBlank()) {
                            Text(
                                text = if (activeDraftExpanded) "Sembunyikan" else "Buka Draf",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { activeDraftExpanded = !activeDraftExpanded }
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }
                }

                if (activeDraftExpanded && (viewModel.editorTitle.isNotBlank() || viewModel.editorContent.isNotBlank())) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                clipboardManager.setPrimaryClip(ClipData.newPlainText("Judul Joomla", viewModel.editorTitle))
                                Toast.makeText(context, "Judul draf disalin!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                        ) {
                            Text("📋 Judul", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }

                        if (viewModel.editorSubTitle.isNotBlank()) {
                            Button(
                                onClick = {
                                    clipboardManager.setPrimaryClip(ClipData.newPlainText("Subjudul Joomla", viewModel.editorSubTitle))
                                    Toast.makeText(context, "Sub-judul draf disalin!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(26.dp)
                            ) {
                                Text("📋 Subjudul", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = {
                                val textToCopy = if (viewModel.editorAuthor.isNotBlank()) {
                                    "${viewModel.editorContent}\n\n*Penulis: ${viewModel.editorAuthor}*"
                                } else {
                                    viewModel.editorContent
                                }
                                clipboardManager.setPrimaryClip(ClipData.newPlainText("Konten Joomla", textToCopy))
                                Toast.makeText(context, "Artikel draf disalin! (Termasuk nama penulis bila diisi)", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(26.dp)
                        ) {
                            Text("📋 Isi berita", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(
                            onClick = {
                                viewModel.currentTab = 1
                            },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit kembali",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Loader Progress Indicator
        if (progressVal < 100) {
            LinearProgressIndicator(
                progress = { progressVal / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        // Embedded Browser Container
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("admin_webview"),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            cacheMode = WebSettings.LOAD_DEFAULT
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                progressVal = 100
                                pageTitle = view?.title ?: "Joomla Administrator"
                                onNavigationStateChanged(view?.canGoBack() ?: false)
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                // Allow standard navigation in-app
                                if (url != null && url.startsWith("https://upm-millenium.com/administrator")) {
                                    return false
                                }
                                // External link safety wrapper
                                if (url != null) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        ctx.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(ctx, "Tidak ada aplikasi browser untuk membuka link ini.", Toast.LENGTH_SHORT).show()
                                    }
                                    return true
                                }
                                return false
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                progressVal = newProgress
                            }
                        }

                        // Load direct link requested by user: upm-millenium.com/administrator
                        loadUrl("https://upm-millenium.com/administrator")
                        webViewRefLocal = this
                        onWebViewCreated(this)
                    }
                }
            )
        }

        // Web Browser controller footer bar
        Surface(
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconButton(
                        onClick = { webViewRefLocal?.goBack() },
                        enabled = webViewRefLocal?.canGoBack() ?: false
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Kembali web",
                            tint = if (webViewRefLocal?.canGoBack() == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }

                    IconButton(
                        onClick = { webViewRefLocal?.goForward() },
                        enabled = webViewRefLocal?.canGoForward() ?: false
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Maju web",
                            tint = if (webViewRefLocal?.canGoForward() == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }

                    IconButton(
                        onClick = { webViewRefLocal?.reload() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Segarkan web",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Button(
                    onClick = {
                        webViewRefLocal?.loadUrl("https://upm-millenium.com/administrator")
                    },
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(imageVector = Icons.Default.Home, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Utama Admin", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
