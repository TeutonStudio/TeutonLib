# FluidLogistics und Factory Logistics

**Deutsch** · [English](FluidLogistics-und-Factory-Logistics-en) · [Übersicht](Home)

TeutonLib behebt die kollidierenden Bestands- und Promise-Queue-Mixins von FluidLogistics und Factory Logistics. Beide Mods verwenden anschließend denselben maßgeblichen Ressourcenbestand und dieselben Lieferzusagen.

## Voraussetzungen

Der Adapter wird automatisch und ausschließlich für folgende Kombination aktiviert:

| Komponente | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| Create | 6.0.10 |
| FluidLogistics | 1.3.1 |
| Create Factory Logistics | 1.4.9 |
| Create Factory Abstractions | 1.4.9, im Factory-Logistics-JAR enthalten |

Alle Mods bleiben externe Abhängigkeiten. Für Spieler gibt es keinen zusätzlichen Block, Befehl oder Config-Schalter: Installiere die passende Kombination und verwende die vorhandenen Bestell- und Lagernetzwerke wie gewohnt.

## Korrigiertes Verhalten

FluidLogistics' ursprüngliche Mixins treffen bei dieser Kombination auf von Factory Logistics ergänzte Methodenüberladungen und scheitern bereits bei der Klassentransformation. TeutonLib ersetzt genau die beiden betroffenen Queue-/Bestands-Mixins. Vollständige Methodensignaturen verhindern die falsche Auswahl.

Factory Logistics bleibt Eigentümer der Daten. FluidLogistics-Ressourcenidentitäten werden auf dessen lebende Sammlungen angewendet; es entsteht kein zusätzlicher persistenter Index.

- Gleiche Ressourcen in unterschiedlichen Trägerkomponenten werden gemeinsam erkannt; verschiedene Komponenten der enthaltenen Ressource bleiben getrennt.
- Teillieferungen, abgelaufene Zusagen und erzwungenes Löschen verändern dieselbe Sammlung, die gespeichert und gezählt wird.
- Bestandsänderungen aktualisieren den sortierten Cache. Mengenberechnungen verwenden `long` und werden bei Creates Unendlichkeitsgrenze begrenzt.
- Item-Map, gefilterte Summen und Creates Item-Bestandscodec lesen den gemeinsamen generischen Speicher. Komponentenvarianten überschreiben einander nicht.
- Andere generische Ressourcen und das bestehende Queue-NBT bleiben unter Factory Logistics' Verwaltung.

## Grenzen und Diagnose

Vor dem Ersetzen prüft TeutonLib die relevanten Originalklassen per SHA-256. Andere oder fehlende Versionen aktivieren keinen Adapter. Stimmen die Versionsnummern, aber die Klassen nicht mit den geprüften Releases überein, bricht die Aktivierung mit einer Diagnose ab. Eine fremde oder nachträglich veränderte JAR wird dadurch nicht stillschweigend gepatcht.

Der Adapter ist unabhängig von [FluidLogistics und TFMG](FluidLogistics-und-TFMG); beide können gleichzeitig aktiv sein. Automatisierte Prüfungen verwenden eigene Testwelten und ersetzen keine vollständige Spielprüfung eines bestehenden Bestellnetzwerks.

## Quellen

- [Versions- und Klassenprüfung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/LogisticsCompatibility.kt)
- [Gemeinsamer Ressourcenadapter](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/logistics/LogisticsResourceAdapter.kt)
- [Ausführliche Dokumentation und Prüfungen](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/logistics-compatibility.md)

[Wiki-Startseite](Home) · [FluidLogistics und TFMG](FluidLogistics-und-TFMG)

## Abbildung


Ausgeführte Desktop-Prüfung der Versions- und Klassenverträge des gemeinsamen Ressourcenadapters; keine neue Bestelloberfläche.

![FluidLogistics und Factory Logistics: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-LogisticsCompatibilityTest.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
