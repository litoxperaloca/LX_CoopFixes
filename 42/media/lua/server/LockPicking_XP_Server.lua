-- LX_CoopFixes: reemplaza LockPicking_XP_Server.lua de "[B42] Lock Picking Skill"
-- (Cf_LockpickingSkill) por un cargador que lee el ORIGINAL instalado y corrige la XP perdida:
-- con doXPBoost=true el juego busca la opcion de sandbox "MultiplierConfig.LockPicking",
-- que no existe para skills de mods -> NullPointerException y la XP no se suma.
require "LXCF_Patcher"

LXCF.LoadPatched("Cf_LockpickingSkill", "media/lua/server/LockPicking_XP_Server.lua", {
    {
        id = "xp_lockpicking_sin_crash",
        count = 1,
        find = [==[
xp:AddXP(
    Perks.LockPicking,
    amount,
    false,
    true,
    true
)]==],
        repl = [==[
do
    local lxcfSo = getSandboxOptions and getSandboxOptions()
    local lxcfHasOpt = lxcfSo and lxcfSo:getOptionByName("MultiplierConfig." .. tostring(Perks.LockPicking)) ~= nil
    local lxcfOk = false
    if lxcfHasOpt then
        lxcfOk = pcall(function() xp:AddXP(Perks.LockPicking, amount, false, true, true) end)
    end
    if not lxcfOk then
        -- sin la opcion de sandbox: XP sin multiplicadores de rasgos (antes se perdia toda)
        xp:AddXP(Perks.LockPicking, amount, false, false, true)
    end
end]==],
    },
})
