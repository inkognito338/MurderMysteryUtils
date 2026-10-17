-- tags.lua
-- Всё сравнивается в нижнем регистре.
-- Тег показывается, только если адрес сервера оканчивается на один из доменов правила.

local COMMON_DOMAINS = {
    "dexland.org",
    "dexland.net",
    "masedworld.net",
    "masedworld.ru",
    "cheatmine.net",
    "cheatmine.ru",
    "mineberry.org",
    "mineberry.net",
    "minepeak.org",
    "minepeak.net",
}

local function concat(a, b)
    local out = {}
    for _, v in ipairs(a) do out[#out + 1] = v end
    for _, v in ipairs(b) do out[#out + 1] = v end
    return out
end

local RULES = {
    {
        nicks   = { "48799464" },
        domains = COMMON_DOMAINS,
        tag     = "§6[MMUtils Author]",
    },
    {
        nicks   = { "inkognito338", "skv_inkognito338" },
        domains = concat(COMMON_DOMAINS, { "ultramc.top" }),
        tag     = "§a[MMUtils Author]",
    },
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

-- host == domain или host оканчивается на ".domain"
local function hostMatches(host, domain)
    if host == domain then return true end
    local suffix = "." .. domain
    return #host > #suffix and host:sub(-#suffix) == suffix
end

local function ruleAllowsHost(rule, host)
    if host == "" then return false end
    for _, domain in ipairs(rule.domains) do
        if hostMatches(host, domain) then
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