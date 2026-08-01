-- chatignore.lua - Вся логика здесь
-- Приоритетные ники
-- 123
local priority = {
    "48799464",
    "SKV_inkognito338"
}

-- Серверы, на которых работает фильтр игнора
local ALLOWED_SERVERS = {
    "dexland",
    "masedworld",
    "mineblaze",
    "cheatmine",
    "mineberry",
    "minepeak"
}

local function isAllowedServer(ip)
    if not ip or ip == "" then return false end
    local lowerIp = string.lower(ip)
    for _, server in ipairs(ALLOWED_SERVERS) do
        if string.find(lowerIp, server) then
            return true
        end
    end
    return false
end

function processChatMessage(json, serverIP)
    -- Работаем только на разрешённых серверах
    if not isAllowedServer(serverIP) then
        return false
    end

    local name = nil
    
    -- Ищем suggest_command
    local start = string.find(json, '"suggest_command"')
    if start then
        local value = string.find(json, '"value"', start)
        if value then
            local q1 = string.find(json, '"', value + 8)
            if q1 then
                local q2 = string.find(json, '"', q1 + 1)
                if q2 then
                    name = string.sub(json, q1 + 1, q2 - 1)
                    name = string.gsub(name, "^%s*(.-)%s*$", "%1")
                end
            end
        end
    end
    
    if not name then
        return false
    end
    
    -- Проверяем приоритет
    for _, p in ipairs(priority) do
        if string.lower(p) == string.lower(name) then
            return false
        end
    end
    
    -- Проверяем игнор через API
    return api.isPlayerIgnored(name)
end
api:log("[ChatIgnore] Script loaded")
