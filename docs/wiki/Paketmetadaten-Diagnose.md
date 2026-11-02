# Paketmetadaten-Diagnose

TeutonLib ordnet Fehler beim Lesen der `pack.mcmeta` eines Mod-Ressourcenpakets einem konkreten Mod-Artefakt zu. Das erleichtert die Fehlersuche, wenn eine Paketmetadatenmeldung ohne eindeutigen Besitzer erscheint.

## Diagnose lesen

Bei einem Metadatenfehler protokolliert TeutonLib:

- Mod-ID und Modversion;
- den Namen der zugehörigen Moddatei;
- den SHA-256-Fingerabdruck der `pack.mcmeta`, beziehungsweise `missing` oder `unreadable`;
- den ursprünglichen Fehler samt Stacktrace.

Die Meldung erscheint unter `TeutonLib/Compatibility`. Wiederholungen derselben Kombination aus Mod und Fingerabdruck werden während des laufenden Spiels begrenzt. Gib bei einer Fehlermeldung den betroffenen Protokollabschnitt und die dort genannte Modversion an.

## Grenzen

Diese Diagnose benötigt keine Android-Erkennung und keinen Config-Schalter. Sie wird über den Ressourcenpaket-Wrapper angebunden. Der ursprüngliche Metadatenfehler wird nach dem Protokollieren weitergereicht: TeutonLib erfindet keine Ersatzmetadaten und unterdrückt den Fehler nicht.

Die separat dokumentierten [Ressourcenreparaturen](Ressourcenreparaturen) haben eigene, genau begrenzte Regeln. Eine Diagnosemeldung bedeutet deshalb nicht, dass die betreffende `pack.mcmeta` bereits repariert wurde.

## Quellen

- [DiagnosticResources und Metadaten-Fingerabdruck](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/compat/resources/ResourceCompatibility.kt)
- [Einmalige Diagnosemeldungen](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/compat/CompatibilityDiagnostics.kt)

[Wiki-Startseite](Home) · [Ressourcenreparaturen](Ressourcenreparaturen)
