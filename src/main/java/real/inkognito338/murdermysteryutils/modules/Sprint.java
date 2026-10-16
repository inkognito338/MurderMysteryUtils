package real.inkognito338.murdermysteryutils.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemBow;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

@SuppressWarnings("SpellCheckingInspection")
@SideOnly(Side.CLIENT)
public class Sprint extends Module {
    private Minecraft mc;

    private boolean pendingRestore = false;
    private double savedMotionX = 0.0;
    private double savedMotionZ = 0.0;
    private boolean wasMoving = false;

    public Sprint() {
        super("Sprint");

        String[] modes = {"Always", "Forward"};
        this.addSetting(new Setting("Mode", SettingType.MODE, "Forward", modes));
        this.addSetting(new Setting("KeepSprint", SettingType.BOOLEAN, true));
        this.addSetting(new Setting("NoSprintBow", SettingType.BOOLEAN, true));
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
        mc = Minecraft.getMinecraft();
        pendingRestore = false;
        wasMoving = false;
        if (mc.player != null && !isDrawingBow()) {
            mc.player.setSprinting(true);
        }
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        pendingRestore = false;
        wasMoving = false;
        if (mc != null && mc.player != null) {
            mc.player.setSprinting(false);
        }
    }

    /**
     * true, если включена настройка NoSprintBow и игрок сейчас натягивает лук
     */
    private boolean isDrawingBow() {
        if (mc == null || mc.player == null) return false;
        if (!(boolean) getSettingByName("NoSprintBow").getValue()) return false;
        return mc.player.isHandActive()
                && mc.player.getActiveItemStack().getItem() instanceof ItemBow;
    }

    @SubscribeEvent
    public void onPlayerTickEnd(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (mc == null || mc.player == null || event.player != mc.player) return;
        if (pendingRestore) return;

        // Пока натягиваем лук — спринт выключен и не включается обратно
        if (isDrawingBow()) {
            if (mc.player.isSprinting()) {
                mc.player.setSprinting(false);
            }
            wasMoving = mc.player.moveForward != 0 || mc.player.moveStrafing != 0;
            return;
        }

        String mode = (String) getSettingByName("Mode").getValue();
        boolean moving = mc.player.moveForward != 0 || mc.player.moveStrafing != 0;

        if (mode.equals("Always")) {
            if (wasMoving && !moving) {
                // Только что остановились — сбросить и включить заново
                mc.player.setSprinting(false);
                mc.player.setSprinting(true);
            } else if (!mc.player.isSprinting()) {
                mc.player.setSprinting(true);
            }
            wasMoving = moving;
        } else {
            boolean hungry = mc.player.getFoodStats().getFoodLevel() <= 6
                    && !mc.player.capabilities.allowFlying;
            if (hungry) return;
            if (mc.player.isPotionActive(MobEffects.BLINDNESS)) return;
            if (mc.player.moveForward > 0 && !mc.player.isSprinting()) {
                mc.player.setSprinting(true);
            }
        }
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (mc == null || mc.player == null) return;
        if (event.getEntityPlayer() != mc.player) return;
        if (!(boolean) getSettingByName("KeepSprint").getValue()) return;
        if (!mc.player.isSprinting()) return;

        savedMotionX = mc.player.motionX;
        savedMotionZ = mc.player.motionZ;
        pendingRestore = true;
    }

    @SubscribeEvent
    public void onPlayerTickStart(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!pendingRestore) return;
        if (mc == null || mc.player == null || event.player != mc.player) return;

        pendingRestore = false;
        mc.player.motionX = savedMotionX;
        mc.player.motionZ = savedMotionZ;
        if (!isDrawingBow()) {
            mc.player.setSprinting(true);
        }
    }
}