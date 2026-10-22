package com.example.macdesk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.storage.StorageVolume
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

// ---------- State holder (lives as long as the window stays open, even when minimized) ----------

class FinderState(private val repo: StorageRepository, private val scope: CoroutineScope) {
    var current by mutableStateOf<Loc?>(null); private set
    var entries by mutableStateOf<List<FileEntry>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var selected by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    val back = mutableStateListOf<Loc>()
    val forward = mutableStateListOf<Loc>()
    private var job: Job? = null

    fun navigate(loc: Loc) { current?.let { back.add(it) }; forward.clear(); show(loc) }
    fun goBack() { val p = back.removeLastOrNull() ?: return; current?.let { forward.add(it) }; show(p) }
    fun goForward() { val n = forward.removeLastOrNull() ?: return; current?.let { back.add(it) }; show(n) }
    fun up() { current?.parent?.let { navigate(it) } }

    private fun show(loc: Loc) { current = loc; selected = null; reload() }

    fun reload() {
        val loc = current ?: return
        job?.cancel()
        job = scope.launch {
            loading = true
            val r = runCatching { repo.list(loc.doc) }
            if (current === loc) {
                entries = r.getOrDefault(emptyList())
                message = r.exceptionOrNull()?.let { "Can't read this folder" }
                loading = false
            }
        }
    }

    fun createFolder(name: String) = mutate { current?.doc?.createDirectory(name) != null }
    fun rename(e: FileEntry, name: String) = mutate { e.doc.renameTo(name) }
    fun delete(e: FileEntry) = mutate { e.doc.delete() }

    private fun mutate(op: () -> Boolean) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching(op).getOrDefault(false) }
            if (!ok) message = "Operation failed (is this location writable?)"
            reload()
        }
    }
}

sealed interface FinderDialog {
    data object NewFolder : FinderDialog
    data class Rename(val e: FileEntry) : FinderDialog
    data class Delete(val e: FileEntry) : FinderDialog
    data class Info(val e: FileEntry) : FinderDialog
}

