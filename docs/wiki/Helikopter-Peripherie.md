# Helikopter-Peripherie: Gimbalsensor

TeutonLib v1.6.0 ergänzt den **Gimbalsensor von Create Simulated** um einzeln lesbare Winkel und eine dreiachsige Rotation. Die Methoden stehen direkt am CC:Tweaked-Computer, im Kabelnetz und über einen [Peripheral Link](Peripheral-Link) zur Verfügung. Die Integration setzt CC:Tweaked, Create und Simulated voraus; die bewegte Orientierung stammt bei vorhandenen Sable-Sublevels aus deren tatsächlicher Rotation.

## Winkel lesen

```lua
local sensor = assert(peripheral.find("gimbal_sensor"), "Gimbalsensor fehlt")
print("Pitch:", sensor.getPitch())
print("Yaw:", sensor.getYaw())
print("Roll:", sensor.getRoll())

local rotation = sensor.getRotation()
print(rotation[1], rotation[2], rotation[3]) -- pitch, yaw, roll
```

| Methode | Ergebnis |
| --- | --- |
| `getPitch()` | Simulateds X-Neigung in Grad. |
| `getRoll()` | Simulateds Z-Neigung in Grad. |
| `getYaw()` | Horizontale Weltorientierung in Grad, Bereich `[0, 360)`. |
| `getRotation()` | Tabelle `{pitch, yaw, roll}` in Grad. |
| `getAngles()` | Unverändertes bestehendes Simulated-Format `{pitch, roll}`. |
| `getAnglesRad()` | Unverändertes bestehendes Simulated-Format `{pitch, roll}` in Radiant. |

TeutonLib erhält die zweielementigen Simulated-Formate, damit bestehende Programme weiter funktionieren. Der frühere Schreibfehler `getRotaion()` bleibt als veralteter Alias von `getRotation()` erhalten.

## Yaw-Konvention

| Weltrichtung | Yaw |
| --- | --- |
| Süd (+Z) | 0° |
| West (−X) | 90° |
| Nord (−Z) | 180° |
| Ost (+X) | 270° |

Auf einem Sable-Sublevel folgt Yaw dessen Weltrotation und ist unabhängig von der Montageausrichtung des Sensorblocks. Bei einem statischen Sensor wird die positive horizontale Blockachse verwendet: Z ergibt Süd/0°, X ergibt Ost/270°.

Ist die Vorwärtsrichtung senkrecht oder nicht endlich, kann keine horizontale Orientierung bestimmt werden. `getYaw()` und damit `getRotation()` melden dann einen Lua-Fehler; ein Regelprogramm kann den Aufruf mit `pcall` absichern.

## Anzeige mit Ingenieursbrille

Mit Creates Ingenieursbrille zeigt der Gimbalsensor zwischen den vorhandenen X- und Z-Werten zusätzlich den **weltrelativen Y-Winkel**. X erscheint rot, Y grün, Z blau; der Y-Wert hat zwei Nachkommastellen und ein Gradzeichen. Kann keine horizontale Orientierung bestimmt werden, erscheint anstelle eines Y-Werts ein grauer Gedankenstrich. Diese Anzeige benötigt Create, Simulated und Sable, aber keinen laufenden CC-Computer.

Die allgemeine TeutonLib-Erweiterung führt keine vollständige Flugregelung aus. Weitere Bausteine sind [Gyroskop-Steuerung](Gyroskop-Steuerung), [optischer Sensor](Optischer-Sensor), [Directional Gearshift](Directional-Gearshift) und [Funk/GPS](Funk-und-GPS).

## Implementierung

[Gimbal-Methoden](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/mixin/peripheral/simulated/GimbalSensorPeripheralMixin.kt), [Weltorientierung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/heli/GimbalHeading.kt), [Ingenieursbrillen-Anzeige](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/mixin/simulated/GimbalSensorTooltipMixin.kt), [Winkelmathematik](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/heli/HeliMath.kt).

[Zur Startseite](Home)
