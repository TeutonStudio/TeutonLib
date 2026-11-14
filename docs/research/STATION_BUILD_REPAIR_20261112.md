# Reparatur des Bahnhofs-Baumodus – 12.11.2026

## Ausgangspunkt

Untersucht wurde das Repository unter `/home/alex/Dokumente/VoxelTeuton/Modding/TeutonLib`
(derselbe Checkout wie `/home/alex/VoxelTeutonModding/TeutonLib`). Keine geltenden
`AGENTS.md` im Repository oder seinen Eltern gefunden. Bereits vorhandene Änderungen
an Schlauchrolle, Tankdarstellung, Buildkonfiguration und Dokumentation bleiben erhalten.

Referenz: `../../../TerrArchitecture/docs/research/STATION_BUILD_SAVE_CAUSALITY_20261112.md`
und die dort genannte originale KubeJS-Sonde. Der Referenzbericht beschreibt den
Fehler mit unveränderten Pack-JARs; die Reparaturprüfungen bauen eine neue TeutonLib-JAR.

## Ursachen und Änderungen

- Create berechnet `bogeyLocations`, `bogeyTypes` und `upsideDownBogeys` im Client
  selbst aus Blockzuständen neu. Die alten Umleitungen konnten nur Serverplots finden.
  Die projizierten Bogeyzustände reisen nun im normalen BlockEntity-Update des Bahnhofs.
  Der Client verwendet sie bei den beiden Bogeyabfragen in `refreshAssemblyInfo`.
  Die Gleisabfrage bleibt in der Welt. Renderer, native Offsetprüfung und serverseitiger
  Klick verwenden dadurch dieselben Weltpositionen; es gibt keine zweite Abstandsregel
  im Produktionscode.
- Die Transformation bleibt eine ganzzahlige Translation zwischen gespeichertem
  Weltanker und Plotzentrum. Der erste Anker verwendet Creates Gleisnormalen statt
  eines fest angenommenen `above()`. Neue Bogeys erweitern nur einen benachbarten
  Ein-Bogey-Plot in Gleisreihenfolge; keine Zuordnung über einen dazwischenliegenden Wagen.
- Sables Serializer setzt einen gültigen Eigenschwerpunkt und einen verfügbaren
  Physikkörper voraus. Ein leerer Bahnhofsbauplot erfüllt beides absichtlich nicht.
  Ein eng begrenztes Kotlin-Mixin verwendet ausschließlich für die Serialisierung
  markierter Konstruktionen ohne Schwerpunkt das Plotzentrum als geometrischen
  Transformationsanker. Die echten Massentracker werden dabei nicht verändert.
  Für geparkte oder bereits zur Entfernung vorgemerkte Konstruktionen wird keine
  Geschwindigkeit eines nicht vorhandenen Körpers gelesen.
- Beim Laden stehen die Bahnhofsbau-Metadaten nun vor `plot.load` zur Verfügung.
  Leere Plots bleiben geparkt und erhalten endliche geometrische Grenzen. Bebaute
  Plots erhalten ihren Körper nach der Massenrekonstruktion und vor dem Hochladen
  ihrer Kollisionsdaten zurück. Native Teleport-/Geschwindigkeitsaufrufe werden für
  diese stationär wiederhergestellten Konstruktionen durch deren feste Pose ersetzt.
- Beim Abbrechen bleiben die Konstruktionsmetadaten bis zur tatsächlichen Entfernung
  erhalten. Sables verzögerte Entfernung lässt sonst einen sofortigen Speicheraufruf
  auf einen unmarkierten, masselosen oder entfernten Körper treffen.
- Übergaben prüfen alle Zielpositionen vor dem Stoppen laufender Maschinen und erneut
  nach deren Vorbereitung. Abbrechen entfernt keinen Plot vor erfolgreicher Übergabe
  aller Wagen; bereits verschobene Wagen werden bei einem Übertragungsfehler zurückgeführt.
  Vorbereitete Maschinen, die beim Abbrechen in die normale Welt zurückkehren, werden
  mit ihrem persistenten Wiederanlaufdatensatz fortgesetzt; dieser wird auch nach
  Chunk-Neuladen wieder aufgenommen.