// ---------- UI ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FinderApp(
    arg: String?,
    roots: List<StorageRoot>,
    onAddRoot: (StorageRoot) -> Unit,
    onRemoveRoot: (String) -> Unit,
    onOpenImage: (uri: String, name: String) -> Unit,
    onOpenMedia: (uri: String, name: String) -> Unit,
    prefs: FinderPrefs,
    onPrefs: (FinderPrefs) -> Unit
) {
    val ctx = LocalContext.current
    val colors = LocalMacColors.current
    val repo = remember { StorageRepository(ctx) }
    val scope = rememberCoroutineScope()
    val state = remember { FinderState(repo, scope) }
    var sortMenu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<FinderDialog?>(null) }

    var hasAccess by remember { mutableStateOf(repo.hasAllFilesAccess()) }
    var volumes by remember { mutableStateOf(repo.removableVolumes()) }

    fun internalLoc() = Loc(repo.internalRoot(), "Internal Storage", null, INTERNAL_ROOT)
    fun rootLoc(id: String): Loc? = when {
        id == INTERNAL_ROOT -> internalLoc()
        id == APP_ROOT -> repo.appStorage()?.let { Loc(it, "App Files", null, APP_ROOT) }
        id.startsWith("fav:") -> id.removePrefix("fav:").let { Loc(repo.folder(it), it, internalLoc(), INTERNAL_ROOT) }
        id.startsWith("vol:") -> volumes.firstOrNull { "vol:${it.uuid}" == id }
            ?.let { v -> repo.volumeRoot(v)?.let { Loc(it, v.getDescription(ctx), null, id) } }
        else -> roots.firstOrNull { it.uri == id }?.let { r -> repo.fromTree(r.uri)?.let { Loc(it, r.label, null, r.uri) } }
    }

    LaunchedEffect(Unit) { rootLoc(arg ?: INTERNAL_ROOT)?.let { state.navigate(it) } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val had = hasAccess
        hasAccess = repo.hasAllFilesAccess(); volumes = repo.removableVolumes()
        if (hasAccess && !had) state.reload()
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasAccess = repo.hasAllFilesAccess(); state.reload()
    }
    fun requestAccess() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            runCatching { ctx.startActivity(repo.allFilesIntent()) }
                .onFailure { ctx.safeStart(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        } else permLauncher.launch(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }
    LaunchedEffect(state.message) {
        state.message?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); state.message = null }
    }

    val grantLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        if (res.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            repo.persist(uri)
            val doc = repo.fromTree(uri.toString())
            val label = doc?.name ?: "Folder"
            onAddRoot(StorageRoot(uri.toString(), label))
            doc?.let { state.navigate(Loc(it, label, null, uri.toString())) }
        }
    }
    fun grant(volume: StorageVolume?) {
        runCatching { grantLauncher.launch(repo.grantIntent(volume)) }
            .onFailure { Toast.makeText(ctx, "Storage picker unavailable", Toast.LENGTH_SHORT).show() }
    }

    fun open(e: FileEntry) {
        val cur = state.current ?: return
        when {
            e.isDir -> state.navigate(Loc(e.doc, e.name, cur, cur.rootId))
            e.isImage -> onOpenImage(e.doc.uri.toString(), e.name)
            e.isVideo || e.isAudio -> onOpenMedia(e.doc.uri.toString(), e.name)
            else -> openFile(ctx, e)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.windowBg)) {
        val sideW = if (maxWidth < 480.dp) 120.dp else 180.dp
        val wide = maxWidth > 640.dp
        val sorted = remember(state.entries, prefs) { sortEntries(state.entries, prefs) }
        Row(Modifier.fillMaxSize()) {
            // ----- Sidebar -----
            Column(
                Modifier.width(sideW).fillMaxHeight().background(colors.sidebar)
                    .verticalScroll(rememberScrollState()).padding(8.dp)
            ) {
                SideHeader("Favorites")
                SideRow("Internal Storage", Icons.Rounded.PhoneAndroid,
                    state.current?.rootId == INTERNAL_ROOT && state.current?.parent == null,
                    onClick = { state.navigate(internalLoc()) })
                repo.favoriteFolders().forEach { f ->
                    SideRow(f, favIcon(f), state.current?.let { it.rootId == INTERNAL_ROOT && it.parent != null && it.name == f } == true,
                        onClick = { rootLoc("fav:$f")?.let { state.navigate(it) } })
                }
                SideRow("App Files", Icons.Rounded.Storage, state.current?.rootId == APP_ROOT, onClick = { rootLoc(APP_ROOT)?.let { state.navigate(it) } })
                SideHeader("Locations")
                volumes.forEach { v ->
                    val id = "vol:${v.uuid}"
                    if (repo.volumeRoot(v) != null) SideRow(v.getDescription(ctx), Icons.Rounded.Usb, state.current?.rootId == id, onClick = { rootLoc(id)?.let { state.navigate(it) } })
                    else if (roots.none { r -> v.uuid != null && r.uri.contains(v.uuid!!) }) SideRow("Connect ${v.getDescription(ctx)}", Icons.Rounded.Usb, false, onClick = { grant(v) })
                }
                roots.forEach { r ->
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        SideRow(r.label, Icons.Rounded.Folder, state.current?.rootId == r.uri,
                            onClick = { rootLoc(r.uri)?.let { state.navigate(it) } }, onLongClick = { menu = true })
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Remove from sidebar") }, onClick = { menu = false; onRemoveRoot(r.uri) })
                        }
                    }
                }
                SideRow("Add Folder…", Icons.Rounded.CreateNewFolder, false, onClick = { grant(null) })
            }
            VerticalDivider(color = colors.divider)

            // ----- Main pane -----
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ state.goBack() }, enabled = state.back.isNotEmpty()) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    IconButton({ state.goForward() }, enabled = state.forward.isNotEmpty()) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Forward") }
                    IconButton({ state.up() }, enabled = state.current?.parent != null) { Icon(Icons.Rounded.ArrowUpward, "Enclosing folder") }
                    Text(
                        state.current?.path?.joinToString("  ›  ") ?: "", Modifier.weight(1f).padding(horizontal = 6.dp),
                        color = colors.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Box {
                        IconButton({ sortMenu = true }) { Icon(Icons.Rounded.SwapVert, "Sort") }
                        DropdownMenu(sortMenu, { sortMenu = false }) {
                            Text("SORT BY", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontSize = 10.sp, color = colors.secondary)
                            SortKey.entries.forEach { k ->
                                DropdownMenuItem(
                                    text = { Text(k.label) },
                                    leadingIcon = { if (prefs.sortKey == k) Icon(Icons.Rounded.Check, null) else Spacer(Modifier.size(24.dp)) },
                                    onClick = { sortMenu = false; onPrefs(prefs.copy(sortKey = k)) }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Ascending") },
                                leadingIcon = { if (prefs.ascending) Icon(Icons.Rounded.Check, null) else Spacer(Modifier.size(24.dp)) },
                                onClick = { sortMenu = false; onPrefs(prefs.copy(ascending = true)) })
                            DropdownMenuItem(text = { Text("Descending") },
                                leadingIcon = { if (!prefs.ascending) Icon(Icons.Rounded.Check, null) else Spacer(Modifier.size(24.dp)) },
                                onClick = { sortMenu = false; onPrefs(prefs.copy(ascending = false)) })
                        }
                    }
                    IconButton({ onPrefs(prefs.copy(grid = !prefs.grid)) }) {
                        Icon(if (prefs.grid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView, "View mode")
                    }
                    IconButton({ dialog = FinderDialog.NewFolder }, enabled = state.current != null) {
                        Icon(Icons.Rounded.CreateNewFolder, "New folder")
                    }
                }
                HorizontalDivider(color = colors.divider)

                val bgItems = listOf(
                    MenuEntry("New Folder", state.current != null) { dialog = FinderDialog.NewFolder },
                    MenuEntry("Refresh") { state.reload() },
                    MenuEntry.Divider,
                    MenuEntry(if (prefs.grid) "View as List" else "View as Icons") { onPrefs(prefs.copy(grid = !prefs.grid)) },
                    MenuEntry("Sort by Name") { onPrefs(prefs.copy(sortKey = SortKey.NAME)) },
                    MenuEntry("Sort by Date Modified") { onPrefs(prefs.copy(sortKey = SortKey.DATE)) },
                    MenuEntry("Sort by Size") { onPrefs(prefs.copy(sortKey = SortKey.SIZE)) },
                    MenuEntry("Sort by Kind") { onPrefs(prefs.copy(sortKey = SortKey.KIND)) }
                )
                ContextMenuHost(bgItems, Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        !hasAccess && (state.current?.let { it.doc.uri.scheme == "file" && it.rootId != APP_ROOT } ?: true) ->
                            PermissionPrompt(::requestAccess)
                        state.current == null -> Hint("Choose a location from the sidebar")
                        state.loading && state.entries.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        state.entries.isEmpty() -> Hint("This folder is empty")
                        prefs.grid -> LazyVerticalGrid(GridCells.Adaptive(96.dp), contentPadding = PaddingValues(10.dp)) {
                            items(sorted, key = { it.doc.uri.toString() }) { e ->
                                EntryItem(e, state.selected == e.doc.uri.toString(), ctx, true, false, { state.selected = e.doc.uri.toString() },
                                    { open(e) }, { dialog = FinderDialog.Rename(e) }, { dialog = FinderDialog.Delete(e) }, { dialog = FinderDialog.Info(e) })
                            }
                        }
                        else -> Column(Modifier.fillMaxSize()) {
                            ListHeader(prefs, wide, onPrefs)
                            LazyColumn(Modifier.weight(1f)) {
                                items(sorted, key = { it.doc.uri.toString() }) { e ->
                                    EntryItem(e, state.selected == e.doc.uri.toString(), ctx, false, wide, { state.selected = e.doc.uri.toString() },
                                        { open(e) }, { dialog = FinderDialog.Rename(e) }, { dialog = FinderDialog.Delete(e) }, { dialog = FinderDialog.Info(e) })
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = colors.divider)
                Text(
                    state.selected?.let { sel -> sorted.firstOrNull { it.doc.uri.toString() == sel }?.let { "“${it.name}” selected  ·  ${sorted.size} items" } }
                        ?: "${sorted.size} items",
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = colors.secondary, fontSize = 11.sp
                )
            }
        }
    }

    when (val d = dialog) {
        FinderDialog.NewFolder -> NameDialog("New Folder", "untitled folder", "Create", { state.createFolder(it); dialog = null }) { dialog = null }
        is FinderDialog.Rename -> NameDialog("Rename", d.e.name, "Rename", { state.rename(d.e, it); dialog = null }) { dialog = null }
        is FinderDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete “${d.e.name}”?") },
            text = { Text("This can't be undone.") },
            confirmButton = { TextButton({ state.delete(d.e); dialog = null }) { Text("Delete") } },
            dismissButton = { TextButton({ dialog = null }) { Text("Cancel") } }
        )
        is FinderDialog.Info -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(d.e.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Kind: ${kindOf(d.e)}")
                    Text("Size: ${if (d.e.isDir) "—" else Formatter.formatFileSize(ctx, d.e.size)}")
                    Text("Modified: ${if (d.e.modified > 0) DateFormat.getDateTimeInstance().format(Date(d.e.modified)) else "—"}")
                    Text("Where: ${d.e.doc.uri.path ?: d.e.doc.uri}", fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton({ dialog = null }) { Text("OK") } }
        )
        null -> Unit
    }
}

