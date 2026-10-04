# Handoff – HA Widget Bridge

Stand: **2026-10-03, 13:4x** · Branch `main`, HEAD `f146b6d` · Release **v0.1.13**
Dieses Dokument beschreibt das komplette Projekt. Alle bis hier gemeldeten Fehler sind
**behoben und am Gerät verifiziert** (§8).
Es ist **nicht** committet (bewusst: das Repo ist öffentlich).

---

## 1. Was das Projekt ist

Ein Home-Assistant-Widget für Android **und** Wear OS, das **Werte und Buttons in einer
einzigen Fläche** zeigt – die offizielle HA-App kann das nicht (Action-Button schaltet nur,
Template-Widget hat genau einen Klick-Handler „aktualisieren“).

Drei Teile arbeiten zusammen:

| Teil | Technik | Aufgabe |
|---|---|---|
| **HA-Integration** `ha_widget_bridge` | Python, keine Abhängigkeiten | speichert die Widget-Definitionen, rendert die Inhalte serverseitig, stellt JSON-API + Dienste bereit |
| **Handy-App** (`android/app`) | Kotlin, Compose (Editor) + RemoteViews (Widget) | Editor (schreibt nach HA), Homescreen-Widget, verteilt Stände an die Uhren |
| **Uhr-App** (`android/wear`) | Kotlin, `androidx.wear.tiles` + `play-services-wearable`, **kein** Compose/HTTP | Kachel (Tile) + scrollbare App-Ansicht auf der Uhr |

Repo: **https://github.com/Harry3349/ha-widget-bridge** (public) · lokal
`/home/erikr/Dokumente/Programmierung/HomeAssistant/ha-widget-bridge`

Datenfluss:

```
Handy-App ──HTTP──▶ Home Assistant (Integration rendert)
    │                     ▲
    │  Snapshot (JSON)    │ Button-Druck (Dienst)
    ▼                     │
Wearable Data Layer ──▶ Uhr (Kachel + App-Ansicht)
```

---

## 2. Umgebung

| | |
|---|---|
| Home Assistant | 2026.9.3, **Docker**, Python 3.14 · Config unter `/mnt/homeassistant` (= `/config`) |
| Integration liegt in | `/mnt/homeassistant/custom_components/ha_widget_bridge` (per `cp -r` kopiert, HACS würde überschreiben) |
| HA-Neustart | `POST /api/services/homeassistant/restart` (Antwort oft **502** = HA ist schon weg); danach 15–30 s warten, bis `/api/ha_widget_bridge/widgets` wieder 200 liefert |
| Handy | Xiaomi 17, `25ea7289` (USB), HyperOS · Paket `de.reimann.hawidget.debug` |
| Uhr | Pixel Watch 3, `192.168.40.101:<port>`, Android 17/API 37 · Paket `de.reimann.hawidget.debug` (**identisch mit dem Handy**, siehe §6) |
| adb zur Uhr | Port wechselt: `avahi-browse -rtp _adb-tls-connect._tcp` (ggf. **zweimal** ausführen, Auflösung klappt oft erst beim zweiten Mal) → `adb connect <ip>:<port>` |
| API-Zugang | Long-Lived-Token aus der App-Prefs: `adb -s 25ea7289 exec-out run-as de.reimann.hawidget.debug cat /data/data/de.reimann.hawidget.debug/shared_prefs/ha_widget_bridge_settings.xml > /tmp/hawb_settings.xml` · Anfragen brauchen `User-Agent: okhttp/4.12.0` (sonst Cloudflare 403) und `Authorization: Bearer <token>` |

Wichtige Einschränkungen: **auf dem Handy lassen sich keine Eingaben per adb senden**
(`INJECT_EVENTS` gesperrt) und der Launcher lässt sich nicht per `am start` öffnen →
UI nur über Screenshots/`uiautomator dump` prüfbar. Auf der **Uhr** funktionieren
`input tap/swipe/keyevent` und die Tile-Broadcasts.

Nützliche Befehle (Uhr = `adb -s 192.168.40.101:<port>`):

