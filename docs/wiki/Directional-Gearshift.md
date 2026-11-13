# Directional Gearshift

**Deutsch** · [English](Directional-Gearshift-en) · [Übersicht](Home)

TeutonLib v1.6.0 stellt den **Directional Gearshift von Create Simulated** als vollständiges CC:Tweaked-Peripheral vom Typ `directional_gearshift` bereit. Er benötigt CC:Tweaked, Create und Simulated. Das Gerät ist direkt, über Kabel und über einen [Peripheral Link](Peripheral-Link) erreichbar.

## Zwei Eingänge steuern

```lua
local gearshift = assert(peripheral.find("directional_gearshift"), "Gearshift fehlt")

gearshift.setOutputs(true, false) -- linker Eingang an, rechter aus
print(gearshift.getOutput("left"))
print(gearshift.getAnalogOutput("right"))
gearshift.clear()
```

Die Richtungslogik des Blocks bleibt bei Simulated. TeutonLib setzt dessen beiden bestehenden Schaltzustände und stößt bei einer Änderung die kinetische Neuberechnung an.

| Methode | Funktion |
| --- | --- |
| `setOutput("left"/"right", boolean)` | Einen Eingang setzen. |
| `getOutput("left"/"right")` | Den aktuellen Schaltzustand lesen. |
| `setLeft(boolean)`, `setRight(boolean)` | Einen Eingang setzen und den anderen beibehalten. |
| `setOutputs(left, right)` | Beide Zustände gemeinsam setzen. |
| `isLeftPowered()`, `isRightPowered()` | Die jeweiligen Zustände lesen. |
| `clear()` | Beide Zustände ausschalten. |
| `setAnalogOutput(side, strength)` | Ganzzahl 0–15; 0 bedeutet aus, jeder positive Wert an. |
| `getAnalogOutput(side)` | Liefert 0 oder 15. |

Die Seitenbezeichner `left` und `right` werden ohne Beachtung der Groß-/Kleinschreibung erkannt. Andere Seiten und Analogwerte außerhalb 0–15 verursachen Lua-Fehler. Die Analogschnittstelle stellt keine abgestufte Drehzahlsteuerung dar.

## Zustand und Kompatibilität

`getStatus()` liefert `leftPowered`, `rightPowered`, `sourceFacing`, `blockPos` und `className`. `getPosition()` liefert die Blockadresse als `{x, y, z}`; auf Sable kann dies eine Plotadresse sein. `getFacing()` liefert bei vorhandener kinetischer Quelle deren Richtungsnamen, sonst `"none"`. `getRotationModifier(face)` liest Simulateds gerundeten Drehzahlfaktor für eine Weltrichtung wie `"north"`. `getClassName()` liefert den Blockentity-Klassennamen, `help()` eine kurze Methodenübersicht.

Die Kompatibilitätsmethoden `getName()` und `setName(name)` legen keinen Namen am Simulated-Block an: `getName()` liefert einen leeren String, `setName` hat keine Wirkung. Für sichtbare Spitznamen die [Computer-UI](Computer-UI) beziehungsweise den Peripheral Link verwenden.

## Implementierung

[Alle Lua-Methoden und Zustandsänderung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/simulated/DirectionalGearshiftPeripheral.kt), [Capability-Registrierung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/simulated/DirectionalGearshiftIntegration.kt).

[Zur Startseite](Home)

## Abbildung


Tatsächliche Lua-Abfrage nach setOutputs(true, false) am nativen Directional Gearshift.

![Directional Gearshift: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/wiki-gearshift.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
