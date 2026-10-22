# TeutonLib 1.3.0

Für Minecraft **1.21.1**, NeoForge **21.1.234** und Java **21**.
**Kotlin for Forge 5.11.0 bis kleiner 6 ist auf Client und Server erforderlich.**

## Änderungen gegenüber 1.2.2

- Kompatible Heli-API-Erweiterung für Peripheral Links: `adjustTilt` und
  `setTargetDirection` am Directional Gyroscopic Propeller Bearing sowie `getYaw` am
  Gimbalsensor.
- Gemeinsames flüchtiges Bearing-Ziel mit 12°-Begrenzung und Ablauf nach 20 Serverticks.
- Identische Link-Methoden für normale CC:Tweaked- und eingebettete CC-Aeroworks-Computer,
  ohne zweites Attachment.
- Weltbezogene Yaw-Berechnung aus Sable statt aus der Montageausrichtung des Sensorblocks.
- Ausführliche [API-Dokumentation mit Lua-Beispielen](heli-peripherals.md).
- Portable SVG-Rasterung und 3×5-Bitmap-Schrift für CC-Terminals, Monitore und
  CC-Aeroworks-Pultanzeigen, ohne CCVisual-Abhängigkeit.
- Pixelgenaue und auflösungsunabhängige SVG-Platzierung über `svg.absolute` und
  `svg.relative`, wiederverwendbare Styles sowie begrenztes Löschen und Überlagern.
- Ausführliche [Computergrafik-Dokumentation mit Lua-Beispielen](computer-graphics.md).

Fremdmods werden nicht eingebettet. Sable, Simulated, Aeronautics, CC:Tweaked und
CC-Aeroworks bleiben optionale Integrationen.

## Prüfstatus

Build-/Automatikprüfungen und tatsächliche Ingame-Prüfungen werden bewusst getrennt:

- Automatisiert ausgeführt: 61 JUnit-Tests für Mathematik, Argumentverträge,
  Methodenerhalt, Timeout-Grenze und Abhängigkeitssignaturen; Gradle-Build und
  Release-Archivprüfung sowie der eigenständige Lua-Grafik-Vertragstest erfolgreich.
- Startprüfungen ausgeführt: separates Serverprofil ohne optionale
  Create/Sable/Aeronautics-Mods sowie Integrationsprofil mit den tatsächlich installierten
  Sable-2.0.5-/Simulated-1.3.2-/Aeronautics-1.3.2-JARs erfolgreich.
- Ingame: noch manuell auszuführen sind Zielerneuerung, Redstone-Rückkehr, Blockabbau,
  Chunk-Entladung und Weltneustart an beiden Computerarten. Diese Punkte werden nicht als
  ausgeführt bezeichnet, bis ein echter Spieltest protokolliert wurde.
