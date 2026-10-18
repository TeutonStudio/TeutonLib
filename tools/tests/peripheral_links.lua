local module = assert(arg[1], 'module path required')
package.preload['cc.expect'] = function()
    return { expect = function(index, value, ...)
        for _, kind in ipairs({...}) do if type(value) == kind then return value end end
        error('bad argument #'..index)
    end }
end
local generation, present, calls = 1, true, 0
local remote = 'cc_aeroworks:link_fixture'
teutonlib_links = {
    getNames = function() return present and {remote} or {} end,
    isPresent = function(name) return present and name == remote end,
    getType = function() return 'inventory', 'energy_storage' end,
    getMethods = function() return {'size', 'echo'} end,
    call = function(name, method, ...) calls=calls+1; return ... end,
    wrap = function()
        local epoch = generation
        return {size=function() assert(present and epoch==generation, 'detached'); return 27 end}
    end,
}
local localCalls = 0
peripheral = {
    getNames = function() return {'left', 'barrel_0'} end,
    isPresent = function(name) return name=='left' or name=='barrel_0' end,
    getType = function(value)
        if type(value)=='table' then return table.unpack(getmetatable(value).types) end
        return value=='left' and 'modem' or 'inventory'
    end,
    hasType = function(value, kind)
        if type(value)=='table' then return getmetatable(value).types[kind] ~= nil end
        return (value=='left' and kind=='modem') or (value=='barrel_0' and kind=='inventory')
    end,
    getMethods = function() return {'size'} end,
    call = function(...) localCalls=localCalls+1; return ... end,
    wrap = function(name) return {localDevice=name} end,
}
local m = assert(loadfile(module))()
assert(m.install()); local first = peripheral.getNames; assert(m.install()); assert(first==peripheral.getNames)
assert(#peripheral.getNames()==3)
assert(peripheral.isPresent('left') and peripheral.isPresent(remote))
local wrapped = assert(peripheral.wrap(remote))
assert(wrapped.size()==27)
assert(peripheral.getType(wrapped)=='inventory' and peripheral.hasType(wrapped,'energy_storage'))
assert(getmetatable(wrapped).name==remote)
assert(select('#',peripheral.getType(remote))==2)
assert(peripheral.hasType(remote,'energy_storage') and not peripheral.hasType(remote,'monitor'))
assert(peripheral.wrap('left').localDevice=='left')
local result=table.pack(peripheral.call(remote,'echo', 1, nil, false, 0.123456789012345))
assert(result.n==4 and result[1]==1 and result[2]==nil and result[3]==false and result[4]==0.123456789012345)
peripheral.call('left','open',5); assert(localCalls==1 and calls==1)
assert(select('#',peripheral.find('inventory'))==2)
assert(select('#',peripheral.find('inventory',function(name) return name==remote end))==1)
present=false; assert(not pcall(wrapped.size)); assert(#peripheral.getNames()==2)
present=true; generation=2; assert(not pcall(wrapped.size)); assert(peripheral.wrap(remote).size()==27)
assert(not pcall(peripheral.isPresent,{}))
print('PASS: Lua decorator, coexistence, multi-return precision, filters, stale handles, idempotence')
