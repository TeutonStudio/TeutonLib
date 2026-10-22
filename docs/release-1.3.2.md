# TeutonLib 1.3.2

Für Minecraft **1.21.1**, NeoForge **21.1.234** und Java **21**.
**Kotlin for Forge 5.11.0 bis kleiner 6 ist auf Client und Server erforderlich.**

## Änderungen gegenüber 1.3.1

- Behebt einen Clientabsturz beim Rechtsklick mit einem DriveByWire-Kabel auf
  normale Einkanal-Quellen wie Simulateds Optical Sensor.
- Die DriveByWire-Kanalauswahl greift nur bei CC:Tweaked-Computern in die
  TeutonLib-Ausgangskanäle ein. Andere Quellen verwenden wieder unverändert den
  nativen DriveByWire-Kanal `world`.
- Das DriveByWire-Client-Mixin liegt nun als Java-Mixin vor, sodass sein injizierter
  Handler innerhalb der Zielklasse auf deren private Shadow-Felder zugreift und keine
  unzulässige Kotlin-Companion-Klasse mehr erzeugt wird.

## Prüfstatus

- Automatischer Regressionstest prüft, dass Shadow-Felder und Inject-Handler direkt
  auf der Mixin-Klasse liegen.
- Vollständiger Gradle-Build und die optionalen Integrationstestquellen werden vor
  Veröffentlichung geprüft.