```bash
# Kachel anzeigen / neu aufnehmen
adb -s <uhr> shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE \
    --es operation remove-tile --ecn component de.reimann.hawidget.debug/de.reimann.hawidget.wear.tile.WidgetTileService
adb -s <uhr> shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE \
    --es operation add-tile --ecn component de.reimann.hawidget.debug/de.reimann.hawidget.wear.tile.WidgetTileService
adb -s <uhr> shell am broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-tile --ei index 0
adb -s <uhr> exec-out screencap -p > /tmp/tile.png

# Uhr-App (scrollbar) öffnen, Krone simulieren
adb -s <uhr> shell am start -n de.reimann.hawidget.debug/de.reimann.hawidget.wear.app.WearAppActivity
adb -s <uhr> shell input scroll --axis SCROLL,3     # Krone
# Bildschirmzeit für Tests: settings put system screen_off_timeout 600000 (danach wieder 10000)

# Logs
adb -s 25ea7289 logcat -s HAWidgetBridge
adb -s <uhr> logcat -s HAWidgetBridge

# Widget-Definitionen ansehen/ändern
python3 /tmp/hawb_snapshot.py            # Snapshot eines Widgets ausgeben
python3 /tmp/hawb_set_widget.py <id> …   # Definition über die API ändern
```

---

## 3. Dateikarte

### Integration `custom_components/ha_widget_bridge/`
| Datei | Inhalt |
|---|---|
| `const.py` | Grenzen/Defaults: `MAX_ROWS=8`, `MAX_ROW_ITEMS=3`, `WATCH_ROWS_MIN/MAX`, `WATCH_SCALE_MIN/MAX=0.6/1.8`, `WIDGET_TARGETS=("phone","watch")`, `LEGACY_TARGETS={"both":"phone"}`, `MAX_WATCH_NODES=5` |
| `store.py` | Validierung + Persistenz (`.storage/ha_widget_bridge.widgets`), `normalize_widget()` |
| `render.py` | `value_view()` (Werte), `row_view()` (Zeilen-Layout), `async_render_widget()` (Snapshot) |
| `http.py` | `/api/ha_widget_bridge/*` (widgets, snapshot, preview, action) |
| `sensor.py` / `button.py` | je Widget ein Inhalts-Sensor + eine Button-Entity pro Taste |
| `actions.py`, `config_flow.py`, `__init__.py` | Dienste, Einrichtung, Setup |

### Handy-App `android/app/src/main/java/de/reimann/hawidget/`
| Datei | Inhalt |
|---|---|
| `MainActivity.kt` | Navigation (`Screen.{WIDGETS,EDITOR,SETUP}`), `BackHandler`, „Änderungen verwerfen?“-Dialog |
| `ui/WidgetListScreen.kt` | **Startbildschirm**: Bereiche *Handy (Homescreen)* und *Smartwatch (Uhr)*, Duplizieren-Dialog (Ziel: Handy/Uhr) |
| `ui/WatchSection.kt` | `WatchCard`: pro Uhr nur **„Fassung wählen“** (Dropdown) + „Zeigt: …“ |
| `ui/WidgetEditorScreen.kt` | Editor (Name, Werte, Buttons, **Zeilen**, Template, Vorschau, Speichern) |
| `ui/RowEditor.kt` | Zeilen-Editor (Objekte, Ausrichtung, Schriftgröße, Breite), `watchWidget` blendet Uhr-/Handy-Abschnitte passend ein |
| `ui/RowCapacity.kt` | Platz-Schätzungen für Hinweise (Handy 105/180/255 dp, Uhr 150 dp) |
| `ui/MainViewModel.kt` | gesamter App-Zustand (Widgets, Uhren, Editor, Duplizieren, Zuordnen) |
| `widget/WidgetRenderer.kt` | **RemoteViews** des Homescreen-Widgets (Werte-Raster, Button-Zeilen, Zeilen-Layout) |
| `widget/Widgets.kt` | Refresh-Logik + **Verteilung an die Uhren** (`pushToWatches`) |
| `widget/HaWidgetProvider.kt` | AppWidgetProvider (Klick-Handling) |
| `work/RefreshWorker.kt` | Hintergrund-Abruf (`KEY_FORCE` überspringt die Bildschirm-Prüfung) |
| `work/PressWorker.kt` | Button-Druck am Handy-Widget |
| `work/LiveUpdateService.kt` | Live-Modus per WebSocket (~1,5 s Takt, nur bei sichtbarem Bildschirm) |
| `wear/Watches.kt`, `wear/WearSync.kt`, `wear/WearMessageListener.kt` | Data Layer: Knoten-Liste, Senden an Knoten, Empfang von „gedrückt/aktualisieren“ |
| `data/Models.kt`, `data/WidgetJson.kt`, `data/Settings.kt` | Datenklassen, JSON (lesen/schreiben), App-Einstellungen + `WidgetPrefs` |

