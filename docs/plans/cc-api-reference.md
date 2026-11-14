# Vollständige CC API Referenz in TeutonLib

Stand: 12. November 2026. Status: Implementierungsplan.

TeutonLib erhält eine gemeinsame, offline nutzbare Lua-API-Referenz für CC:Tweaked und die CC-Integrationen des aktuellen TerrArchitecture-Packs. Ein eigener Reiter **API** im regulären CC-Computerbildschirm öffnet sie ohne Buchitem. Dieselbe Referenz steht Addon-Computern zur Verfügung. Nicht installierte Mods hinterlassen keine leeren Kategorien, Suchtreffer oder Beispiele. Das CC-Aeroworks-API-Handbuch wird nach vollständiger Inhaltsübernahme zum Kompatibilitätseinstieg in diese Referenz.

Die Erweiterung dokumentiert bestehende Schnittstellen. Lua-Namen, Computerdateien, Peripheral-Verbindungen, Kanalverwaltung und Zuständigkeiten der Mods bleiben bestehen. Die öffentliche Kotlin-/Java-Integrations-API von TeutonLib bekommt eine eigene Entwicklerdokumentation; sie wird nicht zwischen die Lua-Funktionen der Ingame-Referenz gemischt.

## 1 Ausgangslage und untersuchter Packstand

Maßgeblich ist TerrArchitectures aktuelles [Modpack-Lockfile](../../../TerrArchitecture/config/modpack-lock.json), zusammen mit der [Versionsmatrix](../../../TerrArchitecture/docs/platform/VERSION_MATRIX.md). Der Repository-Ordner `mods/`, `mods legacy/` und ältere Packberichte sind kein verlässlicher aktueller Bestand. Insbesondere gilt CC:Tweaked **1.120.2**, TeutonLib **1.6.0** und CC-Aeroworks **1.5.4**. Gleichnamige lokale und veröffentlichte Builds können unterschiedliche Oberflächen haben; Version und Artefakthash gehören zur Dokumentationsbasis.

Die [maschinenlesbare Bestandsaufnahme](../research/cc-api-inventory-20261112.json) erfasst CC-Verweise und ROM-Ressourcen einschließlich eingebetteter JARs: 447 von 453 Lockfile-Artefakten waren im untersuchten Clientprofil lesbar. Sie enthält 38 relevante Artefakt-Datensätze einschließlich indirekter Maschinenintegrationen und des Rezeptaddons `ccbr`. Die 38 untersuchten Artefakte stimmen jeweils per SHA-512 mit dem Lockfile überein. Die sechs nicht dort vorliegenden Artefakte sind im Inventar aufgeführt; TerrAs eigener Quellcode wurde ergänzend auf öffentliche CC-Schnittstellen geprüft. Er liefert derzeit keinen eigenen Lua-API-Anbieter.

Ein CC-Klassenverweis ist ein Kandidat, noch keine öffentliche Methode. Umgekehrt bleiben indirekte Registrierungen wie Mekanism Generators und Extreme Reactors in einem reinen `@LuaFunction`-Scan unsichtbar. Phase 1 muss deshalb aus dieser Bestandsaufnahme ein vollständiges öffentliches Symbolinventar bilden.

### Bestehende Anschlussstellen

| Bestandteil | Befund und Verwendung |
| --- | --- |
| `ComputerScreenMixin` | Erweitert `AbstractComputerScreen` um gemeinsame Reiter. Ohne Addon-Deskriptor werden derzeit nur native Blockcomputer `computercraft:computer_*` angenommen. Turtle, Pocket und Android benötigen ausdrücklich geprüfte Hosts. |
| `ComputerSidebarTabs` | Vorhandene CC-Gehäuseoptik, Widgetbedienung und Positionierung für den zusätzlichen API-Reiter verwenden. |
| `WorkspacePanels` | Gemeinsame Quellen-/Kanalansicht. `show()` akzeptiert nur 0 bis 2; die Referenz darf dieses Protokoll nicht als zusätzliche Workspace-Seite missbrauchen. |
| `ComputerWorkspaceClients` | Addons liefern Deskriptoren, eigene Reiter und Clientaktionen. `resolve()` wählt den ersten passenden Deskriptor; die Referenz darf keinen konkurrierenden Catch-all-Deskriptor registrieren. |
| `SourceApi`, `SourceMethod`, `SourceDocumentationProvider` | Bestehende kompakte Darstellung für reale Quellen beibehalten und mit stabilen Referenz-IDs verknüpfen. Bestehende Konstruktoren und Provider bleiben kompatibel. |
| `SourceIntrospection` | Nutzt CCs Methodensupplier und erkennt dynamische/generische Peripheral-Methoden. Abgeleitete Signaturen bleiben teilweise `unknown`; fachliche Semantik muss der Katalog liefern. |
| `ComputerExtensions` | Bestehende Registrierungs- und Workspace-Verträge weiterverwenden. Kein zweites Peripheral-Attachment und keine zweite Kanalbank für Dokumentation. |
| Aeroworks `ApiReferenceCatalog` und `GuideBookContent` | Ausgangspunkt für die Aeroworks-Inhalte, aber kein vollständiger Packkatalog. Zusätzlich aktuelle mobile APIs, Admin-APIs, Handles und CTF-Oberflächen inventarisieren. |
| Aeroworks `verify-api-reference.py` | Vorbild für Abdeckungsprüfungen. Der heutige prüferseitige Klassenkatalog umfasst nur einen Teil der aktuellen Oberflächen und prüft überwiegend Methodennamen. |

Die [PC-UI-Vorlage](../../examples/computer-ui/README.md), [Computergrafik](../computer-graphics.md), [Peripheral Links](../peripheral-link.md) und [Heli-Peripherals](../heli-peripherals.md) liefern vorhandene Verträge und Beschreibungen.

## 2 Vollständiger Umfang

Vollständig bedeutet: Für jede öffentliche, in der unterstützten Packversion vorhandene Lua-Oberfläche sind Funktionen, Aliase, Konstanten, Datentypen, zurückgegebene Handles und Ereignisse auffindbar. Dazu gehören Parameter, Rückgaben, Voraussetzungen und mindestens ein passendes Anwendungsbeispiel je API. Dokumentierte Shellprogramme erhalten eine eigene Bedienreferenz.

Interne Java-/Kotlin-Hilfsklassen, `cc.internal.*`, Debugprobes und Implementierungsdetails werden nicht als öffentliche Lua-APIs veröffentlicht. Absichtlich ausgeschlossene Symbole brauchen im Prüfinventar eine konkrete Begründung. Ein Platzhalter mit Methodennamen und `unknown` erfüllt die Vollständigkeitsabnahme nicht.

### CC:Tweaked und Lua

