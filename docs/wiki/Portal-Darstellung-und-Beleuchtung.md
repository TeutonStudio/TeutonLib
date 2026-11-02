# Portal-Darstellung und Beleuchtung

Baubare Züge bleiben während einer Portalpassage auf beiden Seiten der Portalfläche sichtbar. TeutonLib verwendet dafür den vorhandenen Sable-Aufbau und Immersive Portals' natives Straddling-System: Jede Ansicht zeichnet den räumlich passenden Teil des Wagens und schneidet ihn an der Portalfläche ab.

## Voraussetzungen

Die geprüfte Kombination besteht aus Create **6.0.10**, Flywheel **1.0.6**, Sable **2.0.6**, Create: Train Functionality **0.1.47** und Immersive Portals: Aeronautics **2.6.4**. Optional geprüft sind Sodium **0.8.12-beta.2+mc1.21.1** und Steam ’n’ Rails **0.2.0+neoforge-mc1.21.1**. Minecraft **1.21.1** und NeoForge **21.1.234** bilden die Basis.

Die Integration muss auf Server und Clients vorhanden sein. Für die Anzeige ist kein zusätzlicher Spielerbefehl erforderlich. Die [Übertragung des Wagenaufbaus](Zug-Portaltransfer) und seine Darstellung verwenden denselben lebenden Simulationsbestand.

## ContraptionLevel im Fahrbetrieb

[Hybridzüge](Hybridzuege) verwenden im Fahrmodus ein **ContraptionLevel**. Es hält die kompilierte Blockgeometrie in 16³-Abschnitten vor und übernimmt Sables Renderdaten-Schnittstelle. Immersive Portals kann dieselben Puffer für beide geschnittenen Portalansichten verwenden.

Eine Blockänderung aktualisiert die betroffenen Abschnitte. Bewegung und Drehung ändern die Transformation der vorhandenen Geometrie. BlockEntity-Renderer, bewegliche Flywheel-Teile, Flüssigkeiten und animierte Atlastexturen behalten ihre laufenden Renderpfade.

**F3+B** zeigt einen orangenen Rahmen um den transformierten ContraptionLevel-Aufbau. Ein Moduswechsel in `EDITING` verwendet wieder Sables normale Darstellung. Beim Zurückwechseln wird der aktuelle Aufbau erfasst. Entfernte Plots, Ressourcen-Neuladen und Moduswechsel geben alte Renderdaten frei.

## Licht aus der sichtbaren Welt

Jede sichtbare Wagenhälfte erhält die Beleuchtung der Welt, in der sie gerade dargestellt wird. Eine in die Oberwelt projizierte Netherhälfte darf deshalb nicht den fehlenden Nether-Himmel übernehmen. Die lokale Abschattung des Aufbaus und dessen eigene Lichtquellen bleiben erhalten.

TeutonLib passt die Lichtwerte der vorübergehend projizierten Blockmeshes an und berücksichtigt die sichtbare Welt auch für Flywheel-Animationen. Nach dem Renderpass werden temporäre Licht- und Instanzänderungen zurückgesetzt. Gespeicherte Blöcke und serverseitige Lichtdaten ändern sich dadurch nicht.

Die Korrektur sichert die ursprünglichen Vertexdaten im CPU-Speicher und verwendet begrenzte Caches. Bei Bedarf werden zusätzliche Plot-Lichtsections über Sables Lichtallocator angefordert und nach der Ansicht wieder freigegeben. Ein Auslesen von GPU-Puffern findet nicht statt.

## Wagenverbindungen

Creates sichtbare Verbindung zwischen zwei Wagen muss ihre beiden Enden derselben Portalansicht zuordnen. TeutonLib projiziert die Kupplungsanker passend und schneidet das gezeichnete Segment an der Portalfläche. Dadurch verbindet die Grafik keine Quellposition direkt mit weit entfernten Zielkoordinaten.

Die tatsächliche Zugkopplung und Gleisfahrt bleiben bei Create. Diese grafische Wagenverbindung ist von [Simulated-Docking-Connectoren](Zug-Docking) zu unterscheiden, die eigene Physik- und Ressourcentransfers besitzen.

## Leistung und Grenzen

Zwei sichtbare Portalansichten verursachen zusätzliche Renderarbeit. Die Brücke verwendet vorhandene Meshes; das ContraptionLevel bleibt aus dem lebenden Plot abgeleitet. Es entsteht kein zweiter tickender Maschinen- oder Inventarbestand.

Die zwischengespeicherte Darstellung ersetzt die laufende Sable-Simulation und Physikanbindung nicht. Frühere, auf 120 FPS begrenzte Messungen sind keine allgemeine Aussage über unveränderte Bildraten. Beliebige Shader, Renderer, weitere Physikmods und beliebige Portalanordnungen sind durch die geprüften Profile nicht abgedeckt.

Die bekannte Positionsabweichung stehender Aether-Mitfahrer betrifft die Wagenkörperübergabe und bleibt unter [Zug-Portaltransfer](Zug-Portaltransfer) dokumentiert. Ein erfolgreicher Grafiktest allein prüft diesen Fall nicht.

## Entwicklerprüfung

```sh
python3 tools/verify_train_portal_client.py --verify-flywheel --flywheel-backend=instancing
python3 tools/verify_train_portal_client.py --sodium --render-only --verify-flywheel
python3 tools/verify_train_portal_client.py --aether --sodium --render-only --verify-flywheel --flywheel-backend=indirect
```

Die Läufe starten einen eigenen Server und OpenGL-Client und erfassen echte Renderaufrufe sowie Screenshots vor, während und nach der Passage. `--verify-flywheel` verlangt tatsächlich gezeichnete Motoren und Zahnräder samt angewendeten Lichtwerten. Die Prüfung erfasst außerdem Kupplungssegmente, aktuelle ContraptionLevel-Instanzen, den orangenen F3+B-Rahmen und Konfigurationswechsel.

Berichte und Bilder stehen unter `build/reports/train-portal-client/` und in der jeweils referenzierten Testinstanz. Die ausführlichen historischen Abnahmen stehen in der [technischen Portaldokumentation für 1.6.0](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/train-portal-compatibility.md).

Weiter: [Hybridzüge](Hybridzuege) · [Zug-Portaltransfer](Zug-Portaltransfer) · [Zug-Docking](Zug-Docking)

Quelle für 1.6.0: [ContraptionLevel-Darstellung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/client/train/ContraptionLevel.kt).
