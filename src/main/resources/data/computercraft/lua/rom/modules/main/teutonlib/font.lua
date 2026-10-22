local targets = require("teutonlib.render_target")

local font = {targets = targets}

-- Compact 3x5 font. Unknown characters intentionally become a framed question mark.
local GLYPHS = {
    [" "]={"000","000","000","000","000"}, ["?"]={"110","001","010","000","010"},
    ["!"]={"010","010","010","000","010"}, ["."]={"000","000","000","000","010"},
    [","]={"000","000","000","010","100"}, [":"]={"000","010","000","010","000"},
    ["-"]={"000","000","111","000","000"}, ["+"]={"000","010","111","010","000"},
    ["/"]={"001","001","010","100","100"}, ["_"]={"000","000","000","000","111"},
    ["0"]={"111","101","101","101","111"}, ["1"]={"010","110","010","010","111"},
    ["2"]={"110","001","111","100","111"}, ["3"]={"110","001","011","001","110"},
    ["4"]={"101","101","111","001","001"}, ["5"]={"111","100","110","001","110"},
    ["6"]={"011","100","111","101","111"}, ["7"]={"111","001","010","010","010"},
    ["8"]={"111","101","111","101","111"}, ["9"]={"111","101","111","001","110"},
    A={"010","101","111","101","101"}, B={"110","101","110","101","110"},
    C={"011","100","100","100","011"}, D={"110","101","101","101","110"},
    E={"111","100","110","100","111"}, F={"111","100","110","100","100"},
    G={"011","100","101","101","011"}, H={"101","101","111","101","101"},
    I={"111","010","010","010","111"}, J={"001","001","001","101","010"},
    K={"101","101","110","101","101"}, L={"100","100","100","100","111"},
    M={"101","111","111","101","101"}, N={"101","111","111","111","101"},
    O={"010","101","101","101","010"}, P={"110","101","110","100","100"},
    Q={"010","101","101","111","011"}, R={"110","101","110","101","101"},
    S={"011","100","010","001","110"}, T={"111","010","010","010","010"},
    U={"101","101","101","101","111"}, V={"101","101","101","101","010"},
    W={"101","101","111","111","101"}, X={"101","101","010","101","101"},
    Y={"101","101","010","010","010"}, Z={"111","001","010","100","111"}
}

local function settings(options)
    options = options or {}
    return math.max(1, math.floor(tonumber(options.scale) or 1)),
        math.max(0, math.floor(tonumber(options.spacing) or 1)), options
end

function font.measure(text, options)
    local scale, spacing = settings(options)
    text = tostring(text or "")
    local longest, lines = 0, 1
    for line in (text .. "\n"):gmatch("(.-)\n") do
        longest = math.max(longest, #line)
        if line ~= text then lines = lines + 1 end
    end
    lines = select(2, text:gsub("\n", "")) + 1
    return math.max(0, longest * (3 * scale + spacing) - spacing), lines * 5 * scale + (lines - 1) * scale
end

function font.rasterize(text, x, y, options, rows)
    local scale, spacing, opts = settings(options)
    local colour = opts.color or opts.colour or (colors and colors.white) or 1
    local originX, cursorX, cursorY = math.floor(tonumber(x) or 1), math.floor(tonumber(x) or 1), math.floor(tonumber(y) or 1)
    rows = rows or {}
    local pixels = 0
    text = tostring(text or "")
    for index = 1, #text do
        local character = text:sub(index, index)
        if character == "\n" then
            cursorX, cursorY = originX, cursorY + 6 * scale
        else
            local glyph = GLYPHS[character:upper()] or GLYPHS["?"]
            for gy = 1, 5 do
                for gx = 1, 3 do
                    if glyph[gy]:sub(gx, gx) == "1" then
                        for sy = 0, scale - 1 do
                            local py = cursorY + (gy - 1) * scale + sy
                            rows[py] = rows[py] or {}
                            for sx = 0, scale - 1 do
                                rows[py][cursorX + (gx - 1) * scale + sx] = colour
                                pixels = pixels + 1
                            end
                        end
                    end
                end
            end
            cursorX = cursorX + 3 * scale + spacing
        end
    end
    return rows, pixels
end

function font.write(target, x, y, text, options)
    target = targets.validate(target)
    options = options or {}
    local rows, pixels = font.rasterize(text, x, y, options)
    target.present(rows, {clear = options.clear == true, background = options.background})
    local width, height = font.measure(text, options)
    return {width = width, height = height, pixels = pixels}
end

return font
