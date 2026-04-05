# Bytro Launcher

Android application to access Bytro strategy games from a polished multi-tab interface.

Included:
- Kotlin Android app
- Tabbed browsing for multiple games
- Pull to refresh
- Per-tab progress indicators
- External browser fallback
- GitHub Actions workflow to build the debug APK

Games currently included:
- Call of War
- Supremacy 1914
- Conflict of Nations
- New World Empires

## Build locally

```bash
./gradlew assembleDebug
```

## GitHub Actions

A workflow is included at `.github/workflows/android.yml` and uploads the built APK as an artifact.
