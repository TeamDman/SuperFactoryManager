package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;

public class ToughFancyCableBlock extends FancyCableBlock {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TOUGH_FANCY_CABLE_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.TOUGH_FANCY_CABLE.get().getDescriptionId(),
            () -> "Tough Fancy Inventory Cable"
    );

    public ToughFancyCableBlock(Properties properties) {

        super(properties);
    }

    @Override
    public IFacadableBlock getNonFacadeBlock() {

        return SFMBlocks.TOUGH_FANCY_CABLE.get();
    }

    @Override
    public IFacadableBlock getFacadeBlock() {

        return SFMBlocks.TOUGH_FANCY_CABLE_FACADE.get();
    }

}
