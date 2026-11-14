# FE-Eingang an New-Age-Motorerweiterungen

**Deutsch** · [English](New-Age-FE-Eingang-en) · [Übersicht](Home)

Grundlegende und fortgeschrittene Motorerweiterungen von Create New Age nehmen Forge Energy von Flux Points, Kabeln und anderen FE-Quellen an. Die Energie fließt direkt in den angeschlossenen Motor.

**Unveröffentlichter lokaler Stand vom 14.11.2026, nach der Basis 1.6.1:** Flux Points unterscheiden rückseitige Anschlüsse an Motorerweiterungen von normalen Seitenanschlüssen. Nur rückseitig wird der Anschlussflansch um 2/16 Block verlängert. Modellwahl und Auswahlform folgen demselben synchronisierten Blockzustand; Motorwechsel und Konfigurationsänderungen werden übernommen.

## Aufbau und Bedienung

```text
FE-Quelle → Motorerweiterung → Motor
```

Setze die Erweiterung unmittelbar hinter den Motor. Erweiterung und Motor müssen in dieselbe Richtung zeigen, wie bei New Ages normaler Stressverstärkung. Unterstützte Motoren sind der grundlegende, fortgeschrittene und verstärkte Motor. Alle zugänglichen Seiten der Erweiterung können Energie empfangen.

Die Erweiterung besitzt keinen eigenen Speicher und erlaubt keine Entnahme. Ohne passenden, geladenen und korrekt ausgerichteten Motor wird keine Energie angenommen. Motorwechsel und späteres Platzieren werden auch an bereits abgefragten Energieanschlüssen berücksichtigt. Erweiterungsketten und Verbindungen über Sable-Sublevel sind nicht vorgesehen.

## Voraussetzungen und Konfiguration

Create New Age ist optional; die Registrierung erfolgt nur bei geladener Mod und vorhandenen Block-IDs. Flux Networks wird nur für Flux Points benötigt. Die geprüfte Basis sind **Create New Age 1.1.7c** und **Flux Networks 8.0.0**. Die FE-Registrierung hat anders als die [Sable-Drehzahlkorrektur](New-Age-Motordrehzahl-auf-Sable) keine exakte Versionssperre, bindet aber dieselben bekannten Motor- und Erweiterungs-IDs ein.

In der Weltdatei `serverconfig/teutonlib-server.toml`:

```toml
[compat.createNewAge]
motorExtensionEnergyTransfer = true
```

Standard ist `true`. Änderungen gelten beim Laden beziehungsweise Neuladen der Serverkonfiguration, auch für bestehende Verbindungen. Bei aktiver NeoForge-Dateiüberwachung ist kein Neustart nötig. Die ursprüngliche Stressverstärkung bleibt unabhängig von dieser Einstellung erhalten.

## Quellen und Prüfung

Die opt-in-Energieprüfung deckt beide Erweiterungen, drei Motorarten, sechs Ausrichtungen, alle Eingangsseiten, FE-Simulation, falsche Aufbauten, Config-Neuladen, Entladen und Neustarts ab. Beim Flux Point wird dessen echter Erkennungs- und Übertragungszyklus geprüft; der Aufbau eines drahtlosen Flux-Netzwerks gehört nicht zu diesem Test.

- [FE-Registrierung und Motorauflösung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/energy/NewAgeMotorEnergy.kt)
- [Empfangsschnittstelle ohne eigenen Puffer](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/energy/MotorEnergyInput.kt)
- [Konfiguration](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/ServerConfig.kt)

[Wiki-Startseite](Home) · [New-Age-Motordrehzahl auf Sable](New-Age-Motordrehzahl-auf-Sable)

Die vorhandenen Bilder unten zeigen den ursprünglichen Motoraufbau und belegen nicht das neue rückseitige Flanschmodell. [Aktuelle Modell- und Zustandsauswahl](https://github.com/TeutonStudio/TeutonLib/blob/master/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/energy/FluxPointConnections.kt). Der kombinierte Clientlauf `20261114-203614-8e820559` erzeugte Flux-Aufnahmen für zwei Beobachter und bestätigte die Modellgrenze bei −2/16 Block. Der gesamte Lauf scheiterte anschließend an einem Zeitlimit der Schlauch-Chunkprüfung; ein vollständig erfolgreicher kombinierter Prüflauf liegt damit nicht vor.

## Abbildung

Realer Aufbau FE-Quelle → Motorerweiterung → Motor und vorhandene Flux-Demo.

![FE-Eingang an New-Age-Motorerweiterungen: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/motor-extension.png)

![FE-Eingang an New-Age-Motorerweiterungen: 2](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/gifs/flux-motor.gif)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
