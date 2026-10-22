# Portable Computergrafik: SVG und Bitmap-Schrift

TeutonLib liefert mit `teutonlib.svg` und `teutonlib.font` zwei reine Lua-Module für
CC:Tweaked aus. Sie benötigen weder CCVisual noch CC-Aeroworks. Derselbe SVG-Quelltext
kann auf dem aktuellen Terminal, einem normalen Monitor oder über einen Adapter auf
einem Pixel-Display ausgegeben werden.

## Ziele

```lua
local svg = require("teutonlib.svg")

local terminalTarget = svg.targets.terminal(term.current())
local monitorTarget = svg.targets.monitor("right", 0.5)
local customTarget = svg.targets.custom(64, 32, function(rows, options)
  -- Eigenes Farbraster ausgeben.
end)
```

Für CC-Aeroworks ist keine harte Abhängigkeit eingebaut. Ein erreichbares Desk-Handle
wird direkt über seine öffentliche Pixel-API adaptiert:

```lua
local displayTarget = svg.targets.display(desk, "big")
svg.render(displayTarget, '<svg><circle cx="8" cy="8" r="4"/></svg>')
```

Innerhalb eines CC-Aeroworks-Display-Skripts kann alternativ das dort vorhandene
`display`-Modul verzögert aufgelöst werden:

```lua
local eventTarget = svg.targets.event(event)
```

Das Laden und die übrige Verwendung von TeutonLib funktionieren auch dann, wenn
CC-Aeroworks nicht installiert ist.

## SVG rendern

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

Unterstützt werden:

- `rect`, `line`, `polyline`, `polygon`, `circle`, `ellipse` und `text`;
- geradlinige `path`-Kommandos `M`, `L`, `H`, `V` und `Z`, jeweils absolut oder relativ;
- `viewBox`, `fill`, `stroke`, `stroke-width`, `font-size`, `translate(...)` und `scale(...)`;
- die Skalierungsarten `contain` (Standard), `stretch` und `cover`.

Farben werden auf die 16 Farben von CC:Tweaked abgebildet. Ein ControlDesk-Display
erhält dasselbe Raster monochrom.

Methoden:

```text
parse(source)
style(definitions?)
render(target, sourceOrDocument, style?)
write(target, x, y, text, options?)
targets.terminal(handle?)
targets.monitor(handleOrSide, textScale?)
targets.display(desk, socket)
targets.event(event)
targets.custom(width, height, presenter)
```

Ein an `render` übergebener Style muss mit `svg.style` erzeugt worden sein. Ohne
Style gelten `backgroundColor = colors.black`, `clearDraw = false` und
`fit = "contain"`. Rohe Options-Tabellen werden nicht als Style akzeptiert.

## SVG platzieren

SVGs lassen sich pixelgenau oder relativ zur Zielauflösung platzieren:

```lua
-- 0-basierte Pixelkoordinaten; das Ende ist inklusive.
svg.absolute.between(target, source, {x = 2, y = 1}, {x = 17, y = 8}, style)
svg.absolute.sized(target, source, {2, 1}, {16, 8}, style)
svg.absolute.at(target, source, {2, 1}, style)

-- Werte von 0 bis 1; dadurch bleibt die Platzierung auflösungsunabhängig.
svg.relative.between(target, source, {0, 0}, {0.49, 1}, style)
svg.relative.sized(target, source, {x = 0.5, y = 0}, {x = 0.5, y = 1}, style)
svg.relative.at(target, source, {0.5, 0.5}, style)
```

`between` skaliert unabhängig in X und Y zwischen Anfang und Ende. `sized` verwendet
statt des Endpunkts eine Breite und Höhe. `at` skaliert nicht auf einen äußeren
Bereich, sondern verwendet `width` und `height` des SVG. Fehlt eines der Maße, wird
es aus dem Seitenverhältnis der `viewBox` abgeleitet; fehlen beide, gelten die
`viewBox`-Abmessungen. Ohne bestimmbare natürliche Größe meldet `at` einen Fehler.

Absolute Vektoren enthalten ganze Zahlen und verwenden `(0, 0)` für den Pixel links
oben. Relative Positionen werden auf einen Pixel zwischen dem ersten und letzten
Pixel der Achse gerundet; relative Abmessungen werden gegen die vollständige
Pixelanzahl gerundet. Beide Vektorformen `{x=..., y=...}` und `{..., ...}` sind
zulässig. Relative Werte außerhalb von 0 bis 1, leere Abmessungen und umgekehrte
Bereiche sind Fehler. Gültige absolute Bereiche außerhalb des Ziels werden sichtbar
beschnitten.

Die platzierten Methoden verändern nie Pixel außerhalb ihres Bereichs. Standardmäßig
ersetzt `clearDraw = false` den Bereich und füllt transparente Stellen mit
`backgroundColor`. Mit `clearDraw = true` werden nur sichtbare SVG-Pixel gezeichnet;
der bisherige Inhalt bleibt unter transparenten Stellen sichtbar.

Styles unterstützen:

```lua
local style = svg.style {
  uniformColor = colors.orange,
  backgroundColor = colors.black,
  clearDraw = true,
  fit = "contain"
}
```

`uniformColor` ist eine Fallbackfarbe für Elemente ohne erkennbare Farbe. Explizite
SVG-Farben bleiben erhalten und `none` bleibt transparent. `fit` wirkt nur auf
`svg.render`; `between` und `sized` füllen ihren Bereich immer exakt in beiden Achsen.
Auf monochromen Pultanzeigen werden sichtbare Farben auf gesetzte Pixel reduziert.

## Schrift schreiben

Die eingebaute 3×5-Bitmap-Schrift kann über `svg.write(...)` oder direkt über
`teutonlib.font` verwendet werden:

```lua
local font = require("teutonlib.font")
local target = font.targets.terminal(term.current())

local metrics = font.write(target, 2, 2, "ALT 1200", {
  scale = 2,
  color = colors.orange,
  clear = false
})
print(metrics.width, metrics.height, metrics.pixels)
```

`font.measure(text, options?)` berechnet den Platzbedarf. `font.rasterize(...)`
schreibt in eine vorhandene Rastertabelle, um Text mit anderen Inhalten zu komponieren.
`font.write(...)` übermalt standardmäßig nur die Schriftpixel; `clear = true` leert
zuvor das Ziel.
