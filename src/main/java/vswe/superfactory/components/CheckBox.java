package vswe.superfactory.components;

import vswe.superfactory.Localization;

public abstract class CheckBox {
	private Localization name;
	private int          textWidth;
	private int x, y;

	public CheckBox(Localization name, int x, int y) {
		this.x = x;
		this.y = y;
		this.name = name;
		textWidth = Integer.MAX_VALUE;
	}

	public abstract boolean getValue();

	public abstract void setValue(boolean val);

	public abstract void onUpdate();


	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public String getName() {
		return name == null ? null : name.toString();
	}

	public boolean isVisible() {
		return true;
	}

	public int getTextWidth() {
		return textWidth;
	}

	public void setTextWidth(int textWidth) {
		this.textWidth = textWidth;
	}
}
