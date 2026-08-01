package real.inkognito338.murdermysteryutils.modules;

import net.minecraftforge.common.MinecraftForge;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import real.inkognito338.murdermysteryutils.utils.Module;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

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
