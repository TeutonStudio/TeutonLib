# Peripheral Link

**Deutsch** · [English](Peripheral-Link-en) · [Übersicht](Home)

Der **Peripheral Link** (`teutonlib:peripheral_link`) verbindet ein entferntes Peripheral mit einem CC:Tweaked-Blockcomputer. TeutonLib v1.6.0 registriert ihn bei installiertem **CC:Tweaked und Create**. Es gibt keinen separaten Empfängerblock. Addons können über die öffentliche Ziel-/Topologie-API eigene Computer, etwa Pultreihen, anbinden.

## Herstellen und koppeln

Beide Rezepte sind formlos:

| Zutaten | Ergebnis |
| --- | --- |
| `create:brass_casing` + `minecraft:ender_pearl` + `create:transmitter` | 1 Peripheral Link |
| `create:display_link` + `minecraft:ender_pearl` | 1 Peripheral Link |

1. Mit dem Link-Item einen normalen oder erweiterten CC-Computer rechtsklicken. Ein unterstützter Addon-Anker kann ebenfalls gewählt werden.
2. Den Link gegen das gewünschte Peripheral platzieren. Nur das Gerät auf der Befestigungsseite wird angebunden.
3. Den platzierten Link rechtsklicken, um Verbindungsstatus, Weiterleitungsdaten, Lua-API und Spitznamen zu sehen.

Schleichen und Benutzen löscht die Auswahl im gehaltenen Item. Nach erfolgreicher Platzierung wird sie auch im Kreativmodus aus dem verbleibenden Stapel gelöscht; eine fehlgeschlagene Platzierung behält die Auswahl. Zum Umkoppeln den Link abbauen, ein neues Ziel wählen und erneut platzieren. Drops übernehmen weder UUID noch Kopplung oder Spitznamen. Pocket Computer und Turtles sind keine auswählbaren Ziele.

Kopplung und Platzierung prüfen serverseitig Interaktionsreichweite, geladene Blöcke und Spielerrechte. Die Platzierung prüft zusätzlich Ziel und Funkreichweite.

## Lua verwenden

CraftOS installiert die Peripheral-Erweiterung über einen mitgelieferten ROM-Autorun. Danach funktionieren die normalen CC-Aufrufe auch für Linkgeräte:

```lua
for _, name in ipairs(peripheral.getNames()) do
  print(name, peripheral.getType(name))
end

local inventory = peripheral.find("inventory")
if inventory then
  for slot, item in pairs(inventory.list()) do
    print(slot, item.name, item.count)
  end
end
```

Unterstützt werden `getNames`, `isPresent`, `getType`, `hasType`, `getMethods`, `call`, `wrap` und `find`; lokale Seiten und Kabelperipherie bleiben in derselben API erreichbar. `find` erhält CCs Filterparameter und mehrere Rückgabewerte. Eigene Betriebssysteme ohne CraftOS-Autorun installieren die Erweiterung ausdrücklich:

```lua
require("teutonlib.peripheral_links").install()
```

Die zugrunde liegende native API ist `require("teutonlib.links")` beziehungsweise `teutonlib_links`. Ein Link verwendet die persistente Adresse `cc_aeroworks:link_<uuid>`; der historische Präfix setzt keine CC-Aeroworks-Installation voraus. Spitznamen ändern diese Adresse nicht.

Gewrappte Geräte bieten ergänzend `getPeripheralInfo()`, sofern das Gerät diesen Methodennamen nicht bereits selbst belegt. Die Informationen enthalten `connectionType = "wireless"`, `linkPosition`, `targetPosition` und `targetId`. Positionen dienen dem Blockzugriff und können auf Sable Plotkoordinaten sein. Für die Reichweite werden aktuelle sichtbare Weltpositionen verwendet.

## Reichweite und Lebensdauer

In der weltbezogenen `serverconfig/teutonlib-server.toml`:

```toml
[compat.peripheralLink]
    range = 256
    maxLinksPerNetwork = 64
```

Die Reichweitengrenze ist eingeschlossen. Das Limit gilt pro Standardcomputer beziehungsweise pro gemeinsamem aktivem Addon-Computereigentümer. Bei zu vielen geladenen Links werden erreichbare Links in stabiler UUID-Reihenfolge berücksichtigt. Konfigurationsänderungen wirken nach Laden/Neuladen.

Links laden keine Chunks. Entfernung, Entladung, andere Dimension, fehlendes Gerät oder Überschreiten der Reichweite trennen die Bindung. Nach Wiederverfügbarkeit verbindet sie sich automatisch neu. Die Adresse bleibt gleich, alte Handles werden dadurch jedoch nicht wieder gültig; das Programm muss erneut `wrap` oder `find` verwenden. Die Standardereignisse sind `peripheral` und `peripheral_detach`. Das Zielgerät erhält beim Trennen keine allgemeinen Rücksetzbefehle.

UUIDs bleiben in Blockdaten gespeichert. Erkennt das Register zwei geladene Zielanker mit derselben UUID, wird diese Identität dauerhaft in den Weltdaten gesperrt. Das Entfernen einer Kopie oder ein Neustart hebt dies nicht auf. Eine bewusste neue Zielauswahl vergibt dem Anker eine neue UUID; betroffene Links müssen neu gekoppelt werden.

Zusätzliche Gerätefunktionen: [optischer Sensor](Optischer-Sensor), [Directional Gearshift](Directional-Gearshift), [Helikopter-Peripherie](Helikopter-Peripherie), [Gyroskop-Steuerung](Gyroskop-Steuerung).

## Implementierung

[Rezepte](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/resources/data/teutonlib/recipe/peripheral_link_from_components.json), [Item und Kopplung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/PeripheralLinkItem.kt), [Register und Gültigkeit](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/LinkRegistry.kt), [CraftOS-Erweiterung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/resources/data/computercraft/lua/rom/modules/main/teutonlib/peripheral_links.lua).

[Zur Startseite](Home)

## Abbildung


Echter Peripheral-Link-Bildschirm mit Testdaten aus der UI-Prüfung; Methoden und Status sind hier Prüfdaten, kein Live-Inventar.

![Peripheral Link: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/peripheral-link-ui.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