private val DATE_W = 96.dp
private val SIZE_W = 64.dp
private val KIND_W = 108.dp

fun kindOf(e: FileEntry): String = when {
    e.isDir -> "Folder"; e.isImage -> "Image"; e.isVideo -> "Video"; e.isAudio -> "Audio"
    e.mime == "application/pdf" -> "PDF Document"; e.mime?.startsWith("text/") == true -> "Text"
    else -> e.name.substringAfterLast('.', "").uppercase().let { if (it.isEmpty() || it.length > 5) "Document" else "$it File" }
}

/** Folders always stay on top (like Finder's "Keep folders on top"). */
fun sortEntries(list: List<FileEntry>, p: FinderPrefs): List<FileEntry> {
    val cmp: Comparator<FileEntry> = when (p.sortKey) {
        SortKey.NAME -> compareBy<FileEntry> { it.name.lowercase() }
        SortKey.DATE -> compareBy<FileEntry> { it.modified }.thenBy { it.name.lowercase() }
        SortKey.SIZE -> compareBy<FileEntry> { if (it.isDir) -1L else it.size }.thenBy { it.name.lowercase() }
        SortKey.KIND -> compareBy<FileEntry> { kindOf(it) }.thenBy { it.name.lowercase() }
    }
    return list.sortedWith(if (p.ascending) cmp else cmp.reversed()).sortedByDescending { it.isDir }
}

