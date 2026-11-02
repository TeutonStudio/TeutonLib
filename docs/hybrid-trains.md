# Hybridzüge

Mit Create 6.0.10, Sable 2.0.6 und Create: Train Functionality 0.1.47 verwendet
TeutonLib standardmäßig `teutonlib:hybrid_carriage`. Der Typ erbt Creates Wagenlogik:
Gleise, Fahrplan, Steuerung und Portalgrenzen bleiben native Create-Funktionen.
Alte gespeicherte Create-Wagen werden beim Laden vor dem Einlesen ihrer Daten auf
den neuen Typ umgestellt. Vorhandene CTF-Aufbauten werden an ihren laufenden
Simulationsbestand gebunden, nicht aus einer möglicherweise veralteten Blockkopie
neu hergestellt.

## Direkt am Bahnhof bauen

**Neuen Zug bauen** eröffnet unmittelbar einen stationären Sable-Bauplot. Auch
ohne den ersten Bogey bleibt dieser leere Plot erhalten; es wird kein Hilfsblock
in der Welt platziert. Die Bogeys werden über Creates normale Gleisinteraktion
direkt im Plot gesetzt. Nach zwei Bogeys beginnt der nächste Bogey einen neuen
Wagenplot. Creates Mindestabstände und Reihenfolge entlang der Bahnhofsausrichtung
gelten weiterhin. Sables automatische Auftrennung ist für diese Bauplots ausgesetzt:
Getrennte Bogeys und noch nicht verbundene Teile desselben Wagens bleiben zusammen.
Eine feste Physikverbindung hält jeden belegten Bauplot an seinem Bahnhof.

**Zug bauen** übergibt die Wagen an Creates nativen Zusammenbau einschließlich
Steuerung, Gleisprüfung und Frachtübernahme. Dafür werden die echten Blöcke nur
während dieses synchronen Aufrufs an ihre Weltpositionen übertragen. Nach einem
abgewiesenen Zusammenbau kehren die verbleibenden Blöcke samt BlockEntity-Daten
in ihre Bauplots zurück. Jeder Wagen erfasst seinen gesamten Plot; eine Klebung
zwischen den getrennten Bauteilen desselben Bauplots ist dafür nicht erforderlich.
Die Erfassung bleibt auf diesen Wagen begrenzt und nimmt keine benachbarten
Gleise oder Teile des nächsten Wagens mit.
Ein vorbereitetes Swivel-Lager darf dabei mit seinem Wagen erfasst werden;
seine zuvor abgesetzte Nutzlast wird nach der Konvertierung wieder zusammengesetzt.

Weltblöcke, die inzwischen in den Baubereich gesetzt wurden, verhindern die
Übergabe, bevor ein Wagen verschoben wird. Überlappende Wagenplots werden ebenfalls
abgewiesen. Beim Verlassen des Baubetriebs bleiben die gebauten Blöcke in der Welt.
Wird der Bahnhof entfernt und ist diese Rückgabe versperrt, bleiben die Bauwerke
als normale Sable-Konstruktionen erhalten.

## ContraptionLevel und Darstellung

Im Fahrbetrieb erhält jeder Wagen ein **ContraptionLevel**. Es speichert den
grafischen Aufbau pro 16³-Abschnitt, verwendet kompilierte GPU-Geometrie für die
Blockmodelle und führt BlockEntities sowie `ENTITYBLOCK_ANIMATED`-Blöcke getrennt.
Eine Blockänderung invalidiert nur die betroffenen Abschnitte. Eine Änderung von
Position oder Drehung verwendet dieselbe Geometrie mit einer neuen Transformation.
BlockEntity-Renderer, Flywheel-Animationen, Flüssigkeiten und animierte Atlastexturen
verwenden weiterhin ihre nativen Renderpfade. Die grafischen Daten sind vom
Client-Plot abgeleitet und erzeugen keinen zweiten tickenden Maschinenbestand.
Wiederverwendete Bauplots erhalten den aktuellen Flywheel-Eintrag ihres Wagens,
damit die beweglichen Aufbauten von Lagern und Radar nach dem Zusammenbau sichtbar bleiben.

**F3+B** zeigt zusätzlich einen orangenen Rahmen um das transformierte
ContraptionLevel. Im bearbeitbaren Modus verwendet der Wagen wieder Sables
normale Darstellung. Beim erneuten Fahrbetrieb wird der aktuelle Aufbau erfasst.
Ressourcen-Neuladen, entfernte Plots und Moduswechsel geben die alten Renderpuffer frei.

