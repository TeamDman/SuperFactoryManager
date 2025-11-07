package ca.teamdman.sfm.common.capability;

import ca.teamdman.sfm.common.cablenetwork.CableNetwork;
import ca.teamdman.sfm.common.cablenetwork.SFMBlockCapabilityCacheForLevel;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.logging.TranslatableLogger;
import ca.teamdman.sfm.common.program.LimitedInputSlot;
import ca.teamdman.sfm.common.program.LimitedOutputSlot;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.util.NotStored;
import ca.teamdman.sfm.common.util.SFMDirections;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// When SFM is moving items
///
/// ```
/// INPUT item::, fluid:: FROM a
/// OUTPUT item::, fluid:: TO b
/// ```
///
/// the {@link ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer.ResourceType} being moved are each tied to a {@link SFMBlockCapabilityKind}.
/// See {@link ca.teamdman.sfml.ast.OutputStatement#moveTo(ProgramContext, LimitedInputSlot, LimitedOutputSlot)} for details.
///
/// This class helps keep related capability discovery logic in one place and out of the {@link CableNetwork}.
///
/// The discovery results from {@link CableNetwork#getCapability(SFMBlockCapabilityKind, BlockPos, EnumFacing, TranslatableLogger)}
/// will be cached in the {@link CableNetwork#getLevelCapabilityCache()}
/// so the {@link SFMBlockCapabilityProviderDiscovery} can focus on its job.
public class SFMBlockCapabilityDiscovery {
    public static <CAP> @NotNull SFMBlockCapabilityResult<CAP> discoverCapabilityFromNetwork(
            CableNetwork cableNetwork,
            SFMBlockCapabilityKind<CAP> capKind,
            @NotStored BlockPos pos,
            @Nullable EnumFacing direction,
            TranslatableLogger logger
    ) {
        SFMBlockCapabilityCacheForLevel levelCapabilityCache = cableNetwork.getLevelCapabilityCache();
        World world = cableNetwork.getLevel();

        // It is a precondition to enter the cache that the capability is adjacent to a cable
        SFMBlockCapabilityResult<CAP> cached = discoverCapabilityFromCache(
                world,
                capKind,
                pos,
                direction,
                logger,
                levelCapabilityCache
        );
        if (cached != null && cached.isPresent()) return cached;

        // NEED TO DISCOVER

        // any BlockPos can have labels assigned
        // we must only proceed here if there is an adjacent cable from this network
        if (!cableNetwork.isAdjacentToCable(pos)) {
            logger.warn(x -> x.accept(LocalizationKeys.LOGS_MISSING_ADJACENT_CABLE.get(pos)));
            return SFMBlockCapabilityResult.empty();
        }

        if (world.isRemote) {
            return SFMBlockCapabilityResult.empty();
        }
        SFMBlockCapabilityResult<CAP> cap = discoverCapabilityFromLevel(
                world,
                capKind,
                pos,
                direction
        );
        if (cap.isPresent()) {
            // Track in cache
            levelCapabilityCache.putCapability(world, pos, capKind, direction, cap);
        } else {
            logger.warn(x -> x.accept(LocalizationKeys.LOGS_EMPTY_CAPABILITY.get(
                    pos,
                    capKind.getName(),
                    direction
            )));
        }
        return cap;
    }

    public static boolean hasAnyCapabilityAnyDirection(
            World level,
            BlockPos pos
    ) {
        return SFMWellKnownCapabilities.streamCapabilities().anyMatch(cap -> {
            for (EnumFacing direction : SFMDirections.DIRECTIONS_WITH_NULL) {
                if (discoverCapabilityFromLevel(level, cap, pos, direction).isPresent()) {
                    return true;
                }
            }
            return false;
        });
    }

    public static <CAP> @NotNull SFMBlockCapabilityResult<CAP> discoverCapabilityFromLevel(
            World level,
            SFMBlockCapabilityKind<CAP> capKind,
            @NotStored BlockPos pos,
            @Nullable EnumFacing direction
    ) {
        return SFMBlockCapabilityProviderDiscovery.getCapabilityFromLevel(
                capKind,
                level,
                pos,
                level.getBlockState(pos),
                level.getTileEntity(pos),
                direction
        );
    }

    private static <CAP> @Nullable SFMBlockCapabilityResult<CAP> discoverCapabilityFromCache(
            World world,
            SFMBlockCapabilityKind<CAP> capKind,
            @NotStored BlockPos pos,
            @Nullable EnumFacing direction,
            TranslatableLogger logger,
            SFMBlockCapabilityCacheForLevel levelCapabilityCache
    ) {
        var found = levelCapabilityCache.getCapability(world, pos, capKind, direction);
        if (found != null) {
            // CACHE HIT
            if (found.isPresent()) {
                logger.trace(x -> x.accept(LocalizationKeys.LOG_CAPABILITY_CACHE_HIT.get(
                        pos,
                        capKind.getName(),
                        direction
                )));
                return found;
            } else {
                // CACHE HIT BUT EMPTY
                // This can happen if a previous discovery found nothing. We trust the cache.
                return found;
            }
        } else {
            // CACHE MISS
            logger.trace(x -> x.accept(LocalizationKeys.LOG_CAPABILITY_CACHE_MISS.get(
                    pos,
                    capKind.getName(),
                    direction
            )));
        }
        return null;
    }
}
