# Android- und Zalith-Kompatibilität

**Deutsch** · [English](Android-und-Zalith-Kompatibilitaet-en) · [Übersicht](Home)

> **Ab TeutonLib 1.6.2:** Diese Adapter liegen jetzt im TerrArchitecture-Modul [`mobilecompat`](https://github.com/TeutonStudio/TerrArchitecture/blob/master/src/main/kotlin/de/TeutonStudio/mobilecompat/SPECS.md). TeutonLib liefert sie nicht mehr aus. Beschreibung und Desktop-Prüfnachweise unten beziehen sich auf frühere TeutonLib-Versionen. Bestehende `teutonlib.mobile.*`-Optionen bleiben in TerrArchitecture als Aliase erhalten.

## Migration ab 1.6.2

Für den ausgelagerten Stand TeutonLib und TerrArchitecture gemeinsam aus dem passenden Quellstand bauen und im Modpack ersetzen. Die neuen Launcher-Argumente heißen `-Dterrarchitecture.mobile.mode=auto`, `on` oder `off` und `-Dterrarchitecture.mobile.render.colorMask=true` für den optionalen Farbmasken-Fallback. Die bisherigen Namen bleiben im TerrArchitecture-Modul Aliase; ein Neustart ist erforderlich.

Diese Auslagerung ist in 1.6.2 enthalten. Die Android-Erkennung, mobilen Mixins und Essential-Socket-API-Extraktion sind aus dem aktuellen TeutonLib-Build entfernt. [Ressourcenreparaturen](Ressourcenreparaturen) bleiben unabhängig davon in TeutonLib.

## Historisches TeutonLib-Verhalten bis einschließlich 1.6.1

TeutonLib aktiviert auf Android eng begrenzte Kompatibilitätspfade für bestimmte Modversionen. Die Erkennung erfolgt früh genug für die Mixin-Auswahl und benötigt keine zusätzlichen Spielblöcke.

## Erkennung und Launcher-Einstellungen

Standard ist automatische Erkennung. Eindeutige Android-/Dalvik-JVM-Merkmale, die gemeinsam vorhandenen Umgebungsvariablen `ANDROID_ROOT` und `ANDROID_DATA` oder typische Android-Verzeichnisse lösen sie aus. `Linux` und `aarch64` allein reichen nicht.

Diese JVM-Argumente können vor dem Start im Launcher gesetzt werden:

| Argument | Wirkung |
| --- | --- |
| `-Dteutonlib.mobile.mode=auto` | automatisch erkennen; Standard |
| `-Dteutonlib.mobile.mode=on` | mobile Kompatibilität erzwingen |
| `-Dteutonlib.mobile.mode=off` | mobile Eingriffe abschalten |

Ein unbekannter Wert wird mit Warnung wie `auto` behandelt. Das ist eine Startoption; eine Änderung erfordert einen erneuten Spielstart.

## Einzelne Funktionen

| Bereich | Geprüfte Version | Eigene Anleitung |
| --- | --- | --- |
| Ponder/Catnip | Ponder 1.0.82+mc1.21.1 | [Framebuffer und Stencil-Darstellung](Ponder-und-Catnip-auf-Android) |
| PowerGrid | 0.5.5.1 | [Java-Solver auf Android](PowerGrid-auf-Android) |
| Essential | 1.3.10.9 | [Optionales Discord IPC](Essential-auf-Android) |
| Immersive Melodies | 0.6.4+1.21.1 | [Hardware-MIDI-Erkennung](Immersive-Melodies-auf-Android) |
| Visual Recipe Editor | 1.2.1 | [Entwickler-Workspace-Backup](Visual-Recipe-Editor-auf-Android) |

Die Versionsprüfung begrenzt jede Anpassung auf die tatsächlich untersuchten Zielklassen. C2ME Native Math wird nicht erzwungen; dessen eigener Java-Fallback und Schutzprüfung bleiben bestehen. Die [Ressourcenreparaturen](Ressourcenreparaturen) sind unabhängig von der Android-Erkennung.

## Diagnose

Meldungen unter `TeutonLib/Compatibility` nennen aktivierte Fallbacks und ungeprüfte Versionen. Wiederholte identische Meldungen werden während eines Laufs begrenzt. Bei Problemen helfen die genaue Modversion, die JVM-Argumente und das Startprotokoll beim Zuordnen des aktiven Pfads. TeutonLib verspricht keine allgemeine Android-Kompatibilität beliebiger Mods oder Versionen.

## Quellen

- [Android-Erkennung und JVM-Argumente](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/compat/MobileEnvironment.kt)
- [Mixin-Auswahl und genaue Zielversionen](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/CsoMixinPlugin.kt)
- [Releasebeschreibung der mobilen Integration](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/release-1.5.3.md)

[Wiki-Startseite](Home) · [Ressourcenreparaturen](Ressourcenreparaturen)

## Abbildung

Ausgeführte Desktop-Prüfung der Android-Erkennung. Keine Aufnahme eines Android-/Zalith-Geräts.

![Android- und Zalith-Kompatibilität: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-MobileEnvironmentTest.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