Das ContraptionLevel bleibt mit Sables Renderdaten-Schnittstelle kompatibel.
Immersive Portals verwendet damit dieselben kompilierten Abschnitte für die
geschnittene Darstellung auf beiden Portalseiten; die vorhandene Übertragung,
Weltbeleuchtung und Flywheel-Beleuchtung gelten auch für diese Wagen.

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
Hybrid-Wagentypen bleiben lesbar. Neue Bahnhofssitzungen verwenden wieder Creates
Baubetrieb; bereits eröffnete Sable-Bausitzungen lassen sich zu Ende führen.
Die Clients wechseln zwischen ContraptionLevel und normaler Sable-Darstellung.

Bei aktivierter Funktion werden **Functional Fob** und **Chunk Anchor Lever**
aus Kreativ- und Suchreiter ausgeblendet. Registrierungen, vorhandene Blöcke,
Items und Rezepte bleiben erhalten. Beim Abschalten erscheinen die Einträge
wieder. Der Server synchronisiert diese Auswahl beim Beitritt und bei Änderungen;
ein Neustart des Clients ist dafür nicht nötig.

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

## Docking-Connectoren am Wagen

Ein verriegelter Simulated-Docking-Connector verbindet weiterhin die echten
Sable-Körper und ihre nativen Transferbehälter. Eine angedockte Plattform bleibt
ein eigenständiges Sublevel; sie erhält durch die Verbindung kein ContraptionLevel.
Items, Flüssigkeiten, FE und ComputerCraft verwenden weiterhin die ursprünglichen
Simulated-Verbindungen. Der ausfahrende FULL-Ausleger darf nur seine Basis und
seinen unmittelbar davorliegenden Hilfsblock im gesperrten Wagenplot aktualisieren.

Beim Portalwechsel prüft TeutonLib die vollständigen, transformierten Plotgrenzen
der angedockten mobilen Körper gegen die rechteckige Portalöffnung. Passt der
Verbund, werden die beteiligten Körper koordiniert übertragen. Die nativen
Docking-Verbindungen werden für die Übergabe kurz gelöst und im Ziel wieder
hergestellt, einschließlich Physikverbindung und Ressourcentransfer. Eine bereits
verriegelte Verbindung darf dabei mit ihrer nativen Wiederherstellungsfunktion
zurückkehren; normale neue Verbindungen behalten die analoge Geometrieprüfung.

Passt eine Gegenstelle nicht durch die Öffnung, wird die betreffende Verbindung
bereits am ersten Gleis-Portalübergang getrennt. Die zu breite Gegenstelle bleibt
in der Ausgangswelt und behält ihre normale Physik. Die gelösten Anschlüsse dürfen
sich während dieser Trennung nicht sofort wieder magnetisch verriegeln.
Weltfeste Dockingstationen und eigenständig geführte andere Zugwagen werden nicht
als mobile Nutzlast des Wagens mitgenommen. Für nichtrechteckige Portale wird
keine vollständige Passung einer angedockten Nutzlast angenommen.

Die Übertragung nutzt die einzelnen nativen Sable-Transaktionen. Eine fehlgeschlagene
Wagenübergabe führt bereits übertragene Gegenstellen zurück; die Gruppe besitzt
keine zusätzliche gemeinsame Transaktionsdatei für einen Serverabsturz zwischen
den einzelnen Übertragungen.

Die reproduzierbaren Ingame-Prüfungen verwenden zwei echte Wagen und eine breite,
dynamische Plattform mit nativer Docking-Physik:

```sh
python3 tools/record_train_docking_demo.py --case wide --check
python3 tools/record_train_docking_demo.py --case narrow --check
python3 tools/record_train_docking_demo.py --case wide --check --rollback
```

Ohne `--check` zeichnet derselbe Ablauf das Minecraft-Bild mit durchgehend aktivem
F3+B auf. `tools/finish_train_docking_demo.py <Berichtsverzeichnis>` erzeugt daraus
das jeweilige MP4 mit deutschen Einblendungen. Die Prüfungen erfassen Verriegelung,
echte Constraints, bidirektionale Transfers, erhaltene UUIDs und Fracht sowie die
tatsächliche Bewegung der Plattform durch ihre Docking-Verbindung.

