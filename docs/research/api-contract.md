# API-Vertrag: native Android-App gegen das immo-scrabber-Backend

Recherche zu Issue #4 (Map: #2). Primärquelle ist der Backend-Code in `sir-toby/immo-scrabber`, Stand Commit `40b2806`. Alle Pfadangaben unten beziehen sich auf dieses Repo.

## Kurzfassung

- Die App braucht **7 Endpunkte**: `POST /auth/login`, `GET /properties/results`, `PATCH /properties/<type>/<id>/label`, `PATCH /properties/labels`, `GET`/`POST /preferences`, `PATCH`/`DELETE /preferences/<uuid>`. `POST /auth/register` wird nicht gebraucht.
- Auth läuft über ein **JWT als `Authorization: Bearer <token>`**. Das Token ist **2 Tage gültig** (fest im Code). Es gibt **keinen Refresh-Endpunkt**. Nach Ablauf antwortet das Backend mit 401, und die App muss sich neu einloggen.
- **Bilder werden direkt beim Anbieter geladen (Hotlinking).** `image` ist die rohe URL des Anbieters und kann `null` sein. Es gibt keinen Proxy und keinen Cache.
- Fehlerformate sind **uneinheitlich**: `{"Error": ...}`, `{"error": ...}`, `{"msg": ...}` (JWT) und bei unbehandelten Exceptions eine HTML-Seite mit Status 500.
- Größte Fallstricke: Der Filter `label=unbewertet` findet nur Listings **ohne** Relation. Ein explizit auf `unbewertet` zurückgesetztes Listing taucht in keinem Filter außer „alle“ auf. „Alle unbewerteten als uninteressant markieren“ markiert **alle** Objekte in der DB, nicht nur die, die zu den Suchprofilen passen, und bei `house` auch alle Wohnungen.

## Basis-URL und Transport

- Im Deployment liefert nginx das Web-Frontend aus und leitet `/api/` an den API-Container weiter, wobei das Präfix entfernt wird: `location /api/ { proxy_pass http://api:5000/; }` (`frontend/nginx.conf`). Die App sollte also **`https://<host>/api/`** als Basis-URL nutzen, genau wie das Web (`baseURL: '/api'` in `frontend/src/services/api.ts`). Welcher Host das konkret ist, steht im Self-Host-Repo (`deploy/stacks/immo-scrabber/`, siehe Kommentar in `config.py`) und wurde hier nicht geprüft.
- nginx lässt nur `GET POST PUT PATCH DELETE OPTIONS` durch (`limit_except`). Für die App reicht das.
- Die API ist Flask hinter gunicorn (`Dockerfile.api`). CORS ist global aktiv (`CORS(app)` in `api/app.py`), für eine native App aber egal.
- Request-Bodies werden mit `request.get_json()` gelesen. **Ohne `Content-Type: application/json` antwortet Flask mit 415 und einer HTML-Fehlerseite.** Retrofit/Ktor setzen den Header mit einem JSON-Converter automatisch.
- Die Datenbank ist SQLite (`storage/db.py`).
- `GET /health` ist ohne Auth erreichbar und liefert `{"status":"ok","database":"ok"}` (200) oder 503. Die App kann damit prüfen, ob das Backend erreichbar ist.

## Auth

### `POST /auth/login` (`api/auth/auth.py`)

Request:
```json
{ "username": "tobias", "password": "..." }
```
- Der Benutzername wird vor dem Vergleich kleingeschrieben (`.lower()`), das Passwort nicht.

Responses:
| Status | Body |
|---|---|
| 200 | `{"access_token": "<jwt>"}` |
| 401 | `{"Error": "Invalid credentials"}` |
| 500 (HTML) | Fehlt `username` oder `password` im Body, gibt es einen unbehandelten `KeyError` |

- Das Token wird mit `create_access_token(identity=user.uuid, additional_claims={"username": ...})` erzeugt. Im Payload stehen also `sub` (User-UUID), `username` und `exp`. Die App kann den Anzeigenamen und das Ablaufdatum lokal aus dem JWT lesen. Einen `/me`-Endpunkt gibt es nicht.
- Die Lebensdauer ist `JWT_ACCESS_TOKEN_EXPIRES = timedelta(days=2)` und steht fest in `api/app.py`. Anders als die Werte in `config.py` lässt sie sich **nicht per Env-Variable ändern**.
- Es gibt **keinen Refresh-Token und keinen Logout-Endpunkt**. Das Web löscht bei jedem 401 das Token und leitet zum Login weiter (`frontend/src/services/api.ts`, `auth.ts`).
- Das Secret kommt aus der Env-Variable `JWT_SECRET`.

