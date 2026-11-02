# PowerGrid auf Android

TeutonLib verhindert mit **PowerGrid 0.5.5.1** auf Android den ungeeigneten Versuch, den nativen Desktop-Solver zu laden. Stattdessen bleibt PowerGrids vorhandener Java-Solver `JavaMNA` aktiv.

## Verwendung

Installiere die geprüfte PowerGrid-Version und starte mit der normalen automatischen [Android-Erkennung](Android-und-Zalith-Kompatibilitaet). Die vorhandenen PowerGrid-Blöcke und Schaltungen werden unverändert verwendet. Es gibt keinen zusätzlichen TeutonLib-Spielschalter.

Der Eingriff überspringt ausschließlich `NativeMNA.tryLoad`. TeutonLib liefert keine Android-Nativbibliothek und ersetzt den Java-Solver nicht. Das Protokoll meldet einmalig, dass der Desktop-Nativpfad übersprungen wurde und `JavaMNA` aktiv bleibt.

## Grenzen

Der Mixin wird bei anderen PowerGrid-Versionen nicht aktiviert. Über `-Dteutonlib.mobile.mode=off` lässt sich die mobile Integration vor dem Start insgesamt abschalten; `on` erzwingt ihre Auswahl auch für Launcher-Sonderfälle. Diese Optionen sollten als JVM-Argumente im Launcher gesetzt werden.

## Quellen

- [Native-Ladeversuch überspringen](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/java/de/TeutonStudio/TeutonLib/mixin/mobile/PowerGridNativeMixin.java)
- [Umgebungs- und Versionsauswahl](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/CsoMixinPlugin.kt)

[Wiki-Startseite](Home) · [Android und Zalith](Android-und-Zalith-Kompatibilitaet)
