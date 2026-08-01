package real.inkognito338.murdermysteryutils.mixin;

import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import real.inkognito338.murdermysteryutils.modules.NameProtect;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 10.07.2026
 */

@Mixin(GuiIngame.class)
public abstract class MixinGuiIngame {

    @ModifyArg(
            method = "renderScoreboard",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I"
            ),
            index = 0
    )
    private String replaceScoreboardText(String text) {
        NameProtect module = NameProtect.getInstance();
        if (module == null || !module.isToggled() || text == null) {
            return text;
        }

        String real = NameProtect.getRealName();
        String fake = NameProtect.getFakeName();

        if (real == null || real.isEmpty() || fake == null || real.equals(fake)) {
            // Ничего не подставляем — либо не задано, либо совпадает
            return text;
        }

        // Строим regex, который матчит символы реального ника,
        // допуская между каждым символом любые §-коды форматирования (0 или больше)
        StringBuilder patternBuilder = new StringBuilder();
        for (char c : real.toCharArray()) {
            patternBuilder.append("(?:§.)*").append(java.util.regex.Pattern.quote(String.valueOf(c)));
        }
        patternBuilder.append("(?:§.)*");

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(patternBuilder.toString());
        java.util.regex.Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            String matched = matcher.group();
            String replaced = text.substring(0, matcher.start())
                    + fake
                    + text.substring(matcher.end());

            return replaced;
        }

        return text;
    }
}