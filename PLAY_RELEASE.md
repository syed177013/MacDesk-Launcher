# Releasing MacDesk

1. Create a keystore: `keytool -genkeypair -v -keystore macdesk.jks -alias macdesk -keyalg RSA -keysize 2048 -validity 10000`
2. Create `keystore.properties` in the project root (git-ignored):
   `storeFile=macdesk.jks`, `storePassword=…`, `keyAlias=macdesk`, `keyPassword=…`
3. Build the Play bundle: `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`
4. Change `applicationId` / `namespace` from `com.example.macdesk` before first upload (cannot be changed later).

## Play Console declarations
- **All files access (MANAGE_EXTERNAL_STORAGE):** Play restricts this. Declare "file manager" as core functionality
  and add a short demo video of Finder browsing internal storage / USB. If rejected, ship without the permission
  (Finder then works only on folders the user adds via the system picker).
- **Home screen (launcher) app:** no extra declaration, but screenshots must show the launcher UI.
- **Data safety:** the app collects/shares no data and has no network permission beyond connectivity-state display.
- Provide a privacy policy URL stating the above.
- Target API: 35. Min API: 26.

## Notes
- "Open apps in windows" relies on the OS: freeform windows work on Samsung DeX / Android desktop mode /
  devices with "Enable freeform windows" turned on. Otherwise Android opens apps full-screen.
