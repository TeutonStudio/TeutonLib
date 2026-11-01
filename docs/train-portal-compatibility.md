# Sable-Züge durch Immersive-Portals-Portale

TeutonLib **1.5.8** ergänzt die beidseitige Darstellung baubarer Create-Züge an
Portalflächen. Der vorhandene Sable-Aufbau wird durch IPs natives Straddling-System
auf beiden Seiten geschnitten gezeichnet. Die koordinierten Übertragungen und die
Korrektur des Null-Kontexts beim Entkoppeln aus 1.5.7 bleiben erhalten.
**1.5.9** ergänzt die Bindung stehender Mitfahrer an die neu geladene Wageninstanz.

Die Screenshots vom 31.10.2026 zeigen zusätzlich falsche Beleuchtung und quer
durch die Welt verlaufende Wagenverbindungen. Die dazugehörige Prism-Instanz
verwendete laut Clientlog **1.5.10 mit Sodium 0.8.12-beta.2**. Die folgenden
Darstellungskorrekturen ergänzen deshalb die beidseitigen Wagenansichten.

## Beleuchtung und Wagenverbindungen

An der Portalfläche benötigt jede sichtbare Wagenhälfte die Beleuchtung ihrer
jeweiligen Welt. Insbesondere darf eine aus dem Nether in die Oberwelt projizierte
Hälfte nicht den fehlenden Nether-Himmel übernehmen. Die lokale Abschattung des
Aufbaus und seine eigenen Lichtquellen müssen dabei erhalten bleiben.

Für die kurzzeitig projizierten Blockmeshes werden die Original-Vertexdaten
clientseitig gesichert und die Lichtwerte aus der sichtbaren Welt berechnet.
Nether-Aufbauten erhalten dafür eine eigene clientseitige Berechnung ihrer
lokalen Himmelsabschattung. Bewegliche Create-Teile verwenden im jeweiligen
Flywheel-Renderpass ebenfalls diese Weltbeleuchtung. Temporäre Änderungen an
Lichtdaten und Instanzen werden nach dem Pass zurückgesetzt. Die Korrektur
verändert keine gespeicherten Blöcke oder Server-Lichtdaten.

Viele normale Create-Motoren und Zahnräder fordern selbst keine Shader-Lichtsections
an. Für aktive Portalansichten werden deshalb die betroffenen Plotsections über
Sables eigenen Lichtallocator ergänzt und anschließend wieder freigegeben. Die
normalen Lichtsections der Welt bleiben erhalten. Die Vanilla-Kompilierung sichert
außerdem die zuvor eingestellte Compilerwelt und stellt sie danach wieder her;
IPs Quellwelt darf nicht in den nächsten Terrain-Renderdurchlauf hineinwirken.

Die Sicherung benötigt zusätzlichen CPU-Speicher für die Sable-Blockmeshes.
Portalansichten verwenden begrenzte Caches; beim Beenden einer Ansicht oder
Freigeben eines Renderpuffers werden ihre temporären Daten zurückgesetzt
beziehungsweise freigegeben. Es findet kein Auslesen von GPU-Puffern statt.

Auch die Kupplungsanker gehören zur jeweiligen Ansicht. Ein Anker vor dem Portal
und ein Anker an den entfernten Zielkoordinaten dürfen nicht direkt miteinander
verbunden werden. Die Darstellung ordnet beide Enden derselben Portalansicht zu
und schneidet die Verbindung an der Portalfläche. Die tatsächliche Zugkopplung
und Gleisfahrt bleiben bei Create.

Die Clientprüfung lässt sich mit dem normalen Renderer oder mit dem in der
Prism-Instanz verwendeten Sodium ausführen:

```bash
python3 tools/verify_train_portal_client.py --verify-flywheel --flywheel-backend=instancing
python3 tools/verify_train_portal_client.py --sodium --render-only --verify-flywheel
```

Beide Läufe verwenden eigene Testwelten und Clients. Sie erfassen echte
Renderaufrufe und Bildschirmaufnahmen auf beiden Portalseiten. Der vollständige
Aufruf ohne `--render-only` prüft zusätzlich die bestehenden Mitfahrerfälle.
`--verify-flywheel` verlangt tatsächlich gezeichnete Motoren und Zahnräder mit
passenden Lichtwerten; ein leerer Instanzpfad besteht diese Prüfung nicht.

## Unterstützte Kombination

| Bestandteil | Geprüfte Version |
| --- | --- |
| Minecraft / NeoForge | 1.21.1 / 21.1.234 |
| Create | 6.0.10 |
| Flywheel | 1.0.6 |
| Create: Train Functionality | 0.1.47 |
| Immersive Portals: Aeronautics | 2.6.4 |
| Sable | 2.0.6 |
| Sodium, optional | 0.8.12-beta.2+mc1.21.1 |
| Steam ’n’ Rails, optional | 0.2.0+neoforge-mc1.21.1 |

Sable bleibt eine optionale Abhängigkeit. Falls Sable installiert ist, verlangen
die Modmetadaten mindestens **2.0.6**. Die private Portal-Integration und das
CSO-Modul sind gezielt für **2.0.6** freigeschaltet; eine spätere Sable-Version
wird dadurch nicht automatisch als geprüft behandelt. Mit 2.0.6 verwendet IP
sein natives Straddling-Backend. Die Brücke ist auf Server und Clients zu
installieren. Fremde JARs werden nicht eingebettet.

