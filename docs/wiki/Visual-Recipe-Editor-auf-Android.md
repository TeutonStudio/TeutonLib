# Visual Recipe Editor auf Android

**Deutsch** · [English](Visual-Recipe-Editor-auf-Android-en) · [Übersicht](Home)

> **Ab TeutonLib 1.6.2:** Diese Adapter liegen jetzt im TerrArchitecture-Modul [`mobilecompat`](https://github.com/TeutonStudio/TerrArchitecture/blob/master/src/main/kotlin/de/TeutonStudio/mobilecompat/SPECS.md). TeutonLib liefert sie nicht mehr aus. Beschreibung und Desktop-Prüfnachweise unten beziehen sich auf frühere TeutonLib-Versionen. Bestehende `teutonlib.mobile.*`-Optionen bleiben in TerrArchitecture als Aliase erhalten.

## Historisches TeutonLib-Verhalten bis einschließlich 1.6.1

TeutonLib deaktiviert auf Android das eingebaute **Entwickler-Workspace-Backup von Visual Recipe Editor 1.2.1**. Dieser eng begrenzte Eingriff verhindert die Ausführung der gebündelten Backup-Prozedur.

## Verwendung und Grenzen

Die Anpassung wird automatisch mit der exakten Modversion und aktiver [Android-Erkennung](Android-und-Zalith-Kompatibilitaet) ausgewählt. Die Prozedur `WorkspaceBackupProcedure.execute()` endet unmittelbar; im Protokoll erscheint dazu einmalig ein Hinweis.

Es gibt keinen zusätzlichen TeutonLib-Config-Eintrag und keine Backup-Datei, die der Nutzer verwalten muss. Die Funktion ist kein allgemeines Welt-Backup-System. Sie unterdrückt ausschließlich diese Entwickler-Workspace-Prozedur und verändert keine gespeicherten Welten oder Benutzerressourcen.

Abweichende Visual-Recipe-Editor-Versionen aktivieren den Mixin nicht. Die JVM-Argumente `-Dteutonlib.mobile.mode=on` und `off` steuern wie bei den anderen mobilen Eingriffen die Umgebungswahl vor dem Start.

## Quellen

- [Backup-Prozedur überspringen](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/java/de/TeutonStudio/TeutonLib/mixin/mobile/VisualRecipeEditorBackupMixin.java)
- [Umgebungs- und Versionsprüfung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/CsoMixinPlugin.kt)

[Wiki-Startseite](Home) · [Android und Zalith](Android-und-Zalith-Kompatibilitaet)

## Abbildung

Ausgeführte Desktop-Prüfung der exakt unterstützten Einsprungmethoden. Kein Funktionsnachweis auf einem Android-Gerät.

![Visual Recipe Editor auf Android: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-MobileDependencyCompatibilityTest.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
