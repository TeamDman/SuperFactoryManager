/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ca.teamdman.sfm.client.gui.flow.impl.manager.core;

import ca.teamdman.sfm.client.gui.flow.core.BaseScreen;
import ca.teamdman.sfm.client.gui.flow.core.FlowComponent;
import ca.teamdman.sfm.client.gui.flow.impl.manager.flowdataholder.FlowRelationship;
import ca.teamdman.sfm.common.flow.core.FlowDataHolder;
import ca.teamdman.sfm.common.flow.core.Position;
import ca.teamdman.sfm.common.flow.data.FlowData;
import ca.teamdman.sfm.common.flow.data.RelationshipFlowData;
import com.mojang.blaze3d.matrix.MatrixStack;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.screen.Screen;

public class RelationshipController extends FlowComponent {

	public final ManagerFlowController CONTROLLER;
	private final Position fromPos = new Position();
	private final Position toPos = new Position();
	private FlowDataHolder<?> from;
	private boolean isDragging = false;

	public RelationshipController(ManagerFlowController CONTROLLER) {
		this.CONTROLLER = CONTROLLER;
	}

	@Override
	public int getZIndex() {
		return super.getZIndex() + 300;
	}

	@Override
	public boolean mousePressed(int mx, int my, int button) {
		if (!Screen.hasShiftDown()) {
			return false;
		}
		Optional<? extends FlowComponent> hit = CONTROLLER.getElementsUnderMouse(mx, my)
			.filter(c -> c instanceof FlowDataHolder)
			.filter(c -> ((FlowDataHolder<?>) c).getData().isValidRelationshipTarget())
			.findFirst();
		hit.ifPresent(c -> {
			isDragging = true;
			from = ((FlowDataHolder<?>) c);
			fromPos.setXY(c.getCentroid());
			toPos.setXY(mx, my);
		});
		return hit.isPresent();
	}

	@Override
	public boolean mouseReleased(int mx, int my, int button) {
		if (!isDragging) {
			return false;
		}
		CONTROLLER.getElementsUnderMouse(mx, my)
			.filter(c -> c instanceof FlowDataHolder)
			.filter(c -> !c.equals(from))
			.map(c -> ((FlowDataHolder<?>) c).getData())
			.filter(FlowData::isValidRelationshipTarget)
			.map(FlowData::getId)
			.findFirst()
			.ifPresent(to -> createRelationship(from.getData().getId(), to));
		isDragging = false;
		from = null;
		return true;
	}


	public void createRelationship(UUID from, UUID to) {
		CONTROLLER.SCREEN.sendFlowDataToServer(
			new RelationshipFlowData(
				UUID.randomUUID(),
				from,
				to
			)
		);
	}

	@Override
	public boolean mouseDragged(int mx, int my, int button, int dmx, int dmy) {
		if (!isDragging) {
			return false;
		}
		toPos.setXY(mx, my);
		CONTROLLER.getElementsUnderMouse(mx, my)
			.filter(FlowDataHolder.class::isInstance)
			.filter(c -> !c.equals(from))
			.filter(c -> ((FlowDataHolder<?>) c).getData().isValidRelationshipTarget())
			.map(x -> x.snapToEdge(fromPos))
			.findFirst()
			.ifPresent(toPos::setXY);
		return true;
	}

	@Override
	public void draw(BaseScreen screen, MatrixStack matrixStack, int mx, int my, float deltaTime) {
		if (isDragging) {
			screen.drawArrow(matrixStack, fromPos, toPos, FlowRelationship.COLOUR);
		}
	}
	

}
