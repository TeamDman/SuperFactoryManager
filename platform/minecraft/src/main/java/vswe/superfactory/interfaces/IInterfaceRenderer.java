package vswe.superfactory.interfaces;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public interface IInterfaceRenderer {
	void draw(GuiManager gui, int mX, int mY);

	void drawMouseOver(GuiManager gui, int mX, int mY);

	void onClick(GuiManager gui, int mX, int mY, int button);

	void onDrag(GuiManager gui, int mX, int mY);

	void onRelease(GuiManager gui, int mX, int mY);

	void onKeyTyped(GuiManager gui, char c, int k);

	void onScroll(int scroll);
}
