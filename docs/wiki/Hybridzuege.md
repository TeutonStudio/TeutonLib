# Hybridzüge

Hybridzüge verbinden Creates Gleisfahrt mit einem laufenden Sable-Aufbau. Create verwaltet Gleise, Fahrplan und Zugsteuerung; die Wagen behalten echte Maschinen, Inventare, Flüssigkeitstanks und Unter-Contraptions. TeutonLib verwendet dafür den Wagentyp `teutonlib:hybrid_carriage`.

## Voraussetzungen und Aktivierung

Geprüft sind Create **6.0.10**, Sable **2.0.6** und Create: Train Functionality **0.1.47** auf Minecraft **1.21.1** / NeoForge **21.1.234**. TeutonLib muss auf Server und Clients installiert sein. Immersive Portals ist erst für [Portaltransfers](Zug-Portaltransfer) erforderlich.

```toml
[trains]
hybridByDefault = true
```

Die Option steht in `<Weltordner>/serverconfig/teutonlib-server.toml`, ist standardmäßig aktiv und übernimmt die automatische Konvertierung auch für Wagen ohne Functional Fob. Vorhandene gespeicherte Create-Wagen werden beim Laden auf den Hybridtyp umgestellt; vorhandene CTF-Aufbauten werden an ihren laufenden Simulationsbestand gebunden.

Neue Züge entstehen direkt am Bahnhof. Der Ablauf steht unter [Bahnhofsbau](Bahnhofsbau).

## Fahren und umbauen

| Modus | Verhalten |
| --- | --- |
| `DRIVING` | Der Zug kann fahren. Blockarten und belegte Positionen bleiben fest; Maschinen, Inventare und Änderungen von Blockzuständen laufen weiter. |
| `EDITING` | Der gesamte Zug bleibt stehen. Die echten Sable-Blöcke lassen sich bearbeiten; Maschinen und Unter-Contraptions bleiben aktiv. |

Neben einem angehaltenen und vollständig geladenen Zug stehen diese Befehle zur Verfügung:

```text
/teutonlib train status
/teutonlib train edit
/teutonlib train run
```

Sie erfordern Berechtigungsstufe **2** und wählen den nächsten geladenen Wagen im Umkreis von **32 Blöcken**. Die Modusänderung betrifft dessen ganzen Zug. Vorher werden sämtliche Wagen geprüft. Während einer Portalübertragung oder bei fehlenden Wagenaufbauten ist der Wechsel gesperrt. Die normale Zerlegung am Bahnhof bleibt möglich.

Ein Moduswechsel kopiert oder serialisiert die Inventare nicht neu. Der Modus wird im vorhandenen Sable-Aufbau gespeichert und bleibt beim Neustart erhalten. CTFs explizite Konvertierung eines angehaltenen Wagens führt in den Umbaumodus; die automatische Konvertierung verwendet den Fahrmodus.

## Was während der Fahrt weiterläuft

Redstone, Lampen, Türen und Ofen-Betriebszustände dürfen sich im Fahrmodus ändern. BlockEntities, Tanks und geplante Blockticks arbeiten auf dem tatsächlichen Aufbau weiter. Gewöhnliches Platzieren und Abbauen bleibt gesperrt, einschließlich direkter Chunk-Schreibzugriffe und Creates schleichendem Schraubenschlüsselabbau.

Create-Unter-Contraptions dürfen ihre eigenen erfassten Blöcke zusammenbauen und wieder absetzen. Diese eng begrenzte Ausnahme erlaubt Maschinen keine beliebigen Umbauten am übrigen Wagen.

