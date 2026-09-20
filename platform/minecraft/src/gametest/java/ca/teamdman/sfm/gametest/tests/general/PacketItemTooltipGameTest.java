package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
import ca.teamdman.sfm.client.tooltip.SFMTooltipModeService;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@SFMGameTest(SFMDist.CLIENT)
public class PacketItemTooltipGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "1x1x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        Minecraft minecraft = Minecraft.getInstance();
        AtomicBoolean complete = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        // One client task owns and restores the transient override before any render/input task runs.
        // Ambient GameTests never open a screen, move a pointer, or require the player to release a key.
        minecraft.execute(() -> {
            var previousMode = SFMTooltipModeService.INSTANCE.mode();
            var screen = minecraft.screen;
            var context = SFMClientActionContext.create(screen, () -> minecraft.screen == screen);
            try {
                var stack = PacketItem.create(SFMValue.object(Map.of(
                        "JobId", SFMValue.of("same"), "type", SFMValue.of("Response"))));
                var disk = new ItemStack(SFMItems.DISK.get());
                DiskItem.setProgram(disk, "NAME \"Tooltip fixture\"\nEVERY 20 TICKS DO END");
                DiskItem.setProgramName(disk, "Tooltip fixture");

                invoke(context, "compact");
                String compact = tooltip(stack);
                check(compact.contains("Contains packet data") && !compact.contains("\"JobId\""),
                        "Compact production tooltip must hide the packet value");
                check(disk.getHoverName().getString().equals("Tooltip fixture") && !tooltip(disk).contains("EVERY"),
                        "Compact disk presentation did not retain its program name and hide its source");

                invoke(context, "expand");
                String expanded = tooltip(stack);
                check(expanded.contains("\"JobId\": \"same\"") && expanded.contains("\"type\": \"Response\""),
                        "Expanded production tooltip must reveal the complete pretty packet value");
                check(!disk.getHoverName().getString().equals("Tooltip fixture") && tooltip(disk).contains("EVERY"),
                        "Disk name and source did not share the semantic more-info mode");
                invoke(context, "expand");
                check(tooltip(stack).equals(expanded), "Repeated expand action must be idempotent");
                invoke(context, "compact");
                check(tooltip(stack).equals(compact), "Compact action did not restore the original tooltip");
                invoke(context, "reset");
                check(SFMTooltipModeService.INSTANCE.mode() == SFMTooltipModeService.Mode.AUTO,
                        "Reset must restore configured-key mode");
                check(minecraft.screen == screen, "Tooltip actions opened or replaced a screen");
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                SFMTooltipModeService.INSTANCE.setMode(previousMode);
                complete.set(true);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(failure.get() == null, "Tooltip action regression: " + failure.get());
            helper.assertTrue(complete.get(), "Waiting for the screen-free client tooltip probe");
        });
    }

    private static void invoke(SFMClientActionContext context, String operation) throws Exception {
        check(SFMClientActionExecutor.execute("sfm action invoke sfm:tooltip/more_info/" + operation,
                context, ignored -> { }) == 1, "Registered tooltip action did not succeed: " + operation);
    }

    private static String tooltip(ItemStack stack) {
        return text(stack.getTooltipLines(Minecraft.getInstance().player, TooltipFlag.Default.NORMAL));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static String text(List<Component> lines) {
        return lines.stream().map(Component::getString)
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
