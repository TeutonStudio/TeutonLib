# Physische Schlauchspitze aus SableSchlauch

TeutonLib integriert die Funktionen aus dem lokalen Projekt SableSchlauch für
**Create 6.0.10 und Sable 2.0.6**. Beide Mods bleiben optional. Ohne diese geprüfte
Kombination werden weder Schlauchrollen-Mixins noch Schlauchkopf-Entities aktiviert.
Die separate Mod `hosepulley_sable_bridge` muss entfernt werden.

## Konfiguration

Die aktive Welt-Datei `<Welt>/serverconfig/teutonlib-server.toml` enthält:

```toml
[compat.hosePulley]
entityHeadMode = "ALWAYS"
```

- `ALWAYS`: Entity-Kopf an allen Schlauchrollen (Standard).
- `SABLE_ONLY`: Entity-Kopf nur, solange die Rolle zu einem Sable-Sublevel gehört.
- `NEVER`: Keine Entity-Köpfe; Create verwendet seinen normalen Schlauch.

Die Server-Einstellung gilt für alle Clients und wird während des Spiels übernommen.
Geladene, nicht mehr erlaubte Köpfe werden entfernt; beim Einschalten werden sie
wieder erzeugt. Unbekannte oder entladene UUIDs bleiben reserviert, damit kein
zweiter Kopf für denselben Schlauch entsteht. Bei `SABLE_ONLY` folgt der Kopf dem
Zusammenbau und Abbau des Sable-Aufbaus. Auch Darstellung, Längenbegrenzung und
Fluid-Ziel wechseln gemeinsam zwischen dem physischen Kopf und Creates normalem
Verhalten. Bereits im internen Tank gespeicherte Flüssigkeit bleibt erhalten.

## Bedienung

- Create-Schlauchrollen erzeugen eine persistente physische Schlauchspitze.
- Die physische Spitze ist feuerfest und entzündet sich auch in Lava nicht.
- Die Spitze fällt durch Gravitation, kollidiert mit Blöcken und bleibt über eine
  serverseitig simulierte Schlauchführung mit der Rolle verbunden. Bewegte und gedrehte
  Sable-Konstruktionen verwenden ihren projizierten Weltanker.
- Mit dem Create-Schraubenschlüssel rechtsklicken, um eine ausgefahrene Spitze zu
  tragen. Ein Rechtsklick in die Luft wirft sie in Blickrichtung; ein Rechtsklick
  auf einen Item Drain setzt sie ein. Der getragene Kopf verdeckt dabei kein
  Block-Klickziel. Beim Abwählen des Schraubenschlüssels (auch beim Wechsel auf
  einen anderen Hotbar-Slot mit Schraubenschlüssel) endet das Tragen und die
  Spitze fällt herunter. Tragen hebt die Längenbegrenzung nicht auf.
- Bei vollständig eingezogenem Schlauch erlaubt ein normaler Rechtsklick mit dem
  Schraubenschlüssel das Mitfahren; Schleichen wählt stattdessen das Tragen.
- Die Schlauchlänge ist auf feste **256 Blöcke (Meter)** begrenzt, unabhängig vom
  Abstand zum Boden und zur unteren Baugrenze. Auch Blöcke direkt unter der Rolle
  begrenzen das Ausfahren nicht; die Spitze kollidiert weiterhin mit der Welt.
  Einziehen bleibt möglich.

Der Flüssigkeitstransfer verwendet den tatsächlichen Weltblock am unteren Ende
der lebenden, über ihre UUID zugeordneten Schlauchspitze. So funktionieren Ansaugen
und Ausgeben auch bei seitlich ausgelenkten Köpfen und gedrehten Sable-Konstruktionen.
Creates Quellblocksuche, Flüssigkeitsmengen und Simulationsregeln bleiben erhalten.
Der Schlauch hängt durch, schwingt nach und kollidiert entlang seines Verlaufs mit
Blöcken und Sable-Konstruktionen. Hindernisse verbrauchen Schlauchlänge. Blockiertes
Einziehen baut Zug auf; feste Hindernisse werden dabei nicht durchdrungen.
Selbstkollision und Verknoten sind nicht vorgesehen.

