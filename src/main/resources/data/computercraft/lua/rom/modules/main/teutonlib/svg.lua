local targets = require("teutonlib.render_target")
local font = require("teutonlib.font")

local svg = {targets = targets, font = font, absolute = {}, relative = {}}
local styles = setmetatable({}, {__mode = "k"})

local COLOURS = {
    black=32768, navy=2048, blue=2048, green=8192, lime=32, teal=512, cyan=512,
    red=16384, maroon=128, purple=1024, magenta=4, orange=2, yellow=16,
    gray=128, grey=128, silver=256, white=1
}
local PALETTE = {
    {1,0.941,0.941,0.941},{2,0.949,0.698,0.2},{4,0.898,0.498,0.847},{8,0.6,0.698,0.949},
    {16,0.871,0.871,0.424},{32,0.498,0.8,0.098},{64,0.949,0.698,0.8},{128,0.298,0.298,0.298},
    {256,0.6,0.6,0.6},{512,0.298,0.6,0.698},{1024,0.698,0.4,0.898},{2048,0.2,0.4,0.8},
    {4096,0.498,0.4,0.298},{8192,0.341,0.651,0.306},{16384,0.8,0.298,0.298},{32768,0.067,0.067,0.067}
}

local VALID_COLOURS = {}
for _, entry in ipairs(PALETTE) do VALID_COLOURS[entry[1]] = true end

function svg.style(definitions)
    definitions = definitions or {}
    if type(definitions) ~= "table" then error("style definitions must be a table", 2) end
    local allowed = {uniformColor=true, backgroundColor=true, clearDraw=true, fit=true}
    for key in pairs(definitions) do
        if not allowed[key] then error("unknown SVG style field '" .. tostring(key) .. "'", 2) end
    end
    if definitions.uniformColor ~= nil and not VALID_COLOURS[definitions.uniformColor] then
        error("uniformColor must be one CC colour", 2)
    end
    if definitions.backgroundColor ~= nil and not VALID_COLOURS[definitions.backgroundColor] then
        error("backgroundColor must be one CC colour", 2)
    end
    if definitions.clearDraw ~= nil and type(definitions.clearDraw) ~= "boolean" then
        error("clearDraw must be a boolean", 2)
    end
    local fit = definitions.fit or "contain"
    if fit ~= "contain" and fit ~= "cover" and fit ~= "stretch" then
        error("fit must be 'contain', 'cover' or 'stretch'", 2)
    end
    local values = {
        uniformColor = definitions.uniformColor,
        backgroundColor = definitions.backgroundColor or (colors and colors.black) or 32768,
        clearDraw = definitions.clearDraw == true,
        fit = fit
    }
    local result = setmetatable({}, {
        __index = values,
        __newindex = function() error("SVG styles are immutable", 2) end,
        __metatable = "SVG style"
    })
    styles[result] = values
    return result
end

local defaultStyle = svg.style()

local function checkedStyle(value)
    if value == nil then return styles[defaultStyle] end
    if type(value) ~= "table" or not styles[value] then
        error("style must be created with svg.style", 3)
    end
    return styles[value]
end

local function trim(value) return (tostring(value or ""):gsub("^%s+", ""):gsub("%s+$", "")) end
local function number(value, fallback)
    local result = tonumber(tostring(value or ""):match("[-+]?[%d%.]+"))
    return result or fallback
end

local function colour(value, fallback)
    if value == nil then return fallback end
    value = trim(value):lower()
    if value == "none" or value == "transparent" then return nil end
    if COLOURS[value] then return COLOURS[value] end
    local short = value:match("^#(%x%x%x)$")
    local long = value:match("^#(%x%x%x%x%x%x)$")
    local r, g, b
    if short then
        r, g, b = tonumber(short:sub(1,1),16)/15, tonumber(short:sub(2,2),16)/15, tonumber(short:sub(3,3),16)/15
    elseif long then
        r, g, b = tonumber(long:sub(1,2),16)/255, tonumber(long:sub(3,4),16)/255, tonumber(long:sub(5,6),16)/255
    else
        local rr, gg, bb = value:match("^rgb%(%s*(%d+)%s*,%s*(%d+)%s*,%s*(%d+)%s*%)$")
        if rr then r, g, b = tonumber(rr)/255, tonumber(gg)/255, tonumber(bb)/255 end
    end
    if not r then return fallback end
    local best, distance = 1, math.huge
    for _, entry in ipairs(PALETTE) do
        local dr, dg, db = r-entry[2], g-entry[3], b-entry[4]
        local candidate = dr*dr + dg*dg + db*db
        if candidate < distance then best, distance = entry[1], candidate end
    end
    return best
