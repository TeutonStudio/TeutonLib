local targets = {}

local function expectTable(value, name)
    if type(value) ~= "table" then error((name or "value") .. " must be a table", 3) end
    return value
end

local function sizeOf(handle)
    local width, height = handle.getSize()
    return assert(tonumber(width), "target width is unavailable"),
        assert(tonumber(height), "target height is unavailable")
end

local BLIT = {
    [1] = "0", [2] = "1", [4] = "2", [8] = "3", [16] = "4", [32] = "5",
    [64] = "6", [128] = "7", [256] = "8", [512] = "9", [1024] = "a",
    [2048] = "b", [4096] = "c", [8192] = "d", [16384] = "e", [32768] = "f"
}

local function blitCode(colour)
    if colors and colors.toBlit then return colors.toBlit(colour) end
    return BLIT[colour] or "f"
end

local function presentationBounds(options, width, height)
    local bounds = options and options.bounds
    if not bounds then return 1, 1, width, height, false end
    local x = math.max(1, math.floor(tonumber(bounds.x) or 1))
    local y = math.max(1, math.floor(tonumber(bounds.y) or 1))
    local right = math.min(width, x + math.max(0, math.floor(tonumber(bounds.width) or 0)) - 1)
    local bottom = math.min(height, y + math.max(0, math.floor(tonumber(bounds.height) or 0)) - 1)
    return x, y, right, bottom, true
end

local function terminalTarget(handle, kind)
    expectTable(handle, kind or "terminal")
    if type(handle.getSize) ~= "function" or type(handle.setCursorPos) ~= "function" then
        error((kind or "terminal") .. " does not provide the CraftOS terminal API", 3)
    end

    local target = {kind = kind or "terminal"}
    function target.getSize() return sizeOf(handle) end
    function target.present(rows, options)
        options = options or {}
        local width, height = target.getSize()
        local fallback = options.background or (colors and colors.black) or 32768
        local left, top, right, bottom, bounded = presentationBounds(options, width, height)
        if right < left or bottom < top then return width, height end
        if options.clear ~= false and not bounded and type(handle.setBackgroundColor) == "function" then
            handle.setBackgroundColor(fallback)
            handle.clear()
        end
        for y = top, bottom do
            local row = rows[y]
            if row or options.clear ~= false then
                if type(handle.blit) == "function" and options.clear ~= false then
                    local text, foreground, background = {}, {}, {}
                    for x = left, right do
                        local index = x - left + 1
                        local colour = row and row[x]
                        text[index] = " "
                        foreground[index] = blitCode(colour or fallback)
                        background[index] = blitCode(colour or fallback)
                    end
                    handle.setCursorPos(left, y)
                    handle.blit(table.concat(text), table.concat(foreground), table.concat(background))
                else
                    for x = left, right do
                        local colour = row and row[x]
                        if colour or options.clear ~= false then
                            handle.setCursorPos(x, y)
                            handle.setBackgroundColor(colour or fallback)
                            handle.write(" ")
                        end
                    end
                end
            end
        end
        return width, height
    end
    return target
end

function targets.terminal(handle)
    handle = handle or (term and term.current and term.current()) or term
    return terminalTarget(handle, "terminal")
end

function targets.monitor(monitor, textScale)
    if type(monitor) == "string" then
        if not peripheral then error("peripheral API is unavailable", 2) end
        monitor = peripheral.wrap(monitor)
    end
    if not monitor then error("monitor is unavailable", 2) end
    if textScale ~= nil then monitor.setTextScale(textScale) end
    return terminalTarget(monitor, "monitor")
end

function targets.display(desk, socket)
    expectTable(desk, "ControlDesk")
    if type(desk.getDisplaySize) ~= "function" or type(desk.setDisplayPixels) ~= "function" then
        error("ControlDesk does not provide the pixel display API", 2)
    end
    local target = {kind = "display", socket = socket}
    function target.getSize()
        local result = desk.getDisplaySize(socket)
        if type(result) == "table" then return result.width, result.height end
        return result
    end
    function target.present(rows, options)
        options = options or {}
        local width, height = target.getSize()
        local left, top, right, bottom, bounded = presentationBounds(options, width, height)
        if right < left or bottom < top then return width, height end
        if bounded or options.clear == false then
            local enabled, disabled = {}, {}
            if options.clear ~= false then
                for y = top, bottom do
                    for x = left, right do disabled[#disabled + 1] = {x = x, y = y} end
                end
            end
            for y = top, bottom do
                local row = rows[y]
                if row then
                    for x = left, right do
                        if row[x] then enabled[#enabled + 1] = {x = x, y = y} end
                    end
                end
            end
            if type(desk.setDisplayPixelBatch) == "function" then
                if #disabled > 0 then desk.setDisplayPixelBatch(socket, disabled, false) end
                if #enabled > 0 then desk.setDisplayPixelBatch(socket, enabled, true) end
            elseif type(desk.setDisplayPixel) == "function" then
                for _, point in ipairs(disabled) do desk.setDisplayPixel(socket, point.x, point.y, false) end
                for _, point in ipairs(enabled) do desk.setDisplayPixel(socket, point.x, point.y, true) end
            else
                error("ControlDesk does not provide partial pixel updates", 2)
            end
            return width, height
        end
        local packed = {}
        for y = 1, height do
            local source, chars = rows[y] or {}, {}
            for x = 1, width do chars[x] = source[x] and "1" or "0" end
            packed[y] = table.concat(chars)
        end
        desk.setDisplayPixels(socket, packed)
        return width, height
    end
    return target
end

function targets.event(event)
    local display = require("display")
    local desk, socket = display.resolve(event)
    if not desk then error("display source desk is unavailable", 2) end
    return targets.display(desk, socket)
end

function targets.custom(width, height, presenter)
    width, height = tonumber(width), tonumber(height)
    if not width or not height or width < 1 or height < 1 then error("positive target size expected", 2) end
    if type(presenter) ~= "function" then error("presenter function expected", 2) end
    local target = {kind = "custom"}
    function target.getSize() return math.floor(width), math.floor(height) end
    function target.present(rows, options) return presenter(rows, options) end
    return target
end

function targets.validate(target)
    expectTable(target, "render target")
    if type(target.getSize) ~= "function" or type(target.present) ~= "function" then
        error("render target must provide getSize() and present()", 3)
    end
    return target
end

return targets
