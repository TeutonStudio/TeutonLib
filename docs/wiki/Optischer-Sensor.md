# Optischer Sensor: lokale Laserrichtung

TeutonLib v1.6.0 erweitert den **Optical Sensor von Create Simulated** hinter einem [Peripheral Link](Peripheral-Link) um `getLocalDirection()`. Voraussetzung sind CC:Tweaked, Create, Simulated und ein verbundener Link zum Sensor. Die Zusatzmethode wird ausdrücklich nur im Linkpfad ergänzt.

## Richtung lesen

```lua
local sensor = assert(peripheral.find("optical_sensor"), "Optischer Sensor fehlt")
assert(type(sensor.getLocalDirection) == "function", "Sensor muss ueber einen Peripheral Link verbunden sein")

local direction = sensor.getLocalDirection()
print(direction.x, direction.y, direction.z)
```

Die Rückgabe ist ein benannter Einheitsvektor mit ganzzahligen Komponenten `x`, `y`, `z`. Er folgt der Laserausrichtung des Sensorblocks. Ein nach lokal +Z ausgerichteter Sensor liefert beispielsweise `{x = 0, y = 0, z = 1}`.

Auf Sable entspricht der Vektor dem lokalen Plotkoordinatensystem. Er ist daher nicht automatisch die sichtbare Weltrichtung eines gedrehten Fahrzeugs. Ein Flug- oder Messprogramm muss für eine Weltrichtung die vollständige Sublevelrotation anwenden. Bei einem statischen Sensor stimmen lokales und Weltkoordinatensystem überein.

TeutonLib verändert mit dieser Ergänzung weder die vorhandenen Messmethoden des Sensors noch deren Ergebnisse. Eine Trennung des Links macht vorhandene Handles ungültig; nach dem Ereignis `peripheral` den Sensor neu suchen oder wrappen.

Siehe auch [Helikopter-Peripherie](Helikopter-Peripherie) für Gimbalwinkel und [Gyroskop-Steuerung](Gyroskop-Steuerung).

## Implementierung

[Link-Erweiterung und Vektorbildung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/heli/OpticalSensorPeripheralExtensions.kt), [optionale Erweiterungsauswahl](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/heli/HeliPeripheralExtensions.kt).

[Zur Startseite](Home)
