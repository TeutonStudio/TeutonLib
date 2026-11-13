# New-Age-Motordrehzahl auf Sable

**Deutsch** · [English](New-Age-Motordrehzahl-auf-Sable-en) · [Übersicht](Home)

TeutonLib stellt das Create-Rotationsnetz von New-Age-Motoren nach dem Zusammenbau auf einer Sable-Konstruktion wieder her. Angeschlossene Wellen werden anschließend wieder angetrieben; Drehzahl- und Richtungswechsel vergleichen die tatsächliche kinetische Drehzahl.

## Voraussetzungen

Die Korrektur wird automatisch ausschließlich für diese Kombination aktiviert:

| Komponente | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| Create | 6.0.10 |
| Sable | 2.0.6 |
| Create New Age | 1.1.7c+mc1.21.1 |

Die optionalen Mods werden nicht mit TeutonLib ausgeliefert. Ohne eine der Voraussetzungen oder bei abweichender Version bleibt dieser Mixin inaktiv.

## Verwendung und Verhalten

Baue einen grundlegenden, fortgeschrittenen oder verstärkten New-Age-Motor samt angeschlossenen Wellen auf deiner Sable-Konstruktion und versorge ihn wie gewohnt. Es gibt für diese Reparatur keinen zusätzlichen Block, Befehl oder Config-Schalter.

Beim Zusammenbau setzt Create kinetische Geschwindigkeit und Netzwerk zurück, während New Age seine zuletzt berechnete Drehzahl separat behält. TeutonLib gleicht den Motor am Ende seines Serverticks ab, nachdem New Age die aktuelle FE- und Redstone-Ausgabe bestimmt hat. Hat der Motor keine externe Quelle und weichen erzeugte und kinetische Drehzahl ab, wird das Rotationsnetz aktualisiert. Auch die normale Wechselprüfung liest die kinetische Drehzahl anstelle des alten New-Age-Caches.

Die [FE-Annahme an Motorerweiterungen](New-Age-FE-Eingang) ist eine eigenständige Funktion und lässt sich separat konfigurieren.

## Quellen

- [Versionsprüfung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/NewAgeMotorCompatibility.kt)
- [Drehzahl- und Netzkorrektur](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/mixin/newage/NewAgeMotorRotationMixin.kt)
- [Prüfablauf und Grenzen](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/release-1.5.13.md)

[Wiki-Startseite](Home) · [FE-Eingang an Motorerweiterungen](New-Age-FE-Eingang)

## Abbildung


Ausgeführte Desktop-Prüfung der unterstützten Motorvarianten und der versionsgebundenen Drehrichtungsintegration; keine neue Fahrtaufnahme.

![New-Age-Motordrehzahl auf Sable: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-NewAgeMotorCompatibilityTest.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
