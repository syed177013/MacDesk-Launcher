package com.example.macdesk

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DesktopViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepo = SettingsRepository(application)
    private val appRepo = AppRepository(application)

    val windowManager = WindowManager()
    val windows = windowManager.windows

    val settings: StateFlow<DesktopSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, DesktopSettings())

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()
    private val _packs = MutableStateFlow<List<IconPackInfo>>(emptyList())
    val iconPacks: StateFlow<List<IconPackInfo>> = _packs.asStateFlow()
    private val _running = MutableStateFlow<List<String>>(emptyList())
    val running: StateFlow<List<String>> = _running.asStateFlow()
    val openPrefs = MutableStateFlow(false)
    private val _launchpad = MutableStateFlow(false)
    val launchpadVisible: StateFlow<Boolean> = _launchpad.asStateFlow()

    private var loadJob: Job? = null
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { refreshApps(); refreshPacks() }
    }

    init {
        // Reload icons whenever the chosen icon pack changes (first emission = persisted value).
        viewModelScope.launch {
            settingsRepo.settings.map { it.iconPack }.distinctUntilChanged().collect { refreshApps(it) }
        }
        refreshPacks()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        }
        ContextCompat.registerReceiver(application, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onCleared() { runCatching { getApplication<Application>().unregisterReceiver(packageReceiver) } }

    fun refreshApps(pack: String? = settings.value.iconPack) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _apps.value = appRepo.loadApps(pack)
            val installed = _apps.value.map { it.packageName }.toSet()
            _running.update { l -> l.filter { it in installed } }
        }
    }
    fun refreshPacks() {
        viewModelScope.launch { _packs.value = withContext(Dispatchers.IO) { listIconPacks(getApplication()) } }
    }

    fun setLaunchpad(v: Boolean) { _launchpad.value = v }
    fun launch(app: AppInfo, bounds: Rect?) {
        appRepo.launch(app, bounds)
        _running.update { (it - app.packageName) + app.packageName }
        _launchpad.value = false
    }
    /** Ends the app's background processes and removes it from the dock's running section. */
    fun quit(pkg: String) {
        runCatching { getApplication<Application>().getSystemService(android.app.ActivityManager::class.java).killBackgroundProcesses(pkg) }
        _running.update { it - pkg }
    }
    fun toggleDesktop(pkg: String) = viewModelScope.launch {
        val cur = settings.value.desktopApps
        settingsRepo.setDesktopApps(if (pkg in cur) cur - pkg else cur + pkg)
    }
    fun setFinderPrefs(p: FinderPrefs) = viewModelScope.launch { settingsRepo.setFinderPrefs(p) }
    fun setWindowHintDone() = viewModelScope.launch { settingsRepo.setWindowHintDone() }

    fun togglePin(pkg: String) = viewModelScope.launch {
        val cur = settings.value.pinnedPackages
        settingsRepo.setPinned(if (pkg in cur) cur - pkg else cur + pkg)
    }
    fun toggleDark(effective: Boolean) = viewModelScope.launch { settingsRepo.setDarkMode(!effective) }
    fun setDark(v: Boolean) = viewModelScope.launch { settingsRepo.setDarkMode(v) }
    fun setWallpaper(uri: String?) = viewModelScope.launch { settingsRepo.setWallpaper(uri) }
    fun setDockScale(v: Float) = viewModelScope.launch { settingsRepo.setDockScale(v) }
    fun setMenuBarScale(v: Float) = viewModelScope.launch { settingsRepo.setMenuBarScale(v) }
    fun movePin(pkg: String, delta: Int) = viewModelScope.launch {
        val l = settings.value.pinnedPackages.toMutableList()
        val i = l.indexOf(pkg); val j = i + delta
        if (i >= 0 && j in l.indices) { l[i] = l[j].also { l[j] = l[i] }; settingsRepo.setPinned(l) }
    }
    fun setDesktopIcons(v: Boolean) = viewModelScope.launch { settingsRepo.setDesktopIcons(v) }
    fun setWindowCorner(v: Int) = viewModelScope.launch { settingsRepo.setWindowCorner(v) }
    fun setWindowShadows(v: Boolean) = viewModelScope.launch { settingsRepo.setWindowShadows(v) }
    fun setOpenInWindows(v: Boolean) = viewModelScope.launch { settingsRepo.setOpenInWindows(v) }
    fun setIconPack(pkg: String?) = viewModelScope.launch { settingsRepo.setIconPack(pkg) }
    fun setOnboardingDone() = viewModelScope.launch { settingsRepo.setOnboardingDone(true) }

    fun addStorageRoot(root: StorageRoot) = viewModelScope.launch {
        val cur = settings.value.storageRoots
        if (cur.none { it.uri == root.uri }) settingsRepo.setRoots(cur + root)
    }
    fun removeStorageRoot(uri: String) = viewModelScope.launch {
        settingsRepo.setRoots(settings.value.storageRoots.filterNot { it.uri == uri })
    }
}
