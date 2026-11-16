# Night Blocker (android_website_blocker)
Block apps and websites at certain hours with "block sets" to limit phone use. Personal use only.

- Requirements: `rules.md` · Plan and decisions: `TASKS.md` (both local-only, excluded from git)
- `:core` — pure-Kotlin logic (schedules, block rules, protections, persistence). `sh ./gradlew :core:test`
- `:app` — Android app (accessibility service, UI). `sh ./gradlew assembleDebug`

## Enabling the accessibility service
Open the app and follow the status card. Manually:
1. Settings → Accessibility → **Installed apps** (Samsung) / **Downloaded apps** → Night Blocker → On.
2. If the switch is greyed out ("Restricted setting"): Settings → Apps → Night Blocker → ⋮ → **Allow restricted settings**, then retry.
3. Fallback via adb:
   ```
   adb shell settings put secure enabled_accessibility_services com.example.androidwebsiteblocker/com.example.androidwebsiteblocker.NightBlockerService
   adb shell settings put secure accessibility_enabled 1
   ```
   (This replaces any other enabled accessibility services; append with `:` if you use others.)

## Checking detection
Logcat tag `NightBlocker` logs every foreground change; the app's home screen shows the same log with durations.
