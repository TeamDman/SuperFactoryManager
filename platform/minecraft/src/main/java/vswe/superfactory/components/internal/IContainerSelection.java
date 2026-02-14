package vswe.superfactory.components.internal;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.interfaces.GuiManager;

public interface IContainerSelection {
	int getId();

	@SideOnly(Side.CLIENT)
	void draw(GuiManager gui, int x, int y);

	@SideOnly(Side.CLIENT)
	String getDescription(GuiManager gui);

	@SideOnly(Side.CLIENT)
	String getName(GuiManager gui);

	boolean isVariable(); //fast access
}
