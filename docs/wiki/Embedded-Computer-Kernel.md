# Embedded-Computer-Kernel

**Deutsch** · [English](Embedded-Computer-Kernel-en) · [Übersicht](Home)

TeutonLib v1.6.0 stellt mit `de.TeutonStudio.TeutonLib.api.computer.hosted.HostedComputer` eine wiederverwendbare CC:Tweaked-Laufzeit für eigene Addon-Blockentities bereit. Das Addon definiert Blockregistrierung, Einbau-/Ausbauverhalten und Zugriffsregeln. Der Kernel übernimmt die laufende CC-Instanz und Standard-Computerdaten; er registriert allein keinen neuen Block.

## Einbinden

`HostedComputer` nimmt Host-Blockentity, eingebauten Computer-ItemStack, `ComputerFamily` und optionale Callbacks entgegen. Es verwaltet einen echten `ServerComputer`, dessen ID, Label, Kapazität, Terminalgröße und Einschaltzustand.

```kotlin
val runtime = HostedComputer(
    host = blockEntity,
    installed = computerStack,
    family = ComputerFamily.ADVANCED,
    changed = { blockEntity.setChanged() }
)

// Im serverseitigen Host-Tick:
runtime.tick()
// Beim Entfernen/Schließen des Hosts:
runtime.close()
```

`createServerComputer()` erzeugt bei Bedarf eine Instanz und vergibt für einen neuen Computer eine eindeutige Speicher-ID. `tick()` aktualisiert die Hostposition, Eingänge, Keep-alive, Label und Stromzustand. Ein unbenutzter ausgeschalteter Host ohne ID erzeugt dabei keine Instanz. Mit `configure` werden CC-Properties angepasst, mit `refreshInputs` Addoneingänge aktualisiert.

`queueEventWhenReady(name, ...)` schaltet den Computer bei Bedarf ein und puffert Ereignisse bis zum Betrieb; standardmäßig werden höchstens 64 gehalten, ältere Einträge werden bei Überlauf entfernt. `prepareMenu(player, usable)` hinterlegt eine spielerbezogene Zugriffsprüfung; `createMenu(...)` übernimmt sie in ein CC-Menü. Ohne vorbereitete Prüfung verweigert dieses Menü den Zugriff.

`write`/`read` speichern mit addondefinierten `HostedComputerPersistenceKeys`. `writeStandardComponents` überträgt die Standard-Computerkomponenten auf einen ItemStack. Das Addon ist für Aufrufe an seinen Speicher-/Tick-/Entladepunkten verantwortlich; `close()` leert Ereignisse und Menüzugriffe und schließt die laufende Instanz.

## Verbindung mit anderen APIs

Ein Addon kann den eingebetteten Computer über einen identitätsgeprüften Hostresolver in `ComputerExtensions` anmelden und einen [Workspace](Computer-Workspace-und-Kernel) bereitstellen. [Peripheral-Topologie und Attachments](Peripheral-Topologie-und-Attachments) verwalten bei Bedarf dessen Gerätegraphen. Persistenz des Hostblocks und Aufrufe zum richtigen Lebenszykluszeitpunkt bleiben Aufgaben des Addons.

## Implementierung

[HostedComputer und Persistenzschlüssel](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/hosted/HostedComputer.kt), [Host-/Workspace-Erweiterungen](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/ComputerExtensions.kt).

[Zur Startseite](Home)

## Abbildung


Echter eingebetteter CC-Computer im zusätzlichen Addon CC-Aeroworks als Nutzer des HostedComputer-Kernels. TeutonLib registriert keinen solchen Pultblock.

![Embedded-Computer-Kernel: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/addon-wiki-operator-terminal.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
