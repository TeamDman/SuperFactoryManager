package vswe.superfactory.components;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.CollisionHelper;
import vswe.superfactory.Localization;
import vswe.superfactory.interfaces.GuiManager;

public abstract class VariableDisplay {
	private static final int ARROW_HEIGHT  = 10;
	private static final int ARROW_SRC_X   = 18;
	private static final int ARROW_SRC_Y   = 20;
	private static final int ARROW_WIDTH   = 6;
	private static final int ARROW_X_RIGHT = 38;
	private static final int ARROW_Y       = 3;
	private static final int TEXT_X = -40;
	private static final int TEXT_Y = 5;
	private static final int VARIABLE_SIZE = 14;
	private static final int VARIABLE_X    = 15;
	private Localization name;
	private int          x;
	private int          y;

	protected VariableDisplay(Localization name, int x, int y) {
		this.name = name;
		this.x = x;
		this.y = y;
	}

	@SideOnly(Side.CLIENT)
	public void draw(GuiManager gui, int mX, int mY) {
		if (name != null) {
			gui.drawString(name.toString(), x + TEXT_X, y + TEXT_Y, 0x404040);
		}

		gui.getManager().getVariables()[getValue()].draw(gui, x + VARIABLE_X, y);

		for (int i = 0; i < 2; i++) {
			int posX = x + (i == 0 ? 0 : ARROW_X_RIGHT);
			int posY = y + ARROW_Y;

			int srcXArrow = i;
			int srcYArrow = CollisionHelper.inBounds(posX, posY, ARROW_WIDTH, ARROW_HEIGHT, mX, mY) ? 1 : 0;

			gui.drawTexture(posX, posY, ARROW_SRC_X + srcXArrow * ARROW_WIDTH, ARROW_SRC_Y + srcYArrow * ARROW_HEIGHT, ARROW_WIDTH, ARROW_HEIGHT);
		}
	}

	public abstract int getValue();

	public abstract void setValue(int val);

	@SideOnly(Side.CLIENT)
	public void drawMouseOver(GuiManager gui, int mX, int mY) {
		if (CollisionHelper.inBounds(x + VARIABLE_X, y, VARIABLE_SIZE, VARIABLE_SIZE, mX, mY)) {
			gui.drawMouseOver(gui.getManager().getVariables()[getValue()].getDescription(gui), mX, mY);
		}
	}

	public void onClick(int mX, int mY) {
		for (int i = -1; i <= 1; i += 2) {
			int posX = x + (i == 1 ? ARROW_X_RIGHT : 0);
			int posY = y + ARROW_Y;


			if (CollisionHelper.inBounds(posX, posY, ARROW_WIDTH, ARROW_HEIGHT, mX, mY)) {
				int val = getValue();
				val += i;
				if (val < 0) {
					val = VariableColor.values().length - 1;
				} else if (val == VariableColor.values().length) {
					val = 0;
				}
				setValue(val);
				onUpdate();
				break;
			}
		}
	}

	public abstract void onUpdate();
}
