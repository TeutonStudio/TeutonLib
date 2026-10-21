# Analoge Docking Connector

TeutonLib erweitert die vorhandenen Connector von **Simulated 1.3.2** für Minecraft
1.21.1 / NeoForge, Create 6.0.10 und Sable 2.0.5. Es werden keine neuen Blöcke,
Registrierungen oder Transferbehälter eingeführt. Ohne diese unterstützten optionalen
Mods werden die beiden Analog-Mixins durch `CsoMixinPlugin` nicht angewendet.

In `serverconfig/teutonlib-server.toml`:

```toml
[compat.simulated]
enableAnalogDocking = true
```

Diese Option ist unabhängig von `modules.createSableOptimization.enabled`, das die
TeutonLib-Zahnradbrücken steuert. `false` führt beim nächsten Connector-Tick eine
Entriegelung durch und übergibt anschließend wieder an die ursprüngliche
Simulated-Steuerung. Die Option wirkt auch beim Laden alter Welten.

| Signal | Anforderung | Ausleger | Kontakt ab Basismitte |
| --- | --- | --- | --- |
| 0–5 | OFF | 0 | keine Kopplung |
| 6–10 | HALF | 0,5 Blöcke | 1 Block |
| 11–15 | FULL | 1 Block | 1,5 Blöcke |

Die Serverlogik liest `Level.getBestNeighborSignal` bei jedem Tick. `POWERED` bleibt
als bestehender Blockzustand erhalten, entscheidet aber nicht über die analoge Länge.

## Aushandlung und Geometrie

Die Suche bewertet jeden Kandidaten anhand von `min(requestedA, requestedB)`;
bereits ausgehandelte Längen fließen nicht erneut in die Berechnung ein.
OFF-Anschlüsse sind keine aktiven Kandidaten. Ohne passenden Partner fährt ein
Anschluss bis zu seiner eigenen Anforderung aus, sofern der Raum davor frei ist.

Im selben Blockraster ist ausschließlich der geometrisch passende Abstand erlaubt:
HALF/HALF und HALF/FULL benötigen zwei Blöcke zwischen den Basismittelpunkten
(drei Positionen einschließlich Basen), FULL/FULL drei Blöcke (vier Positionen).
HALF erzeugt keinen `PAIRED_DOCKING_CONNECTOR`. Ein eindeutig dem Gegenüber
gehörender FULL-Ausleger in der gemeinsamen Mittelposition wird bereits bei der
Suche erkannt und für die HALF-Aushandlung entfernt.

Über Sublevel-Grenzen werden Basispositionen und Ausrichtungen in Weltkoordinaten
verglichen. Die bestehende Magnetkraft richtet die Anschlüsse aus; verriegelt wird
erst innerhalb der ursprünglichen Simulated-Abstands- und Winkeltoleranzen.
`getTipPosition` liefert die ausgehandelte Ankerposition für beide vorhandenen
FixedConstraint-Pfade. TeutonLib teleportiert keine Sublevel.

Es wird jeweils nur der gegenseitig gewählte Kandidat berücksichtigt. Bestehende,
weiterhin passende Verbindungen haben Vorrang; ansonsten entscheiden Kontaktabstand
und eine feste Blockpositionsreihenfolge. Zwei unabhängige, begrenzte Suchläufe
ersetzen rekursive Partnersuche. Veraltete Magnetpaare dürfen keine Kraft ausüben
oder eine weitere Verbindung verriegeln.

## Übergänge und Speicherung

`requestedLength`, `effectiveLength`, die vorläufige Partnerwahl sowie der bestehende
`otherConnectorPosition` / `state` bleiben getrennt. Länge, Betriebsmodus und
Dockingzustand werden im namensgetrennten NBT-Tag `TeutonLibAnalogDocking`
gespeichert und über die normalen BlockEntity-Pakete synchronisiert. Alte NBT-Daten
bleiben lesbar; der Server berechnet Eingänge und Partner neu.

Bei einem Wechsel der effektiven Länge werden vor der Umstellung beide Seiten über
`unDock` getrennt: FixedConstraint, Flüssigkeits-, FE-, ComputerCraft- und
TeutonLib-Zahnradbrücken. Die ursprünglichen Inventarpuffer bleiben erhalten, werden
aber für weitere Eingaben gesperrt. Erst nach erneuter geometrischer Prüfung darf
die Verriegelung wieder beginnen. Auch `pairTo(force=true)` und das Wiederherstellen
beim Laden dürfen diese Prüfung im Analogmodus nicht überspringen.

Renderer und Kollisionsform verwenden bereits `getExtensionDistance`; ihre Geometrie
folgt damit derselben tatsächlichen Animation (Ziel 0, 0,5 oder 1). Der Renderer und
die Paired-Blockklasse müssen dafür nicht kopiert oder ersetzt werden.

## Prüfungen

```sh
./gradlew build --offline
TEUTONLIB_DOCKING_TESTS=1 python tools/verify_server.py all
python tools/verify_server.py none
python tools/verify_server.py create
```

Der separate Entwicklungs-Testmod wird nur mit `-PanalogDockingTests` eingebunden und
gehört nicht zur veröffentlichten JAR. Die Prüfungen umfassen Redstone-Grenzen,
alle neun Kombinationen, sechs Ausrichtungen, passende und falsche Rasterabstände,
beidseitige Item-/Flüssigkeits-/FE-Übertragung, fehlende und falsche Gegenstellen,
Signalwechsel, bereits ausgefahrene Gegenstellen, NBT und den Originalmodus.
Der Server-Test enthält außerdem echte Sable-Sublevel und prüft FixedConstraint-
Erzeugung und -Entfernung sowie Weltneustart und Chunk-Reload. Binärvertragstests
sichern die verwendeten Mixin-Ziele und die gemeinsamen Geometriepfade ab.

Eine interaktive Abnahme von bewegten Schiffen, magnetischer Annäherung unter Last,
Create-Zahnradübertragung über bewegte Docks und Client-Rendering bleibt zusätzlich
notwendig. Automatisierte Headless-Tests ersetzen diese Sicht- und Dynamikprüfung
nicht. Die Versionssperre ist absichtlich eng; eine neue Simulated-Version benötigt
eine erneute Prüfung der Mixin-Ziele und Physikpfade.

## Geprüfter Stand (21.10.2026)

- `releaseBundle` / `build`: erfolgreich; 51 JUnit-Tests, keine Fehler oder übersprungenen Tests.
- Voller Server-Test: 115 gezählte Aufbauten, darunter 108 stationäre Kombinationen,
  sechs Stationär-/Sublevel-Constraint-Aufbauten und der Originalmodus.
- Item-, Flüssigkeits-, FE-Transfer sowie ComputerCraft-Verbindung in HALF und FULL
  geprüft; beim Entriegeln sind der native Constraint und die CC-Kante entfernt.
- NBT-Wiederherstellung, Weltneustart und tatsächlicher Chunk-Reload eines HALF-Paars
  erfolgreich; gespeicherte Energie und Flüssigkeit bleiben erhalten.
- Serverstarts ohne die optionalen Mods und mit ausschließlich Create erfolgreich.
- Renderer-/Kollisions-Geometriepfade und Constraint-Anker automatisch geprüft;
  keine interaktive Client-Sichtprüfung oder Langzeitprüfung bewegter Schiffe.
- Release-Archiv geprüft: ausschließlich Hauptmod-Klassen, keine Testmods oder
  eingebetteten fremden JARs.
