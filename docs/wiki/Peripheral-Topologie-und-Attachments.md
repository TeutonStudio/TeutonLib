# Peripheral-Topologie und Attachments

TeutonLib v1.6.0 bietet unter `de.TeutonStudio.TeutonLib.api.computer.peripheral` eine öffentliche Entwickler-API für addondefinierte Peripheral-Netze. Sie übernimmt Bindungslebensdauer, Methodenaufrufe und Mount-Bereinigung. Die öffentliche API verwendet keine CC-Aeroworks-Fachklassen. Für die Spielerbedienung siehe [Peripheral Link](Peripheral-Link).

## Einen Graphen beschreiben

| Typ | Inhalt |
| --- | --- |
| `PeripheralHost` | Opaquer Hostschlüssel, Adresse und Metadaten. |
| `PeripheralEndpoint` | Eindeutige Adresse, primärer Typ, Typmenge, `IPeripheral`, `ownerKey`, optionaler Deskriptor und Metadaten. |
| `PeripheralTopology` | Revision, Zustandsbezeichnung, Hosts, Endpoints und Metadaten als Zeitpunktaufnahme. |
| `PeripheralTopologyProvider` | `snapshot(ComputerContext)` liefert die aktuelle Topologie. |

Das Addon definiert Bedeutung und Stabilität von Schlüsseln, Revision und Metadaten. Endpoint-Adressen müssen innerhalb einer Topologie eindeutig sein; doppelte Adressen werden abgewiesen. `ComputerContext` ist für den Serverthread vorgesehen. Seine vorhandenen Peripherals sind geliehen und dürfen von einem Quellenprovider nicht erneut angehängt werden.

## Attach/Detach verwalten

`PeripheralAttachmentRuntime` erhält das `IComputerSystem`, einen Kontextlieferanten, einen Topologieprovider sowie optionale Beschreibungs- und Ereigniscallbacks. Der Host ruft `refresh()` während seiner serverseitigen Aktualisierung auf und `close()` am Ende der Lebensdauer.

```kotlin
val attachments = PeripheralAttachmentRuntime(
    system = system,
    context = { computerContext },
    provider = PeripheralTopologyProvider { currentTopology() }
)

attachments.refresh()
val binding = attachments.binding(deviceAddress)
// Beim Entfernen oder endgültigen Stoppen des Hosts:
attachments.close()
```

Das Beispiel setzt bereits vorhandene `system`, `computerContext`, `currentTopology()` und `deviceAddress` des Addons voraus. Die Runtime ruft `attach` pro neuer Bindung auf, erhält äquivalente Bindungen und schließt entfernte oder ausgetauschte Endpoints. Der Vergleich berücksichtigt primären Typ, Typmenge, Eigentümerschlüssel und Peripheral-Identität beziehungsweise beidseitige Gleichheit. Ohne `force` wird innerhalb desselben Serverticks höchstens ein neuer Snapshot erzeugt.

`binding(address)` liefert eine `PeripheralDeviceBinding`; eine nicht mehr vorhandene Adresse erzeugt einen Lua-Fehler. Die Bindung führt Methoden durch CCs `PeripheralMethod`-System aus, schützt Hauptthread-Aufgaben mit ihrem Gültigkeitszustand und verwaltet eigene Datei-Mounts. `close()` ist wiederholbar und räumt Mounts auch bei einem Detach-Fehler auf. Alte Bindungen bleiben nach einem erneuten Anschluss ungültig.

`onAttached`/`onDetached` melden Änderungen nach der ersten Initialisierung. `invalidate(queueEvents)` schließt den aktuellen Bestand; `close()` entfernt außerdem die Runtime aus der gemeinsamen Registrierung. `bindings(system.mainThreadMonitor)` ermöglicht die Wiederverwendung vorhandener Bindungen, ohne ein zweites `attach` auszuführen.

## Addonziele und zusätzliche Endpoints

`PeripheralProviders.registerTargets(provider)` registriert einen `PeripheralTargetProvider`. `owner(anchor)` liefert den aktuell zuständigen geladenen Computerblock oder `null`. Für vorübergehend inaktive Anker muss `supports(anchor)` weiterhin `true` liefern, beispielsweise bei einer Pultreihe mit ausgebautem Computer.

`registerEndpoints(provider)` ergänzt addonunabhängige Endpoints. Der Host kann `PeripheralProviders.endpoints(context)` in seinen Graphen aufnehmen. Registrierungen liefern `AutoCloseable`-Handles zum Abmelden. Eigene Provider sollten nur bereits geladene Blöcke auflösen.

Die Metadatenschlüssel `PeripheralEndpointMetadata.LINK`, `ANCHOR_POSITION`, `DEVICE_POSITION` und `SIDE` beschreiben TeutonLib-Linkendpoints. Provider behalten die Zuständigkeit für ihre fachlichen Deskriptoren.

## Implementierung

[Topologietypen](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/peripheral/PeripheralTopology.kt), [Runtime](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/peripheral/PeripheralAttachmentRuntime.kt), [Gerätebindung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/peripheral/PeripheralDeviceBinding.kt), [Providerregister](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/peripheral/PeripheralProviders.kt).

[Zur Startseite](Home)
