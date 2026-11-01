# TeutonLib 1.5.5

- Zusätzlicher versionsgebundener Rohrnetz-Adapter für FluidLogistics 1.3.1 und
  TFMG 1.2.0 mit Create 6.0.10 auf Minecraft 1.21.1.
- Ersetzt den kollidierenden FluidLogistics-Mixin mit Priorität 1100 und passt die
  lokale Distanzvariable an TFMGs primitiven `int` an.
- Erhält FluidLogistics' zusätzliche Pumpenreichweite und berücksichtigt seine
  Pumpen in TFMGs ursprünglichem Suchlauf bei vorhandenem Rohrdruck.
- Eigenständige Versions-/Klassenprüfung; funktioniert ohne Factory Logistics und
  gemeinsam mit dem Queue-/Bestandsadapter aus 1.5.4.
- Binärvertragsprüfungen und dedizierte Rohrnetz-/Transporttests mit Original-JARs.

[Geltungsbereich, Prüfsummen und Testbefehle](fluid-propagation-compatibility.md).

## Prüfergebnis

Geprüft mit NeoForge 21.1.234 / Java 21 und den dokumentierten Original-JARs:

| Prüfung | Ergebnis |
| --- | --- |
| Vollständiger Build | Erfolgreich, 103 JUnit-Tests ohne Fehler |
| Ohne TeutonLib | Ursprünglicher FluidLogistics-/TFMG-Prioritätsabsturz reproduziert |
| Fertiges Release-JAR | Alle sieben nativen Pumpentypen und erweiterte Reichweite geprüft |
| Echte Motor-/Rohrnetze | Wassertransport, Mengenerhaltung, Leitungsunterbrechung und Wiederherstellung bestanden |
| Welt speichern und Prozess neu starten | Fortgesetzter Wassertransport in allen drei Netzen bestanden |
| Zusätzlich Factory Logistics / Abstractions 1.4.9 | Rohrnetztests und bisherige Queue-/Bestandsprüfungen einschließlich Neustart bestanden |
| Nur FluidLogistics / nur TFMG | Erzwungene Klassentransformation und Serverstart mit inaktivem Adapter bestanden |
| Ohne Create und beide Logistikmods | Serverstart und sauberes Beenden bestanden |

Die Prüfung verwendet wegwerfbare Serverwelten. Die ursprünglich betroffene Welt
und der vollständige Client mit Immersive Portals / Sable wurden nicht als
Spielablauf geprüft. Bei TFMGs elektrischer Pumpe wurde die Rohrnetz-Erkennung,
nicht deren Energieversorgung getestet.
