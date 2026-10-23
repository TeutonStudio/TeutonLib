# Quellen und Kanäle für CC:Tweaked

TeutonLib ergänzt normale und erweiterte Blockcomputer um die Reiter **Quellen** und **Kanäle** mit Bildsymbolen in der nativen CC-Seitenleiste. CC:Tweaked genügt; Create wird nur für den Peripheral Link benötigt. Pocket Computer und Turtles erhalten diese Reiter nicht.

## UI ein- und ausschalten

In `config/teutonlib-client.toml` auf jedem Client:

```toml
[computerUi]
    enabled = true
```

Standard ist `true`. Mit `false` entfallen die automatisch ergänzten Quellen-/Kanalreiter
an CC-Blockcomputern und in der Beispielvorlage. Nach Config-Neuladen wird auch ein
offener Bildschirm aktualisiert und zum Terminal zurückgesetzt. Ohne Dateiüberwachung
gilt die Änderung beim nächsten Clientstart. Kanaldaten, Lua-APIs und Peripheral Links
bleiben verfügbar. Eigene Addon-Workspaces wie die Pultoberfläche von CC Aeroworks
werden vom jeweiligen Addon gesteuert.

## Quellen

Die Quellen sind nach lokalem Anschluss, Kabelverbindung und Peripheral Link gruppiert. Ein Klick auf einen Gruppenbalken klappt ihn ein oder aus; ein Klick auf ein Gerät öffnet dessen Details. Linkgeräte erhalten eine kurze Typ-/Linkbezeichnung, während die vollständige Lua-Adresse in den Details und im Tooltip erhalten bleibt.

Die Liste zeigt dieselben Peripheral-Adressen wie die Computer-API: lokale Seiten, Kabelgeräte und Peripheral Links. Geräte werden nicht erneut angehängt. Typen, Verbindungsart und bekannte Block-/Linkpositionen stehen in der Detailzeile und im Tooltip. Verschiedene Adressen bleiben getrennt. Änderungen werden bei geöffnetem Reiter einmal pro Sekunde abgefragt.

Die eigene Quellenkategorie **Redstone · intern** enthält automatisch die sechs normalen CC-Redstone-Eingänge `front`, `back`, `left`, `right`, `top` und `bottom` mit ihren aktuellen Analogwerten (0–15). Sie entsprechen `redstone.getAnalogInput(side)` und benötigen weder Drive By Wire noch benannte Eingangskanäle. Unter **Ausgänge** stehen ausschließlich die vorhandenen benutzerdefinierten Ausgangskanäle, einschließlich der vom Addon bereitgestellten Ausgänge. Diese werden weiterhin im Kanal-Reiter verwaltet. Die Kategorie erscheint auch in Addon-Workspaces.

Normale Computer und CC-Aeroworks-Steuerungseinheiten verwenden dieselben aufklappbaren Listen und API-Fenster. Die Steuerungseinheit ergänzt Display-Links, GPS, Display-Skripte und ihre Steuerungsdaten. Das Info-Symbol rechts an einer Quelle öffnet deren Lua-Methoden mit vorhandener Dokumentation. Gruppen und Detailzeilen erhalten kein Info-Symbol. Escape oder das Schließsymbol kehren zur bisherigen Liste zurück.

Kabel-, Funk- und Endermodems erscheinen einzeln mit Adresse und Modemart. Ihr API-Fenster zeigt Anschluss, bekannte Position, Dimension sowie bei Funk/Ender Reichweite und dimensionsübergreifende Fähigkeit. Kabelperipherie bleibt separat. Die Anzeige führt keine Lua-Methode aus und sendet keine Erkennungspakete.

## Kanäle

Ein Computer besitzt bis zu 64 benannte Kanäle. Namen enthalten 1–32 ASCII-Buchstaben, Ziffern, `_`, `.` oder `-`; `world` ist für DbW reserviert. Ein neuer Kanal ist **privat**.

- **Ausgang:** Ganzzahliger Wert 0–15, lokal per Lua oder mit den Werttasten veränderbar. DbW-Kabel können ihn als Quelle auswählen; Scrollen mit dem Kabelwerkzeug wechselt zwischen Ausgängen.
- **Eingang:** Benannter Lesezugriff auf das DbW-Signal einer Computerseite (`front`, `back`, `left`, `right`, `top`, `bottom`). Mehrere Namen auf derselben Seite lesen dasselbe physische Signal. Eingänge lassen sich nicht beschreiben.

