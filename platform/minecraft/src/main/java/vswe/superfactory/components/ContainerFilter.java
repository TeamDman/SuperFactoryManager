package vswe.superfactory.components;

import vswe.superfactory.Localization;
import vswe.superfactory.blocks.ConnectionBlock;
import vswe.superfactory.interfaces.GuiManager;
import vswe.superfactory.tiles.TileEntityManager;

import java.util.ArrayList;
import java.util.List;

public class ContainerFilter {

	private static final int CHECK_BOX_DISTANCE_INVERT_X = 65;
	private static final int CHECK_BOX_DISTANCE_SPACING  = 30;
	private static final int CHECK_BOX_DISTANCE_Y        = 5;
	private static final int CHECK_BOX_FILTER_INVERT_Y = 55;
	private static final int CHECK_BOX_FILTER_SPACING  = 12;
	private static final int CHECK_BOX_FILTER_Y        = 5;
	private static final int CHECK_BOX_INVERT_VARIABLE_Y = 12;
	private static final int CHECK_BOX_POSITION_INVERT_X = 80;
	private static final int CHECK_BOX_POSITION_SPACING  = 20;
	private static final int CHECK_BOX_POSITION_Y        = 5;
	private static final int CHECK_BOX_SELECTION_Y  = 40;
	private static final int CHECK_BOX_X               = 5;
	private static final int DISTANCE_SECOND_LINE        = 14;
	private static final int RADIO_BUTTON_SPACING = 15;
	private static final int RADIO_BUTTON_SPACING_X = 60;
	private static final int RADIO_BUTTON_X         = 5;
	private static final int RADIO_BUTTON_Y         = 5;
	private static final int RADIO_BUTTON_Y_VARIABLE     = 2;
	private static final int TEXT_BOX_DISTANCE_X_1       = 15;
	private static final int TEXT_BOX_DISTANCE_X_2       = 40;
	private static final int TEXT_BOX_POSITION_X_1       = 25;
	private static final int TEXT_BOX_POSITION_X_2       = 50;
	private static final int TEXT_BOX_Y_OFFSET    = -2;
	public CheckBoxList               checkBoxes;
	public ComponentMenuContainer currentMenu;
	public List<Integer>              filterVariableSelection;
	public RadioButtonList            radioButtonVariable;
	public RadioButtonList            radioButtonsSelection;
	public ScrollController<Variable> scrollControllerVariable;
	public TextBoxNumberList          textBoxes;
	TextBoxPage[] higherRange;
	TextBoxPage[] lowerRange;
	private CheckBox   invertFilterMatch;
	private CheckBox[] invertRange;
	private CheckBox[] useRange;
	private CheckBox[] useSubFilter;
	private CheckBox   variableInvert;

