package vswe.superfactory.network.messages;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import vswe.superfactory.SuperFactoryManager;

/**
 * This class enables the following behaviour:
 * Send a packet to players when they join that tells their client to index items for searching
 */
@Mod.EventBusSubscriber
public class MessageHandler {

	public static final SimpleNetworkWrapper INSTANCE = new SimpleNetworkWrapper(SuperFactoryManager.MODID + ".visual");

	public static void init() {
		INSTANCE.registerMessage(MessageIndexItems.class, MessageIndexItems.class, 6, Side.CLIENT);
	}

	@SubscribeEvent
	public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.player instanceof EntityPlayerMP) {
			INSTANCE.sendTo(new MessageIndexItems(), (EntityPlayerMP) event.player);
		}
	}

}