### Uhr-App `android/wear/src/main/java/de/reimann/hawidget/wear/`
| Datei | Inhalt |
|---|---|
| `tile/WidgetTileService.kt` | Kachel-Einstieg: rendert den **gespeicherten** Snapshot, fragt das Handy bei Stand > 60 s um einen frischen Abruf, verarbeitet Button-Klicks. `FRESHNESS_MILLIS = 15 min`, `RESOURCES_VERSION = "2"` |
| `tile/TileRenderer.kt` | **Kachel-Zeichnung** (protolayout): Titel, „Stand HH:mm“, Zeilen, Hinweis, Button-Breiten, Höhen-/Zeilen-Schätzung |
| `app/WearAppActivity.kt` | scrollbare App-Ansicht (Finger + Krone), Zellbreiten über Gewichte |
| `bridge/SnapshotListener.kt` | `WearableListenerService`: nimmt Stände (Message/DataItem) entgegen, `applySnapshot()` schreibt nur bei Änderung, merkt sich `phoneNode` |
| `data/Bridge.kt` | Nachrichten an das Handy (`press`, `refresh`) |
| `data/Models.kt`, `data/WidgetJson.kt` | Snapshots/Zeilen auf der Uhr |

### Werkzeuge / CI
| Datei | Zweck |
|---|---|
| `tests/test_logik.py` | 103 Prüfungen: `store`/`render` ohne HA-Installation (Attrappen in `sys.modules`) |
| `tools/check.py` | JSON/XML/YAML syntaktisch |
| `tools/check_kotlin.py` | Klammer-Balance aller Kotlin-Dateien (**keine** Referenzprüfung!) |
| `tools/check_resources.py` | `R.<typ>.<name>` / `@typ/name` gegen vorhandene Ressourcen |
| `tools/check_widget_layout.py` | prüft die `VALUE_*`-ID-Listen des RemoteViews-Layouts (221 Prüfungen) |
| `.github/workflows/checks.yml` | Tests + Tools bei Änderungen |
| `.github/workflows/android.yml` | baut **beide** APKs (Artefakte `HAWidgetBridge-debug`, `HAWidgetBridge-wear-debug`) |

**Kein lokales Android-SDK** → gebaut wird immer per CI. Nur so gibt es echte
Compile-Fehler (IDE-„get_errors“ meldete wiederholt fälschlich „keine Fehler“).

---

## 4. Datenmodell (Widget-Definition)

```jsonc
{
  "id": "shelly_uhr", "name": "Shelly & Klima (Uhr)",
  "target": "watch",              // "phone" | "watch"  (früher "both" → gilt jetzt als "phone")
  "watch_nodes": ["83089501"],    // leer = alle Uhren
  "values": [ { "entity": "...", "label": "...", "threshold": 5, "color": "#4DD0E1" } ],
  "buttons": [ { "key": "licht", "label": "Licht", "service": "switch.toggle", "icon": "mdi:lightbulb",
                 "show_state": true, "entity_id": "...", "state_entity": "..." } ],
  "rows": [                        // max. 8 Zeilen, je max. 3 Objekte
    { "items": [
        { "type": "button", "key": "erik_pc", "label": "Erik PC", "align": "left", "size": 14, "width": 70 },
        { "type": "sensor", "entity": "...",    "align": "right", "size": 14, "width": 0 }
    ] }
  ],
  "watch_rows": 0,                 // 0 = so viele, wie hineinpassen
  "watch_scale": 1.0,              // 0.6–1.8
  "value_columns": 2, "value_label_above": true, "text_size": 14,
  "theme": { "background": "#00000000", ... },
  "revision": 2                    // zählt bei jeder Speicherung hoch
}
```

