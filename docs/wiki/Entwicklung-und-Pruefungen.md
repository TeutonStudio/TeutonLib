# Entwicklung und Prüfungen

**Deutsch** · [English](Entwicklung-und-Pruefungen-en) · [Übersicht](Home)

TeutonLib verwendet Kotlin, Gradle Kotlin DSL und Java 21. Einige kleine dependency-isolierte bzw. statische Mixins sind in Java geschrieben. CC-Laufzeitmodule verwenden Lua; externe Prüftreiber Python. Dokumentationsbasis ist **1.6.1, Quellstand `e6c27c6`**; lokale Änderungen sind auf den Funktionsseiten gekennzeichnet.

## Build

```sh
./gradlew build releaseBundle --offline
python3 tools/verify_release.py
python3 tools/verify_wiki.py
```

Benötigt werden die originalen lokalen Mod-Binaries unter `dependencies/compile`, `dependencies/runtime` und `dependencies/verification`; sie sind nicht im Git-Repository enthalten. Die [Entwicklungsanleitung](https://github.com/TeutonStudio/TeutonLib/blob/master/docs/development-en.md) bietet den Einstieg; maßgeblich für aktuelle Abhängigkeiten und opt-in-Profile ist das [Buildskript](https://github.com/TeutonStudio/TeutonLib/blob/master/build.gradle.kts). Simulated zur Laufzeit ausschließlich über das Aeronautics-Bundle laden. Die mobile Essential-API-Extraktion gehört im aktuellen lokalen Stand zu TerrArchitecture.

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
| Schlauchführung und Überlastlösen (Entwicklung) | `python3 tools/verify_hose_cable.py` |
| Zwei-Client-Schlauchdarstellung und Flux-Modell (Entwicklung) | `python3 tools/verify_hose_cable_client.py` |
| Bahnhofsbau und Leerplot-Neustart (Entwicklung) | `python3 tools/verify_station_build.py` |
| Native Bogey-Vorschau (Entwicklung) | `python3 tools/verify_station_preview.py` |
| Antennenseiten, Ortung und Sable-Darstellung | `python3 tools/verify_peripheral_antenna_client.py --sable --visual` |
| Sable-Tankoberflächen (Entwicklung) | `python3 tools/verify_tank_fluid.py` |
| Mekanism-Modelle | `python3 tools/verify_mekanism_models.py` |

Die Skripte verwenden gesonderte lokale Testinstanzen unter `run/smoke/`; sie sind keine Anleitung zum Ausführen in persönlichen Spielständen. Grafikprüfungen benötigen einen OpenGL-Desktop. Die Testmods werden ausschließlich über opt-in-Profile geladen und nicht ausgeliefert. Serverprüfungen nacheinander ausführen.

Ein erfolgreich kompilierter Test ist kein ausgeführter Spieltest. Historische Ergebnisse und Grenzen stehen in den Notizen zu [1.6.0](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/release-1.6.0.md) und [1.6.1](https://github.com/TeutonStudio/TeutonLib/blob/e6c27c6/docs/release-1.6.1.md). Die Auflistung eines Prüftreibers bestätigt nicht dessen erfolgreichen Lauf am aktuellen Quellstand. Insbesondere ist die neue [Schlauch-Clientabnahme](Physische-Schlauchrolle#entwicklerprüfung-und-prüfgrenzen) noch offen.

## Addon-Entwicklung und Wiki

Das Vorlagen-ZIP und [examples/computer-ui](https://github.com/TeutonStudio/TeutonLib/tree/v1.6.0/examples/computer-ui) zeigen die Einbindung eines eigenen Computerbildschirms. Die öffentliche API ist unter `api` sowie den dokumentierten Client-Oberflächen erreichbar. Öffentliche Minor-Erweiterungen bleiben kompatibel; Implementierungsdetails der Integrationen und Mixins sind keine stabilen Erweiterungspunkte.

Die Wiki-Quellen liegen unter [docs/wiki](https://github.com/TeutonStudio/TeutonLib/tree/master/docs/wiki). Jede Funktionsseite besitzt eine deutsche und eine englische Markdown-Datei. Dieselben Dateien samt `assets/` bilden den Inhalt des separaten GitHub-Wiki-Repositorys; `Home.md`, `English.md`, `_Sidebar.md` und `_Footer.md` bilden die Navigation. Eine lokale Bearbeitung veröffentlicht diese Dateien noch nicht.

`python3 tools/verify_wiki.py` prüft das Seiteninventar, Sprachlinks, interne Ziele und Überschriften, Codeblöcke, Bilddateien und Herkunftsnachweise sowie verlinkte Quellpfade. Historische Quellen bleiben an ihre Version gebunden; unveröffentlichte Änderungen verweisen auf `master`. Nach einer Veröffentlichung müssen Bildladen und Darstellung auf GitHub zusätzlich geprüft werden.

TeutonLib und Beispielcode stehen unter MIT; `TEMPLATE_LICENSE.txt` gilt für die ursprünglich von NeoForged gelieferten MDK-Dateien.

[Installation](Installation) · [Alle Funktionen](Home)

## Abbildung

Historischer Prüflauf vom 13.11.2026: 160 JUnit-Tests des unveränderten 1.6.0-Quellstands, keine Fehler oder übersprungenen Tests. Kein Testergebnis für 1.6.1 oder die lokalen Änderungen.

![Entwicklung und Prüfungen: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-summary.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
