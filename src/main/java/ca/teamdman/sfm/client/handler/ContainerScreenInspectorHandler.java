package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.ClientRayCastHelpers;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.widget.SFMButtonBuilder;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.net.ServerboundContainerExportsInspectionRequestPacket;
import ca.teamdman.sfm.common.registry.SFMPackets;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.realmsclient.gui.ChatFormatting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.network.chat.Component;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

@Mod.EventBusSubscriber(modid = SFM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ContainerScreenInspectorHandler {
    private static boolean visible = false;
    private static @Nullable GuiContainer<?> lastScreen = null;
    private static final GuiButton exportInspectorButton = new SFMButtonBuilder()
            .setSize(100, 20)
            .setPosition(5, 50)
            .setText(LocalizationKeys.CONTAINER_INSPECTOR_SHOW_EXPORTS_BUTTON)
            .setOnPress((button) -> {
                TileEntity lookBlockEntity = ClientRayCastHelpers.getLookBlockEntity();
                if (lastScreen != null && lookBlockEntity != null) {
                    SFMPackets.SFM_CHANNEL.sendToServer(new ServerboundContainerExportsInspectionRequestPacket(
                            lastScreen.inventorySlots.windowId,
                            lookBlockEntity.getPos()
                    ));
                }
            })
            .build();

    @SubscribeEvent
    public static void onMouseClick(GuiScreenEvent.MouseInputEvent.Pre event) {
        boolean shouldCapture = Minecraft.getMinecraft().currentScreen instanceof GuiContainer;
        if (shouldCapture && visible && exportInspectorButton.clicked(event.getMouseX(), event.getMouseY())) {
            exportInspectorButton.playDownSound(Minecraft.getInstance().getSoundManager());
            exportInspectorButton.onClick(event.getMouseX(), event.getMouseY());
            event.setCanceled(true);
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
//            PoseStack poseStack = event.get();
//            poseStack.pushPose();
//            poseStack.translate(0, 0, 350); // render text over the items but under the tooltips

            // draw the button
            exportInspectorButton.drawButton(Minecraft.getMinecraft(), event.getMouseX(), event.getMouseY(), event.getRenderPartialTicks());


            // draw index on each slot
            FontRenderer font = Minecraft.getMinecraft().fontRenderer;
            for (var slot : menu.inventorySlots) {
                TextFormatting colour;
                // TODO: can we reference-compare this to the capabilities to find out if this matches any of the inventories exposed for automation?
                if (slot.inventory instanceof IInventory) {
                    //noinspection DataFlowIssue
                    colour = TextFormatting.YELLOW;
                    inventorySlotCount++;
                } else {
                    colour = TextFormatting.BLACK;
                    containerSlotCount++;
                }
                SFMFontUtils.draw(
                        font,
                        new TextComponentString(Integer.toString(slot.getSlotIndex())).getStyle().setColor(color),
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
                        .setStyle(ChatFormatting.GOLD);
                int offset = font.width(notice) / 2;
                SFMFontUtils.draw(
                        poseStack,
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
                        SFMKeyMappings.CONTAINER_INSPECTOR_KEY
                                .get()
                                .getTranslatedKeyMessage()
                                .plainCopy()
                                .withStyle(ChatFormatting.AQUA)
                ).withStyle(ChatFormatting.GOLD);
                int offset = font.width(notice) / 2;
                SFMFontUtils.draw(
                        poseStack,
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
                    poseStack,
                    font,
                    LocalizationKeys.CONTAINER_INSPECTOR_CONTAINER_SLOT_COUNT.getComponent(
                            Component.literal(String.valueOf(containerSlotCount)).withStyle(ChatFormatting.BLUE)
                    ),
                    5,
                    25,
                    0xFFFFFF,
                    true
            );
            SFMFontUtils.draw(
                    poseStack,
                    font,
                    LocalizationKeys.CONTAINER_INSPECTOR_INVENTORY_SLOT_COUNT.getComponent(
                            Component.literal(String.valueOf(inventorySlotCount)).withStyle(ChatFormatting.YELLOW)
                    ),
                    5,
                    40,
                    0xFFFFFF,
                    true
            );
            poseStack.popPose();
        }
    }

    @SubscribeEvent
    public static void onKeyDown(ScreenEvent.KeyPressed.Pre event) {
        // Handle Ctrl+I hotkey to toggle overlay
        var toggleKey = SFMKeyMappings.CONTAINER_INSPECTOR_KEY.get();
        var toggleKeyPressed = toggleKey.isActiveAndMatches(InputConstants.Type.KEYSYM.getOrCreate(event.getKeyCode()));
        if (toggleKeyPressed) {
            visible = !visible;
            event.setCanceled(true);
            return;
        }

        // Handle ~ hotkey to inspect hovered item
        var activateKey = SFMKeyMappings.ITEM_INSPECTOR_KEY.get();
        var activateKeyPressed = activateKey.isActiveAndMatches(InputConstants.Type.KEYSYM.getOrCreate(event.getKeyCode()));
        if (activateKeyPressed) {
            // This doesn't work when activated hovering a JEI item.
            if (event.getScreen() instanceof AbstractContainerScreen<?> acs) {
                Slot hoveredSlot = acs.hoveredSlot;
                if (hoveredSlot != null) {
                    ItemStack hoveredStack = hoveredSlot.getItem();
                    if (!hoveredStack.isEmpty()) {
                        SFMScreenChangeHelpers.showItemInspectorScreen(hoveredStack);
                    }
                }
            }
        }
    }
}