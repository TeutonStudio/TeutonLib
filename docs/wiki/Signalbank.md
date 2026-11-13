# Signalbank

**Deutsch** · [English](Signalbank-en) · [Übersicht](Home)

TeutonLib v1.6.0 bietet mit `de.TeutonStudio.TeutonLib.api.computer.channel.SignalBank` eine Entwickler-API für addondefinierte Signalausgänge und zeitbegrenzte Pulse. Das Addon stellt das physische Ausgabebackend und die Zuständigkeitsprüfung bereit. Diese Klasse erzeugt keine zusätzlichen Standard-Lua-Befehle.

## Signalausgänge mit Pulsen

`SignalBank` verwaltet benannte Signale mit stabiler Definitions-UUID und einem `SignalBackend`. Das Backend implementiert Setzen, Entfernen, Umbenennen, Verbindungszählung und vollständiges Zurücksetzen der physisch ausgegebenen Signale.

Der Konstruktor erhält eine Tickuhr, einen Zuständigkeits-/Freigabecallback, eine Backendfactory und optional einen Änderungs-Callback. Standard sind 32 Kanäle, maximal 1200 Pulsticks und das Namensformat `[a-z][a-z0-9_-]{0,31}`. Addons können diese Regeln anpassen.

```kotlin
val signals = SignalBank(
    clock = { level.gameTime },
    authority = { controllerActive },
    backendFactory = { addonBackend },
    changed = { host.setChanged() }
)
signals.addChannel("warning")
signals.tick() // prüft die Freigabe und aktiviert gegebenenfalls Ausgänge
signals.pulse("warning", durationTicks = 20, value = 15)
```

Das Beispiel setzt einen eigenen `SignalBackend` sowie Host und Freigabezustand voraus. Der Host muss `tick()` regelmäßig auf dem Server aufrufen: Abgelaufene Pulse werden auf 0 gesetzt. Verliert die Bank ihre Zuständigkeit, löscht sie Signalwerte und Pulse und sperrt weitere Ausgaben. `setValue` erlaubt 0–15; `pulse` verlangt 1–15 und eine Dauer von 1 bis zum konfigurierten Maximum. `reset`, `resetAll` und `close` löschen Ausgaben.

**Nur die Definitionen werden serialisiert; Werte und laufende Pulse sind flüchtig.** Nach `loadEncodedDefinitions` beginnen Signale bei 0. Dies unterscheidet die Bank von den gespeicherten Ausgangswerten normaler CC-Computer. Beim Entfernen und Umbenennen informiert die Bank das Backend, damit die physische Zuordnung mitgeführt werden kann. `snapshot()` liefert UUID, Namen, aktuellen Wert und Verbindungszahl.

## Implementierung

[Signalbank, Definitionen und Backendvertrag](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/channel/SignalBank.kt), [Vertragstests](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/test/kotlin/de/TeutonStudio/TeutonLib/api/computer/channel/PersistentChannelBanksTest.kt).

Siehe auch [persistente Kanalgruppen](Persistente-Kanalgruppen), [Computer-Kanäle](Computer-Kanaele) und [gemeinsame API-Übersicht](Persistente-Kanalgruppen-und-Signale).

[Zur Startseite](Home)

## Abbildung


Ausgeführte Prüfung von Signalfreigabe, Pulsende und Rücksetzen nach dem Laden. Die Signalbank hat keine eigene GUI.

![Signalbank: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-PersistentChannelBanksTest.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