Der Kanal-Reiter zeigt links die aufklappbaren Gruppen **Ausgänge**, bestehende **Eingänge** und gegebenenfalls Erweiterungen. Werte erscheinen als Zahl. Rechts befindet sich der gemeinsame Aktionsbereich: **Neuer Kanal** beginnt einen benannten Ausgang; Namen eingeben, anschließend **Anlegen** drücken. Bei leerer Auswahl ist das Anlegen direkt verfügbar. Seiteneingänge können weiterhin über Lua definiert werden.

Das API-Fenster zeigt Argumentnamen, Lua-Typen und Rückgabetypen aus registrierter Dokumentation oder den annotierten Java-/Kotlin-Methoden. Dynamische Argumente und Rückgaben ohne Typdokumentation erscheinen als `unknown`; interne Kontextparameter werden ausgeblendet. Die Erkennung ruft keine Gerätemethode auf.

Ein Klick auf einen vorhandenen Kanal öffnet dessen Einstellungen. Zum Umbenennen den Namen im Editor ändern und **Umbenennen** drücken. Die Schaltfläche **öffentlich** beziehungsweise **privat** schaltet die Sichtbarkeit um. **−/+** ändert den Ausgangswert. **Löschen** löscht erst beim zweiten Klick; physische Verbindungen des gelöschten Ausgangs werden entfernt. Umbenennen übernimmt dessen DbW-Verbindungen.

Die Gestaltung verwendet CCs Computergehäuse und die Bildsymbole der Aeroworks-Seitenleiste. Die Monitoransicht bleibt bei normalen Computern monochrom; erweiterte Computer verwenden dezente Farbakzente. Escape führt zum Terminal zurück.

Ohne Drive By Wire funktionieren Kanaldefinitionen, Ausgänge und Fernabfragen weiter. Eingänge liefern dann 0 und die Oberfläche kennzeichnet das fehlende Backend. Getestete DbW-Schnittstelle: 0.3.0.

Definitionen, Sichtbarkeit und Ausgangswerte liegen in den Blockdaten. Ausschalten und Entladen setzen keine Ausgangswerte zurück. Nach Laden oder Neustart werden gespeicherte Werte wieder an DbW übergeben. Entladene Geräte werden nicht eigens geladen.

## Lua

```lua
local channels = require("teutonlib.channels")
channels.create("throttle", "output")
channels.set("throttle", 12)
channels.setPublic("throttle", true)
channels.create("sensor", "input", "top")
print(channels.read("sensor"))
channels.rename("throttle", "engine")
channels.remove("sensor")
```

`list()` liefert alle eigenen Kanalbeschreibungen und als zweiten Rückgabewert 0. Beschreibungen enthalten `name`, `direction`, `side`, `public`, `value`, `connections` und `provider`. `connections` zählt bei eigenen Ausgängen die DbW-Kabelziele.

```lua
local visible, privateCount = channels.list(5)
print("Private channels:", privateCount)
print(channels.read(5, "engine"))
-- Ein bekannter privater Name kann ebenfalls gelesen werden:
print(channels.read(5, "secret_signal"))
```

Für Fernabfragen benötigen **beide laufenden Computer** ein erreichbares Peripheral vom Typ `modem`, lokal, per Kabel oder per Peripheral Link. Rednet muss nicht laufen und kein Modemkanal muss geöffnet sein. Entfernung ist unbegrenzt, die Dimension muss übereinstimmen. Nicht verfügbare und doppelt belegte Computer-IDs erzeugen einen Lua-Fehler. Es gibt keinen Fernschreibzugriff.

**Privat bedeutet nicht auflistbar, nicht zugriffsgeschützt:** `list(id)` überträgt weder Namen noch Beschreibungen privater Kanäle, sondern nur ihre Anzahl. `read(id, name)` erlaubt das Lesen bei bekanntem Namen.

CraftOS-Befehle:

```text
channels             # eigene Kanäle
channels 5           # öffentliche Kanäle von Computer 5
channels 5 engine    # bekannten Kanal lesen
```

Bei `channels 5` steht die Anzahl nicht angezeigter privater Kanäle in der letzten Zeile. CC Aeroworks behält seinen bestehenden `channels`-Alias; dort kann die neue Funktion explizit über `rom/programs/channels.lua` aufgerufen werden. Eigene Betriebssysteme können das native Modul direkt mit `require` laden.

Lokale Änderungen erzeugen `teutonlib_channels_changed`. Fremde Computer werden nicht aktiv benachrichtigt; ein Abruf liest den aktuellen Wert.

