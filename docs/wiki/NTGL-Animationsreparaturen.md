# NTGL-Animationsreparaturen

**Deutsch** · [English](NTGL-Animationsreparaturen-en) · [Übersicht](Home)

TeutonLib korrigiert zwei bekannte ungültige Animationsausdrücke in **NTGL 3.1.5**. Jede Regel gilt nur für die exakt bekannte Datei mit passendem SHA-256.

## Reparaturen

| Ressource im Namespace `ntgl` | Änderung |
| --- | --- |
| `animations/example_chassis.animation.json` | In `power_punch`, Knochen `body_top`, Rotation bei `0.5`, wird die Y-Komponente `-17.5++NaN` durch die Zahl `-17.5` ersetzt. |
| `animations/weapons/fatman.animation.json` | In `shot`, Knochen `bullet`, Skalierung bei `0.25`, wird der ungültige Vektor aus drei `0+`-Ausdrücken durch `[1, 1, 1]` ersetzt. |

## Verwendung und Grenzen

Die Reparatur arbeitet automatisch beim Laden der Clientressourcen, auf Desktop und Android. Es gibt keinen zusätzlichen Befehl oder Config-Schalter. Sie ändert ausschließlich die gelesenen Bytes im Ressourcenpfad; die Original-JAR bleibt erhalten.

Modversion, Pfad und Fingerabdruck müssen passen. Eine bereits korrigierte oder anderweitig geänderte Datei wird nicht erneut transformiert. Wenn das Lesen oder die Transformation scheitert, bleibt die Originalressource erhalten und das Protokoll enthält die Diagnose.

## Quelle

- [NTGL-Regeln und Transformationen](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/compat/resources/ResourceCompatibility.kt)

[Wiki-Startseite](Home) · [Ressourcenreparaturen](Ressourcenreparaturen)

## Abbildung


Tatsächlich ausgeführte Desktop-Prüfung der Originaldatei-Hashes und konkreten Ressourcenregeln. Die Tests nennen die jeweils behandelte Ressource.

![NTGL-Animationsreparaturen: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-ResourceCompatibilityTest.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