	public ContainerFilter() {
		checkBoxes = new CheckBoxList();

		filterVariableSelection = new ArrayList<Integer>();

		ComponentMenuContainer.Page[] subFilterPages  = {ComponentMenuContainer.Page.POSITION, ComponentMenuContainer.Page.DISTANCE, ComponentMenuContainer.Page.SELECTION, ComponentMenuContainer.Page.VARIABLE};
		Localization[]                subFilterLabels = {Localization.FILTER_POSITION_LABEL, Localization.FILTER_DISTANCE_LABEL, Localization.FILTER_SELECTION_LABEL, Localization.FILTER_VARIABLE_LABEL};
		useSubFilter = new CheckBox[subFilterLabels.length];

		for (int i = 0; i < subFilterPages.length; i++) {
			checkBoxes.addCheckBox(useSubFilter[i] = new CheckBoxPage(subFilterLabels[i], ComponentMenuContainer.Page.FILTER, CHECK_BOX_X, CHECK_BOX_FILTER_Y + CHECK_BOX_FILTER_SPACING * i));
		}
		checkBoxes.addCheckBox(invertFilterMatch = new CheckBoxPage(Localization.INVERT, ComponentMenuContainer.Page.FILTER, CHECK_BOX_X, CHECK_BOX_FILTER_INVERT_Y));

		useRange = new CheckBox[5];
		lowerRange = new TextBoxPage[useRange.length];
		higherRange = new TextBoxPage[useRange.length];
		invertRange = new CheckBox[useRange.length];


		textBoxes = new TextBoxNumberList();
		Localization[] xyz = {Localization.X, Localization.Y, Localization.Z};
		for (int i = 0; i < xyz.length; i++) {
			int y = CHECK_BOX_POSITION_Y + CHECK_BOX_POSITION_SPACING * i;

			checkBoxes.addCheckBox(useRange[i] = new CheckBoxPage(xyz[i], ComponentMenuContainer.Page.POSITION, CHECK_BOX_X, y));
			textBoxes.addTextBox(lowerRange[i] = new TextBoxPage(ComponentMenuContainer.Page.POSITION, TEXT_BOX_POSITION_X_1, y + TEXT_BOX_Y_OFFSET, true, -128));
			textBoxes.addTextBox(higherRange[i] = new TextBoxPage(ComponentMenuContainer.Page.POSITION, TEXT_BOX_POSITION_X_2, y + TEXT_BOX_Y_OFFSET, true, 128));
			checkBoxes.addCheckBox(invertRange[i] = new CheckBoxPage(Localization.INVERT, ComponentMenuContainer.Page.POSITION, CHECK_BOX_POSITION_INVERT_X, y));
		}

		Localization[] distance = {Localization.CABLE_DISTANCE, Localization.DISTANCE};

		for (int i = 0; i < distance.length; i++) {
			int y = CHECK_BOX_DISTANCE_Y + CHECK_BOX_DISTANCE_SPACING * i;

			checkBoxes.addCheckBox(useRange[i + 3] = new CheckBoxPage(distance[i], ComponentMenuContainer.Page.DISTANCE, CHECK_BOX_X, y));

			y += DISTANCE_SECOND_LINE;

			textBoxes.addTextBox(lowerRange[i + 3] = new TextBoxPage(ComponentMenuContainer.Page.DISTANCE, TEXT_BOX_DISTANCE_X_1, y + TEXT_BOX_Y_OFFSET, false, 0));
			textBoxes.addTextBox(higherRange[i + 3] = new TextBoxPage(ComponentMenuContainer.Page.DISTANCE, TEXT_BOX_DISTANCE_X_2, y + TEXT_BOX_Y_OFFSET, false, 128));
			checkBoxes.addCheckBox(invertRange[i + 3] = new CheckBoxPage(Localization.INVERT, ComponentMenuContainer.Page.DISTANCE, CHECK_BOX_DISTANCE_INVERT_X, y));
		}

		radioButtonsSelection = new RadioButtonList() {
			@Override
			public void updateSelectedOption(int selectedOption) {
				setSelectedOption(selectedOption);
			}
		};
		Localization[] selection = {Localization.ONLY_SELECTED, Localization.HIDE_SELECTED};
		for (int i = 0; i < selection.length; i++) {
			radioButtonsSelection.add(new RadioButton(RADIO_BUTTON_X, RADIO_BUTTON_Y + RADIO_BUTTON_SPACING * i, selection[i]));
		}

		//checkBoxes.addCheckBox(new CheckBoxPage(Localization.RELOAD_ON_CHANGE, ComponentMenuContainer.Page.SELECTION, CHECK_BOX_X, CHECK_BOX_SELECTION_Y));

		scrollControllerVariable = new ScrollController<Variable>(false) {
			@Override
			protected List<Variable> getSearchResults(String search, boolean all) {
				if (currentMenu == null) {
					return new ArrayList<Variable>();
				}

				return new ArrayList<Variable>(currentMenu.getFilterVariables());
			}

			@Override
			protected void onClick(Variable variable, int mX, int mY, int button) {
				if (filterVariableSelection.contains(variable.getId())) {
					filterVariableSelection.remove((Integer) variable.getId());
				} else {
					filterVariableSelection.add(variable.getId());
				}
			}

			@Override
			protected void draw(GuiManager gui, Variable variable, int x, int y, boolean hover) {
				currentMenu.drawContainer(gui, variable, filterVariableSelection, x, y, hover);
			}

			@Override
			protected void drawMouseOver(GuiManager gui, Variable variable, int mX, int mY) {
				gui.drawMouseOver(currentMenu.getMouseOverForContainer(variable, filterVariableSelection), mX, mY);
			}
		};

		radioButtonVariable = new RadioButtonList() {
			@Override
			public void updateSelectedOption(int selectedOption) {
				setSelectedOption(selectedOption);
			}
		};

		Localization[] varOptions = {Localization.USE_UNUSED, Localization.USE_FILTER};
		for (int i = 0; i < varOptions.length; i++) {
			radioButtonVariable.add(new RadioButton(RADIO_BUTTON_X + RADIO_BUTTON_SPACING_X * i, RADIO_BUTTON_Y_VARIABLE, varOptions[i]));
		}
		checkBoxes.addCheckBox(variableInvert = new CheckBoxPage(Localization.INVERT, ComponentMenuContainer.Page.VARIABLE, CHECK_BOX_X, CHECK_BOX_INVERT_VARIABLE_Y) {
			@Override
			public boolean isVisible() {
				return super.isVisible() && isVariableListVisible();
			}
		});
	}

	public boolean isVariableListVisible() {
		return radioButtonVariable.getSelectedOption() == 1;
	}

