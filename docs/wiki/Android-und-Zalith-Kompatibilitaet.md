# Android- und Zalith-Kompatibilität

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
