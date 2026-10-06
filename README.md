# Immo-Finder

Native Android-App (Kotlin, Jetpack Compose, Material 3) zum Backend [immo-scrabber](https://github.com/sir-toby/immo-scrabber): Inserate der eigenen Suchprofile durchsehen, bewerten und mit Labels versehen. Fachbegriffe stehen in [GLOSSARY.md](GLOSSARY.md).

## Lokal bauen

Voraussetzungen: JDK 17 oder neuer (z. B. das JBR von Android Studio) und das Android SDK (Plattform 36). Den SDK-Pfad trägt Android Studio in `local.properties` ein, alternativ `ANDROID_HOME` setzen.

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"   # Windows / Git Bash, Beispiel
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Die Debug-APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`. Genau diese drei Tasks laufen auch als CI-Check bei jedem PR und jedem Push auf `main` (`.github/workflows/check.yml`). Lint bricht nur bei Errors ab, Warnungen stehen im Report.

## Debug-APK aus der CI

Jeder Lauf des Checks lädt seine Debug-APK als Artefakt `immo-finder-debug-pr<Nummer>` (bzw. `-main`) hoch, 14 Tage lang. Herunterladen: im PR unter *Checks → Check → Summary → Artifacts*, oder

```bash
gh run download --repo sir-toby/immo-scrabber-app --name immo-finder-debug-pr66
```

Die CI signiert sie mit einem festen Debug-Key aus den Secrets (siehe unten), daher lässt sich jede neue APK über die vorige installieren. Mit demselben Key wie Android Studio lokal gilt das auch für lokal gebaute Debug-APKs.

## Release

1. Auf `main` einen Tag `vX.Y.Z` setzen und pushen:
   ```bash
   git tag v1.2.0
   git push origin v1.2.0
   ```
2. Der Workflow `.github/workflows/release.yml` führt Tests und Lint aus, baut die signierte Release-APK und legt das GitHub Release `v1.2.0` mit `immo-finder-1.2.0.apk` und generierten Release Notes an. Schlägt ein Schritt fehl, entsteht kein Release.

Die Version kommt aus dem Tag: `versionName = 1.2.0`, `versionCode = major·10000 + minor·100 + patch` (Minor und Patch < 100). Ohne Tag gilt `0.0.0-dev` / `1`.

### Benötigte Secrets

Unter *Settings → Secrets and variables → Actions* im Repo:

| Secret | Inhalt |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Release-Keystore (PKCS12), Base64-kodiert, z. B. `base64 -w0 release.p12` |
| `RELEASE_KEYSTORE_PASSWORD` | Passwort des Keystores |
| `RELEASE_KEY_ALIAS` | Alias des Schlüssels (`immo-finder`) |
| `RELEASE_KEY_PASSWORD` | Passwort des Schlüssels |
| `IMMO_PROD_BASE_URL` | Voreingestellter Prod-Server, z. B. `https://…/api/` (bewusst nicht im Repo; lokal als `immo.prodBaseUrl` in `local.properties`). Auch der Check nutzt ihn für die Debug-APK |
| `DEBUG_KEYSTORE_BASE64` | Debug-Keystore, Base64-kodiert, z. B. `base64 -w0 ~/.android/debug.keystore` |
| `DEBUG_KEYSTORE_PASSWORD` | Passwort des Debug-Keystores (Android-Studio-Standard: `android`) |
| `DEBUG_KEY_ALIAS` | Alias (Android-Studio-Standard: `androiddebugkey`) |
| `DEBUG_KEY_PASSWORD` | Passwort des Schlüssels (Android-Studio-Standard: `android`) |

`GITHUB_TOKEN` stellt GitHub Actions selbst bereit. Keystore und Passwörter gehören nie ins Repo, auch nicht der Debug-Keystore: Die Debug-App spricht ebenfalls mit Prod. Der SHA-1 des Debug-Keys muss in Firebase bei der App `de.immoscrabber.app.debug` hinterlegt sein.
