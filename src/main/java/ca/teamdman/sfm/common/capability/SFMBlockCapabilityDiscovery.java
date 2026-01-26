package ca.teamdman.sfm.common.capability;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block_network.CableNetwork;
import ca.teamdman.sfm.common.block_network.SFMBlockCapabilityCacheForLevel;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.logging.TranslatableLogger;
import ca.teamdman.sfm.common.program.LimitedInputSlot;
import ca.teamdman.sfm.common.program.LimitedOutputSlot;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.registry.SFMResourceTypes;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMDirections;
import ca.teamdman.sfml.ast.OutputStatement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/// When SFM is moving items
///
/// ```
/// INPUT item::, fluid:: FROM a
/// OUTPUT item::, fluid:: TO b
///```
///
/// the {@link SFMResourceTypes} being moved are each tied to a {@link SFMBlockCapabilityKind}.
/// See {@link OutputStatement#moveTo(ProgramContext, LimitedInputSlot, LimitedOutputSlot)} for details.
///
/// This class helps keep related capability discovery logic in one place and out of the {@link CableNetwork}.
///
/// The methods by which capabilities are retrieved change in Minecraft 1.20.3.
/// To discover the right capability for a given block position, we use {@link SFMBlockCapabilityProviderDiscovery} to
/// iterate over the appropriate {@link SFMBlockCapabilityProvider} to find a {@link SFMBlockCapabilityResult}.
///
/// The discovery results from {@link CableNetwork#getCapability(SFMBlockCapabilityKind, BlockPos, Direction, TranslatableLogger)}
/// will be cached in the {@link CableNetwork#getLevelCapabilityCache()}
/// so the {@link SFMBlockCapabilityProviderDiscovery} can focus on its job.
public class SFMBlockCapabilityDiscovery {
    public static <CAP> @NotNull SFMBlockCapabilityResult<CAP> discoverCapabilityFromNetwork(
            CableNetwork cableNetwork,
            SFMBlockCapabilityKind<CAP> capKind,
            BlockPos pos,
            @Nullable Direction direction,
            TranslatableLogger logger
    ) {

        /* #####################
                CHECK CACHE
           ##################### */

        // If there is a cache entry, it has already been validated to be adjacent to a cable
        SFMBlockCapabilityCacheForLevel levelCapabilityCache = cableNetwork.getLevelCapabilityCache();

        // It is a precondition to enter the cache that the capability is adjacent to a cable
        SFMBlockCapabilityResult<CAP> cached = discoverCapabilityFromCache(
                capKind,
                pos,
                direction,
                logger,
                levelCapabilityCache
        );
        if (cached.isPresent()) return cached;

        /* #####################
            DISCOVER FROM LEVEL
           ##################### */

        // Any BlockPos can have labels assigned to it.
        // We must only proceed here if there is an adjacent cable from this network.
        if (!cableNetwork.isAdjacentToCable(pos)) {
            logger.warn(x -> x.accept(LocalizationKeys.LOGS_MISSING_ADJACENT_CABLE.get(pos)));
            return SFMBlockCapabilityResult.empty();
        }

        if (!(cableNetwork.getLevel() instanceof ServerLevel serverLevel)) {
            return SFMBlockCapabilityResult.empty();
        }
        SFMBlockCapabilityResult<CAP> cap = discoverCapabilityFromLevel(
                serverLevel,
                capKind,
                pos,
                direction
        );
        if (cap.isPresent()) {
            // Track in cache
            levelCapabilityCache.putCapability(pos, capKind, direction, cap);
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
            LevelAccessor level,
            BlockPos pos
    ) {

        return SFMWellKnownCapabilities.streamCapabilities()
                .filter(cap -> !cap.equals(SFMWellKnownCapabilities.REDSTONE_HANDLER))
                .anyMatch(cap -> {
                    for (Direction direction : SFMDirections.DIRECTIONS_WITH_NULL) {
                        if (discoverCapabilityFromLevel(level, cap, pos, direction).isPresent()) {
                            return true;
                        }
                    }
                    return false;
                });
    }

    @MCVersionDependentBehaviour
    public static <CAP> @NotNull SFMBlockCapabilityResult<CAP> discoverCapabilityFromLevel(
            LevelAccessor level,
            SFMBlockCapabilityKind<CAP> capKind,
            BlockPos pos,
            @Nullable Direction direction
    ) {

        BlockState blockState = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);

        try {
            ArrayList<SFMBlockCapabilityProvider<CAP>> providersForKind = SFMBlockCapabilityProviderDiscovery
                    .getCapabilityProvidersForKindFast(capKind);

            for (SFMBlockCapabilityProvider<CAP> capabilityProviderMapper : providersForKind) {
                var capability = capabilityProviderMapper.getCapability(
                        capKind,
                        level,
                        pos,
                        blockState,
                        blockEntity,
                        direction
                );
                if (capability.isPresent()) {
                    return capability;
                }
            }
        } catch (Throwable t) {
            SFM.LOGGER.error(
                    """
                            SFM encountered an exception while querying capabilities. Please report this!
                            {}
                            capKind={}
                            level={}
                            pos={}
                            blockState={}
                            block={}
                            blockClass={}
                            blockEntity={}
                            direction={}
                            """.stripTrailing().stripIndent(),
                    SFM.ISSUE_TRACKER_URL,
                    capKind,
                    level,
                    pos,
                    blockState,
                    blockState.getBlock(),
                    blockState.getBlock().getClass(),
                    blockEntity,
                    direction
            );
        }
        return SFMBlockCapabilityResult.empty();
    }

    private static <CAP> @NotNull SFMBlockCapabilityResult<CAP> discoverCapabilityFromCache(
            SFMBlockCapabilityKind<CAP> capKind,
            BlockPos pos,
            @Nullable Direction direction,
            TranslatableLogger logger,
            SFMBlockCapabilityCacheForLevel levelCapabilityCache
    ) {

        var found = levelCapabilityCache.getCapability(pos, capKind, direction);
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
                // CACHE HIT BUT STALE
                logger.error(x -> x.accept(LocalizationKeys.LOG_CAPABILITY_CACHE_HIT_INVALID.get(
                        pos,
                        capKind.getName(),
                        direction
                )));
            }
        } else {
            // CACHE MISS
            logger.trace(x -> x.accept(LocalizationKeys.LOG_CAPABILITY_CACHE_MISS.get(
                    pos,
                    capKind.getName(),
                    direction
            )));
        }
        return SFMBlockCapabilityResult.empty();
    }

}
