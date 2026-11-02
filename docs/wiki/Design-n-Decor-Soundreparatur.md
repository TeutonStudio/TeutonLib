# Design 'n' Decor: Soundressource

Für **Design 'n' Decor 2.2b** ersetzt TeutonLib die bekannte, per SHA-256 identifizierte Ressource `dndecor:sounds.json` beim Lesen durch ein leeres JSON-Objekt (`{}`).

## Verwendung und Wirkung

Die Regel aktiviert sich automatisch, unabhängig von der Android-Erkennung. Sie verhindert das Laden der problematischen Sounddefinitionen dieser konkreten Originaldatei. Dadurch werden aus dieser Datei keine eigenen Soundereignisse definiert; die Regel erzeugt keine Ersatzklänge.

Es ist kein Config-Eintrag und kein zusätzlicher Spielbefehl nötig. Die JAR-Datei bleibt unverändert. Nur das Client-Ressourcenpaket der Mod mit ID `dndecor`, Version `2.2b`, dem genannten Pfad und dem geprüften Fingerabdruck wird behandelt.

## Grenzen

Andere Modversionen, veränderte Dateien und Sounddefinitionen in Nutzerpaketen werden nicht pauschal ersetzt. Bei abweichender SHA-256 verwendet TeutonLib die Originalbytes und meldet die Abweichung im Kompatibilitätsprotokoll.

## Quelle

- [Design-'n'-Decor-Regel und Hashprüfung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/compat/resources/ResourceCompatibility.kt)

[Wiki-Startseite](Home) · [Ressourcenreparaturen](Ressourcenreparaturen)
