// ============================================================
//  MurderMysteryUtils API.js
//  Управление табом (совместимо с Java 8u51)
// ============================================================

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

// ====== ВСПОМОГАТЕЛЬНЫЕ ======

// Замена indexOf для совместимости с Java 8u51
function strContains(str, search) {
    if (!str || !search) return false;
    return str.indexOf(search) !== -1;
}

function arrContains(arr, item) {
    if (!arr) return false;
    for (var i = 0; i < arr.length; i++) {
        if (arr[i] === item) return true;
    }
    return false;
}

function detectServer(ip) {
    if (!ip) return "default";
    ip = ip.toLowerCase().replace(/:\d+$/, "");
    if (strContains(ip, "masedworld")) return "masedworld";
    if (strContains(ip, "dexland")) return "dexland";
    if (strContains(ip, "mineblaze")) return "mineblaze";
    if (strContains(ip, "cheatmine")) return "cheatmine";
    if (strContains(ip, "mineberry")) return "mineberry";
    if (strContains(ip, "minepeak")) return "minepeak";
    var parts = ip.split(".");
    return parts.length >= 2 ? parts[parts.length - 2] : ip;
}

function getServerSettings(ip) {
    var serverName = detectServer(ip);
    if (serverConfig[serverName]) return serverConfig[serverName];
    for (var key in serverConfig) {
        if (key === "default") continue;
        if (strContains(ip.toLowerCase(), key)) return serverConfig[key];
    }
    return serverConfig["default"] || {};
}

function matchServer(servers, ip) {
    if (!servers || servers.length === 0) return true;
    if (arrContains(servers, "*")) return true;
    var lower = ip.toLowerCase();
    for (var i = 0; i < servers.length; i++) {
        if (strContains(lower, servers[i].toLowerCase())) return true;
    }
    return false;
}

function replaceColor(text, from, to) {
    if (!text) return text;
    return text.split(from.replace("&", "§")).join(to.replace("&", "§"));
}

// ====== ГЛАВНЫЕ ФУНКЦИИ ======

function getPrefix(name, team, originalPrefix, ip) {
    var n = name.toLowerCase();
    var settings = getServerSettings(ip);
    
    var u = users[n];
    if (u && u.prefix !== undefined && matchServer(u.servers, ip)) {
        return u.prefix;
    }
    
    if (team && settings.teams && settings.teams[team]) {
        var ts = settings.teams[team];
        if (ts.prefix !== undefined) return ts.prefix;
    }
    
    if (settings.prefixRules && originalPrefix) {
        for (var i = 0; i < settings.prefixRules.length; i++) {
            var rule = settings.prefixRules[i];
            if (rule.teams && !arrContains(rule.teams, team)) continue;
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
        return u.suffix;
    }
    
    if (team && settings.teams && settings.teams[team]) {
        var ts = settings.teams[team];
        if (ts.suffix !== undefined) return ts.suffix;
    }
    
    if (settings.suffixRules && originalSuffix) {
        for (var i = 0; i < settings.suffixRules.length; i++) {
            var rule = settings.suffixRules[i];
            if (rule.teams && !arrContains(rule.teams, team)) continue;
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
        return u.color;
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

function getHeader(originalHeader, ip) { return null; }
function getFooter(originalFooter, ip) { return null; }

function getApiTabColor(name) { var u = users[name.toLowerCase()]; return u ? u.color : null; }
function getApiModuleSetting(name, mod) { var u = users[name.toLowerCase()]; return (u && u.modules && u.modules[mod] !== undefined) ? u.modules[mod] : null; }
function getTabNameColor(name, team, prefix, suffix, ip) { return getNameColor(name, team, prefix, suffix, ip); }
function getTabPrefix(name, team, originalPrefix, ip) { return getPrefix(name, team, originalPrefix, ip); }
function getTabSuffix(name, team, originalSuffix, ip) { return getSuffix(name, team, originalSuffix, ip); }
function getTabHeader(originalHeader, ip) { return getHeader(originalHeader, ip); }
function getTabFooter(originalFooter, ip) { return getFooter(originalFooter, ip); }
