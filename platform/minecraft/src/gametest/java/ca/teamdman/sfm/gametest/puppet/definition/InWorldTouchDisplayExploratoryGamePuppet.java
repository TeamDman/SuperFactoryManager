package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.common.block.TouchDisplayBlock;
import ca.teamdman.sfm.common.block.TouchDisplaySurface;
import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.items.IItemHandler;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Opt-in visual and real client-interaction proof, separate from ambient run-all GameTests. */
@SFMGamePuppet(viewportProfile = SFMGamePuppetViewportProfile.COMMON_RESPONSIVE,
        timeoutTicks = 20 * 60 * 15)
public final class InWorldTouchDisplayExploratoryGamePuppet {
    private static final BlockPos DISPLAY = new BlockPos(2, 2, 2);
    private static final BlockPos REAR = DISPLAY.relative(Direction.SOUTH);

    private InWorldTouchDisplayExploratoryGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        AtomicReference<TouchDisplaySurface.UV> requestedTouch = new AtomicReference<>();
        TouchDisplayPuppetFixtureGameTest fixture = new TouchDisplayPuppetFixtureGameTest(requestedTouch);
        puppet.createFreshFlatWorld();
        puppet.startGameTest(fixture);
        puppet.waitTicks(20);
        puppet.exploreTouchDisplayInteractively(requestedTouch);
        puppet.waitForGameTest(fixture.testName());
    }

    /** The fixture passes only after a client gameplay use sends one packet into the rear chest. */
    private static final class TouchDisplayPuppetFixtureGameTest extends SFMGameTestDefinition {
        private final AtomicReference<TouchDisplaySurface.UV> requestedTouch;

        private TouchDisplayPuppetFixtureGameTest(AtomicReference<TouchDisplaySurface.UV> requestedTouch) {
            this.requestedTouch = requestedTouch;
        }

        @Override
        public String template() {
            return "5x5x5";
        }

        @Override
        public int maxTicks() {
            return 20 * 60 * 12;
        }

        @Override
        public void run(SFMGameTestHelper helper) {
            // Open the template's north wall around the camera. A normal
            // teleport inside that solid wall would push the player away and
            // make the crosshair strike the wall instead of the display.
            for (int x = 2; x <= 3; x++) {
                for (int y = 1; y <= 3; y++) {
                    for (int z = 0; z <= 1; z++) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                    }
                }
            }
            // GameTest templates are dark inside; light the face so a captured
            // static image is visually assessable without changing renderer policy.
            helper.setBlock(new BlockPos(4, 3, 2), Blocks.GLOWSTONE);
            helper.setBlock(REAR, Blocks.CHEST);
            helper.setBlock(DISPLAY, SFMBlocks.TOUCH_DISPLAY.get().defaultBlockState()
                    .setValue(TouchDisplayBlock.FACING, Direction.NORTH));
            TouchDisplayBlockEntity display = helper.getBlockEntity(DISPLAY, TouchDisplayBlockEntity.class);
            SFMValue semanticState = SFMValue.object(Map.of("color", SFMValue.of("red")));
            helper.assertTrue(display.commitContent(TouchDisplayBlockEntity.RED_FIXTURE_IMAGE, semanticState),
                    "Touch Display fixture did not commit its red content");
            long revision = display.content().revision();
            BlockPos absoluteDisplay = helper.absolutePos(DISPLAY);

            helper.succeedWhen(() -> {
                IItemHandler rear = helper.getItemHandler(REAR);
                helper.assertCount(rear, SFMItems.PACKET.get(), 1,
                        "Client gameplay use did not deliver exactly one packet to the rear chest");
                ItemStack first = rear.getStackInSlot(0);
                SFMValue packet = PacketItem.getValue(first).orElseThrow();
                helper.assertTrue(packet instanceof SFMValue.ObjectValue,
                        "Touch packet was not an object value");
                Map<String, SFMValue> fields = ((SFMValue.ObjectValue) packet).fields();
                helper.assertTrue(SFMValue.of("sfm:touch@1").equals(fields.get("schema")),
                        "Touch packet schema did not match");
                helper.assertTrue(SFMValue.of("north").equals(fields.get("face")),
                        "Touch packet did not record the north display face");
                helper.assertTrue(SFMValue.of(revision).equals(fields.get("contentRevision")),
                        "Touch packet did not snapshot the committed content revision");
                helper.assertTrue(semanticState.equals(fields.get("state")),
                        "Touch packet did not snapshot the committed red state");
                helper.assertTrue(SFMValue.of(absoluteDisplay.getX()).equals(fields.get("x"))
                        && SFMValue.of(absoluteDisplay.getY()).equals(fields.get("y"))
                        && SFMValue.of(absoluteDisplay.getZ()).equals(fields.get("z")),
                        "Touch packet did not identify its authoritative block position");
                helper.assertTrue(fields.get("u") instanceof SFMValue.DoubleValue
                        && fields.get("v") instanceof SFMValue.DoubleValue,
                        "Touch packet did not include floating-point UV coordinates");
                double u = ((SFMValue.DoubleValue) fields.get("u")).value();
                double v = ((SFMValue.DoubleValue) fields.get("v")).value();
                TouchDisplaySurface.UV requested = requestedTouch.get();
                helper.assertTrue(requested != null, "Touch arrived without a file-driven press request");
                helper.assertTrue(Math.abs(u - requested.u()) <= 1.0e-4
                        && Math.abs(v - requested.v()) <= 1.0e-4,
                        "Touch packet UV coordinates did not match the requested hit point");
            });
        }
    }
}
