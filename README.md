# TeutonLib

TeutonStudio-Bibliothek für **Minecraft 1.21.1 / NeoForge 21.1.234 / Java 21**.
KotlinForForge **5.11.0** ist erforderlich. TeutonLib muss auf Server und Clients installiert sein.

## Neu in 1.5.15: Schlauchkupplung am Item Drain

Creates **Item Drain** nimmt den getragenen Schlauchrollenkopf per
Schraubenschlüssel-Rechtsklick auf. Die verriegelte Darstellung folgt der
Modellvorlage; Drain-Tank und normale Item-Verarbeitung bleiben erhalten.
Der getragene Kopf blockiert das Anklicken des Drains nicht mehr. Beim Abwählen
des Schraubenschlüssels endet das Tragen, auch beim Wechsel auf einen anderen
Hotbar-Slot. Der zusätzliche Kupplungsblock samt Item und Rezept entfällt.
Die RunClient-Umgebung lädt Train Functionality und Sable für Hybridzug- und
Umbaumodus-Tests. [Release-Notizen](docs/release-1.5.15.md).

## Neu in 1.5.14: Hybridzüge

Züge verwenden standardmäßig eine spezielle Create-Wagen-Contraption mit festem
Aufbau und laufender Sable-Simulation. Blockzustände, Maschinen, Inventare und
Unter-Contraptions bleiben aktiv. `/teutonlib train edit` schaltet einen
angehaltenen Zug zum Umbauen auf bearbeitbare Sable-Blöcke; `/teutonlib train run`
sperrt den Aufbau wieder und gibt die Fahrt frei. Beide Befehle gelten für den
gesamten Zug. Die Portalbrücke übernimmt den Modus und startet laufende Lager
nach der Zielinitialisierung wieder. Create-Radare erhalten einen eigenen
gespeicherten Contraption-Typ.

Der Fahrmodus behält einen Sable-Simulationsplot einschließlich Darstellung und
Physikanbindung im Hintergrund. Er ersetzt Sable während der Fahrt noch nicht
vollständig. [Bedienung, Konfiguration und technische Grenzen](docs/hybrid-trains.md).

## Neu in 1.5.13: New-Age-Motordrehzahl auf Sable

New-Age-Motoren bauen ihr Create-Rotationsnetz nach dem Zusammenbau auf Sable
wieder auf. Drehzahl- und Drehrichtungswechsel verwenden die tatsächliche
kinetische Drehzahl, damit die angeschlossenen Wellen weiter angetrieben werden.
Die Korrektur gilt für grundlegende, fortgeschrittene und verstärkte Motoren mit
**Create New Age 1.1.7c**, **Create 6.0.10** und **Sable 2.0.6**.
Die Mods bleiben optional. [Details und Prüfung](docs/release-1.5.13.md).

## Neu in 1.5.12

Der Entity-Schlauchkopf ist in der Server-Config unter
`[compat.hosePulley]` mit `entityHeadMode = "ALWAYS"`, `"SABLE_ONLY"` oder `"NEVER"`
einstellbar. Standard ist `ALWAYS`. Änderungen wirken während des Spiels für alle
Clients. Ohne Entity-Kopf übernimmt wieder der normale Create-Schlauch einschließlich
seiner Flüssigkeitsaufnahme. [Config und Verhalten](docs/sable-hose-pulley.md).

Sable-Züge an Immersive-Portals-Übergängen erhalten außerdem die Beleuchtung der
jeweils sichtbaren Welt. Wagenverbindungen werden auf beiden Portalseiten passend
projiziert und an der Portalfläche geschnitten. Die Clientprüfung berücksichtigt
auch Sodium. [Portal-Darstellung und Prüfungen](docs/train-portal-compatibility.md).

## Neu in 1.5.11

Physische Schlauchrollenköpfe sind jetzt als feuerfeste Entities registriert.
Sie entzünden sich in Lava und Feuer nicht mehr. Die Flüssigkeitsaufnahme und
-ausgabe verwenden die tatsächliche Kopfposition, auch auf Sable-Zügen und bei
seitlich ausgelenktem Schlauch. Kollision und Schlauchbindung bleiben erhalten.

