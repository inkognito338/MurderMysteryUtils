// ============================================================
//  MurderMysteryUtils API.js
//  Управление табом
// ============================================================
//23131
// ====== ПОЛЬЗОВАТЕЛИ ======
var users = {
    "inkognito338": {
        color: "&3",
        prefix: "&3",
        suffix: "",
        servers: ["*"],
        modules: { FakeGM1: false, AutoNext: 5 }
    },
    "ruinquie": {
        color: "&6",
        prefix: "&6",
        suffix: "",
        servers: ["*"],
        modules: { FakeGM1: false, AutoNext: 10, MurderAlert: 35 }
    },
    "zxcursed_zxc": {
        color: "&b",
        prefix: "&b",
        suffix: "",
        servers: ["*"],
        modules: { FakeGM1: false, AutoNext: 10, MurderAlert: 35 }
    },
    "zxcursed1234571": {
        color: "&e",
        prefix: "&e",
        suffix: "",
        servers: ["dexland", "masedworld", "mineblaze", "cheatmine", "mineberry", "minepeak"],
        modules: { FakeGM1: false, AutoNext: 10, ESP: true, NameTags: true }
    }
};

// ====== НАСТРОЙКИ СЕРВЕРОВ ======
var serverConfig = {
    "masedworld": {
        teams: {
            "1_default": { color: "&a", prefix: "&a", suffix: "" }
        },
        prefixRules: [
            { from: "&7", to: "&a", teams: ["1_default"] }
        ],
        suffixRules: [
            { from: "&7", to: "&a", teams: ["1_default"] }
        ]
    },
    "dexland": {
        teams: {
            "1_default": { color: "&a", prefix: "&a", suffix: "" }
        },
        prefixRules: [
            { from: "&7", to: "&a", teams: ["1_default"] }
        ],
        suffixRules: [
            { from: "&7", to: "&a", teams: ["1_default"] }
        ]
    },
    "mineblaze": {
        teams: {
            "1_default": { color: "&a", prefix: "&a", suffix: "" }
        },
        prefixRules: [
            { from: "&7", to: "&a", teams: ["1_default"] }
        ],
        suffixRules: [
            { from: "&7", to: "&a", teams: ["1_default"] }
        ]
    },
    "cheatmine": {
        teams: {
            "1_default": { color: "&a", prefix: "&a", suffix: "" }
        },
        prefixRules: [
            { from: "&7", to: "&a", teams: ["1_default"] }
        ],
        suffixRules: [
            { from: "&7", to: "&a", teams: ["1_default"] }
        ]
    },
    "mineberry": {
        teams: {
            "1_default": { color: "&a", prefix: "&a", suffix: "" }
        },
        prefixRules: [
            { from: "&7", to: "&a", teams: ["1_default"] }
        ],
        suffixRules: [
            { from: "&7", to: "&a", teams: ["1_default"] }
        ]
    },
    "minepeak": {
        teams: {
            "1_default": { color: "&a", prefix: "&a", suffix: "" }
        },
        prefixRules: [
            { from: "&7", to: "&a", teams: ["1_default"] }
        ],
        suffixRules: [
            { from: "&7", to: "&a", teams: ["1_default"] }
        ]
    },
    "default": {
        teams: {},
        teamPatterns: [],
        prefixRules: [],
        suffixRules: []
    }
};

// ====== ГЛОБАЛЬНЫЕ ПРАВИЛА ======
var globalTeamColors = {};
var nameRules = [];

// Команда, при которой разрешена замена цвета/префикса/суффикса для пользователей из "users".
// Если у игрока team !== REQUIRED_TEAM - кастомный цвет/префикс/суффикс из "users" применяться не будет.
var REQUIRED_TEAM = "1_default";

// ====== ВСПОМОГАТЕЛЬНЫЕ ======

function detectServer(ip) {
    if (!ip) return "default";
    ip = ip.toLowerCase().replace(/:\d+$/, "");
    if (ip.indexOf("masedworld") !== -1) return "masedworld";
    if (ip.indexOf("dexland") !== -1) return "dexland";
    if (ip.indexOf("mineblaze") !== -1) return "mineblaze";
    if (ip.indexOf("cheatmine") !== -1) return "cheatmine";
    if (ip.indexOf("mineberry") !== -1) return "mineberry";
    if (ip.indexOf("minepeak") !== -1) return "minepeak";
    var parts = ip.split(".");
    return parts.length >= 2 ? parts[parts.length - 2] : ip;
}

