--[[
    LX_CoopFixes - los dialogos largos se quedan en la mitad del jugador.

    Importante: B42.21 YA arregla esto en ISModalDialog y en ISTextBox, que
    centran usando getPlayerScreenLeft/Top. No hay nada que replicar ahi, y
    pisarlo seria empeorarlo.

    Queda un resto en ISModalRichText (el dialogo de texto largo con scroll:
    avisos del servidor, textos de mods). Ese SI centra bien al crearse, pero
    su update() vuelve a medirse cada frame contra la ventana entera:

        local maxHeight = getCore():getScreenHeight() - 40
        ...
        self:setY(20)

    O sea: crece hasta el alto de la VENTANA y, si se pasa, se pega a y=20,
    que es el borde superior de la ventana - la mitad del jugador 1. Con
    division horizontal, un dialogo largo del jugador 2 se le va a la pantalla
    del otro apenas crece.

    Aca se reimplementa update() tal cual, cambiando solo la altura disponible
    y el tope vertical por los de la mitad del jugador. Sin jugador asignado,
    o jugando solo, los valores son los de la ventana y no cambia nada.
]]

if ISModalRichText == nil then return end
if ISModalRichText.lxcfViewportPatched then return end
ISModalRichText.lxcfViewportPatched = true

-- el helper lo publica LXCF_ContextMenuViewport.lua, que carga antes (C < D)
local viewport = (LXCF and LXCF.contextViewport) or function()
    local core = getCore()
    return 0, 0, core:getScreenWidth(), core:getScreenHeight()
end

local origUpdate = ISModalRichText.update

function ISModalRichText:update()
    local ok = pcall(function()
        ISPanelJoypad.update(self)
        local btnHgt = math.max(25, FONT_HGT_SMALL + 3 * 2)
        local minHeight = self.chatText:getScrollHeight() + btnHgt + 10

        -- lo unico que cambia respecto del original: la mitad, no la ventana
        local _, vTop, _, vHgt = viewport(self.player)
        local maxHeight = vHgt - 40
        local topY = vTop + 20

        minHeight = math.min(minHeight, maxHeight)
        if self:getHeight() < minHeight then
            local dh = minHeight - self:getHeight()
            self:setHeight(minHeight)
            self:ignoreHeightChange()
            self:setY(math.max(self:getY() - dh / 2, topY))
            self:updateButtons()
        elseif self:getHeight() > maxHeight then
            self:setHeight(maxHeight)
            self:ignoreHeightChange()
            self:setY(topY)
            self:updateButtons()
        end
        self.chatText:setHeight(self.height - btnHgt - 10)
        self.chatText:updateScrollbars()
        if self.alwaysOnTop then
            self:bringToTop()
        end
    end)
    if not ok and origUpdate then origUpdate(self) end
end
