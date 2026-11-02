-- tags.lua
-- Всё сравнивается в нижнем регистре.
-- Тег показывается, только если адрес сервера содержит одно из ключевых слов правила.

local COMMON_KEYWORDS = {
    "dexland",
    "masedworld",
    "mineblaze",
    "cheatmine",
    "mineberry",
    "minepeak",
}

local function concat(a, b)
    local out = {}
    for _, v in ipairs(a) do out[#out + 1] = v end
    for _, v in ipairs(b) do out[#out + 1] = v end
    return out
end

local RULES = {
    {
        nicks    = { "48799444" },
        keywords = COMMON_KEYWORDS,
        tag      = "§a[MMUtils Author]",
    },
    {
        nicks    = { "inkognito338", "skv_inkognito338" },
        keywords = concat(COMMON_KEYWORDS, { "ultramc" }),
        tag      = "§a[MMUtils Author]",
    },
    {
        nicks    = { "wxrrior", "onikoe" },
        keywords = concat(COMMON_KEYWORDS),
        tag      = "§7[Clown]",
    }
}

local function normalize(s)
    if not s then return "" end
    return string.lower(tostring(s))
end

-- "play.DexLand.org:25565" -> "play.dexland.org"
local function normalizeHost(server)
    local host = normalize(server)
    host = host:gsub(":%d+$", ""):gsub("%.$", "")
    return host
end

-- ключевое слово встречается в хосте (plain-поиск, без паттернов)
local function hostMatches(host, keyword)
    return host:find(keyword, 1, true) ~= nil
end

local function ruleAllowsHost(rule, host)
    if host == "" then return false end
    for _, keyword in ipairs(rule.keywords) do
        if hostMatches(host, keyword) then
            return true
        end
    end
    return false
end

-- nick, server -> tag | nil
function getNickTag(nick, server)
    local n = normalize(nick)
    if n == "" then return nil end
    local host = normalizeHost(server)

    for _, rule in ipairs(RULES) do
        if ruleAllowsHost(rule, host) then
            for _, ruleNick in ipairs(rule.nicks) do
                if ruleNick == n then
                    return rule.tag
                end
            end
        end
    end
    return nil
end

-- Массовый вариант: {nick_lower = tag} для текущего сервера.
function getNickTagsForServer(server)
    local host = normalizeHost(server)
    local out = {}
    for _, rule in ipairs(RULES) do
        if ruleAllowsHost(rule, host) then
            for _, ruleNick in ipairs(rule.nicks) do
                out[ruleNick] = rule.tag
            end
        end
    end
    return out
end