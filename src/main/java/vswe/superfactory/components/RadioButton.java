package vswe.superfactory.components;

import vswe.superfactory.Localization;

public class RadioButton {
	private Localization text;
	private int          x;
	private int          y;

	public RadioButton(int x, int y, Localization text) {
		this.x = x;
		this.y = y;
		this.text = text;
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public String getText() {
		return text.toString();
	}

	public boolean isVisible() {
		return true;
	}
}
