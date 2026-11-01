# Hybridzüge

Mit Create 6.0.10, Sable 2.0.6 und Create: Train Functionality 0.1.47 verwendet
TeutonLib standardmäßig `teutonlib:hybrid_carriage`. Der Typ erbt Creates Wagenlogik:
Gleise, Fahrplan, Steuerung und Portalgrenzen bleiben native Create-Funktionen.
Alte gespeicherte Create-Wagen werden beim Laden vor dem Einlesen ihrer Daten auf
den neuen Typ umgestellt. Vorhandene CTF-Aufbauten werden an ihren laufenden
Simulationsbestand gebunden, nicht aus einer möglicherweise veralteten Blockkopie
neu hergestellt.

## Fahren und umbauen

Im Fahrmodus `DRIVING` bleiben Blockarten und belegte Positionen fest. Blockzustände
wie Redstone, Lampen, Türen und der Betriebszustand eines Ofens dürfen sich ändern.
BlockEntities, Inventare, Flüssigkeitstanks und geplante Blockticks laufen weiter
über Minecraft und Sable. Es gibt keinen zweiten tickenden Inventarbestand.
Abbau und Platzierung werden auch bei direkten Chunk-Schreibzugriffen gesperrt;
`destroyBlock` wird vor Drops abgewiesen. Spielerabbau und Creates schleichender
Schraubenschlüsselabbau werden bereits vor ihren Nebeneffekten unterbunden.

Neben einem angehaltenen, vollständig geladenen Zug:

* `/teutonlib train edit`: alle Wagen in den bearbeitbaren Sable-Modus `EDITING` wechseln.
* `/teutonlib train run`: den Aufbau sperren und den Fahrbetrieb wieder freigeben.
* `/teutonlib train status`: Modus des nächsten geladenen Zugs anzeigen.

Die Befehle erfordern Berechtigungsstufe 2. Sie wählen den nächsten Wagen im Umkreis
von 32 Blöcken und bearbeiten dessen gesamten Zug. Vor dem Moduswechsel werden
alle Wagen geprüft. Während einer Portalübertragung ist der Wechsel gesperrt.
Im Umbaumodus bleibt der gesamte Zug auf dem Gleis stehen; Maschinen und
Unter-Contraptions laufen weiter. Die normale Zerlegung am Bahnhof bleibt möglich.
Explizites Konvertieren über CTF schaltet einen angehaltenen Wagen in den Umbaumodus;
CTFs automatische Konvertierung verwendet den Fahrmodus.

Der Modus wird in Sables `user_data` gespeichert. Umschalten serialisiert oder
kopiert weder Inventare noch Maschinen und erzeugt keinen neuen Aufbau.

```toml
[trains]
hybridByDefault = true
```

Diese Weltkonfiguration übersteuert CTFs `trainHandling` für die automatische
Konvertierung: Auch Wagen ohne Functional Fob bekommen eine Simulation. Mit
`false` entfallen automatische Hybridkonvertierung und Struktursperre; gespeicherte
Hybrid-Wagentypen bleiben lesbar.

## Lager, Kanonen, Schlauchrolle und Radar

Lager und andere Create-Unter-Contraptions dürfen ihre eigenen erfassten Blöcke
beim Zusammenbau aus dem Plot entnehmen und beim Zerlegen wieder einsetzen.
Die Ausnahme gilt nur innerhalb des jeweiligen Methodenaufrufs, für dessen
konkrete Blockpositionen, und wird auch bei Fehlern aufgehoben. Gewöhnliche
Maschinen dürfen darüber keine beliebigen neuen Blöcke auf dem Zug platzieren.

Mechanische Lager bleiben mit ihrer echten Create-Contraption betriebsfähig.
Swivel-Lager aus **Simulated 1.3.2** behalten ihre eigene Sable-Unterebene und
Nutzlast. Fehlt beim Laden die Quelle ihres separaten Zahnradgetriebes,
initialisiert TeutonLib dessen echte Create-Kinetik nach dem Laden der Nachbarn
erneut. Die Struktursperre folgt der tatsächlichen Lagerverbindung bis zum
Zug; nach dem Lösen gehört ein ehemaliger Kindplot nicht mehr automatisch dazu.

