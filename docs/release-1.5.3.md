# TeutonLib 1.5.3

## Android- und Zalith-Kompatibilität

- Ponder/Catnip erhält einen adaptiven Framebufferpfad. TeutonLib prüft den Originalpfad,
  zwei D24S8-Texturvarianten und einen D24S8-Renderbuffer einschließlich eines echten
  Stencil-/Pixeltests. Resize und Freigabe fehlgeschlagener Kandidaten werden kontrolliert.
- Ein experimenteller Farbmasken-Compositor steht als letzter Fallback über
  `-Dteutonlib.mobile.render.colorMask=true` bereit.
- PowerGrid 0.5.5.1 überspringt auf Android den ungeeigneten Native-Ladeversuch und nutzt
  den bereits vorhandenen Java-Solver.
- Essential 1.3.10.9 behandelt Discord IPC über einen nicht verbundenen, sicher
  schließbaren Socket-Adapter als optional nicht verfügbar.
- Immersive Melodies 0.6.4 überspringt ausschließlich die Hardware-MIDI-Erkennung. MIDI-
  Dateien, Instrumente und die Minecraft-Soundausgabe bleiben unberührt.
- Visual Recipe Editor 1.2.1 führt auf Android sein Entwickler-Workspace-Backup nicht aus.
- C2ME Native Math bleibt in seinem unterstützten Java-Fallback; TeutonLib umgeht dessen
  Schutzprüfung nicht.

Die mobile Erkennung ist standardmäßig automatisch aktiv. Sie kann früh im Startvorgang
mit `-Dteutonlib.mobile.mode=on` erzwungen oder mit
`-Dteutonlib.mobile.mode=off` vollständig deaktiviert werden.

## Ressourcen-Kompatibilität

Eng versionierte und per SHA-256 abgesicherte Regeln behandeln bekannte Release-Artefakte
aus Create Radar 0.4.9.2, EnderIO 8.2.10-beta ohne Athena, NTGL 3.1.5 und Design 'n'
Decor 2.2b. Abweichende Inhalte, Nutzerpakete und spätere Modversionen werden nicht
verändert. Zusätzlich protokolliert TeutonLib den Besitzer und Fingerabdruck fehlerhafter
`pack.mcmeta`-Dateien, damit verbleibende Metadatenfehler eindeutig zugeordnet werden
können.

## Qualitätssicherung

- Optionale Mixins werden nach Umgebung, Zielmod und geprüftem Versionsbereich ausgewählt.
- ASM-Kompatibilitätstests prüfen die Zielsignaturen der unterstützten Release-JARs.
- Fingerprint-Tests sichern den engen Geltungsbereich der Ressourcenregeln ab.
- Dedizierte Server laden keine Client- oder OpenGL-Kompatibilitätsklassen.