## Aether-Portale: Prüfstand

Die Aether-Prüfung verwendet die unveränderten Veröffentlichungs-JARs von
**Aether 1.21.1-1.5.10** und **owo-lib 0.12.15.5-beta.1+1.21** zusätzlich zur oben
genannten Zug-/Portal-Kombination. Beide Dateien liegen ausschließlich im optionalen
Prüfprofil unter `dependencies/verification/`. Der Runner prüft vor dem Start ihre
SHA-256-Werte und übernimmt sie in den Bericht:

| Datei | SHA-256 |
| --- | --- |
| `aether-1.21.1-1.5.10-neoforge.jar` | `a999a9265eb550a46a0f8eedfee7c3c75371d7f6cf34b7c09ff800e48633e9f8` |
| `owo-lib-neoforge-0.12.15.5-beta.1+1.21.jar` | `de6ed336bd80154b7241a7b3276694befc1c94550add8bcdfe7f82e5172fd13d` |

Verwendet wird Aethers eingebautes Paket `builtin/aether_imm_ptl_compat` mit der
effektiv geladenen Definition
`aether:custom_portal_generation/water_bucket_create_aether_portal_to.json`.
Ihr SHA-256-Wert lautet
`524d55924256dd983d6d2252558312ac7d8a9ce6c401a4f0d36cae54e1f717ad`.
Die Prüfung liest sowohl die Ressource als auch ihren tatsächlich registrierten
IP-Generierungseintrag. Sie errichtet Glowstone-Rahmen in Oberwelt und Aether und
benutzt einen Wassereimer über den echten Item-Hook. IP erzeugt daraus den nativen
Portalcluster mit dem Tag `aether:water_bucket_create_aether_portal_to`.

Der Nachweis verlangt genau vier intakte `GeneralBreakablePortal`-Entities,
gegenseitige Hin-/Rückportale, Maßstab 1, die richtigen Zielwelten und Glowstone-
Rahmen mit jeweils 144 nativen Placeholder-Blöcken. Creates registrierter
Portalgleis-Provider muss beide Richtungen erkennen; die anschließende native
IP/Create-Verbindung muss auf beiden Gleis-BlockEntities eine gegenseitige
`boundLocation` und IPs Portalgleis-Markierung hinterlassen. Derselbe native
Vierercluster bleibt für die Grafik- und Mitfahrerphasen erhalten.

Der Serverrunner wartet auf tatsächlich entity-tickende Wagen- und Portalchunks.
Nach den synthetischen Transferprüfungen lädt er die ursprünglichen Rahmenchunks
bei Bedarf erneut und löst die gespeicherten Portal-UUIDs auf ihre aktuellen
Entity-Instanzen auf. Dabei müssen dieselben vier UUIDs, Rahmen und Gleisbindungen
erhalten bleiben; Portale werden während der Durchfahrtsmatrix nicht neu erzeugt.

Aether hat laut geladener Dimensionsdefinition Himmellicht, `minY=0`, Höhe 256,
Ambientlicht 0 und keine feste Tageszeit. Die Snapshots erfassen diese Eigenschaften
für alle geladenen Welten. Die Lichtprüfung verwendet die tatsächlichen
Weltproben und angewendeten Mesh-/Flywheel-Lichtwerte. Aethers optionale kühlere
Lightmap kann die sichtbaren Farben gegenüber der Oberwelt verändern.

Im NeoForge-Development-Minecraft sind zwei synthetische Renderer-Lambdas anders
nummeriert als im ursprünglichen Minecraft. Ein ausschließlich im
`trainPortalTest`-SourceSet registrierter Accessories-Adapter ersetzt deshalb die
beiden ursprünglichen Methodenselektoren durch die passenden Development-Namen.
Vorher prüft ASM die exakten Deskriptoren und Provider-Aufrufe. Accessories'
ursprüngliche Handler und Injection-Punkte bleiben erhalten. Der Adapter ist nur
bei der ausdrücklich aktivierten Clientprüfung und außerhalb einer
Produktionsumgebung aktiv; er lädt serverseitig keine Client-Zielklasse.
Aether, owo und dieser Testadapter sind keine Produktionsabhängigkeit und werden
nicht in die TeutonLib-Release-JAR eingebettet.

Die Aether-Variante ist über `--aether` ausdrücklich wählbar:

```bash
python3 tools/verify_train_portals.py --aether
python3 tools/verify_train_portals.py --aether --packaged
python3 tools/verify_train_portal_client.py --aether --sodium --render-only --verify-flywheel --flywheel-backend=indirect
python3 tools/verify_train_portal_client.py --aether --sodium --verify-flywheel --flywheel-backend=indirect
python3 tools/verify_train_portal_client.py --aether --verify-flywheel --flywheel-backend=instancing
```

