# Computer-Workspace und eingebetteter Kernel

**Deutsch** · [English](Computer-Workspace-und-Kernel-en) · [Übersicht](Home)

TeutonLib v1.6.0 stellt Addons gemeinsame Computeroberflächen und mit `HostedComputer` eine wiederverwendbare CC:Tweaked-Laufzeit für eigene Blockentities bereit. Diese APIs liegen unter `de.TeutonStudio.TeutonLib.api.computer`. Sie erzeugen allein keinen neuen Computerblock. Für die normale Bedienung siehe [Computer-UI](Computer-UI).

## Server-Workspace

Ein `ComputerWorkspace` implementiert drei Methoden:

```kotlin
fun owns(computer: ServerComputer): Boolean
fun snapshot(player: ServerPlayer, computer: ServerComputer, page: Int): Snapshot
fun execute(player: ServerPlayer, computer: ServerComputer, command: Command)
```

`ComputerExtensions.registerWorkspace(id, backend)` registriert den Backend unter einer `ResourceLocation`. Die Callbacks laufen auf dem Serverthread nach der Menüvalidierung. Der Backend prüft bei einer Aktion erneut, ob ausgewählter Eintrag, aktueller Zustand und Aktion noch gültig sind.

Ein `Snapshot` enthält Titel, Status, hierarchische `Entry`-Zeilen und `Action`-Beschreibungen. Einträge besitzen stabile IDs, Beschriftung, Details, Gruppen-/Zielkontext, Kinder und Metadaten. Aktionen geben an, ob Text benötigt wird, eine Bestätigung angezeigt wird oder die Aktion clientseitig zu behandeln ist. `Command` transportiert Seite, Aktion, Auswahl, Gruppe, Ziel und Eingabetext. Der Decoder begrenzt Baumtiefe auf 8, Einträge auf 8192 und Aktionen auf 16 pro Liste.

Clientseitig registriert `ComputerWorkspaceClients.register(...)` einen `ComputerWorkspaceClientDescriptor` mit Workspace-ID, Menüzuordnung, optionalen Reitern und clientseitiger Aktionsbehandlung. Clientklassen dürfen nicht auf dem Dedicated Server geladen werden. Der gemeinsame Controller übernimmt Baumdarstellung und Aktionsfelder; die Beispielintegration zeigt Fokus, Terminalumschaltung und Resize. Die Seitennummern der Standardansicht sind 0 für Terminal, 1 für Quellen, 2 für Kanäle.

## Quellen und Kanalzuständigkeit erweitern

`ComputerExtensions` bietet außerdem:

| Registrierung | Aufgabe |
| --- | --- |
| `registerHosts` | Einem `IComputerSystem` den bereits geladenen, eindeutig zugehörigen Host zuordnen. |
| `registerSources` | Zusätzliche `SourceView`-Beschreibungen bereitstellen. |
| `registerPeripheralSources` | Bereits bestehende Addon-Verbindungen und Metadaten projizieren. |
| `registerSourceDocumentation` | Signaturen und Beschreibungen für tatsächlich verfügbare Methoden ergänzen. |
| `registerChannels` | Zusätzliche Kanalansichten bereitstellen. |
| `registerChannelAuthority` | Eigene Kanalzuständigkeit deklarieren und eine zweite beschreibbare Standardbank verhindern. |

Registrierungen geben schließbare Handles zurück. Sie erfolgen passend zur Common- beziehungsweise Client-Setup-Phase und erst nach einer Prüfung optionaler Abhängigkeiten. Hostidentität sollte über CCs `mainThreadMonitor` statt allein über die Computer-ID verglichen werden. Quellenprovider dürfen nicht rekursiv `computer.peripherals()` aufrufen.

Fremde Kanäle bleiben im gemeinsamen Editor schreibgeschützt und nehmen gemäß `published` an Fernabfragen teil. Das Addon behält Wertebereiche, Speicherung und Mutationen. Eindeutige Namenspräfixe vermeiden Kollisionen. `sourceEntry(...)` erzeugt eine Gerätezeile einschließlich API-Metadaten; `SourceApi.encode()` ist das Format für den API-Viewer.

## Eingebettete Computer

Für die eigentliche CC-Laufzeit eines Addon-Blocks steht ein eigener [Embedded-Computer-Kernel](Embedded-Computer-Kernel) bereit. Dort sind Tick-Lebenszyklus, Ereigniswarteschlange, Menüzugriff, Persistenz und Itemkomponenten von `HostedComputer` dokumentiert.

## Implementierung und Vorlage

[Workspace-Vertrag](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/ComputerWorkspace.kt), [Erweiterungsregister](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/ComputerExtensions.kt), [Clientdeskriptoren](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/client/ComputerWorkspaceClients.kt), [HostedComputer](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/hosted/HostedComputer.kt), [kompilierte Bildschirmvorlage](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/examples/computer-ui/src/main/kotlin/example/teutonlib/client/TemplateComputerScreen.kt).

Siehe auch [Peripheral-Topologie](Peripheral-Topologie-und-Attachments) und [persistente Kanalgruppen und Signale](Persistente-Kanalgruppen-und-Signale).

[Zur Startseite](Home)

## Abbildung


Realer CC-Aeroworks-Workspace als Addon-Nutzer und ausgeführte Snapshot-/Befehlsprüfung. Das Addon stellt die Fachoberfläche bereit.

![Computer-Workspace und eingebetteter Kernel: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/addon-wiki-operator-terminal.png)

![Computer-Workspace und eingebetteter Kernel: 2](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-ComputerWorkspaceTest.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
