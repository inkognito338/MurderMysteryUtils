package real.inkognito338.murdermysteryutils.modules.mm;

import net.minecraft.client.Minecraft;
import net.minecraft.init.MobEffects;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import real.inkognito338.murdermysteryutils.modules.Module;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class AntiBlind extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();

    public AntiBlind() {
        super("AntiBlind");
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (mc.player == null) return;

        if (mc.player.isPotionActive(MobEffects.BLINDNESS)) {
            mc.player.removePotionEffect(MobEffects.BLINDNESS);
        }

        if (mc.player.isPotionActive(MobEffects.NAUSEA)) {
            mc.player.removePotionEffect(MobEffects.NAUSEA);
        }

        mc.player.timeInPortal = 0;
    }
}
