package real.inkognito338.murdermysteryutils.utils;


import net.minecraft.util.text.ITextComponent;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 04.07.2026

 * Обёртка для сообщения, передаваемая в Lua
 * Содержит полную информацию о сообщении: очищенный текст, форматированный текст, JSON
 */
public class MessageWrapper {

    private final String text;        // Очищенный текст без цветов
    private final String formatted;   // Текст с цветами (§)
    private final String raw;         // Оригинальный JSON
    private final ITextComponent component; // Оригинальный объект

    public MessageWrapper(ITextComponent component) {
        this.component = component;
        this.text = component != null ? component.getUnformattedText() : "";
        this.formatted = component != null ? component.getFormattedText() : "";
        this.raw = component != null ? ITextComponent.Serializer.componentToJson(component) : "";
    }

    public String getText() {
        return text;
    }

    public String getFormatted() {
        return formatted;
    }

    public String getRaw() {
        return raw;
    }

    public ITextComponent getComponent() {
        return component;
    }

    @Override
    public String toString() {
        return text;
    }
}