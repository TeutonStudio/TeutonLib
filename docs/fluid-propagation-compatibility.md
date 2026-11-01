# FluidLogistics / TFMG: Rohrnetz-Propagation

TeutonLib 1.5.5 aktiviert diesen Adapter automatisch für Minecraft 1.21.1,
Create 6.0.10, FluidLogistics 1.3.1 und TFMG 1.2.0. Er ist vom vorhandenen
Factory-Logistics-Adapter unabhängig. Beide Adapter können gemeinsam aktiv sein.
Die Testumgebung verwendet NeoForge 21.1.234 und Java 21.

## Ursache und Reparatur

TFMG überschreibt `FluidPropagator.propagateChangedPipe`. FluidLogistics injiziert
mit derselben Priorität 1000 in diesen überschriebenen Methodenkörper. Mixin lehnt
diese Kombination mit `cannot inject into ... merged by ... FluidPropagatorMixin`
ab. Das kann erst beim Laden einer Welt oder beim Rendern auftreten, weil die
Klasse erst dann geladen wird.

Der Ersatz hat Priorität **1100**. Mixin verlangt für diese INVOKE-Injection-Punkte
eine höhere Priorität als die des überschreibenden Mixins. Eine niedrigere
Priorität wie 900 behebt diesen Fehler nicht. Außerdem verwendet TFMG für die
lokale Variable `distance` einen primitiven `int`; FluidLogistics erwartet beim
ursprünglichen Create-Methodenkörper einen `Integer`. Der Ersatz bindet den
korrekten primitiven Wert.

Der Adapter ersetzt ausschließlich
`com.yision.fluidlogistics.mixin.fluids.FluidPropagatorMixin` über das bereits
eingebettete MixinSquared 0.3.7. TFMGs überschreibender Mixin bleibt aktiv.

- Vor dem Löschen des Rohrdrucks wird FluidLogistics' Propagationskontext erzeugt
  und der vorherige Druck erfasst.
- Beim Erreichen von Creates Reichweitengrenze werden die zusätzlichen
  Suchanfänge für FluidLogistics' höhere Pumpenreichweite gesammelt.
- Am Methodenende führt der originale `FluidPumpNetworkUpdater` diese Suche fort.
- TFMGs fest eingebaute Pumpenliste wird um FluidLogistics-Pumpen ergänzt. Das ist
  erforderlich, wenn vorhandener Druck den ursprünglichen Suchlauf bereits über
  Creates Grenze hinausführt und dadurch kein zusätzlicher Suchanfang entsteht.
- TFMGs Erkennung eigener mechanischer und elektrischer Pumpen, Achsenprüfung und
  vorhandene Flusslogik bleiben erhalten.

Alle vier Hooks verwenden vollständige JVM-Methodendeskriptoren und `require=1`.
Der Adapter benötigt keine neuen Weltdaten, keinen zusätzlichen Pumpenzähler und
keinen zusätzlichen persistenten Speicher. Er verwendet die vorhandenen APIs und
Konfigurationen von FluidLogistics. Ein ausgeschaltetes `fluidPumpEnabled` wird
weiterhin bei dessen zusätzlicher Reichweitensuche beachtet; allein dieser
Konfigurationsschalter verhindert den ursprünglichen Mixin-Absturz nicht.

Vor Abschaltung und Ersatz werden die Originalklassen von Create, FluidLogistics
und TFMG per SHA-256 geprüft. Andere oder fehlende Versionen aktivieren keinen
Ersatz. Passende Versionsnummern mit unerwarteten Klassen führen zu einer
verständlichen Fehlermeldung vor der Abschaltung. Vollständige JAR-Prüfsummen:
[`fluid-propagation-compatibility.sha256`](fluid-propagation-compatibility.sha256).

## Entwicklung und Prüfung

Zusätzlich zu den bestehenden Compile-Abhängigkeiten werden diese Original-JARs
unter `dependencies/compile` benötigt:

- `create-1.21.1-6.0.10.jar`
- `fluidlogistics-1.3.1-mc1.21.1.jar`
- [tfmg-1.2.0.jar](https://cdn.modrinth.com/data/USgVjXsk/versions/uDi14nbt/tfmg-1.2.0.jar)

Die fremden Mods werden ausschließlich als lokale Compile-/Testabhängigkeiten
verwendet und nicht in das TeutonLib-Release eingebettet.

```sh
./gradlew build --offline
python3 tools/verify_logistics.py pipes-baseline
python3 tools/verify_logistics.py pipes
python3 tools/verify_logistics.py pipes-packaged
python3 tools/verify_logistics.py pipes-all
python3 tools/verify_logistics.py pipes-fluid
python3 tools/verify_logistics.py pipes-tfmg
python3 tools/verify_logistics.py bare
```

JUnit prüft Versionen, Prüfsummen, veränderte/fehlende Klassen, statische
Handler- und Parameterannotations sowie die tatsächliche primitive lokale Variable
und die benötigten Aufrufziele in TFMGs Release-Bytecode.

Die Serverprofile erzwingen das Laden von `FluidPropagator`, statt lediglich auf
einen erfolgreichen Serverstart zu warten. `pipes-baseline` entfernt TeutonLib
aus Modliste und Klassenpfad und erwartet den ursprünglichen Prioritäts-Absturz.

`pipes` prüft die Entwicklungsquellen; `pipes-packaged` das fertige Release-JAR.
`pipes-all` lädt zusätzlich Factory Logistics 1.4.9 samt Factory Abstractions und
führt auch die vorhandenen Queue-/Bestandsprüfungen aus. Geprüft werden:

- Erkennung von Creates mechanischer Pumpe, allen fünf TFMG-Materialpumpen und
  TFMGs elektrischer Pumpe;
- FluidLogistics-Pumpen außerhalb von Creates Standardreichweite, mit und ohne
  vorherigen Rohrdruck, sowie deaktivierte zusätzliche Suche und entfernte Pumpen;
- echter Wassertransport zwischen Tanks mit einer Create-Pumpe, einer
  TFMG-Materialpumpe und einer FluidLogistics-Pumpe mit je 21 Rohrblöcken pro Seite;
- Mengenerhaltung, Unterbrechen und Wiederherstellen der Rohrleitung;
- Speichern, Prozessneustart und erneut einsetzender Transport.

Die drei Transportnetze werden mit echten Create-Kreativmotoren, Zahnrädern bzw.
direkten Wellenverbindungen angetrieben. Der Test prüft keine Energieversorgung
der elektrischen Pumpe. Deren Erkennung im Suchlauf wird gesondert geprüft.
`pipes-fluid` und `pipes-tfmg` erzwingen die Klassentransformation
im Einzelbetrieb und prüfen, dass der zusätzliche Adapter inaktiv bleibt.

Welten liegen unter `run/smoke/logistics/<Profil>`, Ergebnisse unter
`build/reports/logistics`. Testmod und Testwelten werden nicht ausgeliefert.
Die ursprünglich betroffene Benutzerwelt und der vollständige Client mit
Immersive Portals/Sable benötigen weiterhin eine gesonderte Spielprüfung.
