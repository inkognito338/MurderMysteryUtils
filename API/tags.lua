-- tags.lua
-- Ключи: "<nick>@<server>" или "<nick>@*" для всех серверов.
-- Всё в нижнем регистре. Значение — готовая строка с §-цветами (или &).

local TAGS = {
    ["48799444@*"]        = "§6[MMUtils Author]",
    ["inkognito338@*"]           = "§a[MMUtils Author]",
}

local function normalize(s)
    if not s then return "" end
    return string.lower(tostring(s))
end

-- nick, server -> tag | nil
function getNickTag(nick, server)
    local n = normalize(nick)
    local s = normalize(server)
    if n == "" then return nil end

    -- точное совпадение по серверу
    local tag = TAGS[n .. "@" .. s]
    if tag then return tag end

    -- wildcard
    tag = TAGS[n .. "@*"]
    if tag then return tag end

    return nil
end

-- Массовый вариант — вернуть таблицу {nick_lower = tag} для текущего сервера,
-- чтобы CustomTab не дёргал Lua по каждому игроку.
function getNickTagsForServer(server)
    local s = normalize(server)
    local out = {}
    for k, v in pairs(TAGS) do
        local nick, srv = k:match("^(.-)@(.*)$")
        if nick and (srv == "*" or srv == s) then
            out[nick] = v
        end
    end
    return out
end
