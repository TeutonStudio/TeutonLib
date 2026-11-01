# FluidLogistics / Factory Logistics

TeutonLib 1.5.4 aktiviert den Adapter automatisch für diese Kombination:

| Mod | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| Create | 6.0.10 |
| FluidLogistics | 1.3.1 |
| Create Factory Logistics | 1.4.9 |
| Create Factory Abstractions | 1.4.9, im Factory-Logistics-JAR enthalten |

Die Testumgebung verwendet NeoForge 21.1.234 und Java 21. Andere oder fehlende
Modversionen aktivieren weder Ersatz noch Abschaltung. Das behebt dort auch keinen
bereits vorhandenen Konflikt zwischen Fremdmods. Bei passenden Versionsnummern und
abweichenden relevanten Klassen bricht die Aktivierung mit einer Diagnose ab.

## Funktionsweise

FluidLogistics 1.3.1 wählt in `RequestPromiseQueueMixin` die überladenen Methoden
`add`, `forceClear` und `getTotalPromisedAndRemoveExpired` nur über ihren Namen aus.
Factory Logistics ergänzt Überladungen mit generischen Parametern. Der Injector für
`add(RequestPromise)` trifft dadurch auch auf `add(GenericStack)` und scheitert mit
einem ungültigen Deskriptor. `InventorySummaryMixin` hat dasselbe Selektorproblem
bei `getCountOf` und `erase`.

Eine reine Korrektur der Deskriptoren ließe zwei widersprüchliche Datenbestände
zurück. Der Adapter ersetzt daher genau diese beiden FluidLogistics-Mixins mittels
MixinSquared 0.3.7. Die Bibliothek wird per Jar-in-Jar mitgeliefert. Beide Ersatzmixins
verwenden vollständige JVM-Deskriptoren und verpflichtende Injection-Punkte.

Factory Logistics bleibt Eigentümer der Promise- und Bestandsdaten. Der Adapter
wendet FluidLogistics' `PackageResources.keyOf` auf dessen lebende Sammlungen an.
Es gibt keinen zusätzlichen persistenten Index. Sowohl Creates ItemStack-Methoden
als auch Factory Logistics' generische Methoden erreichen dieselbe Buchführung.
Andere generische Ressourcen verwenden weiter Factory Logistics' Verhalten.

- Ressourcenidentitäten bleiben bei unterschiedlichen Trägerkomponenten erhalten;
  unterschiedliche Komponenten der enthaltenen Ressource bleiben getrennt.
- Teillieferungen verändern dieselben Zusagen, die gezählt und gespeichert werden,
  und melden auch ohne vollständig erfüllte Zusage eine Änderung.
- Ablauf und `forceClear` entfernen Einträge aus der maßgeblichen Sammlung.
- `erase` entfernt alle Trägervarianten derselben normalisierten Ressource.
- Summen werden mit `long` berechnet und bei `BigItemStack.INF` begrenzt.
- Bestandsänderungen invalidieren Factory Logistics' sortierten Cache. Einträge
  werden vor Mengenänderungen aus ihrer Hash-Sammlung entfernt und danach eingefügt.
- Item-Map und gefilterte Summen lesen denselben generischen Speicher. Die Map
  enthält alle Komponentenvarianten eines Trägeritems, ohne frühere Einträge zu überschreiben.
- Creates Item-Bestandscodec liest diese vollständige Map statt des ungenutzten
  alten Speichers. Sein bestehendes Format bleibt erhalten; native Nicht-Item-
  Ressourcen bleiben bei Factory Logistics' generischen Übertragungswegen.
- Queue-NBT-Format, Alter und Ressourcenpersistenz bleiben bei Factory Logistics.

Vor der ersten Abschaltung wird der SHA-256 der relevanten Originalklassen aller
vier Mod-JARs geprüft. Die Regeln stehen in `LogisticsCompatibility.kt` und werden
auch für beide Ersatzmixins verwendet. Die Original-JAR-Prüfsummen stehen in
[`logistics-compatibility.sha256`](logistics-compatibility.sha256).

## Entwicklung und Prüfung