Die Umlenkung verwendet Kontaktpunkte entlang der Blockkanten und bevorzugt bei
nahezu gleich langen Wegen den bisherigen Verlauf. Kollisionskorrekturen erhalten
den vorhandenen Durchhang; die zusätzliche Kontaktlänge wird zeitabhängig geglättet.
Eine kleine Hysterese verhindert ständige Segmentwechsel an Metergrenzen.

## Lebenszyklus

Rolle und Spitze speichern eine UUID-Verbindung. Der Kopf wird direkt über diese
Identität wiedergefunden, unabhängig von Entfernung und Schiffsbewegung. Bei
entladenen Besitzer-/Kopfchunks bleibt die Verbindung bestehen. Beim Laden wartet
die Rolle zunächst auf die gespeicherte Entity. Solange der Kopf fehlt oder nicht
geladen ist, pausiert die Aufnahme und Abgabe von Weltflüssigkeit. Beim Abbau der geladenen Rolle
entfernt sich die Spitze. Tragen wird nach einem Neustart aufgehoben.

Die Physik läuft ausschließlich auf dem Server; die Clients erhalten die
Position und Schlauchlänge sowie den gemeinsamen Segmentverlauf vom Server.
Verlaufsänderungen werden höchstens zehnmal pro Sekunde übertragen und auf dem
Client auch bei wechselnder Segmentzahl entlang der Bogenlänge interpoliert.
Neue Pakete setzen am gerade dargestellten Verlauf an. Der gesamte Schlauch bleibt unabhängig von der Tracking-Reichweite
der Kopf-Entity sichtbar. Die
Darstellung verwendet Creates vorhandene Modelle, sodass Ressourcenpakete weiter
greifen. Es werden keine fremden Modell- oder Texturdateien kopiert.
Texturwiederholung und Querschnittsausrichtung werden vom Schlauchkopf zur Rolle
aufgebaut. Das Muster bleibt dadurch am Kopf verankert; zusätzlicher Schlauch
erscheint beim Ausfahren an der Rolle, ohne die Textur am Kopf zu verschieben.

## Prüfung

```bash
./gradlew build --offline
python3 tools/verify_hose_pulley.py
python3 tools/verify_hose_pulley_client.py
python3 tools/verify_hose_cable.py
python3 tools/verify_hose_cable_client.py
```

Der Entwicklungs-Testmod wird nur mit `-PhoseTests` geladen und nicht in die
Release-JAR gepackt. Die Serverprüfung verwendet eine eigene Wegwerfwelt unter
`run/smoke/hose` und schreibt ihre Ergebnisse nach `build/reports/hose`.
Die separate Clientprüfung benötigt eine OpenGL-Anzeige und verwendet ebenfalls
eine eigene Testinstanz; sie prüft die angewendeten Render-Mixins und erstellt
eine Bildschirmaufnahme der tatsächlich gerenderten Spitze.

`verify_hose_cable.py` misst zusätzlich nach dem Einschwingen jeweils 40 Verläufe
am festen und am versetzten/gedrehten Sable-Hindernis. Für den Vergleich werden
die Verläufe gleichmäßig entlang ihrer Bogenlänge abgetastet. Eine Bewegung von
mehr als 5 cm zwischen zwei Stichproben (mindestens 100 ms Abstand) lässt die
Stabilitätsprüfung fehlschlagen; Maximalwert, RMS und Segmentwechsel stehen im Bericht.

Die Serverprüfung umfasst außerdem echte Lava, gespeichertes Feuer-NBT und die
native Fluid-Schnittstelle am versetzten Kopf vor und nach einer Sable-Drehung:
Simulation ohne Weltänderung, 1.000 mB aus einer endlichen Quelle, Ausgabe am Kopf
sowie kein Weitersaugen aus einem alten Suchcache bei trockenem oder fehlendem Kopf.

## Schlauchkupplung

Creates **Item Drain** (`create:item_drain`) nimmt oben genau eine physische
Schlauchspitze auf. Der zusätzliche Block `teutonlib:hose_connector` entfällt.
Der vorhandene Drain behält seine BlockEntity, seinen 1.500-mB-Tank und seine
normale Item-/Eimer-Verarbeitung. Bei eingesetztem Kopf arbeitet er zusätzlich
als Schlauchkupplung; der vorhandene Tankinhalt bleibt dabei erhalten.
Die vier Seiten und die Unterseite sind Rohranschlüsse. Mit dem
Create-Schraubenschlüssel zuerst den Kopf aufnehmen und anschließend die
Drain rechtsklicken. Ein Schraubenschlüssel-Rechtsklick auf den belegten
Drain übernimmt den Kopf wieder ins Tragen. Ohne Kopf ist die Schlauchverbindung geschlossen; der Drain arbeitet normal weiter.

