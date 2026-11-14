# Bildnachweise und Prüfgrenzen

**Deutsch** · [English](Bildnachweise-en)

Die Abbildungen sind echte Spiel-, Prüfbericht- oder Quellcode-Screenshots und vorhandene Spiel-GIFs. Es wurden keine Spielbilder erzeugt oder als Funktionsnachweis nachgebaut. Historische Aufnahmen werden als solche benannt. Prüfbericht-Screenshots zeigen die am 13.11.2026 tatsächlich ausgeführten **160 JUnit-Tests des Release-Quellstands 15f1a2a**.

Für reine APIs und automatische Adapter gibt es keine eigene Spieloberfläche. Ihre Prüf-/Codeansicht ersetzt keinen Spieltest des gesamten Systems. Insbesondere liegen **keine Android-Geräteaufnahmen** vor. Signaturprüfungen der mobilen Zielmethoden belegen keine Ausführung auf einem Android-Grafiktreiber. Die öffentliche API-Prüfung belegt Signaturkompatibilität, nicht sämtliche Laufzeitfehlerfälle.

Die GPS-, Docking- und Workspace-Bilder aus CC-Aeroworks zeigen einen **zusätzlichen Addon-Nutzer** der TeutonLib-APIs. Die historische CSO-Aufnahme stammt aus dem inzwischen integrierten Vorgängermodul. Das Peripheral-Link-UI-Bild verwendet ausdrücklich Testdaten. Die Tankbilder stammen aus dem **unveröffentlichten lokalen Entwicklungsstand**. Der direkte Gyro-Lua-Aufruf war in der Prüfung nicht verfügbar; die Anleitung dokumentiert den Linkpfad.

Der Dokumentationsstand wurde am 14.11.2026 auf die Basis 1.6.1 aktualisiert. Der abgebildete 160-Test-Bericht gehört weiterhin zum historischen Stand 1.6.0. Ergänzt wurden drei unbearbeitete Antennenaufnahmen aus dem erfolgreichen Lauf `2372da0bb22a` mit CC:Tweaked, Create und Sable: Seitenwahl, Montage am gedrehten Sable-Computer und private Zielmarkierung durch eine Wand. Normale Minecraft-Tutorialeinblendungen sind in den Bildern erhalten.

Die neue Schlauchführung und der rückseitige Flux-Flansch sind lokale Entwicklung. Ihre vorhandenen älteren Bilder belegen diese Änderungen nicht; offene grafische Prüfungen sind auf den Funktionsseiten genannt. Die Android-Bilder bleiben historische TeutonLib-Prüfnachweise nach der lokalen Auslagerung nach TerrArchitecture.

Zwei weitere unbearbeitete Schlauchbilder stammen aus dem lokalen Clientlauf `20261114-203614-8e820559`. Beide Beobachter bestanden Durchhang-/Flux-/Langschlauch-Teilaufnahmen mit identischen verglichenen Schlauchpunkten. Der Gesamtlauf scheiterte anschließend an einem Zeitlimit der Chunk-Pausenprüfung. Die Bilder dokumentieren die Teilfälle, keinen bestandenen Gesamttest.

