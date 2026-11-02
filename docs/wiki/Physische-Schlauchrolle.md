# Physische Schlauchrolle

Creates Schlauchrolle erhält eine persistente physische Schlauchspitze. Der Kopf fällt durch Gravitation, kollidiert mit Blöcken und bleibt durch die Schlauchlänge an seiner Rolle gebunden. Auf bewegten und gedrehten Sable-Konstruktionen verwendet die Bindung den projizierten Weltanker.

## Voraussetzungen und Installation

Die Integration benötigt exakt **Create 6.0.10 und Sable 2.0.6**. Beide Mods bleiben optional. Die frühere separate SableSchlauch-Mod mit der ID `hosepulley_sable_bridge` muss entfernt werden. TeutonLib muss auf Server und Clients installiert sein.

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

Die Leine ist eine kugelförmige Längenbeschränkung und wickelt sich nicht um Hindernisse. Beim Ausfahren begrenzen feste Hindernisse in der senkrechten Weltspalte und die untere Baugrenze die Länge. Blockiert eine Kollision das Einziehen, hat sie Vorrang vor der idealen Leinenlänge.

Rolle und Kopf speichern ihre UUID-Verbindung über Chunkentladung und Neustarts. Tragen endet beim Neustart. Der Abbau der geladenen Rolle entfernt ihre Spitze. Die Physik läuft auf dem Server; Clients erhalten die Positionen synchronisiert und verwenden Creates vorhandene Modelle.

## Quellen

- [Schlauchkopf und Interaktionen](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/hose/HoseHeadEntity.kt)
- [Versionsgrenzen](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/HosePulleyCompatibility.kt)
- [Ausführliche Dokumentation und Tests](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/sable-hose-pulley.md)

[Wiki-Startseite](Home) · [Schlauchkupplung am Item Drain](Schlauchkupplung-am-Item-Drain)
