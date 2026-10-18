local channels = require("teutonlib.channels")
local args = { ... }
if #args > 2 or (#args > 0 and (not tonumber(args[1]) or tonumber(args[1]) % 1 ~= 0)) then
    print("Usage: channels [computerId [name]]")
    return
end
local id = tonumber(args[1])
if args[2] then
    print(channels.read(id, args[2]))
else
    local entries, privateCount
    if id then entries, privateCount = channels.list(id) else entries, privateCount = channels.list() end
    for _, channel in ipairs(entries) do
        print(('%s = %d (%s, %s)'):format(channel.name, channel.value, channel.direction, channel.public and "public" or "private"))
    end
    if id then print(('%d private channels not shown'):format(privateCount)) end
end