Regeln:
* `rows` ersetzt – sobald **eine** Zeile existiert – Werte-Raster und Button-Zeilen.
* `width` = Prozent der Zeilenbreite; `0` = teilt sich den **Rest** gleichmäßig.
* In einem **Button** sitzt das Symbol immer links, „An/Aus“ immer rechts; die
  Ausrichtung verschiebt nur den **Titel**.
* Ein Widget gehört **genau einer** Seite (`phone` oder `watch`). Uhr-Fassungen entstehen
  per **Duplizieren** (Ziel „Smartwatch-Widget“) – es gibt kein „gemeinsames“ Widget mehr.
* Eine Uhr hat höchstens **eine** Fassung (Zuordnung löst den Knoten aus allen anderen).

---

## 5. Abläufe (wichtig für Fehlersuche)

**Rendering**
* Handy: `WidgetRenderer` baut RemoteViews aus dem Snapshot (Werte-Raster oder Zeilen).
* Kachel: `TileRenderer` zeichnet **den auf der Uhr gespeicherten Snapshot**.
* Uhr-App: `WearAppActivity` zeichnet denselben Snapshot, scrollbar.

**Übertragung an die Uhr (nach Akku-Optimierung, v0.1.11)**
* Das Handy sendet **nur**, wenn sich die Fassung geändert hat (`revision`) oder nach einer
  Aktion in der App (`force = true`); sonst passiert nichts – auch keine Bluetooth-Abfrage
  (`PUSH_CHECK_MS = 60 s`).
* Die Uhr holt sich den Stand **selbst**, wenn die Kachel sichtbar wird und der letzte
  Empfang > 60 s her ist (`Bridge.refresh` → Handy lädt Snapshot → `pushSnapshotToNode`).
* Die Uhr-App fragt beim Öffnen ebenfalls (`onResume`).
* Nutzen: im Live-Modus vorher ~25 Nachrichten/50 s → jetzt **1**.

**Speicherorte**
* Definition: HA `.storage/ha_widget_bridge.widgets`
* Snapshot am Handy: `WidgetPrefs` → `ha_widget_instances.xml` (`snapshot_<widgetId>`)
* Snapshot auf der Uhr: `Settings` → `hawidget.xml` (`snapshot`, `last_refresh`, `phone_node`)

---

## 6. Harte Lehren (nicht wiederholen)

* **Wearable Data Layer ordnet über den Paketnamen zu.** Handy- und Uhr-App **müssen**
  dasselbe `applicationId` haben (`de.reimann.hawidget`, debug → `.debug`), sonst kommt auf
  der Uhr nichts an (`WearableService: Failed to deliver message to AppKey`). Der alte
  Zustand (Uhr-App hieß `…wear.debug`) hat den ganzen Datenaustausch stillschweigend
  verhindert.
* **Gewichte ≠ Prozente.** Ein LinearLayout-Gewicht von 70 neben „Rest“ ergibt 70/71 ≈ 99 %.
  Entweder echte dp-Breiten (RemoteViews/Kachel) oder Gewicht + Auffüll-Feld verwenden.
* **HACS überschreibt Handkopien** mit dem Inhalt des letzten Release-Tags ⇒ nach jeder
  Änderung an `custom_components/` die Version in `manifest.json` erhöhen und ein Release
  anlegen, sonst holt ein HACS-Update den alten Stand zurück.
* **Tile nach `adb install -r` neu hinzufügen** (`remove-tile` + `add-tile`), sonst bleibt sie
  im Karussell verschwunden/alt.
* `tools/check_kotlin.py` prüft nur Klammern – **Referenzen prüft nur die CI**.
* Bildschirm aus = keine Abrufe (Stromsparen); Aktionen in der App nutzen `force = true`.
* Handy: `setInt(id,"setGravity",…)` auf TextViews und `setViewLayoutWidth` (API 31+) sind
  die einzigen Wege, RemoteViews-Breiten/Ausrichtung zu setzen.

---

## 7. Aktueller Stand (verifiziert)

