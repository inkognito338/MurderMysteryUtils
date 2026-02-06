package real.inkognito338.murdermysteryutils.modules.mm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import real.inkognito338.murdermysteryutils.modules.Module;

@SuppressWarnings("SpellCheckingInspection")
public class Fly extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public Fly() {
        super("Fly");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        MinecraftForge.EVENT_BUS.unregister(this);

        // Отключаем полёт при выключении модуля
        if (mc.player != null) {
            mc.player.capabilities.isFlying = false;
            if (!mc.player.capabilities.isCreativeMode) {
                mc.player.capabilities.allowFlying = false;
            }
        }
    }

    @SubscribeEvent
    public void onInputUpdate(InputUpdateEvent event) {
        if (!this.isToggled() || mc.player == null) return;

        EntityPlayerSP player = mc.player;

        if (player.capabilities.allowFlying) {
            // Сервер разрешил летать — не трогаем
            return;
        }

        // Включаем читерский полёт
        player.capabilities.isFlying = true;
    }
}