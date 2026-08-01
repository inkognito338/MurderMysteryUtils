// @name join logger
// @author inkognito338
// @description Отправляет в чат сообщения о входе игроков без символа '›'
// @version 1.0

var debug = false; // Включить/выключить отладку

function onPacketChat(message, playerName) {
    if (debug) {
        api.sendSystemMessage("§7[§8DEBUG§7] §fПолучено сообщение: §7" + message);
    }
    
    // Регулярное выражение ищет все варианты: › Игрок [ЛЮБОЙ_ТЕКСТ] Ник Текст
    var regex = /^› Игрок \[([^\]]+)\] (\S+)(.*)$/;
    
    var match = regex.exec(message);
    
    if (match) {
        if (debug) {
            api.sendSystemMessage("§7[§8DEBUG§7] §aНайдено сообщение о входе!");
        }
        
        // Если текст после ника пустой, ставим стандартную фразу
        var text = match[3].trim();
        if (text.length === 0) {
            text = "вошел на сервер";
        }
        
        // ВАЖНО: Мы берем оригинальное сообщение, но убираем из него "› Игрок " 
        // (13 символов, включая пробел после "Игрок")
        var cleanMessage = message.substring("› Игрок ".length);
        
        // ОТПРАВКА В ЧАТ СЕРВЕРА БЕЗ СТРЕЛОЧКИ
        api.sendChatMessage(cleanMessage);
        
        if (debug) {
            api.sendSystemMessage("§7[§8DEBUG§7] §aСообщение отправлено в чат без символа '›'!");
            api.sendSystemMessage("§7[§8DEBUG§7] §fОтправленный текст: §7" + cleanMessage);
        }
    } else {
        if (debug) {
            api.sendSystemMessage("§7[§8DEBUG§7] §7Сообщение не подходит под формат входа, пропущено");
        }
    }
}

api.sendActionBar("§6[Logger Upd] §fСкрипт загружен!");
if (debug) {
    api.sendSystemMessage("§7[§8DEBUG§7] §aРежим отладки включен!");
}