package real.inkognito338.murdermysteryutils.online;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public class OnlineModeGUI extends GuiScreen implements OnlineMode.OnlineModeListener {

    private static OnlineModeGUI instance;
    private final OnlineMode onlineMode = OnlineMode.getInstance();

    private GuiScreen parentScreen;

    private String statusMessage = "";
    private boolean statusIsError = false;
    private boolean busy = false;

    private boolean pendingActionWasAuth = false;
    private boolean pendingActionWasGuest = false;

    private GuiButton agreeButton;
    private GuiButton disconnectButton;
    private GuiButton closeButton;

    private GuiButton openAuthButton;
    private GuiTextField authCodeField;
    private GuiButton authSubmitButton;
    private GuiButton guestSubmitButton;

    private int windowX, windowY;
    private static final int WINDOW_WIDTH = 420;
    private static final int WINDOW_HEIGHT = 315;
    private boolean dragging = false;
    private int dragX, dragY;

    public static OnlineModeGUI getInstance() {
        if (instance == null) {
            instance = new OnlineModeGUI();
        }
        return instance;
    }

    private OnlineModeGUI() {}

    public void openFrom(GuiScreen parent) {
        this.parentScreen = parent;
        net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(this);
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        onlineMode.addListener(this);

        int centerX = width / 2;
        int centerY = height / 2;
        windowX = centerX - WINDOW_WIDTH / 2;
        windowY = centerY - WINDOW_HEIGHT / 2;

        String preservedAuthCode = (authCodeField != null) ? authCodeField.getText() : "";

        buttonList.clear();
        initTopBar();
        initForm();

        if (!preservedAuthCode.isEmpty()) {
            authCodeField.setText(preservedAuthCode);
        }

        updateButtonStates();

        if (onlineMode.isAgreementAccepted() && (pendingActionWasAuth || pendingActionWasGuest)) {
            boolean wasAuth = pendingActionWasAuth;
            boolean wasGuest = pendingActionWasGuest;
            pendingActionWasAuth = false;
            pendingActionWasGuest = false;

            if (wasAuth) {
                doAuth();
            } else if (wasGuest) {
                doGuestLogin();
            }
        }
    }

    private void initTopBar() {
        int btnWidth = 105;
        int btnHeight = 20;
        int btnY = windowY + WINDOW_HEIGHT - 28;
        int btnX = windowX + 16;

        agreeButton = new GuiButton(101, btnX, btnY, btnWidth, btnHeight,
                onlineMode.isAgreementAccepted() ? "§aСоглашение ✓" : "§eСоглашение ✗");
        buttonList.add(agreeButton);
        btnX += btnWidth + 6;

        disconnectButton = new GuiButton(103, btnX, btnY, btnWidth, btnHeight, "§cОтключиться");
        buttonList.add(disconnectButton);
        btnX += btnWidth + 6;

        closeButton = new GuiButton(102, btnX, btnY, 84, btnHeight, "Закрыть");
        buttonList.add(closeButton);
    }

    private void initForm() {
        int fieldWidth = 320;
        int fieldX = windowX + (WINDOW_WIDTH - fieldWidth) / 2;

        int startY = windowY + 65;

        openAuthButton = new GuiButton(21, fieldX, startY + 26, fieldWidth, 20, "§bШаг 1: Открыть вход через Discord");
        buttonList.add(openAuthButton);

        authCodeField = new GuiTextField(11, mc.fontRenderer, fieldX, startY + 66, fieldWidth, 18);
        authCodeField.setMaxStringLength(128);
        authCodeField.setEnableBackgroundDrawing(true);
        authCodeField.setFocused(true);

        authSubmitButton = new GuiButton(22, fieldX, startY + 88, fieldWidth, 20, "§aШаг 2: Подтвердить вход");
        buttonList.add(authSubmitButton);

        guestSubmitButton = new GuiButton(23, fieldX, startY + 138, fieldWidth, 20, "§7Войти как гость (если ник не занят)");
        buttonList.add(guestSubmitButton);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        onlineMode.removeListener(this);
    }

    @Override
    public void onEvent(Event event) {
        switch (event) {
            case CONNECTED:
            case REGISTERED_AND_CONNECTED:
                busy = false;
                setStatus(event == Event.REGISTERED_AND_CONNECTED
                        ? "Аккаунт зарегистрирован и вход выполнен!"
                        : "Успешный вход через Discord!", false);
                authCodeField.setText("");
                break;
            case DISCONNECTED:
                busy = false;
                break;
            case AGREEMENT_ACCEPTED:
                if (pendingActionWasAuth) {
                    pendingActionWasAuth = false;
                    doAuth();
                } else if (pendingActionWasGuest) {
                    pendingActionWasGuest = false;
                    doGuestLogin();
                }
                break;
            case NICK_CHANGED:
                setStatus("Ник изменён! Сессия сброшена. Войдите заново.", true);
                break;
            default:
                break;
        }
        updateButtonStates();
    }

    @Override
    public void onEvent(Event event, Object data) {
        switch (event) {
            case ERROR:
                busy = false;
                setStatus(String.valueOf(data), true);
                break;
            case REGISTERED_PENDING_APPROVAL:
                busy = false;
                setStatus("Аккаунт \"" + data + "\" зарегистрирован и ожидает одобрения администратора.", false);
                break;
            case NOTIFICATION:
                setStatus(String.valueOf(data), false);
                break;
            case NICK_CHANGED:
                setStatus("Ник изменён на " + data + "! Сессия сброшена.", true);
                break;
            default:
                onEvent(event);
                return;
        }
        updateButtonStates();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        for (int i = 1; i <= 6; i++) {
            drawRect(windowX - i, windowY - i, windowX + WINDOW_WIDTH + i, windowY + WINDOW_HEIGHT + i, 0x11000000);
        }

        drawRect(windowX, windowY, windowX + WINDOW_WIDTH, windowY + WINDOW_HEIGHT, 0xF0101010);
        drawRect(windowX, windowY, windowX + WINDOW_WIDTH, windowY + 28, 0xF0202020);

        String title = "§b§lУправление онлайн-режимом";
        int titleWidth = mc.fontRenderer.getStringWidth(title);
        mc.fontRenderer.drawStringWithShadow(title, windowX + (WINDOW_WIDTH - titleWidth) / 2, windowY + 10, 0xFFFFFF);
        drawRect(windowX, windowY + 28, windowX + WINDOW_WIDTH, windowY + 29, 0x40FFFFFF);

        int infoY = windowY + 34;
        String nick = onlineMode.getCurrentPlayerNick();
        String server = onlineMode.getCurrentGameServer();

        mc.fontRenderer.drawStringWithShadow("§7Ник: §f" + (nick != null ? nick : "неизвестен"), windowX + 16, infoY, 0xCCCCCC);
        mc.fontRenderer.drawStringWithShadow("§7Сервер: §f" + (server != null ? server : "не в сети"), windowX + 16, infoY + 11, 0xCCCCCC);

        String statusText;
        int statusColor;
        if (onlineMode.isConnected()) {
            statusText = onlineMode.isGuest() ? "§aПодключен (гость)" : "§aПодключен ✓";
            statusColor = 0x55FF55;
        } else {
            statusText = "§cОтключен";
            statusColor = 0xFF5555;
        }
        mc.fontRenderer.drawStringWithShadow("§7Статус: " + statusText, windowX + 235, infoY, statusColor);

        drawRect(windowX + 16, infoY + 23, windowX + WINDOW_WIDTH - 16, infoY + 24, 0x22FFFFFF);

        if (onlineMode.isConnected()) {
            drawConnectedInfo();
        } else {
            drawLoginForm();
        }

        if (!statusMessage.isEmpty()) {
            int color = statusIsError ? 0xFF5555 : 0x55FF55;
            String truncated = mc.fontRenderer.trimStringToWidth(statusMessage, WINDOW_WIDTH - 32);
            mc.fontRenderer.drawStringWithShadow(truncated, windowX + 16, windowY + WINDOW_HEIGHT - 44, color);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawConnectedInfo() {
        int y = windowY + 70;

        if (!onlineMode.isGuest() && onlineMode.getUserNick() != null) {
            mc.fontRenderer.drawStringWithShadow("§fИнформация об аккаунте:", windowX + 16, y, 0xFFFFFF);
            mc.fontRenderer.drawStringWithShadow("§7• Профиль: §f" + onlineMode.getUserNick(), windowX + 24, y + 14, 0xCCCCCC);
            mc.fontRenderer.drawStringWithShadow("§7• Ранг: " + getRankColor(onlineMode.getUserRank()) + onlineMode.getUserRankName(), windowX + 24, y + 26, 0xCCCCCC);
        } else {
            mc.fontRenderer.drawStringWithShadow("§eВы вошли как гость.", windowX + 16, y, 0xFFEE55);
            mc.fontRenderer.drawStringWithShadow("§7Для получения ранга и привилегий авторизуйтесь через Discord.", windowX + 16, y + 14, 0xAAAAAA);
        }

        int statsY = y + 46;
        long uptime = onlineMode.getUptime();
        String uptimeStr = String.format("§7Время сессии: §f%d:%02d:%02d", uptime / 3600000, (uptime / 60000) % 60, (uptime / 1000) % 60);
        mc.fontRenderer.drawStringWithShadow(uptimeStr, windowX + 16, statsY, 0xCCCCCC);

        double latency = onlineMode.getAverageLatency();
        String latencyStr = String.format("§7Пинг: %s%.0fмс", latency < 100 ? "§a" : (latency < 300 ? "§e" : "§c"), latency);
        mc.fontRenderer.drawStringWithShadow(latencyStr, windowX + 235, statsY, 0xCCCCCC);

        mc.fontRenderer.drawStringWithShadow("§7Сообщения: §f" + onlineMode.getTotalMessagesSent() + " §7отпр. / §f" + onlineMode.getTotalMessagesReceived() + " §7получ.",
                windowX + 16, statsY + 12, 0xCCCCCC);
    }

    private void drawLoginForm() {
        int startY = windowY + 65;
        int leftX = windowX + 16;

        mc.fontRenderer.drawStringWithShadow("§eВход через Discord (OAuth2):", leftX, startY, 0xFFEE55);
        mc.fontRenderer.drawStringWithShadow("§8Нажмите кнопку, разрешите доступ в браузере и скопируйте код.", leftX, startY + 9, 0x888888);

        mc.fontRenderer.drawStringWithShadow("§7Вставьте код из браузера сюда:", leftX, startY + 55, 0xCCCCCC);
        authCodeField.drawTextBox();

        int divY = startY + 111;
        drawRect(windowX + 40, divY + 2, windowX + WINDOW_WIDTH - 40, divY + 3, 0x22FFFFFF);

        String altText = "Или воспользуйтесь гостевым режимом";
        int altWidth = mc.fontRenderer.getStringWidth(altText);
        drawRect(windowX + (WINDOW_WIDTH - altWidth) / 2 - 4, divY - 2, windowX + (WINDOW_WIDTH + altWidth) / 2 + 4, divY + 7, 0xF0101010);
        mc.fontRenderer.drawStringWithShadow("§8" + altText, windowX + (WINDOW_WIDTH - altWidth) / 2, divY - 2, 0x888888);

        mc.fontRenderer.drawStringWithShadow("§8Доступно, если ник не занят другим игроком.", leftX, startY + 126, 0x888888);
    }

    private String getRankColor(int rank) {
        switch (rank) {
            case 0: return "§f";
            case 1: return "§e";
            case 2: return "§b";
            case 3: return "§c";
            default: return "§f";
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 102:
                closeToParent();
                return;
            case 101:
                if (!onlineMode.isAgreementAccepted()) {
                    onlineMode.showAgreementDialog(this);
                } else {
                    // Если соглашение уже принято, показываем его для просмотра
                    onlineMode.showAgreementDialog(this);
                    setStatus("Показано соглашение (принято)", false);
                }
                updateButtonStates();
                return;
            case 103:
                onlineMode.disconnect();
                setStatus("Вы отключены от сервера.", false);
                updateButtonStates();
                return;
            case 21:
                onlineMode.openDiscordAuthPage();
                setStatus("Браузер открыт. Разрешите доступ и скопируйте код.", false);
                return;
            case 22:
                doAuth();
                return;
            case 23:
                doGuestLogin();
                return;
        }
    }

    private void closeToParent() {
        mc.displayGuiScreen(parentScreen);
    }

    private void setStatus(String message, boolean isError) {
        statusMessage = message;
        statusIsError = isError;
    }

    private void doAuth() {
        if (busy) return;
        if (!requireAgreement(true)) return;

        String nick = onlineMode.getCurrentPlayerNick();
        String code = authCodeField.getText().trim();

        if (nick == null) {
            setStatus("Не удалось определить ник", true);
            return;
        }
        if (code.isEmpty()) {
            setStatus("Сначала получите и вставьте код из браузера", true);
            return;
        }

        busy = true;
        setStatus("Авторизация...", false);
        updateButtonStates();

        onlineMode.authenticateWithDiscord(nick, code).thenAccept(success -> {
            if (busy) {
                busy = false;
                if (!success && statusMessage.isEmpty()) {
                    setStatus("Не удалось выполнить вход. Проверьте код и попробуйте снова.", true);
                }
                updateButtonStates();
            }
        });
    }

    private void doGuestLogin() {
        if (busy) return;
        if (!requireAgreement(false)) return;

        String nick = onlineMode.getCurrentPlayerNick();
        if (nick == null) {
            setStatus("Не удалось определить ник", true);
            return;
        }

        busy = true;
        setStatus("Проверка никнейма и подключение...", false);
        updateButtonStates();

        onlineMode.connectAsGuest(nick).thenAccept(success -> {
            if (busy) {
                busy = false;
                if (!success && statusMessage.isEmpty()) {
                    setStatus("Не удалось войти как гость. Попробуйте снова.", true);
                }
                updateButtonStates();
            }
        });
    }

    private boolean requireAgreement(boolean isAuthAttempt) {
        if (!onlineMode.isAgreementAccepted()) {
            pendingActionWasAuth = isAuthAttempt;
            pendingActionWasGuest = !isAuthAttempt;
            onlineMode.showAgreementDialog(this);
            setStatus("Примите пользовательское соглашение", true);
            return false;
        }
        return true;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            closeToParent();
            return;
        }

        if (!onlineMode.isConnected() && authCodeField.isFocused()) {
            authCodeField.textboxKeyTyped(typedChar, keyCode);
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (!onlineMode.isConnected()) {
            authCodeField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        if (mouseButton == 0 && isHovered(mouseX, mouseY, windowX, windowY, WINDOW_WIDTH, 28)) {
            dragging = true;
            dragX = mouseX - windowX;
            dragY = mouseY - windowY;
            return;
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void mouseClickMove(int mouseX, int mouseY, int button, long time) {
        if (dragging) {
            windowX = mouseX - dragX;
            windowY = mouseY - dragY;
            repositionAll();
        }
        super.mouseClickMove(mouseX, mouseY, button, time);
    }

    private void repositionAll() {
        buttonList.clear();
        initTopBar();

        int fieldWidth = 320;
        int fieldX = windowX + (WINDOW_WIDTH - fieldWidth) / 2;
        int startY = windowY + 65;

        openAuthButton.x = fieldX;
        openAuthButton.y = startY + 26;
        buttonList.add(openAuthButton);

        authCodeField.x = fieldX;
        authCodeField.y = startY + 66;

        authSubmitButton.x = fieldX;
        authSubmitButton.y = startY + 88;
        buttonList.add(authSubmitButton);

        guestSubmitButton.x = fieldX;
        guestSubmitButton.y = startY + 138;
        buttonList.add(guestSubmitButton);

        updateButtonStates();
    }

    private boolean isHovered(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void updateButtonStates() {
        boolean agreed = onlineMode.isAgreementAccepted();
        boolean connected = onlineMode.isConnected();

        agreeButton.displayString = agreed ? "§aСоглашение ✓" : "§eСоглашение ✗";
        agreeButton.enabled = true; // всегда можно показать соглашение

        disconnectButton.visible = connected;
        disconnectButton.enabled = connected;

        openAuthButton.visible = !connected;
        authSubmitButton.visible = !connected;
        guestSubmitButton.visible = !connected;

        authSubmitButton.enabled = !connected && !busy;
        guestSubmitButton.enabled = !connected && !busy;
        openAuthButton.enabled = !busy;
    }
}