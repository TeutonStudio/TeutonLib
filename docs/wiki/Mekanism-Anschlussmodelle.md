# Mekanism-Anschlussmodelle auf Sable

**Deutsch** · [English](Mekanism-Anschlussmodelle-en) · [Übersicht](Home)

TeutonLib aktualisiert Mekanism-Rohranschlüsse auf Sable-Konstruktionen zuverlässig, wenn ihre Verbindung mit dem Konfigurator geändert wird. Ziehen-/Drücken-Modelle erreichen dabei auch während der Fahrt den richtigen Rohrblock auf dem Client.

## Voraussetzungen

Die Integration ist exakt auf diese Releases begrenzt:

| Komponente | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| Mekanism | 10.7.19 |
| Create Aeronautics: Mekanism Compatibility | 0.1.21 |
| Sable | 2.0.6 |
| Immersive Portals: Aeronautics | 2.6.4 |

Die Mod-IDs der letzten beiden Adapter sind `create_mekanism_compat` und `ip_aeronautics`. Fehlt eine Voraussetzung oder weicht ihre Version ab, bleiben die Mixins inaktiv. Für diese Korrektur gibt es keinen zusätzlichen Block und keinen TeutonLib-Schalter.

## Bedienung und Funktionsweise

Baue die Mekanism-Rohre wie gewohnt auf einer Sable-Konstruktion und ändere ihre Verbindungen mit dem Mekanism-Konfigurator. TeutonLib adressiert die Aktualisierung mit der festen Plot-Blockadresse anstelle einer momentan projizierten Weltzelle. So bleibt der Empfängerblock auch bei Bewegung und Drehung eindeutig.

Die Empfängerprüfung verwendet dieselbe Chunk-Empfängersuche wie die folgende Mekanism-Paketverteilung. Dadurch berücksichtigt sie auch Spieler, die Sable-Plots über Immersive Portals verfolgen. Nur lebende Sable-Transmitter, die sich selbst aktualisieren und an ihrer erwarteten Speicheradresse stehen, verwenden den besonderen Sendepfad. Andere Updates folgen dem ursprünglichen Verhalten.

Die Funktion korrigiert die Synchronisierung und Darstellung der Anschlüsse; sie ersetzt keine Mekanism-Flüssigkeits- oder Energieverteilung.

## Quellen und Prüfung

`python3 tools/verify_mekanism_models.py` verwendet einen separaten Server und grafischen Sodium-Client. Die Prüfung vergleicht Server-/Clientmodi, Modelldaten, tatsächlich kompilierte Geometrie und adressierte Pakete für Weltrohre, Zusammenbau und Konfiguratoränderungen im Stand und während der Fahrt. Testmods und optionale Fremdmods werden nicht mit TeutonLib ausgeliefert.

- [Versionsprüfung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/MekanismModelCompatibility.kt)
- [Paketadressierung auf Sable](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/mixin/mekanism/MekanismMountedUpdateMixin.kt)
- [Empfängerprüfung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/mixin/mekanism/MekanismPacketTrackingMixin.kt)

[Wiki-Startseite](Home)

## Abbildung


Echte Rohrmodell-Aufnahmen nach Modusänderung und während Sable-Bewegung.

![Mekanism-Anschlussmodelle auf Sable: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/mekanism-modes.png)

![Mekanism-Anschlussmodelle auf Sable: 2](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/mekanism-moving.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
