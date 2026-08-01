-- ============================================================
--  MurderMysteryUtils API.lua
--  Управление табом2
-- ============================================================

-- ====== ПОЛЬЗОВАТЕЛИ ======
local users = {
    ["inkognito338"] = {
        color = "&3",
        prefix = "&3",
        suffix = "",
        servers = {"*"},
        modules = { FakeGM1 = false, AutoNext = 5 }
    },
    ["ruinquie"] = {
        color = "&6",
        prefix = "&6",
        suffix = "",
        servers = {"*"},
        modules = { FakeGM1 = false, AutoNext = 10, MurderAlert = 35 }
    },
    ["test:enhjdXJzZWRfenhj"] = {
        color = "&b",
        prefix = "&b",
        suffix = "",
        servers = {"*"},
        optimization = 2,
        modules = { FakeGM1 = false, AutoNext = 10, MurderAlert = 35 }
    },
    ["zxcursed1234571"] = {
        color = "&e",
        prefix = "&e",
        suffix = "",
        servers = {"dexland", "masedworld", "mineblaze", "cheatmine", "mineberry", "minepeak"},
        optimization = 1,
        modules = { FakeGM1 = false, AutoNext = 10, ESP = true, NameTags = true }
    }
}

-- ====== НАСТРОЙКИ СЕРВЕРОВ ======
local serverConfig = {
    ["masedworld"] = {
        teams = {
            ["1_default"] = { color = "&a", prefix = "&a", suffix = "" }
        },
        prefixRules = {
            { from = "&7", to = "&a", teams = {"1_default"} }
        },
        suffixRules = {
            { from = "&7", to = "&a", teams = {"1_default"} }
        }
    },
    ["dexland"] = {
        teams = {
            ["1_default"] = { color = "&a", prefix = "&a", suffix = "" }
        },
        prefixRules = {
            { from = "&7", to = "&a", teams = {"1_default"} }
        },
        suffixRules = {
            { from = "&7", to = "&a", teams = {"1_default"} }
        }
    },
    ["mineblaze"] = {
        teams = {
            ["1_default"] = { color = "&a", prefix = "&a", suffix = "" }
        },
        prefixRules = {
            { from = "&7", to = "&a", teams = {"1_default"} }
        },
        suffixRules = {
            { from = "&7", to = "&a", teams = {"1_default"} }
        }
    },
    ["cheatmine"] = {
        teams = {
            ["1_default"] = { color = "&a", prefix = "&a", suffix = "" }
        },
        prefixRules = {
            { from = "&7", to = "&a", teams = {"1_default"} }
        },
        suffixRules = {
            { from = "&7", to = "&a", teams = {"1_default"} }
        }
    },
    ["mineberry"] = {
        teams = {
            ["1_default"] = { color = "&a", prefix = "&a", suffix = "" }
        },
        prefixRules = {
            { from = "&7", to = "&a", teams = {"1_default"} }
        },
        suffixRules = {
            { from = "&7", to = "&a", teams = {"1_default"} }
        }
    },
    ["minepeak"] = {
        teams = {
            ["1_default"] = { color = "&a", prefix = "&a", suffix = "" }
        },
        prefixRules = {
            { from = "&7", to = "&a", teams = {"1_default"} }
        },
        suffixRules = {
            { from = "&7", to = "&a", teams = {"1_default"} }
        }
    },
    ["default"] = {
        teams = {},
        teamPatterns = {},
        prefixRules = {},
        suffixRules = {}
    }
}

-- ====== ГЛОБАЛЬНЫЕ ПРАВИЛА ======
local globalTeamColors = {}
local nameRules = {}

-- Команда, при которой разрешена замена цвета/префикса/суффикса для пользователей из "users".
-- Если у игрока team !== REQUIRED_TEAM - кастомный цвет/префикс/суффикс из "users" применяться не будет.
local REQUIRED_TEAM = "1_default"

-- ====== ОТЛАДКА ======
local JS_DEBUG_TEAM = false

