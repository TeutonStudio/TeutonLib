# TeutonLib 1.0.0 – PC-UI-Vorlage

Dieses Release liefert die gemeinsame PC-Oberfläche als Grundlage für
CC Aeroworks und weitere TeutonStudio-Addons.

- Quellen- und Kanalreiter für normale und erweiterte CC-Blockcomputer.
- Gemeinsame Bildschirmkomponenten und öffentliche Provider-/Host-Schnittstellen.
- Kompilierte [Integrationsvorlage](../examples/computer-ui/README.md) mit vollständigem
  Bildschirm und Setupadapter; Fokus, Terminalwechsel und Resize sind enthalten.
- Persistente Kanäle, Lua-Modul `teutonlib.channels`, optionale DbW-Anbindung.
- Peripheral Link und die vorhandenen optionalen Create-/Sable-/New-Age-Integrationen.

## Dateien und Erstellung

```bash
./gradlew releaseBundle --offline
```

Erzeugt `build/distributions/teutonlib-1.0.0-computer-ui-template.zip` mit Mod-JAR,
Quellen-JAR, Dokumentation und kopierbarer Java-Vorlage. Die Mod-JAR liegt auch unter
`build/libs/teutonlib-1.0.0.jar`. Nur die Mod-JAR in den `mods`-Ordner installieren.
Entwicklungs-Testmods und lokale Fremdmod-JARs sind nicht enthalten.

Minecraft 1.21.1, NeoForge ab 21.1.234, Java 21 und KotlinForForge 5.11.0 sind
erforderlich. Die PC-Oberfläche ist mit CC:Tweaked 1.120.2 geprüft. Für optionale
Integrationen gelten die Versionsgrenzen in der [README](../README.md).
Die separate CSO-Mod darf nicht parallel installiert sein.

## Grenzen der Vorlage

CC Aeroworks 1.4.0 verwendet zunächst weiterhin seine eigenen Pultreiter.
Dieses Release stellt die Grundlage für deren anschließende Migration bereit.
Es ändert weder die Pult-Lua-API noch die Steuerungs-/Override-Semantik.
Eigene Computer benötigen einen passenden Menütyp, einen korrekt zugeordneten
geladenen Host und die eigenen Block-/Computer-Lebenszyklusfunktionen.
Pocket Computer und Turtles gehören nicht zur PC-UI-Vorlage.

Build und automatisierte Prüfungen sind in der Vorlagendokumentation beschrieben.
Ein erfolgreicher Build veröffentlicht kein GitHub-Release und lädt keine Dateien
zu einem Modportal hoch.

## Prüfnachweis vom 18. Oktober 2026

| Prüfung | Ergebnis |
| --- | --- |
| Build, Beispiel- und Testmod-Kompilierung | bestanden |
| JUnit einschließlich Kanalbestand und API-Kompatibilität | 26 Tests, keine Fehler |
| Client nur mit CC und KotlinForForge | 8 Szenarien bestanden |
| Client mit CC Aeroworks, DbW und optionalen Integrationen | 8 Szenarien bestanden |
| CC-Server: Kanäle, Peripheral Links, Chunk-Neuladen und Neustart | bestanden |
| CC-Aeroworks-/DbW-Server: Kanäle, Chunk-Neuladen, Neustart und Menürechte | bestanden |
| Release-Paket | Mod, Quellen, Dokumentation und Vorlage enthalten; keine Fremdmod-JARs oder Testmods |
| Maven-POM | `de.TeutonStudio.TeutonLib:teutonlib:1.0.0` bestätigt |

Die Client-Szenarien prüfen normale und erweiterte Computer bei GUI-Skalierung 2/3
für native CC-Bildschirme und den Addon-Vorlagenbildschirm. Fokus, Inventartaste im
Namensfeld, Escape zum Terminal und neue Sitzungen nach Resize sind eingeschlossen.
Screenshots und maschinenlesbare Serverberichte liegen lokal unter
`run/client/screenshots/` beziehungsweise `build/reports/smoke/`.
Ein eigener CC-Aeroworks-Bildschirm auf Basis der Vorlage ist noch nicht migriert;
die Prüfungen bestätigen die Vorlage und ihre Koexistenz mit CC Aeroworks 1.4.0.
