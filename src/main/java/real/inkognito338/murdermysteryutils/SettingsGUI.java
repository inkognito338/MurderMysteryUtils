package real.inkognito338.murdermysteryutils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


// by inkognito338 | forge 1.12.2 - 14.23.5.2860

public class SettingsGUI extends GuiScreen {
    private final Minecraft mc = Minecraft.getMinecraft();

    // Позиция окна настроек (загружается из ConfigManager)
    private static int categoryX = ConfigManager.getCategoryX();
    private static int categoryY = ConfigManager.getCategoryY();
    private static boolean dragging = false;
    private static int dragX, dragY;

    // Список кнопок
    private static final List<FunctionButton> functionButtons = new ArrayList<>();

    @Override
    public void initGui() {
        this.buttonList.clear();
        functionButtons.clear();

        int offsetY = 25;
        for (SettingsOption option : SettingsOption.values()) {
            FunctionButton button = new FunctionButton(option, categoryX + 10, categoryY + offsetY);
            functionButtons.add(button);
            this.buttonList.add(button);
            offsetY += 25;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button instanceof FunctionButton) {
            FunctionButton functionButton = (FunctionButton) button;
            functionButton.toggle();
            ConfigManager.saveSettings(categoryX, categoryY, ConfigManager.getResetMessages());
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        int categoryWidth = 160;
        int categoryHeight = functionButtons.size() * 25 + 25;

        GlStateManager.pushMatrix();

        // Заголовок
        drawRect(categoryX, categoryY, categoryX + categoryWidth, categoryY + 20, 0xFF9933CC);
        mc.fontRenderer.drawStringWithShadow("MurderMystery Utils", categoryX + (categoryWidth / 2) - (mc.fontRenderer.getStringWidth("MurderMystery Utils") / 2), categoryY + 6, 0xFFFFFF);

        // Основной фон
        drawRect(categoryX, categoryY + 20, categoryX + categoryWidth, categoryY + categoryHeight, 0x99000000);

        GlStateManager.popMatrix();
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        try {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mouseButton == 0 && isMouseOverCategory(mouseX, mouseY)) {
            dragging = true;
            dragX = mouseX - categoryX;
            dragY = mouseY - categoryY;
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (state == 0) {
            dragging = false;
            ConfigManager.saveSettings(categoryX, categoryY, ConfigManager.getResetMessages());
        }
    }


    @Override
    public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (dragging) {
            categoryX = mouseX - dragX;
            categoryY = mouseY - dragY;
            updateButtonPositions();
        }
    }

    private boolean isMouseOverCategory(int mouseX, int mouseY) {
        return mouseX >= categoryX && mouseX <= categoryX + 160 && mouseY >= categoryY && mouseY <= categoryY + 20;
    }

    private void updateButtonPositions() {
        int offsetY = 25;
        for (FunctionButton button : functionButtons) {
            button.x = categoryX + 10;
            button.y = categoryY + offsetY;
            offsetY += 25;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // Класс для кнопок
    private static class FunctionButton extends GuiButton {
        private final SettingsOption option;

        public FunctionButton(SettingsOption option, int x, int y) {
            super(option.ordinal(), x, y, 150, 20, option.getDisplayName());
            this.option = option;
            this.updateDisplayString();
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (this.visible) {
                int textColor = option.getValue() ? 0x00FF00 : 0xFFFFFF;
                mc.fontRenderer.drawStringWithShadow(this.displayString, this.x + 5, this.y + 6, textColor);
            }
        }

        public void toggle() {
            option.toggle();
            this.updateDisplayString();
        }

        private void updateDisplayString() {
            this.displayString = option.getDisplayName();
        }
    }
}
