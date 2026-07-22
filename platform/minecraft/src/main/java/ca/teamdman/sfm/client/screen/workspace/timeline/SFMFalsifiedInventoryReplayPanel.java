package ca.teamdman.sfm.client.screen.workspace.timeline;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.SFMGuiCrosshair;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Read-only fixture visualization. It is deliberately not backed by a Menu or player inventory. */
public final class SFMFalsifiedInventoryReplayPanel implements SFMSeekableTimelinePanel {
    public static final SFMTimelineBounds BOUNDS = SFMInventoryReplayFixture.BOUNDS;
    private static final int CHEST_SLOT = 2;
    private static final int PLAYER_SLOT = 13;
    private double keyframePosition;
    private SFMInventoryReplayState state = stateAt(0D);

    @Override
    public Component title() { return Component.literal("Falsified chest replay"); }

    @Override
    public Component narration() {
        return Component.literal("Inventory replay. ").append(Component.literal(state.phase()));
    }

    @Override
    public SFMTimelineBounds timelineBounds() { return BOUNDS; }

    @Override
    public SFMKeyframeTimeline animationTimeline(int ignored) { return SFMInventoryReplayFixture.TIMELINE; }

    @Override
    public void setTimelinePosition(int keyframe) { setTimelinePosition((double) keyframe); }

    @Override
    public void setTimelinePosition(double keyframePosition) {
        if (keyframePosition < BOUNDS.first() || keyframePosition > BOUNDS.last()) {
            throw new IllegalArgumentException("Inventory replay keyframe position is outside bounds: " + keyframePosition);
        }
        this.keyframePosition = keyframePosition;
        this.state = stateAt(keyframePosition);
    }

    public int timestep() { return (int) Math.round(keyframePosition); }
    public double keyframePosition() { return keyframePosition; }
    public SFMInventoryReplayState state() { return state; }

    @Override
    public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds, int mouseX, int mouseY,
                       float partialTick, boolean focused) {
        SFMInventoryReplayGeometry geometry = SFMInventoryReplayGeometry.fit(
                bounds.x(), bounds.y(), bounds.width(), bounds.height()
        );
        String heading = "Visual replay — no live inventory is modified";
        int headingX = bounds.x() + (bounds.width() - minecraft.font.width(heading)) / 2;
        SFMFontUtils.draw(poseStack, minecraft.font,
                Component.literal(heading).withStyle(ChatFormatting.GOLD),
                headingX, bounds.y() + 8, 0xFFFFFFFF, false);

        SFMFontUtils.draw(poseStack, minecraft.font, "Chest", geometry.left(), geometry.chestTop() - 11,
                0xFFDDDDDD, false);
        renderSlots(poseStack, minecraft, state.chestSlots(), geometry.left(), geometry.chestTop(),
                geometry.slotPitch(), 9, 3, 0);

        SFMFontUtils.draw(poseStack, minecraft.font, "Player inventory", geometry.left(),
                geometry.playerMainTop() - 11, 0xFFDDDDDD, false);
        renderSlots(poseStack, minecraft, state.playerSlots(), geometry.left(), geometry.playerMainTop(),
                geometry.slotPitch(), 9, 3, 0);
        renderSlots(poseStack, minecraft, state.playerSlots(), geometry.left(), geometry.hotbarTop(),
                geometry.slotPitch(), 9, 1, 27);

        SFMInventoryReplayGeometry.Point source = geometry.chestSlotCenter(CHEST_SLOT);
        SFMInventoryReplayGeometry.Point destination = geometry.playerSlotCenter(PLAYER_SLOT);
        double progress = state.cursorPathPosition();
        int cursorX = (int) Math.round(source.x() + (destination.x() - source.x()) * progress);
        int cursorY = (int) Math.round(source.y() + (destination.y() - source.y()) * progress);
        renderStack(minecraft, state.cursorStack(), cursorX - 8, cursorY - 8);
        poseStack.pushPose();
        poseStack.translate(0D, 0D, 250D);
        SFMGuiCrosshair.draw(poseStack, cursorX, cursorY, 7, 0xFF55FFFF);
        poseStack.popPose();

        String phase = String.format("keyframe %.2f  %s", keyframePosition, state.phase());
        SFMFontUtils.draw(poseStack, minecraft.font, phase,
                bounds.x() + Math.max(4, (bounds.width() - minecraft.font.width(phase)) / 2),
                bounds.y() + bounds.height() - 14, 0xFF55FFFF, false);
    }

    public static SFMInventoryReplayState stateAt(double keyframePosition) {
        if (keyframePosition < BOUNDS.first() || keyframePosition > BOUNDS.last()) {
            throw new IllegalArgumentException("Inventory replay keyframe position is outside bounds: " + keyframePosition);
        }
        List<ItemStack> chest = emptyStacks(27);
        List<ItemStack> player = emptyStacks(36);
        SFMInventoryReplayFixture.Frame frame = SFMInventoryReplayFixture.sample(keyframePosition);
        if (frame.chestOwnsCobblestone()) chest.set(CHEST_SLOT, new ItemStack(Items.COBBLESTONE));
        if (frame.playerOwnsCobblestone()) player.set(PLAYER_SLOT, new ItemStack(Items.COBBLESTONE));
        ItemStack cursor = frame.cursorOwnsCobblestone() ? new ItemStack(Items.COBBLESTONE) : ItemStack.EMPTY;
        return new SFMInventoryReplayState(chest, player, cursor, frame.cursorPathPosition(), frame.phase());
    }

    private static List<ItemStack> emptyStacks(int count) {
        List<ItemStack> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(ItemStack.EMPTY);
        return result;
    }

    private static void renderSlots(PoseStack poseStack, Minecraft minecraft, List<ItemStack> stacks,
                                    int left, int top, int slotSize, int columns, int rows, int stackOffset) {
        for (int index = 0; index < columns * rows; index++) {
            int x = left + index % columns * slotSize;
            int y = top + index / columns * slotSize;
            GuiComponent.fill(poseStack, x, y, x + slotSize - 1, y + slotSize - 1, 0xFF8B8B8B);
            GuiComponent.fill(poseStack, x + 1, y + 1, x + slotSize - 2, y + slotSize - 2, 0xFF373737);
            renderStack(minecraft, stacks.get(stackOffset + index), x + (slotSize - 16) / 2,
                    y + (slotSize - 16) / 2);
        }
    }

    private static void renderStack(Minecraft minecraft, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        minecraft.getItemRenderer().renderAndDecorateItem(stack, x, y);
        minecraft.getItemRenderer().renderGuiItemDecorations(minecraft.font, stack, x, y);
    }
}
