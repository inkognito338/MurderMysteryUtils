// ============================================================
//  MurderMysteryUtils API.js
// ============================================================

// ====== ПОЛЬЗОВАТЕЛИ ======
var users = {
    "inkognito338": {
        color: "&3",
        prefix: "&3[Dev] ",
        servers: ["*"],
        modules: { FakeGM1: false, AutoNext: 5 }
    },
    "ruinquie": {
        color: "&6",
        servers: ["*"],
        modules: { FakeGM1: false, AutoNext: 10, MurderAlert: 35 }
    },
    "zxcursed_zxc": {
        color: "&b",
        servers: ["*"],
        modules: { FakeGM1: false, AutoNext: 10, MurderAlert: 35 }
    },
    "zxcursed1234571": {
        color: "&e",
        servers: ["dexland", "masedworld", "mineblaze", "cheatmine", "mineberry", "minepeak"],
        modules: { FakeGM1: false, AutoNext: 10, ESP: true, NameTags: true }
    }
};

// ====== НАСТРОЙКИ ТАБА ДЛЯ СЕРВЕРОВ ======
var serverConfig = {
    // MasedWorld
    "masedworld": {
        header: "&6&lMasedWorld &f- &aMurder Mystery",
        footer: "&7Используй &e/next &7после смерти",
        
        // Обработка команд
        teams: {
            "1_default": {
                color: "&a",        // цвет ника
                prefix: "&a",       // цвет префикса
                suffix: ""          // суффикс
            }
        },
        
        // Правила для префиксов
        prefixRules: [
            { from: "&7", to: "&a", teams: ["1_default"] }
        ],
        
        // Правила для суффиксов
        suffixRules: [
            { from: "&7", to: "&a", teams: ["1_default"] }
        ]
    },
    
    // DexLand
    "dexland": {
        header: "&b&lDexLand &f- &eMurder Mystery",
        footer: "&7Жди следующий раунд...",
        
        teams: {
            "1_default": {
                color: "&a",
                prefix: "&a",
                suffix: ""
            }
        },
        
        prefixRules: [
            { from: "&7", to: "&a", teams: ["1_default"] }
        ],
        
        suffixRules: [
            { from: "&7", to: "&a", teams: ["1_default"] }
        ]
    },
    
    // MineBlaze
    "mineblaze": {
        header: "&c&lMineBlaze &f- &6Murder Mystery",
        footer: "",
        
        teams: {
            "1_default": {
                color: "&a",
                prefix: "&a",
                suffix: ""
            }
        },
        
        prefixRules: [
            { from: "&7", to: "&a", teams: ["1_default"] }
        ]
    },
    
    // По умолчанию для всех остальных серверов
    "default": {
        header: null,  // не менять
        footer: null,  // не менять
        
        teams: {
            "1_default": {
                color: "&a",
                prefix: "&a",
                suffix: ""
            }
        },
        
        // Если team начинается с "murder" или "killer" -> красный
        teamPatterns: [
            { pattern: /murder|killer|ubiyca/i, color: "&c", prefix: "&c", suffix: "" },
            { pattern: /detect|sheriff/i, color: "&b", prefix: "&b", suffix: "" },
            { pattern: /innocent|mir/i, color: "&7", prefix: "&7", suffix: "" }
        ]
    }
};

// ====== ГЛОБАЛЬНЫЕ ПРАВИЛА (для всех серверов) ======

// Цвета по имени команды (приоритет выше чем serverConfig)
var globalTeamColors = {
    // "team_name": { color: "&x", prefix: "&x", suffix: "текст" }
    // Пусто - значит нет глобальных правил, всё в serverConfig
};

// Regex правила для имён игроков
var nameRules = [
    // { regex: /admin/i, color: "&c", prefix: "&c[ADMIN] ", suffix: "" }
    // Пусто - не используем
];

// ====== ФУНКЦИИ ОБРАБОТКИ ======

/**
 * Получить настройки для сервера
 */
