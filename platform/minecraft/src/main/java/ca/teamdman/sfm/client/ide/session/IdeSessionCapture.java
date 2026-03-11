package ca.teamdman.sfm.client.ide.session;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class IdeSessionCapture {
    private IdeSessionCapture() {
    }

        public static void capture(IdeSession session, Minecraft minecraft, String focusedPanelDisplay) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            session.update(
                new IdeShellContextSnapshot(
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(focusedPanelDisplay),
                    Optional.empty(),
                    0
                ),
                    IdeSessionTarget.none(),
                    List.of()
            );
            return;
        }

        BlockPos playerPos = minecraft.player.blockPosition();
        Vec3 look = minecraft.player.getLookAngle();
        String dimensionId = minecraft.level.dimension().location().toString();
        IdeSessionTarget focusedTarget = captureFocusedTarget(minecraft, dimensionId);
        List<IdeSessionTarget> selectedTargets = focusedTarget.isBound() ? List.of(focusedTarget) : List.of();

        session.update(
                new IdeShellContextSnapshot(
                    Optional.of(playerPos.getX() + ", " + playerPos.getY() + ", " + playerPos.getZ()),
                    Optional.of(String.format(Locale.ROOT, "%.2f, %.2f, %.2f", look.x, look.y, look.z)),
                    describeHit(minecraft),
                    Optional.of(dimensionId),
                    Optional.of(focusedPanelDisplay),
                        focusedTarget.summary(),
                        selectedTargets.size()
                ),
                focusedTarget,
                selectedTargets
        );
    }

    private static IdeSessionTarget captureFocusedTarget(Minecraft minecraft, String dimensionId) {
        if (minecraft.hitResult instanceof BlockHitResult blockHitResult) {
            BlockPos pos = blockHitResult.getBlockPos();
            var state = minecraft.level.getBlockState(pos);
            var blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            String display = blockId == null ? state.getBlock().toString() : blockId.toString();
            String location = pos.getX() + "," + pos.getY() + "," + pos.getZ();
            return new IdeSessionTarget(IdeTargetKind.BLOCK, Optional.of(display), Optional.of(location), Optional.of(dimensionId));
        }
        if (minecraft.hitResult instanceof EntityHitResult entityHitResult) {
            Entity entity = entityHitResult.getEntity();
            var entityId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
            String display = entityId == null ? entity.getType().toString() : entityId.toString();
            String location = String.format(Locale.ROOT, "%.1f,%.1f,%.1f", entity.getX(), entity.getY(), entity.getZ());
            return new IdeSessionTarget(IdeTargetKind.ENTITY, Optional.of(display), Optional.of(location), Optional.of(dimensionId));
        }
        if (minecraft.hitResult != null) {
            return new IdeSessionTarget(IdeTargetKind.MISS, Optional.of(minecraft.hitResult.getType().name()), Optional.empty(), Optional.of(dimensionId));
        }
        return IdeSessionTarget.none();
    }

    private static Optional<String> describeHit(Minecraft minecraft) {
        HitResult hitResult = minecraft.hitResult;
        if (hitResult instanceof BlockHitResult blockHitResult) {
            BlockPos pos = blockHitResult.getBlockPos();
            var state = minecraft.level.getBlockState(pos);
            var blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            String blockName = blockId == null ? state.getBlock().toString() : blockId.toString();
            return Optional.of(blockName + " @ " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
        }
        if (hitResult instanceof EntityHitResult entityHitResult) {
            Entity entity = entityHitResult.getEntity();
            var entityId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
            String display = entityId == null ? entity.getType().toString() : entityId.toString();
            return Optional.of(display);
        }
        return hitResult == null ? Optional.empty() : Optional.of(hitResult.getType().name());
    }
}