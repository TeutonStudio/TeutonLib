# TeutonLib 1.5.9

Stehende Mitfahrer werden nach der gemeinsamen Portalübergabe wieder an den
tatsächlich geladenen Sable-Wagen gebunden. IPs Übertragung ersetzt dessen
Sublevel-Instanz, auch wenn UUID und Dimension gleich bleiben. Ein Verweis auf
die entfernte Quellinstanz reicht deshalb nicht aus, um weiter mitzufahren.

Die Brücke erhält die Position im Wagen, Blickrichtung und Geschwindigkeit unter
der Portaltransformation. Bereits vor dem Wagenmittelpunkt durchgetretene
Mitfahrer werden über den konkreten Boden der projizierten Wagenhälfte erkannt.
Benachbarte Wagen werden dabei anhand ihrer eigenen Körper und Kollisionsflächen
unterschieden. Die allgemeine Hüllbox-Mitnahme darf einen stehenden Spieler auf
einem anderen Wagen nicht beim Übergang des Nachbarwagens vorzeitig versetzen.
Liegt der Spieler noch auf der Quellhälfte seines eigenen Wagens, bleibt er dort
bis zu seinem eigenen Portalübertritt. Nach einem Rollback wird die Bindung an die wiederhergestellte
Quellinstanz erneuert. Bei sitzenden Passagieren schließt die Brücke nach dem
Wagenwechsel IPs native Sitzübergabe ab, damit ein früher Portal-Sitzproxy wieder
auf den echten Sitz im übertragenen Wagen zurückgeführt wird.

Enthalten ist außerdem die beidseitige Darstellung aus **1.5.8**. Die am
31.10.2026 untersuchte Prism-Instanz verwendete noch **1.5.7**, das diese
Darstellung nicht enthält. **Server und Clients müssen aktualisiert werden.**
Die geprüfte Kombination bleibt Create 6.0.10, CTF 0.1.47, IP Aeronautics 2.6.4
und Sable 2.0.6.

Die Grafikprüfung enthält jetzt zwei baubare Wagen mit unterschiedlichen Farben
und getrennten Dachhöhen. Sie prüft jeden Wagen vor der Berührung, bei Umkehr,
während der Übergabe und nach der vollständigen Durchfahrt. Ein zusätzlicher
Lauf lässt einen echten verbundenen Spieler ohne Sitz auf dem vorderen Wagen
hin- und zurückfahren und prüft seine Bindung nach beiden Übergaben über weitere
Serverticks. Die Sitzprüfung fährt ebenfalls in beide Richtungen und verlangt
anschließend einen echten Sitz im aktuellen Wagenplot. Ein gesonderter Lauf
erzwingt einen fehlgeschlagenen Transfer mit sitzendem Spieler und prüft die
Wiederherstellung des ursprünglichen Sitzes.

Prüfstand vom 31.10.2026: **122 JUnit-Tests**, alle **7 Mehrwagen-Aufnahmen**,
stehende und sitzende Hin-/Rückfahrt mit echtem verbundenem Spieler sowie
Sitz-Rollback bestanden. Grafikbericht:
`build/reports/train-portal-client/20261031-150037-ce93e941/report.json`.
Auch die gepackte JAR besteht alle acht Serverprüfungen einschließlich Neustart:
`build/reports/train-portals/packaged-2.0.6.json`.

Siehe [Funktionsweise und Prüfberichte](train-portal-compatibility.md).
