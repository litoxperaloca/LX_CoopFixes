--[[
    LX_CoopFixes - cargador generico de parches de texto sobre archivos Lua de OTROS mods.
    Lee el archivo original del mod (version instalada), aplica sustituciones exactas
    verificadas y lo ejecuta en su lugar. Si un texto no aparece (el mod se actualizo),
    esa sustitucion se omite y se avisa en console.txt; el original sigue funcionando.
]]

LXCF = LXCF or {}
LXCF.report = LXCF.report or {}

local function log(msg) print("[LX_CoopFixes] " .. tostring(msg)) end
LXCF.log = log

-- =====================================================================
-- Cargador de parches de texto
-- =====================================================================

local function readModFile(modId, rel)
    local ok, reader = pcall(getModFileReader, modId, rel, false)
    if not ok or not reader then return nil end
    local lines = {}
    local line = reader:readLine()
    while line ~= nil do
        lines[#lines + 1] = line
        line = reader:readLine()
    end
    reader:close()
    return table.concat(lines, "\n") .. "\n"
end

local function escapePattern(s)
    return (string.gsub(s, "[%^%$%(%)%%%.%[%]%*%+%-%?]", function(c) return "%" .. c end))
end

-- Reemplazo literal (sin patrones). Devuelve texto nuevo y cantidad de reemplazos.
local function plainReplace(src, find, repl)
    local pat = escapePattern(find)
    local parts, pos, count = {}, 1, 0
    while true do
        local s, e = string.find(src, pat, pos)
        if not s then break end
        parts[#parts + 1] = string.sub(src, pos, s - 1)
        parts[#parts + 1] = repl
        pos = e + 1
        count = count + 1
    end
    parts[#parts + 1] = string.sub(src, pos)
    return table.concat(parts), count
end

local function countOccurrences(src, find)
    local _, n = plainReplace(src, find, "")
    return n
end

LXCF._plainReplace = plainReplace -- expuesto para pruebas

-- rel: ruta dentro del mod Bandits (p.ej. "media/lua/client/BanditUpdate.lua")
-- patches: lista de { id=, find=, repl=, count= } (count = ocurrencias exactas esperadas)
function LXCF.LoadPatched(modId, rel, patches)
    if not getActivatedMods():contains(modId) then return false end
    local src = readModFile(modId, rel)
    if not src then
        log("ERROR: no pude leer " .. rel .. " de " .. modId .. " (archivo NO cargado)")
        return false
    end

    local applied, skipped = {}, {}
    local patched = src
    for _, p in ipairs(patches) do
        local expected = p.count or 1
        local n = countOccurrences(patched, p.find)
        if n == expected then
            patched = plainReplace(patched, p.find, p.repl)
            applied[#applied + 1] = p.id
        else
            skipped[#skipped + 1] = p.id .. " (encontrado " .. n .. ", esperado " .. expected .. ")"
        end
    end

    local chunk, err = loadstring(patched, "@" .. modId .. "/" .. rel)
    if not chunk then
        log("ERROR compilando " .. rel .. " parcheado: " .. tostring(err) .. " -> uso el ORIGINAL sin parches")
        chunk, err = loadstring(src, "@" .. modId .. "/" .. rel)
        applied = {}
        if not chunk then
            log("ERROR compilando original " .. rel .. ": " .. tostring(err))
            return false
        end
    end

    LXCF.report[modId .. "/" .. rel] = { applied = applied, skipped = skipped }
    if #skipped > 0 then
        log(rel .. ": " .. #applied .. " parches OK, OMITIDOS: " .. table.concat(skipped, "; "))
    else
        log(rel .. ": " .. #applied .. "/" .. #patches .. " parches OK")
    end

    if LXCF.DRY_RUN then return true end -- usado solo por el test offline
    chunk()
    return true
end
