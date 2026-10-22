package.path = "src/main/resources/data/computercraft/lua/rom/modules/main/?.lua;" .. package.path

colors = {
    white = 1, orange = 2, magenta = 4, lightBlue = 8, yellow = 16, lime = 32,
    pink = 64, gray = 128, lightGray = 256, cyan = 512, purple = 1024,
    blue = 2048, brown = 4096, green = 8192, red = 16384, black = 32768
}

local svg = require("teutonlib.svg")
local font = require("teutonlib.font")

local presented, presentationOptions
local custom = svg.targets.custom(20, 10, function(rows, options)
    presented, presentationOptions = rows, options
end)
local result = svg.render(custom, [[
<svg viewBox="0 0 20 10">
  <rect x="1" y="1" width="5" height="3" fill="white"/>
  <line x1="0" y1="9" x2="19" y2="9" stroke="#ff0000"/>
  <circle cx="12" cy="3" r="2" fill="lime"/>
  <text x="8" y="9" font-size="5" fill="white">A</text>
</svg>]])
assert(result.width == 20 and result.height == 10 and result.elements == 4)
assert(presented[2][2] == colors.white, "rectangle must be rasterized")
assert(presented[10][1] == colors.red, "stroke colour must use the CC palette")
assert(presentationOptions.clear == true, "SVG rendering must replace the frame by default")