### Auth-Header und JWT-Fehler

Das Header-Format ist `Authorization: Bearer <access_token>`. Das sind die Standard-Einstellungen von flask_jwt_extended, die Header-Konfiguration wird im Backend nirgends geändert. Alle Endpunkte außer `/auth/*` und `/health` haben `@jwt_required()`.

flask_jwt_extended antwortet mit seinen Default-Handlern, der Fehlertext steht unter dem Key `msg`:
| Situation | Status | Body |
|---|---|---|
| Header fehlt | 401 | `{"msg": "Missing Authorization Header"}` |
| Token abgelaufen | 401 | `{"msg": "Token has expired"}` |
| Token kaputt oder falsche Signatur (z. B. Secret rotiert) | **422** | `{"msg": "..."}` |

**Folge für die App:** Sowohl 401 als auch 422 sollten als „neu einloggen“ behandelt werden. Das Web fängt nur 401 ab.

## Listings

### `GET /properties/results` (`api/property/propertySearch.py`)

Query-Parameter:
| Param | Pflicht | Werte |
|---|---|---|
| `propertyTypes` | ja | `houses`, `flats`, `sites` (Plural!), auch kommagetrennt kombinierbar |
| `label` | nein | `unbewertet` \| `interessant` \| `uninteressant`. **Weglassen bedeutet „alle“.** |
| `pageSize` | nein | int, Default 50. Das Web nutzt 15. Es gibt kein Maximum. |
| `beforeCreatedAt` | nein | `created_at` des letzten Elements der vorigen Seite, genau so, wie es kam (ISO-String) |
| `beforeId` | nein | `id` des letzten Elements der vorigen Seite |

Response 200 enthält immer alle drei Arrays:
```json
{
  "houses": [HouseResponse...],
  "flats":  [HouseResponse...],
  "sites":  [SiteResponse...]
}
```

`HouseResponse` (`api/property/models.py`), gilt für Häuser und Wohnungen:
```json
{
  "id": "uuid",
  "title": "string|null",
  "image": "url|null",
  "price": 350000.0,
  "location": { "zipCode": "91052", "city": "Erlangen", "street": "string|null", "number": "string|null" },
  "rooms": 5,
  "area_building": 140.0,
  "area_estate": 500.0,
  "provider": "string|null",
  "url": "Link zum Inserat beim Anbieter",
  "source": "z.B. Kleinanzeigen",
  "created_at": "2026-09-29T10:15:02",
  "label": "unbewertet|interessant|uninteressant",
  "constructionYear": 1995,
  "energyEfficiencyClass": "string|null",
  "property_type": "house|flat"
}
```
`SiteResponse` ist dasselbe **ohne** `rooms`, `area_building`, `provider`, `constructionYear`, `energyEfficiencyClass` und `property_type`.

Hinweise zum Typsystem:
- `price`, `area_*` sind in der DB `Float` und kommen als JSON-Zahl mit Nachkommastelle, obwohl die Dataclass `int` behauptet. In Kotlin sollte man `Double?` nehmen.
- **Praktisch jedes Feld kann `null` sein.** Die Scraper füllen, was der Anbieter hergibt (`storage/models.py`, keine NOT-NULL-Constraints außer auf `uuid`/`property_type`). Nur `latitude`/`longitude` sind durch den Filter garantiert gesetzt, werden aber nicht ausgeliefert.
- `created_at` ist ein **naiver Zeitstempel ohne Zeitzone**. Er kommt aus SQLites `CURRENT_TIMESTAMP` und ist faktisch UTC mit Sekundenauflösung. Zum Anzeigen muss die App ihn als UTC interpretieren. Als Cursor sollte sie ihn unverändert zurückschicken.
- `label` ist immer gesetzt. Ohne Relation liefert der Server `"unbewertet"`.

Serverseitige Filterung:
- Gefiltert wird nach den Suchprofilen des Users: OR über alle Profile des passenden Typs, jeweils mit Preis, Zimmern, Baujahr, Fläche und Haversine-Distanz ≤ `radius` km. Die `provider_blacklist` aller Profile wird vereinigt und angewendet, **allerdings nur bei Häusern und Wohnungen, nicht bei Grundstücken**. `source_blacklist` wird in der Abfrage gar nicht benutzt.
- **Hat der User kein einziges Suchprofil, kommt 400 `{"Error": "Keine Suchparameter gefunden"}`**, auch wenn nur ein Typ ohne Profil abgefragt wird. Hat er nur für andere Typen Profile, kommt 200 mit leeren Arrays. Die App braucht für den 400-Fall einen Leer-Zustand mit Verweis auf die Suchprofile.
- Fehlt `propertyTypes`, kommt 400 `{"Error": "Keine Immobilientypen angegeben"}`.
- Ein kaputtes `beforeCreatedAt` gibt 400 `{"Error": "Ungueltiger beforeCreatedAt Cursor"}`.
- Ein ungültiges `label` gibt bei `sites` 400 `{"Error": "Invalid label"}`, bei `houses`/`flats` aber **500 (HTML)**, weil der `NameError` dort nicht abgefangen wird.