**Create Big Cannons 5.11.7** mit **Ritchie's Projectile Lib 2.1.2** wird für
normale und feste Kanonenlager unterstützt. Die native CBC-Zusammensetzung,
geladene Geschosse, Treibladungen und Autokanonen-Munitionspuffer bleiben erhalten.
CBC führt Schüsse und die Umrechnung der Projektilkoordinaten selbst aus.

Die Schlauchrolle verwendet weiterhin ihren echten Fluidhandler. Ihr physischer
Schlauchkopf wird beim Portalwechsel mit unveränderter UUID übertragen und an
die neue Plotposition gebunden. Verbindungen zu einem außerhalb des Zuges
verbleibenden Schlauchanschluss werden vorher gelöst.

Für Create: Radars **0.4.9.2-1.21.1** ergänzt TeutonLib `teutonlib:radar`.
Die Originalmod speichert ihre Radar-Contraption als gewöhnliches Create-Lager;
TeutonLib erhält stattdessen Typ, Empfänger, Ausrichtung, Schüsselzahl und
Creative-Status beim Speichern und Laden.

## Portale und technische Grenze

Mit Immersive Portals: Aeronautics 2.6.4 läuft der vorhandene koordinierte
Wagentransfer einschließlich Rückabwicklung. Der Modus reist im selben gespeicherten
Aufbau mit. Ein Fehler darf keinen zweiten Wagenaufbau oder Frachtbestand erzeugen.
Vor dem Transfer werden laufende Lager und Kanonen kontrolliert abgesetzt;
Swivel-Nutzlasten werden dabei rekursiv in ihren Elternplot zurückgesetzt.
Die Wiederanlaufabsicht liegt in den persistenten Daten des jeweiligen Controllers
und reist mit dessen Blöcken, auch bei verschachtelten Baugruppen. Nach der
Zielinitialisierung werden sie wieder zusammengesetzt und ihre Steuerzustände
hergestellt. Dieselbe Behandlung gilt nach einer Rückabwicklung. Dabei können
die Entity-UUIDs neu zusammengesetzter Contraptions wechseln.

**Diese Implementierung behält im Fahrbetrieb einen echten Sable-Simulationsplot
einschließlich Sables Darstellung, Transformation und Physikanbindung.** Sie ist
kein vollständig Sable-freier Fahrmodus. Das erhält die vorhandenen Mod-Anbindungen,
insbesondere Radars Weltkoordinaten und verschachtelte Contraptions. Der feste
Aufbau allein beseitigt deshalb weder die Kosten der Blocksimulation noch alle
Sable-Physikkosten. Ein eigener Ersatz für diesen Hintergrund wäre ein weiterer
Architekturumbau.

## Prüfung

`python3 tools/verify_hybrid_trains.py` verwendet eine neue lokale Testwelt mit
den Originalmods. Die Prüfung umfasst automatische Konvertierung, beide Modi,
Struktursperre auf Welt- und Chunk-Ebene, geparkten Umbau, Ofen- und Lampenticks,
ein echtes betriebenes Radar, mechanische Lager mit Nutzlast, Swivel-Lager mit
Kisteninventar und geerbter Struktursperre, beide CBC-Lagertypen, erhaltene Munition,
echte Kanonenschüsse sowie Schlauchkopfidentität und native Fluidförderung.
Portal-Hin- und Rückfahrt, Rückabwicklung und vollständiger Serverneustart werden
mit diesen laufenden Maschinen geprüft.
`--packaged` prüft die gepackte TeutonLib-JAR. Die benötigten Original-JARs werden
im Testprofil geladen und nicht in TeutonLib eingebettet.