- Alle öffentlichen CraftOS-Globals und Standardbibliotheken der tatsächlich gebündelten Cobalt-Laufzeit, einschließlich `_G`, `coroutine`, `string`, `table`, `math`, `bit32`, `utf8` und verfügbarer `debug`-Funktionen. Unterstützte Lua-Versionseigenschaften ausweisen, keine vollständige Lua-5.4-Standardbibliothek voraussetzen.
- CraftOS: `os`, `fs`, `io`, `shell`, `multishell`, `settings`, `help`, `keys`, `textutils`, `parallel`, `colors`/`colours`, `term`, `window`, `paintutils`, `vector`, `peripheral`, `redstone`/`rs`, `rednet`, `gps`, `http`, `disk`, `turtle`, `pocket`, `commands` und `require`/`package`. Tatsächlich exportierte Namen und Aliase gegen 1.120.2 prüfen.
- Öffentliche `cc.*`-Module: `cc.audio.dfpwm`, `cc.base64`, `cc.completion`, `cc.expect`, `cc.image.nft`, `cc.pretty`, `cc.require`, `cc.shell.completion`, `cc.strings`.
- Native Peripherals: Computer/Turtle als Peripheral, Laufwerk, Modem samt Wired-/Wireless-/Ender-Varianten, Monitor, Drucker, Lautsprecher, Redstone Relay und Command Block. Weitere Typen nur aufnehmen, wenn sie in der gepinnten Version vorhanden sind.
- Generische Fähigkeiten: `inventory`, `fluid_storage`, `energy_storage`; Mehrfachtypen und zusammengesetzte Peripherals erklären.
- Rückgabeobjekte: Datei- und IO-Handles, HTTP-Responses, WebSockets, Terminal-Redirects, Fenster, Vektoren, Pretty-Objekte und weitere öffentlich zurückgegebene Objekte einschließlich ihrer Methoden und Lebensdauer.
- Sämtliche öffentlichen Ereignisse mit geordneter Argumentliste und zugehörigen APIs; Timer/Alarm, Tastatur/Maus/Paste, Dateiübertragung, Peripherals, Netzwerk/HTTP/WebSocket, Monitor/Terminal, Speaker, Turtle und Command Computer.
- Shellprogramme und Startverhalten getrennt von API-Globals erfassen. Ein Programm wie `gps` oder `channels` ist kein gleichnamiger Funktions-Namespace.

