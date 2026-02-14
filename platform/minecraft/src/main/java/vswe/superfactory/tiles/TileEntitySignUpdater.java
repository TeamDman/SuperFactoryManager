package vswe.superfactory.tiles;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import vswe.superfactory.blocks.ClusterMethodRegistration;
import vswe.superfactory.components.ComponentMenuSignText;

import java.util.EnumSet;

public class TileEntitySignUpdater extends TileEntityClusterElement {
	@Override
	protected EnumSet<ClusterMethodRegistration> getRegistrations() {
		return EnumSet.of(ClusterMethodRegistration.ON_BLOCK_PLACED_BY);
	}

	public void updateSign(ComponentMenuSignText menu) {
		EnumFacing direction = EnumFacing.byIndex(getBlockMetadata() % EnumFacing.values().length);
		TileEntity te        = world.getTileEntity(new BlockPos(getPos().getX() + direction.getXOffset(), getPos().getY() + direction.getYOffset(), getPos().getZ() + direction.getZOffset()));
		if (te instanceof TileEntitySign) {
			TileEntitySign sign = (TileEntitySign) te;
			sign.setPlayer(null);
			boolean updated = false;
			for (int i = 0; i < 4; i++) {
				if (menu.shouldUpdate(i)) {
					ITextComponent      oldText = sign.signText[i];
					TextComponentString newText = new TextComponentString(menu.getText(i));
					if (!newText.equals(oldText)) {
						sign.signText[i] = newText;
						updated = true;
					}
				}
			}
			if (updated) {
				sign.markDirty();
				world.notifyBlockUpdate(sign.getPos(), getWorld().getBlockState(sign.getPos()), getWorld().getBlockState(sign.getPos()), 3);
			}
		}
	}
}
