# Bahnhofsbau

Ab TeutonLib 1.6.0 entstehen neue Hybridzüge direkt am Create-Bahnhof auf stationären Sable-Bauplots. Bogeys und Wagenaufbauten sind schon während des Bauens echte Sable-Blöcke. Jeder Wagen erhält einen eigenen Bauplot.

## Voraussetzungen

Die Funktion verwendet Minecraft **1.21.1**, NeoForge **21.1.234**, Create **6.0.10**, Sable **2.0.6** und Create: Train Functionality **0.1.47**. TeutonLib und seine erforderliche KotlinForForge-Version müssen auf Server und Clients installiert sein. Die Hybridfunktion muss in der Welt aktiviert sein:

```toml
[trains]
hybridByDefault = true
```

Die Einstellung steht in `<Weltordner>/serverconfig/teutonlib-server.toml` und ist standardmäßig eingeschaltet. Immersive Portals wird für den Bahnhofsbau selbst nicht benötigt.

## Einen Zug bauen

1. Einen Create-Bahnhof an einem geeigneten geraden Gleis einrichten und **Neuen Zug bauen** wählen. TeutonLib eröffnet sofort einen leeren stationären Bauplot; ein erster Bogey ist dafür noch nicht nötig.
2. Die Bogeys mit Creates normaler Gleisinteraktion platzieren. Die ersten zwei gehören zum ersten Wagen. Der dritte Bogey beginnt den nächsten Wagenplot.
3. Den jeweiligen Wagen auf seinem Bauplot aufbauen. Die normalen Mindestabstände und die Bahnhofsausrichtung gelten weiterhin. Getrennte Teile innerhalb desselben Wagenplots müssen für die Erfassung nicht miteinander verklebt sein.
4. Creates normale Anforderungen an Steuerung, Bogeys und Gleise erfüllen und **Zug bauen** wählen. Create führt seine eigene Prüfung und den Zusammenbau aus; TeutonLib übernimmt anschließend die Wagen als Hybridzüge.

Die Bauplots bleiben am Bahnhof fixiert. Sables automatische Auftrennung ist für diese Plots ausgesetzt, damit voneinander getrennte Bogeys und noch unfertige Wagenteile zusammenbleiben. Die Erfassung bleibt auf den eigenen Wagenplot begrenzt.

## Inventare und Maschinen

Die Bauplots besitzen die tatsächlichen Blöcke samt BlockEntities und Inventaren. Für Creates Zusammenbau werden diese nur während des synchronen Zusammenbauaufrufs an ihre Weltpositionen übergeben. Nach einem abgewiesenen Zusammenbau kehren die verbleibenden Teile samt ihren Daten in die Bauplots zurück.

Laufende Lager und unterstützte Unter-Contraptions werden für die Übergabe vorbereitet. Ihre Nutzlasten werden nach der Konvertierung wieder zusammengesetzt. Details zu Lagern, Radar und Kanonen stehen unter [Hybridzüge](Hybridzuege).

## Platzbedarf und Abbrechen

Der gesamte Baubereich muss an seinen Weltpositionen frei bleiben. Zwischenzeitlich dort platzierte Weltblöcke verhindern die Übergabe, bevor Wagen verschoben werden. Überlappende Wagenplots werden ebenfalls abgewiesen.

Beim regulären Verlassen des Baubetriebs bleiben die gebauten Blöcke und Inventare in der Welt erhalten. Ist die Rückgabe durch andere Blöcke versperrt, zuerst den Bereich freimachen. Wird der Bahnhof entfernt und ist eine Rückgabe nicht möglich, bleiben die Bauwerke als gewöhnliche Sable-Konstruktionen erhalten.

Unfertige Bauplots werden mit der Welt gespeichert. Ihre Identität und Fracht bleiben über einen Serverneustart erhalten.

## Häufige Fragen

| Beobachtung | Prüfung |
| --- | --- |
| Der nächste Bogey erzeugt einen weiteren Plot. | Je zwei Bogeys gehören zu einem Wagen; Creates Abstandsregeln gelten zusätzlich. |
| Der Zug lässt sich nicht zusammenbauen. | Creates Fehlermeldung lesen; Steuerung, Bogeys, Gleise und freie Weltpositionen prüfen. |
| Getrennte Wagenteile fallen während des Bauens nicht auseinander. | Der Bauplot hält die Teile dieses Wagens bis zum Zusammenbau gemeinsam. |
| Statt Sable-Bauplots erscheint der normale Create-Baubetrieb. | Die exakten Modversionen und `[trains] hybridByDefault` prüfen. |

Bei deaktivierter Hybridfunktion verwenden neue Bahnhofssitzungen wieder Creates normalen Baubetrieb. Bereits eröffnete Sable-Bausitzungen lassen sich zu Ende führen.

## Entwicklerprüfung

```sh
./gradlew build compileTrainPortalTestKotlin --offline
python3 tools/verify_hybrid_trains.py
python3 tools/verify_hybrid_trains.py --packaged
```

Die separate Testwelt prüft leeren Bauplot, native Bogey-Platzierung, Wagenaufteilung, stationäre Lage, fehlgeschlagenen Zusammenbau, blockierte Übergabe, Fracht und laufende Lager. Ein zweiter Serverstart prüft den gespeicherten Bauplot und die Rückgabe beim Abbrechen. Die Testmod gehört nicht zur veröffentlichten JAR.

Weiter: [Hybridzüge](Hybridzuege) · [Zug-Portaltransfer](Zug-Portaltransfer) · [Zug-Docking](Zug-Docking)

Quellen für 1.6.0: [Bahnhofslaufzeit](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/hybrid/StationBuildRuntime.kt), [Bahnhofsprüfung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/trainPortalTest/kotlin/de/TeutonStudio/TeutonLib/traintest/StationBuildVerification.kt).
