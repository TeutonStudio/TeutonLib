# Entwicklung und Prüfungen

**Deutsch** · [English](Entwicklung-und-Pruefungen-en) · [Übersicht](Home)

TeutonLib 1.6.0 verwendet Kotlin, Gradle Kotlin DSL und Java 21. Einige kleine dependency-isolierte bzw. statische Mixins sind in Java geschrieben. CC-Laufzeitmodule verwenden Lua; externe Prüftreiber Python.

## Build

```sh
./gradlew build releaseBundle --offline
python3 tools/verify_release.py
```

Benötigt werden die originalen lokalen Mod-Binaries unter `dependencies/compile`, `dependencies/runtime` und `dependencies/verification`; sie sind nicht im Git-Repository enthalten. Der genaue Satz und die opt-in-Profile sind in der [README](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/README.md) und im [Buildskript](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/build.gradle.kts) beschrieben. Simulated zur Laufzeit ausschließlich über das Aeronautics-Bundle laden.

`build` kompiliert die Computer-UI-Vorlage und führt die JUnit-Tests aus. `releaseBundle` erzeugt Mod-JAR, Sources und Vorlagen-ZIP. `verify_release.py` prüft unter anderem Metadaten, Klassenidentität mit dem kompilierten Output, Lizenzen, eingebettete Abhängigkeiten und identische JARs im ZIP; anschließend schreibt es `build/distributions/SHA256SUMS`.

## Spielprüfungen

| Bereich | Einstieg |
| --- | --- |
| Minimalinstallation / Create / voller Satz | `python3 tools/verify_server.py none` bzw. `create` bzw. `all` |
| Hybridzüge einschließlich Bahnhofsbau und Neustart | `python3 tools/verify_hybrid_trains.py --packaged` |
| Zug-Portaltransfer | `python3 tools/verify_train_portals.py --packaged` |
| Grafik, Portalbeleuchtung und Flywheel | `python3 tools/verify_train_portal_client.py --sodium --render-only --verify-flywheel` |
| Docking im großen Portal und Rückabwicklung | `python3 tools/record_train_docking_demo.py --case wide --check --rollback` |
| Trennung im kleinen Portal | `python3 tools/record_train_docking_demo.py --case narrow --check` |
| Schlauchrolle und Drain-Kupplung | `python3 tools/verify_hose_connector.py` |
| Schlauchdarstellung | `python3 tools/verify_hose_pulley_client.py` |
| Mekanism-Modelle | `python3 tools/verify_mekanism_models.py` |

Die Skripte verwenden gesonderte lokale Testinstanzen unter `run/smoke/`; sie sind keine Anleitung zum Ausführen in persönlichen Spielständen. Grafikprüfungen benötigen einen OpenGL-Desktop. Die Testmods werden ausschließlich über opt-in-Profile geladen und nicht ausgeliefert. Serverprüfungen nacheinander ausführen.

Ein erfolgreich kompilierter Test ist kein ausgeführter Spieltest. Aktuelle, tatsächlich ausgeführte Releaseprüfungen und bekannte Grenzen stehen in den [Release-Notizen](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/release-1.6.0.md).

## Addon-Entwicklung und Wiki

Das Vorlagen-ZIP und [examples/computer-ui](https://github.com/TeutonStudio/TeutonLib/tree/v1.6.0/examples/computer-ui) zeigen die Einbindung eines eigenen Computerbildschirms. Die öffentliche API ist unter `api` sowie den dokumentierten Client-Oberflächen erreichbar. Öffentliche Minor-Erweiterungen bleiben kompatibel; Implementierungsdetails der Integrationen und Mixins sind keine stabilen Erweiterungspunkte.

Die Wiki-Quellen liegen versioniert unter [docs/wiki](https://github.com/TeutonStudio/TeutonLib/tree/v1.6.0/docs/wiki). Jede Funktionsseite ist eine eigene Markdown-Datei. Dieselben Dateien werden im separaten GitHub-Wiki-Repository veröffentlicht; `Home.md`, `_Sidebar.md` und `_Footer.md` bilden die Navigation.

TeutonLib und Beispielcode stehen unter MIT; `TEMPLATE_LICENSE.txt` gilt für die ursprünglich von NeoForged gelieferten MDK-Dateien.

[Installation](Installation) · [Alle Funktionen](Home)

## Abbildung


Aktuell ausgeführte 160 JUnit-Tests des unveränderten Release-Quellstands: keine Fehler oder übersprungenen Tests.

![Entwicklung und Prüfungen: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-summary.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
