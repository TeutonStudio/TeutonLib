# Essential auf Android

**Deutsch** · [English](Essential-auf-Android-en) · [Übersicht](Home)

Für **Essential 1.3.10.9** behandelt TeutonLib die lokale Discord-IPC-Verbindung auf Android als optional nicht verfügbar. Ein typgerechter, sicher schließbarer Socket-Adapter meldet einen getrennten Zustand, anstatt einen ungeeigneten Desktop-Socketpfad zu verwenden.

## Verwendung

Bei aktiver [Android-Erkennung](Android-und-Zalith-Kompatibilitaet) arbeitet die Integration automatisch. Essential verwendet seinen normalen getrennten Discord-Zustand. Es gibt keine neue Spieloberfläche und keinen zusätzlichen Config-Schalter.

Der Adapter meldet `connected = false`. Verbindungs-, Lese- und Schreibversuche antworten mit einer definierten `IOException`; Schließen ist gefahrlos möglich. Der Adapter stellt keine Discord-Verbindung her und betrifft ausschließlich diesen lokalen IPC-Pfad.

## Versionsprüfung und Diagnose

Essential verwendet einen eigenen gestuften Loader. Deshalb prüft TeutonLib die tatsächliche Essential-Version zusätzlich zur frühen Mixin-Auswahl im Laufzeitadapter. Nur `1.3.10.9` erhält den Eingriff; eine unbekannte oder ungeprüfte Version wird mit einer Warnung unverändert gelassen.

Die mobilen Startoptionen `-Dteutonlib.mobile.mode=on` beziehungsweise `off` gelten auch hier. Die automatische Erkennung ist der Standard. Das Kompatibilitätsprotokoll nennt den nicht verfügbaren Android-IPC-Pfad einmalig.

## Quellen

- [Verifizierter Adapter und getrennte Socket-Semantik](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/compat/EssentialSocketAdapter.kt)
- [Optionaler Essential-Mixin](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/java/de/TeutonStudio/TeutonLib/mixin/mobile/EssentialDiscordIpcMixin.java)

[Wiki-Startseite](Home) · [Android und Zalith](Android-und-Zalith-Kompatibilitaet)

## Abbildung


Der tatsächliche Essential-Socket-Test prüft connected=false, definierte Fehler und sicheres Schließen. Desktop-Aufnahme, kein Android-Spieltest.

![Essential auf Android: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-MobileDependencyCompatibilityTest.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