- Abbrechen wird für bestehende Bauplots erst zugelassen, wenn auch Creates
  Bahnhof und Gleisgraph verfügbar sind. Creates nativer Exit ändert seinen
  Blockzustand bereits vor der abschließenden Graphprüfung; die frühe Prüfung
  verhindert eine vorzeitige Übergabe während des Ladens.
- Der neu verwendete Sable-Serializer wird in der bestehenden Klassenhash-Prüfung
  mitgeprüft. Produktionsänderungen sind Kotlin; Testsonden gehören nur zum Testprofil.

Keine fiktiven Blöcke, keine künstliche Masse, kein Abfangen und Verschweigen von
Speicherfehlern. Sables normale Speicherung von Chunks, BlockEntities, Attachments,
Abhängigkeiten und Nutzerdaten wird weiterverwendet. Native Bogey-, Steuerungs-,
Abstands- und Zusammenbauprüfungen bleiben in Create.

## Prüfeinrichtung

- Ziel: Minecraft 1.21.1, NeoForge 21.1.251, Create 6.0.10, Sable 2.0.6,
  Create Train Functionality 0.1.47, TeutonLib 1.6.0.
- `python3 tools/verify_station_build.py`: neue Serverwelten mit aktivierter Funktion
  und deaktivierter Kontrolle, echte Speicheraufrufe und reguläre Neustarts.
- `python3 tools/verify_station_preview.py`: neuer dedizierter Server und eigener
  grafischer Client, normale Client-Use-Item-Pakete, Screenshots und Messung der
  tatsächlich vom `StationRenderer` aufgerufenen nativen Zulässigkeitsprüfung.
- Jede neue Sonde friert die geprüfte Haupt-JAR in ihrem Berichtsverzeichnis ein.
  Keine Nutzerwelt, kein normales Clientprofil und keine fremde Mod-JAR werden verändert.

## Ergebnisse

- `./gradlew build --offline --console=plain`: bestanden; **174 Tests**, keine
  Fehler, keine übersprungenen Tests. Kotlin-Quellprüfung und Paketprüfungen bestanden.
- `build/reports/station-build/c94030bf2658/result.json`: bestanden. Echte Speicherung
  vor erster Platzierung, mit Fracht und laufendem Lager sowie nach Entfernen des
  letzten Blocks; vollständiger Neustart mit denselben UUIDs. Der Neustart erfolgt
  zusätzlich mit `hybridByDefault=false`: Bestehende Plots bleiben speicherbar,
  neue Platzierung weckt den echten Körper, Abbrechen erhält Fracht und Lager samt
  Glasnutzlast. Eine weitere Welt prüft neue native Sitzungen bei deaktivierter Funktion.
- Die Bahnhofssonde prüft mehrere Bogeys auf zwei Wagen, fehlende Steuerungen,
  vollständige Kisten-NBT nach Ablehnung, blockierten Zusammenbau und blockiertes
  Abbrechen, unverändert laufende Lager bei blockierter Übergabe, Wiederanlauf nach
  nativer Ablehnung sowie erfolgreichen nativen Zusammenbau mit Fracht und Nutzlast.
- `build/reports/station-preview/694ec8a1100b/result.json`: bestanden; **28 reale
  Clientaufnahmen** für Ost, West, Süd und Nord. Jeweils leer, erster/zweiter/dritter
  Bogey, Größenwechsel, Entfernung und Aufwärtsblick. Weltpositionen und Blockzustände
  des Clients stimmen mit den tatsächlichen Plotblöcken auf dem Server überein.
  Die native Zulässigkeitsprüfung des tatsächlichen `StationRenderer` wurde für
  sämtliche dargestellten Offsets aufgezeichnet und überprüft.
