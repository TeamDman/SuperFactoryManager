package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.ClientRayCastHelpers;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.widget.SFMButtonBuilder;
import ca.teamdman.sfm.client.widget.SFMExtendedButton;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.net.ServerboundContainerExportsInspectionRequestPacket;
import ca.teamdman.sfm.common.registry.SFMPackets;
import com.bbscn.Button;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

@Mod.EventBusSubscriber(modid = SFM.MOD_ID, value = Side.CLIENT)
public class ContainerScreenInspectorHandler {
    private static boolean visible = false;
    private static @Nullable GuiContainer lastScreen = null;
    private static final Button exportInspectorButton = new SFMButtonBuilder()
            .setSize(100, 20)
            .setPosition(5, 50)
            .setText(LocalizationKeys.CONTAINER_INSPECTOR_SHOW_EXPORTS_BUTTON)
            .setOnPress((button) -> {
                TileEntity lookBlockEntity = ClientRayCastHelpers.getLookBlockEntity();
                if (lastScreen != null && lookBlockEntity != null) {
                    SFMPackets.sendToServer(new ServerboundContainerExportsInspectionRequestPacket(
                            lastScreen.inventorySlots.windowId,
                            lookBlockEntity.getPos()
                    ));
                }
            })
            .build();

    @SubscribeEvent
    public static void onMouseClick(GuiScreenEvent.MouseInputEvent.Pre event) {
        boolean shouldCapture = Minecraft.getMinecraft().currentScreen instanceof GuiContainer;
        final int eventButton = Mouse.getEventButton();

        if (eventButton > -1 && Mouse.getEventButtonState() && shouldCapture && visible) {
            int mouseX = Mouse.getEventX();
            int mouseY = Mouse.getEventY();

            // 2. Get the current scaled resolution of the game window
            Minecraft mc = Minecraft.getMinecraft();
            ScaledResolution scaledResolution = new ScaledResolution(mc);

            // 3. Calculate the scaled mouse position used by GUIs
            int scaledMouseX = mouseX * scaledResolution.getScaledWidth() / mc.displayWidth;
            int scaledMouseY = scaledResolution.getScaledHeight() - mouseY * scaledResolution.getScaledHeight() / mc.displayHeight - 1;


            if (exportInspectorButton.clicked(scaledMouseX, scaledMouseY)) {
                exportInspectorButton.playDownSound(Minecraft.getMinecraft());
                exportInspectorButton.onClick(scaledMouseX, scaledMouseY, 0);
                event.setCanceled(true);
            }
        }
    }


    @SubscribeEvent
    public static void onGuiRender(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!visible) return;
        if (event.getGui() instanceof GuiContainer screen) {
            lastScreen = screen;
            Container menu = screen.inventorySlots;
            int containerSlotCount = 0;
            int inventorySlotCount = 0;

            GlStateManager.pushMatrix();
            GlStateManager.translate(0, 0, 350); // render text over the items but under the tooltips

            // draw the button
            exportInspectorButton.render(event.getMouseX(), event.getMouseY(), event.getRenderPartialTicks());


            // draw index on each slot
            FontRenderer font = Minecraft.getMinecraft().fontRenderer;
            for (var slot : menu.inventorySlots) {
                TextFormatting colour;
                // TODO: can we reference-compare this to the capabilities to find out if this matches any of the inventories exposed for automation?
                if (slot.inventory instanceof InventoryPlayer) {
                    //noinspection DataFlowIssue
                    colour = TextFormatting.YELLOW;
                    inventorySlotCount++;
                } else {
                    colour = TextFormatting.BLUE;
                    containerSlotCount++;
                }
                SFMFontUtils.draw(
                        font,
                        new TextComponentString(Integer.toString(slot.getSlotIndex())).setStyle(new Style().setColor(colour)),
                        screen.getGuiLeft() + slot.xPos,
                        screen.getGuiTop() + slot.yPos,
                        -1,
                        false
                );
            }

            // draw centered notices
            {
                var notice = LocalizationKeys.CONTAINER_INSPECTOR_NOTICE_1
                        .getComponent()
                        .setStyle(new Style().setColor(TextFormatting.GOLD));
                int offset = font.getStringWidth(notice.getUnformattedText()) / 2;
                SFMFontUtils.draw(
                        font,
                        notice,
                        screen.width / 2 - offset,
                        5,
                        0xFFFFFF,
                        true
                );
            }
            {
                var notice = LocalizationKeys.CONTAINER_INSPECTOR_NOTICE_2.getComponent(
                        new TextComponentString(
                                SFMKeyMappings.CONTAINER_INSPECTOR_KEY.getDisplayName()
                        )
                                .setStyle(new Style().setColor(TextFormatting.AQUA))
                ).setStyle(new Style().setColor(TextFormatting.GOLD));
                int offset = font.getStringWidth(notice.getUnformattedText()) / 2;
                SFMFontUtils.draw(
                        font,
                        notice,
                        screen.width / 2 - offset,
                        16,
                        0xFFFFFF,
                        true
                );
            }

            // draw text for slot totals
            SFMFontUtils.draw(
                    font,
                    LocalizationKeys.CONTAINER_INSPECTOR_CONTAINER_SLOT_COUNT.getComponent(
                            new TextComponentString(String.valueOf(containerSlotCount)).setStyle(new Style().setColor(TextFormatting.BLUE))
                    ),
                    5,
                    25,
                    0xFFFFFF,
                    true
            );
            SFMFontUtils.draw(
                    font,
                    LocalizationKeys.CONTAINER_INSPECTOR_INVENTORY_SLOT_COUNT.getComponent(
                            new TextComponentString(String.valueOf(inventorySlotCount)).setStyle(new Style().setColor(TextFormatting.YELLOW))
                    ),
                    5,
                    40,
                    0xFFFFFF,
                    true
            );
            GlStateManager.popMatrix();
        }
    }

    @SubscribeEvent
    public static void onKeyDown(GuiScreenEvent.KeyboardInputEvent.Post event) {
        char typedChar = Keyboard.getEventCharacter();
        int eventKey = Keyboard.getEventKey();

        if (!(eventKey == 0 && typedChar >= ' ' || Keyboard.getEventKeyState())) {
            return;
        }

        // Handle Ctrl+I hotkey to toggle overlay
        var toggleKeyPressed = SFMKeyMappings.CONTAINER_INSPECTOR_KEY.isActiveAndMatches(eventKey);
        if (toggleKeyPressed) {
            visible = !visible;
            event.setCanceled(true);
            return;
        }

        // Handle ~ hotkey to inspect hovered item
        var activateKeyPressed = SFMKeyMappings.ITEM_INSPECTOR_KEY.isActiveAndMatches(eventKey);
        if (activateKeyPressed) {
            // This doesn't work when activated hovering a JEI item.
            if (event.getGui() instanceof GuiContainer gui) {
                Slot hoveredSlot = gui.getSlotUnderMouse();
                if (hoveredSlot != null) {
                    ItemStack hoveredStack = hoveredSlot.getStack();
                    if (!hoveredStack.isEmpty()) {
                        SFMScreenChangeHelpers.showItemInspectorScreen(hoveredStack);
                    }
                }
            }
        }
    }
}