-- Безопасный лог: работает и через api.log, и через print, если api ещё не готов
local function dbg(msg)
    if not JS_DEBUG_TEAM then
        return
    end
    if api and api.log then
        api.log(msg)
    else
        print(msg)
    end
end

-- ====== ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ======

-- luaj = Lua 5.1, string.trim не входит в стандартную библиотеку — своя реализация
local function trim(s)
    if not s then
        return s
    end
    return (string.gsub(s, "^%s*(.-)%s*$", "%1"))
end

local function detectServer(ip)
    if not ip or ip == "" then
        return "default"
    end
    
    local lowerIp = string.lower(ip)
    lowerIp = string.gsub(lowerIp, ":%d+$", "")
    
    if string.find(lowerIp, "masedworld") then return "masedworld" end
    if string.find(lowerIp, "dexland") then return "dexland" end
    if string.find(lowerIp, "mineblaze") then return "mineblaze" end
    if string.find(lowerIp, "cheatmine") then return "cheatmine" end
    if string.find(lowerIp, "mineberry") then return "mineberry" end
    if string.find(lowerIp, "minepeak") then return "minepeak" end
    
    local parts = {}
    for part in string.gmatch(lowerIp, "[^.]+") do
        table.insert(parts, part)
    end
    
    if #parts >= 2 then
        return parts[#parts - 1]
    end
    
    return lowerIp
end

local function getServerSettings(ip)
    local serverName = detectServer(ip)
    
    if serverConfig[serverName] then
        return serverConfig[serverName]
    end
    
    local lowerIp = string.lower(ip)
    for key, _ in pairs(serverConfig) do
        if key ~= "default" then
            if string.find(lowerIp, key) then
                return serverConfig[key]
            end
        end
    end
    
    return serverConfig["default"] or {}
end

local function matchServer(servers, ip)
    if not servers then
        dbg("[matchServer] servers table is nil -> false")
        return false
    end

    for _, server in ipairs(servers) do
        if server == "*" then
            return true
        end
    end

    if not ip or ip == "" then
        dbg("[matchServer] ip is nil/empty -> false")
        return false
    end

    local lowerIp = string.lower(ip)
    for _, server in ipairs(servers) do
        if string.find(lowerIp, string.lower(server)) then
            return true
        end
    end

    dbg("[matchServer] no match: ip='" .. tostring(ip) .. "' servers=[" .. table.concat(servers, ",") .. "]")
    return false
end

local function replaceColor(text, from, to)
    if not text then
        return text
    end
    
    local fromFixed = string.gsub(from, "&", "§")
    local toFixed = string.gsub(to, "&", "§")
    
    return string.gsub(text, fromFixed, toFixed)
end

-- Сравнение имени команды без учёта регистра и краевых пробелов.
local function isRequiredTeam(team)
    if not team then
        return false
    end
    return string.lower(trim(tostring(team))) == string.lower(REQUIRED_TEAM)
end

-- ====== ГЛАВНЫЕ ФУНКЦИИ ======

function getPrefix(name, team, originalPrefix, ip)
    local n = string.lower(name)
    local settings = getServerSettings(ip)
    
    local u = users[n]
    if u and u.prefix ~= nil and matchServer(u.servers, ip) then
        if not isRequiredTeam(team) then
            return originalPrefix
        end
        return u.prefix
    end
    
    if team and settings.teams and settings.teams[team] then
        local ts = settings.teams[team]
        if ts.prefix ~= nil then
            return ts.prefix
        end
    end
    
    if settings.prefixRules and originalPrefix then
        for _, rule in ipairs(settings.prefixRules) do
            local shouldApply = true
            if rule.teams then
                local teamFound = false
                for _, t in ipairs(rule.teams) do
                    if t == team then
                        teamFound = true
                        break
                    end
                end
                shouldApply = teamFound
            end
            if shouldApply then
                return replaceColor(originalPrefix, rule.from, rule.to)
            end
        end
    end
    
    if settings.teamPatterns and team then
        for _, p in ipairs(settings.teamPatterns) do
            if p.pattern and p.pattern:match(team) then
                return p.prefix ~= nil and p.prefix or originalPrefix
            end
        end
    end
    
    return nil
end

function getSuffix(name, team, originalSuffix, ip)
    local n = string.lower(name)
    local settings = getServerSettings(ip)
    
    local u = users[n]
    if u and u.suffix ~= nil and matchServer(u.servers, ip) then
        if not isRequiredTeam(team) then
            return originalSuffix
        end
        return u.suffix
    end
    
    if team and settings.teams and settings.teams[team] then
        local ts = settings.teams[team]
        if ts.suffix ~= nil then
            return ts.suffix
        end
    end
    
    if settings.suffixRules and originalSuffix then
        for _, rule in ipairs(settings.suffixRules) do
            local shouldApply = true
            if rule.teams then
                local teamFound = false
                for _, t in ipairs(rule.teams) do
                    if t == team then
                        teamFound = true
                        break
                    end
                end
                shouldApply = teamFound
            end
            if shouldApply then
                return replaceColor(originalSuffix, rule.from, rule.to)
            end
        end
    end
    
    if settings.teamPatterns and team then
        for _, p in ipairs(settings.teamPatterns) do
            if p.pattern and p.pattern:match(team) then
                return p.suffix ~= nil and p.suffix or originalSuffix
            end
        end
    end
    
    return nil
end

function getNameColor(name, team, prefix, suffix, ip)
    local n = string.lower(name)
    local settings = getServerSettings(ip)
    
    local u = users[n]
    if u and u.color and matchServer(u.servers, ip) then
        if isRequiredTeam(team) then
            return u.color
        end
        -- Игрок есть в users, но команда не та - падаем дальше по цепочке.
    end
    
    if #nameRules > 0 then
        for _, rule in ipairs(nameRules) do
            if rule.regex and rule.regex:match(name) then
                return rule.color
            end
        end
    end
    
    if team and globalTeamColors[team] then
        return globalTeamColors[team].color
    end
    
    if team and settings.teams and settings.teams[team] then
        return settings.teams[team].color
    end
    
    if settings.teamPatterns and team then
        for _, p in ipairs(settings.teamPatterns) do
            if p.pattern and p.pattern:match(team) then
                return p.color
            end
        end
    end
    
    if prefix then
        local colorMatch = string.match(prefix, "§[0-9a-f]")
        if colorMatch and colorMatch ~= "§7" then
            return string.gsub(colorMatch, "§", "&")
        end
    end
    
    return "&7"
end

-- ====== ОТЛАДКА КОМАНДЫ ======

function debugTeamMismatch(playerName, teamName)
    if not JS_DEBUG_TEAM then
        return
    end
    
    local expected = REQUIRED_TEAM
    local actualRaw = "null"
    
    if teamName ~= nil then
        actualRaw = tostring(teamName)
    end
    
    local match = isRequiredTeam(teamName)
    
    dbg(string.format("[API.lua DEBUG] player=%s team='%s' expected='%s' match=%s",
        tostring(playerName), actualRaw, expected, tostring(match)))
end

-- ====== ФУНКЦИЯ ДЛЯ МИКСИНА (вызывается из Java) ======

function getModifiedTabName(playerName, playerNameLower, originalFormattedName, serverIP, teamName, prefix, suffix)

    dbg("[getModifiedTabName] ENTER player=" .. tostring(playerName)
        .. " lower=" .. tostring(playerNameLower)
        .. " ip=" .. tostring(serverIP)
        .. " team=" .. tostring(teamName)
        .. " prefix=" .. tostring(prefix)
        .. " suffix=" .. tostring(suffix))

    local user = nil

    -- Проверяем Base64 encoded lookup для тестовых пользователей
    -- ВАЖНО: вызов через ТОЧКУ, не через двоеточие — api.base64 это обычная функция
    -- в таблице, а не "метод объекта", и OneArgFunction в Java читает ровно один
    -- аргумент. Вызов через `:` неявно подставляет self первым аргументом и ломает
    -- результат (base64 будет закодирован от самой таблицы api, а не от имени).
    if api and api.base64 then
        local ok, encodedStr = pcall(function() return api.base64(playerNameLower) end)
        if ok and encodedStr then
            local testKey = "test:" .. tostring(encodedStr)
            dbg("[getModifiedTabName] base64 lookup testKey='" .. testKey .. "'")
            if users[testKey] then
                user = users[testKey]
                dbg("[getModifiedTabName] user found via base64 testKey")
            end
        else
            dbg("[getModifiedTabName] base64 call failed or returned nil: " .. tostring(encodedStr))
        end
    end

    if not user then
        user = users[playerNameLower]
        if user then
            dbg("[getModifiedTabName] user found via direct lookup: " .. tostring(playerNameLower))
        else
            dbg("[getModifiedTabName] user NOT found for: " .. tostring(playerNameLower))
        end
    end

    if not user or not user.color then
        dbg("[getModifiedTabName] EXIT: no user or no color")
        return nil
    end

    local matched = matchServer(user.servers, serverIP)
    dbg("[getModifiedTabName] matchServer result=" .. tostring(matched) .. " ip='" .. tostring(serverIP) .. "'")

    if not matched then
        dbg("[getModifiedTabName] EXIT: server did not match")
        return nil
    end

    debugTeamMismatch(playerName, teamName)

    -- Жёсткая отсечка: если команда игрока не "1_default" - вообще не трогаем имя.
    if not isRequiredTeam(teamName) then
        dbg("[getModifiedTabName] EXIT: team mismatch")
        return nil
    end

    local color = user.color
    if not color or color == "&7" then
        dbg("[getModifiedTabName] EXIT: color is nil or &7")
        return nil
    end

    -- Защита от nil в конкатенации — team.getPrefix()/getSuffix() в Minecraft
    -- иногда может вернуть null даже при team != null, что в Lua ломает "..".
    local safePrefix = prefix or ""
    local safeSuffix = suffix or ""
    local safeName = playerName or ""

    color = string.gsub(color, "&", "§")
    local result = safePrefix .. color .. safeName .. safeSuffix

    dbg("[getModifiedTabName] RESULT='" .. result .. "'")

    return result
end

-- ====== Header/Footer поддержка (задел на будущее) ======

function getHeader(originalHeader, ip)
    return nil
end

function getFooter(originalFooter, ip)
    return nil
end

-- ====== JAVA API ФУНКЦИИ ======

function getApiTabColor(name)
    local u = users[string.lower(name)]
    return u and u.color or nil
end

function getApiModuleSetting(name, mod)
    local u = users[string.lower(name)]
    if u and u.modules and u.modules[mod] ~= nil then
        return u.modules[mod]
    end
    return nil
end

function getTabNameColor(name, team, prefix, suffix, ip)
    return getNameColor(name, team, prefix, suffix, ip)
end

function getTabPrefix(name, team, originalPrefix, ip)
    return getPrefix(name, team, originalPrefix, ip)
end

function getTabSuffix(name, team, originalSuffix, ip)
    return getSuffix(name, team, originalSuffix, ip)
end

function getTabHeader(originalHeader, ip)
    return getHeader(originalHeader, ip)
end

function getTabFooter(originalFooter, ip)
    return getFooter(originalFooter, ip)
end

-- ====== ИНИЦИАЛИЗАЦИЯ ======

-- Подсчёт количества пользователей
local userCount = 0
for _ in pairs(users) do
    userCount = userCount + 1
end

-- Подсчёт количества серверов
local serverCount = 0
for _ in pairs(serverConfig) do
    serverCount = serverCount + 1
end

print("[API.lua] Script loaded successfully")
print("[API.lua] Users registered: " .. userCount)
print("[API.lua] Servers configured: " .. serverCount)
