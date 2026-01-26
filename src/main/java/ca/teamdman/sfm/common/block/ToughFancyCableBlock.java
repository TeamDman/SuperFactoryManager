package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.registry.SFMBlocks;

public class ToughFancyCableBlock extends FancyCableBlock {
    public ToughFancyCableBlock(Properties properties) {
        super(properties);
    }

    @Override
    public IFacadableBlock getNonFacadeBlock() {
        return SFMBlocks.TOUGH_FANCY_CABLE_BLOCK.get();
    }

    @Override
    public IFacadableBlock getFacadeBlock() {
        return SFMBlocks.TOUGH_FANCY_CABLE_FACADE_BLOCK.get();
    }
}
