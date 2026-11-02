# TeutonLib 1.6.0

Für Minecraft **1.21.1**, NeoForge **21.1.234 oder neuer auf der 1.21.1-Linie**,
Java **21** und KotlinForForge **ab 5.11.0 bis kleiner 6**.
TeutonLib gehört auf Server und alle Clients.

## Bahnhofsbau auf Sable

- **Neuen Zug bauen** eröffnet direkt einen stationären Sable-Bauplot, auch vor
  dem ersten Bogey. Je zwei Bogeys gehören zu einem Wagen; Bogey drei beginnt
  einen weiteren Plot. Getrennte Wagenteile bleiben während des Bauens zusammen.
- Der native Create-Zusammenbau übernimmt den vollständigen Wagenplot samt
  Inventaren und laufenden Lager-Nutzlasten. Fehlgeschlagener Zusammenbau gibt
  die verbleibenden Blöcke und BlockEntity-Daten an die Bauplots zurück.
- Belegte Weltpositionen und überlappende Wagenplots werden vor der Übergabe
  abgewiesen. Unfertige Bauplots behalten Identität und Inventare über Neustarts;
  Abbrechen gibt die Blöcke an die Welt zurück.

## ContraptionLevel für Hybridzüge

- Der Fahrmodus verwendet zwischengespeicherte Blockgeometrie in 16³-Abschnitten.
  Bewegung und Drehung verwenden dieselben Puffer mit neuer Transformation;
  Blockänderungen aktualisieren die betroffenen Abschnitte.
- BlockEntity-Renderer, Flywheel, Flüssigkeiten und animierte Blockmodelle bleiben
  aktiv. **F3+B** zeigt den transformierten Aufbau mit einem orangenen Rahmen.
- `/teutonlib train edit`, `run` und `status` steuern weiterhin den gesamten Zug.
  Beim Umbauen verwendet der Client Sables normale Darstellung.
- `[trains] hybridByDefault` steuert Bahnhofsbau und Hybridbetrieb. Die Einstellung
  wird an Clients synchronisiert und blendet bei Aktivierung Functional Fob und
  Chunk Anchor Lever aus Kreativ- und Suchreiter aus. Vorhandene Blöcke und Rezepte
  bleiben erhalten; offene Bausitzungen lassen sich nach Abschalten beenden.

Der Fahrmodus behält einen echten Sable-Simulationsplot mit laufenden Maschinen
und Physikanbindung. Die neue Darstellung entfernt diese Simulationskosten nicht.

## Docking und Portale

- Verriegelte Simulated-Connectoren verbinden Hybridwagen mit eigenständigen
  mobilen Plattformen über ihre native Physik und ihre Item-, Flüssigkeits-,
  FE- und ComputerCraft-Verbindungen.
- Passt die vollständige transformierte Nutzlast durch eine rechteckige
  Portalöffnung, reist sie koordiniert mit. Connectoren werden für die Übergabe
  kurz getrennt und im Ziel einschließlich Physik und Transfers wiederhergestellt.
- Zu breite Gegenstellen werden am ersten Gleis-Portalübergang gelöst und bleiben
  in der Ausgangswelt. Weltfeste Docks und eigenständig geführte Zugwagen werden
  nicht als Wagenlast mitgenommen. Fehlschläge führen bereits übertragene Partner
  zurück.
- Das ContraptionLevel verwendet die bestehende beidseitige Portal-Darstellung
  einschließlich Clipping, Beleuchtung und projizierter Wagenverbindungen.

Geprüfte Zugbasis: **Create 6.0.10**, **Sable 2.0.6** und **Create: Train
Functionality 0.1.47**. Portalbetrieb zusätzlich mit **Immersive Portals:
Aeronautics 2.6.4** / **Flywheel 1.0.6**; Docking mit **Simulated 1.3.2**.
Diese Mods bleiben optional und werden nicht mitgeliefert.

## Wiki und bestehende Funktionen

