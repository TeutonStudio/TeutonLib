# TeutonLib 1.2.1

Patch-Release für Minecraft **1.21.1**, NeoForge **21.1.234 oder neuer innerhalb der 21.1-Linie**, Java **21** und KotlinForForge **5.11.0 bis kleiner 6**.

## Änderungen gegenüber 1.2.0

- Flux-Point-Anschlüsse werden bei einer aktiven Create-New-Age-Motorverlängerung korrekt erkannt.
- Die Verbindung wird nur aktiviert, wenn die Motorverlängerung, der Motor, die Ausrichtung und die Energieübertragung tatsächlich zusammenpassen.
- Der Anschlussbereich des Flux Points wird über eine gezielte Mixin-Integration bis zur Motorverlängerung erweitert.
- Das Standard-Testprofil lädt Drive by Wire, Create New Age und Flux Networks automatisch, sofern die lokalen JARs vorhanden sind.

## Downloads und Prüfung

- `teutonlib-1.2.1.jar`: Mod für den `mods`-Ordner auf Client und Server.
- `teutonlib-1.2.1-sources.jar`: Kotlin-Quellen für Entwickler.
- `teutonlib-1.2.1-computer-ui-template.zip`: Mod, Quellen, Dokumentation, Lizenzen und Integrationsbeispiele.

Die Release-Artefakte werden mit `./gradlew releaseBundle` erzeugt. Die vollständige grafische Verbindungsszene wurde nach dem Build mit Create New Age und Flux Networks geprüft.
