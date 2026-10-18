# PC-UI-Vorlage für Addons

Diese Java-Vorlage wird mit TeutonLib 1.0.0 kompiliert und im opt-in Clienttest
gerendert. Zielplattform: Minecraft 1.21.1, NeoForge 21.1.234, Java 21,
KotlinForForge 5.11.0 und CC:Tweaked 1.120.2.

`TemplateComputerScreen` ist ein vollständiger Bildschirm für ein bestehendes
`AbstractComputerMenu`: CC-Terminal, Gehäuse, Strom-/Neustartleiste sowie gemeinsame
Quellen- und Kanalreiter. Die Vorlage enthält keinen neuen Computerblock.
Blockregistrierung, ServerComputer-Lebenszyklus, Menüöffnung und Rechteprüfung
bleiben Aufgaben des Addons.

## Abhängigkeit

Die Release-JAR auf Client und Server installieren. Im Addon-Projekt beispielsweise:

```groovy
dependencies {
    implementation files('libs/teutonlib-1.0.0.jar')
}
```

Alternativ in TeutonLib `./gradlew publish` ausführen und das erzeugte lokale
Maven-Repository `repo/` im Addon eintragen. Die Koordinate lautet
`de.TeutonStudio.TeutonLib:teutonlib:1.0.0`. Minecraft, NeoForge, KotlinForForge und
die Original-JAR von CC:Tweaked muss das Addon selbst bereitstellen; sie werden
nicht transitiv durch die lokale TeutonLib-Publikation installiert.

Das Addon deklariert TeutonLib in seiner `neoforge.mods.toml`:

```toml
[[dependencies.my_mod]]
modId="teutonlib"
type="required"
versionRange="[1.0.0,1.1.0)"
ordering="AFTER"
side="BOTH"
```

`my_mod` durch die eigene Mod-ID ersetzen und CC:Tweaked sowie KotlinForForge
ebenfalls als erforderliche Abhängigkeiten deklarieren. Die Versionsgrenze oben
begrenzt die Übernahme auf die hier geprüfte API-Linie.

## Bildschirm übernehmen

1. `src/main/java/example/teutonlib/client/TemplateComputerScreen.java` ins eigene
   Clientpaket kopieren und den Paketnamen ändern.
2. Den Bildschirm im clientseitigen `RegisterMenuScreensEvent` für den eigenen
   Menütyp registrieren, zum Beispiel mit
   `event.register(MY_COMPUTER_MENU.get(), TemplateComputerScreen::new)`.
3. Der Menütyp muss `AbstractComputerMenu` erweitern, zum tatsächlich laufenden
   `ServerComputer` gehören und `stillValid` korrekt prüfen. Als Display-Stack
   das eigene Addon-Item verwenden. Native CC-Computer erhalten ihre Reiter bereits
   automatisch; dort darf die Vorlage keine zweite Leiste anlegen.
4. Die Registrierung ausschließlich auf `Dist.CLIENT` laden. Gemeinsamer Setupcode
   darf die Bildschirmklasse auf einem Dedicated Server nicht laden.

Der Bildschirm übernimmt Widgetaufbau, Seitenwechsel, Terminal-Sichtbarkeit,
Fokus nach Klicks und Tastaturaktionen, die Inventartaste im Namensfeld, Escape
zum Terminal und den Neuaufbau bei Fenster-/GUI-Skalierungsänderungen.
Die Netzwerkantworten gelangen über `ComputerPanels.Owner` an den aktuellen
Controller. Keine eigenen Request-/Snapshot-Handler duplizieren.

## Eigenen Computer an die gemeinsame Laufzeit anbinden

`TemplateComputerIntegration.register(...)` einmal im Common Setup aufrufen,
beispielsweise aus `FMLCommonSetupEvent.enqueueWork`. Bei optionaler Integration
zuerst auf beide Mods prüfen und erst danach die Integrationsklasse laden.

Der Host-Resolver erhält das `IComputerSystem`. Er muss ausschließlich den
geladenen Block zurückgeben, dessen laufender `ServerComputer` zu genau diesem
System gehört. Für fremde Computer `null` zurückgeben. Zum Identitätsvergleich
eignet sich `getMainThreadMonitor()`; eine Computer-ID allein ist nicht eindeutig.
Nur bereits geladene Hosts durchsuchen, keine Chunks laden.

Der Host ist eine `BlockEntity`. TeutonLib stellt ihr über `ChannelHost` den
persistenten Kanalbestand bereit und speichert ihn unter `TeutonLibChannels`.
Beim Wechsel des zuständigen Blocks eines Multiblocks muss das Addon diesen
Bestand zum neuen Eigentümer übertragen; die Vorlage definiert keine eigene
Multiblock-Migration. Ein einfacher einzelner Computerblock braucht das nicht.

Quellen- und Kanalprovider laufen auf dem Serverthread und sollten für fremde
Computer eine leere Liste liefern. Keine Peripherals selbst anhängen. Beispiele
für Rückgabewerte, nachdem die Zuständigkeit des Addons geprüft wurde:

```java
new SourceView("my_mod:telemetry", List.of("telemetry"), "local",
        computer.position().toShortString(), "");
new ChannelView("my_mod.rpm", "output", "", true,
        currentRpm, 0, "my_mod", false);
```

Fremde Kanäle bleiben im gemeinsamen Editor schreibgeschützt. Eigene Wertebereiche
und Mutationen gehören dem Addon. Präfixe vermeiden Namenskollisionen.
`register...` liefert bei direkter Verwendung einen `AutoCloseable`-Handle;
die Setupvorlage registriert für die gesamte Mod-Lebensdauer.

## Übernahme in CC Aeroworks

Die Vorlage zeigt den Aufbau für eine spätere Migration. CC Aeroworks 1.4.0 behält
in diesem Release seine vorhandenen Pultreiter; TeutonLib ergänzt dort weiterhin
Peripheral-Quellen. Vor einer Ersetzung muss CC Aeroworks seine GPS-/Display-Link-
und Skriptquellen als Provider anbinden, den aktiven Pultcomputer als Host auflösen,
den Kanalbestand bei Pultteilung/-vereinigung erhalten und seine Controls-Navigation
ergänzen. Die bestehende Lua-API `channels`, Kanalgruppen und DbW-Override-Semantik
dürfen dabei nicht durch die einfache TeutonLib-Kanalverwaltung ersetzt werden.

## Prüfen

Im TeutonLib-Quellprojekt:

```bash
./gradlew build compilePeripheralTestJava --offline
./gradlew runClient -PperipheralTests -PcomputerUiTests -PperipheralRuntime=cc -PcsoRuntime=none --offline
python3 tools/verify_peripheral_links.py cc
python3 tools/verify_peripheral_links.py console --dbw --channels-only
./gradlew releaseBundle --offline
```

Die Vorlage wird bei jedem `build` kompiliert. Der Clienttest prüft die native
CC-Ansicht und den Vorlagenbildschirm jeweils mit normalem/erweitertem Computer und
GUI-Skalierung 2/3. Er prüft Fokus, Seitenwechsel und neue Sitzungen nach Resize.
Er arbeitet mit synthetischen Menüs ohne Welt; er ersetzt keinen In-World-Test
des eigenen Addon-Hosts. Serverprüfungen decken echte Kanäle, Chunk-Lebenszyklus,
Neustart und Berechtigungsgrenzen getrennter Menüs ab.

Die TeutonLib-Lizenz bleibt All Rights Reserved. `TEMPLATE_LICENSE.txt` betrifft
ausschließlich die ursprünglich von NeoForged gelieferten MDK-Dateien.
