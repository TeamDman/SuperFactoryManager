package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Proves exact operator reads and bounded wire projection without opening a screen. */
@SFMGameTest(SFMDist.CLIENT)
public final class ManagerShowQueryGameTest extends SFMGameTestDefinition {
    private static final BlockPos MANAGER = new BlockPos(1, 2, 1);
    private static final BlockPos OTHER = new BlockPos(2, 2, 1);

    @Override public String template() { return "3x3x3"; }

    @Override
    public void run(SFMGameTestHelper helper) {
        helper.setBlock(MANAGER, SFMBlocks.MANAGER.get());
        helper.setBlock(OTHER, Blocks.STONE);
        ManagerBlockEntity manager = helper.getBlockEntity(MANAGER, ManagerBlockEntity.class);
        BlockPos absoluteManager = helper.absolutePos(MANAGER);
        BlockPos absoluteOther = helper.absolutePos(OTHER);
        ItemStack disk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(disk, "NAME \"show proof\"\n");
        LabelPositionHolder.from(disk).add("input", absoluteOther).save(disk);
        manager.setItem(0, disk);
        CompoundTag diskBefore = disk.getTag().copy();
        ItemStack originalDisk = disk.copy();

        var level = helper.getLevel();
        var dimension = level.dimension().location();
        helper.assertTrue(level.getServer().getPlayerList().getPlayers().size() == 1,
                "Manager show requires one connected integrated owner");
        ServerPlayer owner = level.getServer().getPlayerList().getPlayers().get(0);
        UUID request = UUID.randomUUID();
        var allowed = SFMManagerShowQuery.evaluate(contextFor(owner), request, dimension, absoluteManager);
        helper.assertTrue(allowed.status() == ClientboundManagerShowPacket.Status.ALLOWED
                        && allowed.requestId().equals(request)
                        && allowed.position().equals(absoluteManager)
                        && allowed.program().equals(DiskItem.getProgramStringReadOnly(disk))
                        && allowed.labels().get("input").contains(absoluteOther),
                "Exact manager show must return only stored program and labels");
        helper.assertTrue(diskBefore.equals(disk.getTag()), "Manager show must not mutate disk NBT");

        FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var codec = new ClientboundManagerShowPacket.Daddy();
            codec.encode(allowed, wire);
            var decoded = codec.decode(wire);
            helper.assertTrue(decoded.equals(allowed), "Manager show reply must round-trip within wire bounds");
        } finally {
            wire.release();
        }

        var wrongDimension = SFMManagerShowQuery.evaluate(contextFor(owner), request,
                new net.minecraft.resources.ResourceLocation("sfm", "elsewhere"), absoluteManager);
        assertDenied(helper, wrongDimension, ClientboundManagerShowPacket.Status.WRONG_DIMENSION);
        var nonManager = SFMManagerShowQuery.evaluate(contextFor(owner), request, dimension, absoluteOther);
        assertDenied(helper, nonManager, ClientboundManagerShowPacket.Status.NOT_MANAGER);
        var noSender = SFMManagerShowQuery.evaluate(contextFor(null), request, dimension, absoluteManager);
        assertDenied(helper, noSender, ClientboundManagerShowPacket.Status.NO_SENDER);

        try {
            for (int i = 0; i <= 32; i++) {
                LabelPositionHolder.from(disk).add("label" + i, absoluteOther);
            }
            LabelPositionHolder.from(disk).save(disk);
            var overBudget = SFMManagerShowQuery.evaluate(contextFor(owner), request, dimension, absoluteManager);
            assertDenied(helper, overBudget, ClientboundManagerShowPacket.Status.DATA_TOO_LARGE);
        } finally {
            // Use a fresh stack so LabelPositionHolder's per-stack cache cannot retain the oversized labels.
            manager.setItem(0, originalDisk);
        }

        // Leave the visible fixture in its readable state for a live sfm.exe query.
        var restored = SFMManagerShowQuery.evaluate(contextFor(owner), request, dimension, absoluteManager);
        helper.assertTrue(restored.status() == ClientboundManagerShowPacket.Status.ALLOWED
                        && restored.program().equals(DiskItem.getProgramStringReadOnly(originalDisk))
                        && restored.labels().get("input").contains(absoluteOther),
                "Live manager show fixture must be restored after the oversized projection test");
        SFM.LOGGER.info("SFM_MANAGER_SHOW_FIXTURE_READY dimension={} x={} y={} z={}",
                dimension, absoluteManager.getX(), absoluteManager.getY(), absoluteManager.getZ());
        helper.succeed();
    }

    private static SFMPacketHandlingContext contextFor(@Nullable ServerPlayer sender) {
        return new SFMPacketHandlingContext(() -> null) {
            @Override public @Nullable ServerPlayer sender() { return sender; }
        };
    }

    private static void assertDenied(SFMGameTestHelper helper, ClientboundManagerShowPacket reply,
                                     ClientboundManagerShowPacket.Status expected) {
        helper.assertTrue(reply.status() == expected && reply.program().isEmpty() && reply.labels().isEmpty(),
                "Denied manager show must contain status only: " + expected);
    }
}