	public boolean matches(TileEntityManager manager, List<Integer> selectedInventories, ConnectionBlock block) {
		boolean filterMatch = true;

		for (int i = 0; i < useSubFilter.length; i++) {

			if (filterMatch && useSubFilter[i].getValue()) {
				int rangeStart = 0, rangeEnd = 2;
				switch (i) {
					case 1:
						rangeStart = 3;
						rangeEnd = 4;
						//fall through (i.e. no break)
					case 0:
						for (int j = rangeStart; j <= rangeEnd; j++) {
							if (useRange[j].getValue()) {


								int value = 0;
								switch (j) {
									case 0:
										value = block.getTileEntity().getPos().getX() - manager.getPos().getX();
										break;
									case 1:
										value = block.getTileEntity().getPos().getY() - manager.getPos().getY();
										break;
									case 2:
										value = block.getTileEntity().getPos().getZ() - manager.getPos().getZ();
										break;
									case 3:
										value = block.getCableDistance();
										break;
									case 4:
										value = block.getDistance(manager);
								}

								boolean isRangeValid = lowerRange[j].getNumber() <= value && value <= higherRange[j].getNumber();

								//sub filter isn't matching
								if (isRangeValid == invertRange[j].getValue()) {
									filterMatch = false;
									break;
								}
							}
						}
						break;
					case 2:
						boolean onlySelected = radioButtonsSelection.getSelectedOption() == 0;
						boolean selected = selectedInventories.contains(block.getId());

						//sub filter isn't matching
						if (selected != onlySelected) {
							filterMatch = false;
						}
						break;
					case 3:
						if (radioButtonVariable.getSelectedOption() == 0) {
							for (Variable variable : manager.getVariables()) {
								if (block.isPartOfVariable(variable)) {
									//sub filter isn't matching
									filterMatch = false;
									break;
								}
							}
						} else {
							boolean variableMatch = false;

							for (Variable variable : manager.getVariables()) {
								if (filterVariableSelection.contains(variable.getId()) && block.isPartOfVariable(variable)) {
									//sub filter isn't matching
									variableMatch = true;
									break;
								}
							}

							if (variableMatch == variableInvert.getValue()) {
								//sub filter isn't matching
								filterMatch = false;
							}
						}
						break;
				}
			}
		}

		//filter matches
		return filterMatch != invertFilterMatch.getValue();
	}

	public void clear() {
		for (CheckBox checkBox : useSubFilter) {
			checkBox.setValue(false);
		}
		invertFilterMatch.setValue(false);

		for (CheckBox checkBox : useRange) {
			checkBox.setValue(false);
		}

		for (CheckBox checkBox : invertRange) {
			checkBox.setValue(false);
		}

		for (TextBoxPage textBoxPage : lowerRange) {
			textBoxPage.resetDefault();
		}

		for (TextBoxPage textBoxPage : higherRange) {
			textBoxPage.resetDefault();
		}

		radioButtonsSelection.setSelectedOption(0);
		radioButtonVariable.setSelectedOption(0);
		filterVariableSelection.clear();

		variableInvert.setValue(false);
	}

	private class CheckBoxPage extends CheckBox {


		private boolean                     checked; //this checkbox is only used on the client side so we don't have to anything special with the values
		private ComponentMenuContainer.Page page;

		public CheckBoxPage(Localization name, ComponentMenuContainer.Page page, int x, int y) {
			super(name, x, y);
			this.page = page;
		}

		@Override
		public boolean getValue() {
			return checked;
		}

		@Override
		public void setValue(boolean val) {
			checked = val;
		}

		@Override
		public void onUpdate() {

		}

		@Override
		public boolean isVisible() {
			return currentMenu.getCurrentPage() == page;
		}
	}

	class TextBoxPage extends TextBoxNumber {
		private int                         defaultNumber;
		private boolean                     negative;
		private ComponentMenuContainer.Page page;

		public TextBoxPage(ComponentMenuContainer.Page page, int x, int y, boolean negative, int defaultNumber) {
			super(x, y, 3, false);
			this.page = page;
			this.negative = negative;
			this.defaultNumber = defaultNumber;
			resetDefault();
		}

		private void resetDefault() {
			setNumber(defaultNumber);
		}

		@Override
		public int getMaxNumber() {
			return 128;
		}

		@Override
		public int getMinNumber() {
			return negative ? -128 : super.getMinNumber();
		}

		@Override
		public boolean isVisible() {
			return page == currentMenu.getCurrentPage();
		}

		@Override
		public float getTextSize() {
			return negative ? 0.7F : super.getTextSize();
		}

		@Override
		public int getTextY() {
			return negative ? 4 : super.getTextY();
		}
	}
}