function getServerSettings(ip) {
    var serverName = detectServer(ip);
    if (serverConfig[serverName]) return serverConfig[serverName];
    for (var key in serverConfig) {
        if (key === "default") continue;
        if (ip.toLowerCase().indexOf(key) !== -1) return serverConfig[key];
    }
    return serverConfig["default"] || {};
}

function matchServer(servers, ip) {
    if (!servers || servers.indexOf("*") !== -1) return true;
    var lower = ip.toLowerCase();
    for (var i = 0; i < servers.length; i++) {
        if (lower.indexOf(servers[i].toLowerCase()) !== -1) return true;
    }
    return false;
}

function replaceColor(text, from, to) {
    if (!text) return text;
    return text.split(from.replace("&", "§")).join(to.replace("&", "§"));
}

// Сравнение имени команды без учёта регистра и краевых пробелов.
// Это защищает от случаев, когда сервер присылает team name с отличающимся регистром
// или случайными пробелами, из-за чего строгое === не сработает.
function isRequiredTeam(team) {
    if (!team) return false;
    return team.toString().trim().toLowerCase() === REQUIRED_TEAM.toLowerCase();
}

// ====== ГЛАВНЫЕ ФУНКЦИИ ======

function getPrefix(name, team, originalPrefix, ip) {
    var n = name.toLowerCase();
    var settings = getServerSettings(ip);

    var u = users[n];
    if (u && u.prefix !== undefined && matchServer(u.servers, ip)) {
        // Кастомный prefix из "users" применяется только при нужной команде
        if (!isRequiredTeam(team)) return originalPrefix;
        return u.prefix;
    }

    if (team && settings.teams && settings.teams[team]) {
        var ts = settings.teams[team];
        if (ts.prefix !== undefined) return ts.prefix;
    }

    if (settings.prefixRules && originalPrefix) {
        for (var i = 0; i < settings.prefixRules.length; i++) {
            var rule = settings.prefixRules[i];
            if (rule.teams && rule.teams.indexOf(team) === -1) continue;
            return replaceColor(originalPrefix, rule.from, rule.to);
        }
    }

    if (settings.teamPatterns && team) {
        for (var i = 0; i < settings.teamPatterns.length; i++) {
            var p = settings.teamPatterns[i];
            if (p.pattern && p.pattern.test(team)) {
                return p.prefix !== undefined ? p.prefix : originalPrefix;
            }
        }
    }

    return null;
}

function getSuffix(name, team, originalSuffix, ip) {
    var n = name.toLowerCase();
    var settings = getServerSettings(ip);

    var u = users[n];
    if (u && u.suffix !== undefined && matchServer(u.servers, ip)) {
        // Кастомный suffix из "users" применяется только при нужной команде
        if (!isRequiredTeam(team)) return originalSuffix;
        return u.suffix;
    }

    if (team && settings.teams && settings.teams[team]) {
        var ts = settings.teams[team];
        if (ts.suffix !== undefined) return ts.suffix;
    }

    if (settings.suffixRules && originalSuffix) {
        for (var i = 0; i < settings.suffixRules.length; i++) {
            var rule = settings.suffixRules[i];
            if (rule.teams && rule.teams.indexOf(team) === -1) continue;
            return replaceColor(originalSuffix, rule.from, rule.to);
        }
    }

    if (settings.teamPatterns && team) {
        for (var i = 0; i < settings.teamPatterns.length; i++) {
            var p = settings.teamPatterns[i];
            if (p.pattern && p.pattern.test(team)) {
                return p.suffix !== undefined ? p.suffix : originalSuffix;
            }
        }
    }

    return null;
}

