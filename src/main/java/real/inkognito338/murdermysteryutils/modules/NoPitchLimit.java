package real.inkognito338.murdermysteryutils.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.Display;
import real.inkognito338.murdermysteryutils.utils.Module;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 15.10.2026
 */

@SideOnly(Side.CLIENT)
public class NoPitchLimit extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();

    /** Pitch без ограничений (градусы, -180..180). */
    private float virtualPitch;

    /** Реальный pitch на конец прошлого кадра — для обнаружения внешних изменений. */
    private float lastReal;

    /** Добавка к углу камеры в текущем кадре. */
    private float viewShift;

    private boolean initialized;
    private boolean updatedThisFrame;

    public NoPitchLimit() {
        super("NoPitchLimit");
    }

    @Override
    public void onEnable() {
        initialized = false;
        updatedThisFrame = false;
        viewShift = 0.0F;
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        initialized = false;
        viewShift = 0.0F;
    }

    // ── Кадр ─────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            updatedThisFrame = false;
            return;
        }

        // Конец кадра
        if (mc.player == null || !updatedThisFrame) {
            // Мир в этом кадре не рисовался (или смотрим не от своего лица) — пересинхронизируемся
            initialized = false;
            return;
        }

        // Возвращаем реальный pitch в допустимые пределы, сохраняя плавность интерполяции
        float target = MathHelper.clamp(virtualPitch, -90.0F, 90.0F);
        float diff = target - mc.player.rotationPitch;
        if (diff != 0.0F) {
            mc.player.rotationPitch += diff;
            mc.player.prevRotationPitch += diff;
        }
        lastReal = mc.player.rotationPitch;
    }

    @SubscribeEvent
    public void onCameraSetup(EntityViewRenderEvent.CameraSetup event) {
        if (mc.player == null || event.getEntity() != mc.player) return;

        if (!updatedThisFrame) {
            updatedThisFrame = true;
            updateVirtualPitch();
        }

        if (viewShift != 0.0F) {
            event.setPitch(event.getPitch() + viewShift);
        }
    }

    /**
     * Обновляет виртуальный pitch один раз за кадр.
     * К этому моменту ваниль уже применила поворот мыши и зажала реальный pitch.
     */
    private void updateVirtualPitch() {
        float vanillaReal = mc.player.rotationPitch;
        viewShift = 0.0F;

        if (!initialized) {
            virtualPitch = vanillaReal;
            initialized = true;
            return;
        }

        // Сколько градусов pitch ваниль отняла бы за этот кадр (та же формула, что в игре)
        float applied = 0.0F;
        boolean focused = mc.inGameHasFocus && mc.currentScreen == null && Display.isActive();
        if (focused && !mc.gameSettings.smoothCamera) {
            float f = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
            float f1 = f * f * f * 8.0F;
            int invert = mc.gameSettings.invertMouse ? -1 : 1;
            applied = mc.mouseHelper.deltaY * f1 * invert * 0.15F;
        }

        // Если реальный pitch изменился не от мыши (телепорт, серверный разворот) — берём его
        float expected = MathHelper.clamp(lastReal - applied, -90.0F, 90.0F);
        if (Math.abs(vanillaReal - expected) > 0.05F) {
            virtualPitch = vanillaReal;
        } else {
            virtualPitch = MathHelper.wrapDegrees(virtualPitch - applied);
        }

        viewShift = virtualPitch - vanillaReal;
    }
}