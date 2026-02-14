package vswe.superfactory.interfaces;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.CollisionHelper;
import vswe.superfactory.Localization;
import vswe.superfactory.network.packets.DataBitHelper;
import vswe.superfactory.network.packets.DataWriter;
import vswe.superfactory.network.packets.PacketHandler;
import vswe.superfactory.tiles.TileEntityRelay;
import vswe.superfactory.util.UserPermission;
import vswe.superfactory.util.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SideOnly(Side.CLIENT)
public class GuiRelay extends GuiBase {
	private static final int BUTTON_AREA_WIDTH = 97;
	private static final int BUTTON_AREA_X     = 121;
	private static final int BUTTON_AREA_Y     = 13;
	private static final int BUTTON_HEIGHT     = 11;
	private static final int BUTTON_TEXT_Y     = 4;
	private static final int BUTTON_WIDTH      = 47;
	private static final int BUTTON_X_LEFT     = BUTTON_AREA_X;
	private static final int BUTTON_X_MIDDLE   = BUTTON_X_LEFT + (BUTTON_AREA_WIDTH - BUTTON_WIDTH) / 2;
	private static final int BUTTON_X_RIGHT    = BUTTON_X_LEFT + BUTTON_AREA_WIDTH - BUTTON_WIDTH;
	private static final int BUTTON_Y_BOT      = 75;
	private static final int BUTTON_Y_DOWN         = 64;
	private static final int BUTTON_Y_FAR_BOT      = 90;
	private static final int BUTTON_Y_FURTHER_DOWN = 77;
	private static final int BUTTON_Y_MIDDLE   = 60;
	private static final int BUTTON_Y_TOP      = 45;
	private static final int INFO_BOX_FULL_TEXT_W = 85;
	private static final int INFO_BOX_FULL_TEXT_X = 5;
	private static final int INFO_BOX_FULL_TEXT_Y = 5;
	private static final int INFO_BOX_INFO_X      = 5;
	private static final int INFO_BOX_INFO_Y      = 16;
	private static final int INFO_BOX_NAME_X      = 5;
	private static final int INFO_BOX_NAME_Y      = 5;
	private static final int INFO_BOX_POS_X = 122;
	private static final int INFO_BOX_POS_Y = 109;
	private static final int INFO_MARGIN_INFO_Y   = 3;
	private static final int INFO_MARGIN_X = 1;
	private static final int INFO_MARGIN_Y = 2;
	private static final int INFO_SIZE     = 7;
	private static final int INFO_TEXT_X          = 6;
	private static final int INFO_TEXT_Y          = 1;
	private static final int LIST_MENU_HEIGHT = 12;
	private static final int LIST_MENU_WIDTH  = 95;
	private static final int LIST_POS_X = 12;
	private static final int LIST_POS_Y = 14;
	private static final int LIST_TEXT_POS_X = 3;
	private static final int LIST_TEXT_POS_Y = 4;
	private static final int NO_ACCESS_TEXT_Y = 30;
	private static final int PAGE_BUTTON_DISTANCE = 40;
	private static final int PAGE_BUTTON_SIZE     = 7;
	private static final int PAGE_BUTTON_X_LEFT   = LIST_POS_X + (LIST_MENU_WIDTH - PAGE_BUTTON_DISTANCE) / 2 - PAGE_BUTTON_SIZE;
	private static final int PAGE_BUTTON_X_RIGHT  = LIST_POS_X + (LIST_MENU_WIDTH + PAGE_BUTTON_DISTANCE) / 2;
	private static final int PAGE_BUTTON_Y        = 147;
	private static final int PAGE_Y               = 149;
	private static final int PERMISSIONS_PER_PAGE = 11;
	private static final ResourceLocation TEXTURE = registerTexture("relay");
	private List<Button>    buttons = new ArrayList<Button>();
	private UserPermission cachedPermission;
	private int             currentPage;
	private boolean        hasCachedPermission;
	private TileEntityRelay relay;
	private int selectedPermission = -1;