**Prüfstand vom 31.10.2026:** Der Lauf
`build/reports/train-portal-client/20261031-180157-d78a4d87/report.json` hat mit
Sodium und Flywheel Indirect alle sieben Grafikstufen einschließlich angewendeter
Beleuchtung, Kupplungssegmente und nativer Aether-Portalnachweise bestanden.
Die Blocklicht-Assertions verlangen inzwischen auch in Welten mit Himmellicht
positive tatsächlich angewendete Mesh- und Flywheel-Blocklichtwerte. Der gespeicherte
Sieben-Stufen-Bericht besteht auch diese verschärfte erneute Prüfung.
Der vollständige Bericht steht trotzdem auf **`passed: false`**: Der anschließende
Test mit echtem stehendem Mitfahrer scheitert nach der Hinfahrt in der Ruhephase
an einer Positionsabweichung von etwa **2,024 Blöcken** bei erlaubten 2 Blöcken.
Ein vollständiger Hin-/Rückfahrtnachweis dieses Mitfahrers liegt damit noch nicht vor.
Der diagnostische Sodium-/Indirect-Lauf
`build/reports/train-portal-client/20261031-181132-f58d84d0/report.json` reproduziert
den Fehler ebenfalls in der Pause nach der Hinfahrt mit etwa **2,169 Blöcken**
Abweichung. Es handelt sich um den unten abgegrenzten bekannten Fehler bei der
Übergabe eines bereits früh durchgetretenen stehenden Spielers. Für diesen Fehler
wurde in diesem Prüfauftrag keine Produktionskorrektur vorgenommen.

### Bekannter Fehler: stehender Spieler bei der Körperübergabe

Die begrenzte Tickdiagnose dieses Laufs liegt unter
`run/smoke/train-portal-client/20261031-181132-f58d84d0/client/train-portal-verification/riders-client.jsonl`
und
`run/smoke/train-portal-client/20261031-181132-f58d84d0/server/train-portal-verification/riders-server.jsonl`.
Sie wird ausschließlich mit `--rider-diagnostics` beziehungsweise
`-Dteutonlib.trainPortalRiderDiagnostics=true` aktiviert. Sie beobachtet die
Bewegung und verändert weder Spielerpositionen noch Erfolgskriterien.

Der Spieler behält während der frühen Portalpassage seinen X-Abstand zur
sichtbaren Wagenpose bei: zunächst etwa 8,072082 und auf der entfernten Projektion
etwa 8,072052 Blöcke. Auch beim Wechsel der nativen Portalflächen und ihren
auslaufenden Sitzungen bleibt dieser Abstand erhalten. Der Verlust entsteht erst
bei der tatsächlichen Übergabe des Wagenkörpers:

| Beobachtung im Diagnoselauf | Ergebnis |
| --- | --- |
| Clienttick 174, unmittelbar vor der Übergabe | Spieler-X 167,255167; projizierte Wagenpose-X 159,183115; Abstand 8,072052 |
| Servertick 643, `capture` | Spieler bereits im Aether bei X 166,859395; die aktuelle projizierte Serverpose liegt bei X 160,737288. Die daraus berechnete Plotposition liegt um etwa 1,949975 Blöcke gegenüber der zuvor begangenen Position versetzt. |
| Derselbe Servertick, `restore-before`/`restore-after` | Der neu gebundene Wagen liegt bei Pose-X 160,324789. Die Wiederherstellung setzt den Spieler auf X 166,446897, also um etwa 0,412498 Blöcke zurück. |
| Clienttick 176, neuer lokaler Wagenkörper | Spieler-X 167,052955; Wagenpose-X 161,149788; Abstand 5,903166. Dieser veränderte Abstand bleibt anschließend erhalten. |
| Ruhephase nach der Hinfahrt | Richtiger aktueller Träger und stabile Höhe 104, aber etwa 2,169 Blöcke Abweichung von der ursprünglichen relativen Standposition; Prüfung fehlgeschlagen. |

Damit ist ein Wechsel zwischen der interpolierten, tatsächlich begangenen
Clientprojektion und der aktuellen Serverpose bei der Erfassung/Wiederherstellung
belegt. Die bereits korrekt funktionierende Projektionsmitnahme lässt sich dafür
nicht als Ursache angeben. Die geprüften Aether-1.5.10-Hooks liefern ebenfalls
keinen passenden horizontalen Bewegungseingriff für einen unmontierten Spieler
auf dieser trockenen Decke bei Höhe 104.

Eine künftige Korrektur muss die tatsächlich begangene Plot-Fußposition beim
Wechsel zur neu geladenen Instanz derselben Wagen-UUID erhalten und dabei
Interpolation, Paketabfolge und eine mögliche Plotverschiebung berücksichtigen.
Sie muss auch Gehen sowie Abspringen während der Übergabe sinnvoll behandeln.
Eine größere Abstandstoleranz, ein zusätzlicher Fixture-Teleport oder ein bloßer
Wechsel der ausgewerteten Carry-Sitzungen wäre kein Nachweis einer Behebung.
Der vollständige Aether-Standing-Hin-/Rückfahrttest bleibt daher ausdrücklich
**fehlgeschlagen**; die nachfolgenden positiven Ergebnisse sind eigenständige
Teilprüfungen.

