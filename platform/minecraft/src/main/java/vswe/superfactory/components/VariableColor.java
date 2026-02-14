package vswe.superfactory.components;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.Localization;
import vswe.superfactory.interfaces.Color;

public enum VariableColor {
	WHITE(Localization.VARIABLE_WHITE, Color.WHITE, 1.0F, 1.0F, 1.0F),
	ORANGE(Localization.VARIABLE_ORANGE, Color.ORANGE, 0.85F, 0.5F, 0.2F),
	MAGENTA(Localization.VARIABLE_MAGENTA, Color.MAGENTA, 0.7F, 0.3F, 0.85F),
	LIGHT_BLUE(Localization.VARIABLE_LIGHT_BLUE, Color.LIGHT_BLUE, 0.4F, 0.6F, 0.85F),
	YELLOW(Localization.VARIABLE_YELLOW, Color.YELLOW, 0.9F, 0.9F, 0.2F),
	LIME(Localization.VARiABLE_LIME, Color.LIME, 0.5F, 0.8F, 0.1F),
	PINK(Localization.VARIABLE_PINK, Color.PINK, 0.95F, 0.5F, 0.65F),
	GRAY(Localization.VARIABLE_GRAY, Color.GRAY, 0.3F, 0.3F, 0.3F),
	LIGHT_GRAY(Localization.VARIABLE_LIGHT_GRAY, Color.LIGHT_GRAY, 0.6F, 0.6F, 0.6F),
	CYAN(Localization.VARIABLE_CYAN, Color.CYAN, 0.3F, 0.5F, 0.6F),
	PURPLE(Localization.VARIABLE_PURPLE, Color.PURPLE, 0.5F, 0.25F, 0.7F),
	BLUE(Localization.VARIABLE_BLUE, Color.BLUE, 0.2F, 0.3F, 0.7F),
	BROWN(Localization.VARIABLE_BROWN, Color.WHITE, 0.4F, 0.3F, 0.2F),
	GREEN(Localization.VARIABLE_GREEN, Color.GREEN, 0.4F, 0.5F, 0.2F),
	RED(Localization.VARIABLE_RED, Color.RED, 0.6F, 0.2F, 0.2F),
	BLACK(Localization.VARIABLE_BLACK, Color.BLACK, 0.1F, 0.1F, 0.1F);

	private float        blue;
	private float        green;
	private Localization name;
	private float        red;
	private Color        textColor;

	VariableColor(Localization name, Color textColor, float red, float green, float blue) {
		this.name = name;
		this.textColor = textColor;
		this.red = red;
		this.green = green;
		this.blue = blue;
	}

	@SideOnly(Side.CLIENT)
	public void applyColor() {
		GlStateManager.color(red, green, blue, 1F);
	}


	@Override
	public String toString() {
		return name.toString();
	}

	public Color getTextColor() {
		return textColor;
	}
}
