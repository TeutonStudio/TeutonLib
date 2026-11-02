# Immersive Melodies auf Android

Mit **Immersive Melodies 0.6.4+1.21.1** überspringt TeutonLib auf Android ausschließlich die Erkennung von Hardware-MIDI-Geräten.

## Verwendung und Wirkung

Die Funktion aktiviert sich automatisch bei aktiver [Android-Erkennung](Android-und-Zalith-Kompatibilitaet) und der exakten Modversion. MIDI-Dateien, Instrumente und die Minecraft-Soundausgabe bleiben erhalten. Verwende sie wie gewohnt; es gibt keinen zusätzlichen Block oder Config-Eintrag.

Der Mixin beendet den Start des Hardware-MIDI-Listeners vor dessen Geräteerkennung. Er greift nicht in die übrige Wiedergabe ein. Das Protokoll enthält einmalig den Hinweis, dass Hardware-MIDI auf dieser Android-Laufzeit nicht verfügbar ist.

Andere Versionen erhalten den Eingriff nicht. Mit `-Dteutonlib.mobile.mode=off` wird die mobile Integration vor dem Start abgeschaltet. `on` erzwingt die mobile Erkennung, hebt aber die Versionsgrenze nicht auf.

## Quellen

- [Begrenzter MIDI-Eingriff](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/java/de/TeutonStudio/TeutonLib/mixin/mobile/ImmersiveMelodiesMidiMixin.java)
- [Exakte Versionsauswahl](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/CsoMixinPlugin.kt)

[Wiki-Startseite](Home) · [Android und Zalith](Android-und-Zalith-Kompatibilitaet)
