package ca.teamdman.sfm.client.gui.flow.impl.manager.flowdataholder.itemstackmatcher;

import ca.teamdman.sfm.client.gui.flow.core.BaseScreen;
import ca.teamdman.sfm.client.gui.flow.core.Colour3f.CONST;
import ca.teamdman.sfm.client.gui.flow.core.FlowComponent;
import ca.teamdman.sfm.client.gui.flow.core.Size;
import ca.teamdman.sfm.client.gui.flow.impl.manager.core.ManagerFlowController;
import ca.teamdman.sfm.client.gui.flow.impl.util.FlowContainer;
import ca.teamdman.sfm.client.gui.flow.impl.util.ItemStackFlowComponent;
import ca.teamdman.sfm.client.gui.flow.impl.util.TextAreaFlowComponent;
import ca.teamdman.sfm.common.flow.core.FlowDataHolder;
import ca.teamdman.sfm.common.flow.core.Position;
import ca.teamdman.sfm.common.flow.data.ItemStackComparerMatcherFlowData;
import ca.teamdman.sfm.common.flow.holder.FlowDataHolderObserver;
import com.mojang.blaze3d.matrix.MatrixStack;

public class ItemStackComparerMatcherFlowComponent extends
	FlowContainer implements
	FlowDataHolder<ItemStackComparerMatcherFlowData> {

	protected final ManagerFlowController PARENT;
	private final ComparerMatcherIconComponent ICON;
	private final TextAreaFlowComponent QUANTITY_INPUT;
	private ItemStackComparerMatcherFlowData data;

	public ItemStackComparerMatcherFlowComponent(
		ManagerFlowController parent,
		ItemStackComparerMatcherFlowData data
	) {
		super(
			new Position(ItemStackFlowComponent.DEFAULT_SIZE.getWidth() + 5, 0),
			new Size(100,24)
		);
		PARENT = parent;
		this.data = data;

		// Quantity input box
		this.QUANTITY_INPUT = new TextAreaFlowComponent(
			parent.SCREEN,
			Integer.toString(data.quantity),
			"#",
			new Position(4, 4),
			new Size(38, 16)
		);
		QUANTITY_INPUT.setResponder(next -> {
			try {
				int nextVal = Integer.parseInt(next);
				if (nextVal != data.quantity) {
					data.quantity = nextVal;
					parent.SCREEN.sendFlowDataToServer(data);
				}
			} catch (NumberFormatException ignored) {
			}
		});
		addChild(QUANTITY_INPUT);

		// "x" separator
		addChild(new FlowComponent(45, 8, 0, 0) {
			@Override
			public void draw(
				BaseScreen screen, MatrixStack matrixStack, int mx, int my, float deltaTime
			) {
				screen.drawString(
					matrixStack,
					"x",
					getPosition().getX(),
					getPosition().getY(),
					CONST.TEXT_DARK
				);
			}
		});

		// Display itemstack
//		this.ICON = new ItemStackFlowComponent(data.stack, new Position(54, 2));
		this.ICON = new ComparerMatcherIconComponent(this, new Position(54, 2));
		addChild(ICON);

		// Add change listener
		parent.SCREEN.getFlowDataContainer().addObserver(new FlowDataHolderObserver<>(
			this,
			ItemStackComparerMatcherFlowData.class
		));

		setEnabled(false);
		setVisible(false);
	}

	@Override
	public int getZIndex() {
		return super.getZIndex() + 120;
	}

	@Override
	public void draw(BaseScreen screen, MatrixStack matrixStack, int mx, int my, float deltaTime) {
		screen.clearRect(
			matrixStack,
			getPosition().getX(),
			getPosition().getY(),
			getSize().getWidth(),
			getSize().getHeight()
		);
		screen.drawRect(
			matrixStack,
			getPosition().getX(),
			getPosition().getY(),
			getSize().getWidth(),
			getSize().getHeight(),
			CONST.PANEL_BACKGROUND_LIGHT
		);
		super.draw(screen, matrixStack, mx, my, deltaTime);
	}

	@Override
	public ItemStackComparerMatcherFlowData getData() {
		return data;
	}

	@Override
	public void setData(ItemStackComparerMatcherFlowData data) {
		this.data = data;
		ICON.BUTTON.setItemStack(data.stack);
		QUANTITY_INPUT.setContent(Integer.toString(data.quantity));
	}
}