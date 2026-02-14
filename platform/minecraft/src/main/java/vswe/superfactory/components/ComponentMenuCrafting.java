package vswe.superfactory.components;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.CollisionHelper;
import vswe.superfactory.Localization;
import vswe.superfactory.components.internal.CraftingSetting;
import vswe.superfactory.components.internal.Setting;
import vswe.superfactory.interfaces.GuiManager;

public class ComponentMenuCrafting extends ComponentMenuItem {
	private CraftingDummy   dummy;
	private CraftingSetting resultItem;

	public ComponentMenuCrafting(FlowComponent parent) {
		super(parent, CraftingSetting.class);

		resultItem = new CraftingSetting(9) {
			@Override
			public boolean canChangeMetaData() {
				return false;
			}

			@Override
			public void delete() {
				for (Setting setting : settings) {
					setting.clear();
					writeServerData(DataTypeHeader.CLEAR, setting);
				}
			}
		};
		settings.add(resultItem);
		dummy = new CraftingDummy(this);


		scrollControllerSelected.setItemsPerRow(3);
		scrollControllerSelected.setVisibleRows(3);
		scrollControllerSelected.setItemUpperLimit(2);
		scrollControllerSelected.setDisabledScroll(true);
	}

	@Override
	public String getName() {
		return Localization.CRAFTING_MENU.toString();
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void onClick(int mX, int mY, int button) {
		super.onClick(mX, mY, button);
		if (!isEditing() && !isSearching() && !resultItem.getItem().isEmpty()) {
			if (button == 1 && CollisionHelper.inBounds(getResultX(), getResultY(), ITEM_SIZE, ITEM_SIZE, mX, mY)) {
				scrollControllerSelected.onClick(resultItem, mX, mY, 1);
			}
		}
	}

	@Override
	protected void initRadioButtons() {
		//no radio buttons
	}

	@Override
	protected int getSettingCount() {
		return 9;
	}

	@Override
	protected void onSettingContentChange() {
		resultItem.setItem(dummy.getResult());
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void draw(GuiManager gui, int mX, int mY) {
		super.draw(gui, mX, mY);
		if (!isEditing() && !isSearching() && !resultItem.getItem().isEmpty()) {
			drawResultObject(gui, resultItem.getItem(), getResultX(), getResultY());

			gui.drawItemAmount(resultItem.getItem(), getResultX(), getResultY());
		}
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void drawMouseOver(GuiManager gui, int mX, int mY) {
		super.drawMouseOver(gui, mX, mY);
		if (!isEditing() && !isSearching() && !resultItem.getItem().isEmpty()) {
			if (CollisionHelper.inBounds(getResultX(), getResultY(), ITEM_SIZE, ITEM_SIZE, mX, mY)) {
				gui.drawMouseOver(getResultObjectMouseOver(resultItem.getItem()), mX, mY);
			}
		}
	}

	private int getResultX() {
		return ITEM_X + ITEM_SIZE_WITH_MARGIN * 3;
	}

	private int getResultY() {
		return scrollControllerSelected.getScrollingStartY() + ITEM_SIZE_WITH_MARGIN;
	}

	public CraftingDummy getDummy() {
		return dummy;
	}

	public CraftingSetting getResultItem() {
		return resultItem;
	}
}
