package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.block.TouchDisplayBlock;
import ca.teamdman.sfm.common.block.TouchDisplaySurface;
import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.value.SFMTouchValue;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;

import java.util.Map;
import java.util.UUID;

/** Server-authoritative touch proof; does not use the arbitrary client insertion path. */
@SFMGameTest(SFMDist.DEDICATED_SERVER)
public class TouchDisplayPressServerGameTest extends SFMGameTestDefinition {
    protected static final BlockPos DISPLAY = new BlockPos(2, 2, 2);
    private static final double INSET = 7.0 / 16.0;

    @Override
    public String template() {
        return "5x5x5";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        runProof(helper);
        helper.succeed();
    }

    protected static void runProof(SFMGameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        for (Direction face : Direction.values()) {
            clearNeighbors(helper);
            helper.setBlock(DISPLAY.relative(face.getOpposite()), Blocks.CHEST);
            TouchDisplayBlockEntity display = orient(helper, face);
            SFMValue semanticState = SFMValue.object(Map.of("face", SFMValue.of(face.getName())));
            helper.assertTrue(display.commitContent(TouchDisplayBlockEntity.RED_FIXTURE_IMAGE, semanticState),
                    face + " did not commit initial semantic state");
            BlockHitResult hit = hit(helper.absolutePos(DISPLAY), face, 0.25, 0.75);
            positionEyeAtFace(player, hit, face, 0.75);
            helper.assertTrue(press(helper, player, InteractionHand.MAIN_HAND, hit) == InteractionResult.CONSUME,
                    face + " main-hand press was not consumed");
            IItemHandler rear = helper.getItemHandler(DISPLAY.relative(face.getOpposite()));
            SFMValue expected = SFMTouchValue.press(
                    helper.getLevel().dimension().location(),
                    helper.absolutePos(DISPLAY),
                    face,
                    0.25,
                    0.75,
                    display.content().revision(),
                    semanticState
            );
            helper.assertCount(rear, PacketItem.create(expected), 1,
                    face + " did not send exactly one packet to its rear chest");
            helper.assertTrue(PacketItem.getValue(rear.getStackInSlot(0)).orElseThrow().equals(expected),
                    face + " packet did not snapshot server-owned coordinates and state");
        }

        // Isolate the north rear chest for negative, stacking and revision assertions.
        Direction face = Direction.NORTH;
        clearNeighbors(helper);
        BlockPos rearPosition = DISPLAY.relative(face.getOpposite());
        helper.setBlock(rearPosition, Blocks.CHEST);
        ChestBlockEntity rearChest = helper.getBlockEntity(rearPosition, ChestBlockEntity.class);
        for (int slot = 0; slot < rearChest.getContainerSize(); slot++) {
            rearChest.setItem(slot, ItemStack.EMPTY);
        }
        TouchDisplayBlockEntity display = orient(helper, face);
        SFMValue red = SFMValue.object(Map.of("color", SFMValue.of("red")));
        display.commitContent(TouchDisplayBlockEntity.RED_FIXTURE_IMAGE, red);
        BlockPos absolute = helper.absolutePos(DISPLAY);
        BlockHitResult valid = hit(absolute, face, 0.25, 0.75);
        positionEyeAtFace(player, valid, face, 0.75);

        SFMValue oversizedTouchState = SFMValue.of("x".repeat(3_000));
        helper.assertTrue(SFMValueJsonCodec.encode(oversizedTouchState).length()
                        < SFMValueJsonCodec.MAX_ENCODED_UTF8_BYTES,
                "Preflight fixture should fit as an independent value");
        TouchDisplayBlockEntity.DisplayContent beforeOversized = display.content();
        boolean oversizedRejected = false;
        try {
            display.commitContent(TouchDisplayBlockEntity.BLUE_FIXTURE_IMAGE, oversizedTouchState);
        } catch (IllegalArgumentException expected) {
            oversizedRejected = true;
        }
        helper.assertTrue(oversizedRejected, "Display accepted state that overflows its touch envelope");
        helper.assertTrue(display.content().equals(beforeOversized),
                "Rejected oversized touch state advanced the content revision");

        press(helper, player, InteractionHand.OFF_HAND, valid);
        press(helper, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(valid.getLocation(), Direction.SOUTH, absolute, false));
        press(helper, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(valid.getLocation(), face, absolute.offset(1, 0, 0), false));
        press(helper, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(valid.getLocation().add(0, 0, -0.001), face, absolute, false));
        press(helper, player, InteractionHand.MAIN_HAND, hit(absolute, face, 1.03, 0.5));
        press(helper, spectator(helper), InteractionHand.MAIN_HAND, valid);
        helper.assertCount(helper.getItemHandler(rearPosition), 0,
                "Offhand, spectator and invalid hits must not emit a packet");

        helper.setBlock(DISPLAY.relative(face), Blocks.STONE);
        positionEyeAtFace(player, valid, face, 2.0);
        press(helper, player, InteractionHand.MAIN_HAND, valid);
        helper.assertCount(helper.getItemHandler(rearPosition), 0,
                "Occluded claimed hit must not emit a packet");
        helper.setBlock(DISPLAY.relative(face), Blocks.AIR);
        positionEyeAtFace(player, valid, face, 0.75);

        press(helper, player, InteractionHand.MAIN_HAND, valid);
        press(helper, player, InteractionHand.MAIN_HAND, valid);
        SFMValue redPress = SFMTouchValue.press(
                helper.getLevel().dimension().location(), absolute, face, 0.25, 0.75,
                display.content().revision(), red
        );
        helper.assertCount(helper.getItemHandler(rearPosition), PacketItem.create(redPress), 2,
                "Repeated presses on unchanged semantic content must stack");

        SFMValue blue = SFMValue.object(Map.of("color", SFMValue.of("blue")));
        helper.assertTrue(display.commitContent(TouchDisplayBlockEntity.BLUE_FIXTURE_IMAGE, blue),
                "Blue content did not commit");
        press(helper, player, InteractionHand.MAIN_HAND, valid);
        SFMValue bluePress = SFMTouchValue.press(
                helper.getLevel().dimension().location(), absolute, face, 0.25, 0.75,
                display.content().revision(), blue
        );
        IItemHandler rear = helper.getItemHandler(rearPosition);
        helper.assertCount(rear, PacketItem.create(redPress), 2,
                "Later content revision must not rewrite earlier packet state");
        helper.assertCount(rear, PacketItem.create(bluePress), 1,
                "New content revision must produce a distinct packet stack");

        for (int slot = 0; slot < rearChest.getContainerSize(); slot++) {
            rearChest.setItem(slot, new ItemStack(Items.STONE, 64));
        }
        helper.assertTrue(press(helper, player, InteractionHand.MAIN_HAND, valid) == InteractionResult.CONSUME,
                "Full rear inventory must still consume the press once");
        helper.assertCount(helper.getItemHandler(rearPosition), Items.STONE,
                rearChest.getContainerSize() * 64, "Rejected full inventory was changed");
        helper.assertCount(helper.getItemHandler(rearPosition), PacketItem.create(bluePress), 0,
                "Full rear inventory accepted a touch packet");

        for (int slot = 0; slot < rearChest.getContainerSize(); slot++) {
            rearChest.setItem(slot, ItemStack.EMPTY);
        }
        helper.setBlock(rearPosition, Blocks.STONE);
        helper.assertTrue(press(helper, player, InteractionHand.MAIN_HAND, valid) == InteractionResult.CONSUME,
                "Missing rear inventory must still consume the press once");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(
                ItemEntity.class, new AABB(absolute).inflate(2)).isEmpty(),
                "Rejected touch press spilled a packet into the world");
    }

    private static TouchDisplayBlockEntity orient(SFMGameTestHelper helper, Direction facing) {
        helper.setBlock(DISPLAY, SFMBlocks.TOUCH_DISPLAY.get().defaultBlockState()
                .setValue(TouchDisplayBlock.FACING, facing));
        return helper.getBlockEntity(DISPLAY, TouchDisplayBlockEntity.class);
    }

    private static InteractionResult press(
            SFMGameTestHelper helper,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        BlockState state = helper.getBlockState(DISPLAY);
        return state.getBlock().use(state, helper.getLevel(), helper.absolutePos(DISPLAY), player, hand, hit);
    }

    protected static BlockHitResult hit(BlockPos position, Direction face, double u, double v) {
        TouchDisplaySurface.Basis basis = TouchDisplaySurface.basis(face);
        double right = (u - 0.5) * 2 * INSET;
        double up = (0.5 - v) * 2 * INSET;
        return new BlockHitResult(new Vec3(
                position.getX() + 0.5 + face.getStepX() * 0.5 + basis.rightX() * right + basis.upX() * up,
                position.getY() + 0.5 + face.getStepY() * 0.5 + basis.rightY() * right + basis.upY() * up,
                position.getZ() + 0.5 + face.getStepZ() * 0.5 + basis.rightZ() * right + basis.upZ() * up
        ), face, position, false);
    }

    @MCVersionDependentBehaviour
    private static Player spectator(SFMGameTestHelper helper) {
        return new Player(
                helper.getLevel(), BlockPos.ZERO, 0,
                new GameProfile(UUID.randomUUID(), "touch-spectator")
        ) {
            @Override
            public boolean isSpectator() {
                return true;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
    }

    private static void positionEyeAtFace(
            Player player,
            BlockHitResult hit,
            Direction face,
            double outwardDistance
    ) {
        Vec3 point = hit.getLocation();
        player.setPos(
                point.x + face.getStepX() * outwardDistance,
                point.y + face.getStepY() * outwardDistance - player.getEyeHeight(),
                point.z + face.getStepZ() * outwardDistance
        );
    }

    private static void clearNeighbors(SFMGameTestHelper helper) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = DISPLAY.relative(direction);
            if (helper.getLevel().getBlockEntity(helper.absolutePos(neighbor)) instanceof ChestBlockEntity chest) {
                for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                    chest.setItem(slot, ItemStack.EMPTY);
                }
            }
            helper.setBlock(neighbor, Blocks.AIR);
        }
    }
}
