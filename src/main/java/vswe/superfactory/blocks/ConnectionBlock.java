package vswe.superfactory.blocks;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.Localization;
import vswe.superfactory.components.ComponentMenuContainer;
import vswe.superfactory.components.internal.IContainerSelection;
import vswe.superfactory.components.Variable;
import vswe.superfactory.interfaces.GuiManager;
import vswe.superfactory.tiles.TileEntityManager;

import java.util.EnumSet;

public class ConnectionBlock implements IContainerSelection {
	private int                          cableDistance;
	private int                          id;
	private TileEntity                   tileEntity;
	private EnumSet<ConnectionBlockType> types;

	public ConnectionBlock(TileEntity tileEntity, int cableDistance) {
		this.tileEntity = tileEntity;
		types = EnumSet.noneOf(ConnectionBlockType.class);
		this.cableDistance = cableDistance;
	}

	public void addType(ConnectionBlockType type) {
		types.add(type);
	}

	public boolean isOfAnyType(EnumSet<ConnectionBlockType> types) {
		for (ConnectionBlockType type : types) {
			if (isOfType(type)) {
				return true;
			}
		}

		return false;
	}

	public boolean isOfType(ConnectionBlockType type) {
		return isOfType(this.types, type);
	}

	public static boolean isOfType(EnumSet<ConnectionBlockType> types, ConnectionBlockType type) {
		return type == null || types.contains(type) || (type == ConnectionBlockType.NODE && (types.contains(ConnectionBlockType.RECEIVER) || types.contains(ConnectionBlockType.EMITTER)));
	}

	public TileEntity getTileEntity() {
		return tileEntity;
	}

	@Override
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	@Override
	public void draw(GuiManager gui, int x, int y) {
		gui.drawBlock(tileEntity, x, y);
	}

	@Override
	public String getDescription(GuiManager gui) {
		String str = gui.getBlockName(tileEntity);

		str += getVariableTag(gui);

		str += "\n" + Localization.X + ": " + tileEntity.getPos().getX() + " " + Localization.Y + ": " + tileEntity.getPos().getY() + " " + Localization.Z + ": " + tileEntity.getPos().getZ();
		int distance = getDistance(gui.getManager());
		str += "\n" + distance + " " + (distance > 1 ? Localization.BLOCKS_AWAY : Localization.BLOCK_AWAY);
		str += "\n" + cableDistance + " " + (cableDistance > 1 ? Localization.CABLES_AWAY : Localization.CABLE_AWAY);

		return str;
	}


	public int getDistance(TileEntityManager manager) {
		return (int) Math.round(Math.sqrt(manager.getDistanceSq(tileEntity.getPos().getX() + 0.5, tileEntity.getPos().getY() + 0.5, tileEntity.getPos().getZ() + 0.5)));
	}

	@Override
	public String getName(GuiManager gui) {
		return gui.getBlockName(tileEntity);
	}

	@Override
	public boolean isVariable() {
		return false;
	}

	@SideOnly(Side.CLIENT)
	private String getVariableTag(GuiManager gui) {
		int    count  = 0;
		String result = "";

		if (GuiScreen.isShiftKeyDown()) {
			for (Variable variable : gui.getManager().getVariables()) {
				if (isPartOfVariable(variable)) {
					result += "\n" + variable.getDescription(gui);
					count++;
				}
			}
		}

		return count == 0 ? "" : result;
	}

	@SideOnly(Side.CLIENT)
	public boolean isPartOfVariable(Variable variable) {
		return variable.isValid() && ((ComponentMenuContainer) variable.getDeclaration().getMenus().get(2)).getSelectedInventories().contains(id);
	}

	public int getCableDistance() {
		return cableDistance;
	}
}