- Screenshots: `run/smoke/station-preview/694ec8a1100b/client/screenshots/`.
  Insbesondere `station-0-third.png` zeigt drei tatsächlich platzierte Bogeys
  einschließlich Größenwechsel und die passenden freien blauen Gleispositionen.
  Die Bilder für Ost sowie beide Z-Richtungen wurden zusätzlich visuell geöffnet.
- Beide Prüfungen verwenden dieselbe Haupt-JAR mit SHA-256
  `a111dc334fb51c903f825cb563d519c6f732e3ac368adbf024a41a2e3a958575`.
  Versionen stehen in den jeweiligen Server-/Clientlogs. Alle erfolgreichen
  Serverläufe wurden mit `stop` beendet; der Client wurde regulär geschlossen.

## Während der Entwicklung gefundene zusätzliche Übergänge

Die Prüfungen fanden neben dem ursprünglichen Null-Schwerpunkt-Fehler auch die
zu späte Wiederherstellung der Metadaten, einen Kollisionsupload vor Wiederanlage
des Körpers und das Speichern unmittelbar nach vorgemerkter Entfernung. Diese
Fälle wurden in der Reparatur berücksichtigt. Frühe Versuche mit parallel neu
gebauter Haupt-JAR wurden durch nachgeladene, währenddessen geänderte Klassen
ungültig und zählen nicht als Abnahme.

## Grenzen

Die vier Richtungen wurden auf geraden Create-Standardgleisen mit den nativen
Standardbogeys geprüft. Diese Bogeys unterstützen keine umgedrehte Variante;
die Aufwärtsblickprobe bestätigt deren native Beschränkung. Eine tatsächlich
invertierte Variante eines anderen Bogey-/Gleistyps wurde nicht gespielt.

Die Tests verwenden die lokalen Originalabhängigkeiten des Entwicklungsprofils,
nicht das gesamte 406-Mod-Pack. Die zusätzliche breite Radar-/Portalregression wird getrennt ausgewiesen:
`build/reports/hybrid-baseline/packaged.json` besteht mit der SHA-512-geprüften
unveränderten Pack-JAR; `build/reports/hybrid-current-only/packaged.json` besteht
mit der reparierten JAR, jeweils einschließlich vollständigem Neustart.
Ein erster kombinierter Lauf scheiterte beim Radar-Wiederanlauf nach Rücktransfer
(`build/reports/hybrid-trains/failed-radar-20261112/`). Das ist kein bestandener Test
und keine bewiesene, bereits vorhandene Ursache. Die isolierten Vergleichsläufe
reproduzierten diesen Fehler nicht. Ein weiterer kombinierter Lauf deckte außerdem die zu frühe Abbruchprüfung beim
Graphladen auf; der Übergang wurde im Produktionscode abgesichert und die Sonde
wartet auf den Graphen (`build/reports/hybrid-trains/failed-graph-reload-20261112/`).

Der abschließende kombinierte Lauf **besteht vollständig**, einschließlich
Bahnhofs- und Hybrid-Neustart: `build/reports/hybrid-trains/packaged.json`.
Geprüft sind dabei auch Radar-Wiederanlauf, Portal-Hin-/Rücktransfer und
Rückabwicklung, mechanische und Swivel-Nutzlasten, Kanonenmunition und reale
Schüsse, Schlauchkopfidentität und native Fluidförderung. Der vorherige einmalige
Radarfehllauf bleibt dokumentiert; die Reparatur behauptet keine Erklärung oder
Beseitigung einer möglichen sporadischen Radar-/Portalstörung.
Nicht jede optionale Unterkonstruktion und kein beliebiger externer Listenerfehler
wurde in die Bahnhofstests injiziert. Die ausgeführten Frachttests umfassen native
Ablehnung und blockierte Übergabe sowie erfolgreiche Übergabe und Abbrechen.