Die eingerastete Spitze verbindet die Rohre an der Schlauchrolle mit den Rohren
an der Kupplung. Eine Pumpe genügt, unabhängig davon, auf welcher Seite sie steht.
Die Verbindung arbeitet in beide Richtungen, überträgt den normalen Create-Druck
und halbiert ausschließlich den Durchfluss. Alle fünf Anschlüsse teilen sich die
verfügbare Fördermenge. Die Schlauchlänge verbraucht keine Pumpenreichweite;
Rohrabschnitte unterliegen weiterhin Creates normaler Reichweitenprüfung.
Während der Verbindung werden am Kopf keine Weltflüssigkeiten aufgenommen oder
abgegeben. Der vorhandene Rollentank bleibt erhalten.

Rolle und Kupplung können auf unterschiedlichen Sable-Konstruktionen sitzen.
Einziehen spannt den Schlauch und zieht bewegliche Konstruktionen an ihren
Befestigungspunkten zusammen; feste Weltblöcke bleiben unbewegliche Anker.
Bei anhaltender Überlast löst die Kupplung, schließt die Fluidverbindung sofort
und gibt den Kopf frei. Die Ingenieursbrille zeigt Verbindung, Durchflussfaktor
und Zugauslastung. Die Sable-Kraftanzeige führt den Schlauchzug als eigene
Kräftegruppe.

Die Welt-Serverkonfiguration ergänzt `[compat.hosePulley]` um:

```toml
springStiffness = 10000.0
damping = 2000.0
breakForce = 50000.0
breakDelaySeconds = 0.25
```

Die Werte verwenden Sables Physikeinheiten. Nur Zug wird übertragen; ein lockerer
Schlauch schiebt die Konstruktionen nicht auseinander. Zwei Befestigungen am
selben starren Körper erzeugen keine Gegenimpulse. Die Schlauchführung berücksichtigt Hindernisse und verwendet deren Umlenkpunkte
für verfügbare Länge und Zugrichtung.

Kupplung und Kopf speichern ihre UUID-Verbindung. Chunkentladung pausiert Kräfte
und Durchfluss, ohne weitere Chunks zu laden. Montage erhält die Identitäten und
passt die Speicherposition an. Nach einem Neustart muss die gegenseitige
Zuordnung bestätigt sein, bevor die Verbindung wieder arbeitet.

Es gibt kein zusätzliches Kupplungsrezept oder Kupplungsitem. Verwende Creates
normalen Item Drain. Die Erweiterung benötigt dieselbe geprüfte Modkombination
wie die physische Schlauchspitze.

Die Darstellung folgt dem bereitgestellten Modell „verriegelte darstellung“:
Der Kopf sitzt mittig mit seiner Unterkante bei 7/16 Blockhöhe im Drain. Die
Gitterdarstellung wechselt zwischen den beiden Modellgruppen. Der Kopf und der
Schlauch werden weiterhin als lebende Entity gerendert, auch auf gedrehten
Sable-Konstruktionen.

```bash
./gradlew build --offline
python3 tools/verify_hose_connector.py
python3 tools/verify_hose_pulley.py
python3 tools/verify_hose_pulley_client.py
python3 tools/verify_hose_cable.py
python3 tools/verify_hose_cable_client.py
python3 tools/verify_logistics.py pipes
```

Die Kupplungsprüfung verwendet echte Create-Pumpen und Tanks: beide Pumpenseiten,
Gegenrichtung, gemeinsame Drosselung über alle fünf Ausgänge, Mengenerhaltung,
erhaltener Rollentank, Lösen und Stillstand ohne Pumpe. Dazu kommen gegenseitige
UUIDs, NBT, Sable-Montage, Zugkräfte, Bewegung/Drehung und Überlastlösen. Die
Clientprüfung erfasst zusätzlich den eingerasteten Kopf mit dem echten Blockmodell.
Ergebnisse liegen unter `build/reports/hose-connector` und
`build/reports/hose-pulley-client`; Testmods werden nicht ausgeliefert.