@Composable
private fun ListHeader(prefs: FinderPrefs, showKind: Boolean, onPrefs: (FinderPrefs) -> Unit) {
    val c = LocalMacColors.current
    @Composable
    fun Col(key: SortKey, mod: Modifier, align: TextAlign = TextAlign.Start) {
        val active = prefs.sortKey == key
        Text(
            key.label.removeSuffix(" Modified") + if (active) (if (prefs.ascending) "  ▲" else "  ▼") else "",
            mod.clickable { onPrefs(if (active) prefs.copy(ascending = !prefs.ascending) else prefs.copy(sortKey = key, ascending = true)) }.padding(vertical = 6.dp),
            color = if (active) c.accent else c.secondary, fontSize = 11.sp, maxLines = 1, textAlign = align
        )
    }
    Row(Modifier.fillMaxWidth().background(c.sidebar).padding(horizontal = 12.dp)) {
        Spacer(Modifier.width(38.dp))
        Col(SortKey.NAME, Modifier.weight(1f))
        Col(SortKey.DATE, Modifier.width(DATE_W))
        Col(SortKey.SIZE, Modifier.width(SIZE_W), TextAlign.End)
        if (showKind) Col(SortKey.KIND, Modifier.width(KIND_W).padding(start = 12.dp))
    }
    HorizontalDivider(color = c.divider)
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    val c = LocalMacColors.current
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.FolderOpen, null, Modifier.size(48.dp), tint = c.accent)
        Spacer(Modifier.height(12.dp))
        Text("Allow access to your files", color = c.text, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text("Finder needs “All files access” to browse internal storage and USB drives.", color = c.secondary, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Button(onGrant) { Text("Grant Access") }
    }
}

private fun favIcon(name: String): ImageVector = when (name) {
    "Download" -> Icons.Rounded.Download; "DCIM" -> Icons.Rounded.PhotoCamera; "Pictures" -> Icons.Rounded.Image
    "Documents" -> Icons.Rounded.Description; "Music" -> Icons.Rounded.MusicNote; else -> Icons.Rounded.Movie
}