Der separate Vanilla-/Instancing-Lauf mit `--seated-only`
`build/reports/train-portal-client/20261031-180847-7d517b93/report.json` steht dagegen
auf **`passed: true`**. Er enthält eine Baseline-Aufnahme und die Hin-/Rückfahrt
eines echten sitzenden Spielers mit genau zwei Dimensionswechseln sowie jeweils
20 stabilen Ticks am aktuellen Sitz nach den Durchfahrten.

Auch der vollständige Vanilla-/Instancing-Grafiklauf
`build/reports/train-portal-client/20261031-181604-e6e43a11/report.json` hat alle
sieben Grafikstufen bestanden und steht auf **`passed: true`**.
Dasselbe gilt für Sodium mit Flywheel Instancing im Lauf
`build/reports/train-portal-client/20261031-182625-2477bb65/report.json`.
Damit bestehen insgesamt 21 Grafikstufen in drei Renderer-/Backend-Kombinationen.
Der Sodium-/Instancing-Lauf
`build/reports/train-portal-client/20261031-181837-a1844a53/report.json` bestätigt
den echten Sitz-Rollback ebenfalls mit **`passed: true`**: eine Baseline-Aufnahme,
ein erzwungener Bindungsfehler am Ziel und die Wiederherstellung des Spielers am
ursprünglichen Sitz.

Die Servermatrix besteht sowohl mit den Development-Klassen als auch mit der
unveränderten Release-JAR **1.5.12**, jeweils einschließlich Neustart:

- `build/reports/train-portals/sources-2.0.6-aether.json`
- `build/reports/train-portals/packaged-2.0.6-aether.json`

Beide Berichte enthalten acht bestandene Prüfgruppen: echtes Entkoppeln, Guard-
und Transferverträge, synthetische Mitfahrer einschließlich Rotation und Rollback,
Hin-/Rückfahrt mit gemischten Wagen, Durchfahrt innerhalb derselben Dimension,
zwei baubare Wagen, Speichern im Aether und Wiederherstellen nach Neustart.
Die echten dimensionsübergreifenden Zugfahrten verwenden den zuvor erzeugten
nativen Aether-Vierercluster. Die isolierten Rotations-/Support-Vertragstests
verwenden zusätzlich generische IP-Portale in derselben beziehungsweise der
Aether-Zielwelt.

| Aether-Prüfung | Ergebnis |
| --- | --- |
| Sodium / Indirect, sieben Grafikstufen | bestanden |
| Echter stehender Mitfahrer | fehlgeschlagen, Abweichung in der Ruhephase nach der Hinfahrt |
| Vanilla / Instancing, Baseline und echter sitzender Mitfahrer | bestanden, Hin-/Rückfahrt mit zwei Dimensionswechseln und stabilen Sitzphasen |
| Sodium / Instancing, Baseline und erzwungener Sitz-Rollback | bestanden, Sitz nach fehlgeschlagener Bindung wiederhergestellt |
| Vanilla / Instancing, vollständige sieben Grafikstufen | bestanden |
| Sodium / Instancing, vollständige sieben Grafikstufen | bestanden |
| Servermatrix einschließlich Neustart und gepackter JAR | bestanden, je acht Prüfgruppen |

Die separat dokumentierten Nether-Ergebnisse bleiben eigenständige Nachweise.
Die vollständige Aether-Mitfahrerprüfung besteht wegen des Standing-Fehlers noch
nicht. Die positiven Grafik-, Sitz- und Servernachweise beheben diesen Fehler nicht.
Dieser Prüfauftrag ändert keinen Produktionscode und erzeugt keine neue Releaseversion.

## Behobener Null-Kontext-Fehler

Railways ruft beim echten Entkoppeln `Carriage.travel(null, ...)` auf, unter anderem
für die kleine Verschiebung zum Neuermitteln belegter Signalabschnitte. In 1.5.6
waren sowohl der eingefügte Kotlin-Handler als auch `TrainPortalGuard.portalListener`
fälschlich mit `Level` anstelle von `Level?` deklariert. Kotlin warf deshalb bereits
beim Einsetzen des Listeners eine NullPointerException, auch ohne Portal.

Beide Parameter sind nun nullable. Ohne Weltkontext wird **derselbe ursprüngliche
Listener** unverändert zurückgegeben. Dafür werden weder Zugbesitz noch Portal-
oder Weltzustand abgefragt. Geprüft werden die JVM-Grenzen und Railways' tatsächliche
`TrainUtils.splitTrain`-Implementierung mit zwei geladenen Wagen. Der erweiterte
Entkopplungstest verwendet zwei echte CTF-Aufbauten mit eigener Fracht und prüft
auch die gespeicherten Zugkennungen nach dem Aufteilen.

## Ablauf der Portal-Durchfahrt

Create und IP bleiben für Gleisfahrt, Wagenseiten und Wagen-Entities zuständig.
TeutonLib übernimmt die vorhandenen CTF-Aufbauten, statt sie aus Creates möglicherweise
veralteter Block-/Inventarkopie neu zu erzeugen.

1. Am Portalgleis wird eine echte passende IP-Portalfläche geprüft: teleportierbar,
   Maßstab 1, passende Zielwelt und passende Gleis-Endpunkte. Ein geladener aktiver
   Aufbau muss vorliegen; die IP-Zielprüfung erfolgt vor dem Eintritt des Rads.
