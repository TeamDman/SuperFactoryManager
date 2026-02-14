package vswe.superfactory.components;

import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.CollisionHelper;
import vswe.superfactory.interfaces.GuiManager;

import java.util.ArrayList;
import java.util.List;

public class TextBoxNumberList {
	private static final int                 TEXT_BOX_SIZE_H = 12;
	private static final int                 TEXT_BOX_SRC_X  = 0;
	private static final int                 TEXT_BOX_SRC_Y  = 221;
	private              TextBoxNumber       selectedTextBox;
	private              List<TextBoxNumber> textBoxes       = new ArrayList<>();

	@SideOnly(Side.CLIENT)
	public void draw(GuiManager gui, int mX, int mY) {
		for (TextBoxNumber textBox : textBoxes) {
			if (textBox.isVisible()) {
				int srcTextBoxX = textBox.equals(selectedTextBox) ? 1 : 0;
				int srcTextBoxY = textBox.isWide() ? 1 : 0;

				gui.drawTexture(textBox.getX(), textBox.getY(), TEXT_BOX_SRC_X + srcTextBoxX * textBox.getWidth(), TEXT_BOX_SRC_Y + srcTextBoxY * TEXT_BOX_SIZE_H, textBox.getWidth(), TEXT_BOX_SIZE_H);
				String raw = String.valueOf(textBox.getNumber());
				String str = raw.substring(MathHelper.clamp(raw.length() - textBox.getLength(), 0, raw.length()));
				if (str.length() != raw.length())
					str = "~"+str;
				gui.drawCenteredString(str, textBox.getX(), textBox.getY() + textBox.getTextY(), textBox.getTextSize(), textBox.getWidth(), 0xFFFFFF);
			}
		}
	}

	public void onClick(int mX, int mY, int button) {
		for (TextBoxNumber textBox : textBoxes) {
			if (textBox.isVisible() && CollisionHelper.inBounds(textBox.getX(), textBox.getY(), textBox.getWidth(), TEXT_BOX_SIZE_H, mX, mY)) {
				if (textBox.equals(selectedTextBox)) {
					if (button == 0) {
						selectedTextBox = null;
					} else {
						textBox.setNumber(0);
						selectedTextBox.onNumberChanged();
					}
				} else {
					selectedTextBox = textBox;
				}

				break;
			}
		}
	}

	@SideOnly(Side.CLIENT)
	public boolean onKeyStroke(GuiManager gui, char c, int k) {
		if (selectedTextBox != null && selectedTextBox.isVisible()) {

			if (Character.isDigit(c)) {
				int number = Integer.parseInt(String.valueOf(c));
				//if (Math.abs(selectedTextBox.getNumber()) < Math.pow(10, selectedTextBox.getLength() - 1)) {
					selectedTextBox.setNumber((Math.abs(selectedTextBox.getNumber()) * 10 + number) * (selectedTextBox.getNumber() < 0 ? -1 : 1));
					selectedTextBox.onNumberChanged();
				//}
				return true;
			} else if (c == '-' && selectedTextBox.allowNegative()) {
				selectedTextBox.setNumber(selectedTextBox.getNumber() * -1);
				selectedTextBox.onNumberChanged();
				return true;
			} else if (k == 14) {
				selectedTextBox.setNumber(selectedTextBox.getNumber() / 10);
				selectedTextBox.onNumberChanged();
				return true;
			} else if (k == 15) {
				for (int i = 0; i < textBoxes.size(); i++) {
					TextBoxNumber textBox = textBoxes.get(i);

					if (textBox.equals(selectedTextBox)) {
						int nextId = (i + 1) % textBoxes.size();
						selectedTextBox = textBoxes.get(nextId);
						break;
					}
				}
				return true;
			}
		}

		return false;
	}

	public void addTextBox(TextBoxNumber textBox) {
		textBoxes.add(textBox);
	}

	public TextBoxNumber getTextBox(int id) {
		return textBoxes.get(id);
	}

}
