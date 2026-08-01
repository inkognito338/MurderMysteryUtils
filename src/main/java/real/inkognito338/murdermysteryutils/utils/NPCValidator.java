package real.inkognito338.murdermysteryutils.utils;

import net.minecraft.entity.player.EntityPlayer;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

public final class NPCValidator {

    private NPCValidator() {}

    /**
     * Проверяет, является ли игрок NPC
     * @return true если игрок является NPC
     */
    public static boolean isNPC(String name) {
        if (name == null || name.isEmpty()) return true;

        name = name.replaceAll("(?i)&[0-9a-fklmnor]", "");
        name = name.replaceAll("(?i)§[0-9a-fklmnor]", "");

        if (name.isEmpty()) return true;

        return name.contains(" ") ||
                name.contains("-") ||
                name.contains("#") ||
                name.contains("[") ||
                name.contains("]") ||
                name.contains("(") ||
                name.contains(")") ||
                name.contains("{") ||
                name.contains("}") ||
                name.contains("<") ||
                name.contains(">") ||
                name.contains("|") ||
                name.contains("\\") ||
                name.contains("/") ||
                name.contains(":") ||
                name.contains(";") ||
                name.contains("'") ||
                name.contains("\"") ||
                name.contains(".") ||
                name.contains(",") ||
                name.contains("?") ||
                name.contains("!") ||
                name.contains("@") ||
                name.contains("$") ||
                name.contains("%") ||
                name.contains("^") ||
                name.contains("&") ||
                name.contains("*") ||
                name.contains("=") ||
                name.contains("+") ||
                name.contains("~") ||
                name.contains("`");
    }

    public static boolean isNPC(EntityPlayer player) {
        if (player == null) return true;
        return isNPC(player.getName());
    }
}