# TeutonLib 1.3.6

Für Minecraft **1.21.1**, NeoForge **21.1.234** und Java **21**.
**Kotlin for Forge 5.11.0 bis kleiner 6 ist auf Client und Server erforderlich.**

## Änderungen gegenüber 1.3.5

- Kanalnamen erlauben nun neben Kleinbuchstaben auch Großbuchstaben und die
  deutschen Buchstaben `ÄÖÜäöüß`.
- Die gemeinsame Workspace-Übertragung erhält diese Zeichen auch in den von Addons
  bereitgestellten Kanalgruppen.
- Die bisher erlaubten Ziffern und Sonderzeichen `_`, `.` und `-` sowie die
  maximale Länge von 32 Zeichen bleiben unverändert; `world` bleibt reserviert.

## Prüfstatus

- Unit-Tests decken Großbuchstaben, deutsche Umlaute, `ß`, die Workspace-
  Übertragung von Kanalgruppen und weiterhin abgelehnte Sonderzeichen ab.
- Der vollständige Gradle-Build und das Release-Bundle wurden erfolgreich geprüft.