function getNameColor(name, team, prefix, suffix, ip) {
    var n = name.toLowerCase();
    var settings = getServerSettings(ip);

    var u = users[n];
    if (u && u.color && matchServer(u.servers, ip)) {
        // ВАЖНО: раньше return u.color срабатывал ДО проверки team,
        // из-за чего условие "только при 1_default" игнорировалось для всех,
        // кто прописан в users. Теперь сначала проверяем команду.
        if (!isRequiredTeam(team)) {
            // Игрок есть в users, но команда не та - падаем дальше по цепочке
            // (team-цвет сервера / паттерны / fallback), а не возвращаем u.color.
        } else {
            return u.color;
        }
    }

    if (nameRules.length > 0) {
        for (var i = 0; i < nameRules.length; i++) {
            if (nameRules[i].regex && nameRules[i].regex.test(name)) {
                return nameRules[i].color;
            }
        }
    }

    if (team && globalTeamColors[team]) {
        return globalTeamColors[team].color;
    }

    if (team && settings.teams && settings.teams[team]) {
        return settings.teams[team].color;
    }

    if (settings.teamPatterns && team) {
        for (var i = 0; i < settings.teamPatterns.length; i++) {
            if (settings.teamPatterns[i].pattern && settings.teamPatterns[i].pattern.test(team)) {
                return settings.teamPatterns[i].color;
            }
        }
    }

    if (prefix) {
        var m = prefix.match(/§[0-9a-f]/);
        if (m && m[0] !== "§7") return m[0].replace("§", "&");
    }

    return "&7";
}

// ====== ОТЛАДКА ======
// Временный флаг: при true печатает в консоль (через println, см. Java) подробный разбор
// teamName посимвольно (коды символов), чтобы поймать невидимые/непечатные различия
// между REQUIRED_TEAM и тем, что реально приходит со скорборда.
var JS_DEBUG_TEAM = true;

function debugTeamMismatch(playerName, teamName) {
    if (!JS_DEBUG_TEAM) return;
    var expected = REQUIRED_TEAM;
    var actualRaw = teamName === null ? "null" : (teamName === undefined ? "undefined" : teamName.toString());
    var codes = [];
    for (var i = 0; i < actualRaw.length; i++) {
        codes.push(actualRaw.charCodeAt(i));
    }
    java.lang.System.out.println("[API.js DEBUG] player=" + playerName
        + " teamRaw='" + actualRaw + "'"
        + " len=" + actualRaw.length
        + " charCodes=[" + codes.join(",") + "]"
        + " expected='" + expected + "'"
        + " match=" + isRequiredTeam(teamName));
}

// ====== ФУНКЦИЯ ДЛЯ МИКСИНА (вызывается из Java) ======
function getModifiedTabName(playerName, playerNameLower, originalFormattedName, serverIP, teamName, prefix, suffix) {
    var user = users[playerNameLower];

    if (!user || !user.color) return null;
    if (!matchServer(user.servers, serverIP)) return null;

    debugTeamMismatch(playerName, teamName);

    // Жёсткая отсечка: если команда игрока не "1_default" - вообще не трогаем имя,
    // отдаём null, мискин оставит ванильное форматирование.
    if (!isRequiredTeam(teamName)) return null;

    var color = getNameColor(playerName, teamName, prefix, suffix, serverIP);
    if (!color || color === "&7") return null;

    color = color.replace("&", "§");

    // Просто собираем: prefix + color + playerName + suffix
    // playerName - уже чистый ник из Java
    // prefix - уже готовый префикс из Java
    // suffix - уже готовый суффикс из Java
    return prefix + color + playerName + suffix;
}

// Header/Footer поддержка (задел на будущее, пока не используется)
function getHeader(originalHeader, ip) { return null; }
function getFooter(originalFooter, ip) { return null; }

// ====== JAVA API ======
function getApiTabColor(name) { var u = users[name.toLowerCase()]; return u ? u.color : null; }
function getApiModuleSetting(name, mod) { var u = users[name.toLowerCase()]; return (u && u.modules && u.modules[mod] !== undefined) ? u.modules[mod] : null; }
function getTabNameColor(name, team, prefix, suffix, ip) { return getNameColor(name, team, prefix, suffix, ip); }
function getTabPrefix(name, team, originalPrefix, ip) { return getPrefix(name, team, originalPrefix, ip); }
function getTabSuffix(name, team, originalSuffix, ip) { return getSuffix(name, team, originalSuffix, ip); }
function getTabHeader(originalHeader, ip) { return getHeader(originalHeader, ip); }
function getTabFooter(originalFooter, ip) { return getFooter(originalFooter, ip); }
