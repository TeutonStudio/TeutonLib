# Persistente Kanalgruppen und Signale

TeutonLib v1.6.0 stellt Addons unter `de.TeutonStudio.TeutonLib.api.computer.channel` zwei eigenständige Bausteine bereit:

| Baustein | Aufgabe | Persistenz |
| --- | --- | --- |
| [Persistente Kanalgruppen](Persistente-Kanalgruppen) | Benannte Gruppen mit stabilen UUIDs und Aliasbindungen auf Ziel-IDs. | Gruppen und Bindungen werden serialisiert; fehlende Ziele bleiben erhalten. |
| [Signalbank](Signalbank) | Benannte Ausgänge mit Freigabe, Backendanbindung und zeitbegrenzten Pulsen. | Nur Definitionen werden serialisiert; Werte und Pulse beginnen nach Laden bei 0. |

Beide sind Entwickler-APIs. Das Addon stellt Bedienung, Speicheranbindung und fachliche Geräteauflösung bereit. Sie legen keine weiteren Standard-Lua-Befehle an.

Die normale [Computer-Kanalbank](Computer-Kanaele) ist eine andere Funktion: Sie bietet bis zu 64 Kanäle pro Standardcomputer und speichert auch ihre Ausgangswerte. Diese Regeln dürfen nicht auf `SignalBank` übertragen werden.

## Implementierung

[Gruppenbank](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/channel/PersistentChannelGroupBank.kt), [Signalbank](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/channel/SignalBank.kt).

[Zur Startseite](Home)
