# Schlauchkupplung am Item Drain

**Deutsch** · [English](Schlauchkupplung-am-Item-Drain-en) · [Übersicht](Home)

Creates vorhandener **Item Drain** (`create:item_drain`) nimmt oben einen [physischen Schlauchkopf](Physische-Schlauchrolle) auf und verbindet die Rohre an der Rolle mit den Rohren am Drain. Es gibt keinen zusätzlichen Kupplungsblock, kein zusätzliches Item und kein Kupplungsrezept.

## Verbinden und Lösen

1. Stelle eine Create-Schlauchrolle und einen Item Drain auf. Voraussetzung sind **Create 6.0.10 und Sable 2.0.6** sowie die aktive physische Schlauchspitze.
2. Fahre ausreichend Schlauch aus und nimm den Kopf mit dem Create-Schraubenschlüssel auf.
3. Rechtsklicke den Drain mit dem Schraubenschlüssel, um den getragenen Kopf einzusetzen. Die Entfernung muss innerhalb der verfügbaren Schlauchlänge liegen.
4. Rechtsklicke den belegten Drain erneut mit dem Schraubenschlüssel, um den Kopf zu lösen und wieder zu tragen.

Der Kopf sitzt mittig im Drain mit seiner Unterkante bei 7/16 Blockhöhe. Die verriegelte Gitterdarstellung wechselt mit dem Belegungszustand. Kopf und Schlauch bleiben lebende, auch auf gedrehten Konstruktionen gerenderte Entities.

## Flüssigkeitstransport

Die vier Seiten und die Unterseite des Drains stehen als Rohranschlüsse zur Verfügung. Eine Create-Pumpe genügt auf einer beliebigen Seite der gesamten Verbindung. Der Schlauch arbeitet in beide Flussrichtungen und erhält den normalen Pumpendruck. Nur der **Durchfluss wird halbiert**; alle fünf Anschlüsse teilen sich die verfügbare Fördermenge.

Die Schlauchlänge verbraucht keine Pumpenreichweite. Angeschlossene Rohrabschnitte unterliegen weiter Creates Reichweitenprüfung. Während der Kupplung nimmt der Kopf keine Weltflüssigkeit auf und gibt keine ab. Der vorhandene Rollentank bleibt erhalten. Ebenso behält der Drain seinen 1.500-mB-Tank und seine normale Item- und Eimerverarbeitung. Ohne Kopf arbeitet der Drain normal und besitzt keine Schlauchverbindung.

## Schlauchzug und Konfiguration

Rolle und Drain können auf unterschiedlichen Sable-Konstruktionen sitzen. Ein gespannter Schlauch zieht bewegliche Konstruktionen an den Befestigungspunkten zusammen. Ein lockerer Schlauch überträgt keinen Druck; feste Weltblöcke wirken als unbewegliche Anker. Bei anhaltender Überlast löst sich die Kupplung und schließt die Flüssigkeitsverbindung sofort.

```toml
[compat.hosePulley]
springStiffness = 10000.0
damping = 2000.0
breakForce = 50000.0
breakDelaySeconds = 0.25
```

Die Werte stehen in der weltbezogenen `teutonlib-server.toml` und verwenden Sables Physikeinheiten. Die Ingenieursbrille zeigt Verbindung, Durchflussfaktor und Zugauslastung. Sables Kraftanzeige enthält eine eigene Kräftegruppe für den Schlauchzug.

UUID-Verbindungen überstehen Montage und Neustarts. Entladene Chunks pausieren Zugkräfte und Transport, ohne weitere Chunks zu laden. Nach dem Laden arbeitet die Verbindung erst, wenn beide Identitäten wieder zueinander passen. Der Schlauch wickelt sich nicht um Hindernisse.

## Quellen

- [Drain-Verbindung, Speicherung und Anzeige](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/hose/HoseDrainConnection.kt)
- [Zugkraft und gemeinsames Durchflussbudget](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/hose/HoseTension.kt)
- [Bedienung, Lebenszyklus und Testumfang](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/sable-hose-pulley.md)

[Wiki-Startseite](Home) · [Physische Schlauchrolle](Physische-Schlauchrolle)

## Abbildung


Echter Entity-Kopf im Item Drain in der getrennten grafischen Schlauchprüfung.

![Schlauchkupplung am Item Drain: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/hose-seated.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
