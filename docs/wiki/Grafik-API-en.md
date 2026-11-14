# Graphics API: portable SVG output

[Deutsch](Grafik-API) · **English** · [Overview](English)

`require("teutonlib.svg")` renders a supported SVG subset on CC terminals, monitors and optional pixel-display adapters. CCVisual/Aeroworks are not required for terminals or monitors. `teutonlib.font` also works separately.

```lua
local svg = require("teutonlib.svg")
local target = svg.targets.terminal(term.current())
local source = [[<svg viewBox="0 0 32 16">
<rect x="1" y="1" width="30" height="14" fill="#111111" stroke="white"/>
<circle cx="8" cy="8" r="4" fill="lime"/>
<text x="14" y="10" fill="white">READY</text></svg>]]
local result = svg.render(target, source, svg.style{fit="contain",backgroundColor=colors.black})
```

Supported: `rect`, `line`, `polyline`, `polygon`, `circle`, `ellipse`, `text`; `viewBox`, fill/stroke/stroke-width/font-size, translate and scale. Colors map to CC's 16 colors; ControlDesk pixel targets are monochrome. **Ordinary `path` elements currently generate no points**; use line/polyline/polygon for straight contours.

`parse` returns a reusable document. `render` returns width, height, element/pixel counts. Its style must come from `svg.style`, not a raw options table. Fit is `contain` (default), `stretch` or `cover`; explicit colors and `none` are preserved.

`absolute.between/sized/at` uses **0-based** integer placement; `relative.between/sized/at` uses 0–1 coordinates. `between` includes the endpoint; `sized` uses dimensions; `at` needs natural SVG size. Areas are independently scaled. Invalid/reversed ranges error; valid out-of-target regions clip. `clearDraw=false` replaces the placement region including transparent background; `true` preserves pixels under transparency.

Targets include `terminal(term.current())`, `monitor(name,textScale)`, `display(desk,socket)`, `event(event)` and `custom(width,height,callback)`. Terminal cells form raster points. Display/event targets lazily require their addon; partial updates need pixel/batch support. [Bitmap font](Bitmap-Schrift-en).

## Illustration


Actual teutonlib.svg and teutonlib.font output on a CC terminal without CCVisual or Aeroworks.

![Graphics API: portable SVG output: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/wiki-graphics.png)


## Sources

- [svg.lua](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/resources/data/computercraft/lua/rom/modules/main/teutonlib/svg.lua)
- [render_target.lua](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/resources/data/computercraft/lua/rom/modules/main/teutonlib/render_target.lua)
- [computer-graphics.md](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/computer-graphics.md)

[Media provenance and verification scope](Bildnachweise-en)
