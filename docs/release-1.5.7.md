# TeutonLib 1.5.7

* Korrigiert die nicht-nullbaren Weltparameter des Portal-Handlers aus 1.5.6.
  Railways' `Carriage.travel(null, ...)` erhält seinen ursprünglichen Listener zurück.
* Implementiert koordinierte Portalübertragungen baubarer Create-Züge mit CTF 0.1.47,
  IP Aeronautics 2.6.4, Create 6.0.10 und Sable 2.0.5/2.0.6. Aufbauten werden nicht
  neu aus der alten Contraption-Kopie erzeugt.
* Migriert Wagen-/Plotzuordnung und Lagerbrücke, verhindert doppelte Konvertierungen
  und behandelt die Quellentfernung während des Transfers nicht als Zuglöschung.
* Bindungsfehler verwenden IPs Original-Sicherung für den Rollback. Der Chunk-Cache
  wird beim Wiederverwenden eines Plots invalidiert.
* Gespeicherte/verwaiste Aufbauten und nicht unterstützte Portalarten bleiben geschützt.
  Bereits verlorene Blöcke werden nicht rekonstruiert.

Siehe [Versionsmatrix, technische Einzelheiten und Testgrenzen](train-portal-compatibility.md).
