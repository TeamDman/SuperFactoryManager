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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

@Mod.EventBusSubscriber(modid = SFM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ContainerScreenInspectorHandler {
    private static boolean visible = false;
    private static @Nullable AbstractContainerScreen<?> lastScreen = null;
    private static final Button exportInspectorButton = new SFMButtonBuilder()
            .setSize(100, 20)
            .setPosition(5, 50)
            .setText(LocalizationKeys.CONTAINER_INSPECTOR_SHOW_EXPORTS_BUTTON)
            .setOnPress((button) -> {
                BlockEntity lookBlockEntity = ClientRayCastHelpers.getLookBlockEntity();
                if (lastScreen != null && lookBlockEntity != null) {
                    SFMPackets.sendToServer(new ServerboundContainerExportsInspectionRequestPacket(
                            lastScreen.getMenu().containerId,
                            lookBlockEntity.getBlockPos()
                    ));
                }
            })
            .build();

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.KeyPressed.MouseButtonPressed.Pre event) {
        boolean shouldCapture = Minecraft.getInstance().screen instanceof AbstractContainerScreen<?>;
        if (shouldCapture && visible && exportInspectorButton.clicked(event.getMouseX(), event.getMouseY())) {
            exportInspectorButton.playDownSound(Minecraft.getInstance().getSoundManager());
            exportInspectorButton.onClick(event.getMouseX(), event.getMouseY());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onGuiRender(ScreenEvent.Render.Post event) {
        if (!visible) return;
        if (event.getScreen() instanceof AbstractContainerScreen<?> screen) {
            lastScreen = screen;
            AbstractContainerMenu menu = screen.getMenu();
            int containerSlotCount = 0;
            int inventorySlotCount = 0;
            PoseStack poseStack = event.getPoseStack();
            poseStack.pushPose();
            poseStack.translate(0, 0, 350); // render text over the items but under the tooltips

            // draw the button
            exportInspectorButton.render(poseStack, event.getMouseX(), event.getMouseY(), event.getPartialTick());


            // draw index on each slot
            Font font = Minecraft.getInstance().font;
            for (var slot : menu.slots) {
                int colour;
                // TODO: can we reference-compare this to the capabilities to find out if this matches any of the inventories exposed for automation?
                if (slot.container instanceof Inventory) {
                    //noinspection DataFlowIssue
                    colour = ChatFormatting.YELLOW.getColor();
                    inventorySlotCount++;
                } else {
                    colour = 0xFFF;
                    containerSlotCount++;
                }
                SFMFontUtils.draw(
                        poseStack,
                        font,
                        Component.literal(Integer.toString(slot.getSlotIndex())),
                        screen.getGuiLeft() + slot.x,
                        screen.getGuiTop() + slot.y,
                        colour,
                        false
                );
            }

            // draw centered notices
            {
                var notice = LocalizationKeys.CONTAINER_INSPECTOR_NOTICE_1
                        .getComponent()
                        .withStyle(ChatFormatting.GOLD);
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
