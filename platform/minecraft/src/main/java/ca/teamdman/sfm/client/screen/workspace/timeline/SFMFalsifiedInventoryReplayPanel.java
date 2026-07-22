package ca.teamdman.sfm.client.screen.workspace.timeline;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
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
    private int timestep;
    private SFMInventoryReplayState state = stateAt(0);

    @Override
    public Component title() {
        return Component.literal("Falsified chest replay");
    }

    @Override
    public Component narration() {
        return Component.literal("Inventory replay. ").append(Component.literal(state.phase()));
    }

    @Override
    public SFMTimelineBounds timelineBounds() {
        return BOUNDS;
    }

    @Override
    public void setTimelinePosition(int timestep) {
        if (timestep < BOUNDS.first() || timestep > BOUNDS.last()) {
            throw new IllegalArgumentException("Inventory replay timestep is outside bounds: " + timestep);
        }
        this.timestep = timestep;
        this.state = stateAt(timestep);
    }

    public int timestep() { return timestep; }
    public SFMInventoryReplayState state() { return state; }

    @Override
    public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds, int mouseX, int mouseY,
                       float partialTick, boolean focused) {
        int slotSize = Math.max(14, Math.min(24, Math.min(
                (bounds.width() - 40) / 9,
                (bounds.height() - 76) / 7
        )));
        int gridWidth = slotSize * 9;
        int left = bounds.x() + Math.max(10, (bounds.width() - gridWidth) / 2);
        int contentHeight = slotSize * 7 + 24;
        int availableHeight = Math.max(contentHeight, bounds.height() - 52);
        int top = bounds.y() + 34 + Math.max(0, (availableHeight - contentHeight) / 2);

        SFMFontUtils.draw(poseStack, minecraft.font,
                Component.literal("Visual replay — no live inventory is modified").withStyle(ChatFormatting.GOLD),
                left, bounds.y() + 8, 0xFFFFFFFF, false);
        SFMFontUtils.draw(poseStack, minecraft.font, "Chest", left, top - 11, 0xFFDDDDDD, false);
        renderSlots(poseStack, minecraft, state.chestSlots(), left, top, slotSize, 9, 3);

        int playerTop = top + slotSize * 3 + 24;
        SFMFontUtils.draw(poseStack, minecraft.font, "Player inventory", left, playerTop - 11, 0xFFDDDDDD, false);
        renderSlots(poseStack, minecraft, state.playerSlots(), left, playerTop, slotSize, 9, 4);

        double progress = state.cursorPathPosition();
        int sourceX = left + CHEST_SLOT % 9 * slotSize + slotSize / 2;
        int sourceY = top + CHEST_SLOT / 9 * slotSize + slotSize / 2;
        int destinationX = left + PLAYER_SLOT % 9 * slotSize + slotSize / 2;
        int destinationY = playerTop + PLAYER_SLOT / 9 * slotSize + slotSize / 2;
        int cursorX = (int) Math.round(sourceX + (destinationX - sourceX) * progress);
        int cursorY = (int) Math.round(sourceY + (destinationY - sourceY) * progress);
        drawCursor(poseStack, cursorX, cursorY);
        renderStack(minecraft, state.cursorStack(), cursorX + 3, cursorY + 3);

        String phase = "t=" + timestep + "  " + state.phase();
        SFMFontUtils.draw(poseStack, minecraft.font, phase, left, bounds.y() + bounds.height() - 14,
                0xFF55FFFF, false);
    }

    public static SFMInventoryReplayState stateAt(int timestep) {
        if (timestep < BOUNDS.first() || timestep > BOUNDS.last()) {
            throw new IllegalArgumentException("Inventory replay timestep is outside bounds: " + timestep);
        }
        List<ItemStack> chest = emptyStacks(27);
        List<ItemStack> player = emptyStacks(36);
        SFMInventoryReplayFixture.Frame frame = SFMInventoryReplayFixture.frameAt(timestep);
        if (frame.chestOwnsCobblestone()) chest.set(CHEST_SLOT, new ItemStack(Items.COBBLESTONE));
        if (frame.playerOwnsCobblestone()) player.set(PLAYER_SLOT, new ItemStack(Items.COBBLESTONE));
        ItemStack cursor = frame.cursorOwnsCobblestone() ? new ItemStack(Items.COBBLESTONE) : ItemStack.EMPTY;
        return new SFMInventoryReplayState(
                chest,
                player,
                cursor,
                frame.cursorPathPosition(),
                frame.phase()
        );
    }

    private static List<ItemStack> emptyStacks(int count) {
        List<ItemStack> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(ItemStack.EMPTY);
        return result;
    }

    private static void renderSlots(PoseStack poseStack, Minecraft minecraft, List<ItemStack> stacks,
                                    int left, int top, int slotSize, int columns, int rows) {
        for (int index = 0; index < columns * rows; index++) {
            int x = left + index % columns * slotSize;
            int y = top + index / columns * slotSize;
            GuiComponent.fill(poseStack, x, y, x + slotSize - 1, y + slotSize - 1, 0xFF8B8B8B);
            GuiComponent.fill(poseStack, x + 1, y + 1, x + slotSize - 2, y + slotSize - 2, 0xFF373737);
            renderStack(minecraft, stacks.get(index), x + (slotSize - 16) / 2, y + (slotSize - 16) / 2);
        }
    }

    private static void renderStack(Minecraft minecraft, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        minecraft.getItemRenderer().renderAndDecorateItem(stack, x, y);
        minecraft.getItemRenderer().renderGuiItemDecorations(minecraft.font, stack, x, y);
    }

    private static void drawCursor(PoseStack poseStack, int x, int y) {
        GuiComponent.fill(poseStack, x, y, x + 2, y + 15, 0xFFFFFFFF);
        GuiComponent.fill(poseStack, x, y, x + 10, y + 2, 0xFFFFFFFF);
        GuiComponent.fill(poseStack, x + 2, y + 2, x + 8, y + 9, 0xFF202020);
    }
}
