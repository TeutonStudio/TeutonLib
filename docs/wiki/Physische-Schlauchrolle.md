# Physische Schlauchrolle

**Deutsch** · [English](Physische-Schlauchrolle-en) · [Übersicht](Home)

Creates Schlauchrolle erhält eine persistente physische Schlauchspitze. Der Kopf fällt durch Gravitation, kollidiert mit Blöcken und bleibt durch die Schlauchlänge an seiner Rolle gebunden. Auf bewegten und gedrehten Sable-Konstruktionen verwendet die Bindung den projizierten Weltanker.

## Voraussetzungen und Installation

Die Integration benötigt exakt **Create 6.0.10 und Sable 2.0.6**. Beide Mods bleiben optional. Die frühere separate SableSchlauch-Mod mit der ID `hosepulley_sable_bridge` muss entfernt werden. TeutonLib muss auf Server und Clients installiert sein.

**Stand 14.11.2026:** Die Grundfunktion gehört zur Basis 1.6.1. Release 1.6.2 ergänzt Schraubenschlüssel-/Hotbar-Korrekturen und die unten beschriebene Schlauchführung mit Durchhang und Hindernissen. Die historische GIF zeigt ausschließlich Tragen und Werfen.

## Bedienung

- Rechtsklicke eine ausgefahrene Spitze mit dem Create-Schraubenschlüssel, um sie zu tragen.
- Rechtsklicke mit dem Schraubenschlüssel in die Luft, um den getragenen Kopf in Blickrichtung zu werfen.
- Ein Rechtsklick auf einen Item Drain setzt ihn als [Schlauchkupplung](Schlauchkupplung-am-Item-Drain) ein.
- Wechselst du vom Schraubenschlüssel weg, fällt der Kopf herunter. Auch der Wechsel auf einen anderen Hotbar-Slot mit einem weiteren Schraubenschlüssel beendet das Tragen.
- Bei vollständig eingezogenem Schlauch lässt ein normaler Schraubenschlüssel-Rechtsklick den Spieler mitfahren. Schleichen wählt stattdessen das Tragen.

Tragen hebt die Längenbegrenzung nicht auf. Der getragene Kopf blockiert das Anklicken eines dahinterliegenden Blocks nicht. Der Kopf ist feuerfest und entzündet sich auch in Lava nicht.

Flüssigkeiten werden am tatsächlichen Weltblock am unteren Ende der lebenden Spitze angesaugt oder abgegeben. Das gilt auch bei seitlicher Auslenkung und gedrehten Sable-Aufbauten. Creates Quellblocksuche, Mengen und Simulationsregeln bleiben erhalten. Fehlt der zugeordnete Kopf oder ist er entladen, pausiert die Aufnahme und Ausgabe von Weltflüssigkeit.

## Konfiguration

In `<Welt>/serverconfig/teutonlib-server.toml`:

```toml
[compat.hosePulley]
entityHeadMode = "ALWAYS"
```

| Wert | Verhalten |
| --- | --- |
| `ALWAYS` | physische Köpfe an allen Rollen; Standard |
| `SABLE_ONLY` | physische Köpfe nur auf Sable-Sublevels |
| `NEVER` | normale Create-Schläuche ohne Entity-Kopf |

Die Servereinstellung gilt für alle Clients und wird im Spiel übernommen. Darstellung, Längenbegrenzung und Flüssigkeitsziel wechseln gemeinsam; vorhandener Rollentankinhalt bleibt erhalten. Reservierte UUIDs verhindern doppelte Köpfe bei entladenen Entities.

## Grenzen und Speicherung

Die Schlauchlänge ist auf feste **256 Blöcke (Meter)** begrenzt, unabhängig vom Bodenabstand, von Blöcken unter der Rolle und von der unteren Baugrenze. **Entwicklung:** Ein serverseitig simulierter Schlauch hängt durch, schwingt nach und wird an Hindernissen umgelenkt. Diese Führung verbraucht Schlauchlänge und bestimmt die Zugrichtung. Blockiertes Einziehen baut Zug auf; feste Hindernisse werden nicht durchdrungen. Selbstkollision und Verknoten sind ausgeschlossen.

Rolle und Kopf speichern ihre UUID-Verbindung über Chunkentladung und Neustarts. Tragen endet beim Neustart. Der Abbau der geladenen Rolle entfernt ihre Spitze. Die Physik läuft auf dem Server; Clients erhalten Kopfposition, Schlauchlänge und den gemeinsamen Segmentverlauf. Verlaufsänderungen werden höchstens zehnmal pro Sekunde übertragen und auf dem Client interpoliert. Der Schlauch kann auch sichtbar bleiben, wenn sein Kopf außerhalb der Entity-Tracking-Reichweite liegt. Die Darstellung verwendet Creates vorhandene Modelle.

## Entwicklerprüfung und Prüfgrenzen

```sh
python3 tools/verify_hose_pulley.py
python3 tools/verify_hose_pulley_client.py
python3 tools/verify_hose_cable.py
python3 tools/verify_hose_cable_client.py
```

Die getrennte Serverprüfung der neuen Führung bestand am 14.11.2026: Segmentverlauf und Kollisionsproben, NBT-Rundlauf mit Endpunktidentitäten, blockiertes Einziehen und Überlastlösen. Der spätere Clientlauf `20261114-203614-8e820559` erzeugte sechs erfolgreiche Teilaufnahmen: Durchhang, Flux-Modell und langer Schlauch ohne getrackten Kopf, jeweils für zwei Beobachter. Die verglichenen Schlauchpunkte waren identisch. Der gesamte Lauf blieb dennoch fehlgeschlagen: Die anschließende Prüfung zum Pausieren an einem nicht getrackten Chunk lief in ein Zeitlimit. Die Aufnahmen unten belegen die gezeigten Teilfälle, keine vollständige Clientabnahme.

## Quellen

- [Aktuelle Schlauchführung und Physik](https://github.com/TeutonStudio/TeutonLib/blob/master/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/sable/HoseCablePhysics.kt)
- [Lokaler Entwicklungsumfang und Prüftreiber](https://github.com/TeutonStudio/TeutonLib/blob/master/docs/sable-hose-pulley.md)

Die folgenden Quellen dokumentieren die ursprüngliche Grundfunktion in 1.6.0:

- [Schlauchkopf und Interaktionen](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/hose/HoseHeadEntity.kt)
- [Versionsgrenzen](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/HosePulleyCompatibility.kt)
- [Ausführliche Dokumentation und Tests](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/sable-hose-pulley.md)

[Wiki-Startseite](Home) · [Schlauchkupplung am Item Drain](Schlauchkupplung-am-Item-Drain)

## Entwicklungsaufnahmen der Schlauchführung

Durchhang und Führung über ein Hindernis aus der Entwicklung von 1.6.2:

![Schlauchführung mit Hindernis](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/hose-cable-development.png)

Der lange Schlauch bleibt in diesem Teilfall sichtbar, obwohl der Client die Kopf-Entity nicht trackt:

![Langer Schlauch ohne getrackten Kopf](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/hose-cable-long-development.png)

Unbearbeitete Aufnahmen aus dem oben genannten teilweise erfolgreichen Clientlauf; keine vollständige Abnahme der neuen Schlauchphysik.

## Historische Abbildung

Echte Spielaufnahme: Schlauchkopf mit dem Schraubenschlüssel tragen und werfen.

![Physische Schlauchrolle: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/gifs/hose-wrench.gif)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
