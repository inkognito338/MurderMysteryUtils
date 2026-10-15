package real.inkognito338.murdermysteryutils.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiEditSign;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MovementInput;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import real.inkognito338.murdermysteryutils.utils.Module;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 14.10.2026
 */

@SuppressWarnings("SpellCheckingInspection")
@SideOnly(Side.CLIENT)
public class InvMove extends Module {
    private Minecraft mc;

    public InvMove() {
        super("InvMove");
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
        mc = Minecraft.getMinecraft();
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    /**
     * В 1.12.2 KeyBinding.isKeyDown() возвращает false при открытом GUI (KeyConflictContext.IN_GAME),
     * поэтому подменяем MovementInput напрямую - читаем клавиши с клавиатуры.
     */
    @SubscribeEvent
    public void onInputUpdate(InputUpdateEvent event) {
        if (mc == null || mc.player == null || mc.world == null) return;

        GuiScreen screen = mc.currentScreen;

        // Работаем в любом GUI, кроме чата и редактирования таблички
        if (screen == null) return;
        if (screen instanceof GuiChat || screen instanceof GuiEditSign) return;

        MovementInput input = event.getMovementInput();

        boolean forward = isDown(mc.gameSettings.keyBindForward);
        boolean back = isDown(mc.gameSettings.keyBindBack);
        boolean left = isDown(mc.gameSettings.keyBindLeft);
        boolean right = isDown(mc.gameSettings.keyBindRight);

        input.forwardKeyDown = forward;
        input.backKeyDown = back;
        input.leftKeyDown = left;
        input.rightKeyDown = right;

        input.moveForward = forward == back ? 0.0F : (forward ? 1.0F : -1.0F);
        input.moveStrafe = left == right ? 0.0F : (left ? 1.0F : -1.0F);

        input.jump = isDown(mc.gameSettings.keyBindJump);
        input.sneak = isDown(mc.gameSettings.keyBindSneak);

        // Как в ванили: замедление при приседании
        if (input.sneak) {
            input.moveStrafe *= 0.3F;
            input.moveForward *= 0.3F;
        }

        // Спринт: ваниль тоже проверяет keyBindSprint.isKeyDown(), который в GUI не работает
        if (isDown(mc.gameSettings.keyBindSprint)
                && input.moveForward >= 0.8F
                && !input.sneak
                && !mc.player.isSprinting()
                && !mc.player.collidedHorizontally
                && !mc.player.isHandActive()
                && (mc.player.getFoodStats().getFoodLevel() > 6 || mc.player.capabilities.allowFlying)) {
            mc.player.setSprinting(true);
        }
    }

    private boolean isDown(KeyBinding key) {
        int code = key.getKeyCode();

        if (code > 0 && code < Keyboard.KEYBOARD_SIZE) {
            return Keyboard.isKeyDown(code);
        }

        // Мышиные кнопки имеют отрицательные коды (-100 = ЛКМ и т.д.)
        if (code < 0) {
            int button = code + 100;
            return button >= 0 && button < Mouse.getButtonCount() && Mouse.isButtonDown(button);
        }

        return false;
    }
}