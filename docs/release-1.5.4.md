# TeutonLib 1.5.4

- Versionsgebundener Adapter für FluidLogistics 1.3.1 und Factory Logistics /
  Factory Abstractions 1.4.9 mit Create 6.0.10 auf Minecraft 1.21.1.
- Ersetzt die beiden kollidierenden FluidLogistics-Mixins für Promise-Queue und
  InventorySummary gemeinsam über das mitgelieferte MixinSquared 0.3.7.
- Ressourcenvergleich, Teillieferungen, Ablauf, Löschen und Bestandsabfragen
  verwenden Factory Logistics' tatsächlichen Speicher.
- Gefilterte Summen, Item-Map und Item-Bestandscodec erfassen alle Trägervarianten.
- Pflicht-Injection-Punkte und SHA-256-Prüfungen der Originalklassen sichern die
  unterstützte Kombination ab. Andere Versionskombinationen bleiben unverändert.
- Automatisierte Binärvertrags- und dedizierte Serverprüfungen einschließlich
  Speicherung und erneutem Start einer Testwelt.

[Details, Prüfsummen und Testbefehle](logistics-compatibility.md).

## Prüfergebnis

Geprüft mit NeoForge 21.1.234 / Java 21 und den dokumentierten Original-JARs:

| Prüfung | Ergebnis |
| --- | --- |
| `./gradlew build --offline` | Erfolgreich, 97 JUnit-Tests ohne Fehler |
| Kombination ohne TeutonLib | Ursprünglicher `RequestPromise`-/`GenericStack`-Deskriptorfehler reproduziert |
| Gemeinsamer Betrieb, Entwicklungsquellen | Buchführung und Welt-Neustart bestanden |
| Gemeinsamer Betrieb, fertiges Release-JAR | Buchführung, alle Bestandspfade und Queue-/Bestands-Neustart bestanden |
| Nur FluidLogistics / nur Factory Logistics | Serverstart und sauberes Beenden bestanden |
| Ohne beide Logistikmods / zusätzlich ohne Create | Serverstart und sauberes Beenden bestanden |

Die Tests verwenden wegwerfbare Welten. Die ursprünglich betroffene Benutzerwelt,
Clientoberflächen und ein vollständiges Stock-Link-/Gauge-Bestellnetzwerk wurden
nicht als Spielablauf geprüft.
