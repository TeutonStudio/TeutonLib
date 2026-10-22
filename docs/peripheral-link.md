# Peripheral Link

[English documentation](peripheral-link.en.md)

Der Peripheral Link wird von **TeutonLib** registriert, sobald **CC:Tweaked und Create** installiert sind. **CC Aeroworks ist optional** und ergänzt die Unterstützung eingebauter Pultcomputer. Es gibt keinen separaten Empfängerblock und keine Avionics-Abhängigkeit.

## Bedienung

Ein Create Display Link und eine Enderperle ergeben im formlosen Rezept einen `teutonlib:peripheral_link`. Der türkisfarbene Link verwendet die Display-Link-Geometrie.

1. Mit dem Item einen normalen oder erweiterten CC-Computer auswählen. Mit CC Aeroworks kann stattdessen ein beliebiges Mitglied einer Aeroworks-Pultreihe ausgewählt werden.
2. Den Link gegen das gewünschte Gerät platzieren. Ausschließlich das Gerät auf der Befestigungsseite wird angebunden.
3. Rechtsklick auf den platzierten Link zeigt die Ziel-UUID und den aktuellen Status.

Schleichen und Benutzen löscht die Zielauswahl des gehaltenen Items. Danach kann ein neues Ziel ausgewählt werden; mit bereits ausgewähltem Ziel platziert der nächste Rechtsklick den Link. Zum Umverknüpfen einen platzierten Link abbauen und mit neuer Auswahl platzieren. Abgebaute Links geben ihre UUID und Kopplung nicht an das Drop-Item weiter. Nach erfolgreicher Platzierung wird die Zielauswahl im verbleibenden Itemstapel gelöscht, auch im Kreativmodus. Fehlgeschlagene Platzierungen erhalten die Auswahl. Pocket Computer und Turtles können nicht ausgewählt werden.

Kopplung und Platzierung prüfen auf dem Server Interaktionsreichweite, Bau-/Interaktionsberechtigung und geladene Blöcke; die Platzierung prüft zusätzlich Ziel und Funkreichweite. Die regulären NeoForge-Interaktions- und Platzierungsereignisse bleiben im normalen Spielerpfad erhalten.

## Konfiguration

In der weltbezogenen `serverconfig/teutonlib-server.toml`:

```toml
[compat.peripheralLink]
    range = 256
    maxLinksPerNetwork = 64
```

Die Reichweitengrenze ist eingeschlossen. Das Limit gilt gemeinsam für aktive Links an allen Ankern derselben Pultreihe beziehungsweise desselben Standardcomputers. Bei mehr geladenen Links werden die ersten erreichbaren Geräte in stabiler Link-UUID-Reihenfolge berücksichtigt. Änderungen gelten nach Laden/Neuladen der Serverkonfiguration.

## Standardcomputer

Im mitgelieferten CraftOS installiert ein ROM-Autorun die Ergänzung einmalig. Lokale Seiten und Kabelmodems bleiben in derselben API erreichbar:

```lua
local inventory = peripheral.find("inventory")
local sensor = peripheral.find("gimbal_sensor")
local gyro = peripheral.find("gyroscopic_propeller_bearing")

for _, name in ipairs(peripheral.getNames()) do
    print(name, peripheral.getType(name))
end
```

Ergänzt werden `getNames`, `isPresent`, `getType`, `hasType`, `getMethods`, `call`, `wrap` und `find`. Gewrappte Objekte behalten die CraftOS-Metadaten für `peripheral.getName`, `getType` und `hasType`. `find` liefert weiterhin mehrere Rückgabewerte und unterstützt den normalen Filterparameter.

Eigene Betriebssysteme, die kein CraftOS-Autorun ausführen, laden das Modul ausdrücklich:

```lua
require("teutonlib.peripheral_links").install()
```

Ein Simulated Optical Sensor hinter einem Peripheral Link erhält zusätzlich
`getLocalDirection()`. Die Methode liefert die Laser-Ausrichtung als lokalen Einheitsvektor
`{ x = ..., y = ..., z = ... }`. Auf einem Sable-Sublevel sind dies dessen Plotkoordinaten,
nicht Weltkoordinaten. Damit kann ein Programm die Weltrichtung anhand der Rotation eines
Gimbalsensors selbst berechnen. Beispiel für einen nach vorne (+Z) gerichteten Sensor:

```lua
local sensor = assert(peripheral.find("optical_sensor"))
local direction = sensor.getLocalDirection()
print(direction.x, direction.y, direction.z) -- 0  0  1
```

Die Methode steht bewusst nur über einen Peripheral Link bereit. Ein statischer Sensor
verwendet dasselbe lokale Koordinatensystem wie die Welt.