| Was | Stand |
|---|---|
| HA-Integration | in `/config` steht 0.1.11 (Code identisch, nur Versionsnummer älter); ein HACS-Update auf 0.1.13 zieht den Tag-Inhalt · API liefert `target`, `watch_nodes`, `rows` mit `width` |
| Handy-App | 0.1.13 installiert (`versionCode 13`), Homescreen-Widget rendert Zeilen mit 70 %-Buttons |
| Uhr-App | 0.1.13 installiert (`versionCode 10`, `lastUpdateTime` 13:37) – identisch mit dem Release; App-Symbol ist das adaptive Icon (§8) |
| Widgets in HA | `shelly` (phone, rev 19, 5 Zeilen) und `shelly_uhr` (watch, Knoten `83089501`, rev 2, 5 Zeilen) – **inhaltlich identisch** |
| Uhr-Snapshot | rev 2, 5 Zeilen, letzter Empfang 13:37 (frisch) |
| Zuletzt verifiziert (03.10. 13:36) | **Kachel fragt beim Anzeigen das Handy** (Log beider Seiten), zeigt alle 5 Zeilen · **App-Ansicht und Kachel sind deckungsgleich** (Screenshots) · Uhr-App-Symbol sichtbar |

Letzte Commits:

```
f146b6d  (tag v0.1.13) Uhr-App-Ansicht: Zeilenbreiten in echten Pixeln wie auf der Kachel
73e9548  Uhr-App-Ansicht an die Kachel angeglichen (nicht umgekehrt)
ea0ba33  Revert "Uhr-Kachel: Kopf, Abstaende und Buttons wie in der App-Ansicht"
a250e1c  Uhr-Kachel: Kopf, Abstaende und Buttons wie in der App-Ansicht   ← zurueckgenommen
f244d19  Uhr: Kachel beim Anzeigen wirklich fragen (Frischezeit 15 min -> 60 s)
0931db9  Uhr: eigenes App-Symbol statt weissem Vektor
bfb0162  Release 0.1.13: App-Symbol der Uhr (Versionen)
b8f8b7c  (tag v0.1.12) Release 0.1.12: Kachel-Fix und Diagnose-Log ausliefern
0bbb874  Kachel: Diagnose-Log fuer Fassung und Zeilen, README-Hilfe bei fehlenden Zeilen
4557829  Kachel: Hoehenschaetzung korrigiert, damit keine Zeilen mehr verschwinden
```

---

## 8. Kachel zeigte nur 3 von 5 Zeilen – **behoben und verifiziert** (2026-10-02)

### Symptom (Nutzer, damals)
> „Die Uhr zeigt eine alte Version des Widgets an, wo Temperatur, Feuchtigkeit und USB-Power
> noch nicht eingebunden waren. Von der Größe passt alles drauf. Die Buttons sehen anders aus.“

Sichtbar sind auf der **Kachel** nur die drei Button-Zeilen mit ihren Leistungswerten
(+ Hinweis „Antippen: alle Zeilen“). Die Zeilen 4 (Temp/Luftfeuchte) und 5 (USB) fehlen.

### Faktenlage (gemessen, 18:0x)
1. `shelly` und `shelly_uhr` sind **Zeile für Zeile identisch** (per API verglichen).
2. Der auf der Uhr **gespeicherte** Snapshot ist rev 2 mit **5 Zeilen** –
   Zeile 4/5 enthalten `Temp 17.7 °C`, `Luftfeuchte 77.0 %`, `USB PC-Tisch 9.2 W`, `USB Nacht 0.0 W`.
3. Der Hinweis **„Antippen: alle Zeilen“** wird nur gezeichnet, wenn der Renderer *mehr* Zeilen
   hatte, als er zeigte (`truncated`). Bei einem alten 3-Zeilen-Widget gäbe es ihn **nicht**.
   → Die Kachel zeichnete den **aktuellen** Snapshot und hat ihn gekürzt.
4. Die **Uhr-App** (scrollbar) zeigt alle 5 Zeilen korrekt (Screenshot).
5. Auf dem Kachel-Screenshot endet der Inhalt bei y≈246 px von 456 px (≈110 dp von 203 dp) –
   es sind **~93 dp ungenutzt**, der Nutzer hat also recht mit „von der Größe passt alles drauf“.