2. Während der Durchfahrt bleibt genau ein Sable-Aufbau pro baubarem Wagen maßgeblich.
   Zusätzliche IP-Wagenansichten werden nicht durch CTF erneut konvertiert und
   erhalten keinen zweiten Kollisionskörper. Die Prüfung ist pro Wagen; ein normaler
   Wagen im selben Zug wird nicht mit dem baubaren Nachbarwagen verwechselt.
3. IPs eigene Straddling-Sitzungen übernehmen Clipping, Darstellung und
   Image-Collider. Ihre Lebensdauer folgt der geometrischen Berührung des
   Aufbaus mit der Portalöffnung. Die unabhängigen Schiffsscanner dürfen den
   Aufbau nicht gleichzeitig verschieben oder aus dem Portal zurückstoßen.
4. Beim Seitenwechsel lädt IP das vorhandene Sublevel am Ziel. Innerhalb von IPs
   Lade-Transaktion wird es an die Ziel-Wagen-Entity gebunden. Erst danach schließt
   IP seine Übertragung einschließlich Maschinen, Mitfahrern und Transferjournal ab.
   Sublevel-UUID und echte Inventare bleiben erhalten; Plotursprung, Dimension,
   Wagen-UUID und gespeicherte CTF-Zuordnung werden aktualisiert.
5. Die Entfernung des Quellkörpers wird in dieser eng begrenzten Transaktion als
   Übertragung behandelt. Sie darf CTFs Lösch-/Zugbereinigung nicht auslösen.
   Die Quellzuordnung wird nach erfolgreicher Übernahme entfernt.
6. Scheitert die Bindung am Ziel, entfernt die Brücke die unvollständige Zielinstanz.
   IP stellt seine originale Quell-Sicherung wieder her und CTF wird daran gebunden.
   Eine frisch geladene Zielinstanz wird für diesen Rollback nicht erneut serialisiert.

Beim Neuladen eines Plots wird außerdem der Minecraft-Chunk-Cache vor und nach dem
Laden invalidiert. Der erzwungene Rollback-Test hat nachgewiesen, dass andernfalls
beim Wiederverwenden derselben Plotkoordinaten ein entfernter Chunk weiter gelesen
werden kann und das Inventar über einen veralteten BlockEntity-Verweis leer erscheint.

Clientseitig werden zusätzliche Ansichten desselben Wagens als ersetzt behandelt,
ohne ihnen einen zweiten Eintrag in CTFs Plotbesitz-Tabelle zu geben. Dadurch erhält
ein Plot weiterhin nur einen maßgeblichen Zeichnungs-/Pose-Eigentümer.

## Beidseitige Darstellung und Seitenwechsel

Die Erkennung verwendet die tatsächliche Sable-Aufbauhülle. Ragt etwa die Spitze
eines langen Wagens bereits durch das Portal, beginnt die Darstellung vor dem
Eintritt des ersten Rads. Auf der Quellseite wird der verbleibende Teil gezeichnet;
auf der Zielseite erscheint der durch die Portaltransformation abgebildete Teil.
Beide Ansichten verwenden den vorhandenen Aufbau einschließlich späterer Änderungen.

Die Sitzung bleibt bestehen, solange die Aufbauhülle die endliche Portalöffnung
berührt. Das gilt auch für einen angehaltenen oder rückwärts fahrenden Zug und für
einen langen Aufbau, dessen letzte Quell-Wagenansicht bereits entfernt wurde.
Eine ursprüngliche Create-Blockkopie oder eine zweite CTF-Instanz wird dafür nicht
angelegt.

Vor der tatsächlichen Übergabe markiert die Brücke die native Sitzung mit
`markHandedOff` und sendet die letzte Pose erneut. Damit verwendet sie IPs vorhandene
clientseitige Mesh-Übergabe. Die Sitzung wird anschließend wie im nativen IP-Transfer
geschlossen. IPs `HandoffLatch` überspringt die Mesh-Übergabe ausdrücklich, wenn
Quelle und Ziel in derselben Dimension liegen. Eine lückenlose Darstellung in
jedem einzelnen Übergangsframe ist deshalb nicht für alle Portalarten zugesichert.

Sieht ein Spieler nur die entfernte Hälfte des Aufbaus, braucht sein Client trotzdem
die Blöcke des ursprünglichen Sublevels. `TrainPortalLoadingMixin` ergänzt dafür
vorübergehend dessen native Sable-Ladeabhängigkeiten. Dies geschieht nur bei einer
aktiven Portalberührung mit einem tatsächlich vorhandenen Beobachter der entfernten
Hälfte. Es werden keine dauerhaften Sable-Ladetickets angelegt. Beim Verlassen des
Portals, bei der Übergabe, beim Entladen oder beim Zurücksetzen der Integration wird
die Ergänzung entfernt.

Ein Dimensionswechsel verwirft clientseitig die bisherigen Welten und Render-Sitzungen.
`TrainPortalViewerSyncMixin` verwendet IPs vorhandenen Spieler-Durchlauf, um diesen
Wechsel zu erkennen und Sitzung sowie Plot-Tracking einmal erneut anzukündigen.
Bereits lokal getrackte, gerade das Portal berührende Zugaufbauten erhalten dabei
einmalig einen nativen vollständigen Sable-Datenstand. Normale Ticks behalten IPs
bestehende Synchronisierung geänderter Daten bei.

