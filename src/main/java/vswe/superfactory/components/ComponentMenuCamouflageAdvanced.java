package vswe.superfactory.components;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.CollisionHelper;
import vswe.superfactory.interfaces.GuiManager;

public abstract class ComponentMenuCamouflageAdvanced extends ComponentMenu {
	private static final int ERROR_SIZE_H = 10;
	private static final int ERROR_SIZE_W = 2;
	private static final int ERROR_SRC_X  = 44;
	private static final int ERROR_SRC_Y  = 212;
	private static final int ERROR_X      = 115;
	private static final int ERROR_Y      = 2;
	public ComponentMenuCamouflageAdvanced(FlowComponent parent) {
		super(parent);
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void draw(GuiManager gui, int mX, int mY) {
		int srcY = CollisionHelper.inBounds(ERROR_X, ERROR_Y, ERROR_SIZE_W, ERROR_SIZE_H, mX, mY) ? 1 : 0;
		gui.drawTexture(ERROR_X, ERROR_Y, ERROR_SRC_X, ERROR_SRC_Y + srcY * ERROR_SIZE_H, ERROR_SIZE_W, ERROR_SIZE_H);
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void drawMouseOver(GuiManager gui, int mX, int mY) {
		if (CollisionHelper.inBounds(ERROR_X, ERROR_Y, ERROR_SIZE_W, ERROR_SIZE_H, mX, mY)) {
			gui.drawMouseOver(getWarningText(), mX, mY, 200);
		}
	}

	protected abstract String getWarningText();
}
