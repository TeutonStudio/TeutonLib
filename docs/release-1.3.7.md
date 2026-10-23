# TeutonLib 1.3.7

Für Minecraft **1.21.1**, NeoForge **21.1.234** und Java **21**.
**Kotlin for Forge 5.11.0 bis kleiner 6 ist auf Client und Server erforderlich.**

## Änderungen gegenüber 1.3.6

- Peripheriegeräte in der CC-Aeroworks-Quellenansicht werden nach **Benachbart**,
  **Kabelnetzwerk**, **Modem-Netzwerk** und **Peripheral Link** gegliedert.
- Die GPS-Hauptzeile zeigt die präzise ermittelte Koordinate zusammen mit Hostanzahl,
  Alter und Status an.
- Für den Peripheral Link steht zusätzlich das formlose Rezept aus Messingrahmen,
  Enderperle und Sender bereit. Das bisherige Rezept aus Anzeige-Link und Enderperle
  bleibt erhalten.
- Deutsche und englische Bezeichnungen für die neuen Verbindungskategorien sind in
  TeutonLib und der CC-Aeroworks-Integration enthalten.

## Prüfstatus

- Unit-Tests decken die vier Verbindungskategorien und die GPS-Zeilenprojektion ab.
- Die Sprachdateien wurden als JSON validiert.
- Die vollständigen Offline-Gradle-Builds von TeutonLib und CC-Aeroworks wurden
  erfolgreich ausgeführt.
