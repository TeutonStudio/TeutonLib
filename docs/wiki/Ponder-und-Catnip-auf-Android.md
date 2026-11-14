# Ponder und Catnip auf Android

**Deutsch** · [English](Ponder-und-Catnip-auf-Android-en) · [Übersicht](Home)

> **Ab TeutonLib 1.6.2:** Diese Adapter liegen jetzt im TerrArchitecture-Modul [`mobilecompat`](https://github.com/TeutonStudio/TerrArchitecture/blob/master/src/main/kotlin/de/TeutonStudio/mobilecompat/SPECS.md). TeutonLib liefert sie nicht mehr aus. Beschreibung und Desktop-Prüfnachweise unten beziehen sich auf frühere TeutonLib-Versionen. Bestehende `teutonlib.mobile.*`-Optionen bleiben in TerrArchitecture als Aliase erhalten.

## Historisches TeutonLib-Verhalten bis einschließlich 1.6.1

TeutonLib ergänzt einen adaptiven Framebufferpfad für Ponder-/Catnip-Oberflächen. Er behandelt Android-Grafiktreiber, die einen formal vollständigen Framebuffer melden, dessen Stencil-Maske aber nicht korrekt arbeitet.

## Voraussetzungen und Verwendung

Die Mixins werden ausschließlich bei aktiver [Android-Erkennung](Android-und-Zalith-Kompatibilitaet) und **Ponder 1.0.82+mc1.21.1** ausgewählt. Öffne Ponder und die betreffenden Create-Oberflächen wie gewohnt. Eine zusätzliche Spielkonfiguration ist für den normalen Fallback nicht nötig.

TeutonLib prüft nacheinander:

1. den vorhandenen NeoForge-/Ponder-Pufferpfad;
2. eine D24S8-Textur mit getrennten Tiefen- und Stencil-Anbindungen;
3. eine D24S8-Textur mit gemeinsamer Anbindung;
4. einen D24S8-Renderbuffer.

Jeder Kandidat muss sowohl die Vollständigkeitsprüfung als auch einen echten Stencil-/Pixeltest bestehen. Nicht funktionierende Kandidaten werden freigegeben. Größenänderungen prüfen und behandeln die verwendete Strategie erneut. Der ausgewählte Pfad und Fehler werden im Kompatibilitätsprotokoll gemeldet.

## Experimenteller letzter Fallback

Falls alle Stencil-Varianten scheitern, lässt sich vor dem Spielstart im Launcher folgender Schalter setzen:

```text
-Dteutonlib.mobile.render.colorMask=true
```

Damit darf ein experimenteller Zwei-Textur-Farbmasken-Compositor den fehlenden Stencilpfad ersetzen. Die Option ist standardmäßig ausgeschaltet und ergänzt die mobile Modusauswahl. Sie ist für den Fall vorgesehen, dass die normalen geprüften Framebufferstrategien auf dem Gerät nicht funktionieren.

Die Funktion ist clientseitig. Dedizierte Server laden keine OpenGL-Kompatibilitätsklassen. Abweichende Ponder-Versionen aktivieren diese Mixins nicht automatisch.

## Quellen

- [Framebufferstrategien und Funktionstest](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/client/compat/MobileStencilRenderTarget.kt)
- [Farbmasken-Compositor](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/client/compat/ColorMaskFallback.kt)
- [Ponder-Einsprungpunkt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/java/de/TeutonStudio/TeutonLib/mixin/mobile/PonderRenderTargetMixin.java)

[Wiki-Startseite](Home) · [Android und Zalith](Android-und-Zalith-Kompatibilitaet)

## Abbildung

Ausgeführte Desktop-Prüfung der exakt unterstützten Einsprungmethoden. Kein Funktionsnachweis auf einem Android-Gerät.

![Ponder und Catnip auf Android: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-MobileDependencyCompatibilityTest.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
