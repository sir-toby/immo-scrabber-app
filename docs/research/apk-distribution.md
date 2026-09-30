# APK-Verteilung und Updates bei privatem Repo

Recherche zu Issue #18. Stand: 2026-09-30.

**Frage:** Beide Repos (`immo-scrabber-app`, `immo-scrabber`) sind privat. Wie verteilen wir eine signierte APK (Kotlin/Compose, minSdk 26) an ca. 3 Geräte (Owner + Familie, alle mit Google Play Services, kein Play Store) und wie erfahren/installieren die Geräte Updates?

**Rahmen:** Backend öffentlich per HTTPS hinter Traefik (Flask, JWT), App nutzt bereits FCM, MVP-lean.

## Kurzfassung

| Option | Aufwand | Kosten | Sicherheit | Update-UX |
|---|---|---|---|---|
| A. GitHub Releases im privaten Repo + Obtainium mit PAT | gering (CI-Release) | 0 | PAT auf Familiengeräten, read-only Contents auf dem **ganzen Quellcode-Repo** | gut: Obtainium prüft im Hintergrund, Notification, 1 Tap |
| B. Separates öffentliches Release-Repo + Obtainium ohne PAT | gering | 0 | kein Token; APK + Versionshistorie öffentlich | gut (wie A) |
| C. Firebase App Distribution | gering–mittel (Gradle-Plugin, Service Account) | 0 | Google-Konto pro Tester, kein Token auf Geräten | ok: App-Tester-App/E-Mail; In-App-Alert-SDK (Beta) |
| D. Self-hosted hinter Traefik/Flask (APK + `version.json`) | mittel (Endpoint + Deploy-Schritt) | 0 | volle Kontrolle, optional hinter JWT | abhängig von E/Obtainium-HTML-Quelle |
| E. Eigener In-App-Updater (PackageInstaller) | mittel–hoch | 0 | eigener Code im Install-Pfad | am besten: FCM-Push → Dialog → Install; ab API 31 ggf. ohne Rückfrage |

**Empfehlung (MVP):** **Firebase App Distribution** für Verteilung und Updates (A/B nur als Alternative). Begründung unten.

## A. GitHub Releases im privaten Repo + Obtainium

