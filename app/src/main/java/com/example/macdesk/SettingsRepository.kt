package com.example.macdesk

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "mac_desktop_settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    private object K {
        val DARK = booleanPreferencesKey("dark_mode")
        val WALLPAPER = stringPreferencesKey("wallpaper_uri")
        val DOCK = floatPreferencesKey("dock_scale")
        val MENUBAR = floatPreferencesKey("menubar_scale")
        val ICONS = booleanPreferencesKey("desktop_icons")
        val CORNER = intPreferencesKey("window_corner")
        val SHADOWS = booleanPreferencesKey("window_shadows")
        val PINNED = stringPreferencesKey("pinned_packages")
        val ROOTS = stringPreferencesKey("storage_roots")
        val WINDOWS = booleanPreferencesKey("open_in_windows")
        val ICONPACK = stringPreferencesKey("icon_pack")
        val ONBOARD = booleanPreferencesKey("onboarding_done")
        val HINT = booleanPreferencesKey("window_hint_done")
        val SORT = stringPreferencesKey("finder_sort")
        val ASC = booleanPreferencesKey("finder_asc")
        val GRID = booleanPreferencesKey("finder_grid")
        val DESKTOP = stringPreferencesKey("desktop_apps")
    }

    val settings: Flow<DesktopSettings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            DesktopSettings(
                loaded = true,
                windowHintDone = p[K.HINT] ?: false,
                desktopApps = decodeList(p[K.DESKTOP]),
                finder = FinderPrefs(
                    p[K.SORT]?.let { runCatching { SortKey.valueOf(it) }.getOrNull() } ?: SortKey.NAME,
                    p[K.ASC] ?: true, p[K.GRID] ?: false
                ),
                openInWindows = p[K.WINDOWS] ?: true,
                iconPack = p[K.ICONPACK],
                onboardingDone = p[K.ONBOARD] ?: false,
                darkMode = p[K.DARK],
                wallpaperUri = p[K.WALLPAPER],
                dockScale = p[K.DOCK] ?: 1f,
                menuBarScale = p[K.MENUBAR] ?: 1f,
                showDesktopIcons = p[K.ICONS] ?: true,
                windowCornerRadius = p[K.CORNER] ?: 12,
                windowShadows = p[K.SHADOWS] ?: true,
                pinnedPackages = decodeList(p[K.PINNED]),
                storageRoots = decodeRoots(p[K.ROOTS])
            )
        }.distinctUntilChanged()

    private suspend fun <T> put(key: Preferences.Key<T>, value: T?) {
        store.edit { if (value == null) it.remove(key) else it[key] = value }
    }

    suspend fun setDarkMode(v: Boolean) = put(K.DARK, v)
    suspend fun setWallpaper(uri: String?) = put(K.WALLPAPER, uri)
    suspend fun setDockScale(v: Float) = put(K.DOCK, v.coerceIn(0.6f, 1.5f))
    suspend fun setMenuBarScale(v: Float) = put(K.MENUBAR, v.coerceIn(0.8f, 1.5f))
    suspend fun setDesktopIcons(v: Boolean) = put(K.ICONS, v)
    suspend fun setWindowCorner(v: Int) = put(K.CORNER, v.coerceIn(0, 24))
    suspend fun setWindowShadows(v: Boolean) = put(K.SHADOWS, v)
    suspend fun setOpenInWindows(v: Boolean) = put(K.WINDOWS, v)
    suspend fun setIconPack(v: String?) = put(K.ICONPACK, v)
    suspend fun setOnboardingDone(v: Boolean) = put(K.ONBOARD, v)
    suspend fun setWindowHintDone() = put(K.HINT, true)
    suspend fun setDesktopApps(list: List<String>) = put(K.DESKTOP, JSONArray(list).toString())
    suspend fun setFinderPrefs(f: FinderPrefs) {
        store.edit { it[K.SORT] = f.sortKey.name; it[K.ASC] = f.ascending; it[K.GRID] = f.grid }
    }
    suspend fun setPinned(list: List<String>) = put(K.PINNED, JSONArray(list).toString())
    suspend fun setRoots(list: List<StorageRoot>) = put(K.ROOTS, JSONArray().also { a ->
        list.forEach { a.put(JSONObject().put("u", it.uri).put("l", it.label)) }
    }.toString())

    private fun decodeList(s: String?): List<String> = runCatching {
        JSONArray(s ?: "[]").let { a -> List(a.length()) { a.getString(it) } }
    }.getOrDefault(emptyList())

    private fun decodeRoots(s: String?): List<StorageRoot> = runCatching {
        JSONArray(s ?: "[]").let { a ->
            List(a.length()) { a.getJSONObject(it).let { o -> StorageRoot(o.getString("u"), o.getString("l")) } }
        }
    }.getOrDefault(emptyList())
}