Pagination:
- Sortiert wird nach `created_at DESC, uuid DESC`. Der Cursor ist `(created_at, id)` des letzten Elements, bei Gleichstand entscheidet die uuid.
- **Es gibt kein `hasMore` und keinen Total-Count.** Das Web nimmt an, dass es weitere Seiten gibt, solange eine Seite genau `pageSize` Elemente hat.
- `pageSize` gilt pro Abfrage. Häuser und Wohnungen laufen durch **eine gemeinsame** Abfrage (`houses,flats` zusammen ergeben höchstens `pageSize` Elemente insgesamt), Grundstücke durch eine eigene. **Empfehlung: pro Request nur einen Typ abfragen**, so wie das Web. Dann ist der Cursor eindeutig.
- Cursor-Pagination mit Label-Filter: Wird ein Element während des Blätterns umbewertet, verschwindet es aus dem gefilterten Tab, aber der Cursor bleibt gültig, weil er auf `created_at` basiert. Nach „alle als uninteressant markieren“ ruft das Web einfach `loadListings()` erneut auf. Die App sollte stattdessen die Liste zurücksetzen.

### `PATCH /properties/<type>/<id>/label`

- `<type>`: `site` wählt Grundstücke, **jeder andere Wert** wählt die Tabelle für Häuser und Wohnungen. Das Web schickt `house`/`flat`/`site` (Singular, `Listing_Card.vue`).
- Body: `{"label": "interessant"}`. Erlaubt sind die Werte von `LabelEnum`: `unbewertet`, `interessant`, `uninteressant` (`storage/enums.py`).

| Status | Body |
|---|---|
| 200 | `{"propertyId": "<id>", "label": "<label>"}` |
| 400 | `{"error": "invalid label value"}` |
| 404 | `{"error": "listing not found"}` |

- Der Aufruf ist idempotent (Upsert der User-Relation) und eignet sich damit für optimistisches UI mit Retry.

### `PATCH /properties/labels` (Bulk)

- Body: `{"propertyType": "houses", "label": "uninteressant"}`. Das Web schickt hier den **Plural** (`houses`/`flats`/`sites`, `ListView.vue`). Der Server prüft nur `== "site"`, darum landet **`sites` (Plural) im Haus-Zweig!**
- Response 200 `{}`, bei ungültigem Label 400 `{"error": "invalid label value"}`.
- **Fallstricke:**
  1. Der Endpunkt markiert **alle** Objekte der Tabelle ohne User-Relation, **unabhängig von Suchprofilen, Radius oder Blacklist**. Das sind auch Objekte, die der User nie gesehen hat.
  2. Häuser und Wohnungen teilen sich eine Tabelle. „Alle Häuser als uninteressant“ markiert also auch alle unbewerteten Wohnungen, und umgekehrt.
  3. Im Web wird `propertyType=sites` gesendet. Weil der Server `site` erwartet, markiert der Button im Grundstücks-Tab nach dem Code **Häuser und Wohnungen statt Grundstücken**. Das sieht nach einem echten Bug im Web aus.
  4. Ein Listing, das der Scraper erst nach dem Laden der Liste gespeichert hat, wird ebenfalls markiert, ohne dass der User es je gesehen hat.
- **Empfehlung für die App:** Den Bulk-Endpunkt nur mit `propertyType: "site"` bzw. `"house"` nutzen und im UI klar machen, dass Häuser und Wohnungen gemeinsam betroffen sind. Die sicherere Variante: Die App ruft für die geladenen, sichtbaren IDs einzeln `PATCH …/label` auf, oder das Backend bekommt einen neuen Endpunkt mit ID-Liste (siehe „Backend-Lücken“).

### Label-Semantik: Fallstrick „unbewertet“

