# Bitmap font

[Deutsch](Bitmap-Schrift) · **English** · [Overview](English)

`require("teutonlib.font")` supplies a compact **3×5 font** for the same terminal, monitor, pixel-display and custom targets as the SVG module. No extra graphics mod is required.

```lua
local font = require("teutonlib.font")
local target = font.targets.terminal(term.current())
local metrics = font.write(target,2,2,"RPM 1200",{
  scale=1, spacing=1, color=colors.orange, clear=false
})
print(metrics.width, metrics.height, metrics.pixels)
```

Font coordinates are **1-based**, unlike absolute SVG placement. Scale is an integer at least 1, spacing at least 0; defaults are 1/1/white. `colour` aliases `color`. `clear=true` clears the target using `background`; normal writing only paints glyph pixels.

`measure(text,options?)` returns width and height. `rasterize(text,x,y,options?,rows?)` adds glyphs into a raster table and returns it plus the written-pixel count; present with `target.present(rows,options)`. Newlines work. The byte-based font includes A–Z, digits and selected punctuation; lowercase becomes uppercase and unknown characters become `?`. It is not a general Unicode font. `svg.write` delegates to this same font.

## Illustration


RPM and READY are actual 3×5 bitmap-font output; shapes come from the SVG module.

![Bitmap font: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/wiki-graphics.png)


## Sources

- [font.lua](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/resources/data/computercraft/lua/rom/modules/main/teutonlib/font.lua)
- [render_target.lua](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/resources/data/computercraft/lua/rom/modules/main/teutonlib/render_target.lua)

[Media provenance and verification scope](Bildnachweise-en)