Abnahme am 02.11.2026: Beide Portalgrößen und die absichtlich fehlgeschlagene
Übergabe mit anschließender Rückabwicklung bestanden. Beim großen Portal wurde
im Ziel wieder ein gültiger nativer FixedConstraint samt Item-, Flüssigkeits-,
FE- und ComputerCraft-Verbindung geprüft; beim kleinen Portal waren beide
Endpunkte und alle Transferverbindungen getrennt. Die Plattform wurde vor der
Übergabe ausschließlich durch die native Docking-Physik über mehr als 46 Blöcke
mitgezogen. Beide Aufnahmen enthalten durchgehend F3+B: 1.865 beziehungsweise
2.125 erfasste Frames, jeweils ohne einen Frame mit ausgeschalteten Hitboxen.
Die vollständigen Zustandsnachweise liegen unter
`build/reports/train-docking-demo/verification-overview.json`.

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
mit nativer Transformation und Physikanbindung.** Das ContraptionLevel ergänzt
die zwischengespeicherte Darstellung; es ist
kein vollständig Sable-freier Fahrmodus. Das erhält die vorhandenen Mod-Anbindungen,
insbesondere Radars Weltkoordinaten und verschachtelte Contraptions. Der feste
Aufbau allein beseitigt deshalb weder die Kosten der Blocksimulation noch alle
Sable-Physikkosten. Ein eigener Ersatz für diesen Hintergrund wäre ein weiterer
Architekturumbau.

## Prüfung

`python3 tools/verify_hybrid_trains.py` verwendet eine neue lokale Testwelt mit
den Originalmods. Die Prüfung umfasst zunächst den echten Bahnhofspfad: leerer
Bauplot, native Bogey-Platzierung, zweiter Wagen ab Bogey drei, ausgebliebene
Auftrennung, stationäre Lage, Rückgabe nach abgewiesenem Zusammenbau, blockierte
Übergabe sowie Fracht und laufende Lager mit Nutzlast nach dem Zusammenbau.
Ein unfertiger Bauplot wird über einen vollständigen Serverneustart mit derselben
UUID und seinem Inventar geprüft; anschließendes Abbrechen gibt die Blöcke und
Fracht an die Welt zurück.
Anschließend prüft sie automatische Konvertierung, beide Modi,
Struktursperre auf Welt- und Chunk-Ebene, geparkten Umbau, Ofen- und Lampenticks,
ein echtes betriebenes Radar, mechanische Lager mit Nutzlast, Swivel-Lager mit
Kisteninventar und geerbter Struktursperre, beide CBC-Lagertypen, erhaltene Munition,
echte Kanonenschüsse sowie Schlauchkopfidentität und native Fluidförderung.
Portal-Hin- und Rückfahrt, Rückabwicklung und vollständiger Serverneustart werden
mit diesen laufenden Maschinen geprüft.
`--packaged` prüft die gepackte TeutonLib-JAR. Die benötigten Original-JARs werden
im Testprofil geladen und nicht in TeutonLib eingebettet.

`python3 tools/verify_train_portal_client.py --sodium --render-only --verify-flywheel`
prüft einen separaten grafischen Client: tatsächliche ContraptionLevel-Instanzen,
Modelle und animierte Blöcke, orangene F3+B-Linien, Kreativmenü-Einträge und
Konfigurationswechsel sowie Hin-/Rückfahrt und Beleuchtung beider Portalseiten.
Screenshots und Messwerte liegen unter `build/reports/train-portal-client/` und
der im Bericht referenzierten Testinstanz.

## Ingame-Video

`python3 tools/record_train_demo.py` nimmt in einer separaten Testwelt den
Stationsbau von zwei Wagen, den Wechsel zum ContraptionLevel und die Fahrt durch
ein Immersive Portal in den Nether auf. Mechanisches Lager, Swivel Bearing,
Radar und das Aus-/Einfahren des physischen Schlauchkopfs werden vor und nach
der Fahrt gezeigt. Derselbe native Stationszug wird während der ganzen Demo
beibehalten. Die automatisierte Demo prüft zusätzlich Nutzlasten, Fracht,
Rotationen und Client-Entities und protokolliert deren Flywheel-Zuordnung.

Benötigt werden das vorhandene Hybrid-Testprofil, ein OpenGL-Desktop und `ffmpeg`
mit `libopenh264`. Der Recorder erfasst ausschließlich den Minecraft-Framebuffer;
die Aufnahme ist ohne Ton. Kapitelaufnahmen, Screenshots und Prüfberichte liegen
in der unter `build/reports/train-demo/` referenzierten Testinstanz.
`python3 tools/finish_train_demo.py <Berichtsverzeichnis>` ergänzt deutsche
Kapiteltexte und exportiert `media/teutonlib-zug-sable-contraptionlevel-portal.mp4`.
Mit `python3 tools/record_train_demo.py --f3b` bleibt F3+B in jedem aufgenommenen
Bild aktiv; der Recorder prüft dies pro Bild. Der Export erhält dann den Zusatz
`-f3b.mp4`, sodass die vorherige Aufnahme erhalten bleibt.
