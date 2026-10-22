# TeutonLib 1.3.1

Für Minecraft **1.21.1**, NeoForge **21.1.234** und Java **21**.
**Kotlin for Forge 5.11.0 bis kleiner 6 ist auf Client und Server erforderlich.**

## Änderungen gegenüber 1.3.0

- Der Gimbalsensor zeigt in der Ingenieursbrille neben X und Z nun auch den Y-Winkel.
- Y verwendet Minecraft-Yaw im Bereich `[0, 360)`: Süd 0°, West 90°, Nord 180°
  und Ost 270°.
- `getPitch()`, `getYaw()`, `getRoll()` und `getRotation()` stehen direkt am
  CC:Tweaked-Peripheral sowie über Peripheral Links und CC-Aeroworks-Pultcomputer zur
  Verfügung. `getRotation()` liefert `{ pitch, yaw, roll }` in Grad.
- `getAngles()` und `getAnglesRad()` behalten ihr kompatibles zweielementiges
  `{ pitch, roll }`-Format. `getRotaion()` ist ein veralteter Alias von `getRotation()`.
- Die eingemischten Peripheral-Methoden bleiben überschreibbar, damit Create Avionics
  seine erweiterte Gimbalsensor-Unterklasse weiterhin laden kann.
- Statische Sensoren verwenden Simulateds visuelle positive Blockachse; auf
  Sable-Schiffen bleibt die Himmelsrichtung von der Sensormontage unabhängig.
- Deutsche und englische Brillenbeschriftungen sowie die Heli-API-Dokumentation wurden
  entsprechend erweitert.

## Prüfstatus

- JUnit-Tests, vollständiger Gradle-Build und Kompilierung der Peripheral-Testquellen
  erfolgreich.
- Ein lokaler CC:Tweaked-/Sable-/Simulated-Server startet mit den neuen Mixins ohne
  Transformations- oder Ladefehler.
- Die visuelle Ingenieursbrillen-Prüfung bleibt als manueller Ingame-Test vorgesehen.
