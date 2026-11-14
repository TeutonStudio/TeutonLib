# TeutonLib 1.6.1

Für Minecraft **1.21.1**, NeoForge **21.1.234+**, Java **21** und KotlinForForge **5.11.0–<6**. TeutonLib gehört auf Server und alle Clients.

## Peripheral-Link-Antennen und Ortung

- Ausgewählte Computer und Pulte erhalten automatisch eine anklickbare Antenne im türkisfarbenen Link-Stil. Ein zusätzliches Item oder ein Nachbarblock ist nicht erforderlich.
- Die Antennen-UI listet bekannte Links einschließlich entladener Geräte. Beide Enden zeigen Weltposition, Dimension und Entfernung und bieten eine private Zielmarkierung für 15 Sekunden.
- Entladene Ziele sind ausdrücklich als letzte bekannte Position gekennzeichnet. Bei anderer Dimension entfallen Entfernung und Weltmarkierung. Die Übersicht lädt keine entfernten Chunks.
- Antennen- und Link-UI verwenden den CC-Computerrahmen mit dunklem Inhalt. Im Reiter **Antennenseiten** sind eine bis sechs Seiten gleichzeitig wählbar. Die letzte Seite lässt sich auch per Netzwerkanfrage nicht deaktivieren.
- Seitenwahl und UUIDs bleiben gespeichert. Antennen folgen Position und Drehung von Sable-Sublevels; bei Pultreihen bleibt der tatsächlich ausgewählte Block der Anker. Bisherige Kopplungen und Lua-Adressen bleiben kompatibel.

## Prüfung

Unit-Tests ergänzen Katalog-Persistenz, alle 63 nicht leeren Seitenkombinationen, alte Speicherdaten und Antennen-Treffertests. Die Serverprofile `cc` und `console` prüfen unter anderem Chunk-Entladen, Neustart, Sable-Assemblierung und Pultreihen. Die Clientprüfung deckt GUI-Skalierung 2/3, Seitenauswahl, unzulässige Anfragen, beide Ortungsrichtungen und Ablauf der Markierung ab.

`python3 tools/verify_peripheral_antenna_client.py --sable --visual` erstellt zusätzlich vier unbearbeitete Weltansichten mit allen sechs Seiten, darunter Ansichten von unten und nach weiteren Verschiebungen und Drehungen. Visuell geprüft mit **CC:Tweaked 1.120.2**, **Create 6.0.10** und **Sable 2.0.6**.

Der Clienttest mit dem vollständigen zusätzlichen Addon-Stack hatte ein Verbindungszeitlimit; die gezielte Prüfung mit CC, Create und Sable bestand.

[Bedienung und technische Details](peripheral-link.md) · [English documentation](peripheral-link.en.md)
