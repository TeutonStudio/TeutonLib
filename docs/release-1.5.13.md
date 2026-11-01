# TeutonLib 1.5.13

## New-Age-Motordrehzahl nach Sable-Montage

Beim Zusammenbau setzt Create die kinetische Drehzahl und die Netzzuordnung
zurück. Create New Age behält separat die zuletzt erzeugte Drehzahl. Dadurch
konnte der Motor sein neues Rotationsnetz überspringen; spätere Drehzahl- oder
Drehrichtungswechsel konnten außerdem einen falschen vorherigen Wert verwenden.

TeutonLib verwendet im New-Age-Rotationsupdate die tatsächliche kinetische
Drehzahl. Ein selbst angetriebener Motor stellt nach seinem normalen
Energie-/Redstone-Tick ein fehlendes Rotationsnetz wieder her. Fremd angetriebene
Motoren behalten Creates normale Quellenprüfung.

Unterstützte Kombination: Minecraft 1.21.1, Create 6.0.10, Create New Age
1.1.7c+mc1.21.1 und Sable 2.0.6. Andere Versionen aktivieren diesen gezielten
Mixin nicht. Es gibt keine zusätzliche Config; die bisherigen optionalen
Abhängigkeiten bleiben optional.

## Prüfung

`./gradlew build energyTestClasses --offline` prüft unter anderem Versionsgrenzen,
die Eingriffspunkte der originalen New-Age-JAR und die optionale Paketierung.

`python3 tools/verify_motor_speed.py` startet einen separaten lokalen Server und
grafischen Client. Der Servertest prüft alle drei Motorvarianten, beide
Drehrichtungen, Montage, Verschiebung und Drehung, FE-Versorgung, Wellenausgabe
sowie Annahme naher und Ablehnung entfernter Create-Einstellpakete. Der Client
öffnet die echte Create-Drehzahlauswahl mit dem Schraubenschlüssel und speichert
neue Werte vor und nach der Montage sowie nach Bewegung und Drehung. Die Werte
werden anschließend auf dem Server kontrolliert.

Berichte: `build/reports/motor-speed/`. Testwelten:
`run/smoke/motor-speed/`. Die Testmod ist nicht in der ausgelieferten JAR enthalten.
