# Zug-Portaltransfer

TeutonLib überträgt baubare Create-Zugaufbauten gemeinsam mit ihrem Wagen durch passende Portalgleise von Immersive Portals: Aeronautics. Die vorhandene Sable-Simulation, Inventare, Maschinen und gespeicherte Wagenzuordnung reisen mit.

## Unterstützte Kombination

| Bestandteil | Geprüfte Version |
| --- | --- |
| Minecraft / NeoForge | **1.21.1 / 21.1.234** |
| Create | **6.0.10** |
| Flywheel | **1.0.6** |
| Sable | **2.0.6** |
| Create: Train Functionality | **0.1.47** |
| Immersive Portals: Aeronautics | **2.6.4** |

Die Brücke muss auf Server und Clients installiert sein. Sable und die anderen Integrationen sind optionale TeutonLib-Module; die Portalbrücke wird nur für die geprüfte Kombination aktiviert. Ein neueres Modrelease ist dadurch nicht automatisch unterstützt.

## Einen Zug hindurchfahren lassen

1. Ein durchquerbares Immersive-Portal-Paar mit **Maßstab 1** und gültigen Portalgleisen anlegen. Die native IP/Create-Gleisverbindung muss beide Seiten verbinden.
2. Den aufgebauten Zug über seine normalen Create-Steuerungen oder seinen Fahrplan fahren lassen. TeutonLib koordiniert den Transfer am tatsächlichen Gleis-Portalübergang.
3. Die Durchfahrt beenden, bevor der Zug in den Umbaumodus wechseln soll. Während ein Wagen mehrere Dimensionen berührt, bleibt der Moduswechsel gesperrt.

Für den Spielerbetrieb ist kein zusätzlicher Transferbefehl nötig. Portale können verschiedene Dimensionen oder zwei Orte derselben Dimension verbinden. Spiegel und skalierte Portale gehören nicht zum unterstützten Transferpfad.

Der unabhängige Schiffscanner darf einen Zugaufbau nicht vor dessen Wagen übertragen. Gewöhnliche Schiffe behalten ihren eigenen Transferpfad. Während der Durchfahrt verwendet der Zug genau einen lebenden Aufbau pro Wagen, auch wenn die [Darstellung auf beiden Portalseiten](Portal-Darstellung-und-Beleuchtung) sichtbar ist.

## Maschinen, Fracht und Rückabwicklung

Der Transfer verwendet die bestehenden Sable-Transaktionen. TeutonLib bindet den übertragenen Aufbau an die passende aktuelle Wageninstanz und erhält dessen Inventare sowie gespeicherte Zuordnung. Ein Bindungsfehler löst die Rückabwicklung aus, statt einen zweiten Frachtbestand zu erzeugen.

Laufende Lager und unterstützte Kanonen werden vorher abgesetzt. Verschachtelte Swivel-Nutzlasten kehren rekursiv in ihre Elternplots zurück. Die Wiederanlaufdaten liegen am jeweiligen Controller und reisen mit seinen Blockdaten. Nach erfolgreicher Übergabe oder Rückabwicklung werden die Maschinen wieder zusammengesetzt. Neu zusammengesetzte Contraption-Entities können neue UUIDs erhalten.

Physische Schlauchköpfe reisen mit unveränderter Kopf-UUID. Eine Schlauchverbindung zu einem außerhalb des Zuges verbleibenden Anschluss wird vorher gelöst. Angedockte mobile Plattformen behandelt [Zug-Docking](Zug-Docking) gesondert nach ihrer vollständigen Portalpassung.

## Mitfahrer und bekannte Einschränkung

Sitzende und stehende Mitfahrer werden an die neu geladene Wageninstanz gebunden. Für echte sitzende Spieler ist eine Hin-/Rückfahrt mit stabiler Sitzbindung nachgewiesen.

**Bekannter Fehler bei stehenden Spielern:** In der dokumentierten Aether-Prüfung vom **31.10.2026** wechselte ein Spieler bereits vor der eigentlichen Wagenkörperübergabe durch das Portal. Bei der anschließenden Übergabe entstand eine Verschiebung seiner relativen Standposition von etwa **2,0–2,2 Blöcken**. Der vollständige Aether-Test für stehende Hin-/Rückfahrt gilt deshalb als fehlgeschlagen. Erfolgreiche Grafik- und Sitztests belegen keine Behebung dieses Fehlers.

Die Diagnose weist auf unterschiedliche interpolierte Client- und aktuelle Serverposen bei der Erfassung und Wiederherstellung hin. Für diesen Fall liegt keine Produktionskorrektur im dokumentierten Stand vor. Die ausführlichen Messwerte stehen in der [technischen Portaldokumentation für 1.6.0](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/train-portal-compatibility.md).

## Aether-Portale

Ein optionales Prüfprofil verwendet **Aether 1.21.1-1.5.10** und **owo-lib 0.12.15.5-beta.1+1.21**. Geprüft wird Aethers natives IP-Kompatibilitätspaket mit Glowstone-Rahmen und Aktivierung per Wassereimer. Portalcluster, Zielwelten und native Create-Gleisbindungen werden ausdrücklich nachgewiesen. Aether und owo sind keine zusätzlichen Pflichtabhängigkeiten von TeutonLib.

Das Aether-Grafikprofil und der Test für sitzende Spieler bestanden; die oben genannte Grenze für stehende Spieler bleibt bestehen.

## Entwicklerprüfung

```sh
./gradlew build compileTrainPortalTestKotlin --offline
python3 tools/verify_train_portals.py
python3 tools/verify_train_portals.py --packaged
python3 tools/verify_train_portals.py --aether --packaged
python3 tools/verify_train_portal_client.py --sodium --verify-flywheel
```

Die Runner verwenden eigene lokale Testwelten. Die Serverprüfung erfasst echte Gleis-/Portalübergänge, mehrere Wagen, gemischte Züge, Fracht, Rückabwicklung und erneutes Laden derselben Welt. Die Grafikprüfung benötigt einen OpenGL-Bildschirm. `--render-only` lässt Mitfahrerprüfungen aus; ein erfolgreicher Lauf mit dieser Option ist daher kein vollständiger Mitfahrernachweis.

Weiter: [Hybridzüge](Hybridzuege) · [Portal-Darstellung und Beleuchtung](Portal-Darstellung-und-Beleuchtung) · [Zug-Docking](Zug-Docking)

Quelle für 1.6.0: [Koordinierter Wagentransfer](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/train/TrainPortalTransfers.kt).
