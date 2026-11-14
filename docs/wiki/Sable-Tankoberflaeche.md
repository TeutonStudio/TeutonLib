# Sable-Tankoberfläche — Entwicklung

**Deutsch** · [English](Sable-Tankoberflaeche-en) · [Übersicht](Home)

**Enthalten ab TeutonLib 1.6.2. Die Bilder stammen aus der Entwicklungsprüfung vom 05.11.2026.**

Creates Fenstertanks erhalten auf Sable eine weltwaagerechte Flüssigkeitsoberfläche. Diese wird gegen den geneigten Tankinnenraum geschnitten und erhält den Füllanteil. Bei unverändertem Füllstand und gleicher Neigung wird die Geometrie wiederverwendet. Eine reine Verschiebung benötigt keine erneute Berechnung derselben Form.

Die getrennte grafische Entwicklungsprüfung zeigt aufrechte, seitliche und geneigte Tanks mit unterschiedlichen Füllständen sowie Cache- und Verschiebungsfälle. Tankinhalt und Flüssigkeitstransport bleiben bei Create. [Umfang und Prüfung](https://github.com/TeutonStudio/TeutonLib/blob/master/docs/sable-tank-fluid.md).

## Voraussetzungen und Bedienung

Benötigt werden **Create 6.0.10 und Sable 2.0.6** unter Minecraft 1.21.1. Der Create-Tank muss Fenster besitzen und zu einem clientseitigen Sable-Sublevel gehören. Die Darstellung greift automatisch; Train Functionality und Immersive Portals sind dafür nicht erforderlich.

Aufrechte, liegende und umgedrehte Tanks verwenden die Weltgravitation. Als leichter als Luft deklarierte Fluide belegen den oberen Teil. Normale Welttanks und Creates Kesseldarstellung behalten ihren nativen Renderer.

## Grenzen und Prüfung

Die Korrektur betrifft die Darstellung des Gleichgewichtszustands. Sie ändert keine Tankkapazität, Flüssigkeitsmengen, Rohrverbindungen oder Physikkörper. Schwappen und Beschleunigungskräfte werden nicht simuliert; die Portalprojektion ist damit nicht gesondert freigegeben.

```sh
./gradlew test --offline
python3 tools/verify_tank_fluid.py
```

Der grafische Treiber benötigt OpenGL und eine Wegwerfkopie der lokalen Welt `Tank Test`. Der dokumentierte Lauf vom 05.11.2026 umfasst zwölf Aufnahmen mit Wasser und Schokolade, mehreren Füllständen/Ausrichtungen sowie Wiederverwendung der Geometrie bei unverändertem oder verschobenem Tank. Die damals berichteten 174 JUnit-Tests gehören zu diesem Entwicklungsstand. Die Prüfung misst keinen allgemeinen FPS- oder Physikgewinn.

## Abbildung

Unveröffentlichter Entwicklungsstand: echte gerenderte Tankoberflächen bei geneigtem und seitlichem Tank. Nicht in der Release-JAR 1.6.0 enthalten.

![Sable-Tankoberfläche — Entwicklung: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/tank-tilted-development.png)

![Sable-Tankoberfläche — Entwicklung: 2](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/tank-sideways-development.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