### Ursache (Code)
`TileRenderer.kt`:
```kotlin
private const val HEADER_DP = 46f        // zu großzügig
private const val BOTTOM_SAFE_DP = 46f   // zu großzügig
private fun rowHeightDp(row, scale) = when (item.type) {
    "button" -> 34f        // fest, ignoriert scale – real sind ~17–20 dp
    "sensor" -> 30f        // fest, ignoriert scale – real sind ~15 dp
    ...
}
```
Damit: `band = 203 − 46 − 46 = 111 dp`; 3 Buttons (3 × 34 = 102) passen, der erste Sensor
(+30 = 132) nicht → **genau 3 Zeilen**. Die übrigen werden stillschweigend weggelassen.

### Fix (verifiziert am 2026-10-02 um 18:12)
`4557829`:
* `HEADER_DP = 34`, `BOTTOM_SAFE_DP = 30`, `HINT_DP = 12`
* `rowHeightDp`: `button = 20 × scale`, `sensor = 15 × scale`, Text = `size × scale × 1.3 + 4`
* **kein** automatisches Verkleinern der Schrift (auf ausdrücklichen Wunsch entfernt;
  `4a89eaa` hatte es kurzzeitig eingebaut)
* Rechnung damit: 5 Zeilen = 3 × 20 + 2 × 15 = **90 dp** ≤ 139 dp Band ⇒ **alle 5 Zeilen
  passen bei unveränderter Schriftgröße**. Das war der eigentliche Fix.

**Übernahme (erledigt):** Artefakt `37032010284` (`HAWidgetBridge-wear-debug`) geladen, per
`adb install -r` installiert (`lastUpdateTime = 2026-10-02 18:12:43`), Kachel danach per
`remove-tile` + `add-tile` neu aufgenommen.

**Beweis:** Der Screenshot um 18:12 zeigt **alle 5 Zeilen** – Erik PC / Licht / 3D-Drucker mit
ihren Leistungswerten, darunter `Temp 17.3 °C`, `Luftfeuchte 78.3 %` und `USB PC-Tisch 16.1 W`,
`USB Nacht 0.0 W` – und **keinen** Hinweis „Antippen: alle Zeilen“ mehr. Es war also wirklich
nur die Kürzung, **kein** veraltetes Rendering.
Der Release-Build `v0.1.12` (`b8f8b7c`) wurde um 18:24 genauso geprüft (Screenshot: 5 Zeilen,
Log weist `versionName 0.1.12` aus). Die Pixel Watch 3 meldet **228 × 228 dp** Kachelgröße
(also 164 dp Band nach Kopf/Fuß) – bei 5 Zeilen à 20/15 dp sind das 90 dp, es bleibt Platz.

```bash
# so wurde geprüft (Uhr = 192.168.40.101:<port>, Port s. §2)
cd /tmp && rm -rf art && mkdir art
gh -R Harry3349/ha-widget-bridge run download 37032010284 -D art
adb -s <uhr> install -r art/HAWidgetBridge-wear-debug/wear-debug.apk
adb -s <uhr> shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE \
    --es operation remove-tile --ecn component de.reimann.hawidget.debug/de.reimann.hawidget.wear.tile.WidgetTileService
adb -s <uhr> shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE \
    --es operation add-tile --ecn component de.reimann.hawidget.debug/de.reimann.hawidget.wear.tile.WidgetTileService
adb -s <uhr> shell am broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-tile --ei index 0
adb -s <uhr> exec-out screencap -p > /tmp/tile.png
```

### Diagnose-Log (`0bbb874`)
`WidgetTileService.onTileRequest` protokolliert jetzt bei jeder Kachel-Anfrage (Tag
`HAWidgetBridge`):

```
Kachel: Fassung <revision>, <gezeigt>/<erlaubt> Zeilen (gesamt <n>), sichtbar <n>,
        gekuerzt <bool>, Schrift <scale>, Stand <ms>, <w>x<h> dp
```

Die Zahlen kommen aus dem neuen `TileRenderer.layoutPlan()` – derselbe Code, der auch
zeichnet (kein zweiter Rechenweg, der auseinanderlaufen könnte):

