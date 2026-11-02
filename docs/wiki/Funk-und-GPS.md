# Funk und GPS auf Sable

TeutonLib v1.6.0 verwendet für [Peripheral Links](Peripheral-Link) sichtbare Weltpositionen und ergänzt CC:Tweakeds höhenabhängige Funkmodemreichweite auf Sable. CC:Tweakeds bestehender GPS-Dienst bleibt zuständig für Hosts, Nachrichten und Trilateration. Es gibt keinen zusätzlichen TeutonLib-GPS-Block und keine eigene `gps`-Lua-API.

## Koordinaten bewegter Konstruktionen

Sable-Blöcke besitzen Plotadressen für Blockzugriffe, während die sichtbare Konstruktion durch ihre Position und Rotation in der Welt bestimmt wird. Eine Entfernung zwischen unveränderten Plotadressen würde deshalb nicht der tatsächlichen Entfernung zwischen Fahrzeugen entsprechen.

TeutonLibs öffentliche `SubLevelGeometry` projiziert Positionen über Sables Helper in Weltkoordinaten und bietet entsprechend angepasste Distanzen. Peripheral Links verwenden diese Distanzen. Die normale Modemreichweite erhält zusätzlich das projizierte Welt-Y für CCs höhenabhängige Berechnung. Sables vorhandene CC-Funkintegration behandelt die Paketdistanz; TeutonLibs Kompatibilitätstests prüfen diese Schnittstelle mit.

Die Standardkommunikation bleibt dimensionsabhängig. Ein normaler Funkpfad wird durch räumlich ähnliche Koordinaten in einer anderen Dimension nicht verbunden. Endermodems behalten die von CC:Tweaked angebotenen Eigenschaften; ein distanzlos empfangenes Paket ist kein gewöhnlicher GPS-Distanzmesswert.

## Vorhandenes GPS verwenden

Für einen reproduzierbaren Aufbau benötigt der Empfänger ein direkt erkennbares Funkmodem sowie erreichbare, mit korrekten globalen Koordinaten konfigurierte GPS-Hosts. Vier räumlich verteilte Hosts mit unterschiedlicher Höhe vermeiden einen rein ebenen Testaufbau. Auf jedem Host wird das normale CraftOS-Programm ausgeführt, beispielsweise für einen tatsächlich bei `(100, 180, 32)` aufgebauten Host:

```text
gps host 100 180 32
```

Die Koordinaten durch den wirklichen Hoststandort ersetzen. Auf dem Empfänger:

```lua
local x, y, z = gps.locate(2, false)
if x then
  print(("Weltposition: %.2f %.2f %.2f"):format(x, y, z))
else
  print("Kein GPS-Fix")
end
```

Auf einem bewegten Sable-Sublevel liefert ein erfolgreicher Fix die globale Position. Das setzt funktionsfähige CC-Hosts und Funkverbindungen voraus. Ein fehlender Fix ist kein Anlass, Plotkoordinaten als Weltposition zu verwenden. CC-Aeroworks' zusätzliche GPS-Quelle gehört zum Addon und erscheint erst nach dessen erfolgreichem Fix; ein entfernt sichtbares `modem`-Peripheral ist nicht automatisch ein direkt erkennbares GPS-Modem.

Die Entfernungslosigkeit von [Computer-Kanal-Fernabfragen](Computer-Kanaele) ist eine gesonderte TeutonLib-Funktion und ändert die physische Funkreichweite nicht.

## Implementierung und Prüfung

[Weltgeometrie-Fassade](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/sable/SubLevelGeometry.kt), [Sable-Adapter](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/sable/SableSubLevelGeometry.kt), [Modem-Höhenkorrektur](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/mixin/computer/WirelessModemRangeMixin.kt), [echte Funk-/GPS-Prüfaufbauten](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/peripheralTest/kotlin/de/TeutonStudio/TeutonLib/peripheraltest/SableVerification.kt).

[Zur Startseite](Home)
