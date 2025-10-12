package real.inkognito338.murdermysteryutils;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

// by inkognito338 | forge 1.12.2 - 14.23.5.2860


public class MurderMysteryTracker {
    private final Minecraft mc = Minecraft.getMinecraft();
    private EntityPlayer murderer = null;
    private EntityPlayer detective = null;

    public void update() {
        if (mc.world == null) return;

        EntityPlayer newMurderer = null;
        EntityPlayer newDetective = null;

        // Iterate through all players in the world
        for (EntityPlayer entity : mc.world.playerEntities) {
            if (isMurderer(entity)) {
                newMurderer = entity;
            }
            if (isDetective(entity)) {
                newDetective = entity;
            }
        }

        // Update references if new entities are found
        if (newMurderer != null) {
            murderer = newMurderer;
        }
        if (newDetective != null) {
            detective = newDetective;
        }
    }

    private boolean isMurderer(EntityPlayer player) {
        // Check if the player has the selected murderer item (either iron sword or shears)
        for (ItemStack stack : player.getHeldEquipment()) {
            if (!stack.isEmpty() && isMurdererItem(stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMurdererItem(ItemStack stack) {
        return (SettingsOption.MURDERER_ITEM_SWORD.getValue() && stack.getItem() == Items.IRON_SWORD) ||
                (SettingsOption.MURDERER_ITEM_SHEARS.getValue() && stack.getItem() == Items.SHEARS);
    }


    private boolean isDetective(EntityPlayer player) {
        // Check if the player has a bow in any equipment slot (main hand, off-hand, or armor)
        for (ItemStack stack : player.getHeldEquipment()) {
            if (!stack.isEmpty() && stack.getItem() == Items.BOW) {
                return true;
            }
        }
        return false;
    }

    public EntityPlayer getMurderer() {
        return murderer;
    }

    public EntityPlayer getDetective() {
        return detective;
    }

    public String getMurdererName() {
        return murderer != null ? murderer.getName() : "Unknown";
    }

    public String getDetectiveName() {
        return detective != null ? detective.getName() : "Unknown";
    }

}