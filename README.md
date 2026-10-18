# TeutonLib

TeutonStudio-Bibliothek für **Minecraft 1.21.1 / NeoForge 21.1.234 / Java 21**.
KotlinForForge **5.11.0** ist erforderlich. TeutonLib muss auf Server und Clients installiert sein.

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
| Sable | 2.0.5 |
| Create Simulated | 1.3.2, enthalten im Aeronautics-Bundle 1.3.2 |

Ohne diese Mods, mit einem unvollständigen Satz oder mit anderen Versionen startet TeutonLib
weiterhin, lässt das Modul aber inaktiv. Log und Statusbefehl nennen die fehlenden oder
nicht unterstützten Versionen. Andere Mods können eigene Abhängigkeitsanforderungen haben.

**Die separate CSO-JAR muss entfernt werden.** Gleichzeitiges Laden der Mod-ID `cso` wird
verhindert, weil TeutonLib dieselben Eingriffe bereits enthält. Fremdmods werden nicht eingebettet.

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
sind nötig. Rezept: **Display Link + Enderperle**, formlos. Standard: **256 Blöcke**
und **64 aktive Links pro Zielnetz**, konfigurierbar unter `[compat.peripheralLink]`
mit `range` und `maxLinksPerNetwork`.

Entfernte Geräte erscheinen automatisch in `peripheral.find()` und auf Pultcomputern
zusätzlich in `peripherals.find()`. Persistente Linknamen, vollständige CC-Methoden,
Inventar-/Flüssigkeitstransfers, Ereignisse und Laufwerks-Mounts werden unterstützt.
Sable-Verbindungen verwenden Weltpositionen einschließlich Bewegung und Drehung.

Bedienung, API, Kompatibilitätsgrenze für eigene Betriebssysteme und Testbefehle:
[Peripheral-Link-Dokumentation](docs/peripheral-link.md).

## Config und Befehle

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

## Entwicklung

Die originalen lokalen Pack-Binaries aus CreateSableOptimization unter `dependencies/compile/`
und `dependencies/runtime/` bereitstellen. Diese JARs sind absichtlich nicht versioniert.

`compile/` benötigt Create, Sable, die aus dem Aeronautics-Bundle extrahierte Simulated-JAR,
KotlinForForge sowie die API-Abhängigkeiten des vorhandenen CSO-Compile-Satzes (unter anderem
Flywheel, Ponder, Registrate und Sables eingebettete Abhängigkeiten).
`runtime/` enthält die unveränderten Create-, Sable-, Aeronautics-Bundle-, KotlinForForge- und JEI-JARs.
Die normale Testumgebung (`./gradlew runClient` bzw. `runServer`, Profil `all`) lädt außerdem
CC:Tweaked 1.120.2 aus `dependencies/compile/` und JEI 19.39.0.372 aus `dependencies/runtime/`.
Ein zusätzlicher `-PperipheralRuntime=cc`-Parameter ist dafür nicht nötig.
Simulated zur Laufzeit ausschließlich über das Bundle laden, um doppelte Mod-IDs zu vermeiden.
Für die Motor-Energieprüfung zusätzlich die originalen JARs von Create New Age 1.1.7c und
Flux Networks 8.0.0 in `dependencies/runtime/` bereitstellen. Für die Kompilierung der
Energieintegration werden diese beiden Mods nicht benötigt.

```bash
./gradlew build
./gradlew runClient
./gradlew runServer
```

Für Startprüfungen stehen `-PcsoRuntime=none` (nur KotlinForForge),
`-PcsoRuntime=create` (Create und KotlinForForge) und `-PcsoRuntime=all` (Standard)
zur Verfügung. Diese Profile beeinflussen nur lokale Entwicklungsstarts.
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

All Rights Reserved. Copyright (c) 2026 TeutonStudio.

## CC:Tweaked: Quellen und Kanäle

Gemeinsame Computerreiter, persistente DbW-Signale und modemgestützte Kanalabfragen: [Deutsch](docs/computer-channels.md) · [English](docs/computer-channels.en.md).