## Stehende Mitfahrer ab 1.5.9

IPs Transfer lädt denselben Wagen unter seiner UUID als neue Sublevel-Instanz.
Die bisherige Steh-Verknüpfung kann dabei auf die entfernte Instanz zeigen.
Die Brücke erfasst vor der Übergabe stehende Mitfahrer mit tatsächlichem
Bodenkontakt zu diesem Wagen und bindet sie nach dem Laden an dessen neue Instanz.
Position im Plot, Blickrichtung und Geschwindigkeit folgen der vollständigen
Portaltransformation einschließlich Drehung und Plotverschiebung.

Ist ein Spieler vor dem Wagenmittelpunkt bereits durch das Portal gegangen,
wird sein Bodenkontakt auf der projizierten Hälfte desselben Wagens geprüft.
Die Prüfung verwendet dessen konkrete Kollisionsfläche, keine gemeinsame
Hüllbox mehrerer Wagen. Fremde Wagen, bloß nahe Entities und sitzende Passagiere
werden nicht als stehende Mitfahrer übernommen. Die clientseitige Mitnahme folgt
der Bewegung dieses projizierten Bodens in Sables bestehender Kollisionsbewegung.
Nach der Wagenübergabe ruft die Brücke außerdem IPs native Sitzübergabe auf;
früh durchgetretene Sitzproxies werden dadurch wieder an den echten Sitz gebunden.

Die allgemeine IP-Hüllbox-Mitnahme darf unmontierte Spieler bei Zugübertragungen
nicht zusätzlich erfassen. Sonst kann ein überlappender Nachbarwagen einen Spieler
auf dem richtigen Wagen mitnehmen, bevor dieser überhaupt am Portal angekommen
ist. Stehende Spieler werden deshalb ausschließlich über die genaue Wagenfläche
zugeordnet. Befindet sich ihr eigener Standpunkt beim Wagenwechsel noch auf der
Quellseite, bleiben sie auf dieser Hälfte, bis sie selbst die Portalfläche kreuzen.
Bei einem entsprechend zurückliegenden Sitz verwendet die Brücke IPs native
Sitzproxy-Verwaltung für die zurückbleibende Hälfte des bereits übertragenen
Wagens. Dadurch läuft der Sitzwechsel mit dem Sitz statt mit dem Wagenmittelpunkt.

Scheitert die Übergabe, erhalten lokale Mitfahrer die wiederhergestellte
Quellinstanz als Träger. Bereits auf der entfernten Projektion stehende Mitfahrer
werden durch den fehlgeschlagenen Transfer nicht zurück zur Quelle versetzt.

## Aufwand und Leistungsgrenzen

Die Überlappungsprüfung verwendet eine feste Anzahl von Trennachsen zwischen der
gedrehten Aufbauhülle und der endlichen Portalfläche. Ihr Rechenaufwand wächst nicht
mit der Blockzahl des Wagens; Plotblöcke werden dafür nicht gelesen.

Die Portalsuche bleibt lokal und wird pro Aufbau fünf Ticks zwischengespeichert.
Verlässt die Aufbauhülle den gespeicherten Suchbereich früher, löst die Bewegung
sofort eine neue Suche aus. Eine bereits berührte Portalfläche wird direkt geprüft.
Die Brücke hängt diese Arbeit an IPs vorhandenen Straddling-Durchlauf und verwendet
IPs vorhandene Meshes und Synchronisierung.

Die zusätzlichen Ladeabhängigkeiten werden pro Welt und Servertick einmal ermittelt
und zwischengespeichert. Änderungen an der Lebensdauer einer Ansicht verwerfen den
betroffenen Cache sofort. Ohne entfernten Beobachter entsteht daraus keine zusätzliche
Plot-Ladeanforderung; ein Dimensionswechsel löst keine dauernd wiederholte vollständige
Synchronisierung aus.

Eine zusätzlich sichtbare Portalansicht benötigt zusätzliche Draw-Aufrufe und
GPU-Arbeit. Deren Kosten hängen unter anderem von sichtbaren Wagen, Portalansichten,
Aufbauschichten und Renderer ab. Die Änderungen vermeiden zusätzliche Blockscans
und doppelte CTF-Aufbauten; eine pauschale Garantie unveränderter FPS lässt sich
daraus nicht ableiten.

## Grenzen

* Unterstützt werden passende IP-Portalgleise mit Maßstab 1. Fehlende/entfernte
  Portale, Spiegel, skalierte Portale und ungeladene Aufbauten werden nicht blind
  übertragen. Die ursprüngliche Sperre für nicht unterstützte Vanilla-Portalgleise
  bleibt sinngemäß erhalten.
* Eigenständige Schiffstransfers eines Zugaufbaus, etwa unabhängig vom Wagen mit
  einem Physikstab, bleiben gesperrt. Gespeicherte/verwaiste Zugteile sind geschützt.
