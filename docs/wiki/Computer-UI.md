# Computer-UI: Quellen und Lua-API

**Deutsch** · [English](Computer-UI-en) · [Übersicht](Home)

TeutonLib v1.6.0 ergänzt normale und erweiterte **CC:Tweaked-Blockcomputer** um die Reiter **Quellen** und **Kanäle** in der nativen Seitenleiste. CC:Tweaked genügt für diese Oberfläche. Pocket Computer und Turtles erhalten die zusätzlichen Reiter nicht. [Kanäle und Lua-Befehle](Computer-Kanaele) sind separat dokumentiert.

## Quellen ansehen

1. Einen CC-Blockcomputer öffnen und den Reiter **Quellen** wählen.
2. Eine Gruppe für lokale Anschlüsse, Kabelverbindungen oder Peripheral Links aufklappen.
3. Ein Gerät auswählen, um Adresse, Blockname, Position und Anschlussart anzuzeigen.
4. Über das Info-Symbol der Gerätezeile die verfügbaren Lua-Methoden öffnen. Escape oder das Schließsymbol kehren zur Liste zurück; anschließend führt Escape zum Terminal.

Die Oberfläche zeigt bereits verbundene Peripherals und hängt sie nicht ein zweites Mal an. Ein frei vergebener Spitzname ändert nur die Beschriftung. Lua-Adresse und Geräteidentität bleiben erhalten. Spitznamen lokaler und kabelgebundener Quellen liegen am Computer; ein [Peripheral Link](Peripheral-Link) speichert seinen Spitznamen am Link und erlaubt die Bearbeitung auch in dessen Rechtsklickfenster. Bei geöffnetem Reiter werden die Daten regelmäßig neu abgefragt.

**Redstone · intern** zeigt die sechs normalen CC-Seiten `front`, `back`, `left`, `right`, `top`, `bottom` mit Analogwerten von 0 bis 15. Diese Werte entsprechen `redstone.getAnalogInput(side)` und benötigen Drive By Wire nicht. Benutzerdefinierte Ausgänge werden zusätzlich als Quellen dargestellt und im Kanalreiter verwaltet.

Kabel-, Funk- und Endermodems erscheinen einzeln. Ihr API-Fenster zeigt bekannte Anschluss-/Positionsdaten und bei Funkmodems Reichweite und dimensionsübergreifende Fähigkeit. Die Inspektion führt keine Gerätemethode aus und versendet keine Erkennungspakete. Methodensignaturen stammen aus registrierter Dokumentation und erkannten Lua-Methoden; nicht dokumentierte dynamische Typen können als `unknown` erscheinen.

## Oberfläche konfigurieren

In `config/teutonlib-client.toml` auf jedem Client:

```toml
[computerUi]
    enabled = true
```

Standard ist `true`. Nach dem Neuladen von `enabled = false` verschwinden die automatisch ergänzten Reiter; ein offenes Zusatzpanel kehrt zum Terminal zurück. Ohne Dateiüberwachung gilt die Änderung beim nächsten Clientstart. Die Lua-APIs, gespeicherten Kanäle und Peripheral Links bleiben verfügbar. Eigene Addon-Workspaces werden vom jeweiligen Addon gesteuert.

Normale Computer verwenden eine monochrome Darstellung, erweiterte Computer Farbakzente. CC Aeroworks kann denselben Listenrenderer für seine zusätzlichen Quellen und Steuerungsdaten verwenden. Für Addon-Entwickler: [Workspace und eingebettete Computer](Computer-Workspace-und-Kernel).

## Implementierung

[Standard-Workspace](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/computer/StandardComputerWorkspace.kt), [Clientrenderer](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/client/computer/WorkspacePanels.kt), [automatische CC-Einbindung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/mixin/computer/client/ComputerScreenMixin.kt), [ausführliche Bedienung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/computer-channels.md).

[Zur Startseite](Home)

## Abbildung


Echter CC-Blockcomputer mit TeutonLib-Quellenreiter in der separaten Aufnahmewelt.

![Computer-UI: Quellen und Lua-API: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/wiki-sources.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
