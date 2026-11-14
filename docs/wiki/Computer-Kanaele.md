# Computer-Kanäle

**Deutsch** · [English](Computer-Kanaele-en) · [Übersicht](Home)

TeutonLib v1.6.0 stellt CC:Tweaked-Blockcomputern bis zu **64 benannte Kanäle pro Computer** bereit. Die native Lua-API heißt `teutonlib.channels`; global ist sie auch als `teutonlib_channels` erreichbar. Die gemeinsame [Computer-UI](Computer-UI) zeigt und verwaltet diese Kanäle.

## Kanalarten und Namen

| Art | Bedeutung |
| --- | --- |
| `output` | Schreibbarer ganzzahliger Wert von 0 bis 15; mit Drive By Wire als Kabelquelle verwendbar. |
| `input` | Benannter Lesezugriff auf das DbW-Signal einer Computerseite; nicht beschreibbar. |

Namen bestehen aus 1–32 Zeichen: `A–Z`, `a–z`, `ÄÖÜäöüß`, Ziffern, `_`, `.` und `-`. Der Name `world` ist reserviert. Gültige Eingangsseiten sind `front`, `back`, `left`, `right`, `top`, `bottom`. Mehrere Eingangsnamen derselben Seite lesen dasselbe physische Signal.

Drive By Wire ist optional; die geprüfte Schnittstelle ist 0.3.0. Ohne DbW funktionieren Definitionen, Ausgangswerte und Fernabfragen, während DbW-Eingänge 0 liefern. Diese Eingänge sind von den normalen CC-Redstone-Eingängen in der Quellenansicht zu unterscheiden.

## Lokal programmieren

```lua
local channels = require("teutonlib.channels")

channels.create("throttle", "output")
channels.set("throttle", 12)
channels.setPublic("throttle", true)
channels.create("sensor", "input", "top")
print(channels.read("sensor"))
channels.rename("throttle", "engine")
channels.remove("sensor")

local own, hidden = channels.list() -- hidden ist lokal 0
for _, entry in ipairs(own) do
  print(entry.name, entry.direction, entry.value)
end
```

`create` schlägt bei vorhandenem Namen fehl; Initialisierung daher nur einmal durchführen oder zuvor `list()` prüfen. Beschreibungen enthalten `name`, `direction`, `side`, `public`, `value`, `connections` und `provider`. Bei eigenen Ausgängen zählt `connections` die DbW-Kabelziele. Lokale Änderungen erzeugen `teutonlib_channels_changed`.

Im Kanalreiter erzeugt **Neuer Kanal → Namen eingeben → Anlegen** einen privaten Ausgang. Auswahl eines Kanals bietet Umbenennen, öffentlich/privat, Wertänderung und Löschen. Löschen erfordert einen zweiten Klick. Umbenennen übernimmt vorhandene DbW-Verbindungen; Löschen entfernt sie. Seiteneingänge werden über Lua angelegt.

Definitionen, Sichtbarkeit und Ausgangswerte werden in den Blockdaten gespeichert. Ausschalten oder Entladen setzt Ausgangswerte nicht zurück; beim Wiederladen werden gespeicherte Werte an DbW übergeben. TeutonLib lädt entfernte Computer hierfür nicht nach.

## Andere Computer lesen

```lua
local channels = require("teutonlib.channels")
local visible, privateCount = channels.list(5)
print("Nicht aufgelistete private Kanaele:", privateCount)
print(channels.read(5, "engine"))
```

Beide laufenden Computer benötigen ein erreichbares Peripheral vom Typ `modem`, lokal, per Kabel oder per Peripheral Link. Rednet muss nicht laufen und ein geöffneter Modemkanal ist nicht erforderlich. Fernabfragen haben keine Entfernungsgrenze, bleiben aber innerhalb derselben Dimension. Nicht verfügbare oder mehrdeutige Computer-IDs verursachen einen Lua-Fehler. Fernschreiben wird nicht angeboten.

**Privat blendet einen Kanal aus der Auflistung aus. Es ist kein Zugriffsschutz.** `list(id)` liefert nur öffentliche Beschreibungen und die Anzahl privater Kanäle. `read(id, name)` liest auch einen privaten Kanal, wenn sein Name bekannt ist. Fremde Computer erhalten keine aktiven Änderungsereignisse; Programme fragen den Wert erneut ab.

CraftOS bietet `channels`, `channels 5` und `channels 5 engine`. Auf CC Aeroworks bleibt dessen bestehender `channels`-Alias bestehen; das TeutonLib-Programm ist dort ausdrücklich als `rom/programs/channels.lua` aufrufbar. Addon-Kanäle können in der gemeinsamen Liste erscheinen, bleiben dort aber schreibgeschützt und behalten ihre eigenen Wertebereiche.

## Implementierung

[Lua-API und Fernauflösung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/computer/ComputerRuntime.kt), [Kanalbestand und Namensregeln](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/computer/ChannelBank.kt), [CraftOS-Befehl](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/resources/data/computercraft/lua/rom/programs/channels.lua).

[Zur Startseite](Home)

## Abbildung


Echter CC-Blockcomputer mit zwei über die native Lua-API angelegten Kanälen.

![Computer-Kanäle: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/wiki-channels.png)

![Computer-Kanäle: 2](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/wiki-channel-ui.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