	public GuiRelay(final TileEntityRelay relay, InventoryPlayer player) {
		super(new ContainerRelay(relay, player));

		xSize = 229;
		ySize = 158;

		this.relay = relay;
		System.out.println(relay + " opened");
		buttons.add(new Button(Localization.GIVE_PERMISSION, BUTTON_X_LEFT, BUTTON_Y_DOWN) {
			@Override
			public boolean isVisible() {
				return getSelectedPermission() == -1;
			}

			@Override
			public boolean isEnabled() {
				return getUserPermission() == null;
			}

			@Override
			public void onClick() {
				if (relay.getPermissions().size() < TileEntityRelay.PERMISSION_MAX_LENGTH) {
					relay.getPermissions().add(new UserPermission(getUserId(), getUserName()));
					addUser();
				}
			}
		});

		buttons.add(new Button(Localization.REVOKE_PERMISSION, BUTTON_X_RIGHT, BUTTON_Y_DOWN) {
			@Override
			public boolean isVisible() {
				return getSelectedPermission() == -1;
			}

			@Override
			public boolean isEnabled() {
				return getUserPermission() != null;
			}

			@Override
			public void onClick() {
				removeUser();
			}
		});

		buttons.add(new Button(Localization.ACTIVATE_USER, BUTTON_X_LEFT, BUTTON_Y_TOP) {
			@Override
			public boolean isVisible() {
				return getSelectedPermission() != -1 && isOp(getUserPermission(), true);
			}

			@Override
			public boolean isEnabled() {
				return !relay.getPermissions().get(getSelectedPermission()).isActive();
			}

			@Override
			public void onClick() {
				relay.getPermissions().get(getSelectedPermission()).setActive(true);
				updateUser(getSelectedPermission());
			}
		});


		buttons.add(new Button(Localization.DEACTIVATE_USER, BUTTON_X_RIGHT, BUTTON_Y_TOP) {
			@Override
			public boolean isVisible() {
				return getSelectedPermission() != -1 && isOp(getUserPermission(), true);
			}

			@Override
			public boolean isEnabled() {
				return relay.getPermissions().get(getSelectedPermission()).isActive();
			}

			@Override
			public void onClick() {
				relay.getPermissions().get(getSelectedPermission()).setActive(false);
				updateUser(getSelectedPermission());
			}
		});

		buttons.add(new Button(Localization.DELETE_USER, BUTTON_X_MIDDLE, BUTTON_Y_BOT) {
			@Override
			public boolean isVisible() {
				return getSelectedPermission() != -1 && isOp(getUserPermission(), true);
			}

			@Override
			public boolean isEnabled() {
				return true;
			}

			@Override
			public void onClick() {
				removeUser(getSelectedPermission());
				selectedPermission = -1;
			}
		});

		buttons.add(new Button(Localization.MAKE_EDITOR, BUTTON_X_LEFT, BUTTON_Y_MIDDLE) {
			@Override
			public boolean isVisible() {
				return getSelectedPermission() != -1 && isOwner(getUserPermission(), true) && !isOwner(relay.getPermissions().get(getSelectedPermission()), false);
			}

			@Override
			public boolean isEnabled() {
				return !relay.getPermissions().get(getSelectedPermission()).isOp();
			}

			@Override
			public void onClick() {
				relay.getPermissions().get(getSelectedPermission()).setOp(true);
				updateUser(getSelectedPermission());
			}
		});

		buttons.add(new Button(Localization.REMOVE_EDITOR, BUTTON_X_RIGHT, BUTTON_Y_MIDDLE) {
			@Override
			public boolean isVisible() {
				return getSelectedPermission() != -1 && isOwner(getUserPermission(), true) && !isOwner(relay.getPermissions().get(getSelectedPermission()), false);
			}

			@Override
			public boolean isEnabled() {
				return relay.getPermissions().get(getSelectedPermission()).isOp();
			}

			@Override
			public void onClick() {
				relay.getPermissions().get(getSelectedPermission()).setOp(false);
				updateUser(getSelectedPermission());
			}
		});

		buttons.add(new Button(Localization.SHOW_LIST_TO_ALL, BUTTON_X_LEFT, BUTTON_Y_FURTHER_DOWN) {
			@Override
			public boolean isVisible() {
				return getSelectedPermission() == -1 && isOp(getUserPermission(), true);
			}

			@Override
			public boolean isEnabled() {
				return relay.doesListRequireOp();
			}

			@Override
			public void onClick() {
				relay.setListRequireOp(false);
				updateGlobalSettings();
			}
		});

		buttons.add(new Button(Localization.SHOW_TO_EDITORS, BUTTON_X_RIGHT, BUTTON_Y_FURTHER_DOWN) {
			@Override
			public boolean isVisible() {
				return getSelectedPermission() == -1 && isOp(getUserPermission(), true);
			}

			@Override
			public boolean isEnabled() {
				return !relay.doesListRequireOp();
			}

			@Override
			public void onClick() {
				relay.setListRequireOp(true);
				updateGlobalSettings();
			}
		});

		buttons.add(new Button(Localization.ENABLE_CREATIVE_MODE, BUTTON_X_LEFT, BUTTON_Y_FAR_BOT) {
			@Override
			public boolean isVisible() {
				return getSelectedPermission() == -1 && isOp(getUserPermission(), true) && (Minecraft.getMinecraft().playerController.isInCreativeMode() || relay.isCreativeMode());
			}

			@Override
			public boolean isEnabled() {
				return !relay.isCreativeMode();
			}

			@Override
			public void onClick() {
				relay.setCreativeMode(true);
				updateGlobalSettings();
			}
		});

		buttons.add(new Button(Localization.DISABLE_CREATIVE_MODE, BUTTON_X_RIGHT, BUTTON_Y_FAR_BOT) {
			@Override
			public boolean isVisible() {
				return getSelectedPermission() == -1 && isOp(getUserPermission(), true) && (Minecraft.getMinecraft().playerController.isInCreativeMode() || relay.isCreativeMode());
			}

			@Override
			public boolean isEnabled() {
				return relay.isCreativeMode();
			}

			@Override
			public void onClick() {
				relay.setCreativeMode(false);
				updateGlobalSettings();
			}
		});
	}

