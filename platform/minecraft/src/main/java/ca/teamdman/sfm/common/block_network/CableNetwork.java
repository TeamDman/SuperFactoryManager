package ca.teamdman.sfm.common.block_network;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityDiscovery;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import ca.teamdman.sfm.common.logging.TranslatableLogger;
import ca.teamdman.sfm.common.util.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import vswe.superfactory.blocks.BlockManager;
import vswe.superfactory.tiles.TileEntityManager;

import java.util.List;
import java.util.stream.Stream;

/// A cable network extends {@link BlockNetwork} to add capability caching for blocks adjacent to cables.
/// When a {@link ManagerBlockEntity} is ticking many times in a row, there is worldly context that changes infrequently.
/// This class stores a cache of the cables and capabilities that the manager is aware of, to avoid repeated expensive lookups.
public class CableNetwork extends BlockNetwork<World, CableType> {
    protected final SFMBlockCapabilityCacheForLevel levelCapabilityCache;
    protected final BlockPosSet visualManagerPositions = new BlockPosSet();

    public CableNetwork(
            World level,
            BlockNetworkMemberFilterMapper<World, CableType> memberFilterMapper
    ) {

        super(level, memberFilterMapper, CableNetwork::new);
        this.levelCapabilityCache = new SFMBlockCapabilityCacheForLevel(level);
    }

    public SFMBlockCapabilityCacheForLevel getLevelCapabilityCache() {

        return levelCapabilityCache;
    }

    @Override
    void addMember(BlockPos memberBlockPos, CableType member) {
        super.addMember(memberBlockPos, member);
        if (member == CableType.VisualManager) {
            visualManagerPositions.add(memberBlockPos);
        }
    }

    @Override
    void removeMember(BlockPos blockPos) {
        super.removeMember(blockPos);
        visualManagerPositions.remove(blockPos);
    }



    /**
     * Only cable blocks are valid network members
     */
    public static boolean isCable(
            @Nullable World world,
            BlockPos cablePos
    ) {

        if (world == null) return false;
        return world
                .getBlockState(cablePos)
                .getBlock() instanceof ICableBlock;
    }
    /**
     * Only cable blocks are valid network members
     */
    public static boolean isVisualManager(
            @Nullable World world,
            BlockPos cablePos
    ) {

        if (world == null) return false;
        return world
                .getBlockState(cablePos)
                .getBlock() instanceof BlockManager;
    }

    /// Member filter mapper for use with BlockNetworkManager
    public static @Nullable CableType cableMemberFilterMapper(
            World level,
            BlockPos pos
    ) {

        return isCable(level, pos) ? isVisualManager(level, pos) ? CableType.VisualManager : CableType.Cable : null;
    }

    /// Discover all contiguous cable positions starting from the given position.
    /// This assumes that the start position is a cable block.
    public static Stream<BlockPos> discoverCables(
            World level,
            BlockPos startPos
    ) {

        return SFMStreamUtils.getRecursiveStream(
                (current, next, results) -> {
                    results.accept(current);
                    BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
                    for (EnumFacing d : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                        target.setPos(current).move(d);
                        if (isCable(level, target)) {
                            next.accept(target.toImmutable());
                        }
                    }
                }, startPos
        );
    }

    public World getLevel() {

        return level();
    }

    @Override
    public String toString() {

        return "CableNetwork{level="
               + getLevel()
               + ", #cables="
               + getCableCount()
               + ", #cache="
               + levelCapabilityCache.size()
               + "}";
    }

    /**
     * Cables should only join the network if they would be touching a cable already in the network
     *
     * @param pos Candidate cable position
     * @return {@code true} if adjacent to cable in network
     */
    public boolean isAdjacentToCable(BlockPos pos) {

        if (containsCablePosition(pos)) {
            return true; // allow managers to interact with themselves
        }
        BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
        for (EnumFacing direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
            target.setPos(pos).move(direction);
            if (containsCablePosition(target)) {
                return true;
            }
        }
        return false;
    }

    public boolean containsCablePosition(BlockPos pos) {

        return members().containsKey(pos);
    }

    @MCVersionDependentBehaviour
    public <CAP> @NotNull SFMBlockCapabilityResult<CAP> getCapability(
            SFMBlockCapabilityKind<CAP> capKind,
            BlockPos pos,
            @Nullable EnumFacing direction,
            TranslatableLogger logger
    ) {

        return SFMBlockCapabilityDiscovery.discoverCapabilityFromNetwork(
                this,
                capKind,
                pos,
                direction,
                logger
        );
    }

    public int getCableCount() {

        return size();
    }

    public BlockPosIterator getCapabilityProviderPositions() {

        return levelCapabilityCache.getPositions();
    }

    public BlockPosIterator getCablePositions() {

        return members().positions();
    }

    @Override
    void purgeChunk(ChunkPos chunkPos) {

        levelCapabilityCache.bustCacheForChunk(chunkPos);
        super.purgeChunk(chunkPos);
    }

    @Override
    void addAllFromOtherNetwork(BlockNetwork<World, CableType> other) {

        super.addAllFromOtherNetwork(other);
        // Also, merge capability caches if the other network is a CableNetwork
        if (other instanceof CableNetwork otherCable) {
            levelCapabilityCache.putAll(otherCable.levelCapabilityCache);
        }
    }

    @Override
    List<BlockNetwork<World, CableType>> splitRemoveMember(BlockPos blockPos) {
        // Call the parent implementation to handle the position tracking split
        List<BlockNetwork<World, CableType>> branches = super.splitRemoveMember(blockPos);

        // Transfer capability cache entries to the appropriate branch networks
        for (BlockNetwork<World, CableType> branch : branches) {
            if (branch instanceof CableNetwork cableBranch) {
                transferCapabilityCacheToBranch(cableBranch);
            }
        }

        return branches;
    }

    /// Transfer capability cache entries from this network to a branch network.
    /// Only transfers entries for positions adjacent to cables in the branch network.
    private void transferCapabilityCacheToBranch(CableNetwork branch) {

        BlockPosSet seenCapabilityPositions = new BlockPosSet();

        // For each cable in the branch, check adjacent positions for capability cache entries
        BlockPos.MutableBlockPos neighbourPos = new BlockPos.MutableBlockPos();
        for (BlockPos.MutableBlockPos cablePos : branch.members().positions()) {
            for (EnumFacing direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                neighbourPos.setPos(cablePos);
                neighbourPos.move(direction);
                // The same block may be touching multiple cables in the network
                boolean firstVisit = seenCapabilityPositions.add(neighbourPos); // correctness: consuming function makes immutable
                if (firstVisit) {
                    branch.levelCapabilityCache.overwriteFromOther(
                            neighbourPos,
                            this.levelCapabilityCache
                    ); // correctness: consuming function makes immutable
                }
            }
        }
    }

    public void bustCapabilityCacheForBlock(BlockPos pos) {
        this.levelCapabilityCache.bustCacheForBlock(pos);
    }


    public void updateVisualManagers() {
        this.updateVisualManagers(null);
    }

    public void updateVisualManagers(@Nullable BlockPos cablePos) {
        this.visualManagerPositions.blockPosIterator().forEach((pos) -> {
            if (!pos.equals(cablePos) && this.level.getTileEntity(pos) instanceof TileEntityManager manager) {
                manager.invalidateInventories();
            }
        });
    }
//
//    public void addVisualManager(BlockPos pos) {
//        this.visualManagerPositions.add(pos.toLong());
//    }
}