Die serverseitige API wird mit `ComputerCraftAPI.registerAPIFactory` bereitgestellt. Ihr globaler Name ist `teutonlib_links`, ihr Modulname `teutonlib.links`. Die Implementierung des ROM-Moduls ist die Referenz für eigene Betriebssysteme. Während der API-Erzeugung erfolgt kein Weltzugriff; der aktuelle Computerblock wird erst nach Aktivierung auf dem Serverthread aufgelöst.

## Eingebaute Pultcomputer

Mit CC Aeroworks werden dieselben Links in dessen bestehenden Peripheral-Graphen aufgenommen:

```lua
local sensor = peripherals.find("gimbal_sensor")
local all = peripherals.findAll("inventory")
local tree = peripherals.getTree()
```

Die vorhandenen Rückgabekonventionen bleiben erhalten: `find` gibt bei einem Treffer dessen Handle, bei mehreren Treffern eine nach Adresse indizierte Tabelle zurück; `findAll` gibt immer eine Tabelle zurück.

Der ausgewählte Pultblock bleibt der Anker. Das aktuell zuständige eingebaute Computersystem der Reihe erhält die Geräte; ohne Computer bleibt die Kopplung gespeichert und inaktiv. Einsetzen, Entfernen, Aufteilen und Zusammenführen ändern die Erreichbarkeit. Beim Ausschalten werden Bindungen geschlossen; ein Reboot registriert denselben API-Runtime erneut. Die Standard-API verwendet für diese Computer die bereits vorhandenen Bindungen des Pult-Graphen und erzeugt keinen zweiten `attach`-Aufruf. Als gemeinsame Identität dient CCs Ausführungsmonitor; verschiedene API-Factories erhalten unterschiedliche `IComputerSystem`-Wrapper für denselben Computer.

## Namen und Informationen

Jeder Link erhält eine persistente Adresse:

```text
cc_aeroworks:link_<uuid>
```

Die Namen sind unabhängig von Computer-IDs, Koordinaten und der Reihenfolge gefundener Geräte. Beide APIs verwenden dieselbe Gerätebindung. Ein nach einer Trennung zurückkehrender Link behält seinen Namen, erhält aber eine neue Bindungslebensdauer: alte Handles werden nicht wieder gültig.

`getPeripheralInfo()` auf gewrappten Geräten und die Pultbaum-Einträge enthalten zusätzlich:

| Feld | Bedeutung |
| --- | --- |
| `connectionType` | `wireless` bei Links, `adjacent` bei lokalen Pultgeräten |
| `linkPosition` | Blockposition des Links mit Dimension |
| `targetPosition` | Blockposition des angebundenen Geräts mit Dimension |
| `targetId` | Persistente UUID des ausgewählten Zielankers |

Alle bisherigen Pultfelder bleiben erhalten. Die Positionen beschreiben Blockzugriffsadressen, auf Sable-Fahrzeugen also Plotkoordinaten. Zur Reichweitenberechnung verwendet die Implementierung ausschließlich die aktuell transformierten Weltpositionen. Im Pultbaum behalten benachbarte Geräte ihre Seiten-Schlüssel; drahtlose Geräte werden unter ihrer eindeutigen Linkadresse abgelegt, damit mehrere Links derselben Seite keine Einträge überschreiben.

## Weiterleitung und Lebenszyklus

Die Gerätesuche verwendet CCs `PlatformHelper.createPeripheralAccess`, einschließlich generischer Inventare und Tanks. Das Ergebnis bleibt bis zu einer Capability- oder Nachbaränderung gespeichert. Reine Validierungen lesen die vorhandenen Chunk-Blockentity-Tabellen über `getChunkNow`, damit sie keine `UNKNOWN`-Tickets auffrischen und Chunks regulär entladen können. Aufrufe werden unverändert durch CCs `PeripheralMethod`-System ausgeführt. `mainThread`-Aufgaben verwenden einen Gültigkeits-Guard; Ergebnisse, Fehler, wartende Aufrufe, Ereignisargumente und Datei-Mounts werden weitergereicht. Es gibt keine zusätzliche Übertragungswartezeit.

`IComputerAccess` enthält die verfügbaren Links, lokalen Seiten und die bereits angeschlossenen Kabelnetze. Damit können Inventar- und Flüssigkeitstransfers Linkgeräte sowie lokale und kabelgebundene Geräte adressieren. Die Auflösung erzeugt keine zusätzlichen Attachments.

Ein Serverregister hält ausschließlich geladene Links und ausgewählte Zielanker. Es lädt keine Chunks und führt keine räumliche Suche nach Links durch. Beim Chunk-Laden werden bereits vorhandene Blockentities mit gespeicherter Zielidentität registriert. Geprüft wird pro Servertick und vor Geräteaufrufen; bei CC-Aufgaben auf dem Hauptthread werden die aktuellen Weltpositionen erneut validiert. Auf dem Computerthread verwenden sichere CC-Methoden den publizierten Verbindungszustand.

