# Heli-Peripherals

TeutonLib 1.3.0 erweitert Geräte hinter einem Peripheral Link. Die Methoden stehen über
`peripheral.call`, `peripheral.wrap`, `peripheral.getMethods` und `peripheral.find` sowohl
auf normalen CC:Tweaked-Computern als auch auf eingebetteten CC-Aeroworks-Computern zur
Verfügung. Vorhandene Peripheral-Typen und Methoden bleiben bestehen.

Geprüfte Integrationsbasis: Minecraft 1.21.1, Sable 2.0.5 und das
Create-Aeronautics-Bundle 1.3.2 mit Aeronautics und Simulated 1.3.2. Alle Integrationen
bleiben optional.

## Directional Gyroscopic Propeller Bearing

```lua
bearing.adjustTilt({ deltaRoll = 1.5, deltaPitch = -0.5 })
bearing.setTargetDirection({ x = 0, y = 1, z = 0 })
```

- `adjustTilt(table)` erwartet `deltaRoll` und `deltaPitch` in Grad. Roll dreht um die
  lokale +Z-Längsachse, Pitch um die lokale +X-Querachse des Sable-Sublevels. Änderungen
  bauen auf dem noch aktiven letzten Lua-Ziel auf, andernfalls auf der aktuellen
  Schubrichtung.
- `setTargetDirection(table)` erwartet einen Weltvektor mit genau `x`, `y` und `z`.
  TeutonLib normalisiert ihn und transformiert ihn mit der tatsächlichen
  Sublevelrotation in lokale Koordinaten.
- Beide Aufrufe setzen dieselbe flüchtige Zielvorgabe zurück. Sie bleibt ab dem letzten
  Aufruf 20 Serverticks aktiv; beim 20. Tick ohne Erneuerung übernimmt wieder das normale
  Redstone-/Stabilisierungsverhalten.
- Die vorhandene 12°-Neigungsgrenze, Bewegungsgeschwindigkeit und Betriebsbedingungen des
  Lagers bleiben wirksam. Solange das Lua-Ziel aktiv ist, ersetzt es die automatische
  seitliche Zielrichtung.
- Das Ziel wird weder in NBT noch in der Welt gespeichert und beim Chunk-Entladen
  verworfen.

Die Tabellen müssen genau die dokumentierten Felder enthalten. Fehlende oder zusätzliche
Felder, Nullvektoren und NaN/unendliche Zahlen erzeugen Lua-Fehler.

Typisches Erneuern in einer Regelschleife:

```lua
local bearing = peripheral.find("gyroscopic_propeller_bearing")
assert(bearing, "bearing missing")

while true do
  bearing.setTargetDirection({ x = 0.08, y = 1, z = -0.03 })
  sleep(0.25) -- deutlich vor dem Timeout von einer Sekunde erneuern
end
```

## Gimbalsensor

```lua
local yaw = sensor.getYaw()
```

`getYaw()` liest die tatsächliche Sable-Sublevelrotation und ist von der Montageausrichtung
des Sensorblocks unabhängig. Das Ergebnis ist in Grad im halboffenen Bereich
`[-180, 180)`: lokale +Z nach Welt-+Z ist 0°, lokale +Z nach Welt-+X ist +90°.

Außerhalb eines Sable-Sublevels oder bei senkrechter beziehungsweise nicht endlicher
Vorwärtsrichtung liefert die Methode einen verständlichen Lua-Fehler. Die vorhandenen
`getAngles()`- und `getAnglesRad()`-Methoden werden unverändert weitergereicht.

## Beispiel mit Methodenprüfung

```lua
for _, name in ipairs(peripheral.getNames()) do
  local methods = peripheral.getMethods(name)
  if methods then
    for _, method in ipairs(methods) do
      if method == "getYaw" then
        print(name, peripheral.call(name, "getYaw"))
      end
    end
  end
end
```
