# Gyroskop-Steuerung

TeutonLib v1.6.0 macht Aeronautics' **Gyroscopic Propeller Bearing** als CC:Tweaked-Peripheral vom Typ `gyroscopic_propeller_bearing` erreichbar. Die generischen Zusatzmethoden gelten auch für Unterklassen, darunter `simulated_addition:directional_gyroscopic_propeller_bearing`; deren eigener Peripheral-Typ und vorhandene Methoden bleiben erhalten.

Die Integration setzt CC:Tweaked, Aeronautics, Simulated und Sable voraus; Create gehört zum Geräteumfeld. Der Zugriff funktioniert direkt, über Kabel und über einen [Peripheral Link](Peripheral-Link). Es wird eine vorübergehende Schubrichtungs-Vorgabe gesetzt, keine komplette Flugregelung.

## Weltziel vorgeben

```lua
local bearing = assert(peripheral.find("gyroscopic_propeller_bearing"), "Lager fehlt")

while true do
  bearing.setTargetDirection({x = 0.08, y = 1, z = -0.03})
  sleep(0.25)
end
```

`setTargetDirection({x, y, z})` erwartet genau die benannten Felder `x`, `y`, `z` als endliche Zahlen. Es normalisiert den Weltvektor und transformiert ihn anhand der aktuellen Sable-Rotation in lokale Koordinaten. Ohne enthaltenes Sublevel wird der Vektor direkt verwendet. Nullvektoren, sehr kleine nicht normalisierbare Vektoren, fehlende/zusätzliche Felder oder nicht endliche Werte verursachen Lua-Fehler.

## Lokale Neigung verändern

```lua
bearing.adjustTilt({deltaRoll = 1.5, deltaPitch = -0.5})
```

`adjustTilt` verlangt genau `deltaRoll` und `deltaPitch` in Grad. Roll dreht um die lokale +Z-Längsachse, Pitch um die lokale +X-Querachse. Ist eine Lua-Vorgabe noch aktiv, wird diese weiter verändert; andernfalls dient die aktuelle Schubrichtung als Ausgangspunkt. Wiederholte Aufrufe sind deshalb inkrementell.

## Zeitverhalten und Grenzen

Beide Methoden aktualisieren dieselbe flüchtige Vorgabe. Sie ist bis unmittelbar vor dem **20. Servertick nach dem letzten Aufruf** aktiv. Ohne Erneuerung übernimmt danach wieder die normale Redstone-/Stabilisierungssteuerung. Bei üblichen 20 Ticks pro Sekunde entspricht dies etwa einer Sekunde; die Beispielschleife erneuert früher.

Die Vorgabe wird auf den vorhandenen **12°-Neigungskegel** um die Lagerachse begrenzt. Bewegungsgeschwindigkeit und normale Betriebsbedingungen des Lagers bleiben maßgeblich. Während die Lua-Vorgabe aktiv ist, ersetzt sie die automatische Zielrichtung. Sie wird nicht in NBT gespeichert und beim Chunk-Entladen verworfen. Ein Programm sollte die Vorgabe regelmäßig erneuern und für Sensorausfälle ein eigenes Verhalten festlegen.

Ein Directional Bearing mit anderem Peripheral-Typ kann über seinen bekannten Namen mit `peripheral.wrap(name)` erreicht werden. `peripheral.getMethods(name)` zeigt, ob die beiden Zusatzmethoden verfügbar sind. Zur Winkelmessung siehe [Gimbalsensor](Helikopter-Peripherie).

## Implementierung

[Peripheral-Registrierung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/heli/HeliPeripheralIntegration.kt), [generische Lua-Methoden](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/heli/BearingGenericMethods.kt), [Welt-/Lokaltransformation](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/heli/BearingControl.kt), [Neigungsgrenze und Timeout](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/heli/HeliMath.kt).

[Zur Startseite](Home)
