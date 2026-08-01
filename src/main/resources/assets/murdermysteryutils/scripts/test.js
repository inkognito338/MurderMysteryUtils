// @name Test Logger
// @author inkognito338
// @description Тестовый скрипт - логирует в чат все события
// @version 1.1

function onChatMessage(message, playerName) {
    api.sendSystemMessage("§7[§6Chat§7] §f" + playerName + ": §7" + message);
}

function onTitle(title) {
    if (title && title.length > 0) {
        api.sendSystemMessage("§7[§5Title§7] §f" + title);
        api.log("Title: " + title);
    }
}

function onSubtitle(subtitle) {
    if (subtitle && subtitle.length > 0) {
        api.sendSystemMessage("§7[§5Subtitle§7] §f" + subtitle);
        api.log("Subtitle: " + subtitle);
    }
}

function onActionBar(text) {
    if (text && text.length > 0) {
        api.sendSystemMessage("§7[§5ActionBar§7] §f" + text);
        api.log("ActionBar: " + text);
    }
}

function onPacketChat(message, playerName) {
    api.sendSystemMessage("§7[§6PacketChat§7] §f" + playerName + ": §7" + message);
}

function onTick() {
    var count = api.getPlayerCount();
    if (count > 0) {
        api.sendSystemMessage("§7[§aTick§7] §fИгроков онлайн: §e" + count);
    }

    var rolesMap = api.getAllRoles(); // это java.util.Map
    var it = rolesMap.entrySet().iterator();

    var found = false;
    while (it.hasNext()) {
        var entry = it.next();
        var playerName = entry.getKey();
        var role = entry.getValue();

        var color = "§a";
        if (role === "MURDERER") color = "§c";
        else if (role === "DETECTIVE") color = "§b";

        api.sendSystemMessage("§7 - §f" + playerName + " §7-> " + color + role);
        found = true;
    }

    if (!found) {
        api.sendSystemMessage("§7[Tick] Роли пока не определены");
    }
}

function onWorldJoin(worldName) {
    api.sendSystemMessage("§7[§aWorld§7] §fЗагружен мир: §e" + worldName);
}

function onWorldLoad() {
    api.sendSystemMessage("§7[§aWorld§7] §fМир загружен");
}

function onWorldUnload() {
    api.sendSystemMessage("§7[§aWorld§7] §fМир выгружен");
}

function onServerConnect(ip) {
    api.sendSystemMessage("§7[§aServer§7] §fПодключено к: §e" + ip);
}

function onServerDisconnect(ip) {
    api.sendSystemMessage("§7[§aServer§7] §fОтключено от: §e" + ip);
}

function onServerChange(oldIP, newIP) {
    api.sendSystemMessage("§7[§aServer§7] §fСмена сервера: §e" + oldIP + " §7-> §e" + newIP);
}

function onModuleEnable() {
    api.sendSystemMessage("§7[§aModule§7] §fLocalAPI §aвключен");
}

function onModuleDisable() {
    api.sendSystemMessage("§7[§aModule§7] §fLocalAPI §cвыключен");
}

function onScriptLoaded(name) {
    api.sendSystemMessage("§7[§aScript§7] §fСкрипт загружен: §e" + name);
}

function onScriptEnabled(name) {
    api.sendSystemMessage("§7[§aScript§7] §fСкрипт включен: §e" + name);
}

function onScriptDisabled(name) {
    api.sendSystemMessage("§7[§aScript§7] §fСкрипт выключен: §e" + name);
}

function onScriptToggle(name, enabled) {
    api.sendSystemMessage("§7[§aScript§7] §fСкрипт " + name + " " + (enabled ? "§aвключен" : "§cвыключен"));
}

function onScriptsReloaded() {
    api.sendSystemMessage("§7[§aScript§7] §fВсе скрипты перезагружены");
}


function colorForRole(role) {
    if (role === "MURDERER") return "§c";
    if (role === "DETECTIVE") return "§b";
    return "§a";
}

function printPlayers() {
    var players = api.getAllPlayersInfo();
    if (!players || players.length === 0) {
        api.sendSystemMessage("§7Игроки не найдены");
        return;
    }

    api.sendSystemMessage("§6--- §fИгроки в мире §6---");
    for (var i = 0; i < players.length; i++) {
        var p = players[i];
        var roleColor = colorForRole(p.role);
        var line = "§7 - " + (p.isSelf ? "§e" : "§f") + p.name +
                " " + roleColor + "[" + p.role + "]" +
                " §7ping: §f" + p.ping;

        if (p.hasPosition) {
            line += " §7xyz: §f" +
                    p.x.toFixed(1) + ", " + p.y.toFixed(1) + ", " + p.z.toFixed(1) +
                    " §7(dim " + p.dimension + ")";
        } else {
            line += " §8(вне радиуса прогрузки)";
        }

        if (p.isNPC) {
            line += " §8[NPC]";
        }

        api.sendSystemMessage(line);
    }
}


api.sendSystemMessage("§7Текущий игрок: §f" + api.getOwnName());
api.sendSystemMessage("§7IP сервера: §f" + api.getServerIP());
api.sendSystemMessage("§7Название сервера: §f" + api.getServerName());
api.sendSystemMessage("§7Игроков онлайн: §f" + api.getPlayerCount());

var scripts = api.getScripts();
if (scripts && typeof scripts.length !== "undefined") {
    var count = scripts.length;
    api.sendSystemMessage("§7Загружено скриптов: §f" + count);
    for (var i = 0; i < count; i++) {
        var scriptName = scripts[i];
        if (scriptName) {
            var enabled = api.isScriptEnabled(scriptName);
            var meta = api.getScriptMetadata(scriptName);
            if (meta && meta.name) {
                api.sendSystemMessage("§7  " + (enabled ? "§a✓" : "§c✗") + " §f" + meta.name + " §7(§3" + meta.author + "§7)");
            } else {
                api.sendSystemMessage("§7  " + (enabled ? "§a✓" : "§c✗") + " §f" + scriptName);
            }
        }
    }
} else {
    api.sendSystemMessage("§7Загружено скриптов: §c0");
}

printPlayers();

var murderers = api.getMurderers();
if (murderers && murderers.length > 0) {
    api.sendSystemMessage("§c[!] Известные убийцы: §f" + murderers.join(", "));
} else {
    api.sendSystemMessage("§7Известных убийц пока нет");
}

api.sendSystemMessage("§6========================================");
api.sendSystemMessage("§a[Test Logger] §fСкрипт успешно загружен!");

api.sendActionBar("§6[Test Logger] §fСкрипт загружен!");