* Dieser Fix rekonstruiert keine zuvor verlorenen Blöcke und repariert keine bereits
  beschädigte Welt. Ein beliebiger Prozessabbruch innerhalb fremder Speicherabläufe
  oder jede Kombination weiterer Mods ist damit nicht pauschal zertifiziert.
* Die genauen ursprünglichen Drops und der später gemeldete Vorfall um 19:48:50
  sind ohne das ursprüngliche Serverlog nicht lückenlos zugeordnet. Der Null-Fehler
  in 1.5.6 und das Verhalten des neuen Rollbacks sind separat reproduzierbar.

Der Adapter wird nur für die genannten Versionen aktiviert. Abweichende Versionen
werden protokolliert und nicht ungeprüft gepatcht. Passende Versionsnummern mit
verändertem Zielbytecode führen zu einer Fingerprint-Fehlermeldung beim Start.
CTFs ursprünglicher Portal-Listener wird nur bei aktiver, geprüfter Brücke ersetzt.
Andere Portal-Listener werden weiterhin aufgerufen. Ohne die optionalen Mods werden
die Integrationsklassen nicht geladen.

## Reproduzierbare Prüfung

Prüfstand **1.5.12 vom 31.10.2026**: Alle **149 JUnit-Tests** bestehen.
Die aktuellen Grafikprüfungen enthalten echte Create-Motoren und Zahnräder,
Beleuchtung durch Glowstone in beiden Welten, den nativen Client-Zuggraphen und
tatsächlich gezeichnete Kupplungssegmente.

| Renderer / Flywheel | Grafikzustände | Mitfahrer und Rollback | Bericht unter `build/reports/train-portal-client/` |
| --- | --- | --- | --- |
| Sodium / Indirect | 7 bestanden | separater Lauf | `20261031-172108-3bdf45be/report.json` |
| Sodium / Instancing | 7 bestanden | alle 3 Fälle bestanden | `20261031-172535-a73840d7/report.json` |
| Vanilla / Instancing | 7 bestanden | alle 3 Fälle bestanden | `20261031-173309-78f003b6/report.json` |

Gemessen werden die tatsächlich angewendeten Mesh- und Flywheel-Lichtwerte:
Oberwelt mit Himmellicht, Nether ohne Himmellicht mit vorhandenem Blocklicht,
einschließlich Projektion eines ursprünglichen Nether-Plots in die Oberwelt.
Die Kupplungsaufnahmen verlangen kurze Segmente mit einem Ende an der Portalfläche
auf beiden Seiten. Die Bilder wurden zusätzlich gesichtet. Die Geometrietests
prüfen auch gedrehte Portalansichten, endliche Öffnungen und verschiedene
Clipplanes. Ein eigener grafischer Lauf für nahe gleichdimensionale Portale
liegt nicht vor; deren Sonderpfad verwendet IPs geprüfte Railgraph-Erkennung.

Die medianen Framezeiten des Sodium-/Indirect-Laufs lagen bei etwa 8,3–8,9 ms,
die 95. Perzentile bei 10,4–20,4 ms. Der Client ist auf 120 FPS begrenzt; diese
kleine Szene ist kein unlimitierter Leistungsbenchmark für große Zugaufbauten.

Historischer Prüfstand **1.5.9 vom 31.10.2026**: Alle **122 JUnit-Tests** bestehen.
Die grafische Prüfung verwendet jetzt zwei baubare Wagen mit getrennten Farben
und Dachhöhen und erfasst alle sieben Phasen. Jeder Wagen wird einzeln auf
vorhandene Quelldaten, Clipping und tatsächliche Draw-Aufrufe geprüft. Beide
Wagen bleiben vollständige, unabhängige CTF-Aufbauten.

Danach fährt ein echter verbundener Spieler ohne Sitz und anschließend auf einem
Create-Sitz hin und zurück. Beide Läufe verlangen genau zwei Dimensionswechsel
und nach jeder Durchfahrt zwanzig stabile Ticks am aktuellen Wagenkörper bzw.
einem echten Sitz im aktuellen Plot. Während der Fahrt berücksichtigt die
Positionsprüfung die clientseitige Pose-Interpolation; nach der Beruhigungszeit
wird enger geprüft. Ein dritter Lauf erzwingt mit sitzendem Spieler eine
fehlgeschlagene Bindung und prüft die Wiederherstellung von Quelldimension und
Sitz. Bericht: `build/reports/train-portal-client/20261031-150037-ce93e941/report.json`.

Die Serverprüfung enthält zusätzlich vier direkte Übergaben (zwischen und
innerhalb von Dimensionen, jeweils mit 0° und 90° Drehung), erhaltene Blickrichtung
und Geschwindigkeit, neue Trägerinstanzen nach Rollback und den Ausschluss eines
nahen Mitfahrers mit veralteter Trägerreferenz. Die vorhandenen Fahr-/Fracht-/
Neustartprüfungen bleiben Bestandteil von `tools/verify_train_portals.py`.
Das gepackte 1.5.9-Artefakt bestand alle acht Prüfungen einschließlich Neustart
(`build/reports/train-portals/packaged-2.0.6.json`).

Zusätzlich zu den normalen lokalen Compile-/Runtime-Abhängigkeiten sind nötig:

