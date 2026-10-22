# MacDesk

**A macOS-inspired desktop launcher for Android tablets and foldables.**

MacDesk replaces your home screen with a desktop workspace: a menu bar on top, a glass dock at the bottom, floating windows in between, and a Finder-style file manager built in. It's written 100% in Kotlin and Jetpack Compose.

> **Status:** early release. It's built for tablets and foldables, and has had limited testing across devices. Issues and pull requests are very welcome.

<!-- Add screenshots here, e.g.
![Desktop](docs/screenshots/desktop.png)
![Finder](docs/screenshots/finder.png)
-->

---

## What it does

### Desktop
- **Menu bar** with an Apple-style menu, File/View/Window/Help menus, clock, Wi-Fi state, battery gauge, a Control Center popup, a light/dark switch and a **notification bell** with a live count.
- **Glass dock** with pinned apps, running-app dots, minimized windows, touch magnification and bounce-on-launch.
- **Desktop icons** for storage locations and any app you send to the desktop.
- **Wallpaper**, light/dark theme, and adjustable menu bar and dock size.
- **Right-click menus** (mouse or trackpad) on the desktop, dock, window title bars, Finder items and app icons. Long-press does the same on touch.

### Windows
- Floating, draggable, resizable windows with traffic-light controls (close, minimize, zoom).
- Tap a window to bring it to the front. Double-tap the title bar to zoom.
- Drag a window past the left or right edge to tile it to that half of the screen.
- Open, close and minimize animations.

### Built-in apps
| App | What it does |
|---|---|
| **Finder** | Browse internal storage, USB/SD drives and folders you add. List and icon views, thumbnails for images and video, sort by name, date, size or kind, new folder, rename, delete, Get Info. |
| **Preview** | View and edit images: zoom, rotate, flip, crop, draw, brightness/contrast/saturation, undo. Save a copy or replace the original. |
| **Player** | Plays video and audio files from Finder (Media3 / ExoPlayer). Pauses when minimized. |
| **Launchpad** | Searchable grid of every installed app, with pin-to-dock and add-to-desktop. |
| **System Preferences** | Appearance, wallpaper, icon pack, window mode, menu bar, dock (including pinned apps), notifications, windows and launcher settings. |

### Icon packs
MacDesk reads installed ADW, Nova, Apex, GO and Atom style icon packs. Pick one in **System Preferences → Icon Pack** and the Launchpad, dock and desktop icons all follow it. The built-in dock icons borrow the pack's icon for the matching system app (Files, Settings, Photos or Gallery, Videos) when one exists.

---

## Requirements

- Android **8.0 (API 26)** or newer. Android 11+ is recommended for the best Finder experience.
- A tablet or foldable is the target. Phones work, with windows opening maximized.
- Android Studio **Ladybug (2024.2)** or newer to build from source.

---

## Getting started

### Build and run
```bash
git clone https://github.com/<your-username>/MacDesk.git
cd MacDesk
./gradlew installDebug
```
Or open the folder in Android Studio and press **Run**.


### Make it your home screen
On first launch MacDesk offers to become your default home app. You can also do it later from the Apple menu → **Set as Default Launcher…**, or in System Preferences → Launcher.

---

## Setup guides

### 1. Let Finder see your files
Finder needs **All files access** to browse internal storage and USB drives. Open Finder and tap **Grant Access** (on Android 10 and below it asks for the normal storage permission instead). USB and SD cards then appear in the sidebar automatically on Android 11+; on older versions tap **Connect** next to the drive.

You can also add any folder through the system picker with **Add Folder…**. Android itself blocks `Android/data` and `Android/obb` on newer versions.

### 2. Open apps as real windows
Android only shows other apps as floating windows when its **freeform window mode** is on. A launcher cannot switch this on by itself, so MacDesk walks you through it in **System Preferences → Window Mode**:

- **Option 1: Developer options.** Turn on *Enable freeform windows* and *Force activities to be resizable*, then restart the device.
- **Option 2: one-tap enabling.** Run this once from a computer with USB debugging on:
  ```bash
  adb shell pm grant <your.package.name> android.permission.WRITE_SECURE_SETTINGS
  ```
  After that, the **Enable Window Mode** button in System Preferences works. (The exact command, with your package name, is shown in the app with a Copy button.)

MacDesk then launches apps with window bounds, so freeform-capable devices (DeX, Android desktop mode, or freeform enabled as above) open them as floating windows. The window frame around those apps is drawn by Android itself, not by MacDesk. If your device doesn't support freeform, apps open full-screen. MacDesk's own windows (Finder, Preview, Player, Preferences) always work.

> In my testing, The feature doesn't seem to work for any natively installed apps. most likely an android limitation imposed upon custom launchers. To get it to work is a WIP.

### 3. Show the notification count
The bell in the menu bar opens the native notification shade. To show the number of active notifications, grant **Notification access**: System Preferences → Notifications → **Grant Notification Access…**.