## Physische Sable-Schlauchspitze

Creates **Item Drain** (`create:item_drain`) nimmt einen getragenen
Entity-Kopf per Schraubenschlüssel-Rechtsklick auf und verbindet ihn mit fünf
Rohranschlüssen. Eine Pumpe versorgt beide Seiten in beliebiger Flussrichtung;
der Durchfluss wird halbiert, der Pumpendruck bleibt erhalten. Ein gespannter
Schlauch zieht Sable-Konstruktionen zusammen und löst die Kupplung bei Überlast.
Der Kopf sitzt nach der Modellvorlage im Drain; dessen eigener Tank und normale
Item-Verarbeitung bleiben erhalten. Der zusätzliche Kupplungsblock entfällt.
[Bedienung und Zugkonfiguration](docs/sable-hose-pulley.md#schlauchkupplung).

Mit **Create 6.0.10 und Sable 2.0.6** übernimmt TeutonLib die Funktionen von
SableSchlauch: eine persistente Schlauchspitze mit Gravitation und Kollision,
Tragen und Werfen per Schraubenschlüssel, Mitfahren bei eingezogenem Schlauch
sowie eine Begrenzung an Hindernissen und am Weltboden. Sable-Konstruktionen
verwenden einen projizierten Weltanker. Die separate SableSchlauch-JAR muss
entfernt werden. [Bedienung, Funktionsgrenzen und Tests](docs/sable-hose-pulley.md).

## Neu in 1.5.10: Mekanism-Anschlussmodelle auf Sable-Zügen

Mit **Mekanism 10.7.19**, **Create Aeronautics: Mekanism Compatibility 0.1.21**,
**Sable 2.0.6** und **IP Aeronautics 2.6.4** korrigiert TeutonLib die
Empfängerprüfung für Rohraktualisierungen. Änderungen per Konfigurator verwenden
auf Sable-Konstruktionen die feste Plot-Blockadresse, damit Ziehen-/Drücken-Modelle
auch während der Fahrt am richtigen Rohr aktualisiert werden.

`python3 tools/verify_mekanism_models.py` prüft mit separatem Server und grafischem
Sodium-Client Weltrohre, Zusammenbau, Konfiguratoränderung im Stand und während der
Fahrt. Die Prüfung vergleicht Server-/Clientmodi, Modelldaten, tatsächlich kompilierte
Geometrie und die direkt adressierten Moduspakete. Ergebnisse und Screenshots liegen
unter `build/reports/mekanism-models/` beziehungsweise der dort referenzierten
Testinstanz in `run/smoke/mekanism-models/`. Persönliche Spielstände werden nicht geöffnet.
Die opt-in-Testmod und die originalen optionalen Mod-JARs werden nicht mit ausgeliefert.

## Neu in 1.5.9

Version 1.5.9 erhält beim Portal-Seitenwechsel die Bindung stehender Mitfahrer an
den übertragenen Sable-Wagen. Die beidseitige Darstellung aus 1.5.8 ist enthalten;
Server und Clients benötigen die aktuelle JAR. Siehe
[Release-Notizen](docs/release-1.5.9.md) und
[Portal-Integration und Prüfungen](docs/train-portal-compatibility.md).

## Neu in 1.5.8

Version 1.5.8 zeichnet baubare Züge während der Portalberührung auf beiden Seiten
der Portalfläche. Dafür verwendet die Brücke die vorhandenen IP-Straddling-Meshes
und hält deren Darstellung auch bei Stillstand, Umkehr und beim Seitenwechsel
aufrecht. Es entsteht kein zweiter CTF-Aufbau. Die Erkennung arbeitet mit einer
geometrischen Hüllprüfung und zwischengespeicherten lokalen Portalsuchen.
Sable bleibt optional; falls installiert, ist jetzt **mindestens 2.0.6** erforderlich.
Die private Zug-Integration ist für **Sable 2.0.6** verifiziert.
[Release-Notizen](docs/release-1.5.8.md) und
[Funktionsweise, Kosten und Testgrenzen](docs/train-portal-compatibility.md).

## Neu in 1.5.7

Version 1.5.7 behebt den Null-Kontext beim Entkoppeln mit Steam ’n’ Rails und
ersetzt die Durchfahrtssperre aus 1.5.6 durch gemeinsame Portalübertragungen von
Create-Wagen und CTF-/Sable-Aufbau. Geprüfte Kombination: CTF 0.1.47,
IP Aeronautics 2.6.4, Create 6.0.10 und Sable 2.0.5/2.0.6.
[Umfang, Grenzen und Tests](docs/train-portal-compatibility.md).

## Neu in 1.5.5

Version 1.5.5 ergänzt einen Adapter für **FluidLogistics 1.3.1 / TFMG 1.2.0** mit
**Create 6.0.10**. Er behebt die kollidierenden Rohrnetz-Mixins, erhält die erweiterte
Pumpenreichweite und berücksichtigt FluidLogistics-Pumpen in TFMGs Suchlauf.
Der Adapter funktioniert auch ohne Factory Logistics.
[Details und Tests](docs/fluid-propagation-compatibility.md).

## Neu in 1.5.4

Version 1.5.4 enthält einen versionsgebundenen Adapter für **FluidLogistics 1.3.1** und
**Factory Logistics / Factory Abstractions 1.4.9** mit **Create 6.0.10**. Er ersetzt die
konfliktbehafteten Queue-/Bestands-Mixins und verwendet für Ressourcenvergleiche und
Mengenänderungen den gemeinsamen Factory-Logistics-Speicher.
[Geltungsbereich, Prüfsummen und Tests](docs/logistics-compatibility.md).

## Neu in 1.5.3

Version 1.5.3 ergänzt eng begrenzte Android-/Zalith-Kompatibilität für Ponder/Catnip,
PowerGrid, Essential und Immersive Melodies. Hinzu kommen verifizierte Reparaturen für
bekannte fehlerhafte Ressourcen sowie Diagnoseausgaben für Paketmetadaten und optionale
Native-Pfade. [Vollständige Release-Notizen](docs/release-1.5.3.md).

## Neu in 1.5.0

Die öffentlichen Computer-APIs umfassen jetzt generische Peripheral-Topologien und
Attachments, Workspace-Deskriptoren, persistente Gruppen und Signalausgänge sowie einen
Kernel für eingebettete CC:Tweaked-Computer. Sable-Funk, Modemreichweite und `gps.locate()`
verwenden sichtbare Weltkoordinaten; Simulated-Docking-Zustände stehen über eine dependency-sichere
Fassade bereit. [Vollständige Release-Notizen](docs/release-1.5.0.md).

## PC-UI als Vorlage

Das Release **1.3.0** enthält eine kompilierte, kopierbare
[PC-UI-Integrationsvorlage](examples/computer-ui/README.md) für CC Aeroworks und
andere Addons: Terminal, gemeinsame Quellen-/Kanalreiter, Fokus und Resize.
`./gradlew releaseBundle --offline` erzeugt das ZIP mit Mod-JAR, Quellen und
Dokumentation unter `build/distributions/`.
[Releaseumfang und Voraussetzungen](docs/release-1.3.0.md).

Die automatische UI-Erweiterung lässt sich clientseitig in `config/teutonlib-client.toml`
unter `[computerUi]` mit `enabled = false` abschalten (Standard: `true`).

## Android-/Zalith-Kompatibilität

TeutonLib erkennt Android über eindeutige JVM-Merkmale und aktiviert dort eng versionierte
Kompatibilitätspfade. `Linux` und `aarch64` allein lösen sie nicht aus. Für Diagnose und
Launcher-Sonderfälle gilt bereits während der Mixin-Auswahl:

```text
-Dteutonlib.mobile.mode=auto   # Standard
-Dteutonlib.mobile.mode=on     # Erkennung erzwingen
-Dteutonlib.mobile.mode=off    # alle mobilen Eingriffe abschalten
```

Ponder/Catnip prüft nacheinander den NeoForge-Puffer, D24S8-Texturen mit getrennter und
gemeinsamer Anbindung sowie einen D24S8-Renderbuffer. Jeder Kandidat muss zusätzlich zu
`GL_FRAMEBUFFER_COMPLETE` einen echten Stencil-/Pixeltest bestehen. Falls alle Varianten
scheitern, kann der experimentelle Zwei-Textur-Compositor mit
`-Dteutonlib.mobile.render.colorMask=true` zugeschaltet werden.

Für die exakt geprüften Releases nutzt PowerGrid 0.5.5.1 seinen Java-Solver, Essential
1.3.10.9 einen sauber getrennten Discord-IPC-Adapter und Immersive Melodies 0.6.4 keine
Hardware-MIDI-Erkennung. MIDI-Dateien, Instrumente und Minecraft-Sounds bleiben erhalten.
C2ME Native Math wird nicht erzwungen. Visual Recipe Editor 1.2.1 führt seinen eingebauten
Entwickler-Workspace-Backup nicht aus.

Verifizierte Ressourcenregeln gelten unabhängig von Android und nur bei passender Version
und SHA-256: Create Radar 0.4.9.2, EnderIO 8.2.10-beta ohne Athena, NTGL 3.1.5 und
Design 'n' Decor 2.2b. Abweichende Dateien und spätere Versionen werden nicht verändert.

## Optionales Create/Sable-Modul

TeutonLib übernimmt die vollständige Zahnrad-Brückenlogik von Create Sable Optimization:
Sichtbar ineinandergreifende Zahnräder können über zwei starr gekoppelte Sable-/Simulated-
Sublevels hinweg Leistung übertragen. Beide Docking Connectors müssen verriegelt sein.

Unterstützt werden kleine und große Create-Zahnräder einschließlich ihrer Übersetzungen
und der rechtwinkligen Großzahnrad-Geometrie. Create berechnet Drehzahl, Drehrichtung,
Stress, Kreise und Überlastung. Wellen, Riemen, Pumpen und andere kinetische Blöcke werden
nicht als zusätzliche Brücken registriert. Die Clientintegration korrigiert den Zahnphasenversatz.

Die Integration ist zunächst für folgende Mod-Versionen freigeschaltet:

| Mod | Version |
| --- | --- |
| Create | 6.0.10 |
| Sable | 2.0.6 |
| Create Simulated | 1.3.2, enthalten im Aeronautics-Bundle 1.3.2 |

Ohne diese Mods oder mit einem unvollständigen Satz bleibt das Modul inaktiv.
Sable ist optional; eine installierte Version unter 2.0.6 wird durch die
Abhängigkeitsprüfung abgelehnt. Andere ungeprüfte Versionen lassen das Modul
inaktiv. Log und Statusbefehl nennen die fehlenden oder nicht unterstützten Versionen.
Andere Mods können eigene Abhängigkeitsanforderungen haben.

**Die separate CSO-JAR muss entfernt werden.** Gleichzeitiges Laden der Mod-ID `cso` wird
verhindert, weil TeutonLib dieselben Eingriffe bereits enthält. Fremdmods werden nicht eingebettet.

## Analoge Docking Connector

Mit `compat.simulated.enableAnalogDocking = true` (Standard) steuert die tatsächliche
Redstone-Stärke den bestehenden Anschluss: 0–5 eingefahren, 6–10 ein halber Block,
11–15 ein Block. Gegenstellen handeln vor dem Andocken die kürzere Länge aus;
HALF benötigt zwei, FULL drei Blöcke Abstand zwischen den Basismittelpunkten.
Verriegelung und die bestehenden Transfers verwenden weiterhin Simulated/Sable.
[Geometrie, Konfiguration und Prüfungen](docs/analog-docking.md).

## Energieeingang an New-Age-Motorerweiterungen

Grundlegende und fortgeschrittene Motorerweiterungen akzeptieren FE-Energie von Flux Points,
Energiekabeln und anderen FE-Quellen auf allen zugänglichen Seiten. Die Verbindung lautet
**FE-Quelle → Motorerweiterung → Motor**. Die Erweiterung muss direkt hinter dem Motor sitzen;
beide Blöcke müssen in dieselbe Richtung zeigen, wie für die normale New-Age-Stressverstärkung.
Unterstützt werden grundlegende, fortgeschrittene und verstärkte Motoren.

In der unten beschriebenen `teutonlib-server.toml` lässt sich die Funktion abschalten:

```toml
[compat.createNewAge]
    motorExtensionEnergyTransfer = true
```

Der Standard ist `true`. Änderungen gelten beim Laden beziehungsweise Neuladen der
Server-Konfiguration, auch für bereits angeschlossene Quellen. Bei aktiviertem Datei-Wächter
ist kein Neustart nötig. Die ursprüngliche Stressverstärkung bleibt unabhängig davon erhalten.

Die Energie landet unmittelbar im vorhandenen Motorspeicher; die Erweiterung hat keinen
eigenen Puffer und erlaubt keine Entnahme. Ohne passenden, geladenen Motor wird keine Energie
angenommen. Erweiterungsketten und Verbindungen über Sable-Sublevel sind nicht vorgesehen.
Create New Age und Flux Networks sind optional; die Weiterleitung benötigt nur Create New Age
und eine FE-Quelle. Testbasis: Create New Age **1.1.7c**, Flux Networks **8.0.0**.

## Peripheral Link für CC:Tweaked

Mit **CC:Tweaked und Create** ergänzt TeutonLib einen drahtlosen Peripheral Link.
CC Aeroworks ist optional; wenn installiert, unterstützt der Link zusätzlich die
normalen und erweiterten eingebauten Computer aller Mitglieder einer Pultreihe.

Mit dem Item zuerst den Computer beziehungsweise Pultanker auswählen und den Link
anschließend am Gerät platzieren. Kein Empfängerblock und kein Verbindungsprogramm
sind nötig. Formlose Rezepte: **Anzeige-Link + Enderperle** oder direkt
**Messingrahmen + Enderperle + Sender**. Beide benötigen dieselben Materialien.
Standard: **256 Blöcke**
und **64 aktive Links pro Zielnetz**, konfigurierbar unter `[compat.peripheralLink]`
mit `range` und `maxLinksPerNetwork`.

Entfernte Geräte erscheinen automatisch in `peripheral.find()` und auf Pultcomputern
zusätzlich in `peripherals.find()`. Persistente Linknamen, vollständige CC-Methoden,
Inventar-/Flüssigkeitstransfers, Ereignisse und Laufwerks-Mounts werden unterstützt.
Sable-Verbindungen verwenden Weltpositionen einschließlich Bewegung und Drehung.
Der Directional Gearshift aus Create Simulated ist direkt und über Peripheral Links als
`directional_gearshift` mit `setOutput("left"|"right", boolean)` steuerbar.

Bedienung, API, Kompatibilitätsgrenze für eigene Betriebssysteme und Testbefehle:
[Peripheral-Link-Dokumentation](docs/peripheral-link.md).

Für Heli-Steuerungen ergänzt TeutonLib direkt, im Modemnetz und über Peripheral Links am
Directional Gyroscopic Propeller Bearing `adjustTilt` und `setTargetDirection`; das gilt
auch für den von Simulated Addition abgeleiteten Block. Der Gimbalsensor zeigt zusätzlich
den Y-Winkel in der Ingenieursbrille und bietet Pitch, Yaw und Roll einzeln sowie über
`getRotation()` an.
Die bestehenden zweiachsigen `getAngles()`- und `getAnglesRad()`-Ergebnisse bleiben
kompatibel. Signaturen, Koordinatensystem, 20-Tick-Timeout und
Lua-Beispiele stehen in der
[Heli-Peripheral-Dokumentation](docs/heli-peripherals.md).

Für portable Computergrafik liefern `teutonlib.svg` und `teutonlib.font` SVG-Rasterung
und eine Bitmap-Schrift für Terminals, Monitore und optionale Pixel-Displays. Beide
Module funktionieren ohne CCVisual und ohne CC-Aeroworks. API und Beispiele stehen in
der [Computergrafik-Dokumentation](docs/computer-graphics.md).

## Config und Befehle

Unter **Mods → TeutonLib → Config** öffnet sich der eingebaute NeoForge-Editor.
**Configured wird dafür nicht benötigt.** Der Editor zeigt die Client-Einstellungen und
bei geladener Einzelspielerwelt auch deren Server-Einstellungen. Die Beschriftungen und
Hilfetexte sind auf Deutsch und Englisch verfügbar. Änderungen werden beim Verlassen der
jeweiligen Config-Seite gespeichert; die Dateiüberwachung übernimmt das Neuladen.

Am Hauptmenü sind Server-Einstellungen nicht geladen. Auf einem entfernten Server und
in einer für LAN geöffneten Welt sperrt der NeoForge-Editor die Server-Einstellungen.
Auf Dedicated Servern die Datei auf dem Server bearbeiten oder für das CSO-Modul die
unten aufgeführten OP-Befehle verwenden. Client-Einstellungen bleiben lokal bearbeitbar.


Die weltbezogene Datei wird automatisch erzeugt:

- Einzelspieler: `saves/<Welt>/serverconfig/teutonlib-server.toml`
- Dedicated Server: `<Weltordner>/serverconfig/teutonlib-server.toml`

```toml
[modules.createSableOptimization]
    enabled = true
```

`true` ist der Standard. Der Server gibt den wirksamen Zustand vor und synchronisiert ihn
beim Beitritt und bei Änderungen an alle Clients. Eine fehlende Voraussetzung verhindert die
Aktivierung unabhängig vom gespeicherten Wert.

| Befehl | Wirkung | Berechtigung |
| --- | --- | --- |
| `/teutonlib cso status` | Config-Wert, wirksamer Zustand und fehlende Voraussetzungen | alle |
| `/teutonlib cso enable` | Einschalten und Config speichern | Stufe 2 / OP |
| `/teutonlib cso disable` | Ausschalten und Config speichern | Stufe 2 / OP |

Im Einzelspieler sind für die Änderungsbefehle Cheats erforderlich. Alternativ die Config-Datei
bearbeiten. Änderungen werden während des Spiels übernommen; dafür muss NeoForges
Config-Dateiüberwachung aktiviert sein (Standardeinstellung). Die Befehle wirken auch ohne
Dateiüberwachung sofort.

Beim Ausschalten werden virtuelle Verbindungen entfernt und die betroffenen Create-Netze
neu berechnet. Beim Einschalten werden bereits geladene Zahnräder wieder berücksichtigt;
verriegelte Docking-Verbindungen werden innerhalb des regulären Prüfzyklus von zehn Ticks
wieder aufgebaut. Ein Welt- oder Spielneustart ist nicht erforderlich.

Für eine neue Welt wird eine vorhandene globale `config/teutonlib-server.toml` als Vorlage
übernommen. Danach sind die Einstellungen der Welten voneinander unabhängig.

## Zugaufbauten an Portalen

Baubare Züge von Create: Train Functionality 0.1.47 können durch passende
Portalgleise von Immersive Portals: Aeronautics 2.6.4 fahren (Create 6.0.10,
Sable 2.0.6). Wagen, vorhandener Aufbau, Inventare und gespeicherte Zuordnung
werden gemeinsam übernommen. Ab 1.5.8 wird der vorhandene Aufbau während seiner
Berührung mit der Portalfläche auf beiden Seiten geschnitten dargestellt. Die
zusätzliche sichtbare Zeichnung kostet Renderarbeit; die Brücke verwendet bereits
vorhandene Meshes und vermeidet Blockscans oder einen zweiten CTF-Aufbau.
Unabhängige Schiffstransfers von Zugaufbauten bleiben gesperrt; gewöhnliche Schiffe
bleiben übertragbar. Die Brücke ist auf Server und Clients erforderlich.
[Diagnose, Grenzen und Regressionstests](docs/train-portal-compatibility.md).

## Entwicklung

Die Mod, Tests und Integrationsbeispiele sind überwiegend in Kotlin geschrieben. Statische
oder dependency-isolierte Mixins bleiben bewusst kleine Java-Klassen.
Auch die Gradle-Builddateien verwenden Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`).
Java 21 ist die Ziel-JVM. Lua bleibt für die CC:Tweaked-Laufzeit erforderlich;
die externen Entwicklungs-Prüfskripte unter `tools/` verwenden Python.


Die originalen lokalen Pack-Binaries aus CreateSableOptimization unter `dependencies/compile/`
und `dependencies/runtime/` bereitstellen. Diese JARs sind absichtlich nicht versioniert.

`compile/` benötigt Create, die aus dem Aeronautics-Bundle extrahierte Simulated-JAR,
KotlinForForge sowie die API-Abhängigkeiten des vorhandenen CSO-Compile-Satzes (unter anderem
Flywheel, Ponder, Registrate und Sables eingebettete Abhängigkeiten). Für die mobilen
Kompatibilitätstests kommen die oben genannten unveränderten Release-JARs einschließlich
des äußeren Essential-JARs und C2ME hinzu. Gradle extrahiert Essentials eingebettete
Socket-API ausschließlich in `build/generated/compatibility/`; sie wird nicht ausgeliefert.
`runtime/` enthält die unveränderten Create-, Aeronautics-Bundle-, KotlinForForge- und JEI-JARs.
Die äußere Sable-JAR wird für Kompilierung und Profil `all` aus
`dependencies/verification/sable-neoforge-1.21.1-2.0.6.jar` geladen. Ältere äußere
Sable-JARs in `compile/` oder `runtime/` werden ausgeschlossen.
Die normale Testumgebung (`./gradlew runClient` bzw. `runServer`, Profil `all`) lädt außerdem
CC:Tweaked 1.120.2, Drive by Wire 0.3.0 und Create: Train Functionality 0.1.47 aus
`dependencies/compile/` sowie JEI 19.39.0.372,
Create New Age 1.1.7c und Flux Networks 8.0.0 aus `dependencies/runtime/`.
Zusätzliche Testparameter sind dafür nicht nötig. Mit Sable 2.0.6 und Train Functionality
können Hybridzüge direkt getestet werden: `/teutonlib train edit` aktiviert den
Sable-Umbaumodus am angehaltenen Zug, `/teutonlib train run` den Fahrmodus und
`/teutonlib train status` zeigt den aktuellen Modus ([Details](docs/hybrid-trains.md)).
Simulated zur Laufzeit ausschließlich über das Bundle laden, um doppelte Mod-IDs zu vermeiden.
Die originalen JARs von Create New Age und Flux Networks müssen dafür in `dependencies/runtime/`
liegen; sie werden im Standardprofil automatisch geladen. Für die Kompilierung der
Energieintegration werden diese beiden Mods nicht benötigt.

```bash
./gradlew build
./gradlew runClient
./gradlew runServer
```

Für Startprüfungen stehen `-PcsoRuntime=none` (nur KotlinForForge),
`-PcsoRuntime=create` (Create und KotlinForForge) und `-PcsoRuntime=all` (Standard)
zur Verfügung. Diese Profile beeinflussen nur lokale Entwicklungsstarts.
Den Config-Editor und das Umschalten der Computerreiter im echten Client prüfen:
`./gradlew runClient -PperipheralTests -PcomputerUiConfigTests --offline`.
Der Client beendet sich nach der Prüfung; das Ergebnis steht unter
`build/reports/smoke/computer-ui-config.txt`.

Die eigenständige Peripheral-Link-Oberfläche benötigt im Entwicklungsprofil CC:Tweaked und
Create. Ihr automatischer Render-, Fokus-, Scroll-, Escape- und Resize-Test läuft mit
`./gradlew runClient -PperipheralTests -PperipheralLinkUiTests -PperipheralRuntime=cc -PcsoRuntime=create --offline`;
das Ergebnis steht unter `build/reports/smoke/peripheral-link-ui.txt`.

Die JUnit-Tests prüfen zusätzlich die API-Signaturen der lokalen Compile-JARs.

Die automatischen Serverprüfungen verwenden separate Testwelten unter `run/smoke/`
und eine temporäre lokale RCON-Verbindung; jeder Server wird anschließend beendet:

```bash
python3 tools/verify_server.py none
python3 tools/verify_server.py create
python3 tools/verify_server.py all
```

Diese Prüfungen nacheinander ausführen. Sie prüfen Start, Status, wiederholtes Umschalten,
Speichern und Datei-Neuladen. Mit Create prüfen sie außerdem, dass eine gewöhnliche
Zahnradverbindung beim Abschalten weiterhin mit der erwarteten Drehzahl läuft.
Protokolle und Ergebnisse liegen unter `build/reports/smoke/`.

Die erweiterte Energieprüfung wird ausdrücklich aktiviert:

```bash
TEUTONLIB_MOTOR_TESTS=1 python3 tools/verify_server.py all
```

Sie lädt eine separate Entwicklungs-Testmod, die nicht in der TeutonLib-JAR enthalten ist.
Geprüft werden beide Erweiterungen mit allen drei Motorvarianten in sechs Ausrichtungen,
alle Eingangsseiten, FE-Buchung und Simulation, voller/fehlender/falsch ausgerichteter Motor,
Blockaustausch, Config-Neuladen an einem bereits abgefragten Eingang, Chunk-Entladen und
Neustart derselben Testwelt. Beim Flux Point wird sein tatsächlicher Erkennungs- und
Übertragungszyklus ausgeführt; der Netzwerkpuffer wird dafür mit einer bekannten Energiemenge
befüllt. Netzwerkerstellung und drahtloser Transport zwischen Plug und Point sind kein Teil
dieser Prüfung.

## In-World-Prüfung

1. Stationären Create-Antrieb und eine Maschine auf einem Sable-Sublevel über passende Zahnräder verbinden.
2. Beide Docking Connectors verriegeln; Drehrichtung, Übersetzung und Stress prüfen.
3. Bei laufender Maschine ausschalten: Die virtuelle Übertragung muss enden, gewöhnliche Create-Verbindungen bleiben bestehen.
4. Ohne Chunk-Neuladen einschalten: Die Brücke muss innerhalb von zehn Ticks wieder entstehen.
5. Entdocken, Zahnräder entfernen, Chunks entladen und die Welt wechseln; veraltete Verbindungen dürfen nicht bestehen bleiben.
6. Mit mehreren Clients wiederholen, während deaktiviertem Modul beitreten und die Config-Datei im Betrieb ändern. Clientdarstellung und Serverzustand müssen übereinstimmen.

## Lizenz

MIT. Copyright (c) 2026 TeutonStudio. Siehe [LICENSE](LICENSE).

## CC:Tweaked: Quellen und Kanäle

Gemeinsame Computerreiter, persistente DbW-Signale und modemgestützte Kanalabfragen: [Deutsch](docs/computer-channels.md) · [English](docs/computer-channels.en.md).

## Gemeinsame Addon-Workspaces (1.1.0)

`ComputerWorkspace` und `ComputerPanels.workspace(...)` erlauben vollständige Quellen-/Kanalpanels mit einem autoritativen Addon-Backend. CC-Aeroworks verwendet diese Schnittstelle für seine vorhandenen Kanalgruppen, Bindungen, DbW-Ziele und Skriptdiagnosen. Die Ladereihenfolge lautet TeutonLib vor CC-Aeroworks; die Daten bleiben im bisherigen Aeroworks-Speicher. Siehe [Integrationsvorlage](examples/computer-ui/README.md).

Geprüfte Basis: Minecraft 1.21.1, NeoForge 21.1.234, Create 6.0.10, Aeronautics 1.3.2 und CC:Tweaked 1.120.2. Die Reiter nativer CC-Computer nutzen weiterhin das Standardbackend.

## Projekt und API

[Quellcode](https://github.com/TeutonStudio/TeutonLib) · [Fehler melden](https://github.com/TeutonStudio/TeutonLib/issues) · [Downloads](https://github.com/TeutonStudio/TeutonLib/releases)

Die dokumentierten Provider-, Kontext-, Workspace- und Client-Schnittstellen sind die öffentlichen Erweiterungspunkte. Minor-Versionen ergänzen die API kompatibel; inkompatible öffentliche API-Änderungen benötigen eine neue Major-Version. Integrations- und Mixin-Implementierungsdetails sind davon ausgenommen.