* `dependencies/compile/create_train_functionality-0.1.47.jar`
* `dependencies/compile/ip_aeronautics-2.6.4.jar`
* `dependencies/compile/cloth-config-15.0.140-neoforge.jar`
* `dependencies/compile/railways-0.2.0+neoforge-mc1.21.1.jar`
* `dependencies/verification/sable-neoforge-1.21.1-2.0.6.jar`

```bash
./gradlew build compileTrainPortalTestKotlin --offline
python3 tools/verify_train_portals.py
python3 tools/verify_train_portals.py --packaged
python3 tools/verify_server.py none
python3 tools/verify_train_portal_client.py
```

Jeder Lauf erstellt eine neue Testwelt unter `run/smoke/train-portals/`. Der zweite
Serverstart verwendet ausdrücklich dieselbe Testwelt, um die gespeicherte Zuordnung
zu prüfen. Die grafische Prüfung benötigt einen verfügbaren OpenGL-Bildschirm und
startet einen eigenen Server sowie einen eigenen Client. Persönliche Minecraft-
Instanzen und Welten werden nicht benutzt. Serverberichte und Logs liegen unter
`build/reports/train-portals/`, Clientberichte und Screenshots unter
`build/reports/train-portal-client/`.

Die Prüfungen umfassen Versions-/Bytecodeverträge, Null-Kontext, tatsächliches
Entkoppeln, unveränderte normale Listener, Schutz vor unabhängigen Schiffstransfers,
gewöhnliche Schiffe mit Fracht als Positivkontrolle und echte Create-Wagenfahrten
über echte IP-Portalflächen/Gleisgraphen über mehrere Server-/Physikticks. Geprüft
werden Hin- und Rückfahrt zwischen und innerhalb von Dimensionen, gemischte Züge,
mehrere baubare Wagen, genau ein Aufbau pro Wagen, erhaltene Inventare (17 bzw.
19 Diamanten), Plotverschiebungen, ein erzwungener Bindungsfehler mit Rollback und
die Live-Daten für eine spätere Demontage. Nach dem Neustart werden Zielwelt,
Wagenzuordnung, genau ein gespeicherter Besitzer und die Lagerbrücke erneut geprüft.

Für den aktuellen Stand von 1.5.8 bestanden alle **119 JUnit-Tests**. Dazu gehören
Geometrie-, Versions-, Metadaten- und Kompatibilitätsprüfungen. Die Metadatenprüfung
liest die gebaute JAR und bestätigt, dass Sable optional bleibt und `[2.0.6,)`
verlangt wird.

Das gepackte 1.5.8-Artefakt mit Sable 2.0.6 bestand die Serverprüfungen einschließlich
Neustart (`build/reports/train-portals/packaged-2.0.6.json`). Auch der Server ohne
optionale Mods bestand seine Smoke-Prüfung (`build/reports/smoke/server-none.json`).

Die grafische Prüfung am 27.10.2026 bestand alle **7 Aufnahmen**. Native Zeichnungen
auf Quell- und Zielseite wurden erfasst; die Screenshots vor, während und nach der
Portalberührung wurden zusätzlich gesichtet. Ein erst am lebenden Aufbau ergänzter
magenta Block ist dabei sichtbar und bestätigt, dass die aktuelle Sable-Geometrie
verwendet wird. Der Beobachter wechselte für die Zielseitenprüfung administrativ
die Dimension mit `portal tp`; dies ist keine Grafikprüfung eines sitzenden
Zugpassagiers. Der Bericht liegt unter
`build/reports/train-portal-client/20261027-193542-42e19f99/report.json`.

Die gemessenen medianen Framezeiten betrugen 8,340399 ms ohne Portalberührung sowie
8,363548 / 8,345869 ms vor dem ersten Rad auf Quell-/Zielseite und
8,398059 / 8,380209 ms während der Durchfahrt auf Quell-/Zielseite. Der Client war
auf **120 FPS begrenzt**. Diese Werte dokumentieren den konkreten Prüflauf und sind
kein unlimitierter Leistungsbenchmark oder Nachweis allgemein unveränderter FPS.
Die Ergebnisse sind keine pauschale Zusicherung für beliebige Renderer, Shader,
Portalanordnungen oder weitere Zug-/Physikmods.

Historischer Prüfstand für 1.5.7 am 25.10.2026:
`./gradlew build compileTrainPortalTestKotlin --offline` war erfolgreich, alle
**110 JUnit-Tests** bestanden. Das gepackte 1.5.7-Artefakt wurde
mit Sable 2.0.6 einschließlich Hin-/Rückfahrt, mehreren Aufbauten, Rollback und
Serverneustart geprüft (`packaged-2.0.6.json`). Das Profil ohne optionale Mods
startet und besteht seine Smoke-Prüfung (`build/reports/smoke/server-none.json`).
Auch das damalige Quellprofil mit Sable 2.0.5 bestand alle Fahr-/Rollback-/Neustartprüfungen
und den erweiterten Entkopplungstest mit zwei CTF-Aufbauten (`sources-2.0.5.json`).
Sable 2.0.5 gehört ab 1.5.8 nicht mehr zur unterstützten Kombination.