- `label=unbewertet` filtert auf `relation.label IS NULL`, also nur auf Listings **ohne** Relation (`get_label_enum` gibt für `unbewertet` `None` zurück).
- Setzt der User ein Listing per `PATCH …/label {"label":"unbewertet"}` zurück, entsteht eine Relation mit `OPEN`. Das Listing hat dann `label: "unbewertet"` in der Response, **erscheint aber weder im Tab „unbewertet“ noch in „interessant“ oder „uninteressant“, sondern nur noch in „alle“.**
- Die App sollte deshalb **nie `unbewertet` per PATCH setzen**, also keine Undo-Funktion auf diesem Weg bauen, oder das Backend muss vorher angepasst werden.
- Labels gelten **pro User**. Familienmitglieder mit eigenem Login sehen die Bewertungen der anderen nicht. Suchprofile sind ebenfalls pro User.

## Suchprofile (`api/userPreferences/userPreferences.py`)

Response-Objekt `SearchPreferenceResponse` (`api/userPreferences/models.py`):
```json
{
  "uuid": "…", "userId": "…",
  "property_type": "HOUSE|FLAT|SITE",
  "city": "Erlangen", "zipCode": "91052",
  "latitude": "49.59", "longitude": "11.00",
  "radius": 20,
  "provider_blacklist": ["…"], "source_blacklist": ["…"],
  "priceLimit": 500000, "minRooms": 4,
  "minConstructionYear": 1970, "maxConstructionYear": null, "minArea": 400
}
```
- `latitude`/`longitude` kommen als **Strings**.
- `property_type` wird in der Response GROSS geschrieben. Beim Schreiben ist die Schreibweise egal (`.lower()`).

| Endpunkt | Body | Erfolg | Fehler |
|---|---|---|---|
| `GET /preferences` | – | 200, Array | – |
| `POST /preferences` | Felder s. u. | 201, Objekt | 400 `{"error": "Maximum of 10 search preferences allowed per user."}` |
| `PATCH /preferences/<uuid>` | Teilmenge der Felder | 200, Objekt | 404 `{"error": "Search preference not found"}`, 500 `{"error": "Internal server error"}` |
| `DELETE /preferences/<uuid>` | – | 200 `{"message": "Deleted successfully"}` | 404 wie oben |

Schreibbare Felder: `property_type`, `city`, `zipCode`, `radius`, `provider_blacklist`, `source_blacklist`, `priceLimit`, `minRooms`, `minConstructionYear`, `maxConstructionYear`, `minArea`. Es gilt Partial-Update-Semantik, gesetzt wird nur, was im Body steht. Zahlenfelder gehen durch `_to_int`, dabei werden `""`, `null` und Unparsebares zu `null`.

Fallstricke:
- `latitude`/`longitude` berechnet der Server durch **Geocoding von `"<zipCode> <city>"`** bei jedem POST/PATCH (`utils/geolocation.py`, Nominatim → Google). Der Aufruf kann also einige Sekunden dauern und braucht einen großzügigen Timeout.
- Findet das Geocoding nichts, ist das Ergebnis `(None, None)`, aber die DB-Spalten sind `NOT NULL`. Bei POST gibt das eine **unbehandelte IntegrityError und damit HTML-500**, bei PATCH `{"error": "Internal server error"}`. Dasselbe gilt, wenn bei POST `property_type`, `city`, `zipCode` oder `radius` fehlen. Das Web prüft Stadt, PLZ und Radius vorher selbst, die App sollte das genauso machen.
- Ein ungültiger `property_type` wirft `ValueError` und führt ebenfalls zu 500.
- Welche Felder für welchen Typ gelten, zeigt `SearchFormFields.vue`: Haus nutzt Preis, Zimmer, Baujahr min/max und Grundstücksfläche. Wohnung nutzt Preis, Zimmer und Baujahr min, `minArea`/`maxConstructionYear` werden beim Filtern ignoriert. Grundstück nutzt Preis und `minArea`.
- `provider_blacklist` wirkt als Teilstring-Match (`ILIKE %x%`) und nur bei Häusern und Wohnungen.
- `source_blacklist` wird gespeichert, laut Code aber nur von den Scrapern genutzt, nicht beim Filtern der Ergebnisse.

## Bilder

- `image` ist die **rohe URL, die der Scraper beim Anbieter gefunden hat** (z. B. `kleinanzeigen.py` `get_image`, `sparkasse.py`, `interhyp.py` `originalUrl`, `vr_bank.py`/`myhome_makler.py` direkt das `src`-Attribut). Das Backend hat **keinen Bild-Proxy und keinen Cache**. Das Web setzt die URL direkt als `background-image` (`Listing_Card.vue`), lädt die Bilder also per Hotlinking.
- Folgen für Android:
  - `image` kann `null` sein. ZVG-Portal liefert grundsätzlich `null`, viele andere nur manchmal. Die App braucht einen Platzhalter.
  - Bei Adaptern, die `src` ohne `urljoin` übernehmen (`vr_bank.py`, `myhome_makler.py`), kann die URL **relativ** oder eine Lazy-Load-`data:`-URL sein. Das sollte die App defensiv behandeln und bei Fehlern den Platzhalter zeigen.
  - Bei `http://`-URLs blockiert Android ab API 28 Cleartext-Traffic standardmäßig. Freischalten ginge per `networkSecurityConfig`, oder die App zeigt ohne Bild an.
  - Anbieter können Hotlinks per Referer- oder User-Agent-Prüfung blocken oder Bild-URLs ablaufen lassen. Coil/Glide senden keinen Referer, was meist hilft, aber ein Cache (Coil-Disk-Cache) und ein Fehler-Platzhalter sind Pflicht.