```bash
adb -s <uhr> logcat -s HAWidgetBridge
```

### Falls wieder einmal Zeilen fehlen (Prüfreihenfolge)
Der Verdacht wäre dann ein **veraltetes Rendering**, nicht die Kürzung. Prüfreihenfolge:
1. **Marker-Test (beweiskräftig, 2 Minuten):** Widget-Namen über die API ändern
   (z. B. `"Shelly & Klima (Uhr) [neu]"`), Push erzwingen (App-Aktion oder Uhr-Anzeige).
   * Kachel zeigt den neuen Namen ⇒ sie rendert den aktuellen Snapshot ⇒ Kürzung (Fix oben).
   * Kachel zeigt den alten Namen ⇒ die Kachel bekommt/zeichnet einen alten Stand → Bug im
     Auslieferungs-/Renderweg.
2. **Log im Tile-Dienst** mitlesen (seit `0bbb874` vorhanden, siehe oben): Fassung (Revision),
   Zeilen, sichtbar/gekürzt, Schrift, Stand. Zeigt sofort, was die Kachel sieht.
3. **`RESOURCES_VERSION`** in `WidgetTileService.kt` ist hart `"2"` und betrifft nur die
   **Symbole**. Bleibt nach einer App-Aktualisierung die Darstellung alt, ist das ein Hebel
   (Zahl erhöhen) – im aktuellen Fall war es das **nicht**.
4. **Zweite Kachel im Karussell** ausschließen (Reste der alten Uhr-App
   `de.reimann.hawidget.wear.debug`; wurde deinstalliert, aber prüfen:
   `adb shell dumpsys activity service <TileService>` bzw. Kachel-Karussell durchblättern).
5. **Truncation hart ausschalten** (Test): in `TileRenderer` `shown = rows` erzwingen und
   sehen, ob alle Zeilen erscheinen.

### Kommunikationshinweis
Der Nutzer ist über mehrere Runden hinweg überzeugt, dass eine **alte Widget-Version**
angezeigt wird. Fakten und Vermutungen klar trennen, keine Vorschläge zum Verkleinern der
Schrift machen (wurde ausdrücklich abgelehnt: „Hör auf die Größe anpassen zu wollen“).
Der wirksamste Weg ist ein **Screenshot-Beweis** nach dem Fix.

### Nachtrag 03.10.: Die Kachel aktualisierte nur beim Öffnen der App
* **Symptom (Nutzer):** „das watchos modul aktualisiert nur wenn ich direkt die app öffne,
  nicht wenn ich nur das widget öffne“.
* **Ursache:** `setFreshnessIntervalMillis(15 min)` in `WidgetTileService`. Wear OS liefert die
  Kachel in dieser Zeit aus dem **Zwischenspeicher** und ruft `onTileRequest` **gar nicht** auf –
  die Kachel konnte deshalb auch nicht beim Handy nachfragen. (Die Prüfung „Stand älter als
  60 s → nachfragen“ wirkt nur, wenn die Kachel überhaupt gefragt wird.) Nur
  `WearAppActivity.onResume` fragt selbst an, deshalb war allein die App aktuell.
* **Fix `f244d19`:** `FRESHNESS_MILLIS = 60_000` – laut Doku darf dieser Mechanismus höchstens
  einmal pro Minute aktualisieren.
* **Beweis (13:36–13:37):** Kachel anzeigen → Uhr: `Kachel: Fassung 2 … Stand <frisch>`,
  Handy: `Nachricht von der Uhr: /hawidget/refresh` → `Snapshot an die Uhr 83089501 übertragen`,
  mehrfach innerhalb von Minuten.

### Nachtrag 03.10.: App-Ansicht und Kachel sahen verschieden aus
* **Vorgabe des Nutzers:** die **App-Ansicht anpassen, nicht die Kachel** – die Kachel ist die
  Referenz („du sollst die app ansicht anpassen nicht das widget“). Eine erste Runde hatte es
  umgekehrt gemacht; `ea0ba33` nimmt das zurück (plus Rundung/grüner Titel aus `f244d19`).