local deskRows, deskBatch
local desk = {
    getDisplaySize = function(socket)
        assert(socket == "big")
        return {width = 8, height = 5}
    end,
    setDisplayPixels = function(socket, rows) deskRows = rows end,
    setDisplayPixelBatch = function(socket, points, enabled)
        assert(socket == "big" and enabled == true)
        deskBatch = points
    end
}
local displayTarget = svg.targets.display(desk, "big")
svg.render(displayTarget, '<svg width="8" height="5"><rect width="2" height="2"/></svg>')
assert(#deskRows == 5 and #deskRows[1] == 8, "display target must write a monochrome raster")
local metrics = svg.write(displayTarget, 1, 1, "A", {clear = false})
assert(metrics.width == 3 and metrics.height == 5 and #deskBatch > 0,
    "font write must use a non-destructive display pixel batch")

local terminal = {width = 6, height = 4, writes = {}}
function terminal.getSize() return terminal.width, terminal.height end
function terminal.setCursorPos(x, y) terminal.x, terminal.y = x, y end
function terminal.setBackgroundColor(value) terminal.background = value end
function terminal.clear() terminal.cleared = true end
function terminal.write(value) terminal.writes[#terminal.writes + 1] = {terminal.x, terminal.y, value, terminal.background} end
local terminalTarget = svg.targets.terminal(terminal)
svg.write(terminalTarget, 2, 1, "!", {color = colors.yellow})
assert(not terminal.cleared and #terminal.writes > 0, "font write must overlay terminals by default")

local width, height = font.measure("AB\nC", {scale = 2})
assert(width == 13 and height == 22, "font metrics must include scale, spacing and line height")

local calls = {}
local placed = svg.targets.custom(10, 8, function(rows, options)
    calls[#calls + 1] = {rows = rows, options = options}
end)
local solid = '<svg viewBox="0 0 2 2"><rect width="2" height="2" fill="red"/></svg>'
local placedResult = svg.absolute.between(placed, solid, {x = 2, y = 1}, {5, 3})
assert(placedResult.width == 4 and placedResult.height == 3, "inclusive absolute bounds must determine size")
local placedCall = calls[#calls]
assert(placedCall.options.bounds.x == 3 and placedCall.options.bounds.y == 2 and
    placedCall.options.bounds.width == 4 and placedCall.options.bounds.height == 3,
    "absolute coordinates must be converted from public zero-based to target coordinates")
assert(placedCall.options.clear == true, "placed SVG must replace its bounds by default")

terminal.writes, terminal.cleared = {}, false
svg.absolute.sized(terminalTarget, solid, {1, 1}, {2, 2})
assert(not terminal.cleared and #terminal.writes == 4,
    "bounded terminal rendering must replace only the selected cells")
for _, write in ipairs(terminal.writes) do
    assert(write[1] >= 2 and write[1] <= 3 and write[2] >= 2 and write[2] <= 3,
        "bounded terminal rendering must preserve cells outside its bounds")
end

local overlay = svg.style {clearDraw = true, uniformColor = colors.yellow}
svg.absolute.sized(placed, '<svg width="2" height="2"><rect width="2" height="2"/></svg>',
    {-2, -1}, {4, 3}, overlay)
local clipped = calls[#calls]
assert(clipped.options.clear == false, "clearDraw must preserve pixels below transparent SVG areas")
assert(clipped.options.bounds.x == 1 and clipped.options.bounds.y == 1 and
    clipped.options.bounds.width == 2 and clipped.options.bounds.height == 2,
    "valid absolute bounds must clip at the target edge")
local foundFallback = false
for _, row in pairs(clipped.rows) do
    for _, value in pairs(row) do if value == colors.yellow then foundFallback = true end end
end
assert(foundFallback, "uniformColor must colour elements which do not define a colour")

svg.relative.sized(placed, solid, {0.5, 0.5}, {x = 0.5, y = 0.5})
local relative = calls[#calls]
assert(relative.options.bounds.x == 6 and relative.options.bounds.y == 5 and
    relative.options.bounds.width == 5 and relative.options.bounds.height == 4,
    "relative positions and dimensions must round against target resolution")
svg.relative.between(placed, solid, {0, 0}, {1, 1})
local relativeFull = calls[#calls]
assert(relativeFull.options.bounds.x == 1 and relativeFull.options.bounds.y == 1 and
    relativeFull.options.bounds.width == 10 and relativeFull.options.bounds.height == 8,
    "relative endpoints zero and one must address the complete target")
local natural = svg.absolute.at(placed,
    '<svg width="3" viewBox="0 0 6 4"><rect width="6" height="4"/></svg>', {1, 2})
assert(natural.width == 3 and natural.height == 2, "missing natural dimension must derive from viewBox ratio")

local explicit = nil
svg.absolute.sized(svg.targets.custom(4, 2, function(rows) explicit = rows end),
    '<svg viewBox="0 0 4 2"><rect width="2" height="2" fill="blue"/><rect x="2" width="2" height="2"/></svg>',
    {0, 0}, {4, 2}, svg.style {uniformColor = colors.orange})
assert(explicit[1][1] == colors.blue and explicit[1][4] == colors.orange,
    "uniformColor must be a fallback and preserve explicit SVG colours")

local function mustFail(label, callback)
    local ok = pcall(callback)
    assert(not ok, label)
end
mustFail("raw style tables must be rejected", function() svg.render(placed, solid, {fit = "stretch"}) end)
mustFail("mixed Vec2 forms must be rejected", function()
    svg.absolute.at(placed, solid, {x = 1, y = 1, [1] = 1})
end)
mustFail("relative values above one must be rejected", function()
    svg.relative.at(placed, solid, {1.1, 0})
end)
mustFail("zero dimensions must be rejected", function()
    svg.relative.sized(placed, solid, {0, 0}, {0, 0.5})
end)

local partialBatches = {}
local partialDesk = {
    getDisplaySize = function() return {width = 6, height = 4} end,
    setDisplayPixels = function() error("partial draw must not replace the full display") end,
    setDisplayPixelBatch = function(_, points, enabled)
        partialBatches[#partialBatches + 1] = {points = points, enabled = enabled}
    end
}
svg.absolute.sized(svg.targets.display(partialDesk, "small"), solid, {1, 1}, {3, 2})
assert(#partialBatches == 2 and partialBatches[1].enabled == false and partialBatches[2].enabled == true,
    "Aeroworks partial replacement must clear then enable pixels in batches")
assert(#partialBatches[1].points == 6, "Aeroworks clear batch must be limited to placed bounds")

print("TeutonLib SVG renderer and bitmap font helpers passed")
