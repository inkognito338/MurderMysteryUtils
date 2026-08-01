-- ============================================================
--  AutoNext.lua 3
-- ============================================================

local DEBUG = false

local YUKI_SERVERS = {
    "dexland",
    "masedworld",
    "mineblaze",
    "cheatmine",
    "mineberry",
    "minepeak"
}

local JENRO_SERVERS = {
    "luckymc",
    "luckyworld", 
    "playmine",
    "musteryworld"
}

local function debugLog(msg)
    if DEBUG then
        api:log("[AutoNext DEBUG] " .. msg)
    end
end

local function isYukiServer(ip)
    if not ip or ip == "" then return false end
    local lowerIp = string.lower(ip)
    for _, server in ipairs(YUKI_SERVERS) do
        if string.find(lowerIp, server) then
            return true
        end
    end
    return false
end

local function isJenroServer(ip)
    if not ip or ip == "" then return false end
    local lowerIp = string.lower(ip)
    for _, server in ipairs(JENRO_SERVERS) do
        if string.find(lowerIp, server) then
            return true
        end
    end
    return false
end

function detectAutoNextStateFull(message, source, playerName, teamName, prefix, suffix, serverIP)
    debugLog("===== detectAutoNextStateFull START =====")
    debugLog("source: " .. tostring(source))
    debugLog("serverIP: " .. tostring(serverIP))
    
    if not message then
        debugLog("message is nil, returning nil")
        return { state = nil, command = nil, autoConfirm = true }
    end
    
    local text = message.text or ""
    local formatted = message.formatted or ""
    
    debugLog("text: '" .. text .. "'")
    debugLog("formatted: '" .. formatted .. "'")
    
    local defaultCommand = isJenroServer(serverIP) and "/random-arena" or "/next"
    debugLog("defaultCommand: " .. defaultCommand)
    
    if source == "chat" and isYukiServer(serverIP) then
        if formatted:find("MurderMystery ▸ Перезагрузка сервера через 10 секунд!") then
            debugLog("GAME_END detected in CHAT!")
            return { state = "GAME_END", command = defaultCommand, autoConfirm = false }
        end
    end
    
    if (source == "title" or source == "subtitle") and isJenroServer(serverIP) then
        if text:find("Победили мирные") or text:find("Победили мирные жители") then
            debugLog("GAME_END detected in TITLE!")
            return { state = "GAME_END", command = "/random-arena", autoConfirm = false }
        end
    end

    if (source == "title" or source == "subtitle") and isJenroServer(serverIP) then
        debugLog("Checking JENRO role detection...")
        
        if text:find("Мирный житель") or text:find("Мирный") or text:find("INNOCENT") then
            debugLog("INNOCENT detected on JENRO server!")
            return { state = "INNOCENT", command = defaultCommand, autoConfirm = true }
        end
        
        if text:find("Доктор") or text:find("Детектив") or text:find("DETECTIVE") then
            debugLog("DETECTIVE detected on JENRO server!")
            return { state = "DETECTIVE", command = defaultCommand, autoConfirm = true }
        end
        
        if text:find("Убийца") or text:find("MURDERER") then
            debugLog("MURDERER detected on JENRO server!")
            return { state = "MURDERER", command = defaultCommand, autoConfirm = true }
        end
    end
    
    if (source == "title" or source == "subtitle") and isYukiServer(serverIP) then
        if text:find("РОЛЬ: МИРНЫЙ ЖИТЕЛЬ") or text:find("ROLE: INNOCENT") then
            debugLog("INNOCENT detected on YUKI server!")
            return { state = "INNOCENT", command = defaultCommand, autoConfirm = true }
        end
        
        if text:find("РОЛЬ: ДЕТЕКТИВ") or text:find("ROLE: DETECTIVE") then
            debugLog("DETECTIVE detected on YUKI server!")
            return { state = "DETECTIVE", command = defaultCommand, autoConfirm = true }
        end
        
        if text:find("РОЛЬ: УБИЙЦА") or text:find("ROLE: MURDERER") then
            debugLog("MURDERER detected on YUKI server!")
            return { state = "MURDERER", command = defaultCommand, autoConfirm = true }
        end
    end
    
    if isJenroServer(serverIP) then
        if text:find("Вы погибли") or text:find("Вы погибли!") or text:find("Сожалеем об этом :(") then
            debugLog("DEATH detected on JENRO server!")
            return { state = "DEATH", command = defaultCommand, autoConfirm = false }
        end
    else
        if source == "chat" and (text:find("ВЫ ПОГИБЛИ") or text:find("YOU DIED")) then
            debugLog("DEATH detected!")
            return { state = "DEATH", command = defaultCommand, autoConfirm = true }
        end
    end
    
    debugLog("No state detected, returning nil")
    return { state = nil, command = nil, autoConfirm = true }
end

api:log("AutoNext script loaded")
