# Create-Radar-Ressourcenreparatur

**Deutsch** · [English](Create-Radar-Ressourcenreparatur-en) · [Übersicht](Home)

Für **Create Radar 0.4.9.2-1.21.1** blendet TeutonLib acht bekannte, vollständig leere Clientressourcen aus. Die Regel greift nur, wenn die Datei tatsächlich den SHA-256 einer leeren Datei besitzt.

## Betroffene Dateien

Im Namespace `create_radar`:

- `blockstates/radome.json`
- `blockstates/shield_jammer.json`
- `blockstates/siren.json`
- `blockstates/sky_radar.json`
- `blockstates/smart_mount.json`
- `blockstates/sonar_bearing.json`
- `blockstates/sonar_panel.json`
- `models/block/cannon_mount_radar.json`

## Verwendung und Grenzen

Die Regel arbeitet automatisch beim Laden des Mod-Ressourcenpakets, unabhängig von Android. Es gibt keinen Config-Schalter. TeutonLib liefert damit keine Ersatzmodelle für diese Dateien; es verhindert, dass die bekannten leeren JSON-Ressourcen als gültige Modell- oder Blockstate-Dateien gelesen werden.

Die Mod-JAR wird nicht verändert. Eine nicht leere Datei, eine andere Version oder eine entsprechende Datei aus einem Nutzerpaket fällt nicht unter diese Regel. Ein abweichender Fingerabdruck bleibt erhalten und erzeugt einen Diagnosehinweis.

Diese Reparatur ist unabhängig von der Speicherung von Radar-Contraptions bei Hybridzügen.

## Quelle

- [Versionsauswahl und radarRules](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/compat/resources/ResourceCompatibility.kt)

[Wiki-Startseite](Home) · [Ressourcenreparaturen](Ressourcenreparaturen)

## Abbildung


Tatsächlich ausgeführte Desktop-Prüfung der Originaldatei-Hashes und konkreten Ressourcenregeln. Die Tests nennen die jeweils behandelte Ressource.

![Create-Radar-Ressourcenreparatur: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-ResourceCompatibilityTest.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