Die [offizielle CC-Referenz für Minecraft 1.21.x](https://tweaked.cc/mc-1.21.x/) dient als fachlicher Ausgangspunkt. Ihre laufend aktualisierte Website ist kein unveränderlicher 1.120.2-Vertrag. Importgrundlage sind passend gepinnte Quellen und die lokalen ROM-/Klassenoberflächen. Die [Lua-Kompatibilitätsreferenz](https://tweaked.cc/mc-1.21.x/reference/feature_compat.html) hilft beim Einordnen der Laufzeitunterschiede.

### CC Addons im aktuellen Pack

In der folgenden Matrix gilt zusätzlich immer `computercraft`. Die Mod-IDs und Versionen stammen aus den Artefaktmetadaten; Dateinamen können davon abweichen. Die letzte Spalte benennt den geplanten Umfang, nicht bereits fertig dokumentierte Methoden.

| Anbieter und Version | Sichtbarkeit nach Mod-ID | Aufzunehmende Oberflächen |
| --- | --- | --- |
| TeutonLib 1.6.0 | `teutonlib`, pro Integration weitere Ziele | `teutonlib_channels` / `teutonlib.channels`, `teutonlib_links` / `teutonlib.links`, Lua-Hilfsmodul `teutonlib.peripheral_links`, SVG, Bitmap-Schrift, Renderziele, Lager-/Heli-Ergänzungen, optische Sensorergänzungen und Simulated-Ergänzungen. Jeder Teil erhält seine tatsächlichen zusätzlichen Voraussetzungen. |
| CC-Aeroworks 1.5.4 | `cc_aeroworks` | Externes ControlDesk, eingebettete `peripherals`, `channels`, `controls`, `wires`, `telemetry`, Desk-/Dock-/Peripheral-/Mobile-Handles, mobile Computer, Display- und Touchmodule, Scriptdiagnose, Admin-Oberflächen, Ereignisse und ROM-Programme. `cc_aeroworks.*`-Modulnamen getrennt vom Aufrufscope erfassen. |
| Aeroworks Radar und CTF | `cc_aeroworks` plus jeweilige aktive Integration und Zielmod | Radar-Pult-Ergänzungen über `cc_aeroworks_radarcompat` mit `create_radar`, DashboardController über `cc_aeroworks_ctfcompat` mit `create_train_functionality`. Aktivierung gegen die tatsächliche Registrierung prüfen. Mitgelieferte Compat-Modcontainer allein belegen keine aktive Fähigkeit. |
| CC: Androids 1.4.0-1.21 | `cc_androids` | `android`, Fähigkeiten und Ereignisse im Android-Kontext, Inventar/Bewegung/Interaktion sowie Startup-, Follow- und Guard-Programme. |
| CC: Sable 1.3.4 | `cc_sable` plus jeweilige Sable-/Aeronautics-Fähigkeit | `sublevel`, `aero`, `matrix`, `quaternion`, `advanced_math.mmath`, `advanced_math.pid`, `advanced_math.stats`; auch Objektmethoden und besondere Ressourcenzugriffe erfassen. |
| Advanced Peripherals 0.7.61b | `advancedperipherals` plus Zielmod je Bridge | ChatBox, Player-/Environment-/Energy-Detector, GeoScanner, BlockReader, InventoryManager, Compass, NBT Storage, Chunk-/Automata-Funktionen, ME-/RS-/Colony-Bridges, Integrationen für Powah/Mekanism und generische Blockergänzungen. Gegen registrierte Peripherals/Plugins abgleichen. |
| MineColonies for ComputerCraft 2.8.3 | `colony4cc` und `minecolonies` | Colony-, Tile- und Pocket-Schnittstellen, Wireless-Variante, Ereignisse und ROM-Autorun. Nicht mit der Colony-Bridge von Advanced Peripherals zusammenführen. |
| CC:C Bridge 1.7.2 | `cccbridge` und `create` | Source, Target, Scroller, RedRouter, Animatronic, Display-/Redstone-Verhalten und Autorun. |
| CC Redstone Link 0.0.2 | `cccredstonelink` und `create` | Öffentlicher RedstoneLinkBridge-Vertrag dieser Implementierung. |
| CC Redstone Link Bridge, Datei 1.0.3, Metadaten 1.0.0 | `ccredstonelinkbridge` und `create` | Separater Bridge-Vertrag. Beide Linkaddons enthalten gleichnamige Klassen; Auflösung und mögliche Überlagerung ausdrücklich prüfen. |
| CC: Graphics 0.2.1 | `ccgraphics` | Native Terminalerweiterungen und Änderungen an `term`, `window`, `paintutils`; Grafikmodi, Koordinaten, Farbformate und Tools `gfxpaint`/`pngview`/`raycast`. Basissymbole werden erweitert statt dupliziert. |
| CC: Terminals 0.1.1 | `ccterminals` | TerminalPeripheral, Autorun, Terminalprogramm, Fähigkeiten verschiedener Terminalgeräte und Redirect-Verhalten. |
| CC: Optical 1.0.3 | `ccoptical`, CBC-Unterteil zusätzlich Zielmod | Entity-Sensor und Cannon-Mount-Integration; Sensor- und Ereignisdaten. |
| NeoPeripheral, Datei 1.4.1ve, Metadaten 1.4ve | `neoperipheral` plus Zielmod je Teil | Radarvarianten, Entity-Radar, NFC, Crypto, Socketmodule, Modem, Sable Engine, Create-/Tweaked-Controller-Hubs, CBC-/Compact-Cannon-Mount-/Synaxis-Integrationen. Kein pauschales Freischalten aller Integrationen mit der Basismod. |
| Simulated: ComputerCraft Compat 1.1.2 | `simulated_cc_compat` plus jeweilige Zielmod | PhysicsDataAccessor, RopeWinch, SteamVent, HotAirBurner, WheelMount; Scope und Verfügbarkeit der geerbten Methoden. |
| CCVisual 0.1.0 | `ccvisual` | `ccvisual_peripherals`, ScriptComputerPeripheral, öffentliche Lua-Hilfen sowie Verknüpfungen zwischen Referenzsymbolen und visuellen Funktionsknoten. Editorinterne Graphklassen sind keine Lua-API. |
| Copycat Architect 0.5.1 | `copycat_architect` und Voraussetzungen der Turtle-Erweiterung | ArchitectPeripheral, Upgrade-Vertrag, `architect`-Turtleprogramm und öffentliche Ergebnis-/Fehlerstrukturen. |
| Turtle Charging Station 1.7.0 | `turtlechargingstation` | ChargingStation- und ThunderchargeDynamo-Peripherals, Energie-/Ladeinformationen und Turtle-Bezug. |
| Create CC Integration 1.2.0 | `ccbr` | Im untersuchten Artefakt Rezept-/Materialintegration ohne eigene erkannte öffentliche Lua-Oberfläche. Als Integrationshinweis bei Einrichtung aufführen; keine erfundene API-Kategorie. |

### Maschinenmods mit eigener oder indirekter CC Integration

| Anbieter und Packversion | Teilbereiche und zusätzliche Bedingungen |
| --- | --- |
| Create 6.0.10 (`create`) | Drehzahl-/Stressmessung, Creative Motor, Sequenced Gearshift, Speed Controller, Nixie, Display Link, Sticker, Bahnstation/-signal/-observer, Logistik mit Packager/Repackager, Frogport/Postbox, Stock Ticker, TableCloth-Shop und Redstone Requester; auch Package-/Order-Luaobjekte. |
| Create Simulated 1.3.2 (`simulated`) | Im Bundle `aeronautics_bundled` eingebettet: Höhen-/Geschwindigkeits-/Gimbal-/Optiksensor, NavTable, Links, DockingConnector, Typewriter, NamePlate, SwivelBearing, TorsionSpring. Das Bundle enthält zusätzlich `aeronautics` und `offroad`; Freischaltung auf echte Modul-IDs und Fähigkeiten stützen. |
| Create Avionics 0.6.0 (`create_avionics`) | Zusammengesetzte Create-/Simulated-Peripherals, Bewegungs- und SCADA-Oberflächen, Aeronautics-Propeller/Gasversorgung/Kanone und Offroad-WheelMount. Überlagerungen mit nativer Create-/Simulated-API und TeutonLib erfassen. |
| Create Gadgets & Gizmos 1.0.6 (`createthrusters`) | Controller, Joystick, Lager, Thruster, Gearbox, Claw, Orientierung, Navigation, Seilwinde und WheelMount; Teilintegrationen und mitgelieferte `rom/thrusters`-Beispiele. |
| Create Propulsion: Simulated 1.1.4 (`createpropulsion`) | Thruster-/Vector-/Liquid-/Ion-/Creative-Varianten, TiltAdapter, RedstoneTransmission, StirlingEngine und CoralGenerator. |
| Create: Radars 0.4.9.4-1.21.1 (`create_radar`) | RadarBearing, PlaneRadar, Monitor sowie Fire-/Pitch-/YawController. Native APIs und Aeroworks-Zusätze getrennt ausweisen. |
| Create Tweaked Controllers 1.21.1-1.2.7 (`create_tweaked_controllers`) | TweakedLectern und Controllerwerte; zusätzliche NeoPeripheral-Hubs separat verknüpfen. |
| Create Diesel Generators 1.21.1-1.3.12 (`createdieselgenerators`) | ChemicalTurret-Peripheral und dessen tatsächliche Steuer-/Statusoberfläche. |
| Create: Power Grid 0.6.2 (`powergrid`) | Batterie, GeneratorClutch, Current-/Voltage-/PowerGauge, EnergyMeter, RedstoneConverter; Einheit und Vorzeichen pro Messwert dokumentieren. |
| FluidLogistics 1.3.1 (`fluidlogistics`) | Von Creates Computerverhalten abgeleitete FluidPackager-, FluidRepackager- und CopperFrogport-Oberflächen. Geerbte Methoden und tatsächliche Abweichungen prüfen. |
| Mekanism 10.7.19 (`mekanism`) | Generierte Computer-Methodenregister, Maschinen, Multiblocks, Filterobjekte, chemische Speicher und Security-Bedingungen. CC-Adapterklassen allein erfassen nicht den Umfang. |
| Mekanism Generators 10.7.19 (`mekanismgenerators`) | Eigene generierte Register für Generatoren, Fissions-/Fusionsreaktoren und Turbinen; zusätzlich Mekanism voraussetzen. |
| Immersive Engineering 12.4.2-194 (`immersiveengineering`) | Dynamisch erzeugte Peripherals und Callback-Registrierungen für unterstützte Maschinen. Vollständigkeit gegen die Callback-Registry prüfen. |
| PneumaticCraft 8.2.20 (`pneumaticcraft`) | PneumaticTilePeripheral und maschinenabhängig bereitgestellte Funktionen; Druck- und Einheitenschema. |
| Draconic Evolution 3.1.4.632 (`draconicevolution`) | FlowGate, ReactorComponent und EnergyPylon; gemeinsame Grundmethoden und gerätespezifische Erweiterungen. |
| Ender IO 8.2.10-beta (`enderio`) | Registriert `EIOBundledRedstoneProvider`. Das Anschlussverhalten gebündelter Redstone-Signale bei den bestehenden Redstone-Funktionen erklären; daraus keinen eigenen Peripheral-Methodensatz ableiten. |
| Extreme Reactors 1.21.1-2.4.9 (`bigreactors`) mit ZeroCore 1.21.1-2.4.21 (`zerocore`) | Reactor-, Turbine- und Energizer-Computerports über ZeroCore; Multiblockzustand und verschiedene Port-/Reaktortypen. ZeroCore ist dabei Infrastruktur, keine zusätzliche fachliche Doppelreferenz. |
| VS Sable Hose Connectors 0.2.2 (`vsfluidlink`) | CC-WiredConnection über Kupplungen als Netzwerktopologie dokumentieren. Daraus keinen unbewiesenen eigenen Lua-Methodensatz ableiten. |

Weitere Maschinen mit CCs generischen Inventar-/Tank-/Energiespeicherfähigkeiten verweisen auf diese gemeinsamen Verträge. Eine Mod bekommt nur dann eine eigene API-Seite, wenn sie eine zusätzliche öffentliche Oberfläche oder ein eigenes Anschlussverhalten besitzt. TFMG ist im maßgeblichen Pack deaktiviert. `cc_cbc`, `cbcperipheral` und andere nur historisch/deaktiviert vorliegende Addons sind kein aktiver Pflichtumfang; ihre späteren Dokumentationspakete folgen denselben Sichtbarkeitsregeln.

## 3 Navigation und Kategorien

Die Hauptnavigation ordnet nach Aufgabe. Der Anbieter ist ein zusätzlicher Filter. Jede API besitzt genau eine kanonische Seite; weitere Kategorien verlinken darauf.

| Hauptkategorie | Inhalt |
| --- | --- |
| Einstieg und Lua | Syntax, Typnotation, Globals, Standardbibliotheken, `require`, Programme starten, Beispiele lesen. |
| Computer und Betriebssystem | `os`, Shell, Multishell, Settings, Help, Startup, Gerätefamilien und Command Computer. |
| Dateien und Daten | `fs`, `io`, Datei-/Response-Handles, Text/JSON/NBT, Base64 und Datenstrukturen. |
| Terminal und Grafik | `term`, Farben, Fenster, Paintutils, Monitore, CC: Graphics, CC: Terminals, SVG, Schrift, Aeroworks-Displays und Touch. |
| Peripherie und Verbindungen | `peripheral`, Modems, Peripheral Links, Netzwerktopologie, Generic Peripherals, Docking-Verbindungen und CCVisual-Spitznamen. |
| Kommunikation und Position | Rednet, HTTP/WebSockets, GPS, Chat, Funk und Netzwerkereignisse. |
| Redstone und Steuerung | Redstone/Relay, Link-Bridges, Kanäle, Controls, Wires, Controller, Joysticks und Drive By Wire. |
| Create und Logistik | Kinetik, Produktionsmaschinen, Anzeigen, Item-/Fluidlogistik, Lager und Handelsobjekte. |
| Fahrzeuge und Physik | Sable/Sublevel, Simulated, Aeronautics, Avionics, Offroad, Propulsion, Thruster, Orientierung, Telemetrie, Docking und Züge. |
| Sensorik und Erfassung | Optical, Radar, Geo-/Player-/Environment-Sensoren, BlockReader und NFC. |
| Energie und Maschinen | Mekanism, Generatoren, IE, PneumaticCraft, Draconic Evolution, Extreme Reactors und Power Grid. |
| Turtles und Androids | Native Turtle-/Pocket-Funktionen, Upgrades, Automata, Androids, Architect und Laden. |
| Kolonien und Speicherbrücken | Colony4CC, AP-Colony-/ME-/RS-Bridges, Crafting und deren Rückgabeobjekte. |
| Ereignisse und Fehler | Globaler Ereigniskatalog, Fehlerprotokolle, typische Fehlermeldungen, Lifecycle und Diagnose. |

Die Startseite zeigt Suche, zuletzt gelesene Einträge, Favoriten, „An diesem Computer“ und die nichtleeren Kategorien. Programme, Beispiele und Typen sind eigene Inhaltsarten innerhalb dieses Baums. Der Pixel-Editor erscheint unter Grafik als Werkzeug und wird nicht zwischen Methoden einsortiert.

Suchfilter: Kategorie, Mod, Inhaltsart, Gerätetyp und „hier verfügbar“. Suchrangfolge: exakter qualifizierter Symbolname, exakter Kurzname, Alias, Titel, Beschreibung. Suche nach `getSpeed`, `Drehzahl` und `speed` soll dieselben relevanten Verträge finden. API-Namen und Code bleiben unverändert; Texte und Suchsynonyme werden lokalisiert, zunächst `de_de` und `en_us`.

## 4 Sichtbarkeit und tatsächliche Verfügbarkeit

### Zwei Ansichten

**Installierte APIs** ist die Standardansicht und zeigt den gesamten unterstützten Umfang der auf dem Server installierten Mods. Eine nicht angeschlossene Maschine verschwindet hier nicht. Eine Turtle-Funktion bleibt als Dokumentation sichtbar und trägt den Hinweis „Turtle erforderlich“.

**An diesem Computer** zeigt die an der aktuellen Computerinstanz verfügbaren Quellen, Host-APIs und Fähigkeiten. Verbundene Peripheral-Methoden werden mit der tatsächlich erkannten Oberfläche geschnitten. Externe ControlDesk-Aufrufe, eingebettete Pult-APIs und Display-/Touchhandler erhalten verschiedene Scopes. Ein normaler CC-Computer bekommt keine ausführbare `channels`-/`telemetry`-Oberfläche versprochen, nur weil ein Aeroworks-Katalog installiert ist.

### Bedingungen im Datenmodell

Jeder Eintrag kann Bedingungen auf Anbieter-, API-, Methoden-, Ereignis- und Beispielebene besitzen. Benötigt werden `allOf`, `anyOf`, Mod-ID mit Versionsbereich sowie registrierte Feature-/Capability-Flags. Eine Methode erbt die Voraussetzungen ihres Anbieters und ihrer API und darf sie weiter einschränken. Bedingungen bestehen aus Daten, nicht aus im Ressourcenpaket ausführbarem Code.

```text
Katalogsichtbar = CC vorhanden
               UND Anbieter vorhanden
               UND notwendige Zielmods vorhanden
               UND passender Dokumentationsvertrag ausgewählt

Hier verfügbar = Katalogsichtbar
               UND passender Host und Ausführungsscope
               UND nötige Fähigkeiten aktiviert
               UND erforderliche Methode/Quelle tatsächlich erkannt
```

| Zustand | Verhalten |
| --- | --- |
| CC:Tweaked fehlt | Kein CC-Reiter, kein Laden CC-abhängiger Adapter. TeutonLib bleibt ohne CC startfähig. |
| Anbieter- oder notwendige Zielmod fehlt | Betroffene Seiten, Methoden, Events, Beispiele, Vorschläge und Suchsynonyme vollständig ausblenden. Elternknoten ohne Kinder rekursiv entfernen. |
| Nur eine Unterintegration fehlt | Nur diesen Unterbereich ausblenden, beispielsweise AP-RS ohne Refined Storage oder Aeroworks-Radar ohne Create Radars. |
| Gerät fehlt am Computer | In „Installierte APIs“ dokumentiert; in „An diesem Computer“ nicht als angeschlossen darstellen. |
| Falscher Host oder Scope | Hinweis „nur Turtle“, „nur eingebetteter Pultcomputer“ oder „nur Displayskript“; im lokalen Verfügbarkeitsfilter ausblenden. |
| Feature durch Serverconfig deaktiviert | Installierte Dokumentation bleibt mit Status und Voraussetzung lesbar; lokale Aufrufe nicht als verfügbar markieren. |
| Installierte Version hat keinen geprüften Vertrag | Keine Signaturen einer anderen Version einsetzen. Nur belegte gemeinsame Inhalte und live erkannte Methodennamen mit Versionshinweis zeigen; keine erfundenen Parameter. |
| Unbekanntes Peripheral | Eigene Gerätekarte mit Typen, Verbindung und realen Methodennamen; „Signatur nicht dokumentiert“. Kein falscher Match über einen gleichnamigen Typ. |
| Gespeicherter Favorit oder Link führt zu ausgeblendetem Inhalt | Neutrale Meldung „In dieser Sitzung nicht verfügbar“ und Rückweg; keine verwaisten Seiten im Baum. Favorit lokal behalten. |

Beispiele und Querverweise werden nach derselben Filterung aufgelöst. Ein Beispiel, das mehrere Mods benötigt, erbt deren gemeinsame Bedingungen. Es darf nach Entfernen einer Mod nicht unter einer weiterhin sichtbaren allgemeinen Seite als unbrauchbarer Code stehen bleiben.

### Multiplayer und Aktualisierungen

Der Server liefert die maßgeblichen Modversionen und freigegebenen Fähigkeiten. Eine rein clientseitige `ModList.isLoaded()`-Prüfung genügt nicht. Auf dem Client vorhandene Zusatzmods dürfen keine serverseitige API vortäuschen. Clientbezogene Darstellung kann zusätzliche eigene Bedingungen haben.

Beim Sitzungsbeginn wird ein kompakter Verfügbarkeitsstand aufgebaut. Geräteänderungen, Reboot, Hostwechsel und relevante Configänderungen aktualisieren die lokale Ansicht. Ressourcenreload baut Dokumente und Suchindex atomar neu auf; Logout verwirft den Serversitzungsstand. Statische Dokumente werden nicht jede Sekunde mit den Quellen-/Kanal-Snapshots übertragen.

## 5 Lesefunktion im regulären CC UI

### Einstieg und Darstellung

Der vorhandenen CC-Seitenleiste wird ein einheitlicher Reiter **API** mit Buch-/Referenzsymbol hinzugefügt. Reihenfolge: Terminal, Quellen, Kanäle, API; Addons können ihre fachlichen Reiter einfügen. Der API-Einstieg wird genau einmal vom gemeinsamen Hostcontroller bereitgestellt. Kein neuer Computerblock und kein obligatorisches Item.

Die Referenz ist ein eigenes lokales Panel innerhalb des bestehenden Computerbildschirms. Sein Öffnen schließt oder ersetzt das Container-Menü nicht. Damit bleiben Computeridentität, Inventarslots und vorhandene serverseitige Menüprüfung erhalten. Auf kleiner Fläche zeigt sie zunächst Listen und anschließend den Inhalt; bei ausreichend Platz stehen Navigation und Lesebereich nebeneinander. Lange Signaturen und Codeblöcke bekommen horizontales Scrollen oder gezieltes Umbruchverhalten.

```text
CC Seitenleiste | API suchen                         [Hier verfügbar]
Terminal       | Einstieg       > term > getSize
Quellen        | Grafik          term.getSize() -> width, height
Kanäle         | Verbindungen    Beschreibung und Voraussetzungen
API            | Create          Parameter | Rückgaben | Beispiel
               | Fahrzeuge       [Signatur kopieren] [Beispiel kopieren]
               | Ereignisse      Verwandte APIs und Ereignisse
```

Die zusätzliche Zeile darf bei hoher GUI-Skalierung nicht außerhalb des Fensters verschwinden. Der gemeinsame Controller muss verfügbare Seitenleistenhöhe berücksichtigen und bei Bedarf eine kompakte oder scrollbar erreichbare Leiste verwenden.

### Bedienung

- Suche, Zurück/Vorwärts, Breadcrumbs, Favoriten, letzte Position und Sprung zu Methoden-/Typ-/Ereignis-IDs.
- Ein Info-Einstieg in der Quellenansicht öffnet die passende Referenz mit konkretem Gerätenamen, Anschlussart und beobachteten Methoden. Dokumentationsanbieter und tatsächlicher Geräteanbieter bleiben sichtbar.
- Textauswahl und Kopieren von Signaturen, Beispielen und symbolischen Links. Code wird beim Kopieren nicht automatisch gestartet oder an das Terminal gesendet.
- Während Suche oder Referenz fokussiert sind, gehen Tastatureingaben nicht an CraftOS. Tab-Navigation und Narration gehören zu den Bedienelementen.
- Escape verlässt zuerst eine Detail-/Suchansicht, danach das API-Panel zum zuvor aktiven Computerbereich. Erst der reguläre Schließvorgang beendet das Menü. Die Inventartaste schließt kein fokussiertes Suchfeld versehentlich.
- Wechsel zurück zum Terminal stellt dessen Fokus wieder her. Referenz, Auswahl und Scrollposition bleiben bei Resize, GUI-Skalierung und Ressourcenreload erhalten, soweit die Eintrags-ID weiter existiert.
- Öffnen und Lesen funktionieren auch bei ausgeschaltetem Computer; „hier verfügbar“ zeigt dann einen klaren Status statt Methoden durch Ausführung zu ermitteln.

`[computerUi] enabled` bleibt die bestehende Steuerung der automatischen nativen UI-Erweiterung. Addonhosts behalten ihre Host-Policy. Eine neue Einstellung `apiReference.enabled` kann den Referenzeinstieg zusätzlich abschalten; sie darf weder Quell-/Kanalverwaltung noch Lua-APIs verändern.

### Unterstützte Hosts

Zur Gesamtumsetzung gehören normale und Advanced-Blockcomputer, Turtle, Pocket und Command Computer sowie die installierten CC-Aeroworks-, CC:Terminals-, Android- und CCVisual-Computeroberflächen. Pro Host ist zu prüfen, ob er `AbstractComputerScreen`/`AbstractComputerMenu` nutzt oder einen eigenen Adapter benötigt. Ein sichtbarer CC-Bildschirm allein belegt noch keinen gültigen Backend-Host.

Für eigene Addons gibt es eine öffentliche clientseitige Einstiegshilfe und einen Hostvertrag. Die bestehende [UI-Vorlage](../../examples/computer-ui/README.md) wird daran angepasst. Addonregistrierung darf weder einen zweiten Referenzkatalog noch eine zweite UI-Leiste erfordern.

TeutonLibs bestehende `ComputerRuntime.startup()` registriert Pocket-/Turtle-Computer derzeit ausdrücklich nicht im aktiven Workspace-Runtime-Verzeichnis. Für deren Referenzkontext muss daher ein passender Host-/Sitzungsadapter entstehen. Die Dokumentationsanbindung darf nicht beiläufig bestehende Kanalzuständigkeiten oder portable APIs erweitern.

## 6 Inhalt eines Referenzeintrags

| Feld | Verbindlicher Inhalt |
| --- | --- |
| Identität | Stabile Anbieter-/Vertrags-/Symbol-ID, Name, Namespace, Aliase und Inhaltsart. IDs bleiben unabhängig von Sprache, Gerätenamen und Sortierung. |
| Geltung | Modversion/Vertragsbereich, zusätzliche Mods, Featureflags, Computerfamilie, Scope, Zugriffspfad und bevorzugte API. |
| Aufruf | Global, `require(...)`, `peripheral.find`/`wrap`, zurückgegebenes Handle, Objektmethode oder Handler. Punkt-/Doppelpunktnotation ausdrücklich zeigen. |
| Signatur | Überladungen, benannte Parameter, Typen, Optionalität, Standardwerte, Varargs und geordnete Mehrfachrückgaben. |
| Werte | Enums, Konstanten, erlaubte Bereiche, Einheiten, Vorzeichen, Koordinatenursprung, lokale/Weltkoordinaten und Indexbasis. |
| Datenmodelle | Benannte Tabellen-/Objekttypen mit Feldern, optionalen Feldern, Arrays/Maps und verlinkten Handle-Methoden. |
| Verhalten | Effekte, Yield-/Async-Verhalten, Thread-/Tick-Anforderungen, Lebensdauer, Attach/Detach und vorhandene Rechtebedingungen. |
| Fehler | Geworfener Lua-Fehler gegenüber `nil, reason`/`false, reason`; typische Ursachen und Rückkehr zum gültigen Zustand. |
| Ereignisse | Exakter Name, Scope/Quelle, geordnete Payload, Auslösebedingungen und zugehörige Subscription-/Filterbeispiele. |
| Beispiele | Kleines gültiges Beispiel mit vollständigem Zugriff/Import, Voraussetzungen und erwarteter Ausgabe; längere Rezepte separat. |
| Pflege | Quellenpfad/Quellversion oder Hash, geprüfte Registrierung, Dokumentationsversion und Ersatz bei veralteten Symbolen. |

`integer` bezeichnet in der Dokumentation eine ganzzahlige Erwartung, ohne einen zusätzlichen Lua-Zahlentyp zu behaupten. Farb-/Colour-Aliase verlinken auf denselben Vertrag. Gleichnamige Methoden verschiedener Anbieter erhalten getrennte Verträge.

Einträge für TeutonLibs SVG dokumentieren beispielsweise die aktuelle Einschränkung bei üblichen `path`-Elementen, statt die Unterstützung des gesamten SVG-Standards zu versprechen. Bei Bewegungsschnittstellen sind Grad/Radiant und lokale/Weltkoordinaten Pflichtangaben. Bei Grafik werden Terminalzellen, Pixel und normierte Koordinaten getrennt beschrieben.

## 7 Architektur in TeutonLib

### Schichten und vorgesehene Dateien

Alle folgenden neuen Namen sind Entwurf. Sie werden vor Implementierung gegen bestehende öffentliche API-Namenskonventionen geprüft.

```text
src/main/kotlin/de/TeutonStudio/TeutonLib/
  api/documentation/                 neutrale Dokumenttypen und Registrierung
  api/computer/documentation/        optionale CC-Host-/Symbolanbindung
  api/computer/client/               öffentlicher Client-Einstieg
  integration/documentation/         Laden, Versionen, Bedingungen, Index
  integration/computer/documentation/ Hosts, Capability-Snapshots, Liveabgleich
  client/computer/documentation/     Referenzpanel, Navigation, Code und Suche

src/main/resources/assets/teutonlib/cc_reference/
  providers/                        Anbieter, Verträge und Voraussetzungen
  apis/                             strukturierte Methoden- und Typdaten
  events/                           Ereignisverträge
  guides/                           Einrichtung und Beispiele
  locales/de_de/                    deutsche Texte
  locales/en_us/                    englische Texte

tools/                              Import und Abdeckungsbericht
```

Die neutralen Dokumenttypen dürfen weder `net.minecraft.client.*` noch Klassen optionaler Mods oder CCs `ServerComputer` enthalten. CC-spezifische Bindings liegen in der bestehenden optional geladenen Integrationsschicht. Java-/Kotlin-Registrierung von Addons ergänzt Ressourcen, ohne den Reader zu besitzen.

TeutonLib bleibt unabhängig von CC-Aeroworks-Klassen. Aeroworks und andere eigene Addons liefern Dokumentationsdaten und Capability-Metadaten über öffentliche TeutonLib-Verträge. Für Fremdmods ohne TeutonLib-Anbindung werden geprüfte Dokumentationspakete in TeutonLib ausgeliefert, ohne deren Laufzeit-JARs einzubetten.

### Katalog und Registrierung

Geplant sind neutrale Modelle für `DocumentationProvider`, `ApiContract`, `Symbol`, `LuaType`, `EventContract`, `Example` und `Requirement`. Der Root bekommt eine Schemaversion. Jeder Anbieter besitzt einen eigenen Namespace und mehrere versionsgebundene Verträge. Ressourcenüberschreibungen brauchen eine eindeutige Priorität; konkurrierende Addon- und eingebaute Pakete für denselben Vertrag werden diagnostiziert und deterministisch aufgelöst.

Beispiel einer konzeptionellen Symbol-ID: `computercraft:term/getSize`. Ein lokaler Link ergänzt getrennt die aktuelle Quellen-ID und den Sitzungskontext. Quellen-Spitznamen oder automatisch vergebene Peripheral-Namen werden nie Teil der kanonischen Dokumentations-ID.

Ressourcen enthalten nur deklarative Daten. Übersetzungen referenzieren stabile Textschlüssel. Ressourcenreload validiert Schema, Eindeutigkeit, Querverweise und Bedingungen, bevor er den alten Katalog ersetzt. Ein defektes Drittanbieterpaket wird isoliert und mit konkretem Pfad diagnostiziert.

### Wiederverwendung der Quellenansicht

`SourceMethod` bleibt ein kompakter Kompatibilitätsvertrag. Eine zusätzliche, getrennte Zuordnung von Quelle/Methode zu Referenz-ID liefert strukturierte Details. Bestehende Drittanbieter-Provider müssen keine neuen Pflichtfelder implementieren. Der heutige Info-Renderer kann ohne zugeordneten Eintrag weiterhin reale Methodennamen zeigen.

Bei verbundenen Geräten wird das Dokumentationsangebot mit dem tatsächlichen Methodensatz geschnitten. Der Match verwendet Anbieter, registrierten Vertrag, Peripherietypen und aktive Erweiterungen. Ein Match nur auf `getSpeed` oder einen Typnamen wie `radar` ist unzulässig. Avionics-, Gadgets-, TeutonLib- und native Methoden können sich auf einem Gerät ergänzen oder überschreiben; die Referenz zeigt die effektiv verfügbare Variante und deren Herkunft.

Auch generische Sources, geerbte Funktionen und `IDynamicPeripheral`/dynamische Lua-Objekte gehören in den Abgleich. Host-Globals und zurückgegebene Handles werden über explizite Verträge/Registrierung erfasst; ein Peripheral-Methodensupplier deckt sie nicht vollständig ab.

### UI Integration ohne Protokollbruch

Ein gemeinsamer Hostcontroller verwaltet die Auswahl `TERMINAL`, `SOURCES`, `CHANNELS`, `DOCUMENTATION`. Er übersetzt nur Quellen/Kanäle in die vorhandenen Workspace-Seiten 1/2. Das Dokumentationspanel bekommt seinen eigenen lokalen Zustand und seine eigene Aktualisierung. Es werden keine Dokumentations-Snapshots als Kanalbäume verpackt.

Die native Mixin-Integration und die öffentliche Addon-UI-Vorlage verwenden denselben Einstieg. Die Aeroworks-Deskriptoren liefern nur Kontext und fachliche Ergänzungen. Eine Standardreferenz darf nicht mit einem zusätzlichen `ComputerWorkspaceClientDescriptor` die bestehende Addonauflösung verdrängen.

### Netz und Laufzeit

Die Serveranbindung liefert einen versionierten, begrenzten Fähigkeitsstand und optional die Zuordnung einer bereits zulässigen Quelle. Menünummer, Sitzung, aktuelle Computeridentität, Menügültigkeit und Zugriff werden wie bei den bestehenden Workspace-Payloads erneut geprüft. Antworten einer alten Sitzung werden verworfen.

Statische Inhalte sind lokal. Von Addons mitgelieferte Ressourcen werden über den regulären Ressourcenmechanismus bereitgestellt; serverseitige Ergänzungen benötigen bei Bedarf ein ausdrücklich versioniertes, begrenztes Datenformat. Bei fehlender kompatibler Gegenseite bleibt der lokale Reader nutzbar, kennzeichnet den Serverstatus als unbekannt und erfindet keine lokale Verfügbarkeit.

Lesen und Suchen dürfen keine Gerätemethoden aufrufen, Maschinen konfigurieren, Module per `require` ausführen, Computer starten, Chunks laden oder Peripherals erneut anhängen. Liveabgleich verwendet vorhandene Attachments und passive Methodendeskriptoren. Erkenntnislücken bleiben sichtbar, bis ein geprüfter Adapter sie füllt.

## 8 Pflege und Vollständigkeitsprüfung

### Import und Quellen

1. Das Lockfile und rekursive Modmetadaten liefern Anbieter, Versionen, Hashes und eingebettete Modul-IDs.
2. CCs ROM, BIOS, native APIs und öffentliche Modquellen liefern die tatsächlichen Symbole. Nur bei fehlenden Quellen werden Klassenmetadaten/Registrierungen ausgewertet.
3. Lua-Exports werden einschließlich Tabellen, Objektmethoden, Aliassen und umgeleiteten Globals erfasst. Kotlin-/Java-Exporte brauchen Vererbung, `@LuaFunction`-Aliase, dynamische Registrierungen und zurückgegebene Handles.
4. Frameworks erhalten eigene Extraktoren: Mekanism-Computerregister, IE-Callbacks, ZeroCore-Verträge, AP-Plugins und Create-Peripherieverhalten. Maschinen ohne direkte CC-Imports dürfen dadurch nicht verschwinden.
5. Die fachliche Redaktion ergänzt Typen, Einheiten, Semantik, Fehler, Eventpayloads und Beispiele. Java-Reflection allein kann beispielsweise aus `MethodResult` keinen korrekten Lua-Rückgabetyp ableiten.
6. Upstream-Texte werden nur entsprechend ihrer jeweiligen Lizenz übernommen; Lizenz-/Quellhinweise werden mit ausgeliefert. Bei ungeklärter Textübernahme werden eigene Beschreibungen und Quellenverweise verwendet.

Alle Schritte sind offline aus den gepinnten Entwicklungsartefakten reproduzierbar. Der Release-Build lädt keine laufend veränderlichen Online-Dokumente nach. Zur Laufzeit wird kein Modordner vollständig nach Klassen durchsucht.

### Abdeckungsbericht

Pro Anbieter/Vertragsversion werden erkannte öffentliche Symbole, vollständig dokumentierte Symbole, begründet interne Symbole, unbekannte Symbole und verwaiste Dokumentation ausgewiesen. Methoden, Aliase, Typen, Handle-Methoden und Ereignisse werden getrennt gezählt.

Ein Release mit Anspruch auf vollständige Referenz hat für alle unterstützten aktiven Anbieter **keine ungeklärten öffentlichen Symbole und keine verwaisten Verträge**. Bekannte, zur Laufzeit eingeschränkte APIs gelten mit korrekt dokumentierter Einschränkung als abgedeckt. Unbekannte Drittanbieter nach einem Packupdate können weiterhin live angezeigt werden, verhindern aber die neue Vollständigkeitsfreigabe für diesen Packstand.

CI prüft Semantik und Registerzuordnung statt nur das Vorkommen eines Funktionsnamens als String. Generierte Symbolinventare und redaktionelle Inhalte bleiben getrennt; Generatoren überschreiben keine Beschreibungen. Packupdates erzeugen einen nachvollziehbaren API-Diff mit hinzugefügten, geänderten und entfernten Symbolen.

## 9 Ablösung des Aeroworks API Buchs

Die Migration beginnt erst nach einem funktionsfähigen TeutonLib-Reader und öffentlichem Anbieterformat. Aeroworks bleibt Eigentümer seiner fachlichen API und liefert deren Dokumentation als Anbieterpaket. So muss TeutonLib bei späteren Aeroworks-API-Änderungen nicht gegen Aeroworks kompiliert werden.

| Bisheriger Inhalt | Neues Ziel |
| --- | --- |
| API-Katalog, Scopes, Typnotation | Gemeinsamer Referenzkatalog und Typseiten. |
| Einstieg, Computer, Module und Bedienhinweise | Aeroworks-Anleitungen innerhalb der Referenz. |
| Netzwerk, Kanäle, Controls, Wires und Telemetrie | Entsprechende APIs samt Beispielen und Scopes. |
| Displays, Touch, Ereignisse und Fehler | Gemeinsame Fachkategorien mit Aeroworks-Verträgen. |
| Simulated-Docking, Radar und Dashboard/CTF | Bedingt sichtbare Integrationsseiten. |
| Pixel-Editor | Werkzeug unter Grafik, mit derselben real konfigurierten Rastergröße, Vorschau und kopierbarem Lua-Code. |
| Fallback-Buchinhalt und ältere Abschnittslinks | Stabile Weiterleitungen auf neue Eintrags-IDs; soweit nötig kompakter Fallback bei noch inkompatibler Gegenstelle. |

Zuerst öffnet `cc_aeroworks:guide_book` die neue Referenz mit dem Aeroworks-Einstieg. Auch ein Öffnen aus dem Item muss ohne laufendes Computermenü möglich sein; dann wird nur die installierte Dokumentation ohne lokales Gerät dargestellt. Die bestehenden Registrierungs-IDs bleiben erhalten, damit Items in Inventaren und Welten nicht verschwinden.

Nach Inhaltsparität verschwinden doppelte Buch-/Referenzreiter aus den Computeroberflächen. Die eigenständige Aeroworks-Readerimplementierung und ihre parallele Inhaltsquelle werden entfernt. Das alte Item kann als optionaler Einstieg bestehen bleiben; sein Rezept und seine kreative Sichtbarkeit können anschließend entfallen. Für API-Nutzung und Dokumentation ist es dann vollständig entbehrlich.

Die bisherige Aeroworks-Abdeckungsprüfung wird auf das Anbieterpaket umgestellt und um fehlende mobile/Admin-/CTF-/Handle-Verträge erweitert. Anleitungen, Pixel-Editor, Übersetzungen, Beispiele und Menü-Rückkehr gehören zur Ablöseabnahme. Create-Ponder-Szenen bleiben erreichbar und werden bei Bedarf verlinkt.

## 10 Umsetzung in prüfbaren Phasen

| Phase | Ergebnis | Abnahme |
| --- | --- | --- |
| 1 Öffentliche Oberflächen | Gepinntes Anbieter-/Symbolinventar für alle Tabellen oben; genaue Mod-IDs, Scopes, zusätzliche Bedingungen, Konflikte und Ausschlüsse. | Jede relevante Registrierung ist einem Vertrag zugeordnet; indirekte und eingebettete Anbieter sind berücksichtigt. |
| 2 Daten und Bedingungen | Neutrales Schema, Registrierungen, Versionenauswahl, Lokalisierung, Ressourcenloader, Schema-/Link-/Gateprüfung und Suchindex. | Entfernte Mods hinterlassen keine Inhalte; inkompatible Versionen erhalten keine falschen Signaturen; Third-party-Pakete können ergänzt werden. |
| 3 Reader und native CC Hosts | API-Reiter, Reader, Suche, Verlauf, Kopieren, Quellenverknüpfung und Fähigkeitsstand. Zunächst Blockcomputer, danach Turtle/Pocket/Command. | Native Hosts und ausgeschaltete Computer funktionieren ohne Aeroworks; Fokus, Resize und Menülebenszyklus bestehen. |
| 4 CC Basis und TeutonLib | Vollständige CC-/Lua-/TeutonLib-Dokumentation einschließlich Events, Handles, Programme und Grafikhilfen. | Automatischer Symbolabgleich ohne offene öffentliche Lücken; Beispiele unter 1.120.2 geprüft. |
| 5 Alle Packintegrationen | Dokumentationspakete für sämtliche aktiven Addons und Maschinenanbieter, zusammengesetzte APIs und Hostadapter für Androids, Terminals, CCVisual und Aeroworks. | Vollpack-Abdeckung und isolierte Tests der Unterintegrationen; keine reine Modnamensprüfung für optionale Bridges. |
| 6 Aeroworks Übergang | Anbieterpaket, Inhalts-/Pixel-Editor-Migration, Buchweiterleitung und Entfernen doppelter Reader. | Sämtliche bisherigen Buchfunktionen im neuen System erreichbar; Altitems und Menüs funktionieren weiter. |
| 7 Packfreigabe | Aktualisierte UI-Vorlage, externe Entwicklerdokumentation, Abdeckungsbericht und versionsgebundene Release-Artefakte. | TeutonLib allein, CC-Minimalprofil, Multiplayer und TerrArchitecture-Vollprofil geprüft. |

Phase 5 ist Bestandteil des vollständigen Auftrags. Der Abschluss von Basisreader oder CC-Kern allein ist keine vollständige Packreferenz. Änderungen an den bestehenden optionalen Abhängigkeitsbereichen werden erst passend zum tatsächlich verwendeten API-Vertrag vorgenommen.

## 11 Prüfplan und Fertigkriterien

### Inhalt und Bedingungen

- Öffentliche Symbole gegen die gepinnten CC-/Modregistrierungen abgleichen; Aliase, geerbte/generische/dynamische Methoden, zurückgegebene Handles und Events separat prüfen.
- Alle Links, Kategorien, Übersetzungsschlüssel und Beispiele validieren. Fehlende Zielmod auf API-, Methoden-, Ereignis- und Beispielebene testen; Filterung muss vor Suche/Indexierung greifen.
- Paarweise Unterintegrationen prüfen: AP ohne AE2/RS/MineColonies/Powah, NeoPeripheral ohne einzelne Ziele, Aeroworks ohne Radar/CTF/DbW/Simulated, Grafik ohne CCGraphics/Terminals/Aeroworks. Diese Fälle benötigen gegebenenfalls getrennte minimal lauffähige Modsets, statt unerlaubt Pflichtmods eines Addons zu entfernen.
- Beide Redstone-Link-Implementierungen sowie native/Avionics/Gadgets/TeutonLib-Kompositionen auf Namen, Herkunft und tatsächlichen Methodensatz prüfen.
- Eingebaute CC-Basisfunktionen bleiben sichtbar, wenn nur die dokumentierende Erweiterungsmod fehlt. Nach Entfernen von `ccgraphics` verschwinden deren Ergänzungen, nicht die Basisseite `term`.

### UI und Laufzeit

| Profil oder Fall | Erwartung |
| --- | --- |
| TeutonLib ohne CC | Sauberer Client-/Dedicated-Serverstart ohne CC-Klassenladefehler. |
| TeutonLib mit CC, ohne Create/Aeroworks | Basisreferenz und unabhängige Lua-Grafikmodule nutzbar, keine fremden leeren Kapitel. |
| Alle unterstützten Hostfamilien | Referenzeinstieg genau einmal, korrekter Scope, intakte Menüidentität und Rückkehr zum Terminal. |
| Normal/Advanced, GUI-Skalierungen und kleine Fenster | Lesbare Navigation, erreichbare Reiter, vollständige Code-/Signaturanzeige, Tastaturbedienung und Narration. |
| Zwei Spieler an unterschiedlichen Computern | Keine Übernahme von Quellen, Favoritenkontext oder Antworten der anderen Computersitzung. |
| Falsche/alte Sitzung, geschlossenes Menü, Hostentfernung | Keine Liveinformationen oder Aktionen für ungültige Computer. Rein statisches Lesen kann ohne Gerätebezug fortgesetzt werden. |
| Attach/Detach, Modem/Link, Reboot, Bewegung und Dimension | Lokale Methoden-/Quellenzuordnung wird korrekt aktualisiert, ohne Maschinenaufrufe oder Chunkloads. |
| Server-/Clientbestand verschieden, fehlender Protokollpartner | Serverstatus maßgeblich beziehungsweise ausdrücklich unbekannt; kein vorgetäuschter Liveumfang. |
| Ressourcenreload, Sprache, Resize und UI-Configwechsel | Katalogtausch ohne Mischzustand, gültige Navigation erhalten, Terminalfokus und Menü bleiben korrekt. |
| Vollpack | Alle unterstützten Anbieter und nach Modvoraussetzungen sichtbaren Unterbereiche vorhanden; Suche und Scrollen ohne wiederholte Vollscans. |
| Altes Aeroworks-Buch | Öffnen aus Inventar und Computer führt zur neuen Referenz; Pixel-Editor und bisherige Anleitungen bleiben erreichbar. |

Statische Struktur-/Bedingungsprüfungen, automatisierte Clientprüfungen und echte In-World-/Multiplayerprüfungen werden im Freigabebericht getrennt ausgewiesen. Für Beispiele mit schreibenden Funktionen werden isolierte Testwelten und gezielt vorbereitete Geräte genutzt.

Fertig ist die Erweiterung, wenn die CC-UI ohne Buch alle installierten APIs auffindbar macht, die oben inventarisierten Anbieter vollständig dokumentiert sind, fehlende Mods einschließlich aller abhängigen Inhalte konsequent ausgeblendet werden und das Aeroworks-Buch keinen eigenen benötigten Inhalt oder Reader mehr besitzt.

## 12 Nachweis dieser Planung

Dieser Plan beruht auf den lokalen TeutonLib-/Aeroworks-/TerrArchitecture-Quellen, dem aktuellen Lockfile, rekursiver JAR-/ROM-Inspektion und den verlinkten offiziellen CC-Referenzen. Das beigefügte Inventar hält Artefakte, Hashes, Kandidatenklassen, ROM-Ressourcen und Scanlücken fest. Die Bestandsaufnahme führt keine Peripheral-Funktionen aus und bestätigt noch keine vollständige Methodenbeschreibung. Implementierung, Builds und die oben geplanten Laufzeitprüfungen sind nachfolgende Arbeit.
