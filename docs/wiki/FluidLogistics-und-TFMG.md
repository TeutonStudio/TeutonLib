# FluidLogistics und TFMG

TeutonLib ermöglicht das gemeinsame Rohrnetz von FluidLogistics und Create: The Factory Must Grow (TFMG), indem es einen Konflikt in Creates Flüssigkeits-Propagation behebt und FluidLogistics-Pumpen in TFMGs Suchlauf berücksichtigt.

## Voraussetzungen und Verwendung

Der Adapter wird automatisch ausschließlich mit **Minecraft 1.21.1, Create 6.0.10, FluidLogistics 1.3.1 und TFMG 1.2.0** aktiv. Factory Logistics ist dafür nicht erforderlich. Der [Factory-Logistics-Adapter](FluidLogistics-und-Factory-Logistics) kann gleichzeitig aktiv sein.

Verwende die vorhandenen Rohre, Pumpen und Tanks wie gewohnt. Es gibt keinen zusätzlichen Pumpenblock oder TeutonLib-Konfigurationsschalter. Die erweiterte Reichweite wird weiterhin durch FluidLogistics und dessen Einstellungen bestimmt.

## Wirkung

TFMG überschreibt `FluidPropagator.propagateChangedPipe`. FluidLogistics' ursprünglicher Mixin kann in den überschriebenen Methodenkörper nicht mit seiner bisherigen Priorität und lokalen Variablenbindung injizieren. Der Fehler kann deshalb erst beim Laden einer Welt oder beim Rendern auftreten.

TeutonLib ersetzt ausschließlich den betroffenen FluidLogistics-Mixin. TFMGs eigener Mixin bleibt aktiv. Der Ersatz erfasst den vorherigen Druck, sammelt zusätzliche Suchanfänge an Creates Reichweitengrenze und lässt FluidLogistics' originalen Netzwerk-Updater die Suche fortsetzen. TFMGs Pumpenliste erkennt zusätzlich FluidLogistics-Pumpen, auch wenn vorhandener Rohrdruck die Suche bereits über Creates normale Reichweite hinausführt.

TFMGs mechanische und elektrische Pumpen, Achsenprüfung und Flusslogik bleiben erhalten. Es gibt keinen zusätzlichen dauerhaften Pumpenzähler und keine neuen Weltdaten. Ein ausgeschaltetes `fluidPumpEnabled` wird bei FluidLogistics' zusätzlicher Suche weiterhin berücksichtigt; dieser Fremdmod-Schalter allein behebt den ursprünglichen Mixin-Konflikt nicht.

## Versionsgrenzen und Prüfung

Relevante Klassen von Create, FluidLogistics und TFMG müssen zusätzlich zu den Versionsnummern den hinterlegten SHA-256-Werten entsprechen. Fehlende oder andere Versionen aktivieren keinen Ersatz. Geänderte Klassen mit passenden Versionsnummern erzeugen vor der Abschaltung des Originals eine Fehlermeldung.

Die Entwicklungsprüfung erzwingt die betroffene Klassentransformation und testet reale Pumpen-/Tanknetze, erweiterte Reichweite, Mengenerhaltung, Leitungsunterbrechung und Neustart. Die Energieversorgung einer TFMG-Elektropumpe gehört nicht zum Transporttest; deren Erkennung wird separat geprüft.

## Quellen

- [Versions- und Fingerprintprüfung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/FluidPropagationCompatibility.kt)
- [TFMG-Propagationserweiterung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/mixin/fluids/TfmgFluidPropagatorMixin.kt)
- [Technische Beschreibung und Testprofile](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/fluid-propagation-compatibility.md)

[Wiki-Startseite](Home) · [FluidLogistics und Factory Logistics](FluidLogistics-und-Factory-Logistics)
