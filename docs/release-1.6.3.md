# TeutonLib 1.6.3

Veröffentlicht am **21.11.2026** für Minecraft **1.21.1**, NeoForge **21.1.234+**, Java **21** und KotlinForForge **5.11.0–<6**. Gebaut mit NeoForge **21.1.251**. Auf Server und allen Clients aktualisieren.

## Änderungen seit 1.6.2

- **Flux Points an New-Age-Motorerweiterungen:** Anschlussplatten passen sich jetzt an alle zugänglichen Flächen beider Motorerweiterungen an, einschließlich gestuftem Gehäuse, Zierplatten und horizontalem Standfuß. Darstellung, Auswahl- und Kollisionsform verwenden dieselbe Geometrie. Drehungen der benachbarten Erweiterung werden auch dann übernommen, wenn der gespeicherte Anschlusszustand gleich bleibt. Bestehende Blockstate-Eigenschaften bleiben kompatibel; die Netzwerkfarbe bleibt sichtbar.
- **Absturzfix beim Bahnhofszusammenbau:** Geparkte Sable-Physikkörper werden weder aufgeweckt noch ein zweites Mal aus der nativen Physiksimulation entfernt. Das schützt die Wiederherstellung nach einer abgewiesenen Montage und die Entsorgung des Bauplots nach erfolgreicher Montage. Fehlende Zugsteuerungen werden weiterhin von Create gemeldet; die Baublöcke bleiben für einen erneuten Versuch erhalten.
- **Entwicklerprüfungen:** Neuer Geometrietest gegen die Originalmodelle von Create New Age, Flux-Modellprüfung für beide Erweiterungen in allen sechs Ausrichtungen mit je fünf Eingängen und zuschaltbare Clientprüfung über Creates echten Zusammenbauknopf. Testmods bleiben außerhalb der Release-JAR.

## Prüfung und Grenzen

`releaseBundle` mit Java 21 bestanden: **191 JUnit-Tests, 0 Fehler, 0 übersprungen**. Energie-, Schlauch- und Stationsmontage-Testmods erfolgreich kompiliert. Wiki-Prüfung: **52 Sprachpaare**, Navigation, Medien und Quellverweise geprüft. Release-Archive: Metadaten, Lizenzen, Klassenabgleich, Ausschluss der Testmods und eingebettete Abhängigkeiten geprüft; SHA-256-Prüfsummen erzeugt.

Die dokumentierte Flux-Modellprüfung vom **14.11.2026**, Lauf `20261114-220349-74040066`, bestand für zwölf Anordnungen und prüfte gebackene Modelle, Netzwerkfarbflächen und Modell-/Formgrenzen. Die Stationsmontageprüfung vom **15.11.2026**, Lauf `e84605a7d3b2`, bestand: Ablehnung ohne Steuerung, Wiederherstellung desselben Bauplots und anschließender erfolgreicher Zusammenbau mit Steuerung. Diese datierten Spieltests sind keine erneute vollständige Abnahme aller optionalen Modkombinationen.

Die bekannte Aether-Positionsabweichung stehender Mitfahrer und mögliches Durchhängen angedockter Lasten bleiben dokumentiert. Android-/Zalith-Adapter liegen seit 1.6.2 in TerrArchitectures Modul `mobilecompat`.

Downloads: Mod-JAR, Quellen-JAR, Computer-UI-Vorlagenpaket und `SHA256SUMS`.

[Änderungen in 1.6.2](https://github.com/TeutonStudio/TeutonLib/blob/master/docs/release-1.6.2.md) · [Wiki Deutsch](https://github.com/TeutonStudio/TeutonLib/wiki) · [Wiki English](https://github.com/TeutonStudio/TeutonLib/wiki/English)
