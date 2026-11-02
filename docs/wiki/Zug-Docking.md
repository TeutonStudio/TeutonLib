# Zug-Docking

Simulated-Docking-Connectoren an Hybridwagen verbinden den Zug mit eigenständigen Sable-Körpern. Verriegelte Anschlüsse verwenden weiterhin Simulateds native Physikverbindung sowie Item-, Flüssigkeits-, FE- und ComputerCraft-Verbindungen. Eine angedockte Plattform bleibt eine eigene Sable-Konstruktion.

## Voraussetzungen und Bedienung

Geprüft sind Create **6.0.10**, Sable **2.0.6**, Create: Train Functionality **0.1.47** und Simulated **1.3.2**. Für Portalpassagen kommt Immersive Portals: Aeronautics **2.6.4** hinzu. Die Basis ist Minecraft **1.21.1** / NeoForge **21.1.234**; TeutonLib gehört auf Server und Clients.

Connectoren wie gewohnt gegenüberliegend montieren, ausrichten und versorgen. Das [analoge Docking](Analoge-Dockingports) ist standardmäßig aktiviert:

| Redstone-Stärke | Anforderung | Abstand zwischen den Basismittelpunkten |
| --- | --- | --- |
| **0–5** | eingefahren / OFF | keine aktive Kopplung |
| **6–10** | HALF, Ausleger 0,5 Blöcke | 2 Blöcke bei HALF/HALF oder HALF/FULL |
| **11–15** | FULL, Ausleger 1 Block | 3 Blöcke bei FULL/FULL |

Die Anschlüsse handeln die kürzere angeforderte Länge aus. Magnetische Annäherung, zulässige Winkel und Verriegelung bleiben Teil des nativen Dockings. Der FULL-Ausleger darf im gesperrten Hybridwagen seine Basis und den unmittelbar davorliegenden Hilfsblock aktualisieren; der übrige Wagen bleibt im Fahrmodus gegen Umbauten geschützt.

## Plattformen während der Fahrt

Die Plattform wird über die echte Docking-Physik mitgeführt. Sie erhält durch das Andocken kein eigenes ContraptionLevel und wird nicht zu einer zusätzlichen Create-Wageninstanz. Ihre Inventare und Transferbehälter bleiben im ursprünglichen Körper.

**Physikgrenze:** Angedockte Lasten können unter Gewicht und Hebelwirkung durchhängen oder kippen, obwohl beide Connectoren verriegelt sind und ein gültiger nativer FixedConstraint besteht. Lokale Vergleichstests vom **02.11.2026** zeigen dieses Verhalten auch mit normalen Create-Wagen bei deaktivierter Hybridfunktion. Es ist damit kein bloßer ContraptionLevel-Darstellungsfehler. Größere Masse und längere Verbindungen verstärken die gemessene Abweichung; die genaue Ursache im gemeinsamen Physikpfad bleibt offen.

Beim Bauen sind deshalb ausreichend Bodenfreiheit und die tatsächlich bewegten Außenmaße zu berücksichtigen. Ein bestehender Lock ist keine Zusicherung einer vollkommen starren, lastunabhängigen Lage.

## Passung am Portal

TeutonLib prüft angedockte mobile Körper gegen die **rechteckige Portalöffnung**. Alle acht transformierten Ecken ihrer vollständigen Plotgrenzen müssen in die Öffnung passen. Dabei zählt der tatsächliche Körper einschließlich seiner Ausrichtung, nicht nur die Anschlussposition.

| Angedockter Körper | Verhalten |
| --- | --- |
| Mobile Plattform passt vollständig. | Sie reist koordiniert mit; ihre nativen Verbindungen werden im Ziel wiederhergestellt. |
| Körper passt nicht durch die Öffnung. | Die Verbindung wird schon am ersten Gleis-Portalübergang gelöst. Der Körper bleibt mit normaler Physik in der Ausgangswelt. |
| Weltfeste Dockingstation | Sie bleibt in der Welt und wird nicht als Wagenlast mitgenommen. |
| Eigenständig geführter anderer Zugwagen | Er wird nicht als angehängte mobile Nutzlast dieses Wagens behandelt. |
| Nichtrechteckiges Portal | Es wird keine vollständige Passung einer angedockten Nutzlast angenommen. |

Ein zu großer, gelöster Partner darf sich während der Trennung nicht sofort wieder magnetisch verriegeln oder den unabhängigen Schifftransfer als Umgehung nutzen. Er bleibt ein normaler physischer Körper; TeutonLib friert ihn nicht in der Ausgangswelt ein.

## Übertragung und Wiederherstellung

Für eine passende Gruppe löst TeutonLib die Verbindungen kurz, überträgt die mobilen Partner über Sables native Transaktionen und stellt die Anschlüsse im Ziel wieder her. Damit kehren Physikconstraint und Ressourcentransfers zurück. Eine zuvor bestehende Verriegelung darf über Simulateds native Wiederherstellung zurückkehren, auch wenn gewöhnliches Durchhängen die Toleranz für eine neue Verbindung überschreitet. Normale neue Verbindungen behalten ihre Geometrieprüfung.

Wenn die Wagenübergabe scheitert, werden bereits übertragene Partner zurückgeführt und die Ausgangsverbindungen wiederhergestellt. Die einzelnen Körper verwenden native Sable-Transaktionen. Für die gesamte Gruppe existiert keine zusätzliche gemeinsame Transaktionsdatei, die einen Serverabsturz zwischen den Einzelübertragungen als atomaren Vorgang absichert.

## Diagnose und Entwicklerprüfung

```sh
python3 tools/record_train_docking_demo.py --case wide --check
python3 tools/record_train_docking_demo.py --case narrow --check
python3 tools/record_train_docking_demo.py --case wide --check --rollback
```

Diese Prüfungen verwenden eigene Testwelten mit zwei echten Wagen und einer dynamischen Plattform. Sie erfassen native Locks und Constraints, bidirektionale Transfers, Körper-UUIDs, Fracht und tatsächliche Bewegung über die Docking-Verbindung. Für den dokumentierten Stand vom **02.11.2026** bestanden breite Öffnung, zu enge Öffnung und absichtlich ausgelöste Rückabwicklung.

Ohne `--check` zeichnet derselbe Ablauf das Minecraft-Bild mit durchgehend aktivem F3+B auf. `tools/finish_train_docking_demo.py <Berichtsverzeichnis>` ergänzt deutsche Einblendungen. Vergleichsskripte für Durchhängen und Lasten liegen unter `tools/verify_train_docking_sag.py` und `tools/verify_train_docking_weight.py`; sie dienen der Diagnose und belegen keine Behebung der Physikgrenze.

Weiter: [Hybridzüge](Hybridzuege) · [Zug-Portaltransfer](Zug-Portaltransfer) · [Portal-Darstellung und Beleuchtung](Portal-Darstellung-und-Beleuchtung)

Quelle für 1.6.0: [Native Dockinggruppen und Portalpassung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/train/TrainPortalDocking.kt).