### 4. Use an icon pack
Install an icon pack from the Play Store, then go to System Preferences → **Icon Pack** and pick it. Changes apply to Launchpad, dock and desktop immediately.

### 5. Customize the dock
- Long-press (or right-click) an app in Launchpad → **Keep in Dock**.
- Or use System Preferences → Dock → **Add App to Dock…** and reorder with the arrows.
- Long-press (or right-click) a dock icon for Quit, Remove from Dock, Move Left/Right and Add to Desktop.

---

## Tips

| Do this | To get this |
|---|---|
| Right-click the desktop | New Finder window, wallpaper, Launchpad, preferences |
| Right-click a window title bar | Close, minimize, zoom, move to left/right half |
| Drag a window past a screen edge | Snap it to half the screen |
| Click a column header in Finder | Sort by that column; click again to reverse |
| Open an image in Finder | Edit it in Preview |
| Open a video or audio file in Finder | Play it in Player |
| Swipe down from the top edge | Reveal the Android status bar temporarily |

---

## Architecture

- **UI:** Jetpack Compose with Material 3 and window size classes (compact screens open windows maximized).
- **State:** MVVM with `StateFlow`. `DesktopViewModel` owns settings, installed apps, icon packs, running apps and the window manager.
- **Settings:** Jetpack DataStore (theme, wallpaper, sizes, pinned apps, desktop apps, Finder sort and view, icon pack).
- **Windows:** `WindowManager` keeps a list of `WindowState` (position, size, z-order, minimized, maximized). `MacWindowFrame` renders each one.
- **Glass effect:** the wallpaper is blurred once into a tiny bitmap, and each glass surface draws its slice of it plus a tint. No live blur, so it stays cheap on battery.
- **Storage:** direct file access for internal storage and volumes, and the Storage Access Framework for folders you add.
- **Media:** Coil for thumbnails (with video frames), Media3 for playback.

### Project layout
```
app/src/main/java/com/example/macdesk/
├── MainActivity.kt            Entry point, edge-to-edge, home intent handling
├── MacDeskApp.kt              Application class, Coil image loader
├── DesktopSettings.kt         Settings model
├── SettingsRepository.kt      DataStore persistence
├── DesktopViewModel.kt        App state and actions
├── AppRepository.kt           Installed apps, launching, icon-pack themed icons
├── IconPack.kt                Icon pack discovery and appfilter.xml parsing
├── StorageRepository.kt       Storage roots, volumes, file listing
├── WindowEngine.kt            Window state and manager
├── WindowMode.kt              Freeform window mode status and setup
├── MacDesktopScreen.kt        Desktop, dock and menu bar assembly
├── MenuBar.kt                 Menu bar, Control Center, notification bell
├── Dock.kt                    Dock and app icons
├── MacWindowFrame.kt          Window chrome, drag, resize, traffic lights
├── GlassSurface.kt            Frosted glass and wallpaper loading
├── ContextMenu.kt             Right-click menus
├── FinderApp.kt               File manager
├── ImageEditorApp.kt          Image viewer and editor
├── PlayerApp.kt               Video and audio player
├── LaunchpadOverlay.kt        App grid
├── SettingsApp.kt             System Preferences and About
├── MacNotificationService.kt  Notification count and shade access
└── MacTheme.kt                Colors and composition locals
```

---

## Permissions

| Permission | Why |
|---|---|
| `MANAGE_EXTERNAL_STORAGE` | Finder browses internal storage and USB drives. |
| `READ/WRITE_EXTERNAL_STORAGE` (Android 10 and below) | Same, on older systems. |
| `ACCESS_NETWORK_STATE` | Wi-Fi indicator in the menu bar. |
| `EXPAND_STATUS_BAR` | Opens the native notification shade from the bell. |
| `KILL_BACKGROUND_PROCESSES` | The dock's **Quit** option. |
| `WRITE_SECURE_SETTINGS` (optional) | One-tap Window Mode. Only works after you grant it over ADB. |
| Notification listener (user-granted) | Shows the notification count. |

MacDesk has no internet permission, no analytics and no ads. Everything stays on your device.

---

## Known limitations

- Android only lets the system draw other apps in windows, so **windowed Android apps depend on freeform mode** being enabled (see above).
- The dock keeps an app's running dot until you choose **Quit**, because Android doesn't tell launchers when an app closes.
- `Android/data` and `Android/obb` are not browsable on recent Android versions.
- If your device blocks the notification-shade call, the bell shows a built-in notification panel instead.
- Only lightly tested so far. Please report what you find, with your device and Android version.

## Contributing

1. Fork the repo and create a branch: `git checkout -b feature/your-idea`
2. Keep changes focused, and follow the existing Kotlin style.
3. Test on a real tablet if you can, and include the device and Android version in your PR.
4. Open a pull request describing what changed and why.

Bug reports are most useful with: device model, Android version, what you expected, what happened, and steps to reproduce.

---

## License

Released under the [MIT License](LICENSE).

macOS is a trademark of Apple Inc. MacDesk is an independent project and is not affiliated with or endorsed by Apple.
