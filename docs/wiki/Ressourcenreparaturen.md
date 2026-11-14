# Ressourcenreparaturen

**Deutsch** · [English](Ressourcenreparaturen-en) · [Übersicht](Home)

TeutonLib behandelt bekannte fehlerhafte Clientressourcen bestimmter Mod-Releases. Die Regeln sind unabhängig von Android und werden nur angewendet, wenn **Mod-ID, Version, Ressourcenpfad und SHA-256** der Originaldatei passen.

## Einzelne Reparaturen

| Mod | Geprüfte Version | Wirkung |
| --- | --- | --- |
| Create Radar | 0.4.9.2-1.21.1 | [Leere Modell- und Blockstate-Dateien ausblenden](Create-Radar-Ressourcenreparatur) |
| EnderIO | 8.2.10-beta, Athena nicht geladen | [Athena-Teilmodelle ausblenden](EnderIO-ohne-Athena) |
| NTGL | 3.1.5 | [Zwei ungültige Animationsausdrücke reparieren](NTGL-Animationsreparaturen) |
| Design 'n' Decor | 2.2b | [Geprüfte sounds.json ersetzen](Design-n-Decor-Soundreparatur) |

Die Regeln wirken beim Lesen des jeweiligen Mod-Ressourcenpakets. Die JAR-Dateien werden nicht verändert. Nutzerpakete und andere Versionen werden nicht pauschal gefiltert. Es gibt keinen zusätzlichen Spielbefehl und keinen TeutonLib-Config-Schalter.

## Schutzgrenzen und Fehlerbehandlung

Bei abweichender SHA-256 bleiben die Originalbytes erhalten und TeutonLib protokolliert eine Warnung. Scheitert eine Transformation oder das Lesen zur Prüfung, verwendet es ebenfalls die ursprüngliche Ressource. Die Implementierung betrifft ausschließlich die benannten Clientressourcen; Serverdaten werden nicht umgeschrieben.

Für jedes passende Mod-Paket wird zusätzlich die [Paketmetadaten-Diagnose](Paketmetadaten-Diagnose) eingebunden. Sie ordnet Fehler in `pack.mcmeta` einem Mod, dessen Dateinamen und dem Fingerabdruck zu, repariert solche unbekannten Metadatenfehler aber nicht automatisch.

## Quellen

- [Alle Ressourcenregeln und Hashes](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/compat/resources/ResourceCompatibility.kt)
- [Einbindung in Mod-Ressourcenpakete](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/java/de/TeutonStudio/TeutonLib/mixin/resource/ResourcePackLoaderMixin.java)
- [Releasebeschreibung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/release-1.5.3.md)

[Wiki-Startseite](Home) · [Android und Zalith](Android-und-Zalith-Kompatibilitaet)

## Abbildung


Tatsächlich ausgeführte Desktop-Prüfung der Originaldatei-Hashes und konkreten Ressourcenregeln. Die Tests nennen die jeweils behandelte Ressource.

![Ressourcenreparaturen: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-ResourceCompatibilityTest.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
