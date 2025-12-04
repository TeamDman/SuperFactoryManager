package vswe.superfactory.network.messages;

import io.netty.buffer.ByteBuf;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import vswe.superfactory.util.SearchUtil;

/**
 * A message class used to send a packet to a player that triggers an item cache rebuild
 * See also: {@link MessageHandler}
 */
public class MessageIndexItems implements IMessage, IMessageHandler<MessageIndexItems, IMessage> {
	@Override
	public void fromBytes(ByteBuf buf) {}

	@Override
	public void toBytes(ByteBuf buf) {}

	@Override
	public IMessage onMessage(MessageIndexItems message, MessageContext ctx) {
		if (ctx.side == Side.CLIENT && (Launch.blackboard.get("fml.deobfuscatedEnvironment") != null || SearchUtil.getCache().isEmpty())) {
			SearchUtil.buildCache();
		}
		return null;
	}
}