@Composable
private fun Thumb(e: FileEntry, size: androidx.compose.ui.unit.Dp) {
    val ctx = LocalContext.current
    val (icon, tint) = iconFor(e, LocalMacColors.current.accent)
    if (e.isImage || e.isVideo) {
        val req = remember(e.doc.uri) { ImageRequest.Builder(ctx).data(e.doc.uri).size(256).build() }
        AsyncImage(req, null, Modifier.size(size).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
    } else Icon(icon, null, Modifier.size(size), tint = tint)
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true) },
        confirmButton = { TextButton({ if (text.isNotBlank()) onConfirm(text.trim()) }) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = LocalMacColors.current.secondary, fontSize = 14.sp)
    }
}

@Composable
private fun SideHeader(text: String) {
    Text(
        text.uppercase(), Modifier.padding(start = 6.dp, top = 10.dp, bottom = 4.dp),
        color = LocalMacColors.current.secondary, fontSize = 10.sp
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SideRow(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val colors = LocalMacColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .background(if (selected) colors.selection else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = colors.accent)
        Spacer(Modifier.width(8.dp))
        Text(label, color = colors.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryItem(
    e: FileEntry, selected: Boolean, ctx: Context, grid: Boolean, showKind: Boolean,
    onSelect: () -> Unit, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, onInfo: () -> Unit
) {
    val colors = LocalMacColors.current
    var menu by remember { mutableStateOf(false) }
    val click = Modifier.combinedClickable(onClick = onSelect, onDoubleClick = onOpen, onLongClick = { onSelect(); menu = true })
    val bg = if (selected) colors.selection else Color.Transparent

    Box(Modifier.onSecondaryClick { onSelect(); menu = true }) {
        if (grid) {
            Column(
                Modifier.padding(4.dp).clip(RoundedCornerShape(8.dp)).background(bg).then(click).padding(8.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Thumb(e, 56.dp)
                Text(e.name, color = colors.text, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        } else {
            Row(Modifier.fillMaxWidth().background(bg).then(click).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Thumb(e, 28.dp)
                Spacer(Modifier.width(10.dp))
                Text(e.name, Modifier.weight(1f), color = colors.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (e.modified > 0) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(e.modified)) else "—",
                    Modifier.width(DATE_W), color = colors.secondary, fontSize = 11.sp, maxLines = 1
                )
                Text(
                    if (e.isDir) "—" else Formatter.formatShortFileSize(ctx, e.size),
                    Modifier.width(SIZE_W), color = colors.secondary, fontSize = 11.sp, textAlign = TextAlign.End, maxLines = 1
                )
                if (showKind) Text(kindOf(e), Modifier.width(KIND_W).padding(start = 12.dp), color = colors.secondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text("Open") }, onClick = { menu = false; onOpen() })
            DropdownMenuItem(text = { Text("Get Info") }, onClick = { menu = false; onInfo() })
            DropdownMenuItem(text = { Text("Rename…") }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text("Delete…") }, onClick = { menu = false; onDelete() })
        }
    }
}

private fun iconFor(e: FileEntry, accent: Color): Pair<ImageVector, Color> {
    if (e.isDir) return Icons.Rounded.Folder to accent
    val m = e.mime.orEmpty()
    val gray = Color(0xFF8E8E93)
    return when {
        m.startsWith("image/") -> Icons.Rounded.Image to Color(0xFF34C759)
        m.startsWith("video/") -> Icons.Rounded.Movie to Color(0xFFFF9F0A)
        m.startsWith("audio/") -> Icons.Rounded.MusicNote to Color(0xFFFF375F)
        m == "application/pdf" -> Icons.Rounded.PictureAsPdf to Color(0xFFFF453A)
        m.startsWith("text/") -> Icons.Rounded.Description to gray
        else -> Icons.AutoMirrored.Rounded.InsertDriveFile to gray
    }
}

private fun openFile(ctx: Context, e: FileEntry) {
    runCatching {
        val raw = e.doc.uri
        val uri = if (raw.scheme == "file") FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(raw.path!!)) else raw
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, e.mime ?: "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { Toast.makeText(ctx, "No app can open this file", Toast.LENGTH_SHORT).show() }
}