## Schnittstellen für CC Aeroworks und andere Erweiterungen

Die öffentlichen Typen liegen unter `de.TeutonStudio.TeutonLib.api.computer`:

- `ComputerContext`: ID, Serverlevel, Position, bereits vorhandene Peripherals und Ereigniszustellung. Auf dem Serverthread verwenden; übergebene Peripherals sind geliehen und werden vom Provider nicht angehängt.
- `SourceProvider` / `SourceView`: zusätzliche Quellen über `ComputerExtensions.registerSources(...)` registrieren.
- `ChannelProvider` / `ChannelView`: zusätzliche Kanalansichten über `registerChannels(...)` registrieren. Fremde Kanäle sind in der gemeinsamen Verwaltung schreibgeschützt, nehmen aber gemäß `published` an Fernabfragen teil. Anbieter behalten ihre Wertebereiche und Zuständigkeit; Namenskollisionen gewinnen vorhandene TeutonLib-Kanäle. Anbieter sollten deshalb eigene eindeutige Präfixe verwenden.
- `ComputerExtensions.registerHosts(...)`: einem `IComputerSystem` den zugehörigen geladenen Addon-Block zuordnen. Für fremde Systeme `null` zurückgeben. Der Block muss eindeutig zu diesem laufenden System gehören. Damit kann beispielsweise der aktuell zuständige Pultcomputer die gemeinsame Laufzeit und den persistenten Kanalbestand verwenden.

Alle Registrierungen liefern einen `AutoCloseable`-Handle zum Abmelden. Sie erfolgen einmal beim Common Setup, nach optionaler Prüfung auf CC. Der gemeinsame Datenspeicher wird über `ChannelHost` auch Addon-Blockentities bereitgestellt.

Clientseitig kann ein Addon `ComputerPanels` in sein `AbstractComputerMenu` einbetten und `ComputerPanels.Owner` implementieren. `show(0/1/2)` wählt Terminal/Quellen/Kanäle; das Addon schaltet sein Terminal passend sichtbar und ruft `tick()` auf. `family(...)` setzt die passende monochrome beziehungsweise farbige Darstellung. `ComputerSidebarTabs.standardTab(...)` liefert den obersten Terminalreiter, `ComputerSidebarTabs.tab(...)` die darunter angeordneten Quellen- und Kanalreiter; alle verwenden die native CC-Umrandung. Nach Klick-/Tastaturzustellung übernimmt der Host einen von `takeRequestedFocus()` gelieferten Fokus, damit **Neuer Kanal** das Namensfeld aktiviert. Der Controller verwaltet die Widgets und eine eigene Sitzungs-ID. Antworten werden nur an das passende aktuelle Menü und dessen Controller zugestellt. Für Serveranfragen werden Menü-ID, Gültigkeit, Computerinstanz und Nutzungsberechtigung geprüft.

Die bestehende Aeroworks-Lua-API `channels` wird nicht überschrieben. Pultspezifische Steuerung, Display-Skripte und Drive-by-Wire-Override-Semantik bleiben beim Addon.

## Prüfung

`./gradlew test build compilePeripheralTestKotlin --offline` prüft Datenspeicher und Kompilierung. `python3 tools/verify_peripheral_links.py cc` ergänzt reale CraftOS-Tests ohne DbW. Das Profil `console` prüft zusätzlich Pulte und Sable. Mit `--dbw` wird die lokale, unveränderte `dependencies/compile/drivebywire-0.3.0.jar` als Testlaufzeit eingebunden; dieses JAR wird nicht mit TeutonLib ausgeliefert.

Für die gezielte Prüfung von Chunk-Entladung, Wiederladen, Neustart und getrennten Spieler-Menüs: `python3 tools/verify_peripheral_links.py console --dbw --channels-only`. Der Render-/Fokustest startet mit `./gradlew runClient -PperipheralTests -PcomputerUiTests -PperipheralRuntime=cc -PcsoRuntime=none --offline`; die vollständige optionale Clientkombination verwendet stattdessen `-PperipheralRuntime=console -PdbwTests -PcsoRuntime=all`. Testcode und Testmixins sind nicht im veröffentlichten JAR enthalten.

Der gezielte Config-Test läuft mit
`./gradlew runClient -PperipheralTests -PcomputerUiConfigTests -PperipheralRuntime=cc -PcsoRuntime=none --offline`.
Er prüft Standard an, ausgeschalteten Start, Aus-/Einschalten bei offenem Reiter,
Resize und Terminal-Wiederherstellung für native Computer und die Beispielvorlage.