- `url` zeigt auf das Original-Inserat. Das Web öffnet es in einem neuen Tab, die App sollte Custom Tabs bzw. einen `ACTION_VIEW`-Intent nutzen.

## Fehlerformate im Überblick

| Quelle | Format |
|---|---|
| Login, results (400) | `{"Error": "…"}` (großes E) |
| Label, Preferences | `{"error": "…"}` |
| JWT | `{"msg": "…"}` (401/422) |
| Unbehandelte Exceptions, fehlender JSON-Content-Type | HTML-Seite (500/415) |

**Empfehlung:** In der App einen Fehler-Parser bauen, der `error`, `Error` und `msg` probiert und auf den HTTP-Status zurückfällt. Eine HTML-Antwort sollte als generischer Serverfehler gelten.

## Lücken im Backend und Wünsche für eine native App

Keine dieser Lücken blockiert den MVP, aber sie sind relevant:
1. **Kein Refresh-Token.** Alle 2 Tage muss man sich neu einloggen. Mögliche Optionen: (a) akzeptieren, (b) Zugangsdaten im Android Keystore bzw. in EncryptedSharedPreferences ablegen und still neu einloggen, (c) im Backend `JWT_ACCESS_TOKEN_EXPIRES` per Env konfigurierbar machen oder Refresh-Tokens (`create_refresh_token`, `/auth/refresh`) ergänzen.
2. **Kein `/me`-Endpunkt.** Der Username steht im JWT-Claim `username`, das reicht.
3. **Keine Zählwerte** (z. B. Anzahl unbewerteter Listings für Badge oder Benachrichtigung) und kein „neu seit“-Endpunkt. Für Benachrichtigungen müsste die App die erste Seite pollen und den neuesten `created_at` vergleichen.
4. **Kein `hasMore`.** Man muss es aus `len < pageSize` ableiten.
5. **Bulk-Label ignoriert die Suchprofile** und fasst Häuser und Wohnungen zusammen, dazu kommt der Plural-Bug bei `sites` (s. o.). Besser wäre ein Endpunkt mit ID-Liste oder einer, der dieselben Filter wie `/results` anwendet.
6. **`unbewertet` per PATCH macht Listings unsichtbar** (s. o.). Abhilfe wäre ein Filter `label IS NULL OR label = OPEN` oder das Löschen der Relation.
7. **Keine Koordinaten in der Listing-Response.** Eine Kartenansicht wäre nicht möglich, obwohl `latitude`/`longitude` in der DB stehen.
8. **Uneinheitliche Fehler-Keys** und HTML-500 bei Eingabefehlern (Login ohne Feld, ungültiges Label bei Häusern, Geocoding-Fehlschlag).

## Quellen

- `api/app.py`: JWT-Konfiguration (2 Tage, `JWT_SECRET`), CORS, `/health`
- `api/auth/auth.py`: Login und Register
- `api/property/propertySearch.py`, `api/property/models.py`: Results, Labels, Response-Modelle
- `api/userPreferences/userPreferences.py`, `api/userPreferences/models.py`: Suchprofile
- `storage/models.py`, `storage/enums.py`, `storage/db.py`: Schema, Label-Enum, SQLite
- `utils/geolocation.py`: `getCoordinatesFromAddress` liefert `(None, None)`, wenn nichts gefunden wird
- `scraper/adapters/*.py`: Herkunft der Bild-URLs
- `frontend/nginx.conf`, `frontend/vite.config.js`: `/api`-Präfix, Proxy
- `frontend/src/services/api.ts`, `auth.ts`, `views/ListView.vue`, `components/Listing_Card.vue`, `views/Preferences.vue`, `components/SearchFormFields.vue`, `router/index.ts`: Nutzung durch das Web
- JWT-Fehlerformate (`msg`, 401 bzw. 422) sind die Defaults von flask_jwt_extended. Das Backend überschreibt keine Error-Loader.
