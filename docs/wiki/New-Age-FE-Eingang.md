# FE-Eingang an New-Age-Motorerweiterungen

Grundlegende und fortgeschrittene Motorerweiterungen von Create New Age nehmen Forge Energy von Flux Points, Kabeln und anderen FE-Quellen an. Die Energie fließt direkt in den angeschlossenen Motor.

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
