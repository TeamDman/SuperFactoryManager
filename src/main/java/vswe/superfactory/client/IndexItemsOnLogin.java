package vswe.superfactory.client;

import ca.teamdman.sfm.SFM;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.util.SearchUtil;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(Side.CLIENT)
public class IndexItemsOnLogin {

    @SubscribeEvent
    public static void onPlayerLogin(FMLNetworkEvent.ClientConnectedToServerEvent event) {
       	if ((Launch.blackboard.get("fml.deobfuscatedEnvironment") != null || SearchUtil.getCache().isEmpty())) {
            SearchUtil.buildCache();
		}
    }

}