- Release-Assets werden über `GET /repos/{owner}/{repo}/releases/assets/{asset_id}` mit `Accept: application/octet-stream` geladen; Clients müssen 200 oder 302 behandeln. ([docs.github.com – Release assets](https://docs.github.com/en/rest/releases/assets?apiVersion=2022-11-28))
- Bei privatem Repo braucht dieser Aufruf Authentifizierung. Ein fine-grained PAT benötigt dafür **Contents: read** (gilt für `releases`, `releases/latest` und `releases/assets/{asset_id}`). ([docs.github.com – Permissions for fine-grained PATs](https://docs.github.com/en/rest/authentication/permissions-required-for-fine-grained-personal-access-tokens?apiVersion=2022-11-28))
- Fine-grained PATs lassen sich auf einzelne Repos einschränken; Ablaufdatum wählbar, „Infinite lifetimes are allowed“ (sofern keine Org-Policy). Tokens hängen am erzeugenden User; max. 50 fine-grained PATs pro User. ([docs.github.com – Managing PATs](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens))
- Obtainium unterstützt GitHub als Quelle und hat in den Quelleinstellungen ein PAT-Feld (`github-creds`, Label „GitHub personal access token (increases rate limit)“); der Token wird als `Authorization: Token …` gesendet, beim APK-Download mit `Accept: application/octet-stream`. ([Obtainium `lib/app_sources/github.dart`](https://github.com/ImranR98/Obtainium/blob/main/lib/app_sources/github.dart), [README](https://github.com/ImranR98/Obtainium))
- Obtainium kann im Hintergrund prüfen (konfigurierbares Intervall), benachrichtigen und optional still installieren; es deklariert `REQUEST_INSTALL_PACKAGES` und `UPDATE_PACKAGES_WITHOUT_USER_ACTION`. ([Obtainium `en.json`](https://github.com/ImranR98/Obtainium/blob/main/assets/translations/en.json), [AndroidManifest](https://github.com/ImranR98/Obtainium/blob/main/android/app/src/main/AndroidManifest.xml))

**Sicherheit:** „Contents: read“ gibt nicht nur Releases frei, sondern den **gesamten Quellcode** des Repos (inkl. Historie, evtl. versehentlich committeter Secrets). Der Token liegt auf 3 Familiengeräten in einer Drittanbieter-App. Mildern: eigenes Bot-/Zweitkonto, Token nur auf ein Repo, Ablaufdatum setzen (dann aber regelmäßig auf allen Geräten erneuern). Obtainium selbst ist Drittsoftware aus GitHub/F-Droid, nicht aus dem Play Store.

**Aufwand:** CI-Job, der signierte APK als Release-Asset hochlädt; einmalig Obtainium + PAT auf jedem Gerät einrichten.

## B. Separates öffentliches Release-Repo

- Gleiche Mechanik wie A, aber Obtainium braucht keinen Token (öffentliche Repos sind ohne Auth lesbar; ohne Token gilt nur das niedrigere anonyme Rate-Limit, laut Obtainium „GitHub rate limiting can be avoided using an API key“ – bei 3 Geräten unkritisch).
- **Was leakt:** APK (dekompilierbar → Backend-URL, API-Pfade, `google-services.json`-Werte wie Firebase-Projekt-ID/API-Key), Release-Notes, Versionsfrequenz, Existenz des Projekts. Firebase-API-Keys sind laut Firebase kein Geheimnis ([firebase.google.com – API keys](https://firebase.google.com/docs/projects/api-keys)), die Backend-URL ist ohnehin öffentlich. Echte Absicherung muss sowieso serverseitig (JWT) liegen, da jede APK auf einem Gerät extrahierbar ist.
- Bewertung: akzeptabel, wenn in der APK keine Secrets stecken; Quellcode bleibt privat. Zusätzlicher Pflegeaufwand: CI braucht Schreibrecht auf zweites Repo (PAT/Deploy-Key als CI-Secret).

## C. Firebase App Distribution

- **Kostenlos**; bis 500 Tester pro Projekt, 200 pro Gruppe; Releases verfallen nach **150 Tagen**, max. 1.000 Releases pro App; Binaries bis 2048 MiB. ([firebase.google.com – Troubleshooting/Limits](https://firebase.google.com/docs/app-distribution/troubleshooting?platform=android))
- Tester brauchen ein **Google-Konto**, nehmen die E-Mail-Einladung an (Einladung verfällt nach 30 Tagen, nur einmal einlösbar) und installieren die **Firebase App Tester**-App über den Browser-Prompt bzw. `appdistribution.firebase.google.com` (also als Sideload, nicht aus dem Play Store). Installation aus App Tester per „Download“. AAB-Releases bräuchten zusätzlich „Internal app sharing“ im Play Store – daher **APK** verteilen. ([firebase.google.com – Tester setup](https://firebase.google.com/docs/app-distribution/get-set-up-as-a-tester?platform=android), [Limits](https://firebase.google.com/docs/app-distribution/troubleshooting?platform=android))
- **CI-Upload:** Gradle-Plugin, `./gradlew assembleRelease appDistributionUploadRelease`; Auth per Service Account (Rolle „Firebase App Distribution Admin“, `serviceCredentialsFile` oder `GOOGLE_APPLICATION_CREDENTIALS`); `artifactType = "APK"`, `testers`/`groups` konfigurierbar. ([firebase.google.com – Distribute with Gradle](https://firebase.google.com/docs/app-distribution/android/distribute-gradle)) Die Marketplace-Action „Firebase App Distribution“ ist **Community**, nicht von Google ([GitHub Marketplace](https://github.com/marketplace/actions/firebase-app-distribution)) – Gradle-Plugin oder Firebase CLI bevorzugen.
- **In-App-Alerts (SDK):** zeigt Testern im App-Code „neuer Build verfügbar“, übernimmt Sign-in, Download und Install-Prompt; Tester melden sich einmalig mit Google an („persists across updates“); ab Android 13 `POST_NOTIFICATIONS` für Fortschritts-Notifications. Das SDK ist **Beta** (`firebase-appdistribution:16.0.0-beta20`). ([firebase.google.com – Set up alerts](https://firebase.google.com/docs/app-distribution/set-up-alerts?platform=android))
- **Policy-Caveat:** Das volle SDK enthält Self-Update-Code, der als Verstoß gegen die Google-Play-Policy gelten kann, „even if that code is not executed at runtime“; Empfehlung: `firebase-appdistribution-api` in allen Varianten, volles SDK nur in Pre-Release-Varianten. Für uns ohne Play Store **irrelevant**, solange die App nie in den Play Store soll. (gleiche Quelle)
- **Semantik:** Das Produkt ist für Pre-Release-/Test-Builds gedacht; es gibt keinen „Produktions“-Kanal. Für 3 Familiengeräte funktioniert es trotzdem; einzige echte Reibung ist der 150-Tage-Verfall (alte Builds verschwinden aus App Tester, installierte Apps laufen weiter).

**Sicherheit:** kein Token auf Geräten; Zugriff an Google-Konto gebunden; Service-Account-Key nur als CI-Secret. **Update-UX:** E-Mail + App Tester, oder mit SDK direkt in der App ein Dialog.

## D. Self-hosted hinter Traefik/Flask

- Flask liefert `GET /app/version.json` (`versionCode`, `versionName`, `url`, `sha256`) und `GET /app/latest.apk`; optional hinter dem vorhandenen JWT-Check. CI lädt die APK per SSH/Deploy auf den Server.
- Obtainium kann das ohne eigenen Updater nutzen: es gibt eine **HTML-Quelle** und eine **Direct-APK-Link-Quelle**; die HTML-Quelle erlaubt eigene Request-Header (z. B. statischer Bearer-Token statt GitHub-PAT). ([Obtainium `lib/app_sources/`](https://github.com/ImranR98/Obtainium/tree/main/lib/app_sources), [`html.dart`](https://github.com/ImranR98/Obtainium/blob/main/lib/app_sources/html.dart))
- Vorteile: keine Drittanbieter-Konten, Zugriff an eigene Auth koppelbar. Nachteile: Deploy-Pipeline + Speicherplatz + Endpoint pflegen; ohne E nur halbe Lösung.

## E. Eigener In-App-Updater

- `REQUEST_INSTALL_PACKAGES` im Manifest; der Nutzer muss der App einmalig „Unbekannte Apps installieren“ erlauben (pro App, seit API 26 = unser minSdk). Prüfen via `PackageManager.canRequestPackageInstalls()`, Settings öffnen via `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` mit `package:<id>`. ([developer.android.com – Settings](https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_UNKNOWN_APP_SOURCES), [PackageManager](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()))
- Installation per `PackageInstaller`-Session (create → APK schreiben → commit). Standard bei `REQUEST_INSTALL_PACKAGES`: Nutzer muss bestätigen (`STATUS_PENDING_USER_ACTION`). Ab API 31 entfällt die Bestätigung mit `setRequireUserAction(USER_ACTION_NOT_REQUIRED)`, wenn die App u. a. **sich selbst aktualisiert** (oder Installer of record/Update Owner ist) **und** ein aktuelles targetSdk hat (z. B. ≥ 34 auf Android 15, ≥ 35 auf Android 16; die Schwelle steigt mit jeder Version) – trotzdem muss `STATUS_PENDING_USER_ACTION` immer behandelt werden. ([developer.android.com – SessionParams.setRequireUserAction](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int)))
- Android 14 (API 34) führte Update Ownership ein (`setRequestUpdateOwnership`, nur bei Erstinstallation, braucht `ENFORCE_UPDATE_OWNERSHIP`); danach brauchen andere Installer Nutzerbestätigung. ([SessionParams.setRequestUpdateOwnership](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequestUpdateOwnership(boolean))) Ab Android 14 sind Apps mit targetSdk < 23 nicht installierbar – für uns irrelevant. ([Android 14 behavior changes](https://developer.android.com/about/versions/14/behavior-changes-all))
- **Signatur:** Updates müssen mit demselben Schlüssel signiert sein wie die installierte Version, sonst schlägt die Installation fehl → Keystore sicher sichern (Verlust = Neuinstallation auf allen Geräten, Datenverlust). Das gilt für **alle** Optionen, ebenso steigender `versionCode`.
- Trigger: FCM-Data-Message „neue Version“ aus CI/Backend → App prüft `version.json` → Download → Session-Install. Aufwand: Download-Manager, Hash-Prüfung, Session-Callbacks, Fehlerfälle, Tests auf mehreren Android-Versionen.

## Empfehlung

Für 3 Geräte, MVP-lean: **Firebase App Distribution (C), APK-Upload per Gradle-Plugin aus CI, eine Tester-Gruppe „family“.**

- Firebase-Projekt existiert bereits (FCM), kostenlos, kein Token auf Familiengeräten, kein zusätzliches Repo, kein eigener Server-Code.
- Update-UX: zunächst App Tester + E-Mail. Wenn das zu umständlich ist, das In-App-Alert-SDK nur in der Release-Variante einbinden (Play-Policy irrelevant, da kein Play Store) – Beta-Status akzeptieren.
- 150-Tage-Verfall beachten: bei längeren Release-Pausen einfach neu hochladen.
- **Fallback**, falls Google-Konten/App Tester stören: **B** (öffentliches Release-Repo + Obtainium ohne Token) – vorher sicherstellen, dass die APK keine Secrets enthält. **A** (PAT auf privatem Repo) wegen Quellcode-Lesezugriff auf Familiengeräten nicht empfohlen. **D/E** erst, wenn wirklich ein nahtloser Ein-Tap-Updater gewünscht ist.
- Unabhängig von der Option: Release-Keystore + Passwörter außerhalb des Repos sichern (CI-Secret + Offline-Backup).