function getServerSettings(ip) {
    var serverName = detectServer(ip);
    
    // Ищем точное совпадение
    if (serverConfig[serverName]) {
        return serverConfig[serverName];
    }
    
    // Ищем частичное совпадение
    for (var key in serverConfig) {
        if (key === "default") continue;
        if (ip.toLowerCase().indexOf(key) !== -1) {
            return serverConfig[key];
        }
    }
    
    // Дефолт
    return serverConfig["default"] || {};
}

/**
 * Определить сервер по IP
 */
function detectServer(ip) {
    if (!ip) return "default";
    ip = ip.toLowerCase().replace(/:\d+$/, "");
    
    if (ip.indexOf("masedworld") !== -1) return "masedworld";
    if (ip.indexOf("dexland") !== -1) return "dexland";
    if (ip.indexOf("mineblaze") !== -1) return "mineblaze";
    if (ip.indexOf("cheatmine") !== -1) return "cheatmine";
    if (ip.indexOf("mineberry") !== -1) return "mineberry";
    if (ip.indexOf("minepeak") !== -1) return "minepeak";
    if (ip.indexOf("hypixel") !== -1) return "hypixel";
    
    var parts = ip.split(".");
    return parts.length >= 2 ? parts[parts.length - 2] : ip;
}

/**
 * Проверить подходит ли сервер
 */
function matchServer(servers, ip) {
    if (!servers || servers.indexOf("*") !== -1) return true;
    var lower = ip.toLowerCase();
    for (var i = 0; i < servers.length; i++) {
        if (lower.indexOf(servers[i].toLowerCase()) !== -1) return true;
    }
    return false;
}

/**
 * Заменить цветовые коды
 */
function replaceColor(text, from, to) {
    if (!text) return text;
    return text.split(from.replace("&", "§")).join(to.replace("&", "§"));
}

// ====== ГЛАВНЫЕ ФУНКЦИИ (вызываются из Java) ======

/**
 * Получить header таба
 * @param originalHeader - оригинальный header от сервера
 * @param serverIP - IP сервера
 * @return новый header или null (оставить как есть)
 */
function getHeader(originalHeader, serverIP) {
    var settings = getServerSettings(serverIP);
    
    // Если header задан явно - возвращаем его
    if (settings.header) {
        return settings.header;
    }
    
    // Если null - не меняем
    if (settings.header === null) {
        return null;
    }
    
    // Иначе можно модифицировать оригинал
    // Например: return originalHeader.replace("&7", "&f");
    return null;
}

/**
 * Получить footer таба
 */
function getFooter(originalFooter, serverIP) {
    var settings = getServerSettings(serverIP);
    
    if (settings.footer) {
        return settings.footer;
    }
    
    if (settings.footer === null) {
        return null;
    }
    
    return null;
}

/**
 * Получить префикс для игрока
 * @param name - имя игрока
 * @param team - команда
 * @param originalPrefix - оригинальный префикс от сервера
 * @param ip - IP сервера
 * @return новый префикс или null (оставить как есть)
 */
function getPrefix(name, team, originalPrefix, ip) {
    var n = name.toLowerCase();
    var settings = getServerSettings(ip);
    
    // 1. Пользовательский префикс
    var u = users[n];
    if (u && u.prefix && matchServer(u.servers, ip)) {
        return u.prefix;
    }
    
    // 2. Правила для команды
    if (team && settings.teams && settings.teams[team]) {
        var teamSettings = settings.teams[team];
        if (teamSettings.prefix) {
            return teamSettings.prefix;
        }
    }
    
    // 3. Правила замены цвета
    if (settings.prefixRules && originalPrefix) {
        for (var i = 0; i < settings.prefixRules.length; i++) {
            var rule = settings.prefixRules[i];
            
            // Проверяем команду
            if (rule.teams && rule.teams.indexOf(team) === -1) continue;
            
            // Заменяем цвет
            return replaceColor(originalPrefix, rule.from, rule.to);
        }
    }
    
    // 4. Team patterns
    if (settings.teamPatterns && team) {
        for (var i = 0; i < settings.teamPatterns.length; i++) {
            var pattern = settings.teamPatterns[i];
            if (pattern.pattern && pattern.pattern.test(team)) {
                return pattern.prefix || originalPrefix;
            }
        }
    }
    
    return null;
}

