package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.registry.SFMBlocks;

public class ToughCableBlock extends CableBlock {
    public ToughCableBlock(Properties properties) {
        super(properties);
    }

    @Override
    public IFacadableBlock getNonFacadeBlock() {
        return SFMBlocks.TOUGH_CABLE_BLOCK.get();
    }

    @Override
    public IFacadableBlock getFacadeBlock() {
        return SFMBlocks.TOUGH_CABLE_FACADE_BLOCK.get();
    }
}
