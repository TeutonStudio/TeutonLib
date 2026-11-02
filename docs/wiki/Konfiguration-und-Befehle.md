# Konfiguration und Befehle

TeutonLib 1.6.0 verwendet NeoForges eingebauten Konfigurationseditor unter **Mods → TeutonLib → Config**. Die Beschriftungen sind auf Deutsch und Englisch verfügbar. Die zusätzliche Mod Configured wird dafür nicht benötigt.

## Dateien und Editor

| Einstellung | Datei |
| --- | --- |
| Lokale Computeroberfläche | `config/teutonlib-client.toml` |
| Einzelspielerwelt | `saves/<Welt>/serverconfig/teutonlib-server.toml` |
| Dedicated Server | `<Weltordner>/serverconfig/teutonlib-server.toml` |

Die Serverdatei wird automatisch erzeugt. Eine vorhandene globale `config/teutonlib-server.toml` wird bei einer neuen Welt als Vorlage übernommen; anschließend verwendet jede Welt ihren eigenen Bestand.

Im Hauptmenü ist keine Weltkonfiguration geladen. Auf einem entfernten Server und in einer für LAN geöffneten Welt sperrt NeoForge die Servereinstellungen im Editor. Bearbeite dort die Datei auf dem Server. Clientoptionen bleiben lokal bearbeitbar. Änderungen im Editor werden beim Verlassen der jeweiligen Config-Seite gespeichert. Dateiänderungen werden bei aktiver NeoForge-Dateiüberwachung während des Spiels neu geladen.

## Serveroptionen

Die folgende Tabelle enthält die Schlüssel und Standardwerte. Abschnitt und Schlüssel bilden zusammen den vollständigen Pfad.

| Abschnitt | Schlüssel | Standard | Wirkung |
| --- | --- | --- | --- |
| `modules.createSableOptimization` | `enabled` | `true` | Zahnradbrücken über verriegelte Docks |
| `compat.simulated` | `enableAnalogDocking` | `true` | Analoge Docking-Ausleger |
| `compat.createNewAge` | `motorExtensionEnergyTransfer` | `true` | FE-Eingang an Motorerweiterungen |
| `compat.peripheralLink` | `range` | `256` | Reichweite in Weltblöcken; 1 bis 30.000.000 |
| `compat.peripheralLink` | `maxLinksPerNetwork` | `64` | Aktive Links je Zielnetz; 1 bis 4.096 |
| `compat.hosePulley` | `entityHeadMode` | `"ALWAYS"` | `ALWAYS`, `SABLE_ONLY` oder `NEVER` |
| `compat.hosePulley` | `springStiffness` | `10000.0` | Schlauch-Zugsteifigkeit; 0 bis 1.000.000.000 |
| `compat.hosePulley` | `damping` | `2000.0` | Axiale Dämpfung; 0 bis 1.000.000.000 |
| `compat.hosePulley` | `breakForce` | `50000.0` | Haltekraft; 1 bis 1.000.000.000 |
| `compat.hosePulley` | `breakDelaySeconds` | `0.25` | Dauerüberlast bis zum Lösen; 0,01 bis 60 Sekunden |
| `trains` | `hybridByDefault` | `true` | Sable-Bahnhofsbau und Hybrid-Fahrbetrieb |

Die Kraftparameter verwenden Sables Krafteinheiten. Der Server gibt den wirksamen Modul-, Schlauchkopf- und Hybridzugzustand vor. Fehlende oder nicht unterstützte Mod-Versionen können durch einen Config-Wert nicht freigeschaltet werden.

Beispiel:

```toml
[trains]
hybridByDefault = true

[compat.hosePulley]
entityHeadMode = "SABLE_ONLY"
```

## Clientoption

```toml
[computerUi]
enabled = true
```

`false` entfernt die zusätzlichen Quellen-/Kanalreiter nativer CC-Blockcomputer auch bei geöffneter Oberfläche nach einem Neuladen. Kanalspeicherung, Lua-APIs und Peripheral Links bleiben verfügbar. Addons können ihre eigenen Workspace-Oberflächen unabhängig steuern.

## Befehle

| Befehl | Wirkung | Berechtigung |
| --- | --- | --- |
| `/teutonlib cso status` | Config-Wert, effektiver Zustand und fehlende Voraussetzungen | alle |
| `/teutonlib cso enable` | Zahnradbrücken einschalten und speichern | Stufe 2 / OP |
| `/teutonlib cso disable` | Zahnradbrücken ausschalten und speichern | Stufe 2 / OP |
| `/teutonlib train status` | Modus des nächsten geladenen Zuges anzeigen | Stufe 2 / OP |
| `/teutonlib train edit` | Alle Wagen des angehaltenen Zuges bearbeiten | Stufe 2 / OP |
| `/teutonlib train run` | Aufbau sperren und Fahrbetrieb freigeben | Stufe 2 / OP |

Die Zugbefehle benötigen die aktive [Hybridintegration](Hybridzuege) und wählen einen Zug über den nächsten geladenen Wagen im Umkreis von 32 Blöcken. Alle Wagen müssen geladen sein; während Portalübertragungen sind Moduswechsel gesperrt. Im Einzelspieler benötigen OP-Befehle aktivierte Cheats.

Mobile JVM-Startparameter stehen unter [Android und Zalith](Android-und-Zalith-Kompatibilitaet).

Quellen: [ServerConfig](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/ServerConfig.kt), [ClientConfig](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/ClientConfig.kt), [Zugbefehle](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/hybrid/HybridTrainCommands.kt).