/**
 * Получить суффикс для игрока
 */
function getSuffix(name, team, originalSuffix, ip) {
    var n = name.toLowerCase();
    var settings = getServerSettings(ip);
    
    // 1. Пользовательский суффикс
    var u = users[n];
    if (u && u.suffix && matchServer(u.servers, ip)) {
        return u.suffix;
    }
    
    // 2. Правила для команды
    if (team && settings.teams && settings.teams[team]) {
        var teamSettings = settings.teams[team];
        if (teamSettings.suffix !== undefined) {
            return teamSettings.suffix;
        }
    }
    
    // 3. Правила замены цвета
    if (settings.suffixRules && originalSuffix) {
        for (var i = 0; i < settings.suffixRules.length; i++) {
            var rule = settings.suffixRules[i];
            if (rule.teams && rule.teams.indexOf(team) === -1) continue;
            return replaceColor(originalSuffix, rule.from, rule.to);
        }
    }
    
    // 4. Team patterns
    if (settings.teamPatterns && team) {
        for (var i = 0; i < settings.teamPatterns.length; i++) {
            var pattern = settings.teamPatterns[i];
            if (pattern.pattern && pattern.pattern.test(team)) {
                return pattern.suffix !== undefined ? pattern.suffix : originalSuffix;
            }
        }
    }
    
    return null;
}

/**
 * Получить цвет ника
 */
function getNameColor(name, team, prefix, suffix, ip) {
    var n = name.toLowerCase();
    var settings = getServerSettings(ip);
    
    // 1. Пользовательский цвет
    var u = users[n];
    if (u && u.color && matchServer(u.servers, ip)) {
        return u.color;
    }
    
    // 2. Regex правила для имени
    if (nameRules.length > 0) {
        for (var i = 0; i < nameRules.length; i++) {
            var rule = nameRules[i];
            if (rule.regex && rule.regex.test(name)) {
                return rule.color;
            }
        }
    }
    
    // 3. Глобальные цвета по команде
    if (team && globalTeamColors[team]) {
        return globalTeamColors[team].color;
    }
    
    // 4. Правила сервера для команды
    if (team && settings.teams && settings.teams[team]) {
        return settings.teams[team].color;
    }
    
    // 5. Team patterns
    if (settings.teamPatterns && team) {
        for (var i = 0; i < settings.teamPatterns.length; i++) {
            var pattern = settings.teamPatterns[i];
            if (pattern.pattern && pattern.pattern.test(team)) {
                return pattern.color;
            }
        }
    }
    
    // 6. Цвет из префикса (если ничего не нашли)
    if (prefix) {
        var m = prefix.match(/§[0-9a-f]/);
        if (m && m[0] !== "§7") return m[0].replace("§", "&");
    }
    
    return "&7";
}

// ====== ФУНКЦИИ ДЛЯ JAVA ======

function getApiTabColor(name) {
    var u = users[name.toLowerCase()];
    return u ? u.color : null;
}

function getApiModuleSetting(name, mod) {
    var u = users[name.toLowerCase()];
    if (u && u.modules && u.modules[mod] !== undefined) return u.modules[mod];
    return null;
}

// Основная функция для таба
function getTabNameColor(name, team, prefix, suffix, ip) {
    return getNameColor(name, team, prefix, suffix, ip);
}

// Дополнительные функции для полного контроля
function getTabPrefix(name, team, originalPrefix, ip) {
    return getPrefix(name, team, originalPrefix, ip);
}

function getTabSuffix(name, team, originalSuffix, ip) {
    return getSuffix(name, team, originalSuffix, ip);
}

function getTabHeader(originalHeader, ip) {
    return getHeader(originalHeader, ip);
}

function getTabFooter(originalFooter, ip) {
    return getFooter(originalFooter, ip);
}