Zusätzlich zu den bestehenden lokalen Compile-Abhängigkeiten werden benötigt:

- [FluidLogistics 1.3.1 für 1.21.1](https://cdn.modrinth.com/data/g4AWLVIr/versions/YepGDZRz/fluidlogistics-1.3.1-mc1.21.1.jar)
- [Factory Logistics 1.4.9 für 1.21.1](https://cdn.modrinth.com/data/QMGO17gm/versions/uvsUx0PA/create_factory_logistics-1.21.1-1.4.9.jar)

Beide JARs nach `dependencies/compile` legen und das enthaltene
`META-INF/jars/create_factory_abstractions-1.21.1-1.4.9.jar` ebenfalls dorthin
extrahieren. Beim Serverlauf wird nur das äußere Factory-Logistics-JAR hinzugefügt;
NeoForge lädt Abstractions aus dessen eingebettetem JAR. Keine dieser Mod-Dateien
wird mit TeutonLib ausgeliefert.

```sh
./gradlew test check --offline
python3 tools/verify_logistics.py baseline
python3 tools/verify_logistics.py both
python3 tools/verify_logistics.py packaged
python3 tools/verify_logistics.py fluid
python3 tools/verify_logistics.py factory
python3 tools/verify_logistics.py none
python3 tools/verify_logistics.py bare
```

JUnit prüft die Versionsmatrix, fehlende/veränderte Klassen, Release-Prüfsummen,
eingebettete MixinSquared-Laufzeit und die vollständigen Deskriptoren gegen die
Original-JARs. Der Baseline-Lauf entfernt TeutonLib aus der Entwicklungs-Modliste
und dem Klassenpfad und löst über den Testmod die ursprüngliche Queue-Transformation
aus. Erwartet wird der ursprüngliche Descriptor-Absturz.

Das Profil `both` lädt einen separaten Testmod und prüft die tatsächlich durch
Mixin transformierten Klassen: zwei Ressourcen im selben Trägeritem, identische
Ressourcen mit anderen Trägerkomponenten, unterschiedliche Ressourcenkomponenten,
beide API-Pfade, Teillieferungen, Ablauf, Löschen, große Mengen, Kopieren, Zusammenführen,
Cache-Invalidierung, Item-Map, gefilterte Summen und Bestandscodec sowie normale
Items und native generische Flüssigkeiten. Es speichert eine teilweise erfüllte
Queue und einen Ressourcenbestand, beendet den Server und prüft nach einem
Prozessneustart Mengen, Ressourcenidentität, Alter und weitere Erfüllung.
Das Profil `packaged` führt dieselben Prüfungen mit dem gebauten Release-JAR
anstelle der TeutonLib-Klassenverzeichnisse aus.

Die Profile `fluid`, `factory` und `none` prüfen Start und sauberes Beenden ohne
gemeinsamen Adapter. `bare` lässt zusätzlich Create weg. Welten liegen unter `run/smoke/logistics/<Profil>`, Berichte
unter `build/reports/logistics`. Der Testmod ist nicht Bestandteil des Release-JARs.

Eine Kopie der ursprünglich betroffenen Benutzerwelt und eine vollständige
Spielprüfung von Stock Links, Gauges und Bestellnetzwerken sind gesonderte
Abnahmefälle; die automatisierten Tests verwenden eine neue, wegwerfbare Welt.

## Referenzen

- [FluidLogistics 1.3.1: Queue](https://github.com/yision1/CreateFluidLogistics/blob/d85aaa2db5f06610a03cdbb5e6a53b348b188da5/src/main/java/com/yision/fluidlogistics/mixin/logistics/RequestPromiseQueueMixin.java)
- [Factory Logistics 1.4.9: Queue](https://github.com/zznty/CreateFactoryLogistics/blob/22f57d3230547cd62916a10ce599230073daa562/forge/src/main/java/ru/zznty/create_factory_logistics/mixin/logistics/packager/GenericPromiseQueueMixin.java)
- [MixinSquared Canceller](https://github.com/Bawnorton/MixinSquared/wiki/Mixin-Canceller)
