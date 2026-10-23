# TeutonLib 1.3.4

Für Minecraft **1.21.1**, NeoForge **21.1.234** und Java **21**.
**Kotlin for Forge 5.11.0 bis kleiner 6 ist auf Client und Server erforderlich.**

## Änderungen gegenüber 1.3.3

- Die gemeinsame Computer-Seitenleiste besitzt nun oberhalb von **Quellen** und
  **Kanäle** den Reiter **Standard**.
- **Standard** öffnet die vorhandene Terminalseite direkt und zeigt ihren aktiven
  Zustand wie die übrigen Reiter an.
- Alle drei Reiter verwenden denselben Bildschirmrahmen. Ein Pfeil nach innen
  kennzeichnet **Quellen**, ein Pfeil nach außen **Kanäle**.
- Native CC-Computer und die kopierbare Addon-Vorlage verwenden dieselbe Reihenfolge
  **Standard**, **Quellen**, **Kanäle**. Für eigene Bildschirme steht
  `ComputerSidebarTabs.standardTab(...)` bereit.
- Maus-, Tastatur-, Fokus-, Escape- und Resize-Verhalten der bestehenden Seiten
  bleiben erhalten.

## Prüfstatus

- Die UI-Prüfungen kontrollieren Anzahl, Beschriftung und Reihenfolge der drei Reiter
  sowie die Rückkehr zum fokussierten Terminal über **Standard**.
- Vollständiger Gradle-Build und die optionalen Integrationstestquellen werden vor
  Veröffentlichung geprüft.
