# Flüssigkeitsoberfläche in Sable-Tanks

**Enthalten ab TeutonLib 1.6.2.** Die Bilder und der unten datierte Spieltest stammen aus der Entwicklungsprüfung vom 05.11.2026.

Die Clientdarstellung von Creates Fenstertanks berücksichtigt die tatsächliche
Sable-Ausrichtung. Flüssigkeit sammelt sich an der weltunteren Seite des
Tankinnenraums; ihre freie Oberfläche bleibt waagerecht zur Welt. Das gilt auch
für liegende und umgedrehte Tanks. Als leichter als Luft deklarierte Fluide
belegen entsprechend den oberen Teil.

Die Darstellung verwendet Creates interpolierten Füllanteil und die originalen
Innenmaße des Multiblocktanks. Die Schnitthöhe wird so bestimmt, dass das
eingeschlossene Volumen diesem Füllanteil entspricht. Bei einem liegenden Tank
ist die Flüssigkeit daher nicht einfach der unverändert gedrehte aufrechte
Flüssigkeitsquader. Die Polygone enden an den Innenwänden.

## Voraussetzungen und Umfang

- Minecraft 1.21.1, Create 6.0.10 und Sable 2.0.6.
- Der Create-Tank gehört zu einem clientseitigen Sable-Sublevel und besitzt Fenster.
- Die Integration benötigt weder Train Functionality noch Immersive Portals.
- Gewöhnliche Welt-Tanks und Creates Kesseldarstellung verwenden ihren nativen Renderer.

Die Korrektur ändert keine gespeicherten Flüssigkeitsmengen, Tankkapazitäten,
Rohrverbindungen, Dockingverbindungen oder Physikkörper. Sie stellt den
Gleichgewichtszustand bei Weltgravitation dar; Schwappen und Beschleunigungskräfte
werden nicht simuliert. Eine gesonderte Freigabe für die Portalprojektion ist
damit nicht verbunden.

## Kosten und Prüfung

Die Flüssigkeitsgeometrie wird für Tankinnenmaße, Füllanteil und die Richtung der
Weltvertikalen im lokalen Tankraum zwischengespeichert. Eine reine Verschiebung
benötigt keine erneute Volumenberechnung. Änderungen an Füllstand oder Neigung
aktualisieren ausschließlich die Flüssigkeitsdarstellung.

Die Mathematiktests prüfen Volumenerhaltung, Oberflächennormale, Grenzen,
Extremfüllstände sowie aufrechte, liegende und umgedrehte Tanks. Der optionale
Spieltest verwendet eine wegwerfbare Kopie von `run/client/saves/Tank Test`:

```sh
./gradlew test --offline
python3 tools/verify_tank_fluid.py
```

Der Spieltest benötigt OpenGL und eine grafische Sitzung. Testmod und Weltkopie
werden nicht mit der TeutonLib-JAR ausgeliefert. Prüfberichte und Bildschirmbilder
liegen unter `build/reports/tank-fluid-client/` beziehungsweise der dort referenzierten
Testinstanz. Der ursprüngliche Spielstand wird nicht geöffnet oder verändert.

Prüfung am **05.11.2026**: 174 JUnit-Tests bestanden. Der grafische Vanilla-Client
bestand zwölf Aufnahmen an einer Kopie der beiden vorhandenen 3×3×9-Tanks:
Schokolade im gespeicherten Aufbau, Wasser bei 10/50/90 Prozent in drei
Ausrichtungen sowie unveränderte und verschobene Wiederholung. Das beobachtete
Flüssigkeitsvolumen entsprach dem interpolierten Füllanteil; die Oberfläche blieb
weltwaagerecht. Die Wiederholungen verwendeten dieselbe berechnete Geometrie.
Der SHA-256-Vergleich aller Quelldateien bestätigte den unveränderten Originalspielstand.
Diese Prüfung misst die Darstellung und Cache-Wiederverwendung, keinen allgemeinen
FPS- oder Physik-Performancegewinn.