Die [GitHub-Wiki](https://github.com/TeutonStudio/TeutonLib/wiki) dokumentiert jede
Funktion auf einer eigenen Seite: Installation und Konfiguration, Computer-UI,
Kanäle und Entwickler-APIs, Peripheral Links, Heli-Steuerung, Grafik und Schrift,
Funk/GPS, Zahnradbrücken, Docking, FE-Motoreingänge, physische Schläuche und
Item-Drain-Kupplung, Zugfunktionen sowie optionale Logistik-, Mekanism-, Android-
und Ressourcenadapter. Die Quellen liegen versioniert unter [wiki](wiki/Home.md)
und sind im Vorlagen-ZIP enthalten. Die Computer-UI-Vorlage verwendet jetzt die
aktuelle Release-Abhängigkeit 1.6.0.

## Bekannte Grenzen

- Im dokumentierten Aether-Test für **stehende** Mitfahrer besteht weiterhin ein
  Positionssprung von etwa 2–2,2 Blöcken bei der Wagenkörperübergabe. Ein erfolgreicher
  Render- oder sitzender Mitfahrertest belegt keine Behebung dieses Falles.
- Angedockte Lasten können abhängig von Masse und Hebelwirkung durchhängen,
  obwohl native Verriegelung und Constraint gültig bleiben. Vergleichsläufe zeigen
  dieses Verhalten auch ohne Hybridfunktion; die Ursache ist noch offen.
- Dockinggruppen verwenden einzelne native Sable-Transaktionen. Ein Serverabsturz
  zwischen diesen Übertragungen ist nicht durch eine zusätzliche atomare
  Gruppentransaktion abgesichert.
- Die Integrationen sind an die dokumentierten Mod-Versionen und zum Teil an
  Klassen-Prüfsummen gebunden. Neuere oder veränderte Fremd-JARs sind nicht
  automatisch kompatibel.
- Der vorhandene Lua-SVG-Parser zeichnet gewöhnliche `path`-Elemente derzeit
  nicht. Die Grafikdokumentation nennt diese Grenze und die Alternativen
  `line`, `polyline` und `polygon` ausdrücklich.

Details: [Hybridzüge](hybrid-trains.md) und
[Portal-Kompatibilität](train-portal-compatibility.md).

## Downloads

- `teutonlib-1.6.0.jar`: Mod für Server und Clients.
- `teutonlib-1.6.0-sources.jar`: zugehöriger Quellcode.
- `teutonlib-1.6.0-computer-ui-template.zip`: Mod, Quellen, Beispiele und Dokumentation.
- `SHA256SUMS`: Prüfsummen dieser drei Artefakte.

Die separaten Mods `cso` und `hosepulley_sable_bridge` dürfen nicht zusätzlich
installiert sein. Testmods, lokale Original-Mod-JARs und Videoaufnahmen werden
nicht ausgeliefert.

## Prüfung

Am 02.11.2026 ausgeführt:

- `./gradlew build compileTrainPortalTestKotlin --offline`: erfolgreich,
  **160 JUnit-Tests**, keine Fehler oder übersprungenen Tests.
- `python3 tools/verify_hybrid_trains.py --packaged`: neue 1.6.0-JAR einschließlich
  Bahnhofsbau, abgewiesenem Zusammenbau, Fracht und laufenden Maschinen, beiden
  Fahrmodi, Portal-Hin-/Rückfahrt, Rückabwicklung sowie zwei Neustart-Prüfgruppen
  erfolgreich geprüft.

- `python3 tools/record_train_docking_demo.py --case wide --check --rollback`:
  native Docking-Transfers, passender Portalverbund, Rückabwicklung und erneute
  Fahrt erfolgreich geprüft. Der RCON-Prüfclient verarbeitet nun auch mehrteilige
  Zustandsberichte vollständig.

- `python3 tools/record_train_docking_demo.py --case narrow --check`: zu breite
  Plattform vor der Portalpassage getrennt, native Transferverbindungen gelöst,
  Fracht und Körperidentität erhalten.
- `./gradlew build releaseBundle --offline` und `python3 tools/verify_release.py`:
  Release-Artefakte, Metadaten, Lizenzen und Paketinhalt geprüft.
- 51 Dokumentationsseiten sowie Startseite und Navigation: interne Wiki-Links,
  versionierte Quellpfade und Codeblöcke geprüft; alle Funktionen anhand ihrer
  bestehenden Implementierung dokumentiert.
