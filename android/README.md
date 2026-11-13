# HindiAnimestuff – Android app (Kotlin + Jetpack Compose + Media3 ExoPlayer)

- Catalog: bundled `app/src/main/assets/app-data.json`, refreshed on launch from
  https://hindianimestuff.web.app/app-data.json (made by generate.py on every deploy).
- Build: push to GitHub -> Actions -> "Build Android APK" -> download artifact `HindiAnimestuff-apk`.
- Config (site URL, Telegram link, Firebase key): `app/src/main/java/com/hindianimestuff/app/Data.kt` -> `object Config`.
- Refresh the bundled catalog: `python export_app_data.py` (from the project root).