end

local function attributes(source)
    local result = {}
    for key, quote, value in source:gmatch("([%w_:%-]+)%s*=%s*(['\"])(.-)%2") do result[key] = value end
    local style = result.style
    if style then
        for key, value in style:gmatch("([%w%-]+)%s*:%s*([^;]+)") do result[key] = trim(value) end
    end
    return result
end

local function copy(source)
    local result = {}
    for key, value in pairs(source or {}) do result[key] = value end
    return result
end

function svg.parse(source)
    if type(source) ~= "string" then error("SVG source string expected", 2) end
    source = source:gsub("<!%-%-.-%-%->", ""):gsub("<%?.-%?>", "")
    local document = {nodes = {}}
    local stack = {{style = {stroke = "none", ["stroke-width"] = "1"}}}
    local position = 1
    while true do
        local startAt, endAt, raw = source:find("<([^>]+)>", position)
        if not startAt then break end
        local content = source:sub(position, startAt - 1)
        local current = stack[#stack]
        if current and current.node and current.node.name == "text" then current.node.text = (current.node.text or "") .. content end
        position = endAt + 1
        raw = trim(raw)
        if raw:sub(1,1) == "/" then
            if #stack > 1 then table.remove(stack) end
        elseif raw:sub(1,1) ~= "!" then
            local selfClosing = raw:sub(-1) == "/"
            if selfClosing then raw = trim(raw:sub(1,-2)) end
            local name, rest = raw:match("^([%w:_%-]+)%s*(.*)$")
            name = name and name:lower()
            if name then
                local attr = attributes(rest)
                local inherited = copy(stack[#stack].style)
                for _, key in ipairs({"fill","stroke","stroke-width","font-size"}) do
                    if attr[key] ~= nil then inherited[key] = attr[key] end
                end
                local node = {name = name, attributes = attr, style = inherited}
                if name == "svg" and not document.root then document.root = node else document.nodes[#document.nodes + 1] = node end
                if not selfClosing then stack[#stack + 1] = {style = inherited, node = node} end
            end
        end
    end
    if not document.root then error("SVG root element is missing", 2) end
    return document
end

local function viewBoxOf(document)
    local attr = document.root.attributes
    local viewBox = attr.viewBox or attr.viewbox
    if viewBox then
        local values = {}
        for value in viewBox:gmatch("[-+]?[%d%.]+") do values[#values + 1] = tonumber(value) end
        if #values >= 4 then return values[1], values[2], values[3], values[4] end
    end
end

local function transformFor(document, viewport, fit)
    local attr = document.root.attributes
    local minX, minY, sourceWidth, sourceHeight = viewBoxOf(document)
    minX, minY = minX or 0, minY or 0
    sourceWidth = sourceWidth or number(attr.width, viewport.width)
    sourceHeight = sourceHeight or number(attr.height, viewport.height)
    if sourceWidth <= 0 or sourceHeight <= 0 then error("SVG dimensions must be positive", 2) end
    local sx, sy = viewport.width/sourceWidth, viewport.height/sourceHeight
    local ox, oy = 0, 0
    if fit ~= "stretch" then
        local scale = fit == "cover" and math.max(sx,sy) or math.min(sx,sy)
        sx, sy = scale, scale
        ox, oy = (viewport.width-sourceWidth*scale)/2, (viewport.height-sourceHeight*scale)/2
    end
    return function(x,y) return viewport.x+ox+(x-minX)*sx, viewport.y+oy+(y-minY)*sy end, sx, sy
end

local function localTransform(node)
    local tx, ty, sx, sy = 0, 0, 1, 1
    local value = node.attributes.transform or ""
    for name, args in value:gmatch("([%a]+)%s*%(([^)]*)%)") do
        local values = {}
        for item in args:gmatch("[-+]?[%d%.]+") do values[#values+1] = tonumber(item) end
        if name == "translate" then tx, ty = tx+(values[1] or 0), ty+(values[2] or 0)
        elseif name == "scale" then sx, sy = sx*(values[1] or 1), sy*(values[2] or values[1] or 1) end
    end
    return function(x,y) return x*sx+tx, y*sy+ty end
end

local function canvas(width, height, clip)
    local rows = {}
    for y=1,height do rows[y] = {} end
    local count = 0
    local function plot(x,y,c)
        x,y = math.floor(x+0.5), math.floor(y+0.5)
        if c and x>=1 and x<=width and y>=1 and y<=height and
            (not clip or x>=clip.x and x<clip.x+clip.width and y>=clip.y and y<clip.y+clip.height) then
            rows[y][x]=c; count=count+1
        end
    end
    return rows, plot, function() return count end
end

local function line(plot, x0,y0,x1,y1,c,width)
    local dx,dy=x1-x0,y1-y0
    local steps=math.max(1,math.ceil(math.max(math.abs(dx),math.abs(dy))))
    local radius=math.max(0,math.floor((width or 1)/2))
    for i=0,steps do
        local x,y=x0+dx*i/steps,y0+dy*i/steps
        for oy=-radius,radius do for ox=-radius,radius do plot(x+ox,y+oy,c) end end
    end
end

local function polygon(plot, points, fill, stroke, strokeWidth)
    if fill and #points >= 3 then
        local minY,maxY=math.huge,-math.huge
        for _,p in ipairs(points) do minY,maxY=math.min(minY,p[2]),math.max(maxY,p[2]) end
        for y=math.floor(minY),math.ceil(maxY) do
            local intersections={}
            for i=1,#points do
                local a,b=points[i],points[i%#points+1]
                if (a[2]<=y and b[2]>y) or (b[2]<=y and a[2]>y) then
                    intersections[#intersections+1]=a[1]+(y-a[2])*(b[1]-a[1])/(b[2]-a[2])
                end
            end
            table.sort(intersections)
            for i=1,#intersections-1,2 do for x=math.ceil(intersections[i]),math.floor(intersections[i+1]) do plot(x,y,fill) end end
        end
    end
    if stroke then for i=1,#points do local a,b=points[i],points[i%#points+1]; line(plot,a[1],a[2],b[1],b[2],stroke,strokeWidth) end end
end

local function pointList(value, map, localMap)
    local values, result = {}, {}
    for item in tostring(value or ""):gmatch("[-+]?[%d%.]+") do values[#values+1]=tonumber(item) end
    for i=1,#values-1,2 do local x,y=localMap(values[i],values[i+1]); x,y=map(x,y); result[#result+1]={x,y} end
    return result
end

local function pathPoints(value)
    local tokens={}
    for token in tostring(value or ""):gmatch("[MmLlHhVvZz]|[-+]?%d*%.?%d+") do tokens[#tokens+1]=token end
    local result,x,y,startX,startY={},0,0,0,0
    local command,index=nil,1
    while index<=#tokens do
        if tokens[index]:match("%a") then command=tokens[index]; index=index+1 end
        if command=="Z" or command=="z" then result[#result+1]={startX,startY}; x,y=startX,startY; command=nil
        elseif command=="M" or command=="L" or command=="m" or command=="l" then
            local nx,ny=tonumber(tokens[index]),tonumber(tokens[index+1]); if not ny then break end; index=index+2
            if command:match("%l") then nx,ny=x+nx,y+ny end
            x,y=nx,ny; if command=="M" or command=="m" then startX,startY=x,y; command=command=="M" and "L" or "l" end
            result[#result+1]={x,y}
        elseif command=="H" or command=="h" then local nx=tonumber(tokens[index]); index=index+1; x=command=="h" and x+nx or nx; result[#result+1]={x,y}
        elseif command=="V" or command=="v" then local ny=tonumber(tokens[index]); index=index+1; y=command=="v" and y+ny or ny; result[#result+1]={x,y}
        else index=index+1 end
    end
    return result
end

local function renderInto(target, document, viewport, style, fit, bounded)
    target=targets.validate(target)
    local width,height=target.getSize(); width,height=math.floor(width),math.floor(height)
    local rows,plot,pixelCount=canvas(width,height,viewport)
    local extraPixels=0
    local map,sx,sy=transformFor(document,viewport,fit)
    local defaultColour=style.uniformColor or 32768
    local rendered=0
    for _,node in ipairs(document.nodes) do
        local attr,style=node.attributes,node.style
        local fill=colour(style.fill,defaultColour); local stroke=colour(style.stroke,nil)
        local strokeWidth=math.max(1,number(style["stroke-width"],1)*math.max(math.abs(sx),math.abs(sy)))
        local lm=localTransform(node)
        local function p(x,y) x,y=lm(x,y); return map(x,y) end
        if node.name=="rect" then
            local x,y=p(number(attr.x,0),number(attr.y,0)); local x2,y2=p(number(attr.x,0)+number(attr.width,0),number(attr.y,0)+number(attr.height,0))
            polygon(plot,{{x,y},{x2,y},{x2,y2},{x,y2}},fill,stroke,strokeWidth); rendered=rendered+1
        elseif node.name=="line" then
            local x1,y1=p(number(attr.x1,0),number(attr.y1,0)); local x2,y2=p(number(attr.x2,0),number(attr.y2,0))
            line(plot,x1,y1,x2,y2,stroke or fill,strokeWidth); rendered=rendered+1
        elseif node.name=="polyline" or node.name=="polygon" then
            local points=pointList(attr.points,map,lm)
            if node.name=="polyline" then for i=1,#points-1 do line(plot,points[i][1],points[i][2],points[i+1][1],points[i+1][2],stroke or fill,strokeWidth) end
            else polygon(plot,points,fill,stroke,strokeWidth) end; rendered=rendered+1
        elseif node.name=="circle" or node.name=="ellipse" then
            local cx,cy=p(number(attr.cx,0),number(attr.cy,0)); local rx=number(attr.rx,number(attr.r,0))*math.abs(sx); local ry=number(attr.ry,number(attr.r,0))*math.abs(sy)
            for py=math.floor(cy-ry),math.ceil(cy+ry) do for px=math.floor(cx-rx),math.ceil(cx+rx) do
                local d=((px-cx)/(rx==0 and 1 or rx))^2+((py-cy)/(ry==0 and 1 or ry))^2
                if fill and d<=1 then plot(px,py,fill) end
                if stroke and d<=1.12 and d>=0.82 then plot(px,py,stroke) end
            end end; rendered=rendered+1
        elseif node.name=="path" then
            local points={}; for _,raw in ipairs(pathPoints(attr.d)) do local x,y=p(raw[1],raw[2]); points[#points+1]={x,y} end
            polygon(plot,points,fill,stroke,strokeWidth); rendered=rendered+1
        elseif node.name=="text" then
            local x,y=p(number(attr.x,0),number(attr.y,0)-number(style["font-size"],5))
            local scale=math.max(1,math.floor(number(style["font-size"],5)*math.min(math.abs(sx),math.abs(sy))/5+0.5))
            local textRows=font.rasterize(trim(node.text),math.floor(x+0.5),math.floor(y+0.5),{scale=scale,spacing=1,color=fill or defaultColour})
            for py,row in pairs(textRows) do
                if py>=1 and py<=height and py>=viewport.y and py<viewport.y+viewport.height then
                    for px,value in pairs(row) do
                        if px>=1 and px<=width and px>=viewport.x and px<viewport.x+viewport.width then
                            rows[py][px]=value; extraPixels=extraPixels+1
                        end
                    end
                end
            end
            rendered=rendered+1
        end
    end
    local presentation = {clear=not style.clearDraw,background=style.backgroundColor}
    if bounded then
        local left = math.max(1, viewport.x)
        local top = math.max(1, viewport.y)
        local right = math.min(width, viewport.x + viewport.width - 1)
        local bottom = math.min(height, viewport.y + viewport.height - 1)
        if right >= left and bottom >= top then
            presentation.bounds = {x=left,y=top,width=right-left+1,height=bottom-top+1}
            target.present(rows,presentation)
        end
    else
        target.present(rows,presentation)
    end
    return {width=viewport.width,height=viewport.height,elements=rendered,pixels=pixelCount()+extraPixels}
end

local function documentOf(source)
    return type(source)=="table" and source or svg.parse(source)
end

function svg.render(target, source, style)
    target=targets.validate(target)
    style=checkedStyle(style)
    local width,height=target.getSize(); width,height=math.floor(width),math.floor(height)
    return renderInto(target,documentOf(source),{x=1,y=1,width=width,height=height},style,style.fit,false)
end

local function vector(value, name, relative)
    if type(value) ~= "table" then error(name .. " must be a Vec2 table", 4) end
    local named = value.x ~= nil or value.y ~= nil
    local indexed = value[1] ~= nil or value[2] ~= nil
    if named == indexed then error(name .. " must use either {x=, y=} or {x, y}", 4) end
    local x,y = named and value.x or value[1], named and value.y or value[2]
    if type(x) ~= "number" or type(y) ~= "number" or x ~= x or y ~= y or
        x == math.huge or y == math.huge or x == -math.huge or y == -math.huge then
        error(name .. " components must be finite numbers", 4)
    end
    if relative then
        if x < 0 or x > 1 or y < 0 or y > 1 then error(name .. " components must be between 0 and 1", 4) end
    elseif x % 1 ~= 0 or y % 1 ~= 0 then
        error(name .. " components must be integers", 4)
    end
    return x,y
end

local function rounded(value) return math.floor(value + 0.5) end

local function naturalLength(value)
    if value == nil then return nil end
    local text = trim(value)
    local parsed = text:match("^([-+]?%d*%.?%d+)$") or text:match("^([-+]?%d*%.?%d+)%s*px$")
    return parsed and tonumber(parsed) or nil
end

local function naturalSize(document)
    local attr = document.root.attributes
    local width,height = naturalLength(attr.width),naturalLength(attr.height)
    local _,_,viewWidth,viewHeight = viewBoxOf(document)
    if width and not height and viewWidth and viewHeight and viewWidth ~= 0 then height=width*viewHeight/viewWidth end
    if height and not width and viewWidth and viewHeight and viewHeight ~= 0 then width=height*viewWidth/viewHeight end
    width,height = width or viewWidth,height or viewHeight
    if not width or not height or width <= 0 or height <= 0 then
        error("SVG natural size requires positive width/height or viewBox dimensions", 4)
    end
    width,height=rounded(width),rounded(height)
    if width < 1 or height < 1 then error("SVG natural size must cover at least one pixel", 4) end
    return width,height
end

local function drawPlaced(target, source, startX, startY, width, height, style, fit)
    if width < 1 or height < 1 then error("SVG dimensions must be positive", 4) end
    local document=documentOf(source)
    return renderInto(target,document,{x=startX+1,y=startY+1,width=width,height=height},checkedStyle(style),fit,true)
end

function svg.absolute.between(target, source, start, finish, style)
    local x,y=vector(start,"start",false)
    local endX,endY=vector(finish,"finish",false)
    if endX < x or endY < y then error("finish must not precede start",2) end
    return drawPlaced(target,source,x,y,endX-x+1,endY-y+1,style,"stretch")
end

function svg.absolute.sized(target, source, start, dimensions, style)
    local x,y=vector(start,"start",false)
    local width,height=vector(dimensions,"dimensions",false)
    return drawPlaced(target,source,x,y,width,height,style,"stretch")
end

function svg.absolute.at(target, source, start, style)
    local x,y=vector(start,"start",false)
    local document=documentOf(source)
    local width,height=naturalSize(document)
    return renderInto(target,document,{x=x+1,y=y+1,width=width,height=height},checkedStyle(style),"contain",true)
end

local function relativePosition(value, extent) return rounded(value*math.max(0,extent-1)) end
local function relativeDimension(value, extent) return rounded(value*extent) end

function svg.relative.between(target, source, start, finish, style)
    target=targets.validate(target)
    local targetWidth,targetHeight=target.getSize()
    local x,y=vector(start,"start",true)
    local endX,endY=vector(finish,"finish",true)
    x,y=relativePosition(x,targetWidth),relativePosition(y,targetHeight)
    endX,endY=relativePosition(endX,targetWidth),relativePosition(endY,targetHeight)
    if endX < x or endY < y then error("finish must not precede start",2) end
    return drawPlaced(target,source,x,y,endX-x+1,endY-y+1,style,"stretch")
end

function svg.relative.sized(target, source, start, dimensions, style)
    target=targets.validate(target)
    local targetWidth,targetHeight=target.getSize()
    local x,y=vector(start,"start",true)
    local width,height=vector(dimensions,"dimensions",true)
    x,y=relativePosition(x,targetWidth),relativePosition(y,targetHeight)
    width,height=relativeDimension(width,targetWidth),relativeDimension(height,targetHeight)
    return drawPlaced(target,source,x,y,width,height,style,"stretch")
end

function svg.relative.at(target, source, start, style)
    target=targets.validate(target)
    local targetWidth,targetHeight=target.getSize()
    local x,y=vector(start,"start",true)
    x,y=relativePosition(x,targetWidth),relativePosition(y,targetHeight)
    local document=documentOf(source)
    local width,height=naturalSize(document)
    return renderInto(target,document,{x=x+1,y=y+1,width=width,height=height},checkedStyle(style),"contain",true)
end

function svg.write(target, x, y, text, options) return font.write(target,x,y,text,options) end

return svg
