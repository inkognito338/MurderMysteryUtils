package real.inkognito338.murdermysteryutils.utils.gui;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 20.07.2026
 */

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import real.inkognito338.murdermysteryutils.online.OnlineMode;

import java.io.IOException;

/**
 * GUI соглашения для онлайн-режима.
 *
 * Всегда открывается поверх некоторого родительского экрана (обычно
 * SettingsGUI или OnlineModeGUI) и по завершении (Принять / Отклонить / ESC)
 * возвращается обратно в него, а не закрывает весь интерфейс.
 */
public class AgreementGui extends GuiScreen {

    private static final String AGREEMENT_TEXT =
            "§6§lСОГЛАШЕНИЕ ОБ ОНЛАЙН-РЕЖИМЕ\n\n" +
                    "§7Включая онлайн-режим, вы соглашаетесь на:\n\n" +
                    "§7• Передачу вашего §fникнейма Minecraft§7 на сервер\n" +
                    "§7• Передачу вашего §fDiscord ID и имени пользователя§7 (если привязан)\n" +
                    "§7• Передачу §fадреса игрового сервера§7, на котором вы находитесь\n" +
                    "§7• Передачу §fсообщений чата§7, отправленных через мод\n" +
                    "§eЭти данные используются для:\n" +
                    "§7• Включения кросс-пользовательского чата\n" +
                    "§7• Отображения рангов и префиксов пользователей\n" +
                    "§7• Анимаций в CustomTab\n" +
                    "§7• Списка онлайн-пользователей\n" +
                    "§7• Модерации пользователей и предотвращения злоупотреблений\n\n" +
                    "§cВы можете отключить онлайн-режим в любой момент.\n\n" +
                    "§aВы принимаете эти условия?";

    private final GuiScreen returnTo;

    private GuiButton acceptButton;
    private GuiButton declineButton;
    private int scrollY = 0;
    private boolean dragging = false;
    private int dragX, dragY;
    private int windowX, windowY;
    private int windowWidth = 420;
    private int windowHeight = 320;

    /**
     * @param returnTo экран, на который нужно вернуться после закрытия этого диалога
     *                 (Принять / Отклонить / ESC). Может быть null — тогда диалог
     *                 закрывается полностью, как раньше.
     */
    public AgreementGui(GuiScreen returnTo) {
        this.returnTo = returnTo;
    }

    /** Оставлено для обратной совместимости с местами, где родитель неизвестен. */
    public AgreementGui() {
        this(null);
    }

    @Override
    public void initGui() {
        int centerX = width / 2;
        int centerY = height / 2;
        windowX = centerX - windowWidth / 2;
        windowY = centerY - windowHeight / 2;

        acceptButton = new GuiButton(1, windowX + 50, windowY + windowHeight - 35, 130, 20, "§aПринять");
        declineButton = new GuiButton(2, windowX + windowWidth - 180, windowY + windowHeight - 35, 130, 20, "§cОтклонить");
        buttonList.add(acceptButton);
        buttonList.add(declineButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        // Тень
        for (int i = 1; i <= 8; i++) {
            drawRect(windowX - i, windowY - i, windowX + windowWidth + i, windowY + windowHeight + i,
                    0x11000000);
        }

        // Основное окно
        drawRect(windowX, windowY, windowX + windowWidth, windowY + windowHeight, 0xF0101010);
        drawRect(windowX, windowY, windowX + windowWidth, windowY + 32, 0xF0202020);

        // Заголовок
        String title = "§b§lСоглашение об онлайн-режиме";
        int titleWidth = mc.fontRenderer.getStringWidth(title);
        mc.fontRenderer.drawStringWithShadow(title, windowX + (windowWidth - titleWidth) / 2, windowY + 10, 0xFFFFFF);

        // Линия
        drawRect(windowX, windowY + 32, windowX + windowWidth, windowY + 33, 0x40FFFFFF);

        // Текст соглашения (с прокруткой)
        int textX = windowX + 20;
        int textY = windowY + 45 + scrollY;
        int textWidth = windowWidth - 40;
        int maxTextY = windowY + windowHeight - 50;

        // Отсечение текста
        drawRect(windowX, windowY + 33, windowX + windowWidth, maxTextY, 0xF0101010);

        // Рисуем текст построчно
        String[] lines = AGREEMENT_TEXT.split("\n");
        for (String line : lines) {
            if (textY + mc.fontRenderer.FONT_HEIGHT < windowY + 33 || textY > maxTextY) {
                textY += mc.fontRenderer.FONT_HEIGHT;
                continue;
            }

            // Обработка цветов
            String display = line;
            if (display.startsWith("§6§l")) {
                mc.fontRenderer.drawStringWithShadow(display, textX, textY, 0xFFAA00);
            } else if (display.startsWith("§7")) {
                mc.fontRenderer.drawStringWithShadow(display, textX + 8, textY, 0xCCCCCC);
            } else if (display.startsWith("§e")) {
                mc.fontRenderer.drawStringWithShadow(display, textX + 8, textY, 0xFFFF00);
            } else if (display.startsWith("§c")) {
                mc.fontRenderer.drawStringWithShadow(display, textX + 8, textY, 0xFF4444);
            } else if (display.startsWith("§a")) {
                mc.fontRenderer.drawStringWithShadow(display, textX + 8, textY, 0x44FF44);
            } else {
                mc.fontRenderer.drawStringWithShadow(display, textX, textY, 0xFFFFFF);
            }
            textY += mc.fontRenderer.FONT_HEIGHT;
        }

        // Кнопки
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Индикатор прокрутки
        int totalHeight = textY - (windowY + 45);
        int visibleHeight = maxTextY - (windowY + 33);
        if (totalHeight > visibleHeight) {
            float scrollPercent = (float)(-scrollY) / (totalHeight - visibleHeight);
            int scrollBarHeight = Math.max(10, visibleHeight - 20);
            int scrollBarY = windowY + 40 + (int)(scrollPercent * (visibleHeight - scrollBarHeight - 10));
            drawRect(windowX + windowWidth - 6, scrollBarY, windowX + windowWidth - 4, scrollBarY + scrollBarHeight, 0x80FFFFFF);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 1) {
            OnlineMode.getInstance().acceptAgreement();
            closeToParent();
        } else if (button.id == 2) {
            closeToParent();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == org.lwjgl.input.Keyboard.KEY_ESCAPE) {
            // ESC отклоняет диалог и возвращает на родительский экран, а не
            // закрывает весь интерфейс.
            closeToParent();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    /**
     * Возвращает игрока на экран, с которого был открыт этот диалог, если он
     * известен; иначе закрывает интерфейс целиком (прежнее поведение).
     */
    private void closeToParent() {
        mc.displayGuiScreen(returnTo);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dw = org.lwjgl.input.Mouse.getDWheel();
        if (dw != 0) {
            int scrollAmount = dw > 0 ? -20 : 20;
            scrollY += scrollAmount;

            int maxScroll = Math.max(0, (AGREEMENT_TEXT.split("\n").length * mc.fontRenderer.FONT_HEIGHT + 20) - (windowHeight - 70));
            scrollY = Math.max(Math.min(scrollY, 0), -maxScroll);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton == 0 && isHovered(mouseX, mouseY, windowX, windowY, windowWidth, 32)) {
            dragging = true;
            dragX = mouseX - windowX;
            dragY = mouseY - windowY;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = false;
    }

    @Override
    public void mouseClickMove(int mouseX, int mouseY, int button, long time) {
        if (dragging) {
            windowX = mouseX - dragX;
            windowY = mouseY - dragY;
        }
    }

    private boolean isHovered(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}