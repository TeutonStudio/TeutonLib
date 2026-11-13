# Grafik-API: portable SVG-Ausgabe

**Deutsch** · [English](Grafik-API-en) · [Übersicht](Home)

TeutonLib v1.6.0 liefert das reine Lua-Modul `teutonlib.svg` für CC:Tweaked. Es rendert einen unterstützten SVG-Teilumfang auf Terminals, Monitoren und über Adapter auf Pixelanzeigen. CCVisual und CC Aeroworks sind für Terminal-/Monitorausgabe nicht erforderlich. [Bitmap-Schrift](Bitmap-Schrift) ist zusätzlich direkt verwendbar.

## Erstes Bild

Ein Monitor muss unter der verwendeten Peripheral-Adresse erreichbar sein:

```lua
local svg = require("teutonlib.svg")
local target = svg.targets.monitor("right", 0.5)
local source = [[
<svg viewBox="0 0 32 16">
  <rect x="1" y="1" width="30" height="14" fill="#111111" stroke="white"/>
  <circle cx="8" cy="8" r="4" fill="lime"/>
  <text x="14" y="10" fill="white">READY</text>
</svg>
]]
local style = svg.style {
  backgroundColor = colors.black,
  fit = "contain"
}
local result = svg.render(target, source, style)
print(result.width, result.height, result.elements, result.pixels)
```

Für das aktuelle Terminal `svg.targets.terminal(term.current())` verwenden. Terminal- und Monitorziele zeichnen einen Rasterpunkt als Terminalzelle. Ihre Auflösung folgt `getSize()`; `textScale` verändert die Monitorauflösung.

## SVG-Teilumfang

Unterstützt sind `rect`, `line`, `polyline`, `polygon`, `circle`, `ellipse` und `text`. Hinzu kommen `viewBox`, `fill`, `stroke`, `stroke-width`, `font-size`, `translate(...)` und `scale(...)`. Farben werden auf CCs 16 Farben abgebildet. ControlDesk-Pixelanzeigen erhalten ein monochromes Raster. Es handelt sich nicht um eine vollständige Browser-SVG-Implementierung.

**Aktuelle Grenze:** Der vorhandene `path`-Parser erzeugt bei üblichen SVG-Pfaden derzeit keine Punkte; solche Elemente bleiben leer. Verwende für geradlinige Konturen `line`, `polyline` oder `polygon`.

`svg.parse(source)` liefert ein wiederverwendbares Dokument. `svg.render(target, sourceOrDocument, style?)` liefert Breite, Höhe, Element- und Pixelzählung. `style` muss durch `svg.style` erzeugt werden; rohe Optionstabellen sind an dieser Stelle ungültig.

Ohne eigenen Style gelten `backgroundColor = colors.black`, `clearDraw = false`, `fit = "contain"`. `contain` erhält das Seitenverhältnis innerhalb des Ziels, `stretch` füllt beide Achsen, `cover` füllt mit möglichem Beschnitt. `uniformColor` dient als Fallback für Elemente ohne erkennbare Farbe; explizite SVG-Farben bleiben erhalten, `none` bleibt transparent.

## Position und Größe

```lua
-- Absolute Platzierung: 0-basierte ganzzahlige Pixelkoordinaten.
svg.absolute.between(target, source, {2, 1}, {17, 8}, style)
svg.absolute.sized(target, source, {2, 1}, {16, 8}, style)
svg.absolute.at(target, source, {2, 1}, style)

-- Relative Platzierung mit Werten zwischen 0 und 1.
svg.relative.between(target, source, {0, 0}, {0.49, 1}, style)
svg.relative.sized(target, source, {0.5, 0}, {0.5, 1}, style)
svg.relative.at(target, source, {0.5, 0.5}, style)
```

`between` verwendet einen inklusiven Endpunkt; `sized` Breite und Höhe. Beide füllen ihren Bereich in X und Y unabhängig. `at` verwendet die natürliche Größe aus `width`/`height` und/oder `viewBox` und meldet einen Fehler, wenn diese nicht bestimmbar ist. Vektoren dürfen `{x = ..., y = ...}` oder `{..., ...}` sein. Relative Positionen werden auf die erste bis letzte Pixelposition gerundet, relative Größen auf die vollständige Pixelanzahl.

Platzierte SVGs verändern nur ihren Bereich. Mit `clearDraw = false` wird der Bereich einschließlich transparenter Stellen durch `backgroundColor` ersetzt. Mit `clearDraw = true` bleiben vorhandene Pixel unter transparenten Stellen sichtbar. Gültige absolute Bereiche außerhalb des Ziels werden beschnitten; umgekehrte Bereiche, leere Größen und relative Werte außerhalb 0–1 verursachen Fehler.

## Weitere Ziele

```lua
local displayTarget = svg.targets.display(desk, "big")
local eventTarget = svg.targets.event(event)
local customTarget = svg.targets.custom(64, 32, function(rows, options)
  -- rows[y][x] enthält eine CC-Farbe oder keinen gesetzten Pixel.
  -- options einschließlich bounds/clear für die eigene Ausgabe beachten.
end)
```

`desk` muss die öffentliche ControlDesk-Pixel-API anbieten. `targets.event` ist für CC-Aeroworks-Display-Skripte vorgesehen und löst deren `display`-Modul erst beim Aufruf auf. Teilupdates an Pultanzeigen benötigen `setDisplayPixelBatch` oder `setDisplayPixel`. Ein eigenes Ziel kann stattdessen `getSize()` und `present(rows, options)` implementieren.

## Implementierung

[SVG-Modul](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/resources/data/computercraft/lua/rom/modules/main/teutonlib/svg.lua), [Renderziele](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/resources/data/computercraft/lua/rom/modules/main/teutonlib/render_target.lua), [ausführliche Grafikreferenz](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/computer-graphics.md).

[Zur Startseite](Home)

## Abbildung


Echte Ausgabe von teutonlib.svg und teutonlib.font im CC-Terminal ohne CCVisual oder CC-Aeroworks.

![Grafik-API: portable SVG-Ausgabe: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/wiki-graphics.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
