package vswe.superfactory.components;

import net.minecraft.client.renderer.GlStateManager;
import vswe.superfactory.components.internal.IContainerSelection;
import vswe.superfactory.interfaces.GuiManager;

public class LabelSelection implements IContainerSelection {
	private final int id;
	private final String name;

	public LabelSelection(int id, String name) {
		this.id = id;
		this.name = name;
	}

	public boolean isValid() {
		return true;
	}

	@Override
	public int getId() {
		return id;
	}

	@Override
	public void draw(GuiManager gui, int x, int y) {
		GlStateManager.color(1F, 1F, 1F, 1F);
		gui.drawCenteredString(name.substring(0,1), x, Math.round(y + (16 - gui.getFontHeight())/2.0F), 1, 16,1);
		GlStateManager.color(1F, 1F, 1F, 1F);

	}

	@Override
	public String getDescription(GuiManager gui) {
		return "<" + this.name + ">";
	}

	@Override
	public String getName(GuiManager gui) {
		return this.name;
	}

	@Override
	public boolean isVariable() {
		return true;
	}

}