| Anleitung | Abbildung und Aussage |
| --- | --- |
| [Installation](Installation) | Der eingebaute NeoForge-Editor für TeutonLib; eine zusätzliche Config-Mod ist nicht erforderlich. |
| [Konfiguration und Befehle](Konfiguration-und-Befehle) | TeutonLib im nativen Konfigurationseditor. Servereinstellungen sind nur bei einer passenden geladenen Welt bearbeitbar. |
| [Bahnhofsbau](Bahnhofsbau) | Historische Spielaufnahme des ersten Wagens am Bahnhof aus der 1.6.0-Demo. Die lokale Vorschau-Reparatur vom 12.11.2026 ist damit nicht belegt. |
| [Hybridzüge](Hybridzuege) | Echter Hybridzug mit Maschinen und orangefarbenem ContraptionLevel-Rahmen aus der 1.6.0-Demo. |
| [Lager und Untercontraptions](Lager-und-Untercontraptions) | Laufende native Lager-Nutzlast auf dem Hybrid-Testzug. Persistenz und Wiederanlauf sind zusätzlich im historischen Spieltest geprüft. |
| [Radar-Typ und Speicherung](Radar-Typ-und-Speicherung) | Echtes Radar auf dem Hybridzug. Das Bild zeigt den Aufbau; den gespeicherten Typ prüft der separat dokumentierte Spieltest. |
| [Create Big Cannons auf Hybridzügen](Create-Big-Cannons-auf-Hybridzuegen) | Tatsächlich ausgeführte Desktop-Prüfung der versionsgebundenen Hybridintegration einschließlich Kanonen-Zielklassen. Keine Aufnahme eines Schusses. |
| [Zug-Docking](Zug-Docking) | Native Verriegelung, Gegenstelle nach passender Portalpassage und bei zu kleiner Öffnung zurückgelassene Plattform. |
| [Zug-Portaltransfer](Zug-Portaltransfer) | Echte Portaldurchfahrt mit demselben Maschinenaufbau auf Quell- und Zielseite. Die bekannte Abweichung stehender Mitfahrer bleibt bestehen. |
| [Portal-Darstellung und Beleuchtung](Portal-Darstellung-und-Beleuchtung) | Portal-Clipping, Beleuchtung der Zielwelt und ContraptionLevel-Rahmen in der echten Testszene. |
| [Zahnradbrücken über Sable-Dockingverbindungen](Zahnradbruecken) | Historische Spielaufnahme des inzwischen integrierten CSO-Moduls: Antrieb und Zahnräder an einer Dockingverbindung. Kein neuer TeutonLib-Abnahmetest. |
| [Analoge Dockingports](Analoge-Dockingports) | Reale native Dockingverbindung und ausgeführte Prüfung der OFF/HALF/FULL-Längen. Das Spielbild allein belegt keine bestimmte Redstone-Stärke. |
| [FE-Eingang an New-Age-Motorerweiterungen](New-Age-FE-Eingang) | Realer Aufbau FE-Quelle → Motorerweiterung → Motor und vorhandene Flux-Demo. |
| [New-Age-Motordrehzahl auf Sable](New-Age-Motordrehzahl-auf-Sable) | Ausgeführte Desktop-Prüfung der unterstützten Motorvarianten und der versionsgebundenen Drehrichtungsintegration; keine neue Fahrtaufnahme. |
| [Physische Schlauchrolle](Physische-Schlauchrolle) | Historische GIF zum Tragen/Werfen plus lokale Entwicklungsaufnahmen von Durchhang, Hindernisführung und langem Schlauch ohne getrackten Kopf. Die Teilaufnahmen bestanden; der vollständige Clientlauf scheiterte später an der Chunk-Pausenprüfung. |
| [Schlauchkupplung am Item Drain](Schlauchkupplung-am-Item-Drain) | Echter Entity-Kopf im Item Drain in der getrennten grafischen Schlauchprüfung. |
| [Mekanism-Anschlussmodelle auf Sable](Mekanism-Anschlussmodelle) | Echte Rohrmodell-Aufnahmen nach Modusänderung und während Sable-Bewegung. |
| [FluidLogistics und Factory Logistics](FluidLogistics-und-Factory-Logistics) | Ausgeführte Desktop-Prüfung der Versions- und Klassenverträge des gemeinsamen Ressourcenadapters; keine neue Bestelloberfläche. |
| [FluidLogistics und TFMG](FluidLogistics-und-TFMG) | Ausgeführte Desktop-Prüfung der Pumpen- und Propagations-Zielklassen; die weiterführende Flüssigkeitsprüfung ist separat dokumentiert. |
| [Computer-UI: Quellen und Lua-API](Computer-UI) | Echter CC-Blockcomputer mit TeutonLib-Quellenreiter in der separaten Aufnahmewelt. |
| [Computer-Kanäle](Computer-Kanaele) | Echter CC-Blockcomputer mit zwei über die native Lua-API angelegten Kanälen. |
| [Peripheral Link](Peripheral-Link) | Historische Link-UI mit Prüfdaten sowie echte Aufnahmen der Antennenseiten, Sable-Montage und Zielmarkierung aus der erfolgreichen Antennen-Clientprüfung. Keine Aufnahme eines persönlichen Spielstands. |
| [Directional Gearshift](Directional-Gearshift) | Tatsächliche Lua-Abfrage nach setOutputs(true, false) am nativen Directional Gearshift. |
| [Helikopter-Peripherie: Gimbalsensor](Helikopter-Peripherie) | Tatsächliche Pitch-/Yaw-/Roll-Abfrage und weiterhin zweiachsiges Legacy-Format am nativen Gimbalsensor. |
| [Gyroskop-Steuerung](Gyroskop-Steuerung) | Die tatsächlichen Lua-Aufrufe setTargetDirection und adjustTilt werden am nativen Lager über Peripheral Link angenommen; keine vollständige Flugregelung. |
| [Optischer Sensor: lokale Laserrichtung](Optischer-Sensor) | Echte Lua-Ausgabe des lokalen Richtungsvektors über Peripheral Link und ausgeführte Prüfung der sechs Richtungen. |
| [Funk und GPS auf Sable](Funk-und-GPS) | Echter GPS-Fix eines Sable-montierten CC-Aeroworks-Computers mit vier GPS-Hosts. CC-Aeroworks ist hier ein zusätzlicher Nutzer der TeutonLib-Integration. |
| [Grafik-API: portable SVG-Ausgabe](Grafik-API) | Echte Ausgabe von teutonlib.svg und teutonlib.font im CC-Terminal ohne CCVisual oder CC-Aeroworks. |
| [Bitmap-Schrift](Bitmap-Schrift) | Die Texte RPM und READY stammen tatsächlich aus der 3×5-Bitmap-Schrift; Formen aus dem SVG-Modul. |
| [Computer-Workspace und eingebetteter Kernel](Computer-Workspace-und-Kernel) | Realer CC-Aeroworks-Workspace als Addon-Nutzer und ausgeführte Snapshot-/Befehlsprüfung. Das Addon stellt die Fachoberfläche bereit. |
| [Embedded-Computer-Kernel](Embedded-Computer-Kernel) | Echter eingebetteter CC-Computer im zusätzlichen Addon CC-Aeroworks als Nutzer des HostedComputer-Kernels. TeutonLib registriert keinen solchen Pultblock. |
| [Peripheral-Topologie und Attachments](Peripheral-Topologie-und-Attachments) | Reale Geräteansicht und ausgeführte Prüfung öffentlicher API-Signaturen. Das Bild belegt nicht jeden Attach/Detach-Fehlerfall. |
| [Persistente Kanalgruppen und Signale](Persistente-Kanalgruppen-und-Signale) | Realer Addon-Gruppeneditor und ausgeführte Bank-Prüfungen. Gruppen- und Signalbank sind getrennte APIs. |
| [Persistente Kanalgruppen](Persistente-Kanalgruppen) | CC-Aeroworks als realer Nutzer der Gruppenbank; die Prüfung zeigt Erhalt fehlender Ziele und Definitionspersistenz. |
| [Signalbank](Signalbank) | Ausgeführte Prüfung von Signalfreigabe, Pulsende und Rücksetzen nach dem Laden. Die Signalbank hat keine eigene GUI. |
| [Sable-Geometrie-API](Sable-Geometrie-API) | Die sichtbare Weltposition wird in einem realen Sable-GPS-Nutzer ausgegeben; nicht jeder Interaktions- und Renderhelfer wird damit einzeln geprüft. |
| [Docking-Zustands-API](Docking-Zustands-API) | Reale verriegelte native Verbindung und ausgelesener Dockingzustand in einem zusätzlichen CC-Aeroworks-Nutzer. |
| [Netzwerk-Sitzungen und Budgets](Netzwerk-Sitzungen-und-Budgets) | Ausgeführte Desktop-Vertragstests für Sitzungsablauf, Gestenreihenfolge und Spieler-Tickbudgets; keine eigene Spieloberfläche. |
| [Android- und Zalith-Kompatibilität](Android-und-Zalith-Kompatibilitaet) | Ausgeführte Desktop-Prüfung der Android-Erkennung. Keine Aufnahme eines Android-/Zalith-Geräts. |
| [Ponder und Catnip auf Android](Ponder-und-Catnip-auf-Android) | Ausgeführte Desktop-Prüfung der exakt unterstützten Einsprungmethoden. Kein Funktionsnachweis auf einem Android-Gerät. |
| [PowerGrid auf Android](PowerGrid-auf-Android) | Ausgeführte Desktop-Prüfung der exakt unterstützten Einsprungmethoden. Kein Funktionsnachweis auf einem Android-Gerät. |
| [Immersive Melodies auf Android](Immersive-Melodies-auf-Android) | Ausgeführte Desktop-Prüfung der exakt unterstützten Einsprungmethoden. Kein Funktionsnachweis auf einem Android-Gerät. |
| [Visual Recipe Editor auf Android](Visual-Recipe-Editor-auf-Android) | Ausgeführte Desktop-Prüfung der exakt unterstützten Einsprungmethoden. Kein Funktionsnachweis auf einem Android-Gerät. |
| [Essential auf Android](Essential-auf-Android) | Der tatsächliche Essential-Socket-Test prüft connected=false, definierte Fehler und sicheres Schließen. Desktop-Aufnahme, kein Android-Spieltest. |
| [Ressourcenreparaturen](Ressourcenreparaturen) | Tatsächlich ausgeführte Desktop-Prüfung der Originaldatei-Hashes und konkreten Ressourcenregeln. Die Tests nennen die jeweils behandelte Ressource. |
| [Create-Radar-Ressourcenreparatur](Create-Radar-Ressourcenreparatur) | Tatsächlich ausgeführte Desktop-Prüfung der Originaldatei-Hashes und konkreten Ressourcenregeln. Die Tests nennen die jeweils behandelte Ressource. |
| [EnderIO-Ressourcen ohne Athena](EnderIO-ohne-Athena) | Tatsächlich ausgeführte Desktop-Prüfung der Originaldatei-Hashes und konkreten Ressourcenregeln. Die Tests nennen die jeweils behandelte Ressource. |
| [NTGL-Animationsreparaturen](NTGL-Animationsreparaturen) | Tatsächlich ausgeführte Desktop-Prüfung der Originaldatei-Hashes und konkreten Ressourcenregeln. Die Tests nennen die jeweils behandelte Ressource. |
| [Design 'n' Decor: Soundressource](Design-n-Decor-Soundreparatur) | Tatsächlich ausgeführte Desktop-Prüfung der Originaldatei-Hashes und konkreten Ressourcenregeln. Die Tests nennen die jeweils behandelte Ressource. |
| [Paketmetadaten-Diagnose](Paketmetadaten-Diagnose) | Screenshot des tatsächlichen Diagnosepfads: Dateiname, Modversion, Fingerabdruck und erneutes Auslösen des ursprünglichen Fehlers. Codeansicht, kein nachgestellter Spielerabsturz. |
| [Entwicklung und Prüfungen](Entwicklung-und-Pruefungen) | Historischer Prüflauf vom 13.11.2026: 160 JUnit-Tests des unveränderten 1.6.0-Quellstands, keine Fehler oder übersprungenen Tests. Kein Testergebnis für 1.6.1 oder die lokalen Änderungen. |
| [Sable-Tankoberfläche — Entwicklung](Sable-Tankoberflaeche) | Unveröffentlichter Entwicklungsstand: echte gerenderte Tankoberflächen bei geneigtem und seitlichem Tank. Nicht in der Release-JAR 1.6.0 enthalten. |

[Machine-readable media inventory](https://github.com/TeutonStudio/TeutonLib/blob/master/docs/wiki-media-coverage.json) · [Review report](https://github.com/TeutonStudio/TeutonLib/blob/master/docs/wiki-review-2026-11-13.md)