Entfernung, Entladung, Dimensionsunterschied, Reichweitenüberschreitung oder fehlende Geräte trennen die Bindung. Bei Wiederverfügbarkeit wird sie automatisch erneuert. Standardereignisse sind `peripheral` und `peripheral_detach`; im Pultnetz kommen `cc_aeroworks_peripheral_attached` und `cc_aeroworks_peripheral_detached` hinzu. Das Zielgerät erhält keine Rücksetzbefehle und behält sein eigenes Verhalten.

Ziel- und Link-UUIDs liegen in den Blockdaten. Sie überstehen Speichern, Neustarts sowie Sable-Assemblierung und -Disassemblierung, wenn diese Daten übernommen werden. Werden zwei geladene Zielblöcke mit derselben Ziel-UUID erkannt, wird die UUID als Konflikt dauerhaft in den Weltdaten gesperrt. Auch nach Entladen/Entfernen einer Kopie oder Neustart bleibt sie deaktiviert. Erneute bewusste Auswahl vergibt dem betroffenen Anker eine neue UUID; die Links müssen anschließend neu gekoppelt werden. Eine zeitgleiche Registrierung während einer Sable-Blockverschiebung wird erst beim Validieren nach Bereinigung entfernter Blockentities als Konflikt bewertet.

## Entwicklung und Prüfung

Compile-Abhängigkeiten zusätzlich unter `dependencies/compile/` bereitstellen:

- CC:Tweaked 1.120.2 (API-Kompatibilitätsbereich 1.119.0 bis vor 1.121.0).
- CC Aeroworks 1.4.0 und Aeroworks 1.5.0 für die optionale Pultanbindung.

Diese unveränderten JARs werden nicht in TeutonLib eingebettet. Die Pultanbindung ist ebenfalls in Kotlin implementiert. Für die lokal geprüften Aeroworks-Binaries mit Kotlin-2.4-Metadaten ist der Compiler entsprechend konfiguriert; die JVM-Schnittstellen werden zusätzlich durch Kompatibilitätstests geprüft.

```bash
./gradlew build --offline
lua tools/tests/peripheral_links.lua src/main/resources/data/computercraft/lua/rom/modules/main/teutonlib/peripheral_links.lua
python3 tools/verify_peripheral_links.py cc
python3 tools/verify_peripheral_links.py console
```

Die Serverprüfungen nacheinander ausführen. Sie verwenden eigene Welten unter `run/peripheral/<profil>`, eine ausschließlich lokale RCON-Verbindung und eine separate Entwicklungsmod, die nicht in der auszuliefernden JAR enthalten ist. Protokolle und strukturierte Ergebnisse liegen unter `build/reports/smoke/peripheral-<profil>.*`.

Der CC-Test läuft ohne CC Aeroworks; das Console-Profil ergänzt Aeroworks und dessen Computer. Das CC-Profil läuft mit Create ohne Sable und ohne CC Aeroworks; das Console-Profil verwendet zusätzlich die lokalen Sable-/Aeroworks-Binaries. Für lokale Starts lässt sich der Zusatz explizit mit `-PperipheralRuntime=cc` oder `-PperipheralRuntime=console` aktivieren. Entwicklungsprüfungen benötigen zusätzlich `-PperipheralTests`.

Die Tests prüfen reale normale und erweiterte CC-Computer mit CraftOS, Kopplung/Platzierung, Löschen der Itemauswahl, generische Inventare, mehrere Links, konfiguriertes Netzlimit, persistente Namen, falsche Dimension, exakte Reichweitengrenze, echte Chunk-Entladung, Wiederverbindung, alte Handles, Kabel- und Flüssigkeitstransfers, Monitorereignisse, Mounts sowie UUID-Konflikte und einen Serverneustart. Die Sable-Prüfung verwendet echte Assemblierung und Blockverschiebung mit separaten Sublevels, veränderten Positionen und Drehung. Das Console-Profil prüft beide Computervarianten, reale Gimbal-Messwerte, genau ein gemeinsames Laufwerks-Attachment für beide APIs, gemeinsame Namen und Bindungen, Pultbaum, computerlose Reihen sowie Entfernen/Einsetzen des Computers, Reboots und Teilen/Zusammenführen der Reihe.

Zusätzliche Ingame-Abnahme: Erscheinungsbild und Ponder auf einem Client sowie reale Avionics-Stellbefehle mit dessen vorhandenem Interface. Die Beispieltypen oben setzen deren jeweilige Mods voraus. Der Link nimmt keine Avionics-spezifischen Eingriffe vor.
