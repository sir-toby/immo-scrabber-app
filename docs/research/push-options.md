# Push-Benachrichtigungen auf Android ohne Play Store

Research zu Issue #5 (Map: #2). Stand: 2026-09-29.

**Frage:** Welche Push-Optionen gibt es für eine per APK verteilte Android-App (Kotlin + Compose, kein Play Store, Nutzer: eine Person + Familie) mit selbst gehostetem Python-Backend (Flask + SQLAlchemy, SQLite, Docker hinter Traefik), dessen Scraper alle ~6 h eine Runde läuft?

## Kontext aus dem Backend

- `immo-scrabber/main.py`: `scrape_loop(21600)`, also eine Runde alle 6 h (`run_search()` und danach `enrich_location()`), läuft in einem eigenen Scraper-Container (`Dockerfile.scraper`), getrennt von der Flask-API (`api/app.py`).
- `scraper/scraper.py` → `save_results()` gibt bereits `addCount + mergeCount` zurück, die Stelle, an der man neue Angebote erkennt. Ein Versand-Hook gehört ans Ende von `run_search()`, bzw. nach `enrich_location()`, damit die Benachrichtigung schon angereicherte Daten enthält; pro Nutzer (`SearchPreference.userId`) aggregiert.
- Weil die Runde nur alle 6 h läuft, ist die Latenz-Anforderung gering: eine Benachrichtigung, die ein paar Minuten zu spät kommt, ist egal. Wichtig ist nur, dass sie überhaupt ankommt, auch im Doze.

## Querschnitt: Android 13+ `POST_NOTIFICATIONS`

Das gilt für **jede** Option (auch für die Dauer-Benachrichtigung eines Foreground Service):