* **Echter Fehler in der App-Ansicht:** `WearAppActivity` verteilte die Zellbreiten als
  `LinearLayout`-**Gewichte** (`weight = width`) und füllte den Rest mit einem leeren `View` auf.
  Gewichte drücken keine Prozente aus (nur das Verhältnis am Rest) → der 70-%-Button war
  schmaler als auf der Kachel, `3D-Drucker` brach in zwei Zeilen um, die Werte saßen an anderer
  Stelle. **Fix `f146b6d`:** `widthsOf(items, rowWidthPx)` rechnet in echten Pixeln wie die
  Kachel; Button-Titel `maxLines = 1`.
* **Optik angeglichen (`73e9548`):** Titel 12 sp (nicht fett), „Stand“ 9 sp, keine
  Zeilenabstände, Button 4 dp Innenabstand / 14-dp-Symbol / eckig / weißer Titel.
* **Beweis:** Screenshots von Kachel und App-Ansicht sind deckungsgleich (Titel, Pill-Breiten,
  Werte-Positionen).
* **Messhilfe:** `/tmp/hawb_kachel_messen.py` (PIL) liest einen Kachel-Screenshot aus
  (456 px = 228 dp = 2 px/dp) und listet die hellen Bänder mit Höhe, Abstand und x-Ausdehnung.
  Damit gemessen (verworfene Variante mit 15 sp/Abständen): Inhalt endet bei **188 dp** von
  228 dp, der Titel lief in den runden Rand, die letzte Zeile berührte die Unterkante.
* Nicht verwirren lassen: Kachel-Screenshots kamen je nach Anzeigeweg **hell oder dunkel**
  (Systemrenderer) – Inhalt und Zeilen sind trotzdem dieselben.

---

## 9. Offene Punkte / Backlog

* ✅ Kachel-Fix ausgeliefert und verifiziert (siehe §8).
* Kachel: `watch_rows` (feste Zeilenzahl) ist derzeit die einzige Steuerung – evtl. eine
  Option „alle Zeilen zeigen, Rest weglassen“ gegenüber „so viele wie passen“ im Editor klarer
  benennen.
* ✅ `WidgetTileService`: Diagnose-Log (Revision/Zeilen/gekürzt) ergänzt (`0bbb874`).
* ✅ README-Abschnitt „Fehlersuche → Die Kachel auf der Uhr zeigt nicht alle Zeilen“ ergänzt.
* Uhr-App: nach `adb install -r` die Kachel automatisch neu registrieren? (nur manuell möglich)
* ✅ **Release `v0.1.12`** und **`v0.1.13`** veröffentlicht (beide APKs).
* ✅ Kachel fragt beim Anzeigen das Handy; App-Ansicht = Kachel; App-Symbol der Uhr sichtbar.
* Integration in `/config`: dort steht 0.1.11 (Code identisch) – ein HACS-Update auf 0.1.13 zieht
  den Tag-Inhalt.
* Kachel-Screenshots kommen je nach Anzeigeweg **hell oder dunkel** aus `screencap`
  (Systemrenderer) – beim Vergleich beachten.

---

## 10. Arbeitsweise im Projekt

1. Änderung machen → `python3 tools/check_kotlin.py`, `python3 tools/check.py`,
   `python3 tests/test_logik.py`, `python3 tools/check_resources.py`,
   `python3 tools/check_widget_layout.py`.
2. Committen + pushen (CI baut beide APKs), Build mit
   `gh -R Harry3349/ha-widget-bridge run watch <id> --exit-status` abwarten.
3. APKs laden (`gh run download <id> -D <dir>`), per `adb install -r` auf Handy **und** Uhr.
4. Integration geändert? → `cp -r custom_components/ha_widget_bridge/. /mnt/homeassistant/custom_components/ha_widget_bridge/`
   und HA per API neu starten; Version in `manifest.json` erhöhen + Release anlegen (HACS!).
5. Verifizieren: Screenshots (`exec-out screencap -p`), `uiautomator dump` (Handy),
   `logcat -s HAWidgetBridge` (beide Geräte), API-Snapshots.
6. Erkenntnisse in `/memories/repo/ha-widget-bridge.md` festhalten (dort stehen auch alle
   früheren Fallstricke ausführlich).
