# TeutonLib 1.5.15

## Schlauchkupplung am Create Item Drain

- `create:item_drain` übernimmt die Schlauchkupplung. Der zusätzliche
  `teutonlib:hose_connector`-Block samt Item und Rezept entfällt.
- Mit dem Create-Schraubenschlüssel einen ausgefahrenen Schlauchkopf aufnehmen
  und den Item Drain rechtsklicken. Ein Rechtsklick auf den belegten Drain nimmt
  den Kopf wieder auf; ein Rechtsklick in die Luft wirft ihn.
- Der getragene Kopf verdeckt kein Block-Klickziel mehr. Beim Abwählen des
  Schraubenschlüssels werden getragene Köpfe losgelassen; dies gilt auch beim
  Wechsel auf einen anderen Hotbar-Slot mit Schraubenschlüssel und beim Entfernen
  des Schraubenschlüssels aus der Nebenhand.
- Die Darstellung übernimmt die bereitgestellte Drain-/Schlauchkopf-Vorlage:
  Der Kopf sitzt bei 7/16 Blockhöhe im Drain, das Gitter wechselt passend zum
  Verriegelungszustand. Schlauch und Kopf folgen bewegten Sable-Konstruktionen.
- Der originale Drain behält seine BlockEntity, seinen 1.500-mB-Tank und seine
  normale Item-/Eimer-Verarbeitung. Einsetzen des Kopfs verbraucht keinen Tankinhalt.
- Die Schlauchverbindung arbeitet in beiden Richtungen mit erhaltenem Pumpendruck
  und gemeinsam halbiertem Durchfluss. UUID-Zuordnung, Sable-Montage, Zugkräfte
  und Überlastlösen bleiben erhalten, einschließlich Serverneustart.

## Entwicklungsumgebung und enthaltene Funktionen

`./gradlew runClient --offline` lädt im Standardprofil Create: Train Functionality
0.1.47 und Sable 2.0.6. `/teutonlib train edit` aktiviert am angehaltenen Zug den
Sable-Umbaumodus; `/teutonlib train run` gibt den Fahrmodus wieder frei.

Das Release enthält außerdem die seit 1.5.0 ergänzten optionalen Integrationen:
Hybridzüge, koordinierte Zug-Portalübertragungen einschließlich Darstellung und
Beleuchtung, Mekanism-Anschlussmodelle auf Sable, New-Age-Motorreparaturen sowie
die Adapter für FluidLogistics/Factory Logistics/TFMG und die mobile Kompatibilität.
Die genauen Versionsgrenzen und technischen Grenzen stehen in der README und den
jeweiligen Integrationsdokumentationen. Der Hybrid-Fahrmodus verwendet weiterhin
einen echten Sable-Simulationsplot.

## Voraussetzungen und Downloads

Minecraft 1.21.1, NeoForge 21.1.234 oder neuer auf der 1.21.1-Linie, Java 21 und
KotlinForForge 5.11.0 sind erforderlich. TeutonLib muss auf Server und Clients
installiert sein. Die Schlauchintegration ist für Create 6.0.10 und Sable 2.0.6
aktiviert. Die separate Mod `hosepulley_sable_bridge` darf nicht zusätzlich
installiert sein.

- `teutonlib-1.5.15.jar`: Mod für Server und Clients.
- `teutonlib-1.5.15-sources.jar`: zugehöriger Quellcode.
- `teutonlib-1.5.15-computer-ui-template.zip`: Mod, Quellen, Beispiele und Dokumentation.
- `SHA256SUMS`: Prüfsummen der drei Downloads.

## Prüfung

`./gradlew build releaseBundle --offline` und `python3 tools/verify_release.py`
prüfen Build, 160 Tests, Metadaten, Lizenzen, Paketinhalt und übereinstimmende
Release-Artefakte. Testmods und lokale optionale Mod-JARs werden nicht ausgeliefert.

Die Spielprüfungen `tools/verify_hose_connector.py` und
`tools/verify_hose_pulley_client.py` haben den Drain-Umbau auf separaten lokalen
Testinstanzen geprüft: beide Flussrichtungen, Drosselung, Mengenerhaltung,
Drain-Tank/Eimerbedienung, Trageabbruch, Klickweg, Sable-Montage und -Kräfte,
Neustart sowie synchronisierte verriegelte Darstellung im grafischen Client.