	@Override
	public ResourceLocation getComponentResource() {
		return TEXTURE;
	}

	private int getSelectedPermission() {
		if (selectedPermission < 0 || selectedPermission >= relay.getPermissions().size() || (relay.doesListRequireOp() && !isOp(getUserPermission(), true))) {
			return selectedPermission = -1;
		} else {
			return selectedPermission;
		}
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float f, int mX, int mY) {
		hasCachedPermission = false;
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

		bindTexture(TEXTURE);
		drawTexture(0, 0, 0, 0, xSize, ySize);

		mX -= guiLeft;
		mY -= guiTop;

		UserPermission player   = getUserPermission();
		UserPermission selected = getSelectedPermission() == -1 ? null : relay.getPermissions().get(getSelectedPermission());


		if (!relay.doesListRequireOp() || isOp(player, true)) {
			int start = getStartId();
			for (int i = start; i <= getEndId(); i++) {
				UserPermission permission = relay.getPermissions().get(i);

				int x = LIST_POS_X;
				int y = LIST_POS_Y + (i - start) * LIST_MENU_HEIGHT;

				int srcXMenu = getSelectedPermission() == i ? 1 : 0;
				int srcYMenu = CollisionHelper.inBounds(x, y, LIST_MENU_WIDTH, LIST_MENU_HEIGHT, mX, mY) ? 1 : 0;

				drawTexture(x, y, srcXMenu * LIST_MENU_WIDTH, ySize + srcYMenu * LIST_MENU_HEIGHT, LIST_MENU_WIDTH, LIST_MENU_HEIGHT);

				for (int j = 0; j < 2; j++) {
					renderInfoBox(j, permission, x + LIST_MENU_WIDTH - (1 + j) * (INFO_SIZE + INFO_MARGIN_X), y + INFO_MARGIN_Y);
				}

				drawString(permission.getUserName(), x + LIST_TEXT_POS_X, y + LIST_TEXT_POS_Y, 0.7F, 0xEEEEEE);
			}

			if (getPageCount() > 1) {
				drawCenteredString(Localization.PAGE.toString() + " " + (currentPage + 1) + " " + Localization.OF.toString() + " " + getPageCount(), LIST_POS_X, PAGE_Y, 0.6F, LIST_MENU_WIDTH, 0x404040);

				for (int i = 0; i < 2; i++) {
					int x = i == 0 ? PAGE_BUTTON_X_LEFT : PAGE_BUTTON_X_RIGHT;
					int y = PAGE_BUTTON_Y;

					int srcXButton = i;
					int srcYButton = CollisionHelper.inBounds(x, y, PAGE_BUTTON_SIZE, PAGE_BUTTON_SIZE, mX, mY) ? 1 : 0;
					drawTexture(x, y, srcXButton * PAGE_BUTTON_SIZE, ySize + LIST_MENU_HEIGHT * 2 + INFO_SIZE + BUTTON_HEIGHT * 3 + srcYButton * PAGE_BUTTON_SIZE, PAGE_BUTTON_SIZE, PAGE_BUTTON_SIZE);
				}
			}
		} else {
			drawCenteredString(Localization.NO_ACCESS.toString(), LIST_POS_X, LIST_POS_Y + NO_ACCESS_TEXT_Y, 1F, LIST_MENU_WIDTH, 0x804040);
		}

		UserPermission info = relay.doesListRequireOp() && !isOp(player, true) ? player : selected;
		if (info != null) {
			drawString(info.getUserName(), INFO_BOX_POS_X + INFO_BOX_NAME_X, INFO_BOX_POS_Y + INFO_BOX_NAME_Y, 0.7F, 0x404040);
			for (int i = 0; i < 2; i++) {
				int x = INFO_BOX_POS_X + INFO_BOX_INFO_X;
				int y = INFO_BOX_POS_Y + INFO_BOX_INFO_Y + i * (INFO_SIZE + INFO_MARGIN_INFO_Y);
				renderInfoBox(i, info, x, y);
				drawInfoBoxString(i, info, x + INFO_SIZE + INFO_TEXT_X, y + INFO_TEXT_Y);
			}
		} else {
			String str;

			if (isOp(player, true)) {
				str = Localization.EDITOR_DESCRIPTION_SHORT.toString();
			} else {
				str = Localization.USER_DESCRIPTION_SHORT.toString();
			}

			drawSplitString(str, INFO_BOX_POS_X + INFO_BOX_FULL_TEXT_X, INFO_BOX_POS_Y + INFO_BOX_FULL_TEXT_Y, INFO_BOX_FULL_TEXT_W, 0.7F, 0x404040);
		}

		for (Button button : buttons) {
			if (button.isVisible()) {
				int textureId = button.isEnabled() ? CollisionHelper.inBounds(button.x, button.y, BUTTON_WIDTH, BUTTON_HEIGHT, mX, mY) ? 2 : 1 : 0;

				drawTexture(button.x, button.y, 0, ySize + LIST_MENU_HEIGHT * 2 + INFO_SIZE + BUTTON_HEIGHT * textureId, BUTTON_WIDTH, BUTTON_HEIGHT);
				drawCenteredString(button.name.toString(), button.x, button.y + BUTTON_TEXT_Y, 0.4F, BUTTON_WIDTH, button.isEnabled() ? 0x404040 : 0x808080);
			}
		}


		String message = null;
		if (getSelectedPermission() == -1) {
			message = Localization.USER_DESCRIPTION_LONG.toString();
		} else if (isOp(getUserPermission(), true)) {
			message = Localization.EDITOR_DESCRIPTION_LONG.toString();
		}

		if (message != null) {
			drawSplitString(message, BUTTON_AREA_X, BUTTON_AREA_Y, BUTTON_AREA_WIDTH, 0.6F, 0x404040);
		}
	}

