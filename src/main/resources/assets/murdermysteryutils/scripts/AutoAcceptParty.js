// @name AutoAcceptParty
// @author inkognito338
// @description Автоматически принимает приглашения в пати
// @version 1.0

function onPacketChat(message, playerName) {
    var cleanMessage = message.replace(/§[0-9a-fk-or]/g, "");
    if (cleanMessage.includes("Нажмите сюда чтобы присоединиться! У вас есть 60 секунд.")) {
        api.sendChatMessage("/p accept");
    }
}

api.sendActionBar("§a[Party Accept] §fСкрипт автоматического принятия загружен!");