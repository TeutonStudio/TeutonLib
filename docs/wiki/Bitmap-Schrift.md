# Bitmap-Schrift

**Deutsch** · [English](Bitmap-Schrift-en) · [Übersicht](Home)

`teutonlib.font` ist das mit TeutonLib v1.6.0 ausgelieferte CC:Tweaked-Lua-Modul für eine kompakte **3×5-Bitmap-Schrift**. Es verwendet dieselben Terminal-, Monitor-, Pixel- und benutzerdefinierten Ziele wie die [Grafik-API](Grafik-API). Eine zusätzliche Grafikmod ist nicht nötig.

## Text ausgeben

```lua
local font = require("teutonlib.font")
local target = font.targets.terminal(term.current())

local metrics = font.write(target, 2, 2, "ALT 1200", {
  scale = 1,
  spacing = 1,
  color = colors.orange,
  clear = false
})
print(metrics.width, metrics.height, metrics.pixels)
```

Die Schriftfunktionen verwenden **1-basierte Rasterkoordinaten**. Dies unterscheidet sie von der 0-basierten absoluten SVG-Platzierung. `scale` wird auf eine ganze Zahl von mindestens 1 begrenzt; `spacing` auf eine ganze Zahl von mindestens 0. Standard sind Skalierung 1, Abstand 1 und Weiß. `colour` ist als Alternative zu `color` erlaubt.

Standardmäßig übermalt `write` nur die Schriftpixel. `clear = true` leert das Ziel vor der Ausgabe; `background` legt dabei die Hintergrundfarbe fest. Der Rückgabewert enthält `width`, `height` und `pixels`.

## Messen und zusammensetzen

```lua
local width, height = font.measure("RPM\n1200", {scale = 1})
local rows, pixels = font.rasterize("RPM", 1, 1, {
  color = colors.white
}, {})
-- Weitere Rasterinhalte können zu rows hinzugefügt werden.
target.present(rows, {clear = true, background = colors.black})
```

`measure(text, options?)` liefert zwei Zahlen. `rasterize(text, x, y, options?, rows?)` ergänzt eine vorhandene Rastertabelle und liefert diese sowie die Zahl geschriebener Schriftpixel. Zeilenumbrüche werden unterstützt. Enthalten sind lateinische A–Z, Ziffern und ausgewählte Satzzeichen; Kleinbuchstaben werden groß dargestellt, unbekannte Zeichen erscheinen als Fragezeichen. Die Schrift ist bytebasiert und keine allgemeine Unicode-Schrift.

`require("teutonlib.svg").write(...)` ruft dieselbe Schriftfunktion auf.

## Implementierung

[Schrift und Zeichensatz](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/resources/data/computercraft/lua/rom/modules/main/teutonlib/font.lua), [Ausgabeadapter](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/resources/data/computercraft/lua/rom/modules/main/teutonlib/render_target.lua).

[Zur Startseite](Home)

## Abbildung


Die Texte RPM und READY stammen tatsächlich aus der 3×5-Bitmap-Schrift; Formen aus dem SVG-Modul.

![Bitmap-Schrift: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/wiki-graphics.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