	@Override
	protected void mouseClicked(int mX, int mY, int b) throws IOException {
		mX = scaleX(mX);
		mY = scaleY(mY);

		super.mouseClicked(mX, mY, b);

		mX -= guiLeft;
		mY -= guiTop;

		UserPermission player = getUserPermission();

		if (!relay.doesListRequireOp() || isOp(player, true)) {
			int start = getStartId();
			for (int i = start; i <= getEndId(); i++) {
				UserPermission permission = relay.getPermissions().get(i);

				int x = LIST_POS_X;
				int y = LIST_POS_Y + (i - start) * LIST_MENU_HEIGHT;

				if (CollisionHelper.inBounds(x, y, LIST_MENU_WIDTH, LIST_MENU_HEIGHT, mX, mY)) {
					if (getSelectedPermission() == i) {
						selectedPermission = -1;
					} else {
						selectedPermission = i;
					}
					break;
				}
			}

			if (getPageCount() > 1) {
				for (int i = -1; i <= 1; i += 2) {
					int x = i == -1 ? PAGE_BUTTON_X_LEFT : PAGE_BUTTON_X_RIGHT;
					int y = PAGE_BUTTON_Y;

					if (CollisionHelper.inBounds(x, y, PAGE_BUTTON_SIZE, PAGE_BUTTON_SIZE, mX, mY)) {
						currentPage += i;
						if (currentPage < 0) {
							currentPage = getPageCount() - 1;
						} else if (currentPage == getPageCount()) {
							currentPage = 0;
						}

						break;
					}
				}
			}
		}

		for (Button button : buttons) {
			if (button.isVisible() && button.isEnabled() && CollisionHelper.inBounds(button.x, button.y, BUTTON_WIDTH, BUTTON_HEIGHT, mX, mY)) {
				button.onClick();
				break;
			}
		}
	}

	private int getStartId() {
		int maxPageId = getPageCount();
		if (currentPage >= maxPageId) {
			currentPage = maxPageId - 1;
		}

		return currentPage * PERMISSIONS_PER_PAGE;
	}

