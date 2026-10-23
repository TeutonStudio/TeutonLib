# TeutonLib 1.3.5

Für Minecraft **1.21.1**, NeoForge **21.1.234** und Java **21**.
**Kotlin for Forge 5.11.0 bis kleiner 6 ist auf Client und Server erforderlich.**

## Änderungen gegenüber 1.3.4

- Peripheriegeräte im Reiter **Quellen** können einen frei vergebenen Spitznamen
  erhalten. Der Spitzname kann jederzeit geändert oder entfernt werden.
- Spitznamen werden am zugehörigen Computer beziehungsweise Addon-Pult gespeichert
  und überleben Entladen und Neustarts.
- Die technische Peripheral-Adresse und Lua-Identität bleiben unverändert.
- Blockname, Geräte- und Linkposition, Verbindungsart sowie Peripheral-Typen bleiben
  in der Detailansicht und im Tooltip sichtbar.
- Die gemeinsame Workspace-Verarbeitung stellt die Funktion auch Addon-Oberflächen
  bereit, die TeutonLibs Peripheral-Einträge verwenden.

## Prüfstatus

- Persistenz, Bereinigung ungültiger NBT-Daten und Entfernen von Spitznamen sind durch
  Unit-Tests abgedeckt.
- Der vollständige Gradle-Build und die optionalen UI-Testquellen wurden erfolgreich
  kompiliert und geprüft.
