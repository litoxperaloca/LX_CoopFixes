--[[
    LX_CoopFixes - los menus contextuales se quedan en la mitad del jugador.

    ISContextMenu.lua del juego (1237 lineas en B42.21) no tiene UNA SOLA
    referencia al viewport del jugador: mide y recorta todo contra la ventana
    ENTERA. En pantalla dividida eso rompe tres cosas:

      1. calcHeight() decide cuantas opciones entran y si hace falta scroll
         usando getScreenHeight(). Con division horizontal cada jugador tiene
         la mitad de alto, asi que un menu largo se dibuja con el alto de la
         ventana completa y se derrama en la pantalla del otro.
      2. El submenu se pasa a la izquierda solo si choca con el borde DERECHO
         DE LA VENTANA. Para el jugador 1 (mitad izquierda) eso nunca pasa, asi
         que sus submenus invaden la mitad del jugador 2 en lugar de plegarse.
      3. Nada reubica el menu si se abre cerca del borde de su mitad.

    Esto nos pega mas que a nadie: LX Split-Screen Quick Build genera menus
    largos y con submenus profundos, que es el peor caso posible.

    Fuera de pantalla dividida el viewport ES la ventana entera, asi que todo
    lo de aca se vuelve una operacion nula: quien juega solo no nota nada.

    No se reemplaza ninguna funcion del juego: se envuelven. Si algo falla,
    se cae al comportamiento original en vez de romper el menu.
]]

if ISContextMenu == nil then return end
if ISContextMenu.lxcfViewportPatched then return end
ISContextMenu.lxcfViewportPatched = true

local function log(msg) print("[LX_CoopFixes] " .. tostring(msg)) end

-- Mitad de pantalla de un jugador: left, top, width, height.
-- Ante cualquier duda devuelve la ventana entera, que es el comportamiento
-- original del juego.
local function viewport(n)
    local core = getCore()
    local sw, sh = core:getScreenWidth(), core:getScreenHeight()
    if type(n) ~= "number" or n < 0 or n > 3 then return 0, 0, sw, sh end
    if type(getPlayerScreenWidth) ~= "function" then return 0, 0, sw, sh end
    local okW, w = pcall(getPlayerScreenWidth, n)
    local okH, h = pcall(getPlayerScreenHeight, n)
    local okL, l = pcall(getPlayerScreenLeft, n)
    local okT, t = pcall(getPlayerScreenTop, n)
    if not (okW and okH and okL and okT) then return 0, 0, sw, sh end
    if type(w) ~= "number" or type(h) ~= "number" or w <= 0 or h <= 0 then
        return 0, 0, sw, sh
    end
    return l or 0, t or 0, w, h
end

LXCF = LXCF or {}
LXCF.contextViewport = viewport   -- lo reusan otros archivos del mod

-- Mete un rectangulo dentro de [lo, lo+span]. Si no entra, pega al borde lo.
local function fitInto(v, size, lo, span)
    local hi = lo + span - size
    if hi < lo then return lo end          -- mas grande que la mitad: al borde
    if v < lo then return lo end
    if v > hi then return hi end
    return v
end

-- =====================================================================
-- 1. El menu se mide contra la mitad del jugador, no contra la ventana
-- =====================================================================
-- Reimplementacion fiel de ISContextMenu:calcHeight() con una sola diferencia:
-- la altura disponible es la del viewport. Se agrega ademas una guarda que el
-- original no tiene: con una mitad muy baja, numVisibleItems podia dar 0 y
-- dejar un menu de alto cero.

local origCalcHeight = ISContextMenu.calcHeight

function ISContextMenu:calcHeight()
    local ok = pcall(function()
        local _, _, _, vh = viewport(self.player)
        local itemsHgt = (self.numOptions - 1) * self.itemHgt
        if itemsHgt + self.padTopBottom * 2 > vh then
            local usable = vh - self.padTopBottom * 2 - self.scrollIndicatorHgt * 2
            local numVisibleItems = math.floor(usable / self.itemHgt)
            if numVisibleItems < 1 then numVisibleItems = 1 end
            self.scrollAreaHeight = numVisibleItems * self.itemHgt
            self:setHeight(self.scrollAreaHeight + self.padTopBottom * 2 + self.scrollIndicatorHgt * 2)
            self:setScrollHeight(itemsHgt)
        else
            self.scrollAreaHeight = itemsHgt
            self:setHeight(itemsHgt + self.padTopBottom * 2)
            self:setScrollHeight(itemsHgt)
        end
    end)
    if not ok and origCalcHeight then origCalcHeight(self) end
end

-- =====================================================================
-- 2. El menu no se sale de su mitad
-- =====================================================================

local function clampIntoViewport(menu)
    local l, t, w, h = viewport(menu.player)
    local mw, mh = menu:getWidth(), menu:getHeight()
    if type(mw) ~= "number" or type(mh) ~= "number" then return end
    local x, y = menu:getX(), menu:getY()
    local nx, ny = fitInto(x, mw, l, w), fitInto(y, mh, t, h)
    if nx ~= x then menu:setX(nx) end
    if ny ~= y then menu:setY(ny) end
end

local origPrerender = ISContextMenu.prerender

function ISContextMenu:prerender()
    pcall(clampIntoViewport, self)
    if origPrerender then origPrerender(self) end
end

-- =====================================================================
-- 3. Los submenus se pliegan contra SU mitad, no contra la ventana
-- =====================================================================
-- El juego decide la X de cada submenu dentro de render(), o sea cada frame,
-- asi que la correccion tiene que ir DESPUES del render original. Solo se
-- toca un submenu que efectivamente se sale de la mitad del jugador: si ya
-- estaba bien, no se mueve nada.

local function foldSubmenus(menu)
    if type(menu.options) ~= "table" or type(menu.getSubMenu) ~= "function" then return end
    local l, _, w = viewport(menu.player)
    local right = l + w
    for _, opt in ipairs(menu.options) do
        if type(opt) == "table" and opt.subOption then
            local sub = menu:getSubMenu(opt.subOption)
            if sub and sub.isVisible and sub:isVisible() then
                local sw = sub:getWidth()
                local sx = sub:getX()
                if sx + sw > right then
                    -- no entra a la derecha: al otro lado del menu padre
                    sub:setX(menu:getX() - sw - 1)
                end
                if sub:getX() < l then
                    -- tampoco entra a la izquierda: pegado al borde de su mitad
                    sub:setX(l)
                end
            end
        end
    end
end

local origRender = ISContextMenu.render

function ISContextMenu:render()
    if origRender then origRender(self) end
    pcall(foldSubmenus, self)
end

if Events ~= nil and Events.OnGameStart ~= nil then
    Events.OnGameStart.Add(function()
        log("menus contextuales: recortados contra la mitad de cada jugador")
    end)
end