	private int getPageCount() {
		return (relay.getPermissions().size() - 1) / PERMISSIONS_PER_PAGE + 1;
	}

	private int getEndId() {
		return Math.min(getStartId() + PERMISSIONS_PER_PAGE, relay.getPermissions().size()) - 1;
	}

	private void renderInfoBox(int id, UserPermission permission, int x, int y) {
		int textureId = id == 0 ? isOwner(permission, false) ? 5 : isOp(permission, false) ? 4 : 3 : permission.isActive() ? relay.isCreativeMode() ? 2 : 1 : 0;

		drawTexture(x, y, textureId * INFO_SIZE, ySize + LIST_MENU_HEIGHT * 2, INFO_SIZE, INFO_SIZE);
	}

	private void drawInfoBoxString(int id, UserPermission permission, int x, int y) {
		String str = id == 0 ? isOwner(permission, false) ? Localization.PERMISSION_OWNER.toString() : isOp(permission, false) ? Localization.PERMISSION_EDITOR.toString() : Localization.PERMISSION_USER.toString() : relay.isCreativeMode() ? permission.isActive() ? Localization.PERMISSION_RESTRICTED.toString() : Localization.PERMISSION_CREATIVE.toString() : permission.isActive() ? Localization.PERMISSION_INVENTORY.toString() : Localization.PERMISSION_DENIED.toString();

		drawString(str, x, y, 0.7F, 0x404040);
	}

	private boolean isOwner(UserPermission permission, boolean viewer) {
		return (permission != null && permission.getUserId().equals(relay.getOwner())) || (viewer && getUserId().equals(relay.getOwner()));
	}

	private boolean isOp(UserPermission permission, boolean viewer) {
		return isOwner(permission, viewer) || (permission != null && permission.isOp());
	}

	private UUID getUserId() {
		return Minecraft.getMinecraft().player.getUniqueID();
	}

	private String getUserName() {
		return Utils.stripControlCodes(Minecraft.getMinecraft().player.getDisplayNameString());
	}

	private UserPermission getUserPermission() {
		if (hasCachedPermission) {
			return cachedPermission;
		} else {
			hasCachedPermission = true;
			cachedPermission = null;
		}

		for (UserPermission permission : relay.getPermissions()) {
			if (permission.getUserId().equals(getUserId())) {
				cachedPermission = permission;
				break;
			}
		}
		return cachedPermission;
	}

	private void removeUser(int id) {
		DataWriter dw = PacketHandler.getWriterForServerPacket();
		dw.writeBoolean(true); //user data
		dw.writeBoolean(false); //existing
		dw.writeData(id, DataBitHelper.PERMISSION_ID);
		dw.writeBoolean(true); //deleted
		PacketHandler.sendDataToServer(dw);
	}

	private void removeUser() {
		for (int i = 0; i < relay.getPermissions().size(); i++) {
			if (relay.getPermissions().get(i).getUserId().equals(getUserId())) {
				removeUser(i);
				break;
			}
		}
	}

	private void updateGlobalSettings() {
		DataWriter dw = PacketHandler.getWriterForServerPacket();
		dw.writeBoolean(false); //no user data
		dw.writeBoolean(relay.isCreativeMode());
		dw.writeBoolean(relay.doesListRequireOp());
		PacketHandler.sendDataToServer(dw);
	}

	private void addUser() {
		DataWriter dw = PacketHandler.getWriterForServerPacket();
		dw.writeBoolean(true); //user data
		dw.writeBoolean(true); //added
		dw.writeString(getUserId().toString(), DataBitHelper.UUID_LENGTH);
		dw.writeString(getUserName(), DataBitHelper.NAME_LENGTH);
		PacketHandler.sendDataToServer(dw);
	}

	private void updateUser(int id) {
		UserPermission permission = relay.getPermissions().get(id);
		DataWriter     dw         = PacketHandler.getWriterForServerPacket();
		dw.writeBoolean(true); //user data
		dw.writeBoolean(false); //existing
		dw.writeData(id, DataBitHelper.PERMISSION_ID);
		dw.writeBoolean(false); //update
		dw.writeBoolean(permission.isActive());
		dw.writeBoolean(permission.isOp());
		PacketHandler.sendDataToServer(dw);
	}

	private abstract class Button {
		private Localization name;
		private int          x, y;

		protected Button(Localization name, int x, int y) {
			this.name = name;
			this.x = x;
			this.y = y;
		}

		public abstract boolean isVisible();

		public abstract boolean isEnabled();

		public abstract void onClick();
	}
}
