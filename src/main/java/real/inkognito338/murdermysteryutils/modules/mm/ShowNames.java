package real.inkognito338.murdermysteryutils.modules.mm;

import net.minecraftforge.common.MinecraftForge;
import real.inkognito338.murdermysteryutils.modules.Module;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ShowNames extends Module {

    private static ShowNames INSTANCE;

    public ShowNames() {
        super("ShowNames");
        INSTANCE = this;
    }

    public static ShowNames getInstance() {
        return INSTANCE;
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }
}
