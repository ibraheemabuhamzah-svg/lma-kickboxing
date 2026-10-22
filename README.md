# LMA Muay Thai & KickBoxing

Club management app, built two ways from the same spec (see [CLAUDE.md](CLAUDE.md)):

- **[`web/`](web)** — vanilla HTML/CSS/JS web app, data stored in `localStorage`. Open `web/index.html` via any static file server (e.g. `python3 -m http.server --directory web`).
- **[`android/`](android)** — native Kotlin Android app (same features, data stored on-device). Built automatically by [GitHub Actions](.github/workflows/android-build.yml) on every push to `main`; download the latest debug APK from the [Releases page](../../releases/tag/latest-apk) or the workflow's build artifacts.

## Registry password

Coach-only back office password: `LmaKickBoxingR`
