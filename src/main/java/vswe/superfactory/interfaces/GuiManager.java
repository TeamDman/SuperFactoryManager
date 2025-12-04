package vswe.superfactory.interfaces;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import vswe.superfactory.CollisionHelper;
import vswe.superfactory.Localization;
import vswe.superfactory.animation.AnimationController;
import vswe.superfactory.components.FlowComponent;
import vswe.superfactory.network.packets.DataBitHelper;
import vswe.superfactory.network.packets.DataWriter;
import vswe.superfactory.network.packets.PacketHandler;
import vswe.superfactory.tiles.TileEntityManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiManager extends GuiBase {
	private static final ResourceLocation BACKGROUND_1 = registerTexture("background_1");
	private static final ResourceLocation BACKGROUND_2 = registerTexture("background_2");
	private static final ResourceLocation COMPONENTS   = registerTexture("flow_components");
	public static int Z_LEVEL_COMPONENT_CLOSED_DIFFERENCE = 1;
	public static int Z_LEVEL_COMPONENT_OPEN_DIFFERENCE   = 100;
	public static int Z_LEVEL_COMPONENT_START             = 750;
	public static int Z_LEVEL_OPEN_MAXIMUM                = 5;
	private List<SecretCode> codes = new ArrayList<SecretCode>();
	private AnimationController controller;
	private boolean             doubleShiftFlag;
	private long lastTicks;
	private TileEntityManager manager;
	private boolean             useBlueScreen;
	private boolean             useButtons   = true;
	private boolean             useGreenScreen;
	private boolean             useInfo      = true;
	private boolean             useMouseOver = true;
	private boolean             usePinkScreen;

	{
		codes.add(new SecretCode("animate") {
			@Override
			protected void trigger() {
				controller = new AnimationController(manager, 2);
			}
		});
		codes.add(new SecretCode("animslow") {
			@Override
			protected void trigger() {
				controller = new AnimationController(manager, 1);
			}
		});
		codes.add(new SecretCode("animfast") {
			@Override
			protected void trigger() {
				controller = new AnimationController(manager, 5);
			}
		});
		codes.add(new SecretCode("animrapid") {
			@Override
			protected void trigger() {
				controller = new AnimationController(manager, 20);
			}
		});
		codes.add(new SecretCode("animinstant") {
			@Override
			protected void trigger() {
				controller = new AnimationController(manager, 100);
			}
		});
		codes.add(new SecretCode("green") {
			@Override
			protected void trigger() {
				useGreenScreen = !useGreenScreen;
				useBlueScreen = false;
				usePinkScreen = false;
			}
		});
		codes.add(new SecretCode("blue") {
			@Override
			protected void trigger() {
				useBlueScreen = !useBlueScreen;
				useGreenScreen = false;
				usePinkScreen = false;
			}
		});
		codes.add(new SecretCode("pink") {
			@Override
			protected void trigger() {
				usePinkScreen = !usePinkScreen;
				useGreenScreen = false;
				useBlueScreen = false;
			}
		});
		codes.add(new SecretCode("buttons") {
			@Override
			protected void trigger() {
				useButtons = !useButtons;
			}
		});
		codes.add(new SecretCode("info") {
			@Override
			protected void trigger() {
				useInfo = !useInfo;
			}
		});
		codes.add(new SecretCode("mouse") {
			@Override
			protected void trigger() {
				useMouseOver = !useMouseOver;
			}
		});
	}
	public GuiManager(TileEntityManager manager, InventoryPlayer player) {
		super(new ContainerManager(manager, player));

		xSize = 512;
		ySize = 256;

		this.manager = manager;
		Keyboard.enableRepeatEvents(true);
	}

	@Override
	public ResourceLocation getComponentResource() {
		return COMPONENTS;
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float f, int x, int y) {

		if (!useGreenScreen && !useBlueScreen && !usePinkScreen) {
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
			bindTexture(BACKGROUND_1);
			drawTexture(0, 0, 0, 0, 256, 256);

			bindTexture(BACKGROUND_2);
			drawTexture(256, 0, 0, 0, 256, 256);
		}

		x -= guiLeft;
		y -= guiTop;

		bindTexture(COMPONENTS);

		if (hasSpecialRenderer()) {
			getSpecialRenderer().draw(this, x, y);
			getSpecialRenderer().drawMouseOver(this, x, y);
			return;
		}

		if (useButtons) {
			for (int i = 0; i < manager.buttons.size(); i++) {
				TileEntityManager.Button button = manager.buttons.get(i);
				if (button.isVisible()) {
					int srcButtonY = CollisionHelper.inBounds(button.getX(), button.getY(), TileEntityManager.BUTTON_SIZE_W, TileEntityManager.BUTTON_SIZE_H, x, y) ? 1 : 0;

					drawTexture(button.getX(), button.getY(), TileEntityManager.BUTTON_SRC_X, TileEntityManager.BUTTON_SRC_Y + srcButtonY * TileEntityManager.BUTTON_SIZE_H, TileEntityManager.BUTTON_SIZE_W, TileEntityManager.BUTTON_SIZE_H);
					drawTexture(button.getX() + 1, button.getY() + 1, TileEntityManager.BUTTON_INNER_SRC_X, TileEntityManager.BUTTON_INNER_SRC_Y + i * TileEntityManager.BUTTON_INNER_SIZE_H, TileEntityManager.BUTTON_INNER_SIZE_W, TileEntityManager.BUTTON_INNER_SIZE_H);
				}
			}
		}


		//update components completely independent on their visibility
		long  ticks          = Minecraft.getSystemTime();
		float elapsedSeconds = (ticks - this.lastTicks) / 1000F;
		if (controller != null) {
			controller.update(elapsedSeconds);
		}
		for (FlowComponent component : manager.getFlowItems()) {
			component.update(elapsedSeconds);
		}
		this.lastTicks = ticks;

		int zLevel    = Z_LEVEL_COMPONENT_START;
		int openCount = 0;
		for (int i = 0; i < manager.getZLevelRenderingList().size(); i++) {
			FlowComponent itemBase = manager.getZLevelRenderingList().get(i);

			if (itemBase.isVisible()) {
				if (itemBase.isOpen() && openCount == Z_LEVEL_OPEN_MAXIMUM) {
					itemBase.close();
				}

				if (itemBase.isOpen()) {
					zLevel -= Z_LEVEL_COMPONENT_OPEN_DIFFERENCE;
					openCount++;
				} else {
					zLevel -= Z_LEVEL_COMPONENT_CLOSED_DIFFERENCE;
				}
				itemBase.draw(this, x, y, zLevel);

				if (itemBase.isBeingMoved() || CollisionHelper.inBounds(itemBase.getX(), itemBase.getY(), itemBase.getComponentWidth(), itemBase.getComponentHeight(), x, y)) {
					CollisionHelper.disableInBoundsCheck = true;
				}
			}
		}
		CollisionHelper.disableInBoundsCheck = false;

		if (useInfo) {
			drawString(getInfo(), 5, ySize - 13, 1F, 0x606060);
		}

		if (useMouseOver) {

			if (useButtons) {
				for (TileEntityManager.Button button : manager.buttons) {
					if (button.isVisible() && CollisionHelper.inBounds(button.getX(), button.getY(), TileEntityManager.BUTTON_SIZE_W, TileEntityManager.BUTTON_SIZE_H, x, y)) {
						drawMouseOver(button.getMouseOver(), x, y);
					}
				}
			}

			for (FlowComponent itemBase : manager.getZLevelRenderingList()) {
				if (itemBase.isVisible()) {
					itemBase.drawMouseOver(this, x, y);
					if (itemBase.isBeingMoved() || CollisionHelper.inBounds(itemBase.getX(), itemBase.getY(), itemBase.getComponentWidth(), itemBase.getComponentHeight(), x, y)) {
						CollisionHelper.disableInBoundsCheck = true;
					}
				}
			}
		}
		CollisionHelper.disableInBoundsCheck = false;

		if (!Keyboard.isKeyDown(54) && doubleShiftFlag) {
			doubleShiftFlag = false;
		}
	}

	private String getInfo() {
		String ret = Localization.COMMANDS.toString() + ": " + manager.getFlowItems().size() + "  ";

		String        path      = "";
		FlowComponent component = manager.getSelectedComponent();

		if (component != null) {
			ret += "|";
		}
		while (component != null) {
			String nextPath = "> " + component.getName() + " " + path;
			if (getStringWidth(ret + nextPath) > xSize - 15) {
				path = "... " + path;
				break;
			}
			path = nextPath;
			component = component.getParent();
		}
		ret += path;

		return ret;
	}

	@Override
	protected void keyTyped(char c, int k) throws IOException {
		if (hasSpecialRenderer()) {
			getSpecialRenderer().onKeyTyped(this, c, k);
		} else {


			if (k == 54 && !doubleShiftFlag) {
				DataWriter dw = PacketHandler.getWriterForServerActionPacket();
				PacketHandler.sendDataToServer(dw);
				doubleShiftFlag = true;
			}

			for (FlowComponent itemBase : manager.getZLevelRenderingList()) {
				if (itemBase.isVisible() && itemBase.onKeyStroke(this, c, k) && k != 1) {
					return;
				}
			}

			boolean recognized = false;
			for (SecretCode code : codes) {
				if (code.keyTyped(c)) {
					recognized = true;
				}
			}

			if (recognized) {
				return;
			}
		}

		super.keyTyped(c, k);
	}

	@Override
	protected void mouseClicked(int x, int y, int button) throws IOException {
		x = scaleX(x);
		y = scaleY(y);

		super.mouseClicked(x, y, button);

		x -= guiLeft;
		y -= guiTop;

		if (hasSpecialRenderer()) {
			getSpecialRenderer().onClick(this, x, y, button);
			return;
		}

		for (int i = 0; i < manager.getZLevelRenderingList().size(); i++) {
			FlowComponent itemBase = manager.getZLevelRenderingList().get(i);
			if (itemBase.isVisible() && itemBase.onClick(x, y, button)) {
				manager.getZLevelRenderingList().remove(i);
				manager.getZLevelRenderingList().add(0, itemBase);
				break;
			}
		}


		if (useButtons) {
			onClickButtonCheck(x, y, false);
		}
	}

	private void onClickButtonCheck(int x, int y, boolean release) {
		for (int i = 0; i < manager.buttons.size(); i++) {
			TileEntityManager.Button guiButton = manager.buttons.get(i);
			if (guiButton.isVisible() && CollisionHelper.inBounds(guiButton.getX(), guiButton.getY(), TileEntityManager.BUTTON_SIZE_W, TileEntityManager.BUTTON_SIZE_H, x, y) && guiButton.activateOnRelease() == release) {
				DataWriter dw = PacketHandler.getButtonPacketWriter();
				dw.writeData(i, DataBitHelper.GUI_BUTTON_ID);
				if (guiButton.onClick(dw)) {
					PacketHandler.sendDataToServer(dw);
				}
				break;
			}
		}
	}

	@Override
	protected void mouseReleased(int x, int y, int button) {
		x = scaleX(x);
		y = scaleY(y);

		super.mouseReleased(x, y, button);

		x -= guiLeft;
		y -= guiTop;

		if (hasSpecialRenderer()) {
			getSpecialRenderer().onRelease(this, x, y);
			return;
		}

		if (useButtons) {
			onClickButtonCheck(x, y, true);
		}

		if (!manager.justSentServerComponentRemovalPacket) {
			for (FlowComponent itemBase : manager.getZLevelRenderingList()) {
				if (itemBase.isVisible()) {
					itemBase.onRelease(x, y, button);
				}
			}
		}

		for (FlowComponent itemBase : manager.getZLevelRenderingList()) {
			if (itemBase.isVisible()) {
				itemBase.postRelease();
			}
		}

	}

	@Override
	protected void mouseClickMove(int x, int y, int button, long ticks) {
		x = scaleX(x);
		y = scaleY(y);

		super.mouseClickMove(x, y, button, ticks);

		x -= guiLeft;
		y -= guiTop;

		if (hasSpecialRenderer()) {
			getSpecialRenderer().onDrag(this, x, y);
			return;
		}

		for (FlowComponent itemBase : manager.getZLevelRenderingList()) {
			if (itemBase.isVisible()) {
				itemBase.onDrag(x, y);
			}
		}
	}

	@Override
	public void onGuiClosed() {
		Keyboard.enableRepeatEvents(false);

		for (FlowComponent flowComponent : manager.getFlowItems()) {
			flowComponent.onGuiClosed();
		}

		super.onGuiClosed();
	}

	private boolean hasSpecialRenderer() {
		return getSpecialRenderer() != null;
	}

	private IInterfaceRenderer getSpecialRenderer() {
		return manager.specialRenderer;
	}

	@Override
	public void handleMouseInput() throws IOException {
		super.handleMouseInput();

		int scroll = Mouse.getEventDWheel();
		if (scroll != 0) {
			if (hasSpecialRenderer()) {
				getSpecialRenderer().onScroll(scroll);
				return;
			}

			for (FlowComponent component : manager.getZLevelRenderingList()) {
				if (component.isVisible()) {
					component.doScroll(scroll);
					return;
				}
			}
		}
	}

	@Override
	public void drawWorldBackground(int val) {
		if (usePinkScreen) {
			drawRect(0, 0, width, height, 0xFFEC008C);
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		} else if (useBlueScreen) {
			drawRect(0, 0, width, height, 0xFF000A91);
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		} else if (useGreenScreen) {
			drawRect(0, 0, width, height, 0xFF00FF00);
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		} else {
			super.drawWorldBackground(val);
		}
	}

	public TileEntityManager getManager() {
		return manager;
	}

	private abstract class SecretCode {
		private final String code;
		private       int    triggerNumber;

		private SecretCode(String code) {
			this.code = code;
		}

		public boolean keyTyped(char c) {
			if (Character.isAlphabetic(c)) {
				if (code.charAt(triggerNumber) == c) {
					if (triggerNumber + 1 > code.length() - 1) {
						triggerNumber = 0;
						trigger();
					} else {
						triggerNumber++;
					}
					return true;
				} else if (triggerNumber != 0) {
					triggerNumber = 0;
					keyTyped(c);
				}
			}

			return false;
		}

		protected abstract void trigger();
	}

}