| Erweiterung | Verhalten und geprüfte Basis |
| --- | --- |
| [Mechanische Create-Lager](Lager-und-Untercontraptions) | Echte Create-Contraptions und Nutzlasten bleiben betriebsfähig. |
| [Swivel Bearing](Lager-und-Untercontraptions) | Simulated **1.3.2** behält seine Sable-Unterebene und Nutzlast. Die Struktursperre folgt der tatsächlichen Elternverbindung zum Wagen. |
| [Create Big Cannons](Create-Big-Cannons-auf-Hybridzuegen) | **5.11.7** mit Ritchie's Projectile Lib **2.1.2**: normale und feste Kanonenlager, geladene Geschosse, Treibladungen und Autokanonenpuffer. Schüsse berechnet CBC selbst. |
| [Create: Radars](Radar-Typ-und-Speicherung) | **0.4.9.2-1.21.1**: `teutonlib:radar` erhält Typ, Empfänger, Ausrichtung, Schüsselzahl und Creative-Status beim Speichern und Laden. |
| Physische Schlauchrolle | Der echte Fluidhandler bleibt aktiv. Beim Portalwechsel reist die Schlauchkopf-UUID mit; Verbindungen zu zurückbleibenden Anschlüssen werden vorher gelöst. |
| Docking Connector | Native Simulated-Physik und Item-, Flüssigkeits-, FE- und ComputerCraft-Verbindungen bleiben erhalten. Siehe [Zug-Docking](Zug-Docking). |

Beim Portaltransfer werden laufende Lager und Kanonen kontrolliert abgesetzt und nach der Zielinitialisierung wieder zusammengesetzt. Das gilt auch nach einer Rückabwicklung und für verschachtelte Swivel-Nutzlasten. Dabei können Entity-UUIDs neu zusammengesetzter Contraptions wechseln.

## ContraptionLevel und Leistung

Im Fahrmodus verwendet jeder Wagen ein **ContraptionLevel** mit zwischengespeicherter Blockgeometrie pro 16³-Abschnitt. Blockänderungen aktualisieren betroffene Abschnitte; Bewegung und Drehung verwenden vorhandene Geometrie mit einer neuen Transformation. BlockEntity-Renderer, Flywheel-Animationen, Flüssigkeiten und animierte Texturen bleiben aktiv.

**F3+B** zeigt einen orangenen Rahmen um das transformierte ContraptionLevel. Im Umbaumodus verwendet der Wagen Sables normale Darstellung; beim Wechsel in den Fahrmodus wird der aktuelle Aufbau erfasst. Weitere Details stehen unter [Portal-Darstellung und Beleuchtung](Portal-Darstellung-und-Beleuchtung).

Der Fahrmodus behält einen echten Sable-Simulationsplot samt Physikanbindung. Die zwischengespeicherte Darstellung beseitigt weder die Kosten der laufenden Blocksimulation noch sämtliche Sable-Physikkosten.

## Abschalten und vorhandene Welten

Mit `hybridByDefault = false` entfallen automatische Hybridkonvertierung und Struktursperre. Neue Bahnhofssitzungen verwenden Creates Baubetrieb; vorhandene Sable-Bausitzungen lassen sich beenden. Gespeicherte Hybrid-Wagentypen bleiben lesbar. Der Client verwendet dann wieder Sables normale Darstellung.

Bei aktivierter Funktion verschwinden **Functional Fob** und **Chunk Anchor Lever** aus Kreativ- und Suchreiter. Ihre Registrierungen, vorhandenen Blöcke, Items und Rezepte bleiben erhalten. Beim Abschalten erscheinen die Einträge wieder. Der Server synchronisiert den Zustand beim Beitritt und bei Änderungen.

## Entwicklerprüfung

```sh
python3 tools/verify_hybrid_trains.py
python3 tools/verify_hybrid_trains.py --packaged
python3 tools/verify_train_portal_client.py --sodium --render-only --verify-flywheel
```

Die Serverprüfung erfasst Bahnhofsbau, beide Modi, Schreibsperre, Maschinen, echte Kanonenschüsse, Fracht, Schlauchkopf, Portal-Hin-/Rückfahrt, Rückabwicklung und Weltneustart. Die Clientprüfung erfasst tatsächliche ContraptionLevel-Instanzen, Animationen, F3+B-Rahmen, Kreativmenü und Portalbeleuchtung. Die bekannte Grenze stehender Portalmitfahrer ist unter [Zug-Portaltransfer](Zug-Portaltransfer) beschrieben.

Weiter: [Bahnhofsbau](Bahnhofsbau) · [Zug-Portaltransfer](Zug-Portaltransfer) · [Zug-Docking](Zug-Docking)

Quellen für 1.6.0: [Hybridlaufzeit](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/hybrid/HybridTrainRuntime.kt), [Darstellung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/client/train/ContraptionLevel.kt), [Versionsprüfung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/HybridTrainCompatibility.kt).
