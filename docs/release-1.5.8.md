# TeutonLib 1.5.8

Baubare CTF-/Sable-Züge verwenden während der Portalberührung jetzt IP Aeronautics'
native Straddling-Darstellung auf beiden Portalseiten. Die Erkennung beginnt mit
der Berührung des Aufbaus, auch vor dem ersten Rad, und hält die Sitzung bei
Stillstand, Umkehr und nach Entfernung der letzten Quell-Wagenansicht aufrecht.

Beim Seitenwechsel übernimmt die Brücke IPs native Handoff-Markierung und sendet
die letzte Pose erneut. Dabei verwendet sie IPs clientseitige Mesh-Übergabe. Deren
`HandoffLatch` überspringt Übergaben innerhalb derselben Dimension; eine universell
lückenlose Darstellung jedes Übergangsframes ist damit nicht zugesichert. Der
vorhandene Aufbau bleibt die einzige CTF-Instanz; eine alte Create-Blockkopie wird
nicht erneut gezeichnet.

Für Spieler auf der entfernten Portalseite werden die nativen Ladeabhängigkeiten
des ursprünglichen Sublevels vorübergehend ergänzt. Dies geschieht nur, solange
ein Spieler die entfernte Hälfte beobachtet, und erzeugt keine dauerhaften Sable-
Ladetickets. Die Ergänzung endet beim Verlassen des Portals, bei der Übergabe, beim
Entladen oder beim Zurücksetzen. Nach einem Dimensionswechsel werden Sitzung und
Plot-Tracking einmal neu angekündigt, weil der Client seine bisherigen Welten
verworfen hat. Bereits lokal getrackte aktive Zugaufbauten erhalten dabei einmalig
einen nativen vollständigen Sable-Datenstand.

Die Überlappung wird mit einer konstanten Anzahl geometrischer Trennachsen geprüft.
Lokale Portalsuchen werden für fünf Ticks zwischengespeichert und bei Bewegung aus
dem Suchbereich sofort erneuert. Vorhandene Meshes werden wiederverwendet, ohne
zusätzliche Blockscans. Eine zweite sichtbare Ansicht benötigt zusätzliche
Renderarbeit; unveränderte FPS für beliebige Szenen werden damit nicht zugesichert.
Die ergänzten Ladeabhängigkeiten sind pro Welt und Servertick zwischengespeichert
und werden bei Änderungen an der Lebensdauer einer Ansicht sofort invalidiert.
Für die erneute Ankündigung wird IPs vorhandener Spieler-Durchlauf verwendet.

Sable bleibt optional. Falls installiert, ist **mindestens 2.0.6** erforderlich.
Die private Portal-Integration und das CSO-Modul sind für **Sable 2.0.6** verifiziert.
Weitere Voraussetzungen sind Create 6.0.10, CTF 0.1.47 und IP Aeronautics 2.6.4.
Unterstützt werden passende Portalgleise mit Maßstab 1. TeutonLib ist auf Server
und Clients zu aktualisieren.

Alle **119 JUnit-Tests** bestehen. Das gepackte Artefakt mit Sable 2.0.6 bestand die
Fahr-, Rollback- und Neustartprüfung; auch der Serverstart ohne optionale Mods besteht.
Die grafische Prüfung mit `python3 tools/verify_train_portal_client.py` bestand alle
**7 Aufnahmen**. Quell- und Zielansicht verwenden den aktuellen Aufbau einschließlich
eines nachträglich ergänzten Blocks. Der Beobachterwechsel erfolgte administrativ
mit `portal tp`; eine Grafikprüfung als sitzender Zugpassagier ist darin nicht enthalten.
Bericht: `build/reports/train-portal-client/20261027-193542-42e19f99/report.json`.

Bei einem 120-FPS-Limit lagen die medianen Framezeiten bei 8,340399 ms ohne
Portalberührung und zwischen 8,345869 und 8,398059 ms in den aktiven Ansichten.
Dies ist kein unlimitierter Benchmark und belegt keine allgemein unveränderte
Framerate.

Siehe [Funktionsweise, Versionsmatrix, Prüfungen und Grenzen](train-portal-compatibility.md).
