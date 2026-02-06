package real.inkognito338.murdermysteryutils.modules.mm;

import net.minecraft.client.Minecraft;
import net.minecraft.world.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import real.inkognito338.murdermysteryutils.modules.Module;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class FakeGM1 extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();

    public FakeGM1() {
        super("FakeGM1");
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
    public void onTick(TickEvent.ClientTickEvent event) {
        if (mc.playerController != null) {
            mc.playerController.setGameType(GameType.CREATIVE);
            mc.player.capabilities.allowFlying = false;
            mc.player.capabilities.isFlying = false;
        }
    }
}