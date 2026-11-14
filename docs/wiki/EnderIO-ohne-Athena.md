# EnderIO-Ressourcen ohne Athena

**Deutsch** · [English](EnderIO-ohne-Athena-en) · [Übersicht](Home)

Für **EnderIO 8.2.10-beta** blendet TeutonLib drei bekannte Athena-Teilmodelle aus, wenn die Mod `athena` nicht geladen ist. Mit installiertem Athena bleibt diese Reparatur vollständig inaktiv.

## Betroffene Ressourcen

Im Namespace `enderio`:

- `models/block/athena/basic_capacitor_bank_part.json`
- `models/block/athena/advanced_capacitor_bank_part.json`
- `models/block/athena/vibrant_capacitor_bank_part.json`

Jede der drei Dateien muss ihrem eigenen hinterlegten SHA-256 entsprechen. Die Regel wird beim Lesen des Mod-Ressourcenpakets angewendet und verändert die JAR-Datei nicht.

## Verwendung und Grenzen

Es ist keine Bedienung erforderlich und es gibt keinen eigenen Config-Eintrag. Die Regel gilt auf Desktop und Android gleichermaßen. Andere EnderIO-Versionen, veränderte Originaldateien und Nutzerpakete werden dadurch nicht gefiltert. Die Integration fügt Athena nicht hinzu und ersetzt dessen Funktionalität nicht.

Bei abweichendem Fingerabdruck bleibt die jeweilige Originalressource erhalten; das Kompatibilitätsprotokoll weist darauf hin.

## Quelle

- [Athena-Prüfung und EnderIO-Regeln](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/compat/resources/ResourceCompatibility.kt)

[Wiki-Startseite](Home) · [Ressourcenreparaturen](Ressourcenreparaturen)

## Abbildung


Tatsächlich ausgeführte Desktop-Prüfung der Originaldatei-Hashes und konkreten Ressourcenregeln. Die Tests nennen die jeweils behandelte Ressource.

![EnderIO-Ressourcen ohne Athena: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-ResourceCompatibilityTest.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
