-- Load explicitly with require("teutonlib.peripheral_links").install() in custom OSes.
local expect = require("cc.expect").expect
local M = {}
function M.install()
    if not teutonlib_links or not peripheral then return false end
    if peripheral.__teutonlib_links then return true end
    local base, links = {}, teutonlib_links
    for k, v in pairs(peripheral) do base[k] = v end
    peripheral.__teutonlib_links = true
    function peripheral.getNames()
        local names, seen = base.getNames(), {}
        for _, name in ipairs(names) do seen[name] = true end
        for _, name in ipairs(links.getNames()) do
            if not seen[name] then names[#names + 1] = name; seen[name] = true end
        end
        return names
    end
    function peripheral.isPresent(name)
        expect(1, name, "string")
        return links.isPresent(name) or base.isPresent(name)
    end
    function peripheral.getType(value)
        if type(value) == "string" and links.isPresent(value) then return links.getType(value) end
        return base.getType(value)
    end
    function peripheral.hasType(value, wanted)
        expect(2, wanted, "string")
        if type(value) == "string" and links.isPresent(value) then
            for _, kind in ipairs({ links.getType(value) }) do if kind == wanted then return true end end
            return false
        end
        return base.hasType(value, wanted)
    end
    function peripheral.getMethods(name)
        expect(1, name, "string")
        if links.isPresent(name) then return links.getMethods(name) end
        return base.getMethods(name)
    end
    function peripheral.call(name, method, ...)
        expect(1, name, "string"); expect(2, method, "string")
        if links.isPresent(name) then return links.call(name, method, ...) end
        return base.call(name, method, ...)
    end
    function peripheral.wrap(name)
        expect(1, name, "string")
        if not links.isPresent(name) then return base.wrap(name) end
        -- Java supplies closures tied to this attachment, so old handles stay invalid.
        local wrapped, types = links.wrap(name), { links.getType(name) }
        for i = 1, #types do types[types[i]] = true end
        return setmetatable(wrapped, { __name = "peripheral", name = name, type = types[1], types = types })
    end
    function peripheral.find(kind, filter)
        expect(1, kind, "string"); expect(2, filter, "function", "nil")
        local results = {}
        for _, name in ipairs(peripheral.getNames()) do
            if peripheral.hasType(name, kind) then
                local wrapped = peripheral.wrap(name)
                if wrapped and (not filter or filter(name, wrapped)) then results[#results + 1] = wrapped end
            end
        end
        return table.unpack(results)
    end
    return true
end
return M
