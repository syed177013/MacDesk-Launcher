package com.example.macdesk

enum class SortKey(val label: String) { NAME("Name"), DATE("Date Modified"), SIZE("Size"), KIND("Kind") }
data class FinderPrefs(val sortKey: SortKey = SortKey.NAME, val ascending: Boolean = true, val grid: Boolean = false)

data class StorageRoot(val uri: String, val label: String)

data class DesktopSettings(
    val loaded: Boolean = false,
    val darkMode: Boolean? = null,
    val wallpaperUri: String? = null,
    val dockScale: Float = 1f,
    val menuBarScale: Float = 1f,
    val showDesktopIcons: Boolean = true,
    val windowCornerRadius: Int = 12,
    val windowShadows: Boolean = true,
    val openInWindows: Boolean = true,
    val iconPack: String? = null,
    val onboardingDone: Boolean = false,
    val windowHintDone: Boolean = false,
    val finder: FinderPrefs = FinderPrefs(),
    val desktopApps: List<String> = emptyList(),
    val pinnedPackages: List<String> = emptyList(),
    val storageRoots: List<StorageRoot> = emptyList()
)
