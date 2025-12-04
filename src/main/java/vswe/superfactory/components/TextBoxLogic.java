package vswe.superfactory.components;

import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.interfaces.GuiManager;

public class TextBoxLogic {
	private String  text;
	private int     width;
	private int     charLimit;
	private int     widthLimit;
	private float   widthMultiplier;
	private int     cursorIndex;
	private int     cursorPosition;
	private boolean dirtyCursor;

	public TextBoxLogic(int charLimit, int width) {
		this.charLimit = charLimit;
		this.width = width;
		widthMultiplier = 1F;
	}

	@SideOnly(Side.CLIENT)
	private void addText(GuiManager gui, String str) {
		String newText = text.substring(0, cursorIndex) + str + text.substring(cursorIndex);

		if (newText.length() <= charLimit) { // && gui.getStringWidth(newText) * widthMultiplier <= width) {
			text = newText;
			if (gui.getStringWidth(newText) * widthMultiplier < width - 2)
				widthLimit = newText.length();
			else if (gui.getStringWidth(getDisplayText()) * widthMultiplier >= width - 2)
				widthLimit--;
			moveCursor(gui, str.length());
			textChanged();
		}
	}

	@SideOnly(Side.CLIENT)
	private void deleteText(GuiManager gui, int direction) {
		if (cursorIndex + direction >= 0 && cursorIndex + direction <= text.length()) {
			if (direction > 0) {
				text = text.substring(0, cursorIndex) + text.substring(cursorIndex + 1);
			} else {
				text = text.substring(0, cursorIndex - 1) + text.substring(cursorIndex);
				moveCursor(gui, direction);
			}
			textChanged();
		}
	}

	@SideOnly(Side.CLIENT)
	private void moveCursor(GuiManager gui, int steps) {
		cursorIndex += steps;

		updateCursor();
	}


	protected void textChanged() {
	}

	public String getText() {
		return text;
	}

	public String getDisplayText() {
		return text.substring(MathHelper.clamp(text.length() - widthLimit, 0, text.length()));
	}

	public void setText(String text) {
		this.text = text;
	}

	public int getCursorPosition(GuiManager gui) {
		if (dirtyCursor) {
			cursorPosition = (int) (gui.getStringWidth(getDisplayText().substring(0, Math.min(cursorIndex, getDisplayText().length()))) * widthMultiplier);
			dirtyCursor = false;
		}

		return cursorPosition;
	}

	@SideOnly(Side.CLIENT)
	public void onKeyStroke(GuiManager gui, char c, int k) {
		if (k == 203) {
			moveCursor(gui, -1);
		} else if (k == 205) {
			moveCursor(gui, 1);
		} else if (k == 14) {
			deleteText(gui, -1);
		} else if (k == 211) {
			deleteText(gui, 1);
		} else if (ChatAllowedCharacters.isAllowedCharacter(c)) {
			addText(gui, Character.toString(c));
		}
	}

	public void updateCursor() {
		if (cursorIndex < 0) {
			cursorIndex = 0;
		} else if (cursorIndex > getDisplayText().length()) {
			cursorIndex = text.length();
		}

		dirtyCursor = true;
	}

	public void setTextAndCursor(String s) {
		setText(s);
		resetCursor();
	}

	public void resetCursor() {
		cursorIndex = text.length();
		dirtyCursor = true;
	}

	public void setWidthMultiplier(float widthMultiplier) {
		this.widthMultiplier = widthMultiplier;
	}
}
