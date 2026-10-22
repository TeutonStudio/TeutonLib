# TeutonLib 1.3.3

Für Minecraft **1.21.1**, NeoForge **21.1.234** und Java **21**.
**Kotlin for Forge 5.11.0 bis kleiner 6 ist auf Client und Server erforderlich.**

## Änderungen gegenüber 1.3.2

- Ein Simulated Optical Sensor hinter einem Peripheral Link stellt nun
  `getLocalDirection()` bereit.
- Die Methode liefert die Laser-Ausrichtung als benannten Einheitsvektor
  `{ x = ..., y = ..., z = ... }` im lokalen Koordinatensystem. Auf einem
  Sable-Sublevel sind dies dessen Plotkoordinaten; eine Weltorientierung kann damit
  zusammen mit der Gimbalsensor-Rotation berechnet werden.
- Statische Sensoren verwenden dasselbe Koordinatensystem wie die Welt. Das bestehende
  Optical-Sensor-Peripheral selbst und seine bisherigen Methoden bleiben unverändert.

## Prüfstatus

- Automatischer Test prüft alle sechs lokalen Blockausrichtungen und die erwarteten
  Simulated-Schnittstellen.
- Vollständiger Gradle-Build und die optionalen Integrationstestquellen werden vor
  Veröffentlichung geprüft.
