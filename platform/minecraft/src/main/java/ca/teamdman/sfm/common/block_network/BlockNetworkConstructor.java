package ca.teamdman.sfm.common.block_network;

/// A factory for creating new {@link BlockNetwork} instances.
/// Used by {@link BlockNetworkManager} and {@link BlockNetwork#splitRemoveMember} to create new networks.
@FunctionalInterface
public interface BlockNetworkConstructor<LEVEL, T, NETWORK extends BlockNetwork<LEVEL, T>> {
    NETWORK create(LEVEL level, BlockNetworkMemberFilterMapper<LEVEL, T> memberFilterMapper);
}