- Ab Android 13 (API 33) gibt es die Runtime-Permission `POST_NOTIFICATIONS`; sie muss im Manifest deklariert **und** zur Laufzeit angefragt werden. Bei Neuinstallation auf Android 13+ sind Benachrichtigungen **standardmäßig aus**. [developer.android.com/…/notification-permission](https://developer.android.com/develop/ui/views/notifications/notification-permission)
- Vor dem Posten `NotificationManager.areNotificationsEnabled()` prüfen; der Nutzer kann die Permission jederzeit widerrufen. (ebd.)
- Compose: `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`, nur bei `SDK_INT >= TIRAMISU`. (ebd.)

## Option 1: Firebase Cloud Messaging (FCM)

**Funktioniert ohne Play-Store-Distribution?** Ja. Die Firebase-Doku sagt ausdrücklich, dass man nicht auf den Play Store als Vertriebsweg beschränkt ist. Das **Gerät** braucht aber Android 6.0+ mit installiertem Google Play Store bzw. Google Play Services (oder einen Emulator mit Google APIs). [firebase.google.com/…/android/client](https://firebase.google.com/docs/cloud-messaging/android/client)
→ Funktioniert **nicht** auf de-googelten Geräten (GrapheneOS ohne sandboxed Play, LineageOS ohne GApps, Huawei ohne GMS).

**Was es braucht (App):**
- Firebase-Projekt, `google-services.json` im App-Modul, Google-Services-Gradle-Plugin, `firebase-messaging`-Dependency. (ebd.)
- Einen `FirebaseMessagingService` im Manifest; die Registrierung kommt über den Callback `onRegistered()` (Firebase Installation ID, FID), bzw. klassisch `getToken()` + `onNewToken()`. Laut Doku ist `getToken()` inzwischen deprecated und FIDs sind der empfohlene Weg. (ebd., [admin-sdk](https://firebase.google.com/docs/cloud-messaging/send/admin-sdk))
- `POST_NOTIFICATIONS` (siehe oben).

**Was es braucht (Backend):**
- Service-Account-Credentials (JSON) des Firebase-Projekts + `firebase-admin` (Python) **oder** direkt die HTTP-v1-API mit OAuth2. Cloud Messaging API im Projekt aktiviert. [firebase.google.com/…/send/admin-sdk](https://firebase.google.com/docs/cloud-messaging/send/admin-sdk)
- Versand: `messaging.send(Message(..., fid=...))` bzw. `send_each_for_multicast` für bis zu 500 Empfänger. (ebd.)
- Registrierungs-Endpoint (z. B. `POST /devices` mit FID/Token, gebunden an den eingeloggten User) + Tabelle `device(user_id, fid, updated_at)`. Firebase empfiehlt, FIDs **mit Zeitstempel** zu speichern; nach ~1 Monat Inaktivität als stale betrachten; nach 270 Tagen Inaktivität verfällt die Registrierung auf Android. Bei `UNREGISTERED` (404) / `INVALID_ARGUMENT` (400) den Eintrag löschen. [firebase.google.com/…/manage-tokens](https://firebase.google.com/docs/cloud-messaging/manage-tokens)

**Kosten:** FCM ist „No-cost“, auch im Spark-Plan (ohne Zahlungsmittel). [firebase.google.com/pricing](https://firebase.google.com/pricing)

**Zuverlässigkeit unter Doze:** Gut, wenn man **High Priority** nutzt. High-Priority-Nachrichten dürfen ein schlafendes Gerät wecken; Normal Priority wird im Doze ggf. bis zum Ende des Doze verzögert. [firebase.google.com/…/message-priority](https://firebase.google.com/docs/cloud-messaging/android/message-priority), [developer.android.com/…/doze-standby](https://developer.android.com/training/monitoring-device-state/doze-standby)
Achtung: Wenn High-Priority-Nachrichten wiederholt **keine** sichtbare Benachrichtigung auslösen (Betrachtung über 7 Tage), stuft FCM sie herunter. Also immer eine Notification anzeigen. (message-priority, ebd.)
Seit Android 13 bestimmen App-Standby-Buckets nicht mehr die FCM-High-Priority-Quoten. [developer.android.com/…/power-details](https://developer.android.com/topic/performance/power/power-details)

**Datenschutz:** Nachrichten laufen über Google; Google sieht Metadaten (Gerät, Zeitpunkt, Payload, falls nicht selbst verschlüsselt). Abmildern: nur „3 neue Angebote“ senden, Details holt die App selbst von der eigenen API.

**Aufwand:** Mittel. Firebase-Console-Setup, `google-services.json` ins Repo (bzw. CI-Secret), Service-Account-JSON als Docker-Secret in den Scraper-Container, ~50 Zeilen Python. Keine eigene Infrastruktur, kein Zusatz-App für die Nutzer.

## Option 2: ntfy / UnifiedPush (self-hosted)

### Bausteine

- **ntfy-Server**: Docker-Image `binwiederhier/ntfy`, Befehl `serve`, eigene `server.yml` mounten (`base-url` = externe URL), Cache-Volume `/var/cache/ntfy`. Ressourcenbedarf klein (Beispielwerte ab ~128 MiB RAM). [docs.ntfy.sh/install](https://docs.ntfy.sh/install/)
- Hinter Traefik: **`behind-proxy: true`** setzen, sonst werden alle Clients als ein Visitor rate-limitiert. Privater Server: `auth-default-access: deny-all`, Nutzer/ACLs, Access Tokens (`ntfy token add`). [docs.ntfy.sh/config](https://docs.ntfy.sh/config/)
- **ntfy-Android-App** (F-Droid/APK/Play): Für **selbst gehostete Server nutzt die App nie Firebase**, sondern „Instant Delivery“ über einen **Foreground Service** mit dauerhafter Benachrichtigung, und liefert so auch im Doze sofort zu. [docs.ntfy.sh/subscribe/phone](https://docs.ntfy.sh/subscribe/phone/)
- Die ntfy-App ist zugleich ein **UnifiedPush-Distributor**. (ebd.)

### Variante 2a: ntfy-App als Notification-Client (ohne Code in unserer App)

- Backend postet nach der Runde per `requests.post("https://ntfy.example/<topic>", data=..., headers={"Title": ..., "Priority": ..., "Click": <Deep-Link/URL>, "Authorization": "Bearer <token>"})`. [docs.ntfy.sh/publish](https://docs.ntfy.sh/publish/)
- Familie installiert die ntfy-App und abonniert ein Topic pro Person; `Click` öffnet unsere App bzw. das Angebot.
- Backend: kein Registrierungs-Endpoint nötig, nur Topic-Name pro User (Konfiguration oder Spalte in den User-Preferences).
- Nachteil: Benachrichtigung kommt von der ntfy-App, nicht von unserer App; zwei Apps nötig.

### Variante 2b: UnifiedPush in unserer App (ntfy als Distributor)

- **Distributor-App ist Pflicht** (ntfy, Sunup, NextPush, Conversations, gCompat-UP …). [unifiedpush.org/users/distributors](https://unifiedpush.org/users/distributors/), [unifiedpush.org/users/faq](https://unifiedpush.org/users/faq/)
- App: Dependency `org.unifiedpush.android:connector`, Service, der `PushService` erweitert, `UnifiedPush.register()`; regelmäßiges Re-Registrieren wird empfohlen. Die Library zieht `tink` (Krypto) mit. [unifiedpush.org/kdoc/connector](https://unifiedpush.org/kdoc/connector/)
- Protokoll = **Web Push**: Die App erhält eine Endpoint-URL + Public Key + Auth-Secret und schickt sie an unser Backend; das Backend verschlüsselt nach **RFC 8291** (Pflicht) und authentifiziert optional per VAPID (RFC 8292). Serverseitig nimmt man eine Web-Push-Library (Python: z. B. `pywebpush`). [unifiedpush.org/developers/intro](https://unifiedpush.org/developers/intro/)
- ntfy-Server bei `deny-all`: anonymen Schreibzugriff auf UnifiedPush-Topics erlauben: `ntfy access '*' 'up*' write-only`; optional `visitor-subscriber-rate-limiting: true`. [docs.ntfy.sh/config](https://docs.ntfy.sh/config/)
- Optionaler Fallback ohne Distributor: **Embedded FCM Distributor** (keine proprietären Google-Blobs; braucht VAPID-fähigen Server oder ein Gateway). Braucht dann aber wieder Play Services auf dem Gerät. [unifiedpush.org/kdoc/embedded_fcm_distributor](https://unifiedpush.org/kdoc/embedded_fcm_distributor/)
- Backend: Registrierungs-Endpoint `POST /push-subscriptions (endpoint, p256dh, auth)` pro User, Tabelle dazu, Versand via Web-Push-Lib, bei 404/410 vom Push-Server den Eintrag löschen (Web-Push-Konvention).

### Bewertung ntfy/UnifiedPush

- **Kosten:** 0 € (Open Source, läuft auf dem vorhandenen Docker-Host).
- **Zuverlässigkeit unter Doze:** Gut. Die ntfy-App hält per Foreground Service eine Verbindung und liefert laut Doku auch im Doze sofort. Preis: permanente Benachrichtigung (kann ausgeblendet werden) und etwas Akku. Herstellerspezifische Akku-Killer (Xiaomi, Samsung …) können den Dienst trotzdem beenden; das ist nicht in den Primärquellen belegt, sondern allgemeine Erfahrung, ggf. Akkuoptimierung für ntfy abschalten.
- **Datenschutz:** Sehr gut. Kein Google, alles auf eigener Infrastruktur; bei 2b zusätzlich Ende-zu-Ende-verschlüsselt (RFC 8291).
- **Aufwand:** 2a gering (ein Container + ~15 Zeilen Python). 2b hoch (Connector-Lib, Web-Push-Krypto, Registrierungs-Endpoint, Distributor-Auswahl-UX).

## Option 3: WorkManager-Polling (Fallback)

- `PeriodicWorkRequest` hat ein **Mindestintervall von 15 Minuten** (wie JobScheduler), optional ein Flex-Intervall; Constraints wie `setRequiredNetworkType(CONNECTED)`. [developer.android.com/…/define-work](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)
- **Doze:** kein Netz, JobScheduler (und damit WorkManager) läuft nicht; Jobs werden in Maintenance Windows nachgeholt, die mit der Zeit **immer seltener** werden. [developer.android.com/…/doze-standby](https://developer.android.com/training/monitoring-device-state/doze-standby)
- **App Standby Buckets:** In „Frequent“ nur ~10 min Jobs pro 12 h, in „Rare“ 10 min pro 24 h **und Netzwerk deaktiviert**, in „Restricted“ einmal pro Tag. Eine selten geöffnete App rutscht also ab, und Polling wird dann unzuverlässig. [developer.android.com/…/power-details](https://developer.android.com/topic/performance/power/power-details)
- Backend: kein Push-Code; nur ein Endpoint wie `GET /properties?since=<ts>` bzw. „neue Treffer seit letztem Abruf“ (evtl. schon durch die bestehende Property-API abgedeckt).
- **Kosten** 0, **Datenschutz** sehr gut, **Aufwand** gering. **Zuverlässigkeit**: Für einen 6-h-Takt reicht die Latenz eigentlich, aber in den Buckets Rare/Restricted kann die Benachrichtigung einen Tag oder länger ausbleiben.

## Vergleich

| | FCM | ntfy-App (2a) | UnifiedPush + ntfy (2b) | WorkManager-Polling |
|---|---|---|---|---|
| Kosten | 0 € | 0 € (self-hosted) | 0 € (self-hosted) | 0 € |
| Gerät braucht | Play Services | ntfy-App | Distributor-App (ntfy) | nichts |
| Doze-Zuverlässigkeit | hoch (High Priority) | hoch (Foreground Service) | hoch (via ntfy) | niedrig bis mittel (Buckets) |
| Datenschutz | Google sieht Metadaten | voll self-hosted | self-hosted + E2E | voll self-hosted |
| Aufwand App | mittel | keiner | hoch | gering |
| Aufwand Backend | FID-Endpoint + firebase-admin + Service Account | ntfy-Container + HTTP POST | Subscription-Endpoint + Web-Push-Krypto + ntfy-Container | Delta-Endpoint |
| Zusatz-Infra | Firebase-Projekt | ntfy-Server | ntfy-Server | keine |

## Empfehlung

1. **FCM als Hauptweg**, wenn alle Familiengeräte normale Android-Phones mit Google Play Services sind. Das ist sehr wahrscheinlich. Es kostet nichts, funktioniert trotz Sideload-APK, ist im Doze zuverlässig (High Priority + immer sichtbare Notification) und braucht keine Zusatz-App und keinen zusätzlichen Server. Datenschutz-Kompromiss abmildern: nur Anzahl/IDs in der Payload, Details lädt die App über die eigene API.
2. **ntfy (Variante 2a) als pragmatische Alternative**, falls Firebase/Google vermieden werden soll: ntfy-Container hinter Traefik + ein HTTP-POST am Ende der Scraper-Runde. Das ist in einem Abend umgesetzt und sogar unabhängig von der App nutzbar. UnifiedPush (2b) lohnt den Mehraufwand für einen Nutzerkreis von ~3 Personen nicht.
3. **WorkManager-Polling nicht als alleinige Lösung**, höchstens als Ergänzung (z. B. Badge/Sync beim App-Start).

**Backend-Arbeit in jedem Push-Fall:** (a) Tabelle + Endpoint zur Registrierung pro User (FCM: FID; 2a: Topic-Name; 2b: Web-Push-Subscription), (b) Versand-Hook im Scraper-Container nach `run_search()`/`enrich_location()`, der pro User nur **neue** Treffer seit der letzten Benachrichtigung zusammenfasst (dafür fehlt aktuell noch eine „neu seit“-Markierung; `save_results()` liefert nur Zählwerte, nicht pro User), (c) Aufräumen ungültiger Registrierungen bei 404/410.

## Offene Punkte

- Haben alle Zielgeräte Google Play Services? (entscheidet FCM vs. ntfy)
- Wie wird „neu für User X“ bestimmt? Das Scraper-Ergebnis ist aktuell nicht pro User zugeordnet (Adapter ohne User-Präferenzen scrapen global).
