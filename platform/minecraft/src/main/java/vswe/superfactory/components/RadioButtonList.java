package vswe.superfactory.components;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.CollisionHelper;
import vswe.superfactory.interfaces.GuiManager;

import java.util.ArrayList;
import java.util.List;

public abstract class RadioButtonList {
	private static final int RADIO_SIZE   = 8;
	private static final int RADIO_SRC_X  = 30;
	private static final int RADIO_SRC_Y  = 52;
	private static final int RADIO_TEXT_X = 12;
	private static final int RADIO_TEXT_Y = 2;

	private List<RadioButton> radioButtonList;
	private int               selectedOption;

	public RadioButtonList() {
		radioButtonList = new ArrayList<RadioButton>();

	}

	@SideOnly(Side.CLIENT)
	public void draw(GuiManager gui, int mX, int mY) {
		for (int i = 0; i < radioButtonList.size(); i++) {
			RadioButton radioButton = radioButtonList.get(i);

			if (radioButton.isVisible()) {
				int srcRadioX = getSelectedOption() == i ? 1 : 0;
				int srcRadioY = CollisionHelper.inBounds(radioButton.getX(), radioButton.getY(), RADIO_SIZE, RADIO_SIZE, mX, mY) ? 1 : 0;

				gui.drawTexture(radioButton.getX(), radioButton.getY(), RADIO_SRC_X + srcRadioX * RADIO_SIZE, RADIO_SRC_Y + srcRadioY * RADIO_SIZE, RADIO_SIZE, RADIO_SIZE);
				gui.drawString(radioButton.getText(), radioButton.getX() + RADIO_TEXT_X, radioButton.getY() + RADIO_TEXT_Y, 0.7F, 0x404040);
			}
		}
	}

	public int getSelectedOption() {
		return selectedOption;
	}

	public void setSelectedOption(int selectedOption) {
		this.selectedOption = selectedOption;
	}

	public void onClick(int mX, int mY, int button) {
		for (int i = 0; i < radioButtonList.size(); i++) {
			RadioButton radioButton = radioButtonList.get(i);

			if (radioButton.isVisible() && CollisionHelper.inBounds(radioButton.getX(), radioButton.getY(), RADIO_SIZE, RADIO_SIZE, mX, mY) && getSelectedOption() != i) {
				updateSelectedOption(i);
				break;
			}
		}
	}

	public abstract void updateSelectedOption(int selectedOption);

	public void add(RadioButton radioButton) {
		radioButtonList.add(radioButton);
	}

	public final int getRawSelectedOption() {
		return selectedOption;
	}

	public int size() {
		return radioButtonList.size();
	}
}
